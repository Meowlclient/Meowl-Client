package com.lennart.nebula.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gemerkter Zustand der Tools (Armor-Trim-Vorschau, Block-Palette, Brau-Helfer, Verzauberungsrechner, Nether-Koordinaten):
 * beim Schließen gespeichert, beim nächsten Öffnen – auch nach einem Neustart – wieder da. Datei: config/meowl-tools.json.
 */
public final class ToolState {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static JsonObject data;

   private ToolState() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("meowl-tools.json");
   }

   private static JsonObject root() {
      if (data == null) {
         data = new JsonObject();

         try {
            Path f = file();
            if (Files.isRegularFile(f)) {
               JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
               if (e.isJsonObject()) {
                  data = e.getAsJsonObject();
               }
            }
         } catch (Exception var2) {
            NebulaClient.LOGGER.warn("Tool-Zustand nicht lesbar: {}", var2.toString());
         }
      }

      return data;
   }

   /** Zustand eines Tools (leer, wenn noch nichts gespeichert). */
   public static JsonObject get(String tool) {
      JsonObject r = root();
      return r.has(tool) && r.get(tool).isJsonObject() ? r.getAsJsonObject(tool) : new JsonObject();
   }

   public static void put(String tool, JsonObject state) {
      root().add(tool, state);

      try {
         Files.writeString(file(), GSON.toJson(root()), StandardCharsets.UTF_8);
      } catch (Exception var3) {
         NebulaClient.LOGGER.warn("Tool-Zustand nicht speicherbar: {}", var3.toString());
      }
   }

   public static int getInt(JsonObject o, String key, int def) {
      try {
         return o.has(key) ? o.get(key).getAsInt() : def;
      } catch (RuntimeException var4) {
         return def;
      }
   }

   public static boolean getBool(JsonObject o, String key, boolean def) {
      try {
         return o.has(key) ? o.get(key).getAsBoolean() : def;
      } catch (RuntimeException var4) {
         return def;
      }
   }

   public static String getString(JsonObject o, String key, String def) {
      try {
         return o.has(key) ? o.get(key).getAsString() : def;
      } catch (RuntimeException var4) {
         return def;
      }
   }
}
