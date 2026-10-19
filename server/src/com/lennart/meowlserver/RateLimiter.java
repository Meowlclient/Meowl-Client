package com.lennart.meowlserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
   private final Map<String, RateLimiter.Bucket> buckets = new ConcurrentHashMap<>();

   public void check(String user, String action, double capacity, double perSecond) {
      RateLimiter.Bucket b = this.buckets.computeIfAbsent(user + "|" + action, k -> {
         RateLimiter.Bucket n = new RateLimiter.Bucket();
         n.tokens = capacity;
         return n;
      });
      synchronized (b) {
         long now = System.nanoTime();
         b.tokens = Math.min(capacity, b.tokens + (now - b.last) / 1.0E9 * perSecond);
         b.last = now;
         if (b.tokens < 1.0) {
            throw new ApiException(429, "Zu viele Anfragen (" + action + ")");
         } else {
            b.tokens--;
         }
      }
   }

   public void cleanup() {
      long cutoff = System.nanoTime() - 600000000000L;
      this.buckets.entrySet().removeIf(e -> e.getValue().last < cutoff);
   }

   private static final class Bucket {
      double tokens;
      long last = System.nanoTime();
   }
}
