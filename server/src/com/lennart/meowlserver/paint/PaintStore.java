package com.lennart.meowlserver.paint;

import com.lennart.meowlserver.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Hochgeladene Bilder, öffentlich unter /<ordner>/<id>.<png|jpg> ausgeliefert:
 * Meowl Paint (nur PNG, max. 512×512) und Chat-Bilder/Screenshots (PNG oder JPEG, max. 2560 px).
 */
public final class PaintStore {
   public static final int MAX_BYTES = 262144;
   public static final int CHAT_MAX_BYTES = 2097152;
   private static final byte[] PNG = new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};
   private final Path dir;
   private final int maxDim;
   private final boolean allowJpeg;
   private final int maxFiles;

   public PaintStore(Path dataDir) {
      this(dataDir, "paint", 512, false, 3000);
   }

   public PaintStore(Path dataDir, String folder, int maxDim, boolean allowJpeg, int maxFiles) {
      this.dir = dataDir.resolve(folder);
      this.maxDim = maxDim;
      this.allowJpeg = allowJpeg;
      this.maxFiles = maxFiles;

      try {
         Files.createDirectories(this.dir);
      } catch (IOException var7) {
      }
   }

   private static boolean starts(byte[] data, byte[] sig) {
      if (data.length < sig.length) {
         return false;
      } else {
         for (int i = 0; i < sig.length; i++) {
            if (data[i] != sig[i]) {
               return false;
            }
         }

         return true;
      }
   }

   /** Speichert das Bild und gibt den Dateinamen zurück (z.B. "ab12….png"). */
   public synchronized String storeFile(byte[] data) throws IOException {
      String ext;
      if (starts(data, PNG)) {
         ext = "png";
      } else if (this.allowJpeg && data.length > 3 && (data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255) {
         ext = "jpg";
      } else {
         throw new ApiException(400, this.allowJpeg ? "Nur PNG- oder JPEG-Bilder" : "Nur PNG-Bilder");
      }

      BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
      if (img == null || img.getWidth() > this.maxDim || img.getHeight() > this.maxDim) {
         throw new ApiException(400, "Bild ungültig oder zu groß (max. " + this.maxDim + "×" + this.maxDim + ")");
      }

      String id;
      try {
         id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).substring(0, 32);
      } catch (Exception var6) {
         throw new IOException(var6);
      }

      String name = id + "." + ext;
      Path file = this.dir.resolve(name);
      if (!Files.exists(file)) {
         Path tmp = this.dir.resolve(id + ".tmp");
         Files.write(tmp, data);
         Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         this.prune();
      }

      return name;
   }

   /** Für Meowl Paint: nur die ID (ohne Endung). */
   public String store(byte[] png) throws IOException {
      String name = this.storeFile(png);
      return name.substring(0, name.indexOf('.'));
   }

   public Path file(String name) {
      if (name == null || !name.matches("[0-9a-f]{32}\\.(png|jpg)")) {
         return null;
      }

      Path p = this.dir.resolve(name);
      return Files.isRegularFile(p) ? p : null;
   }

   public static String contentType(Path file) {
      return file.getFileName().toString().endsWith(".jpg") ? "image/jpeg" : "image/png";
   }

   /** Älteste Bilder löschen, wenn es zu viele werden. */
   private void prune() throws IOException {
      List<Path> all;
      try (Stream<Path> s = Files.list(this.dir)) {
         all = s.filter(p -> p.getFileName().toString().matches(".*\\.(png|jpg)")).toList();
      }

      if (all.size() > this.maxFiles) {
         List<Path> sorted = all.stream().sorted(Comparator.comparingLong(p -> p.toFile().lastModified())).toList();

         for (int i = 0; i < sorted.size() - this.maxFiles; i++) {
            Files.deleteIfExists(sorted.get(i));
         }
      }
   }
}
