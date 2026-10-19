package com.lennart.nebula.tiers;

import com.lennart.nebula.NebulaClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TierManager {
   private static final int MAX_IN_FLIGHT = 6;
   private static final int FAILS_UNTIL_PAUSE = 3;
   private final List<TierProvider> providers = List.of(
      new McPvpProvider(),
      McPvpProvider.legacy(),
      new TiersApiProvider("pvptiers", "PvPTiers", "https://pvptiers.com/api/profile/", false, null),
      new TiersApiProvider("subtiers", "Subtiers", "https://subtiers.net/api/v2/profile/", true, "https://subtiers.net/api/v2/mode/list")
         .withNameFallback("https://subtiers.net/api/v2/profile/by-name/")
   );
   private final TierCache cache = new TierCache();
   private final AtomicInteger inFlight = new AtomicInteger();
   private final Map<String, TierManager.Health> health = new ConcurrentHashMap<>();

   public List<TierProvider> providers() {
      return this.providers;
   }

   public TierProvider byId(String id) {
      for (TierProvider p : this.providers) {
         if (p.id().equalsIgnoreCase(id)) {
            return p;
         }
      }

      return null;
   }

   public TierProvider byName(String displayName) {
      for (TierProvider p : this.providers) {
         if (p.displayName().equalsIgnoreCase(displayName) || p.id().equalsIgnoreCase(displayName) || label(p).equalsIgnoreCase(displayName)) {
            return p;
         }
      }

      return this.providers.get(0);
   }

   public static String label(TierProvider p) {
      String var1 = p.id();

      return switch (var1) {
         case "mcpvp" -> "MCPVP (Donut, aktuell)";
         case "mctiers" -> "MCTiers (Legacy)";
         default -> p.displayName();
      };
   }

   public boolean isOffline(TierProvider provider) {
      TierManager.Health h = this.health.get(provider.id());
      return h != null && System.currentTimeMillis() < h.pausedUntil;
   }

   public TierCache.Result get(TierProvider provider, UUID uuid, String name) {
      if (uuid == null) {
         return null;
      } else {
         if (this.cache.needsFetch(provider.id(), uuid) && this.inFlight.get() < 6) {
            if (this.isOffline(provider)) {
               TierCache.Result old = this.cache.get(provider.id(), uuid);
               if (old == null || old.state() == TierCache.State.LOADING) {
                  this.cache.put(provider.id(), uuid, TierCache.State.ERROR, null);
               }
            } else {
               this.request(provider, uuid, name);
            }
         }

         return this.cache.get(provider.id(), uuid);
      }
   }

   private void request(TierProvider provider, UUID uuid, String name) {
      TierCache.Result old = this.cache.get(provider.id(), uuid);
      if (old == null) {
         this.cache.put(provider.id(), uuid, TierCache.State.LOADING, null);
      }

      this.inFlight.incrementAndGet();

      try {
         provider.fetch(uuid, name).whenComplete((result, error) -> {
            this.inFlight.decrementAndGet();
            if (error != null) {
               this.failed(provider, error);
               this.cache.put(provider.id(), uuid, TierCache.State.ERROR, null);
            } else {
               this.health.remove(provider.id());
               if (result.isPresent()) {
                  this.cache.put(provider.id(), uuid, TierCache.State.FOUND, result.get());
               } else {
                  this.cache.put(provider.id(), uuid, TierCache.State.NONE, null);
               }
            }
         });
      } catch (RuntimeException var6) {
         this.inFlight.decrementAndGet();
         this.failed(provider, var6);
         this.cache.put(provider.id(), uuid, TierCache.State.ERROR, null);
      }
   }

   private void failed(TierProvider provider, Throwable error) {
      TierManager.Health h = this.health.computeIfAbsent(provider.id(), k -> new TierManager.Health());
      synchronized (h) {
         h.fails++;
         if (h.fails >= 3) {
            int step = Math.min(5, h.fails - 3);
            long pause = Math.min(600000L, 30000L << step);
            h.pausedUntil = System.currentTimeMillis() + pause;
            NebulaClient.LOGGER.warn("Tiers: {} antwortet nicht ({}), Pause {} s", provider.displayName(), error.toString(), pause / 1000L);
         } else {
            NebulaClient.LOGGER.debug("Tiers: {} fehlgeschlagen: {}", provider.displayName(), error.toString());
         }
      }
   }

   public Optional<TierProfile> profile(TierProvider provider, UUID uuid, String name) {
      TierCache.Result r = this.get(provider, uuid, name);
      return r == null ? Optional.empty() : r.profileOpt();
   }

   public void invalidate(UUID uuid) {
      this.cache.invalidate(uuid);
   }

   public static String tag(TierProvider p) {
      String id = p.id();

      return switch (id) {
         case "mcpvp" -> "MP";
         case "mctiers" -> "MC";
         case "pvptiers" -> "PT";
         case "subtiers" -> "ST";
         default -> p.id().substring(0, Math.min(2, p.id().length())).toUpperCase(Locale.ROOT);
      };
   }

   public static int color(TierProvider p) {
      String id = p.id();

      return switch (id) {
         case "mcpvp" -> -19641;
         case "mctiers" -> -3558530;
         case "pvptiers" -> -495247;
         case "subtiers" -> -11870592;
         default -> -7035976;
      };
   }

   public void clearCache() {
      this.cache.clear();
      this.health.clear();
   }

   private static final class Health {
      int fails;
      long pausedUntil;
   }
}
