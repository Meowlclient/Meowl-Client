package com.lennart.nebula.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Setting;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class ConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Path file;
   private final ModuleManager moduleManager;
   private JsonObject unknownModules = new JsonObject();

   public ConfigManager(Path file, ModuleManager moduleManager) {
      this.file = file;
      this.moduleManager = moduleManager;
   }

   public void load() {
      if (!Files.exists(this.file)) {
         this.save();
      } else {
         try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement.isJsonObject()) {
               JsonObject modules = rootElement.getAsJsonObject().getAsJsonObject("modules");
               if (modules == null) {
                  return;
               }

               this.unknownModules = new JsonObject();

               for (Entry<String, JsonElement> entry : modules.entrySet()) {
                  if (this.moduleManager.getByName(entry.getKey()).isEmpty() && !entry.getKey().equals("Meowl Netzwerk")) {
                     this.unknownModules.add(entry.getKey(), entry.getValue());
                  }
               }

               for (Module module : this.moduleManager.getModules()) {
                  JsonObject data = modules.getAsJsonObject(module.getName());
                  List<JsonObject> sources = new ArrayList<>();
                  if (data != null && data.getAsJsonObject("settings") != null) {
                     sources.add(data.getAsJsonObject("settings"));
                  }

                  boolean legacyEnabled = false;
                  List<Object[]> pendingLegacy = new ArrayList<>();

                  for (String legacyName : module.getLegacyNames()) {
                     JsonObject legacy = modules.getAsJsonObject(legacyName);
                     if (legacy != null) {
                        if (legacy.getAsJsonObject("settings") != null) {
                           sources.add(legacy.getAsJsonObject("settings"));
                        }

                        boolean wasEnabled = legacy.has("enabled") && legacy.get("enabled").getAsBoolean();
                        legacyEnabled |= wasEnabled;
                        this.unknownModules.remove(legacyName);
                        pendingLegacy.add(new Object[]{legacyName, wasEnabled});
                     }
                  }

                  for (Setting<?> setting : module.getSettings()) {
                     JsonElement value = find(sources, setting);
                     if (value != null) {
                        setting.fromJson(value);
                     }
                  }

                  for (Object[] legacy : pendingLegacy) {
                     module.applyLegacy((String)legacy[0], (Boolean)legacy[1], data == null ? null : data.getAsJsonObject("settings"));
                  }

                  if (data != null && data.has("enabled") && module.persistEnabled()) {
                     module.setEnabled(data.get("enabled").getAsBoolean());
                  } else if (data == null && legacyEnabled && module.persistEnabled()) {
                     module.setEnabled(true);
                  }
               }

               for (Module modulex : this.moduleManager.getModules()) {
                  modulex.onConfigLoaded();
               }

               NebulaClient.LOGGER.info("Config geladen: {}", this.file);
               return;
            }

            return;
         } catch (IOException | RuntimeException var16) {
            NebulaClient.LOGGER.error("Config konnte nicht geladen werden – Defaults werden verwendet", (Throwable)var16);
         }
      }
   }

   private static JsonElement find(List<JsonObject> sources, Setting<?> setting) {
      for (JsonObject src : sources) {
         if (src.has(setting.getName())) {
            return src.get(setting.getName());
         }

         for (String alias : setting.getAliases()) {
            if (src.has(alias)) {
               return src.get(alias);
            }
         }
      }

      return null;
   }

   public void save() {
      JsonObject modules = new JsonObject();

      for (Entry<String, JsonElement> entry : this.unknownModules.entrySet()) {
         modules.add(entry.getKey(), entry.getValue());
      }

      for (Module module : this.moduleManager.getModules()) {
         JsonObject settings = new JsonObject();

         for (Setting<?> setting : module.getSettings()) {
            if (!(setting instanceof ActionSetting)) {
               settings.add(setting.getName(), setting.toJson());
            }
         }

         JsonObject data = new JsonObject();
         data.addProperty("enabled", module.persistEnabled() && module.isEnabled());
         data.add("settings", settings);
         modules.add(module.getName(), data);
      }

      JsonObject root = new JsonObject();
      root.add("modules", modules);

      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");

         try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson((JsonElement)root, writer);
         }

         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var91) {
         NebulaClient.LOGGER.error("Config konnte nicht gespeichert werden", (Throwable)var91);
      }
   }
}
