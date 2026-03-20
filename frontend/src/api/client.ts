import axios from "axios";
import { clearAuthStorage, getAccessToken, getRefreshToken } from "../utils/authStorage";

export const api = axios.create({
  baseURL: "/api",
});

let refreshRequest: Promise<string> | null = null;

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token && !config.url?.startsWith("/auth/")) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
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
        refreshRequest = axios.post("/api/auth/refresh", { refreshToken }).then((response) => {
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

