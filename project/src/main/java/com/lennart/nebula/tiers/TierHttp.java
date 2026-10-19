package com.lennart.nebula.tiers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class TierHttp {
   static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NORMAL).build();
   private static final int MAX_BODY = 2097152;

   static CompletableFuture<String> get(String url) {
      return getOnce(url).exceptionallyCompose(error -> {
         Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
         return cause instanceof IOException ? getOnce(url) : CompletableFuture.failedFuture(cause);
      });
   }

   private static CompletableFuture<String> getOnce(String url) {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(10L))
         .header("User-Agent", "MeowlClient")
         .header("Accept", "application/json")
         .GET()
         .build();
      return CLIENT.sendAsync(request, BodyHandlers.ofString()).thenApply(response -> {
         int code = response.statusCode();
         if (code == 404 || code == 422) {
            return null;
         } else if (code != 200) {
            throw new IllegalStateException("HTTP " + code + " von " + url);
         } else {
            String body = response.body();
            if (body.length() > 2097152) {
               throw new IllegalStateException("Antwort zu groß");
            } else {
               return body;
            }
         }
      });
   }

   private TierHttp() {
   }
}
