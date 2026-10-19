package com.lennart.nebula.waypoints;

import com.google.gson.Gson;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

public final class WaypointStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Path file = FabricLoader.getInstance().getConfigDir().resolve("meowl-waypoints.json");
   private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
   private boolean loaded;

   private void ensureLoaded() {
      if (!this.loaded) {
         this.loaded = true;

         try {
            if (Files.exists(this.file)) {
               JsonObject root = JsonParser.parseString(Files.readString(this.file, StandardCharsets.UTF_8)).getAsJsonObject();
               JsonObject w = root.has("worlds") ? root.getAsJsonObject("worlds") : new JsonObject();

               for (Entry<String, JsonElement> e : w.entrySet()) {
                  List<Waypoint> list = new ArrayList<>();

                  for (JsonElement el : e.getValue().getAsJsonArray()) {
                     JsonObject o = el.getAsJsonObject();
                     Waypoint wp = new Waypoint(
                        o.get("name").getAsString(),
                        o.get("x").getAsDouble(),
                        o.get("y").getAsDouble(),
                        o.get("z").getAsDouble(),
                        o.has("dim") ? o.get("dim").getAsString() : "minecraft:overworld",
                        o.has("color") ? o.get("color").getAsInt() : Waypoint.COLORS[0],
                        o.has("symbol") ? o.get("symbol").getAsString() : Waypoint.SYMBOLS[0]
                     );
                     wp.visible = !o.has("visible") || o.get("visible").getAsBoolean();
                     list.add(wp);
                  }

                  this.worlds.put(e.getKey(), list);
               }
            }
         } catch (IOException | RuntimeException var10) {
            NebulaClient.LOGGER.warn("Waypoints konnten nicht geladen werden", (Throwable)var10);
         }
      }
   }

   public void save() {
      JsonObject w = new JsonObject();

      for (Entry<String, List<Waypoint>> e : this.worlds.entrySet()) {
         if (!e.getValue().isEmpty()) {
            JsonArray arr = new JsonArray();

            for (Waypoint wp : e.getValue()) {
               JsonObject o = new JsonObject();
               o.addProperty("name", wp.name);
               o.addProperty("x", wp.x);
               o.addProperty("y", wp.y);
               o.addProperty("z", wp.z);
               o.addProperty("dim", wp.dim);
               o.addProperty("color", wp.color);
               o.addProperty("symbol", wp.symbol);
               o.addProperty("visible", wp.visible);
               arr.add(o);
            }

            w.add(e.getKey(), arr);
         }
      }

      JsonObject root = new JsonObject();
      root.add("worlds", w);

      try {
         Path tmp = this.file.resolveSibling("meowl-waypoints.json.tmp");
         Files.writeString(tmp, GSON.toJson((JsonElement)root), StandardCharsets.UTF_8);
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var8) {
         NebulaClient.LOGGER.warn("Waypoints konnten nicht gespeichert werden", (Throwable)var8);
      }
   }

   public static String worldKey() {
      String id = MeowlService.currentServerId();
      return id != null && !id.isEmpty() ? id : "unbekannt";
   }

   public List<Waypoint> current() {
      this.ensureLoaded();
      return this.worlds.computeIfAbsent(worldKey(), k -> new ArrayList<>());
   }
}
