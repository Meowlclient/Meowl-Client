package com.lennart.nebula.images;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.net.MeowlService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ImageManager {
   private final Path localFile;
   private final Map<String, PlacedImage> images = new ConcurrentHashMap<>();
   private String server = "";
   private final ImageSyncManager sync;

   public ImageManager(Path localFile) {
      this.localFile = localFile;
      this.sync = new ImageSyncManager(this);
   }

   public Collection<PlacedImage> all() {
      return this.images.values();
   }

   public String server() {
      return this.server;
   }

   public void tick() {
      String now = MeowlService.currentServerId();
      if (!now.equals(this.server)) {
         this.server = now;
         this.images.clear();
         this.loadLocal();
         this.sync.refresh();
      }
   }

   void putShared(PlacedImage image) {
      if (image.server().equals(this.server)) {
         this.images.put(image.id(), image);
      }
   }

   void removeShared(String id) {
      PlacedImage image = this.images.get(id);
      if (image != null && !image.local()) {
         this.images.remove(id);
      }
   }

   void replaceShared(List<PlacedImage> list) {
      this.images.values().removeIf(ix -> !ix.local());

      for (PlacedImage i : list) {
         this.putShared(i);
      }
   }

   public void place(PlacedImage draft, Consumer<String> result) {
      if (MeowlService.get().isConnected()) {
         this.sync.upload(draft, result);
      } else {
         PlacedImage local = new PlacedImage(
            "local-" + UUID.randomUUID(), draft.url(), this.server, draft.dim(), draft.pos(), draft.facing(), draft.w(), draft.h(), draft.creator(), true
         );
         // nur ein Bild pro Spieler: ältere eigene lokale Bilder entfernen
         this.images.values().removeIf(PlacedImage::local);
         this.images.put(local.id(), local);
         this.saveLocal();
         result.accept("Bild platziert (nur lokal – ohne Meowl Server sehen es andere nicht)");
      }
   }

   public void remove(PlacedImage image, Consumer<String> result) {
      if (image.local()) {
         this.images.remove(image.id());
         this.saveLocal();
         result.accept("Bild entfernt");
      } else {
         this.sync.delete(image, result);
      }
   }

   private void loadLocal() {
      if (Files.exists(this.localFile) && !this.server.isEmpty()) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(this.localFile, StandardCharsets.UTF_8)).getAsJsonObject();

            for (JsonElement el : root.has(this.server) ? root.getAsJsonArray(this.server) : new JsonArray()) {
               PlacedImage i = PlacedImage.fromJson(el.getAsJsonObject(), true);
               if (i != null) {
                  this.images.put(i.id(), i);
               }
            }
         } catch (IOException | RuntimeException var5) {
            NebulaClient.LOGGER.warn("Lokale Bilder konnten nicht geladen werden", (Throwable)var5);
         }
      }
   }

   private void saveLocal() {
      try {
         JsonObject root = Files.exists(this.localFile)
            ? JsonParser.parseString(Files.readString(this.localFile, StandardCharsets.UTF_8)).getAsJsonObject()
            : new JsonObject();
         JsonArray list = new JsonArray();

         for (PlacedImage i : this.images.values()) {
            if (i.local()) {
               list.add(i.toJson());
            }
         }

         root.add(this.server, list);
         Files.createDirectories(this.localFile.getParent());
         Files.writeString(this.localFile, new GsonBuilder().setPrettyPrinting().create().toJson((JsonElement)root), StandardCharsets.UTF_8);
      } catch (IOException | RuntimeException var51) {
         NebulaClient.LOGGER.warn("Lokale Bilder konnten nicht gespeichert werden", (Throwable)var51);
      }
   }

   public List<PlacedImage> snapshot() {
      return new ArrayList<>(this.images.values());
   }
}
