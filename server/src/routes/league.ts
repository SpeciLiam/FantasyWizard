import { Router } from "express";
import { z } from "zod";
import { sleeper } from "../upstream/sleeper.js";
import type { CacheClient } from "../cache/cacheClient.js";

const r = Router();

// Types
const UserZ = z.object({
  user_id: z.string(),
  display_name: z.string(),
  avatar: z.string().nullable().optional(),
});
const UsersZ = z.array(UserZ);

// GET /api/league/:leagueId/users
import type { Request, Response } from "express";

r.get("/league/:leagueId/users", async (req: Request, res: Response) => {
  const { leagueId } = req.params;
  const key = `cache:v1:league:${leagueId}:users`;
  const ttlMs = 12 * 60 * 60 * 1000;
  const swrMs = 6 * 60 * 60 * 1000;

  const cache: CacheClient = req.app.locals.cache;

  try {
    const { data, cacheStatus } = await cache.getOrFetch(
      key,
      { ttlMs, swrMs },
      async () => {
        const raw = await sleeper(`/v1/league/${leagueId}/users`);
        const users = UsersZ.parse(raw);
        return users.map(u => ({
          userId: u.user_id,
          displayName: u.display_name,
          avatar: u.avatar ?? undefined,
          avatarUrl: u.avatar ? `https://sleepercdn.com/avatars/thumbs/${u.avatar}` : undefined,
        }));
      }
    );
    res.setHeader("x-cache-key", key);
    res.setHeader("x-cache-status", cacheStatus);
    res.setHeader("cache-control", "public, max-age=60");
    res.json(data);
  } catch (e: any) {
    res.status(502).json({ error: "upstream_failed", detail: e?.message });
  }
});

export default r;
