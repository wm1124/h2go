// H2-Go sign-in page: sends the forms to Java (/api/login, /api/signup).

// Opens the map page when it worked, or shows Java's error message.
async function send(url, form) {
  const response = await fetch(url, { method: 'POST', body: new URLSearchParams(new FormData(form)) });
  const answer = await response.json();
  if (response.ok) {
    location.href = 'index.html';
  } else {
    const error = document.getElementById('error');
    error.textContent = answer.error;
    error.hidden = false;
  }
}

document.getElementById('login-form').onsubmit = function (event) {
  event.preventDefault();
  send('/api/login', event.target);
};

document.getElementById('signup-form').onsubmit = function (event) {
  event.preventDefault();
  send('/api/signup', event.target);
};

document.getElementById('show-signup').onclick = function (event) {
  event.preventDefault();
  document.getElementById('signup-form').hidden = false;
};
