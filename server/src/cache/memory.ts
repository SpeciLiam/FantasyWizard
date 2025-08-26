import { LRUCache } from "lru-cache";

const maxSizeMB = 10; // Max memory LRU cache size (MB)
const maxSizeBytes = maxSizeMB * 1024 * 1024;

export const memoryCache = new LRUCache<string, any>({
  maxSize: maxSizeBytes,
  sizeCalculation: (value, key) => {
    try {
      return Buffer.byteLength(JSON.stringify(value)) + Buffer.byteLength(key);
    } catch {
      return 500;
    }
  },
  ttl: 12 * 60 * 60 * 1000, // Default TTL: 12h (per-entry override in client)
});

export default memoryCache;
