// H2-Go map page: asks Java (/api/...) for the data, fills the templates in index.html, and draws the map with Leaflet.

let map = null;
let you = null;           // { lat, lng, where }: where = the template that says how you got there
let youMarker = null;
let markers = {};         // fountain id -> its pin
let selectedId = null;    // the fountain whose details are showing
let routeLine = null;
let walkways = null;      // the walkway graph from /api/paths
let walkwayLayer = null;
let searches = 0;         // to show only the newest search's answer
let currentTab = 'find';

// ================================================================ Java

async function getJson(url) {
  const response = await fetch(url);
  return readAnswer(response);
}

// data: an object like { id: 7 }, or a form's fields (new FormData(form))
async function postForm(url, data) {
  const response = await fetch(url, { method: 'POST', body: new URLSearchParams(data) });
  return readAnswer(response);
}

async function readAnswer(response) {
  const answer = await response.json();
  if (response.status === 401) {
    location.href = 'login.html';   // not signed in
  }
  if (!response.ok) {
    throw new Error(answer.error);
  }
  return answer;
}

// A short message at the top of the panel (red for errors).
function say(text, isError) {
  const box = document.getElementById('message');
  box.textContent = text;
  box.className = isError ? 'error' : 'ok';
  box.hidden = false;
  clearTimeout(say.timer);
  say.timer = setTimeout(function () { box.hidden = true; }, 5000);
}

function showError(error) {
  say(error.message, true);
}

// ================================================================ templates

// A copy of a <template> filled with values: data-text sets the text, data-class adds a class,
// data-show / data-hide show or hide the element (a list counts as true when it is not empty).
function fill(templateId, values) {
  const copy = document.getElementById(templateId).content.cloneNode(true);
  for (const element of copy.querySelectorAll('[data-text]')) {
    element.textContent = values[element.dataset.text] ?? '';
  }
  for (const element of copy.querySelectorAll('[data-class]')) {
    element.classList.add(values[element.dataset.class]);
  }
  for (const element of copy.querySelectorAll('[data-show]')) {
    element.hidden = !isOn(values[element.dataset.show]);
  }
  for (const element of copy.querySelectorAll('[data-hide]')) {
    element.hidden = isOn(values[element.dataset.hide]);
  }
  return copy;
}

function isOn(value) {
  return Array.isArray(value) ? value.length > 0 : Boolean(value);
}

// The words in a <template> (messages and labels written in index.html).
function words(templateId) {
  return document.getElementById(templateId).content.textContent.trim();
}

// Fills a form from Java's values, for example { bottle: 'LARGE', need: ['1', '8'] }.
function setForm(form, values) {
  for (const field of form.elements) {
    if (!(field.name in values)) {
      continue;
    }
    if (field.type === 'checkbox') {
      field.checked = values[field.name].includes(field.value);
    } else {
      field.value = values[field.name];
    }
  }
}

// A map pin drawn by style.css.
function cssPin(classNames) {
  return L.divIcon({ className: classNames, iconSize: null });
}

// ================================================================ start

async function start() {
  const answer = await getJson('/api/me');
  if (!answer.signedIn) {
    location.href = 'login.html';
    return;
  }
  const me = answer.user;
  document.getElementById('hello').replaceChildren(fill('hello-text', me));

  const mapBox = document.getElementById('map');
  map = L.map(mapBox);
  L.tileLayer(mapBox.dataset.tiles, {
    maxZoom: 19,
    attribution: document.getElementById('map-credit').innerHTML,
  }).addTo(map);
  map.on('click', function (e) { setYou(e.latlng.lat, e.latlng.lng, 'you-at-click'); });
  map.on('locationfound', function (e) { setYou(e.latlng.lat, e.latlng.lng, 'you-at-gps'); });
  map.on('locationerror', function () { say(words('no-location'), true); });

  const findForm = document.getElementById('find-form');
  setForm(findForm, me.preferences);

  walkways = await getJson('/api/paths');
  const stations = await loadStations();
  map.fitBounds(stations.bounds);

  setYou(walkways.start.lat, walkways.start.lng, 'you-at-gate');

  for (const field of findForm.elements) {
    field.onchange = function () { findBest().catch(showError); };
  }
  document.getElementById('locate-btn').onclick = function () { map.locate({ setView: true, maxZoom: 18 }); };
  document.getElementById('show-walkways').onchange = toggleWalkways;
  document.getElementById('logout-btn').onclick = async function () {
    await postForm('/api/logout', {});
    location.href = 'login.html';
  };
  for (const button of document.querySelectorAll('.tabs button')) {
    button.onclick = function () { showTab(button.dataset.tab); };
  }
}

