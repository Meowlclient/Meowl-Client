package com.lennart.nebula.tiers;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class TierCache {
   private static final long TTL_MS = 1800000L;
   private static final long ERROR_TTL_MS = 30000L;
   private static final int MAX_ENTRIES = 2000;
   private final Map<String, TierCache.Result> entries = new ConcurrentHashMap<>();

   private static String key(String system, UUID uuid) {
      return system + ":" + uuid;
   }

   public TierCache.Result get(String system, UUID uuid) {
      return this.entries.get(key(system, uuid));
   }

   public boolean needsFetch(String system, UUID uuid) {
      TierCache.Result r = this.entries.get(key(system, uuid));
      if (r == null) {
         return true;
      } else {
         long age = System.currentTimeMillis() - r.time();

         return switch (r.state()) {
            case LOADING -> age > 30000L;
            case ERROR -> age > 30000L;
            default -> age > 1800000L;
         };
      }
   }

   public void put(String system, UUID uuid, TierCache.State state, TierProfile profile) {
      if (this.entries.size() >= 2000) {
         this.prune();
      }

      TierCache.Result old = this.entries.get(key(system, uuid));
      if (state == TierCache.State.ERROR && old != null && old.state() == TierCache.State.FOUND) {
         this.entries.put(key(system, uuid), new TierCache.Result(TierCache.State.FOUND, old.profile(), System.currentTimeMillis() - 1800000L + 30000L));
      } else {
         this.entries.put(key(system, uuid), new TierCache.Result(state, profile, System.currentTimeMillis()));
      }
   }

   public void invalidate(UUID uuid) {
      this.entries.keySet().removeIf(k -> k.endsWith(":" + uuid));
   }

   public void clear() {
      this.entries.clear();
   }

   private void prune() {
      long now = System.currentTimeMillis();
      Iterator<Entry<String, TierCache.Result>> it = this.entries.entrySet().iterator();

      while (it.hasNext()) {
         if (now - it.next().getValue().time() > 1800000L) {
            it.remove();
         }
      }

      if (this.entries.size() >= 2000) {
         this.entries.clear();
      }
   }

   public record Result(TierCache.State state, TierProfile profile, long time) {
      public Optional<TierProfile> profileOpt() {
         return Optional.ofNullable(this.profile);
      }
   }

   public static enum State {
      LOADING,
      FOUND,
      NONE,
      ERROR;
   }
}
