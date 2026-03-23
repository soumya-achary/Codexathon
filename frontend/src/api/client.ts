import axios from "axios";
import { clearAuthStorage, getAccessToken, getRefreshToken } from "../utils/authStorage";

const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "/api";
const apiBaseUrl = rawApiBaseUrl.replace(/\/$/, "");
const isRelativeApiBaseUrl = /^\/(?!\/)/.test(apiBaseUrl);
const isAzureStaticAppsHost = typeof window !== "undefined" && window.location.hostname.endsWith(".azurestaticapps.net");
const missingAzureApiConfigMessage = "API base URL is not configured for Azure Static Web Apps. Set VITE_API_BASE_URL to your backend App Service URL ending in /api, for example https://YOUR_BACKEND_APP.azurewebsites.net/api.";

function getAzureApiConfigError() {
  if (!import.meta.env.PROD) return null;
  if (!isAzureStaticAppsHost) return null;
  if (!isRelativeApiBaseUrl) return null;
  return new Error(missingAzureApiConfigMessage);
}

export const api = axios.create({
  baseURL: apiBaseUrl,
});

let refreshRequest: Promise<string> | null = null;

api.interceptors.request.use((config) => {
  const configError = getAzureApiConfigError();
  if (configError) throw configError;
  const token = getAccessToken();
  if (token && !config.url?.startsWith("/auth/")) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error instanceof Error && error.message === missingAzureApiConfigMessage) throw error;
    const originalRequest = error.config as (typeof error.config & { _retry?: boolean }) | undefined;
    if (!originalRequest || originalRequest.url?.startsWith("/auth/")) throw error;
    if (![401, 403].includes(error.response?.status)) throw error;
    if (originalRequest._retry) {
      clearAuthStorage();
      throw error;
    }
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      clearAuthStorage();
      throw error;
    }
    originalRequest._retry = true;
    try {
      if (!refreshRequest) {
        refreshRequest = axios.post(`${apiBaseUrl}/auth/refresh`, { refreshToken }).then((response) => {
          localStorage.setItem("finance_access_token", response.data.accessToken);
          localStorage.setItem("finance_refresh_token", response.data.refreshToken);
          return response.data.accessToken as string;
        }).finally(() => {
          refreshRequest = null;
        });
      }
      const nextAccessToken = await refreshRequest;
      originalRequest.headers = originalRequest.headers ?? {};
      originalRequest.headers.Authorization = `Bearer ${nextAccessToken}`;
      return api(originalRequest);
    } catch (refreshError) {
      clearAuthStorage();
      throw refreshError;
    }
  },
);