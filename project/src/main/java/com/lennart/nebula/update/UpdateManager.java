package com.lennart.nebula.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.util.ChatUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;

public final class UpdateManager {
   private static final long FIRST_CHECK_DELAY_MS = 8000L;
   /** Liegt diese Datei im Jar, ist der Updater komplett aus (eigene Fassung „ohne Updater“). */
   public static final boolean NO_UPDATER = UpdateManager.class.getResource("/meowl-no-updater") != null;
   private final Minecraft mc = Minecraft.getInstance();
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NORMAL).build();
   private final UpdateManager.Settings settings;
   private final List<UpdateSource> sources;
   private final Version current;
   private final UpdateInstaller installer;
   private final long startedAt = System.currentTimeMillis();
   private volatile UpdateManager.State state = UpdateManager.State.IDLE;
   private volatile UpdateInfo latest;
   private volatile String message = "";
   private volatile long downloaded;
   private long lastCheck;
   private boolean notified;
   private Version readyVersion;
   private Version blockedVersion;
   private String pendingNotice;
   private boolean noticeShown;
   private boolean readyReminded;
   private long restartArmed;
   /** Version, zu der schon gefragt wurde bzw. die mit „Später“ abgelehnt wurde (gilt bis zum nächsten Start). */
   private Version prompted;
   private Version declined;
   private final Path marker = FabricLoader.getInstance().getConfigDir().resolve("meowl-update.json");

   public UpdateManager(UpdateManager.Settings settings) {
      this.settings = settings;
      this.sources = List.of(new MeowlServerUpdateSource(settings::sourceUrl, this.http));
      Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("nebula");
      this.current = mod.<Version>map(m -> Version.parse(m.getMetadata().getVersion().getFriendlyString())).orElse(null);
      Path jar = mod.<Path>flatMap(m -> m.getOrigin().getPaths().stream().findFirst()).orElse(null);
      boolean realJar = jar != null && Files.isRegularFile(jar) && jar.getFileName().toString().endsWith(".jar");
      this.installer = realJar && this.current != null ? new UpdateInstaller(jar) : null;
      if (NO_UPDATER) {
         // Fassung „ohne Updater“: nie nach Updates suchen, nie fragen, nie etwas laden
         this.state = UpdateManager.State.DISABLED;
         this.message = "Diese Version hat keinen Updater – neue Versionen selbst herunterladen";
      } else if (this.installer == null) {
         this.state = UpdateManager.State.DISABLED;
         this.message = "Nur in der fertigen Mod-JAR verfügbar";
      } else {
         this.verifyLastInstall();
         Version pending = this.installer.cleanupAndFindPending(this.current);
         if (pending != null && (this.blockedVersion == null || !pending.equals(this.blockedVersion))) {
            this.readyVersion = pending;
            this.state = UpdateManager.State.READY;
            this.message = "Version " + pending + " ist bereit – Neustart von Minecraft nötig";
         }
      }

      MeowlService.get().onConnected(() -> {
         if (this.lastCheck != 0L && this.state != UpdateManager.State.DISABLED && this.settings.checkEnabled()) {
            this.check(false);
         }
      });
   }

   private void verifyLastInstall() {
      if (Files.isRegularFile(this.marker)) {
         try {
            JsonObject o = JsonParser.parseString(Files.readString(this.marker)).getAsJsonObject();
            Version expected = Version.parse(o.get("expected").getAsString());
            int attempts = o.has("attempts") ? o.get("attempts").getAsInt() : 1;
            if (expected == null || !expected.isNewerThan(this.current)) {
               Files.deleteIfExists(this.marker);
               if (expected != null && expected.equals(this.current)) {
                  this.pendingNotice = "§aMeowl Client wurde auf §f" + this.current + " §aaktualisiert.";
               }
            } else if (attempts >= 2) {
               Files.deleteIfExists(this.marker);
               this.blockedVersion = expected;

               try {
                  Files.deleteIfExists(this.installer.pendingFile(expected));
               } catch (IOException var61) {
               }

               this.state = UpdateManager.State.FAILED;
               this.message = "Update " + expected + " ließ sich nicht installieren – Datei war gesperrt";
               this.pendingNotice = "§cUpdate auf "
                  + expected
                  + " ist zweimal fehlgeschlagen. §7Minecraft schließen und §fmeowl-client-"
                  + expected
                  + ".jar §7selbst in den mods-Ordner legen (alte meowl-client-Datei löschen).";
            } else {
               NebulaClient.LOGGER.warn("Update {} ist noch nicht aktiv (Versuch {}), wird beim nächsten Beenden erneut installiert", expected, attempts);
            }
         } catch (IOException | RuntimeException var7) {
            try {
               Files.deleteIfExists(this.marker);
            } catch (IOException var5) {
            }
         }
      }
   }

   private void writeMarker(Version expected) {
      int attempts = 1;

      try {
         if (Files.isRegularFile(this.marker)) {
            JsonObject old = JsonParser.parseString(Files.readString(this.marker)).getAsJsonObject();
            if (old.has("expected") && expected.raw().equals(old.get("expected").getAsString()) && old.has("attempts")) {
               attempts = old.get("attempts").getAsInt() + 1;
            }
         }
      } catch (IOException | RuntimeException var6) {
      }

      JsonObject o = new JsonObject();
      o.addProperty("expected", expected.raw());
      o.addProperty("from", this.current.raw());
      o.addProperty("attempts", attempts);
      o.addProperty("time", System.currentTimeMillis());

      try {
         Files.writeString(this.marker, o.toString());
      } catch (IOException var5) {
         NebulaClient.LOGGER.warn("Update-Marker konnte nicht geschrieben werden: {}", var5.toString());
      }
   }

   public boolean restartToInstall() {
      if (this.state == UpdateManager.State.READY && this.readyVersion != null) {
         long now = System.currentTimeMillis();
         if (now - this.restartArmed > 4000L) {
            this.restartArmed = now;
            ChatUtil.chat("§eNochmal klicken: Minecraft wird beendet und Update " + this.readyVersion + " installiert. §7Danach Minecraft neu starten.");
            return false;
         } else {
            this.restartArmed = 0L;
            this.mc.stop();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean restartArmed() {
      return System.currentTimeMillis() - this.restartArmed < 4000L;
   }

   public UpdateManager.State state() {
      return this.state;
   }

   public String message() {
      return this.message;
   }

   public Version currentVersion() {
      return this.current;
   }

   public UpdateInfo latest() {
      return this.latest;
   }

   public float progress() {
      UpdateInfo info = this.latest;
      return info != null && info.size() > 0L ? Math.min(1.0F, (float)this.downloaded / (float)info.size()) : 0.0F;
   }

   public boolean updateAvailable() {
      return this.state == UpdateManager.State.AVAILABLE || this.state == UpdateManager.State.DOWNLOADING || this.state == UpdateManager.State.READY;
   }

   /** „Später“: zu dieser Version bis zum nächsten Start nicht mehr fragen. */
   public void declineCurrent() {
      UpdateInfo info = this.latest;
      this.declined = info != null ? info.version() : this.readyVersion;
   }

   /** Im Hauptmenü (ohne Welt): einmal pro Version fragen, ob installiert werden soll. */
   private void maybePrompt() {
      UpdateInfo info = this.latest;
      if (this.state != UpdateManager.State.AVAILABLE || info == null || this.mc.level != null) {
         return;
      }

      net.minecraft.client.gui.screens.Screen screen = this.mc.gui.screen();
      boolean title = screen instanceof net.minecraft.client.gui.screens.TitleScreen || screen instanceof com.lennart.nebula.gui.MeowlTitleScreen;
      if (title && !info.version().equals(this.declined) && !info.version().equals(this.prompted)) {
         this.prompted = info.version();
         this.mc.gui.setScreen(new com.lennart.nebula.gui.UpdatePromptScreen(screen, this));
      }
   }

   public void tick() {
      this.maybePrompt();
      if (this.mc.player != null) {
         if (this.pendingNotice != null && !this.noticeShown) {
            this.noticeShown = true;
            ChatUtil.chat(this.pendingNotice);
         }

         if (this.state == UpdateManager.State.READY && !this.readyReminded && this.readyVersion != null) {
            this.readyReminded = true;
            this.announceReady(this.readyVersion);
         }
      }

      if (this.state != UpdateManager.State.DISABLED && this.settings.checkEnabled()) {
         long now = System.currentTimeMillis();
         long interval = (long)(Math.max(1.0, this.settings.intervalHours()) * 3600000.0);
         boolean due = this.lastCheck == 0L ? now - this.startedAt > 8000L : now - this.lastCheck > interval;
         if (due && this.state != UpdateManager.State.CHECKING && this.state != UpdateManager.State.DOWNLOADING) {
            this.check(false);
         }
      }
   }

   public void check(boolean manual) {
      if (this.state != UpdateManager.State.DISABLED && this.state != UpdateManager.State.CHECKING && this.state != UpdateManager.State.DOWNLOADING) {
         this.lastCheck = System.currentTimeMillis();
         UpdateManager.State before = this.state;
         this.state = UpdateManager.State.CHECKING;
         this.message = "Suche nach Updates …";
         CompletableFuture<Optional<UpdateInfo>> chain = CompletableFuture.completedFuture(Optional.empty());

         for (UpdateSource source : this.sources) {
            chain = chain.thenCompose(
               found -> found.isPresent()
                  ? CompletableFuture.completedFuture((Optional<UpdateInfo>)found)
                  : source.fetchLatest().exceptionally(e -> Optional.empty())
            );
         }

         chain.orTimeout(30L, TimeUnit.SECONDS)
            .whenComplete(
               (result, error) -> this.mc
                  .execute(
                     () -> {
                        if (error != null) {
                           this.state = before == UpdateManager.State.READY ? UpdateManager.State.READY : UpdateManager.State.FAILED;
                           this.message = "Update-Server nicht erreichbar";
                           if (manual) {
                              ChatUtil.chat("§cUpdate-Prüfung fehlgeschlagen: Server nicht erreichbar.");
                           }
                        } else {
                           UpdateInfo info = (UpdateInfo)result.orElse(null);
                           if (info != null
                              && info.version().isNewerThan(this.current)
                              && (this.blockedVersion == null || info.version().isNewerThan(this.blockedVersion))) {
                              this.latest = info;
                              if (this.readyVersion != null && !info.version().isNewerThan(this.readyVersion)) {
                                 this.state = UpdateManager.State.READY;
                              } else {
                                 this.state = UpdateManager.State.AVAILABLE;
                                 this.message = "Version " + info.version() + " verfügbar (du hast " + this.current + ")";
                                 // nie automatisch laden: erst fragen (Fenster im Hauptmenü, im Spiel Chat-Knöpfe)
                                 this.notifyAvailable(info);
                              }
                           } else {
                              this.state = before == UpdateManager.State.READY ? UpdateManager.State.READY : UpdateManager.State.UP_TO_DATE;
                              if (this.state == UpdateManager.State.UP_TO_DATE) {
                                 this.message = "Du hast die neueste Version (" + this.current + ")";
                              }

                              if (manual) {
                                 ChatUtil.chat("§aMeowl Client ist aktuell (" + this.current + ").");
                              }
                           }
                        }
                     }
                  )
            );
      }
   }

   private void notifyAvailable(UpdateInfo info) {
      if (!this.notified) {
         this.notified = true;
         com.lennart.nebula.compat.Compat.toast(Component.literal("Meowl Client " + info.version()), Component.literal("Update verfügbar – willst du es installieren?"));
         if (this.mc.player != null) {
            ChatUtil.chat(
               Component.literal("§dUpdate verfügbar: §f" + this.current + " → " + info.version() + " ")
                  .append(ChatUtil.button("[Installieren]", 4906624, "/meowl update download", "Lädt das Update herunter – installiert wird beim Beenden von Minecraft"))
                  .append(Component.literal(" "))
                  .append(ChatUtil.button("[Später]", 9474192, "/meowl update later", "Diesmal nicht – beim nächsten Start wieder fragen"))
            );
         }
      }
   }

   public void download() {
      UpdateInfo info = this.latest;
      if (this.installer != null
         && info != null
         && this.state != UpdateManager.State.DOWNLOADING
         && (this.state != UpdateManager.State.READY || !info.version().equals(this.readyVersion))) {
         this.state = UpdateManager.State.DOWNLOADING;
         this.message = "Lade " + info.version() + " herunter …";
         this.downloaded = 0L;
         Path part = this.installer.partFile(info.version());
         HttpRequest request = HttpRequest.newBuilder(info.download()).timeout(Duration.ofMinutes(5L)).header("User-Agent", "MeowlClient").GET().build();
         this.http
            .sendAsync(request, BodyHandlers.ofInputStream())
            .thenApply(r -> {
               if (r.statusCode() != 200) {
                  throw new IllegalStateException("HTTP " + r.statusCode());
               } else {
                  try (
                     InputStream in = r.body();
                     OutputStream out = Files.newOutputStream(part);
                  ) {
                     byte[] buf = new byte[65536];

                     int n;
                     while ((n = in.read(buf)) > 0) {
                        this.downloaded += n;
                        if (this.downloaded > info.size()) {
                           throw new IOException("Datei größer als angekündigt");
                        }

                        out.write(buf, 0, n);
                     }
                  } catch (IOException var13) {
                     throw new UncheckedIOException(var13);
                  }

                  try {
                     return this.installer.verifyAndStage(info);
                  } catch (IOException var101) {
                     throw new UncheckedIOException(var101);
                  }
               }
            })
            .whenComplete(
               (pending, error) -> {
                  if (error != null) {
                     try {
                        Files.deleteIfExists(part);
                     } catch (IOException var6) {
                     }
                  }

                  this.mc
                     .execute(
                        () -> {
                           if (error != null) {
                              Throwable cause = error.getCause() != null ? error.getCause() : error;
                              this.state = UpdateManager.State.FAILED;
                              this.message = "Download fehlgeschlagen: " + cause.getMessage() + " – aktuelle Version bleibt";
                              ChatUtil.chat("§c" + this.message);
                           } else {
                              this.readyVersion = info.version();
                              this.state = UpdateManager.State.READY;
                              this.message = "Version " + info.version() + " ist bereit – Neustart von Minecraft nötig";
                              this.readyReminded = true;
                              com.lennart.nebula.compat.Compat.toast(Component.literal("Update bereit"), Component.literal("Meowl " + info.version() + " wird beim Beenden installiert"));
                              this.announceReady(info.version());
                           }
                        }
                     );
               }
            );
      }
   }

   private void announceReady(Version version) {
      if (this.mc.player != null) {
         ChatUtil.chat(
            Component.literal("§aUpdate " + version + " ist heruntergeladen und geprüft. §7Ein Neustart von Minecraft ist nötig. ")
               .append(
                  ChatUtil.button(
                     "[Jetzt beenden & installieren]",
                     4906624,
                     "/meowl update restart",
                     "Beendet Minecraft sauber – das Update wird danach automatisch eingespielt"
                  )
               )
         );
      }
   }

   public void onShutdown() {
      if (this.installer != null && this.readyVersion != null && this.installer.launchSwapAfterExit(this.readyVersion)) {
         this.writeMarker(this.readyVersion);
      }
   }

   public interface Settings {
      boolean checkEnabled();

      double intervalHours();

      String sourceUrl();
   }

   public static enum State {
      DISABLED,
      IDLE,
      CHECKING,
      UP_TO_DATE,
      AVAILABLE,
      DOWNLOADING,
      READY,
      FAILED;
   }
}
