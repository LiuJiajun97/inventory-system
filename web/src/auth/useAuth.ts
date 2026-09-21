// token + 用户信息管理
import { useEffect, useState } from "react";
import type { UserInfo } from "../types";
import { AUTH_CHANGED_EVENT } from "./MenuContext";

const TOKEN_KEY = "token";
const USER_KEY = "user";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

export function setAuth(token: string, user: UserInfo): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  // 通知菜单上下文重新拉取当前用户菜单/权限
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  // 退出时清掉菜单/权限缓存
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function useAuth() {
  const [user, setUser] = useState<UserInfo | null>(getUser());
  useEffect(() => {
    const onStorage = () => setUser(getUser());
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);
  return {
    user,
    setUser,
    isLoggedIn: !!getToken(),
  };
}