// ================================================================ the map

// One pin per fountain, colored by its status group.
async function loadStations() {
  const answer = await getJson('/api/stations');
  for (const s of answer.stations) {
    if (markers[s.id]) {
      markers[s.id].remove();
    }
    const classNames = 'pin ' + s.statusGroup + (s.favorite ? ' fav' : '') + (s.id === selectedId ? ' selected' : '');
    const marker = L.marker([s.lat, s.lng], { icon: cssPin(classNames), keyboard: false });
    marker.bindTooltip(s.name);
    marker.on('click', function () { showStation(s.id).catch(showError); });
    marker.addTo(map);
    markers[s.id] = marker;
  }
  return answer;
}

// "You": where the walk starts.
function setYou(lat, lng, where) {
  you = { lat: lat, lng: lng, where: where };
  if (youMarker) {
    youMarker.remove();
  }
  youMarker = L.marker([lat, lng], { icon: cssPin('you'), keyboard: false, bubblingMouseEvents: true })
    .bindTooltip(words('you-label'), { permanent: true, direction: 'top', className: 'you-label' })
    .addTo(map);
  clearRoute();
  findBest().catch(showError);
  if (currentTab === 'favorites') {
    showFavorites(false).catch(showError);
  }
}

function clearRoute() {
  if (routeLine) {
    routeLine.remove();
    routeLine = null;
  }
}

// The points and walkways Dijkstra uses.
function toggleWalkways() {
  if (walkwayLayer) {
    walkwayLayer.remove();
    walkwayLayer = null;
  }
  if (!document.getElementById('show-walkways').checked) {
    return;
  }
  walkwayLayer = L.layerGroup();
  for (const point of walkways.points) {
    L.marker([point.lat, point.lng], { icon: cssPin('walk-point'), keyboard: false, bubblingMouseEvents: true })
      .bindTooltip(point.label)
      .addTo(walkwayLayer);
  }
  for (const line of walkways.lines) {
    L.polyline(line, { className: 'walkway' }).addTo(walkwayLayer);
  }
  walkwayLayer.addTo(map);
}

// ================================================================ the best fountains

async function findBest() {
  if (!you) {
    return;
  }
  const params = new URLSearchParams(new FormData(document.getElementById('find-form')));
  params.set('lat', you.lat);
  params.set('lng', you.lng);
  searches++;
  const thisSearch = searches;
  const answer = await getJson('/api/recommend?' + params);
  if (thisSearch !== searches) {
    return;   // a newer search was started meanwhile
  }

  document.getElementById('you-text').replaceChildren(fill(you.where, answer));

  const results = fill('results-list', answer);
  const list = results.querySelector('.results');
  for (const choice of answer.results) {
    const item = fill('result-item', choice);
    item.querySelector('li').onclick = function () { showStation(choice.id).catch(showError); };
    list.append(item);
  }
  document.getElementById('results').replaceChildren(results);
}

// ================================================================ one fountain

