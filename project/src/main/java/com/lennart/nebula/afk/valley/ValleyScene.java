package com.lennart.nebula.afk.valley;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.afk.space.McSpaceCanvas;
import com.lennart.nebula.afk.space.Tex;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * AFK-Szene „Tal des Endes“: Landschaft wird einmalig im Hintergrund aus Blocktexturen des aktiven Ressourcenpakets
 * gerendert, danach läuft der Kampf von Naruto und Sasuke live.
 */
public final class ValleyScene {
   private final McSpaceCanvas canvas = new McSpaceCanvas();
   private CompletableFuture<ValleyTracer> building;
   private ValleyTracer built;
   private boolean uploaded;
   private ValleyFight fight;
   private ValleyView view;
   private ValleySounds sounds;
   private float time;
   private boolean battle = true;
   private boolean effects = true;

   public void prepare() {
      if (this.building == null) {
         Map<String, Tex> blocks = loadBlocks();
         this.building = CompletableFuture.supplyAsync(() -> {
            ValleyTracer t = new ValleyTracer(blocks::get);
            t.render();
            return t;
         });
      }
   }

   public boolean ready() {
      if (this.built == null && this.building != null && this.building.isDone()) {
         try {
            this.built = this.building.join();
         } catch (RuntimeException var2) {
            NebulaClient.LOGGER.warn("AFK-Szene Tal des Endes konnte nicht erzeugt werden", var2);
            this.building = null;
         }
      }

      return this.built != null;
   }

   private static Map<String, Tex> loadBlocks() {
      Map<String, Tex> out = new HashMap<>();
      Minecraft mc = Minecraft.getInstance();

      for (String name : ValleyTracer.BLOCKS) {
         try {
            Optional<Resource> res = mc.getResourceManager().getResource(Identifier.withDefaultNamespace("textures/block/" + name + ".png"));
            if (res.isPresent()) {
               try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                  Tex t = new Tex(img.getWidth(), img.getHeight());

                  for (int y = 0; y < t.h; y++) {
                     for (int x = 0; x < t.w; x++) {
                        t.px[y * t.w + x] = img.getPixel(x, y);
                     }
                  }

                  out.put(name, t);
               }
            }
         } catch (Exception var11) {
            NebulaClient.LOGGER.debug("Blocktextur {} fehlt: {}", name, var11.toString());
         }
      }

      return out;
   }

   private void upload() {
      ValleyTracer t = this.built;
      this.canvas.upload(ValleyView.BG, t.image);
      this.canvas.upload(ValleyView.FALL, t.fallAtlas);
      this.canvas.upload(ValleyView.RIVER, t.riverAtlas);
      this.view = new ValleyView(t);
      this.restart();
      this.uploaded = true;
   }

   /** Neuer Kampf beim nächsten AFK (Texturen bleiben). */
   public void restart() {
      if (this.built != null) {
         this.fight = new ValleyFight(this.built, System.nanoTime());
         this.fight.configure(this.battle, this.effects);
         this.fight.prewarm(1.0F);
         if (this.sounds != null) {
            this.fight.setEvents(this.sounds);
         }
      }
   }

   public ValleyView view() {
      return this.view;
   }

   public void configure(boolean battle, boolean effects) {
      this.battle = battle;
      this.effects = effects;
      if (this.fight != null) {
         this.fight.configure(battle, effects);
      }
   }

   public void sounds(boolean enabled, float master, float explosions, float shots, float ambience, float fade) {
      if (this.sounds == null) {
         this.sounds = new ValleySounds();
      }

      if (this.fight != null) {
         this.fight.setEvents(this.sounds);
      }

      this.sounds.configure(enabled, master, explosions, shots, ambience);
      this.sounds.update(fade);
   }

   /** Zeichnet die Szene; false, solange sie noch erzeugt wird. */
   public boolean render(GuiGraphics ctx, float dt, boolean cameraMove, float fade) {
      if (!this.ready()) {
         return false;
      } else {
         if (!this.uploaded) {
            this.upload();
         }

         this.time += dt;
         this.fight.update(Math.min(dt, 0.1F));
         this.view.render(this.canvas.bind(ctx), ctx.guiWidth(), ctx.guiHeight(), this.fight, this.time, dt, cameraMove, fade);
         return true;
      }
   }

   public void stopSounds() {
      if (this.sounds != null) {
         this.sounds.stop();
      }
   }

   public void destroy() {
      this.stopSounds();
      this.canvas.destroy();
      this.uploaded = false;
   }
}
