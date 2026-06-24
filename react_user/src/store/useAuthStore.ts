import { create } from 'zustand';
import type { User } from '@guga-reading/types';
import { useApiAuth } from '@guga-reading/shares';
import axios from 'axios';

interface TokenData {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

function loadToken(): TokenData | null {
  try {
    const raw = localStorage.getItem('token');
    if (raw) return JSON.parse(raw);
  } catch (error) {
    console.error(error);
  }
  return null;
}

function saveToken(data: TokenData) {
  localStorage.setItem('token', JSON.stringify(data));
}

function removeToken() {
  localStorage.removeItem('token');
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: string | null;
  user: User | null;
  redictUrl: string | null;
  isRemeber: boolean;

  // computed-like getters (functions)
  isLogin: () => boolean;
  getUser: () => User | null;
  getAccessToken: () => string | null;
  getRefreshToken: () => string | null;
  getTokenType: () => string | null;
  getRedictUrl: () => string | null;

  // actions
  initial: () => Promise<boolean>;
  setRemeber: (isRemeber: boolean) => void;
  setToken: (
    accessToken: string,
    refreshToken: string,
    tokenType: string,
  ) => void;
  setUser: (user: User) => void;
  clearToken: () => void;
  clearUser: () => void;
  tokenRefresh: () => Promise<void>;
  setRedictUrl: (url: string | null) => void;
  clearRedictUrl: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  tokenType: null,
  user: null,
  redictUrl: null,
  isRemeber: true,

  isLogin: () => get().user !== null,
  getUser: () => get().user,
  getAccessToken: () => get().accessToken,
  getRefreshToken: () => get().refreshToken,
  getTokenType: () => get().tokenType,
  getRedictUrl: () => get().redictUrl,

  async initial() {
    const token = loadToken();
    if (token) {
      get().setToken(token.accessToken, token.refreshToken, token.tokenType);
      const { success, data } = await useApiAuth.authMe(
        get().tokenType!,
        get().accessToken!,
      );
      if (success) {
        get().setUser(data!);
        axios.defaults.headers.common['Authorization'] =
          `${get().tokenType} ${get().accessToken!}`;
        return true;
      }
      const result = await useApiAuth.refreshToken(
        get().tokenType!,
        get().refreshToken!,
      );
      if (result.success) {
        get().setToken(
          result.data!.accessToken,
          result.data!.refreshToken,
          result.data!.tokenType,
        );
        get().setUser(result.data!.user);
        axios.defaults.headers.common['Authorization'] =
          `${get().tokenType} ${get().accessToken!}`;
        return true;
      }
      return false;
    }
    return false;
  },

  setRemeber(isRemeber: boolean) {
    set({ isRemeber });
  },

  setToken(accessToken: string, refreshToken: string, tokenType: string) {
    set({ accessToken, refreshToken, tokenType });
    if (!get().isRemeber) {
      removeToken();
      return;
    }
    saveToken({ accessToken, refreshToken, tokenType });
  },

  setUser(user: User) {
    set({ user });
  },

  clearToken() {
    set({ accessToken: '', refreshToken: '', tokenType: '' });
    removeToken();
  },

  clearUser() {
    set({ user: null });
  },

  async tokenRefresh() {
    const { success, data, message } = await useApiAuth.refreshToken(
      get().tokenType!,
      get().refreshToken!,
    );
    if (success) {
      get().setToken(data!.accessToken, data!.refreshToken, data!.tokenType);
    } else {
      console.error(message);
    }
  },

  setRedictUrl(url: string | null) {
    set({ redictUrl: url });
  },

  clearRedictUrl() {
    set({ redictUrl: null });
  },
}));
