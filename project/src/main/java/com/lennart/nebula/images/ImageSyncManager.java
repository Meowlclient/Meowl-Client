package com.lennart.nebula.images;

import com.google.gson.JsonElement;
import com.lennart.nebula.net.MeowlService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class ImageSyncManager {
   private final ImageManager manager;

   ImageSyncManager(ImageManager manager) {
      this.manager = manager;
      MeowlService service = MeowlService.get();
      service.onConnected(this::refresh);
      service.on("image_added", data -> {
         PlacedImage i = PlacedImage.fromJson(data, false);
         if (i != null) {
            manager.putShared(i);
         }
      });
      service.on("image_removed", data -> manager.removeShared(data.get("id").getAsString()));
   }

   public void refresh() {
      MeowlService service = MeowlService.get();
      if (service.isConnected() && !this.manager.server().isEmpty()) {
         service.get("/images").thenAccept(r -> {
            if (r.ok() && r.json() != null) {
               List<PlacedImage> list = new ArrayList<>();

               for (JsonElement el : r.json().getAsJsonArray("images")) {
                  PlacedImage i = PlacedImage.fromJson(el.getAsJsonObject(), false);
                  if (i != null) {
                     list.add(i);
                  }
               }

               Minecraft.getInstance().execute(() -> this.manager.replaceShared(list));
            }
         });
      }
   }

   void upload(PlacedImage draft, Consumer<String> result) {
      MeowlService.get().post("/images", draft.toJson()).whenComplete((r, error) -> Minecraft.getInstance().execute(() -> {
         if (error != null) {
            result.accept("§cHochladen fehlgeschlagen: " + error.getMessage());
         } else if (!r.ok()) {
            result.accept("§c" + r.error());
         } else {
            PlacedImage placed = PlacedImage.fromJson(r.json(), false);
            if (placed != null) {
               this.manager.putShared(placed);
            }

            result.accept("Bild platziert – alle Meowl-Spieler hier sehen es");
         }
      }));
   }

   void delete(PlacedImage image, Consumer<String> result) {
      MeowlService.get().request("DELETE", "/images/" + image.id(), null, null).whenComplete((r, error) -> Minecraft.getInstance().execute(() -> {
         if (error != null) {
            result.accept("§cEntfernen fehlgeschlagen: " + error.getMessage());
         } else if (!r.ok()) {
            result.accept("§c" + r.error());
         } else {
            this.manager.removeShared(image.id());
            result.accept("Bild entfernt");
         }
      }));
   }
}
