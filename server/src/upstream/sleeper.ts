import { fetch as undiciFetch, type RequestInit } from "undici";
import { SLEEPER_API } from "../env.js";

// Use Node's global AbortController, fallback for TS if not defined
declare const AbortController: {
  new (): AbortController;
  prototype: AbortController;
};

export async function sleeper(path: string, init?: RequestInit) {
  const url = `${SLEEPER_API}${path}`;
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 6000);
  try {
    const res = await undiciFetch(url, { ...init, signal: ctrl.signal });
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    return res.json();
  } finally {
    clearTimeout(t);
  }
}
