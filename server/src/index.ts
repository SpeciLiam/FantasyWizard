import express from "express";
import cors from "cors";
import { createServer } from "http";
import { PORT, FRONTEND_ORIGIN } from "./env.js";
import { memoryCache } from "./cache/memory.js";
import { redis } from "./cache/redis.js";
import { CacheClient } from "./cache/cacheClient.js";
import { logger } from "./utils/logger.js";

const app = express();

app.use(express.json({ limit: "2mb" }));

// CORS
app.use(
  cors({
    origin: FRONTEND_ORIGIN,
    credentials: true,
    methods: ["GET", "POST", "OPTIONS"],
    allowedHeaders: ["Content-Type", "x-cache-bust-secret"],
  })
);

// Minimal in-memory token bucket rate limit: 60 req/min per IP
const tokens: Record<string, { ts: number; count: number }> = {};
import type { Request, Response, NextFunction } from "express";

app.use((req: Request, res: Response, next: NextFunction) => {
  const ip = req.ip ?? "anonymous";
  const now = Date.now();
  if (!tokens[ip] || now - tokens[ip].ts > 60_000) tokens[ip] = { ts: now, count: 0 };
  tokens[ip].count += 1;
  if (tokens[ip].count > 60) return res.status(429).json({ error: "rate_limited" });
  next();
});

// Attach cache client and logger on app locals
const cache = new CacheClient(redis, memoryCache);
app.locals.cache = cache;
app.locals.logger = logger;

app.get("/api/ping", (req: Request, res: Response) => res.json({ pong: true }));

import leagueRoutes from "./routes/league.js";
// TODO: mount ./routes/cache

app.use("/api", leagueRoutes);

app.use((err: any, req: Request, res: Response, next: NextFunction) => {
  logger.error({ err, path: req.path }, "Unhandled server error");
  res.status(err.status || 500).json({ error: err.error || "unknown", detail: err.message });
});

createServer(app).listen(PORT, () => {
  logger.info(`Sleeper proxy server running on http://localhost:${PORT}`);
});

export { app };
