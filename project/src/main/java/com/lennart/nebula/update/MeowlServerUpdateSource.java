package com.lennart.nebula.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.net.MeowlService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MeowlServerUpdateSource implements UpdateSource {
   private final Supplier<String> baseUrl;
   private final HttpClient http;

   public MeowlServerUpdateSource(Supplier<String> baseUrl, HttpClient http) {
      this.baseUrl = baseUrl;
      this.http = http;
   }

   static boolean secure(String url) {
      URI u = URI.create(url.trim());
      String host = u.getHost() == null ? "" : u.getHost();
      return "https".equalsIgnoreCase(u.getScheme()) || host.equals("localhost") || host.equals("127.0.0.1");
   }

   /** Minecraft-Version, damit der Server die passende Jar (1.21.11 oder 26.x) anbietet. */
   static String mcQuery() {
      return net.fabricmc.loader.api.FabricLoader.getInstance()
         .getModContainer("minecraft")
         .map(m -> "?mc=" + m.getMetadata().getVersion().getFriendlyString().replaceAll("[^0-9.]", ""))
         .orElse("");
   }

   @Override
   public String name() {
      return "Meowl Server";
   }

   @Override
   public CompletableFuture<Optional<UpdateInfo>> fetchLatest() {
      String base = this.baseUrl.get();
      if (base != null && MeowlService.isValidUrl(base) && secure(base)) {
         String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
         HttpRequest request = HttpRequest.newBuilder(URI.create(root + "/update/latest" + mcQuery()))
            .timeout(Duration.ofSeconds(10L))
            .header("User-Agent", "MeowlClient")
            .GET()
            .build();
         return this.http.sendAsync(request, BodyHandlers.ofString()).thenApply(r -> {
            if (r.statusCode() == 404) {
               return Optional.empty();
            } else if (r.statusCode() == 200 && r.body().length() <= 16384) {
               JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
               if (o.has("version") && o.has("sha256") && o.has("size")) {
                  Version v = Version.parse(o.get("version").getAsString());
                  String sha = o.get("sha256").getAsString().toLowerCase(Locale.ROOT);
                  long size = o.get("size").getAsLong();
                  if (v != null && sha.matches("[0-9a-f]{64}") && size > 0L) {
                     String changelog = o.has("changelog") && !o.get("changelog").isJsonNull() ? o.get("changelog").getAsString() : "";
                     URI download = URI.create(root + "/update/download/" + v.raw() + mcQuery());
                     return Optional.of(new UpdateInfo(v, download, sha, size, changelog));
                  } else {
                     return Optional.empty();
                  }
               } else {
                  return Optional.empty();
               }
            } else {
               throw new IllegalStateException("Update-Server antwortet mit HTTP " + r.statusCode());
            }
         });
      } else {
         return CompletableFuture.completedFuture(Optional.empty());
      }
   }
}
