package com.lennart.meowlserver.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class UpdateStore {
   private static final Pattern NAME = Pattern.compile("meowl-client-(\\d+(?:\\.\\d+){0,3}(?:-[A-Za-z0-9.]{1,32})?)\\.jar");
   private final Path dir;
   private UpdateStore.Release cached;
   private long cachedStamp;

   private final Map<String, UpdateStore> perMc = new java.util.concurrent.ConcurrentHashMap<>();

   public UpdateStore(Path dataDir) {
      this.dir = dataDir.resolve("updates");

      try {
         Files.createDirectories(this.dir);
      } catch (IOException var3) {
      }
   }

   private UpdateStore(Path dir, boolean sub) {
      this.dir = dir;
   }

   /**
    * Updates für eine bestimmte Minecraft-Version (Unterordner updates/mc26.3). Ohne Angabe oder ohne passenden
    * gilt der Hauptordner (1.21.11). Für andere Versionen ohne eigenen Ordner gibt es null (nie die falsche Jar).
    */
   public UpdateStore forMc(String mc) {
      if (mc == null || mc.isEmpty() || mc.startsWith("1.21")) {
         return this;
      }
      if (!mc.matches("\\d{1,3}(\\.\\d{1,3}){1,2}")) {
         return null;
      }
      Path sub = this.dir.resolve("mc" + mc);
      if (!Files.isDirectory(sub)) {
         return null;
      }
      return this.perMc.computeIfAbsent(mc, k -> new UpdateStore(sub, true));
   }

   public synchronized UpdateStore.Release latest() throws IOException {
      if (!Files.isDirectory(this.dir)) {
         return null;
      } else {
         Path best = null;
         String bestVersion = null;
         long stamp = 0L;

         try (Stream<Path> files = Files.list(this.dir)) {
            for (Path p : (Iterable<Path>)files::iterator) {
               Matcher m = NAME.matcher(p.getFileName().toString());
               if (m.matches() && Files.isRegularFile(p)) {
                  stamp = stamp * 31L + Files.getLastModifiedTime(p).toMillis() + Files.size(p);
                  if (bestVersion == null || compare(m.group(1), bestVersion) > 0) {
                     best = p;
                     bestVersion = m.group(1);
                  }
               }
            }
         }

         if (best == null) {
            this.cached = null;
            return null;
         } else if (this.cached != null && this.cachedStamp == stamp && this.cached.file().equals(best)) {
            return this.cached;
         } else {
            String changelog = "";
            Path notes = best.resolveSibling(best.getFileName().toString().replace(".jar", ".txt"));
            if (Files.isRegularFile(notes) && Files.size(notes) < 4096L) {
               changelog = Files.readString(notes, StandardCharsets.UTF_8).trim();
            }

            this.cached = new UpdateStore.Release(bestVersion, best, Files.size(best), sha256(best), changelog);
            this.cachedStamp = stamp;
            return this.cached;
         }
      }
   }

   /** Alle veröffentlichten Changelogs (neueste zuerst): [{version, changelog}]. */
   public synchronized java.util.List<Map<String, Object>> notes() throws IOException {
      java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
      if (!Files.isDirectory(this.dir)) {
         return out;
      }

      Pattern txt = Pattern.compile("meowl-client-(\\d+(?:\\.\\d+){0,3}(?:-[A-Za-z0-9.]{1,32})?)\\.txt");

      try (Stream<Path> files = Files.list(this.dir)) {
         for (Path p : (Iterable<Path>)files::iterator) {
            Matcher m = txt.matcher(p.getFileName().toString());
            if (m.matches() && Files.isRegularFile(p) && Files.size(p) < 8192L) {
               Map<String, Object> e = new LinkedHashMap<>();
               e.put("version", m.group(1));
               e.put("changelog", Files.readString(p, StandardCharsets.UTF_8).trim());
               out.add(e);
            }
         }
      }

      out.sort((a, b) -> compare((String)b.get("version"), (String)a.get("version")));
      return out;
   }

   public Path file(String version) throws IOException {
      if (version != null && version.matches("\\d+(\\.\\d+){0,3}(-[A-Za-z0-9.]{1,32})?")) {
         Path p = this.dir.resolve("meowl-client-" + version + ".jar");
         return Files.isRegularFile(p) && p.normalize().startsWith(this.dir.normalize()) ? p : null;
      } else {
         return null;
      }
   }

   private static String sha256(Path file) throws IOException {
      try {
         String var5;
         try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];

            int n;
            while ((n = in.read(buf)) > 0) {
               md.update(buf, 0, n);
            }

            var5 = HexFormat.of().formatHex(md.digest());
         }

         return var5;
      } catch (NoSuchAlgorithmException var81) {
         throw new IOException(var81);
      }
   }

   static int compare(String a, String b) {
      String[] as = a.split("-", 2);
      String[] bs = b.split("-", 2);
      String[] ap = as[0].split("\\.");
      String[] bp = bs[0].split("\\.");

      for (int i = 0; i < Math.max(ap.length, bp.length); i++) {
         int x = i < ap.length ? Integer.parseInt(ap[i]) : 0;
         int y = i < bp.length ? Integer.parseInt(bp[i]) : 0;
         if (x != y) {
            return Integer.compare(x, y);
         }
      }

      boolean aPre = as.length > 1;
      boolean bPre = bs.length > 1;
      if (aPre != bPre) {
         return aPre ? -1 : 1;
      } else {
         return aPre ? as[1].compareTo(bs[1]) : 0;
      }
   }

   public record Release(String version, Path file, long size, String sha256, String changelog) {
      public Map<String, Object> toJson() {
         Map<String, Object> m = new LinkedHashMap<>();
         m.put("version", this.version);
         m.put("size", this.size);
         m.put("sha256", this.sha256);
         m.put("changelog", this.changelog);
         return m;
      }
   }
}
