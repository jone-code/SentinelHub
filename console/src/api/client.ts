import axios from 'axios';

const TOKEN_KEY = 'sentinelhub_token';

export const api = axios.create({
  baseURL: '/api/admin/v1',
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  },
);

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageData<T> {
  items: T[];
  total: number;
  page: number;
  page_size: number;
}
