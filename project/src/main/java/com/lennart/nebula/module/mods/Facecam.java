package com.lennart.nebula.module.mods;

import com.lennart.nebula.facecam.CameraCapture;
import com.lennart.nebula.facecam.FacecamManager;
import com.lennart.nebula.facecam.FacecamSyncManager;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public class Facecam extends Module {
   private final TextSetting camera = this.add(new TextSetting("Kamera", "Name der Kamera (leer = erste gefundene)", "", 128));
   private final ModeSetting resolution = this.add(new ModeSetting("Auflösung", "Aufnahme-Auflösung", "320×240", "160×120", "320×240", "640×480"));
   private final NumberSetting fps = this.add(new NumberSetting("FPS", "Bilder pro Sekunde (lokal)", 15.0, 5.0, 30.0, 1.0, "FPS"));
   private final NumberSetting size = this.add(new NumberSetting("Größe", "Breite der Facecam in Blöcken", 0.6, 0.3, 2.0, 0.05, "Blöcke"));
   private final ModeSetting position = this.add(
      new ModeSetting("Position", "Wo die Facecam am Kopf sitzt", "Vor dem Gesicht", "Vor dem Gesicht", "Über dem Kopf", "Neben dem Kopf")
   );
   private final NumberSetting distance = this.add(new NumberSetting("Abstand", "Wie weit die Facecam vor dem Gesicht schwebt", 0.6, 0.3, 1.5, 0.05, "Blöcke"));
   private final NumberSetting maxPitch = this.add(
      new NumberSetting("Max. Neigung", "Wie stark die Facecam beim Hoch-/Runterschauen mitkippt", 30.0, 0.0, 60.0, 5.0, "°")
   );
   private final BooleanSetting mirror = this.add(new BooleanSetting("Spiegeln", "Bild horizontal spiegeln", false));
   private final BooleanSetting frame = this.add(new BooleanSetting("Rahmen", "Rahmen um die Facecam", true));
   private final ColorSetting frameColor = this.add(new ColorSetting("Rahmen-Farbe", "Farbe des Rahmens", -7643914));
   private final BooleanSetting preview = this.add(new BooleanSetting("Vorschau im HUD", "Kleines eigenes Bild im HUD (Position im HUD-Editor)", true));
   private final HudBox previewBox = this.hudBox("Facecam-Vorschau", 0.5, 8.0);
   private final BooleanSetting stream = this.add(
      new BooleanSetting("An andere senden", "Deine Kamera für Meowl-Spieler auf diesem Server sichtbar machen (braucht Meowl Server)", false)
   );
   private final ModeSetting streamResolution = this.add(
      new ModeSetting("Stream-Auflösung", "Auflösung für andere (max. 320×240)", "160×120", "160×120", "240×180", "320×240")
   );
   private final NumberSetting streamFps = this.add(new NumberSetting("Stream-FPS", "Bilder pro Sekunde für andere", 6.0, 1.0, 10.0, 1.0, "FPS"));
   private final BooleanSetting showOthers = this.add(new BooleanSetting("Facecams anderer", "Facecams anderer Meowl-Spieler anzeigen", true));
   private final NumberSetting othersFps = this.add(new NumberSetting("Empfangs-FPS", "Wie oft Bilder anderer geladen werden", 5.0, 1.0, 10.0, 1.0, "FPS"));
   private final FacecamManager manager = new FacecamManager();

   public Facecam() {
      super("Facecam", "Webcam-Bild am Kopf (Kamera standardmäßig AUS, für andere nur auf Wunsch).", Category.MEOWL);
      this.section(Section.MEOWL);
      this.icon(Items.OBSERVER);
      this.previewBox.background.set(false);
      this.previewBox.background.setVisibility(() -> false);
      this.frameColor.setVisibility(this.frame::get);
      this.streamResolution.setVisibility(this.stream::get);
      this.streamFps.setVisibility(this.stream::get);
      this.othersFps.setVisibility(this.showOthers::get);
      this.add(new ActionSetting("Kamera wechseln", "Zur nächsten angeschlossenen Kamera wechseln", "Wechseln", this::nextCamera));
      ClientTickEvents.END_CLIENT_TICK
         .register(
            client -> {
               this.manager
                  .renderer()
                  .tick(
                     uuid -> this.manager.sync().isActive(uuid)
                        || client.player != null && uuid.equals(client.player.getUUID()) && this.manager.isCameraRunning()
                  );
               if (this.showOthers.get()) {
                  this.manager.sync().tickViewer(this.othersFps.getInt());
               } else {
                  this.manager.sync().clearViewer();
               }
            }
         );
      WorldCtx.afterEntities(this::renderAll);
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
         if (this.isEnabled()) {
            this.setEnabled(false);
         }

         this.manager.sync().clearViewer();
      }));
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nebula", "facecam_status"), (ctx, tick) -> this.drawStatus(ctx));
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   private void nextCamera() {
      Thread t = new Thread(() -> {
         List<String> list = CameraCapture.listCameras();
         this.mc.execute(() -> {
            if (list.isEmpty()) {
               ChatUtil.chat("§cKeine Kamera gefunden.");
            } else {
               int i = list.indexOf(this.camera.get());
               this.camera.set(list.get((i + 1) % list.size()));
               ChatUtil.chat("Kamera: §f" + this.camera.get() + " §7(" + list.size() + " gefunden)");
            }
         });
      }, "Meowl-Facecam-Suche");
      t.setDaemon(true);
      t.start();
   }

   private static int[] parse(String res) {
      String[] p = res.split("×");
      return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
   }

   @Override
   protected void onEnable() {
      this.manager.clearError();
      ChatUtil.actionBar("§cKamera wird eingeschaltet");
   }

   @Override
   protected void onDisable() {
      this.manager.stopCamera();
   }

   @Override
   public void onTick() {
      if (this.mc.player != null) {
         int[] res = parse(this.resolution.get());
         int[] sres = parse(this.streamResolution.get());
         boolean canStream = this.stream.get() && MeowlService.get().isConnected();
         this.manager.setConfig(new FacecamManager.Config(this.mirror.get(), canStream, sres[0], sres[1], this.streamFps.getInt()));
         UUID self = this.mc.player.getUUID();
         this.manager.ensureRunning(self, this.camera.get(), res[0], res[1], this.fps.getInt());
         String error = this.manager.capture().error();
         if (error != null) {
            ChatUtil.chat("§cFacecam: " + error);
            this.setEnabled(false);
         }
      }
   }

   private void renderAll(WorldCtx context) {
      if (this.mc.level != null && this.mc.player != null) {
         float tickDelta = this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

         for (Player player : this.mc.level.players()) {
            boolean self = player == this.mc.player;
            if (self
               ? this.isEnabled() && !this.mc.options.getCameraType().isFirstPerson()
               : this.showOthers.get()
                  && this.manager.sync().isActive(player.getUUID())
                  && !player.isInvisible()
                  && !(this.mc.player.distanceToSqr(player) > FacecamSyncManager.viewDistance() * FacecamSyncManager.viewDistance())) {
               this.manager
                  .renderer()
                  .render(
                     context,
                     player,
                     tickDelta,
                     this.position.get(),
                     this.size.get(),
                     this.frame.get(),
                     this.frameColor.get(),
                     this.distance.get(),
                     this.maxPitch.get()
                  );
            }
         }
      }
   }

   private void drawStatus(GuiGraphicsExtractor ctx) {
      if (this.manager.isCameraRunning()) {
         boolean streaming = this.manager.sync().isStreaming();
         String text = streaming
            ? "● LIVE – Meowl-Spieler sehen deine Kamera"
            : (this.manager.sync().isSending() ? "● KAMERA AKTIV – verbinde mit Meowl Server …" : "● KAMERA AKTIV (nur du)");
         int w = this.mc.font.width(text) + 8;
         Render2D.roundRect(ctx, 4, 4, w, 12, 3, -1072557024);
         ctx.text(this.mc.font, text, 8, 6, -1096636, false);
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      if (this.mc.player != null) {
         boolean live = this.manager.isCameraRunning();
         boolean editing = HudLayout.isEditing();
         if ((live || editing) && this.preview.get()) {
            Identifier tex = this.manager.renderer().textureOf(this.mc.player.getUUID());
            int[] dim = this.manager.renderer().sizeOf(this.mc.player.getUUID());
            boolean hasImage = live && tex != null && dim != null && dim[0] > 0;
            int w = 80;
            int h = hasImage ? 80 * dim[1] / dim[0] : 60;
            this.previewBox.begin(ctx, w + 2, h + 2, false);
            Render2D.roundRect(ctx, 0, 0, w + 2, h + 2, 2, this.previewBox.fade(this.frameColor.get()));
            if (hasImage) {
               ctx.blit(RenderPipelines.GUI_TEXTURED, tex, 1, 1, 0.0F, 0.0F, w, h, w, h);
            } else {
               ctx.fill(1, 1, w + 1, h + 1, -15592416);
               ctx.text(this.mc.font, "Facecam", (w + 2 - this.mc.font.width("Facecam")) / 2, h / 2 - 3, -7564628, false);
            }

            this.previewBox.end(ctx);
         }
      }
   }
}
