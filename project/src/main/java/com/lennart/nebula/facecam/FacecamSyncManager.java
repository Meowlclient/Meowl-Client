package com.lennart.nebula.facecam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lennart.nebula.net.MeowlService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class FacecamSyncManager {
   private static final double VIEW_DISTANCE = 32.0;
   private static final long HEARTBEAT_MS = 4000L;
   private static final long RECONCILE_MS = 5000L;
   private final FacecamRenderer renderer;
   private final AtomicBoolean uploading = new AtomicBoolean();
   private volatile boolean allowed;
   private volatile boolean confirmedLive;
   private volatile long lastUpload;
   private long lastHeartbeat;
   private final Set<UUID> active = ConcurrentHashMap.newKeySet();
   private long lastReconcile;
   private boolean wasConnected;
   private String lastServer = "";
   private final Map<UUID, Long> lastSeq = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastFetch = new ConcurrentHashMap<>();
   private final Set<UUID> fetching = ConcurrentHashMap.newKeySet();

   public FacecamSyncManager(FacecamRenderer renderer) {
      this.renderer = renderer;
      MeowlService service = MeowlService.get();
      service.on("cam_state", this::onState);
      service.onConnected(() -> {
         this.clearViewer();
         this.lastReconcile = 0L;
         if (this.allowed) {
            this.sendState(true);
         }
      });
   }

   public void setSending(boolean wanted) {
      if (wanted && !this.allowed) {
         this.allowed = true;
         this.sendState(true);
      } else if (!wanted && this.allowed) {
         this.stopStream();
      }

      if (this.allowed && System.currentTimeMillis() - this.lastHeartbeat > 4000L) {
         this.sendState(true);
      }
   }

   private void sendState(boolean on) {
      this.lastHeartbeat = System.currentTimeMillis();
      MeowlService service = MeowlService.get();
      if (service.isConnected()) {
         JsonObject o = new JsonObject();
         o.addProperty("on", on);
         service.post("/cam/state", o).whenComplete((r, e) -> {
            if (on && !this.allowed) {
               this.sendStop();
            } else if (e == null && r != null && r.ok()) {
               this.confirmedLive = on && this.allowed;
            }
         });
      }
   }

   public void offerFrame(byte[] jpeg, int fps) {
      MeowlService service = MeowlService.get();
      if (this.allowed && service.isConnected() && jpeg != null && !MeowlService.currentServerId().isEmpty()) {
         long now = System.currentTimeMillis();
         if (now - this.lastUpload >= 1000L / Math.max(1, fps) && this.uploading.compareAndSet(false, true)) {
            if (!this.allowed) {
               this.uploading.set(false);
            } else {
               this.lastUpload = now;
               service.request("POST", "/cam/frame", null, jpeg).whenComplete((r, e) -> {
                  this.uploading.set(false);
                  if (!this.allowed) {
                     this.sendStop();
                  } else if (e == null && r != null && r.ok()) {
                     this.confirmedLive = true;
                  }
               });
            }
         }
      }
   }

   public void stopStream() {
      this.allowed = false;
      this.confirmedLive = false;
      this.sendStop();
   }

   private void sendStop() {
      if (MeowlService.get().isConnected()) {
         MeowlService.get().post("/cam/stop", new JsonObject());
      }
   }

   public boolean isStreaming() {
      return this.confirmedLive && this.allowed;
   }

   public boolean isSending() {
      return this.allowed;
   }

   private void onState(JsonObject data) {
      UUID uuid;
      try {
         uuid = UUID.fromString(data.get("uuid").getAsString());
      } catch (RuntimeException var4) {
         return;
      }

      boolean on = data.has("on") && data.get("on").getAsBoolean();
      if (!uuid.toString().equals(MeowlService.get().myUuid())) {
         if (on) {
            this.active.add(uuid);
            this.lastSeq.remove(uuid);
         } else {
            this.forget(uuid);
         }
      } else {
         this.confirmedLive = on && this.allowed;
      }
   }

   private void forget(UUID uuid) {
      this.active.remove(uuid);
      this.lastSeq.remove(uuid);
      this.lastFetch.remove(uuid);
      this.renderer.remove(uuid);
   }

   public void tickViewer(int viewFps) {
      MeowlService service = MeowlService.get();
      Minecraft mc = Minecraft.getInstance();
      boolean connected = service.isConnected();
      if (connected && mc.level != null && mc.player != null) {
         this.wasConnected = true;
         String server = MeowlService.currentServerId();
         if (!server.equals(this.lastServer)) {
            this.lastServer = server;
            this.clearViewer();
            this.lastReconcile = 0L;
         }

         long now = System.currentTimeMillis();
         if (now - this.lastReconcile > 5000L) {
            this.lastReconcile = now;
            service.get("/cam/active").thenAccept(r -> {
               if (r.ok() && r.json() != null && r.json().has("active")) {
                  Set<UUID> set = new HashSet<>();

                  for (JsonElement el : r.json().getAsJsonArray("active")) {
                     try {
                        set.add(UUID.fromString(el.getAsString()));
                     } catch (RuntimeException var7) {
                     }
                  }

                  mc.execute(() -> this.reconcile(set));
               }
            });
         }

         String me = service.myUuid();

         for (Player player : mc.level.players()) {
            UUID uuid = player.getUUID();
            if (player != mc.player && !uuid.toString().equals(me) && this.active.contains(uuid) && !(mc.player.distanceToSqr(player) > 1024.0)) {
               long last = this.lastFetch.getOrDefault(uuid, 0L);
               if (now - last >= 1000L / Math.max(1, viewFps) && this.fetching.add(uuid)) {
                  this.lastFetch.put(uuid, now);
                  long after = this.lastSeq.getOrDefault(uuid, 0L);
                  service.get("/cam/frame/" + uuid + "?after=" + after).whenComplete((r, e) -> {
                     try {
                        if (e == null && r.status() == 200 && r.bytes() != null && this.active.contains(uuid)) {
                           String seq = header(r.headers(), "x-seq");
                           if (seq != null) {
                              this.lastSeq.put(uuid, Long.parseLong(seq));
                           }

                           BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.bytes()));
                           if (img != null && img.getWidth() <= 320 && img.getHeight() <= 240) {
                              int[] px = VideoEncoder.pixels(img);
                              int w = img.getWidth();
                              int h = img.getHeight();
                              mc.execute(() -> {
                                 if (this.active.contains(uuid)) {
                                    this.renderer.submit(uuid, px, w, h);
                                 }
                              });
                           }
                        }
                     } catch (Exception var13) {
                     } finally {
                        this.fetching.remove(uuid);
                     }
                  });
               }
            }
         }
      } else {
         if (this.wasConnected || !this.active.isEmpty()) {
            this.clearViewer();
         }

         this.wasConnected = connected;
      }
   }

   private void reconcile(Set<UUID> serverActive) {
      String me = MeowlService.get().myUuid();

      for (UUID uuid : Set.copyOf(this.active)) {
         if (!serverActive.contains(uuid)) {
            this.forget(uuid);
         }
      }

      for (UUID uuidx : serverActive) {
         if (!uuidx.toString().equals(me)) {
            this.active.add(uuidx);
         }
      }

      if (me != null && this.allowed) {
         this.confirmedLive = serverActive.stream().anyMatch(u -> u.toString().equals(me));
      }
   }

   private static String header(Map<String, List<String>> headers, String name) {
      for (Entry<String, List<String>> e : headers.entrySet()) {
         if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
            return e.getValue().get(0);
         }
      }

      return null;
   }

   public static double viewDistance() {
      return 32.0;
   }

   public boolean isActive(UUID uuid) {
      return this.active.contains(uuid);
   }

   public void clearViewer() {
      for (UUID uuid : Set.copyOf(this.active)) {
         this.forget(uuid);
      }

      this.active.clear();
      this.lastSeq.clear();
      this.lastFetch.clear();
   }
}
