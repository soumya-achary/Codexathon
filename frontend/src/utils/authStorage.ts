import type { AuthResponse, User } from "../types";

const ACCESS = "finance_access_token";
const REFRESH = "finance_refresh_token";
const USER = "finance_user";

export function setAuthStorage(data: AuthResponse) {
  localStorage.setItem(ACCESS, data.accessToken);
  localStorage.setItem(REFRESH, data.refreshToken);
  localStorage.setItem(USER, JSON.stringify(data.user));
}

export function clearAuthStorage() {
  localStorage.removeItem(ACCESS);
  localStorage.removeItem(REFRESH);
  localStorage.removeItem(USER);
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH);
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    clearAuthStorage();
    return null;
  }
}
