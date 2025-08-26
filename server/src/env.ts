import "dotenv/config";

export const PORT = parseInt(process.env.PORT || "8787", 10);

export const REDIS_URL = process.env.REDIS_URL || "";
export const SLEEPER_API = process.env.SLEEPER_API || "https://api.sleeper.app";
export const CACHE_BUST_SECRET = process.env.CACHE_BUST_SECRET || "";
export const FRONTEND_ORIGIN = process.env.FRONTEND_ORIGIN || "http://localhost:8000";
export const IN_SEASON = (process.env.IN_SEASON || "false").toLowerCase() === "true";

export function requireSecret(header: string | undefined) {
  if (!header || header !== CACHE_BUST_SECRET) {
    const err = new Error("Unauthorized");
    (err as any).status = 401;
    throw err;
  }
}
