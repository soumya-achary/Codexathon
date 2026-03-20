import { create } from "zustand";
import type { AuthResponse, User } from "../types";
import { clearAuthStorage, getStoredUser, setAuthStorage } from "../utils/authStorage";

type AuthState = {
  user: User | null;
  hydrated: boolean;
  setAuth: (data: AuthResponse) => void;
  logout: () => void;
  hydrate: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  hydrated: false,
  setAuth: (data) => {
    setAuthStorage(data);
    set({ user: data.user, hydrated: true });
  },
  logout: () => {
    clearAuthStorage();
    set({ user: null, hydrated: true });
  },
  hydrate: () => {
    set({ user: getStoredUser(), hydrated: true });
  },
}));
