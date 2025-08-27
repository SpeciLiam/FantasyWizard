package com.sleeper.advisor.web;

import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/cache")
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173}")
public class CacheAdminController {
  private final CacheManager cacheManager;
  public CacheAdminController(CacheManager cacheManager) { this.cacheManager = cacheManager; }

  @PostMapping("/evict/{name}")
  public String evict(@PathVariable String name) {
    var cache = cacheManager.getCache(name);
    if (cache != null) cache.clear();
    return "cleared:" + name;
  }

  @PostMapping("/clearAll")
  public String clearAll() {
    cacheManager.getCacheNames().forEach(n -> {
      var c = cacheManager.getCache(n);
      if (c != null) c.clear();
    });
    return "cleared:all";
  }
}
