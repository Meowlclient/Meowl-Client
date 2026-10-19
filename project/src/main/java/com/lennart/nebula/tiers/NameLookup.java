package com.lennart.nebula.tiers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class NameLookup {
   private static final String CRAFTY = "https://api.crafty.gg/api/v2/";
   private static final long TTL_MS = 600000L;
   private final Map<String, NameLookup.Cached<List<NameLookup.Candidate>>> searches = new ConcurrentHashMap<>();
   private final Map<UUID, NameLookup.Cached<List<NameLookup.NameChange>>> histories = new ConcurrentHashMap<>();

   public CompletableFuture<List<NameLookup.Candidate>> search(String query) {
      String q = query.trim();
      UUID asUuid = parseUuid(q);
      if (asUuid != null) {
         return this.history(asUuid).thenApply(h -> List.of(new NameLookup.Candidate(asUuid, h.isEmpty() ? q : h.get(0).name(), false)));
      } else if (!q.matches("[A-Za-z0-9_]{1,16}")) {
         return CompletableFuture.completedFuture(List.of());
      } else {
         String key = q.toLowerCase(Locale.ROOT);
         NameLookup.Cached<List<NameLookup.Candidate>> c = this.searches.get(key);
         if (c != null && System.currentTimeMillis() - c.time() < 600000L) {
            return CompletableFuture.completedFuture(c.value());
         } else {
            CompletableFuture<Optional<NameLookup.Candidate>> current = TierHttp.get("https://api.mojang.com/users/profiles/minecraft/" + q)
               .thenApply(body -> {
                  if (body == null) {
                     return Optional.<NameLookup.Candidate>empty();
                  } else {
                     JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                     return Optional.of(new NameLookup.Candidate(parseUuid(o.get("id").getAsString()), o.get("name").getAsString(), false));
                  }
               })
               .exceptionally(e -> Optional.<NameLookup.Candidate>empty());
            CompletableFuture<List<NameLookup.Candidate>> old = TierHttp.get("https://api.crafty.gg/api/v2/usernames/" + q).thenApply(body -> {
               List<NameLookup.Candidate> list = new ArrayList<>();
               if (body == null) {
                  return list;
               } else {
                  JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
                  if (data == null) {
                     return list;
                  } else {
                     if (data.has("current_player") && data.get("current_player").isJsonObject()) {
                        JsonObject p = data.getAsJsonObject("current_player");
                        list.add(new NameLookup.Candidate(parseUuid(p.get("uuid").getAsString()), p.get("username").getAsString(), false));
                     }

                     if (data.has("historical_players") && data.get("historical_players").isJsonArray()) {
                        for (JsonElement el : data.getAsJsonArray("historical_players")) {
                           JsonObject p = el.getAsJsonObject();
                           list.add(new NameLookup.Candidate(parseUuid(p.get("uuid").getAsString()), p.get("username").getAsString(), true));
                           if (list.size() >= 8) {
                              break;
                           }
                        }
                     }

                     return list;
                  }
               }
            }).exceptionally(e -> List.of());
            return current.thenCombine(old, (cur, hist) -> {
               Map<UUID, NameLookup.Candidate> merged = new LinkedHashMap<>();
               cur.ifPresent(c2 -> merged.put(c2.uuid(), c2));

               for (NameLookup.Candidate h : hist) {
                  if (h.uuid() != null) {
                     merged.putIfAbsent(h.uuid(), h);
                  }
               }

               merged.remove(null);
               List<NameLookup.Candidate> result = List.copyOf(merged.values());
               this.searches.put(key, new NameLookup.Cached<>(result, System.currentTimeMillis()));
               return result;
            });
         }
      }
   }

   public CompletableFuture<List<NameLookup.NameChange>> history(UUID uuid) {
      NameLookup.Cached<List<NameLookup.NameChange>> c = this.histories.get(uuid);
      return c != null && System.currentTimeMillis() - c.time() < 600000L
         ? CompletableFuture.completedFuture(c.value())
         : TierHttp.get("https://api.crafty.gg/api/v2/players/" + uuid).thenApply(body -> {
            List<NameLookup.NameChange> list = new ArrayList<>();
            if (body != null) {
               JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
               if (data != null && data.has("usernames") && data.get("usernames").isJsonArray()) {
                  List<String> seen = new ArrayList<>();

                  for (JsonElement el : data.getAsJsonArray("usernames")) {
                     JsonObject u = el.getAsJsonObject();
                     if (!u.has("hidden") || !u.get("hidden").getAsBoolean()) {
                        String name = u.get("username").getAsString();
                        String at = u.has("changed_at") && !u.get("changed_at").isJsonNull() ? u.get("changed_at").getAsString() : "";
                        if (!seen.contains(name.toLowerCase(Locale.ROOT)) || list.size() <= 0 || !list.get(list.size() - 1).name().equalsIgnoreCase(name)) {
                           seen.add(name.toLowerCase(Locale.ROOT));
                           list.add(new NameLookup.NameChange(name, at.length() >= 10 ? at.substring(0, 10) : at));
                        }
                     }
                  }
               }
            }

            this.histories.put(uuid, new NameLookup.Cached<>(List.copyOf(list), System.currentTimeMillis()));
            return List.copyOf(list);
         }).exceptionally(e -> List.of());
   }

   public static UUID parseUuid(String s) {
      if (s == null) {
         return null;
      } else {
         String t = s.trim().replace("-", "");
         return !t.matches("[0-9a-fA-F]{32}") ? null : UUID.fromString(t.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
      }
   }

   private record Cached<T>(T value, long time) {
   }

   public record Candidate(UUID uuid, String name, boolean viaOldName) {
   }

   public record NameChange(String name, String changedAt) {
   }
}
