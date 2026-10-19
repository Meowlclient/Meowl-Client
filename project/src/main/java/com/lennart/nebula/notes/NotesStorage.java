package com.lennart.nebula.notes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NotesStorage {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private final Path file;

   public NotesStorage(Path file) {
      this.file = file;
   }

   public List<Note> load() {
      List<Note> notes = new ArrayList<>();
      if (!Files.exists(this.file)) {
         return notes;
      } else {
         try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);

            for (JsonElement el : root.isJsonObject() && root.getAsJsonObject().has("notes") ? root.getAsJsonObject().getAsJsonArray("notes") : new JsonArray()) {
               JsonObject o = el.getAsJsonObject();
               Note n = new Note(str(o, "id", UUID.randomUUID().toString()), str(o, "title", "Notiz"), str(o, "content", ""));
               n.category = str(o, "category", "");
               n.color = o.has("color") ? o.get("color").getAsInt() : Note.COLORS[0];
               n.pinned = o.has("pinned") && o.get("pinned").getAsBoolean();
               n.favorite = o.has("favorite") && o.get("favorite").getAsBoolean();
               n.created = o.has("created") ? o.get("created").getAsLong() : System.currentTimeMillis();
               n.modified = o.has("modified") ? o.get("modified").getAsLong() : n.created;
               notes.add(n);
            }
         } catch (IOException | RuntimeException var11) {
            NebulaClient.LOGGER.error("Notizen konnten nicht geladen werden", (Throwable)var11);

            try {
               Files.copy(this.file, this.file.resolveSibling(this.file.getFileName() + ".kaputt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException var8) {
            }
         }

         return notes;
      }
   }

   public void save(List<Note> notes) {
      JsonArray array = new JsonArray();

      for (Note n : notes) {
         JsonObject o = new JsonObject();
         o.addProperty("id", n.id);
         o.addProperty("title", n.title);
         o.addProperty("content", n.content);
         o.addProperty("category", n.category);
         o.addProperty("color", n.color);
         o.addProperty("pinned", n.pinned);
         o.addProperty("favorite", n.favorite);
         o.addProperty("created", n.created);
         o.addProperty("modified", n.modified);
         array.add(o);
      }

      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      root.add("notes", array);

      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");

         try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson((JsonElement)root, writer);
         }

         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var101) {
         NebulaClient.LOGGER.error("Notizen konnten nicht gespeichert werden", (Throwable)var101);
      }
   }

   private static String str(JsonObject o, String key, String def) {
      return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
   }
}
