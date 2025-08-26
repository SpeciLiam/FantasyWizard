import * as Redis from "ioredis";
import { REDIS_URL } from "../env.js";

export const redis =
  REDIS_URL && REDIS_URL !== ""
    ? new Redis.Redis(REDIS_URL, {
        maxRetriesPerRequest: 2,
        enableOfflineQueue: false,
        lazyConnect: false,
        connectTimeout: 4000,
      })
    : null;
