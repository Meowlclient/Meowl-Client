package com.lennart.meowlserver.images;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ImageStore {
   private static final Logger LOG = Logger.getLogger("Images");
   public static final int MAX_SIZE = 32;
   /** Je Spieler und Minecraft-Server höchstens so viele Bilder; das älteste wird ersetzt. */
   public static final int MAX_PER_CREATOR = 1;
   private static final int MAX_TOTAL = 20000;
   private final Path file;
   private final Map<String, ImageStore.Image> images = new ConcurrentHashMap<>();

   public ImageStore(Path dataDir) {
      this.file = dataDir.resolve("images.json");
      this.load();
   }

   public static String validateUrl(String url) {
      if (url != null && url.length() <= 512) {
         URI uri;
         try {
            uri = new URI(url.trim());
         } catch (Exception var3) {
            throw new ApiException(400, "Ungültige URL");
         }

         if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
            String host = uri.getHost().toLowerCase();
            if (host.matches("[0-9.]+") || host.contains(":") || host.equals("localhost") || !host.contains(".")) {
               throw new ApiException(400, "IP-Adressen und lokale Hosts sind nicht erlaubt");
            } else if (uri.getPort() != -1 && uri.getPort() != 443) {
               throw new ApiException(400, "Nur Standard-Port 443");
            } else {
               return uri.toString();
            }
         } else {
            throw new ApiException(400, "Nur https-Links ohne Benutzerdaten sind erlaubt");
         }
      } else {
         throw new ApiException(400, "URL fehlt oder ist zu lang");
      }
   }

   public ImageStore.Image add(Map<String, Object> body, String server, UUID creator) {
      if (server != null && !server.isEmpty()) {
         String url = validateUrl(Json.str(body, "url", 512));
         String dim = Json.str(body, "dim", 64);
         if (!dim.matches("[a-z0-9_.:/-]+")) {
            throw new ApiException(400, "Ungültige Dimension");
         } else {
            String facing = Json.str(body, "facing", 5).toLowerCase();
            if (!List.of("north", "south", "east", "west").contains(facing)) {
               throw new ApiException(400, "Nur senkrechte Flächen (north/south/east/west)");
            } else {
               int x = (int)Json.num(body, "x", -3.0E7, 3.0E7);
               int y = (int)Json.num(body, "y", -2048.0, 2048.0);
               int z = (int)Json.num(body, "z", -3.0E7, 3.0E7);
               int w = (int)Json.num(body, "w", 1.0, 32.0);
               int h = (int)Json.num(body, "h", 1.0, 32.0);
               if (this.images.size() >= 20000) {
                  throw new ApiException(507, "Speicher voll");
               } else {
                  ImageStore.Image image = new ImageStore.Image(
                     UUID.randomUUID().toString(), url, server, dim, x, y, z, facing, w, h, creator, System.currentTimeMillis()
                  );
                  this.images.put(image.id(), image);
                  this.save();
                  return image;
               }
            }
         }
      } else {
         throw new ApiException(400, "Kein Minecraft-Server gemeldet");
      }
   }

   /** Älteste eigene Bilder auf diesem Server entfernen, bis Platz für ein neues ist. */
   public List<ImageStore.Image> trimFor(UUID creator, String server) {
      List<ImageStore.Image> mine = new ArrayList<>();

      for (ImageStore.Image i : this.images.values()) {
         if (i.creator().equals(creator) && i.server().equals(server)) {
            mine.add(i);
         }
      }

      mine.sort(java.util.Comparator.comparingLong(ImageStore.Image::created));
      List<ImageStore.Image> removed = new ArrayList<>();

      while (mine.size() - removed.size() >= MAX_PER_CREATOR) {
         ImageStore.Image old = mine.get(removed.size());
         this.images.remove(old.id());
         removed.add(old);
      }

      if (!removed.isEmpty()) {
         this.save();
      }

      return removed;
   }

   /** Alle Bilder eines Spielers auf einem Server löschen (er hat den Server verlassen). */
   public List<ImageStore.Image> removeAllOf(UUID creator, String server) {
      List<ImageStore.Image> removed = new ArrayList<>();
      this.images.values().removeIf(i -> {
         boolean hit = i.creator().equals(creator) && (server == null || i.server().equals(server));
         if (hit) {
            removed.add(i);
         }

         return hit;
      });
      if (!removed.isEmpty()) {
         this.save();
      }

      return removed;
   }

   public ImageStore.Image remove(String id, UUID requester) {
      ImageStore.Image image = this.images.get(id);
      if (image == null) {
         throw new ApiException(404, "Bild nicht gefunden");
      } else if (!image.creator().equals(requester)) {
         throw new ApiException(403, "Nur der Ersteller darf das Bild entfernen");
      } else {
         this.images.remove(id);
         this.save();
         return image;
      }
   }

   public List<ImageStore.Image> forServer(String server) {
      List<ImageStore.Image> out = new ArrayList<>();

      for (ImageStore.Image i : this.images.values()) {
         if (i.server().equals(server)) {
            out.add(i);
         }
      }

      return out;
   }

   private void load() {
      if (Files.exists(this.file)) {
         try {
            Object root = Json.parse(Files.readString(this.file, StandardCharsets.UTF_8));

            for (Object o : (List)((Map)root).get("images")) {
               Map<String, Object> m = (Map<String, Object>)o;
               ImageStore.Image i = new ImageStore.Image(
                  (String)m.get("id"),
                  (String)m.get("url"),
                  (String)m.get("server"),
                  (String)m.get("dim"),
                  ((Double)m.get("x")).intValue(),
                  ((Double)m.get("y")).intValue(),
                  ((Double)m.get("z")).intValue(),
                  (String)m.get("facing"),
                  ((Double)m.get("w")).intValue(),
                  ((Double)m.get("h")).intValue(),
                  UUID.fromString((String)m.get("creator")),
                  ((Double)m.get("created")).longValue()
               );
               this.images.put(i.id(), i);
            }

            LOG.info(this.images.size() + " Bilder geladen");
         } catch (Exception var61) {
            LOG.severe("images.json konnte nicht gelesen werden: " + var61);
         }
      }
   }

   private synchronized void save() {
      List<Object> list = new ArrayList<>();

      for (ImageStore.Image i : this.images.values()) {
         list.add(i.toJson());
      }

      try {
         Files.createDirectories(this.file.getParent());
         Path tmp = this.file.resolveSibling("images.json.tmp");
         Files.writeString(tmp, Json.write(Map.of("images", list)), StandardCharsets.UTF_8);
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var4) {
         LOG.severe("images.json konnte nicht gespeichert werden: " + var4);
      }
   }

   public record Image(String id, String url, String server, String dim, int x, int y, int z, String facing, int w, int h, UUID creator, long created) {
      public Map<String, Object> toJson() {
         Map<String, Object> m = new LinkedHashMap<>();
         m.put("id", this.id);
         m.put("url", this.url);
         m.put("server", this.server);
         m.put("dim", this.dim);
         m.put("x", this.x);
         m.put("y", this.y);
         m.put("z", this.z);
         m.put("facing", this.facing);
         m.put("w", this.w);
         m.put("h", this.h);
         m.put("creator", this.creator.toString());
         m.put("created", this.created);
         return m;
      }
   }
}
