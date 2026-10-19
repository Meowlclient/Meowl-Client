package com.lennart.nebula.tiers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

public class McPvpProvider implements TierProvider {
   private static final long REFRESH_MS = 3600000L;
   private static final int MAX_PAGES = 30;
   private final String id;
   private final String display;
   private final String versionParam;
   private final long refreshMs;
   private volatile Map<String, TierProfile> byUuid;
   private volatile long loadedAt;
   private CompletableFuture<Map<String, TierProfile>> loading;

   public McPvpProvider() {
      this("mcpvp", "MCPVP", "", 3600000L);
   }

   public McPvpProvider(String id, String display, String versionParam, long refreshMs) {
      this.id = id;
      this.display = display;
      this.versionParam = versionParam;
      this.refreshMs = refreshMs;
   }

   public static McPvpProvider legacy() {
      return new McPvpProvider("mctiers", "MCTiers", "&version=legacy", 21600000L);
   }

   @Override
   public String id() {
      return this.id;
   }

   @Override
   public String displayName() {
      return this.display;
   }

   @Override
   public CompletableFuture<Optional<TierProfile>> fetch(UUID uuid, String name) {
      String key = uuid.toString().replace("-", "");
      return name != null && name.matches("[A-Za-z0-9_]{1,16}")
         ? TierHttp.get("https://www.mcpvp.com/tiers/search?q=" + name + this.versionParam).thenApply(body -> {
            if (body == null) {
               return Optional.empty();
            } else {
               Map<String, TierProfile> found = new HashMap<>();
               JsonObject root = JsonParser.parseString(body).getAsJsonObject();
               if (root.has("players") && root.get("players").isJsonArray()) {
                  for (JsonElement el : root.getAsJsonArray("players")) {
                     this.parsePlayer(el.getAsJsonObject(), found);
                  }
               }

               TierProfile p = found.get(key);
               if (p == null && uuid.version() != 4) {
                  for (TierProfile candidate : found.values()) {
                     if (name.equalsIgnoreCase(candidate.name())) {
                        p = candidate;
                        break;
                     }
                  }
               }

               return p == null ? Optional.empty() : Optional.of(new TierProfile(p.system(), uuid, p.name(), p.rankings(), p.region(), p.points()));
            }
         })
         : this.table().thenApply(map -> {
            TierProfile p = map.get(key);
            return p == null ? Optional.empty() : Optional.of(new TierProfile(p.system(), uuid, p.name(), p.rankings(), p.region(), p.points()));
         });
   }

   private synchronized CompletableFuture<Map<String, TierProfile>> table() {
      if (this.byUuid != null && System.currentTimeMillis() - this.loadedAt < this.refreshMs) {
         return CompletableFuture.completedFuture(this.byUuid);
      } else {
         if (this.loading == null) {
            this.loading = this.loadPage(0, new HashMap<>()).whenComplete((map, error) -> {
               synchronized (this) {
                  if (error == null) {
                     this.byUuid = (Map<String, TierProfile>)map;
                     this.loadedAt = System.currentTimeMillis();
                  }

                  this.loading = null;
               }
            });
         }

         return this.loading;
      }
   }

   private CompletableFuture<Map<String, TierProfile>> loadPage(int page, Map<String, TierProfile> into) {
      return TierHttp.get("https://www.mcpvp.com/tiers/data?offset=" + page * 100 + this.versionParam).thenCompose(body -> {
         if (body == null) {
            return CompletableFuture.completedFuture(into);
         } else {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();

            for (JsonElement el : root.getAsJsonArray("players")) {
               this.parsePlayer(el.getAsJsonObject(), into);
            }

            boolean more = root.has("hasMore") && root.get("hasMore").getAsBoolean();
            return more && page + 1 < 30 ? this.loadPage(page + 1, into) : CompletableFuture.completedFuture(into);
         }
      });
   }

   private void parsePlayer(JsonObject p, Map<String, TierProfile> into) {
      if (p.has("uuid") && p.has("kitRanks")) {
         Map<String, String> titles = new HashMap<>();
         if (p.has("contributingKits")) {
            for (JsonElement k : p.getAsJsonArray("contributingKits")) {
               JsonObject kit = k.getAsJsonObject();
               if (kit.has("key") && kit.has("label")) {
                  titles.put(kit.get("key").getAsString(), kit.get("label").getAsString());
               }
            }
         }

         JsonObject retiredMap = p.has("kitRetired") && p.get("kitRetired").isJsonObject() ? p.getAsJsonObject("kitRetired") : new JsonObject();
         JsonObject peaks = p.has("kitPeakRanks") && p.get("kitPeakRanks").isJsonObject() ? p.getAsJsonObject("kitPeakRanks") : new JsonObject();
         List<TierEntry> entries = new ArrayList<>();

         for (Entry<String, JsonElement> e : p.getAsJsonObject("kitRanks").entrySet()) {
            if (!e.getValue().isJsonNull()) {
               String mode = e.getKey();
               boolean retired = retiredMap.has(mode) && retiredMap.get(mode).isJsonPrimitive() && retiredMap.get(mode).getAsBoolean();
               String peak = peaks.has(mode) && peaks.get(mode).isJsonPrimitive() ? peaks.get(mode).getAsString() : null;
               TierEntry entry = TierEntry.parse(mode, titles.getOrDefault(mode, TiersApiProvider.prettify(mode)), e.getValue().getAsString(), retired, peak);
               if (entry != null) {
                  entries.add(entry);
               }
            }
         }

         if (!entries.isEmpty()) {
            String uuid = p.get("uuid").getAsString().replace("-", "");
            String name = p.has("name") ? p.get("name").getAsString() : null;
            String region = p.has("region") && !p.get("region").isJsonNull() ? p.get("region").getAsString() : null;
            int points = p.has("points") ? p.get("points").getAsInt() : 0;
            into.put(uuid, new TierProfile(this.display, null, name, List.copyOf(entries), region, points));
         }
      }
   }
}
