package com.lennart.nebula.tiers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class TiersApiProvider implements TierProvider {
   private final String id;
   private final String name;
   private final String profileUrl;
   private final boolean dashedUuid;
   private final String modeListUrl;
   private String nameUrl;
   private final Map<String, String> modeTitles = new ConcurrentHashMap<>();
   private volatile boolean modesRequested;

   public TiersApiProvider(String id, String name, String profileUrl, boolean dashedUuid, String modeListUrl) {
      this.id = id;
      this.name = name;
      this.profileUrl = profileUrl;
      this.dashedUuid = dashedUuid;
      this.modeListUrl = modeListUrl;
   }

   public TiersApiProvider withNameFallback(String url) {
      this.nameUrl = url;
      return this;
   }

   @Override
   public String id() {
      return this.id;
   }

   @Override
   public String displayName() {
      return this.name;
   }

   @Override
   public CompletableFuture<Optional<TierProfile>> fetch(UUID uuid, String playerName) {
      this.loadModeTitles();
      String key = this.dashedUuid ? uuid.toString() : uuid.toString().replace("-", "");
      CompletableFuture<Optional<TierProfile>> byUuid = TierHttp.get(this.profileUrl + key).thenApply(body -> this.toProfile(uuid, body));
      boolean offlineUuid = uuid.version() != 4;
      return this.nameUrl != null && offlineUuid && playerName != null && playerName.matches("[A-Za-z0-9_]{1,16}")
         ? byUuid.<CompletableFuture<? super Optional<TierProfile>>>handle(
               (result, error) -> error == null && result.isPresent()
                  ? CompletableFuture.completedFuture(result)
                  : TierHttp.get(this.nameUrl + playerName).thenApply(body -> {
                     Optional<TierProfile> p = this.toProfile(uuid, body);
                     if (p.isEmpty() && error != null) {
                        throw new CompletionException(error);
                     } else {
                        return p;
                     }
                  })
            )
            .thenCompose(f -> (CompletionStage<Optional<TierProfile>>)f)
         : byUuid;
   }

   private Optional<TierProfile> toProfile(UUID uuid, String body) {
      if (body == null) {
         return Optional.empty();
      } else {
         JsonElement root = JsonParser.parseString(body);
         if (!root.isJsonObject()) {
            return Optional.empty();
         } else {
            JsonObject o = root.getAsJsonObject();
            if (o.has("uuid") && o.get("uuid").isJsonPrimitive() && uuid.version() == 4) {
               String got = o.get("uuid").getAsString().replace("-", "");
               if (!got.equalsIgnoreCase(uuid.toString().replace("-", ""))) {
                  return Optional.empty();
               }
            }

            return this.parse(uuid, o);
         }
      }
   }

   private Optional<TierProfile> parse(UUID uuid, JsonObject o) {
      if (o.has("rankings") && o.get("rankings").isJsonObject()) {
         List<TierEntry> entries = new ArrayList<>();

         for (Entry<String, JsonElement> e : o.getAsJsonObject("rankings").entrySet()) {
            if (e.getValue().isJsonObject()) {
               JsonObject r = e.getValue().getAsJsonObject();
               if (r.has("tier") && !r.get("tier").isJsonNull() && r.has("pos") && !r.get("pos").isJsonNull()) {
                  int tier = r.get("tier").getAsInt();
                  int pos = r.get("pos").getAsInt();
                  boolean retired = r.has("retired") && !r.get("retired").isJsonNull() && r.get("retired").getAsBoolean();
                  String peak = null;
                  if (r.has("peak_tier") && !r.get("peak_tier").isJsonNull() && r.has("peak_pos") && !r.get("peak_pos").isJsonNull()) {
                     peak = (r.get("peak_pos").getAsInt() == 0 ? "HT" : "LT") + r.get("peak_tier").getAsInt();
                  }

                  if (tier >= 1 && tier <= 5) {
                     entries.add(new TierEntry(e.getKey(), this.title(e.getKey()), tier, pos == 0 ? "HT" : "LT", retired, peak));
                  }
               }
            }
         }

         String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null;
         String region = o.has("region") && !o.get("region").isJsonNull() ? o.get("region").getAsString() : null;
         int points = o.has("points") && !o.get("points").isJsonNull() ? o.get("points").getAsInt() : 0;
         return entries.isEmpty() && points == 0 ? Optional.empty() : Optional.of(new TierProfile(this.name, uuid, name, List.copyOf(entries), region, points));
      } else {
         return Optional.empty();
      }
   }

   private String title(String mode) {
      String t = this.modeTitles.get(mode);
      return t != null ? t : prettify(mode);
   }

   static String prettify(String mode) {
      return switch (mode) {
         case "nethop", "neth_pot", "netherite-pot" -> "Neth Pot";
         case "dia_smp", "diamond-smp" -> "Dia SMP";
         case "dia_crystal" -> "Dia Crystal";
         case "og_vanilla" -> "OG Vanilla";
         case "uhc" -> "UHC";
         case "smp" -> "SMP";
         default -> {
            String s = mode.replace('_', ' ').replace('-', ' ');
            StringBuilder b = new StringBuilder();

            for (String part : s.split(" ")) {
               if (!part.isEmpty()) {
                  b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT)).append(' ');
               }
            }

            yield b.toString().trim();
         }
      };
   }

   private void loadModeTitles() {
      if (this.modeListUrl != null && !this.modesRequested) {
         this.modesRequested = true;
         TierHttp.get(this.modeListUrl).thenAccept(body -> {
            if (body != null) {
               JsonElement root = JsonParser.parseString(body);
               if (root.isJsonObject()) {
                  for (Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                     if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("title")) {
                        this.modeTitles.put(e.getKey(), e.getValue().getAsJsonObject().get("title").getAsString());
                     }
                  }
               }
            }
         }).exceptionally(t -> null);
      }
   }
}