async function showStation(id) {
  selectedId = id;
  showTab('find');
  const station = await getJson('/api/station?id=' + id);
  await loadStations();   // the selected pin gets bigger

  const card = fill('station-card', station);
  const list = card.querySelector('.reports');
  for (const report of station.reports) {
    const item = fill('report-item', report);
    item.querySelector('.remove-btn').onclick = function () { removeReport(report.id, id).catch(showError); };
    list.append(item);
  }
  card.querySelector('#route-btn').onclick = function () {
    showRoute(id, document.getElementById('route-text')).catch(showError);
  };
  card.querySelector('#refill-btn').onclick = function () { refill(id).catch(showError); };
  card.querySelector('#fav-btn').onclick = function () { toggleFavorite(id).catch(showError); };
  card.querySelector('#report-form').onsubmit = function (event) {
    event.preventDefault();
    sendReport(id, event.target).catch(showError);
  };

  const details = document.getElementById('details');
  details.replaceChildren(card);
  details.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// The walk from You to the fountain. textBox shows how long it is.
async function showRoute(id, textBox) {
  const params = new URLSearchParams({ lat: you.lat, lng: you.lng, id: id });
  const route = await getJson('/api/route?' + params);
  clearRoute();
  routeLine = L.polyline(route.line, { className: 'route' }).addTo(map);
  map.fitBounds(route.bounds);
  textBox.replaceChildren(fill('route-info', route));
}

async function sendReport(id, form) {
  const data = new URLSearchParams(new FormData(form));
  data.set('id', id);
  const answer = await postForm('/api/report', data);
  say(answer.message);
  await showStation(id);
  await findBest();
}

// Admins only
async function removeReport(reportId, stationId) {
  if (!confirm(words('remove-question'))) {
    return;
  }
  const answer = await postForm('/api/deleteReport', { id: reportId });
  say(answer.message);
  await showStation(stationId);
  await findBest();
}

async function refill(id) {
  const answer = await postForm('/api/refill', { id: id });
  say(answer.message);
}

async function toggleFavorite(id) {
  const answer = await postForm('/api/favorite', { id: id });
  say(answer.message);
  await showStation(id);
}

// ================================================================ the other tabs

function showTab(name) {
  currentTab = name;
  for (const tab of ['find', 'favorites', 'maintenance', 'profile']) {
    document.getElementById('tab-' + tab).hidden = (tab !== name);
    document.querySelector('[data-tab="' + tab + '"]').classList.toggle('active', tab === name);
  }
  document.getElementById('map').classList.toggle('favorites-view', name === 'favorites');   // style.css fades the others
  let loading = null;
  if (name === 'favorites') loading = showFavorites(true);
  if (name === 'maintenance') loading = showMaintenance();
  if (name === 'profile') loading = showProfile();
  if (loading) {
    loading.catch(showError);
  }
}

// Your favorite fountains, nearest first. fitMap: move the map to show them and You.
async function showFavorites(fitMap) {
  const params = new URLSearchParams({ lat: you.lat, lng: you.lng });
  const answer = await getJson('/api/favorites?' + params);
  const tab = fill('favorites-tab', answer);
  const list = tab.querySelector('.favorites');
  for (const fountain of answer.favorites) {
    const item = fill('favorite-item', fountain);
    const row = item.querySelector('li');
    row.onclick = function () {
      map.setView([fountain.lat, fountain.lng], 19);
      markers[fountain.id].openTooltip();
    };
    item.querySelector('.go-btn').onclick = function (event) {
      event.stopPropagation();
      showRoute(fountain.id, row.querySelector('.route-text')).catch(showError);
    };
    item.querySelector('.open-btn').onclick = function (event) {
      event.stopPropagation();
      showStation(fountain.id).catch(showError);
    };
    list.append(item);
  }
  document.getElementById('tab-favorites').replaceChildren(tab);
  if (fitMap) {
    map.fitBounds(answer.bounds);
  }
}

async function showMaintenance() {
  const answer = await getJson('/api/maintenance');
  const tab = fill('maintenance-tab', answer);
  const list = tab.querySelector('.issues');
  for (const issue of answer.issues) {
    const item = fill('issue-item', issue);
    item.querySelector('.fix-btn').onclick = function () { markFixed(issue.stationId).catch(showError); };
    list.append(item);
  }
  document.getElementById('tab-maintenance').replaceChildren(tab);
}

// Admins only
async function markFixed(stationId) {
  const answer = await postForm('/api/fix', { id: stationId });
  say(answer.message);
  await showMaintenance();
  await loadStations();
}

async function showProfile() {
  const profile = await getJson('/api/profile');
  const tab = fill('profile-tab', profile);
  const form = tab.querySelector('#pref-form');
  setForm(form, profile.preferences);
  form.onsubmit = function (event) {
    event.preventDefault();
    savePreferences(form).catch(showError);
  };
  document.getElementById('tab-profile').replaceChildren(tab);
}

async function savePreferences(form) {
  const answer = await postForm('/api/preferences', new FormData(form));
  setForm(document.getElementById('find-form'), answer.user.preferences);
  say(answer.message);
}

start().catch(showError);
