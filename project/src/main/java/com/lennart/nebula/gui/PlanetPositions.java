package com.lennart.nebula.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Section;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Eigene Positionen der Planeten in der Übersicht (verschoben per Maus), gespeichert in config/meowl-planets.json. */
public final class PlanetPositions {
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("meowl-planets.json");
   private static final Map<Section, float[]> CUSTOM = new EnumMap<>(Section.class);
   private static boolean loaded;

   private PlanetPositions() {
   }

   /** Position als Anteil {x von Breite, y von Höhe des Bereichs}. */
   public static float[] get(Section s) {
      load();
      float[] p = CUSTOM.get(s);
      return p != null ? p : new float[]{s.ox(), s.oy()};
   }

   public static boolean hasCustom() {
      load();
      return !CUSTOM.isEmpty();
   }

   public static void set(Section s, float x, float y) {
      load();
      CUSTOM.put(s, new float[]{Math.max(0.03F, Math.min(0.97F, x)), Math.max(0.0F, Math.min(1.0F, y))});
   }

   public static void reset() {
      CUSTOM.clear();
      save();
   }

   private static void load() {
      if (!loaded) {
         loaded = true;

         try {
            if (Files.exists(FILE)) {
               JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();

               for (Section s : Section.values()) {
                  if (o.has(s.name()) && o.get(s.name()).isJsonArray() && o.getAsJsonArray(s.name()).size() == 2) {
                     CUSTOM.put(s, new float[]{o.getAsJsonArray(s.name()).get(0).getAsFloat(), o.getAsJsonArray(s.name()).get(1).getAsFloat()});
                  }
               }
            }
         } catch (Exception var3) {
            NebulaClient.LOGGER.warn("Planeten-Positionen konnten nicht geladen werden: {}", var3.toString());
         }
      }
   }

   public static void save() {
      JsonObject o = new JsonObject();

      for (Map.Entry<Section, float[]> e : CUSTOM.entrySet()) {
         com.google.gson.JsonArray a = new com.google.gson.JsonArray();
         a.add(e.getValue()[0]);
         a.add(e.getValue()[1]);
         o.add(e.getKey().name(), a);
      }

      try {
         Files.writeString(FILE, o.toString(), StandardCharsets.UTF_8);
      } catch (Exception var3) {
         NebulaClient.LOGGER.warn("Planeten-Positionen konnten nicht gespeichert werden: {}", var3.toString());
      }
   }
}
