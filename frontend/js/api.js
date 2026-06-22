/**
 * api.js — shared frontend API configuration
 *
 * Change BASE_URL to your production server when deploying.
 */

const BASE_URL = 'http://localhost:8080/api';

// ── Token helpers ────────────────────────────────────────────────
function getToken() {
  return localStorage.getItem('ecg_token');
}

function setToken(token) {
  localStorage.setItem('ecg_token', token);
}

function clearToken() {
  localStorage.removeItem('ecg_token');
  localStorage.removeItem('ecg_admin_email');
}

function getAdminEmail() {
  return localStorage.getItem('ecg_admin_email');
}

function setAdminEmail(email) {
  localStorage.setItem('ecg_admin_email', email);
}

// ── Auth guard — redirect to login if no token ───────────────────
function requireAuth() {
  if (!getToken()) {
    window.location.href = 'login.html';
  }
}

// ── Generic authenticated fetch ──────────────────────────────────
async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = {
    ...(options.headers || {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  // If the server returns 401 our token is expired — send back to login
  if (res.status === 401 || res.status === 403) {
    clearToken();
    window.location.href = 'login.html';
    return;
  }

  return res;
}
