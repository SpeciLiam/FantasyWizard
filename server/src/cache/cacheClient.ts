import * as Redis from "ioredis";
import { LRUCache } from "lru-cache";

type CacheEntry<T> = { data: T; storedAt: number; ttlMs: number; swrMs: number };
export type GetOpts = { ttlMs: number; swrMs: number; revalidate?: boolean };

import { logger } from "../utils/logger.js";

export class CacheClient {
  constructor(
    private redis: Redis.Redis | null,
    private memory: LRUCache<string, CacheEntry<any>>,
    private inFlight = new Map<string, Promise<any>>()
  ) {}

  async getOrFetch<T>(
    key: string,
    opts: GetOpts,
    fetcher: () => Promise<T>
  ): Promise<{ data: T; cacheStatus: "hit" | "stale" | "miss" | "revalidate" }> {
    const now = Date.now();

    // 1) Try Redis
    let entry: CacheEntry<T> | null = null;
    if (this.redis) {
      try {
        const raw = await this.redis.get(key);
        if (raw) {
          entry = JSON.parse(raw);
          logger.debug({ key }, "cache: redis hit");
        } else {
          logger.debug({ key }, "cache: redis miss");
        }
      } catch (e) {
        logger.warn({ key, err: e }, "cache: redis get error");
      }
    }
    // 2) Try memory fallback
    if (!entry) entry = this.memory.get(key) as CacheEntry<T> | null;

    const isFresh = (e: CacheEntry<T>) => now - e.storedAt <= e.ttlMs;
    const withinSwr = (e: CacheEntry<T>) => now - e.storedAt <= (e.ttlMs + e.swrMs);

    if (entry) {
      if (isFresh(entry)) {
        return { data: entry.data, cacheStatus: "hit" };
      }
      if (withinSwr(entry)) {
        // background revalidate
        this.revalidate(key, opts, fetcher).catch(()=>{});
        return { data: entry.data, cacheStatus: "stale" };
      }
      // fully expired → fall through to fetch
    }

    // dedupe concurrent
    if (this.inFlight.has(key)) {
      const data = await this.inFlight.get(key)!;
      return { data, cacheStatus: "revalidate" };
    }

    const p = (async () => {
      const data = await fetcher();
      const newEntry: CacheEntry<T> = { data, storedAt: now, ttlMs: opts.ttlMs, swrMs: opts.swrMs };
      this.memory.set(key, newEntry);
      logger.debug({ key }, "cache: lru set");
      if (this.redis) {
        await this.redis.set(key, JSON.stringify(newEntry), "PX", opts.ttlMs + opts.swrMs);
        logger.debug({ key }, "cache: redis set");
      }
      return data;
    })();
    this.inFlight.set(key, p);
    try {
      const data = await p;
      return { data, cacheStatus: "miss" };
    } finally {
      this.inFlight.delete(key);
    }
  }

  private async revalidate<T>(key: string, opts: GetOpts, fetcher: () => Promise<T>) {
    if (this.inFlight.has(key)) return;
    const p = (async () => {
      const data = await fetcher();
      const entry: CacheEntry<T> = { data, storedAt: Date.now(), ttlMs: opts.ttlMs, swrMs: opts.swrMs };
      this.memory.set(key, entry);
      logger.debug({ key }, "cache: lru set (revalidate)");
      if (this.redis) {
        await this.redis.set(key, JSON.stringify(entry), "PX", opts.ttlMs + opts.swrMs);
        logger.debug({ key }, "cache: redis set (revalidate)");
      }
      return data;
    })();
    this.inFlight.set(key, p);
    try { await p; } finally { this.inFlight.delete(key); }
  }

  async del(key: string) {
    this.memory.delete(key);
    if (this.redis) await this.redis.del(key);
  }

  async delPrefix(prefix: string) {
    for (const k of this.memory.keys()) if (k.startsWith(prefix)) this.memory.delete(k);
    if (this.redis) {
      const stream = this.redis.scanStream({ match: `${prefix}*`, count: 100 });
      for await (const keys of stream) if (keys.length) await this.redis.del(keys);
    }
  }
}
