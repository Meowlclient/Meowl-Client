package com.lennart.nebula.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class UpdateInstaller {
   public static final String PART = ".part";
   public static final String PENDING = ".pending";
   private static final long MAX_SIZE = 67108864L;
   private final Path currentJar;

   public UpdateInstaller(Path currentJar) {
      this.currentJar = currentJar;
   }

   public Path modsDir() {
      return this.currentJar.getParent();
   }

   public Path targetJar(Version v) {
      return this.modsDir().resolve("meowl-client-" + v.raw() + ".jar");
   }

   public Path pendingFile(Version v) {
      return this.modsDir().resolve("meowl-client-" + v.raw() + ".jar.pending");
   }

   public Path partFile(Version v) {
      return this.modsDir().resolve("meowl-client-" + v.raw() + ".jar.part");
   }

   public Version cleanupAndFindPending(Version current) {
      Version newest = null;

      try (Stream<Path> files = Files.list(this.modsDir())) {
         for (Path p : (Iterable<Path>)files::iterator) {
            String name = p.getFileName().toString();
            if (name.startsWith("meowl-client-")) {
               if (name.endsWith(".jar.part")) {
                  Files.deleteIfExists(p);
               } else if (name.endsWith(".jar.pending")) {
                  Version v = Version.parse(name.substring("meowl-client-".length(), name.length() - ".jar.pending".length()));
                  if (v == null || !v.isNewerThan(current)) {
                     Files.deleteIfExists(p);
                  } else if (newest == null || v.isNewerThan(newest)) {
                     newest = v;
                  }
               }
            }
         }
      } catch (IOException var101) {
         NebulaClient.LOGGER.warn("Update: mods-Ordner nicht lesbar: {}", var101.toString());
      }

      return newest;
   }

   public Path verifyAndStage(UpdateInfo info) throws IOException {
      Path part = this.partFile(info.version());

      try {
         long size = Files.size(part);
         if (size == info.size() && size <= 67108864L) {
            String sha = sha256(part);
            if (!sha.equalsIgnoreCase(info.sha256())) {
               throw new IOException("Prüfsumme stimmt nicht – Datei beschädigt");
            } else {
               checkModJar(part, info.version());
               Path pending = this.pendingFile(info.version());
               Files.move(part, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               return pending;
            }
         } else {
            throw new IOException("Größe stimmt nicht (" + size + " statt " + info.size() + " Bytes)");
         }
      } catch (IOException | RuntimeException var71) {
         Files.deleteIfExists(part);
         throw var71 instanceof IOException io ? io : new IOException(var71.getMessage(), var71);
      }
   }

   static String sha256(Path file) throws IOException {
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

   static void checkModJar(Path jar, Version expected) throws IOException {
      try (ZipFile zip = new ZipFile(jar.toFile())) {
         ZipEntry entry = zip.getEntry("fabric.mod.json");
         if (entry == null) {
            throw new IOException("Keine Fabric-Mod");
         }

         String json;
         try (InputStream in = zip.getInputStream(entry)) {
            json = new String(in.readNBytes(262144), StandardCharsets.UTF_8);
         }

         JsonObject o = JsonParser.parseString(json).getAsJsonObject();
         if (!"nebula".equals(o.get("id").getAsString())) {
            throw new IOException("Falsche Mod in der Datei");
         }

         Version v = Version.parse(o.get("version").getAsString());
         if (v == null || !v.equals(expected)) {
            throw new IOException("Version in der Datei passt nicht");
         }
      }
   }

   public boolean launchSwapAfterExit(Version version) {
      Path pending = this.pendingFile(version);
      Path target = this.targetJar(version);
      if (!Files.exists(pending)) {
         return false;
      } else {
         long pid = ProcessHandle.current().pid();

         try {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            ProcessBuilder pb;
            if (windows) {
               Path script = Files.createTempFile("meowl-update-", ".cmd");
               Files.writeString(script, windowsScript(pid, this.currentJar, pending, target), StandardCharsets.UTF_8);
               pb = new ProcessBuilder("cmd.exe", "/c", script.toString());
            } else {
               pb = new ProcessBuilder("/bin/sh", "-c", unixScript(pid, this.currentJar, pending, target));
            }

            pb.redirectErrorStream(true);
            pb.redirectOutput(Redirect.DISCARD);
            pb.start();
            NebulaClient.LOGGER.info("Update {} wird nach dem Beenden installiert", version);
            return true;
         } catch (IOException var91) {
            NebulaClient.LOGGER.warn("Update-Hilfsprogramm konnte nicht gestartet werden: {}", var91.toString());
            return false;
         }
      }
   }

   static String unixScript(long pid, Path old, Path pending, Path target) {
      return "while kill -0 "
         + pid
         + " 2>/dev/null; do sleep 1; done; mv -f "
         + q(pending)
         + " "
         + q(target)
         + " || exit 1; i=0; while [ -e "
         + q(old)
         + " ] && [ $i -lt 30 ]; do rm -f "
         + q(old)
         + "; i=$((i+1)); [ -e "
         + q(old)
         + " ] && sleep 1; done; [ -e "
         + q(old)
         + " ] && mv -f "
         + q(target)
         + " "
         + q(pending)
         + "; exit 0";
   }

   private static String q(Path p) {
      return "'" + p.toAbsolutePath().toString().replace("'", "'\\''") + "'";
   }

   static String windowsScript(long pid, Path old, Path pending, Path target) {
      String o = old.toAbsolutePath().toString();
      String p = pending.toAbsolutePath().toString();
      String t = target.toAbsolutePath().toString();
      return String.join(
         "\r\n",
         "@echo off",
         "chcp 65001 >nul",
         "set n=0",
         ":wait",
         "tasklist /FI \"PID eq " + pid + "\" /NH 2>nul | find \" " + pid + " \" >nul",
         "if errorlevel 1 goto gone",
         "set /a n+=1",
         "if %n% geq 900 goto end",
         "ping -n 2 127.0.0.1 >nul",
         "goto wait",
         ":gone",
         "move /y \"" + p + "\" \"" + t + "\" >nul",
         "if errorlevel 1 goto end",
         "set t=0",
         ":del",
         "del /f /q \"" + o + "\" >nul 2>&1",
         "if not exist \"" + o + "\" goto end",
         "set /a t+=1",
         "if %t% geq 30 goto undo",
         "ping -n 2 127.0.0.1 >nul",
         "goto del",
         ":undo",
         "move /y \"" + t + "\" \"" + p + "\" >nul",
         ":end",
         "(goto) 2>nul & del \"%~f0\"",
         ""
      );
   }
}
