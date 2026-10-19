package com.lennart.meowlserver.cam;

import com.lennart.meowlserver.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class CamRelay {
   public static final int MAX_BYTES = 49152;
   public static final int MAX_W = 320;
   public static final int MAX_H = 240;
   public static final long FRAME_TIMEOUT_MS = 10000L;
   private final Map<UUID, CamRelay.Stream> streams = new ConcurrentHashMap<>();
   private final AtomicLong seq = new AtomicLong();
   private final CamRelay.Listener listener;

   public CamRelay(CamRelay.Listener listener) {
      this.listener = listener;
   }

   public synchronized long upload(UUID sender, String server, byte[] jpeg) {
      if (server != null && !server.isEmpty()) {
         if (jpeg.length < 4 || jpeg.length > 49152) {
            throw new ApiException(413, "Bild zu groß (max. 48 KB)");
         } else if ((jpeg[0] & 255) == 255 && (jpeg[1] & 255) == 216) {
            checkDimensions(jpeg);
            long now = System.currentTimeMillis();
            CamRelay.Stream s = this.turnOn(sender, server, now);
            s.lastAlive = now;
            s.frame = new CamRelay.Frame(jpeg, this.seq.incrementAndGet(), now);
            return s.frame.seq();
         } else {
            throw new ApiException(415, "Nur JPEG erlaubt");
         }
      } else {
         throw new ApiException(409, "Nicht auf einem Minecraft-Server");
      }
   }

   public synchronized void setState(UUID sender, String server, boolean on) {
      if (on) {
         if (server == null || server.isEmpty()) {
            throw new ApiException(409, "Nicht auf einem Minecraft-Server");
         }

         this.turnOn(sender, server, System.currentTimeMillis()).lastAlive = System.currentTimeMillis();
      } else {
         this.stop(sender);
      }
   }

   public synchronized void stop(UUID sender) {
      CamRelay.Stream s = this.streams.remove(sender);
      if (s != null && s.on) {
         this.listener.stateChanged(sender, s.server, false);
      }
   }

   private CamRelay.Stream turnOn(UUID sender, String server, long now) {
      CamRelay.Stream s = this.streams.computeIfAbsent(sender, u -> new CamRelay.Stream());
      if (s.on && !server.equals(s.server)) {
         this.listener.stateChanged(sender, s.server, false);
         s.on = false;
         s.frame = null;
      }

      if (!s.on) {
         s.on = true;
         s.server = server;
         s.since = now;
         this.listener.stateChanged(sender, server, true);
      }

      return s;
   }

   private static void checkDimensions(byte[] jpeg) {
      try {
         try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
               throw new ApiException(415, "Kein gültiges JPEG");
            }

            ImageReader reader = readers.next();

            try {
               reader.setInput(in, true, true);
               int w = reader.getWidth(0);
               int h = reader.getHeight(0);
               if (w > 320 || h > 240 || w < 8 || h < 8) {
                  throw new ApiException(413, "Auflösung zu groß (max. 320×240)");
               }
            } finally {
               reader.dispose();
            }
         }
      } catch (IOException var141) {
         throw new ApiException(415, "Kein gültiges JPEG");
      }
   }

   public synchronized CamRelay.Frame latest(UUID sender, String viewerServer, long after) {
      CamRelay.Stream s = this.streams.get(sender);
      if (s != null && s.on && viewerServer.equals(s.server)) {
         CamRelay.Frame f = s.frame;
         return f != null && f.seq() > after ? f : null;
      } else {
         return null;
      }
   }

   /** Neuestes Bild unabhängig vom Server (Zugriff vorher prüfen, z.B. über die Tab-Liste). */
   public synchronized CamRelay.Frame latestAny(UUID sender, long after) {
      CamRelay.Stream s = this.streams.get(sender);
      if (s != null && s.on) {
         CamRelay.Frame f = s.frame;
         return f != null && f.seq() > after ? f : null;
      } else {
         return null;
      }
   }

   public synchronized List<UUID> allActive() {
      List<UUID> out = new ArrayList<>();
      this.streams.forEach((uuid, s) -> {
         if (s.on) {
            out.add(uuid);
         }
      });
      return out;
   }

   public synchronized List<String> active(String server) {
      List<String> out = new ArrayList<>();
      this.streams.forEach((uuid, s) -> {
         if (s.on && server.equals(s.server)) {
            out.add(uuid.toString());
         }
      });
      return out;
   }

   public synchronized void tick(Predicate<UUID> online) {
      long now = System.currentTimeMillis();

      for (UUID uuid : List.copyOf(this.streams.keySet())) {
         CamRelay.Stream s = this.streams.get(uuid);
         if (s != null && (now - s.lastAlive > 10000L || !online.test(uuid))) {
            this.stop(uuid);
         }
      }
   }

   public record Frame(byte[] jpeg, long seq, long time) {
   }

   @FunctionalInterface
   public interface Listener {
      void stateChanged(UUID var1, String var2, boolean var3);
   }

   private static final class Stream {
      String server;
      boolean on;
      long since;
      long lastAlive;
      CamRelay.Frame frame;
   }
}
