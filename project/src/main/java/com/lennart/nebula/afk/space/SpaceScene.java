package com.lennart.nebula.afk.space;

import com.lennart.nebula.NebulaClient;
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
 * AFK-Szene „Sternenschlacht“: Brücke, Planet und Schiffe werden einmalig im Hintergrund erzeugt (Blocktexturen
 * kommen aus dem aktiven Ressourcenpaket), danach läuft die Schlacht live.
 */
public final class SpaceScene {
   private static final String[] BLOCKS = {
      "smooth_stone", "polished_andesite", "iron_block", "light_gray_concrete", "gray_concrete", "black_concrete", "polished_deepslate",
      "polished_blackstone", "sea_lantern", "white_concrete", "redstone_lamp_on", "light_blue_concrete", "lime_concrete", "purple_concrete",
      "glowstone", "deepslate_tiles", "ochre_froglight_top", "cyan_stained_glass"
   };
   private final McSpaceCanvas canvas = new McSpaceCanvas();
   private final PlanetPainter.Planet planet;
   private final int density;
   private CompletableFuture<Built> building;
   private Built built;
   private boolean uploaded;
   private SpaceBattle battle;
   private SpaceView view;
   private SpaceSounds sounds;
   private float time;

   private record Built(BridgeTracer bridge, PlanetPainter planet, ShipSprites ships) {
   }

   public SpaceScene(PlanetPainter.Planet planet, int density) {
      this.planet = planet;
      this.density = density;
   }

   public PlanetPainter.Planet planet() {
      return this.planet;
   }

   public int density() {
      return this.density;
   }

   /** Startet die Erzeugung (Blocktexturen werden auf dem Render-Thread gelesen, der Rest im Hintergrund). */
   public void prepare() {
      if (this.building == null) {
         Map<String, Tex> blocks = loadBlocks();
         long seed = System.nanoTime();
         this.building = CompletableFuture.supplyAsync(() -> {
            BridgeTracer bridge = new BridgeTracer(blocks::get, this.planet.light);
            bridge.render();
            PlanetPainter painter = new PlanetPainter(this.planet, seed);
            painter.render();
            ShipSprites ships = new ShipSprites(blocks::get);
            ships.bakeAll();
            return new Built(bridge, painter, ships);
         });
      }
   }

   public boolean ready() {
      if (this.built == null && this.building != null && this.building.isDone()) {
         try {
            this.built = this.building.join();
         } catch (RuntimeException var2) {
            NebulaClient.LOGGER.warn("AFK-Sternenschlacht konnte nicht erzeugt werden", var2);
            this.building = null;
         }
      }

      return this.built != null;
   }

   private static Map<String, Tex> loadBlocks() {
      Map<String, Tex> out = new HashMap<>();
      Minecraft mc = Minecraft.getInstance();

      for (String name : BLOCKS) {
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
      Built b = this.built;
      this.canvas.upload(SpaceView.BRIDGE, b.bridge.image);
      this.canvas.upload(SpaceView.WINDOW_LIGHT, b.bridge.windowLight);
      this.canvas.upload(SpaceView.ALARM_LIGHT, b.bridge.alarmLight);
      this.canvas.upload(SpaceView.PLANET, b.planet.image);
      this.canvas.upload(SpaceView.PLANET_GLOW, b.planet.glow);

      for (int k = 0; k < ShipSprites.COUNT; k++) {
         this.canvas.upload(SpaceView.shipId(k), b.ships.sprites[k].image);
      }

      this.battle = new SpaceBattle(b.ships, this.density, System.nanoTime());
      this.battle.prewarm(12.0F);
      this.view = new SpaceView(b.ships, b.bridge.blinkers, this.planet.light);
      this.uploaded = true;
   }

   /** Neue Schlacht beim nächsten AFK (Texturen bleiben). */
   public void restartBattle() {
      if (this.built != null && this.uploaded) {
         this.battle = new SpaceBattle(this.built.ships, this.density, System.nanoTime());
         this.battle.prewarm(12.0F);
      }
   }

   public SpaceView view() {
      return this.view;
   }

   public void configure(boolean battle, boolean flyers, boolean explosions, boolean alarms) {
      if (this.battle != null) {
         this.battle.configure(battle, flyers, explosions, alarms);
      }
   }

   public void sounds(boolean enabled, float master, float explosions, float shots, float ambience, float fade) {
      if (this.sounds == null) {
         this.sounds = new SpaceSounds();
      }

      if (this.battle != null) {
         this.battle.setEvents(this.sounds);
      }

      this.sounds.configure(enabled, master, explosions, shots, ambience);
      this.sounds.update(fade);
   }

   /** Zeichnet die Szene; false, solange sie noch erzeugt wird. */
   public boolean render(GuiGraphics ctx, float dt, boolean cameraMove, float fade, boolean simulate) {
      if (!this.ready()) {
         return false;
      } else {
         if (!this.uploaded) {
            this.upload();
         }

         this.time += dt;
         if (simulate) {
            this.battle.update(Math.min(dt, 0.1F));
         }

         this.view.render(this.canvas.bind(ctx), ctx.guiWidth(), ctx.guiHeight(), this.battle, this.time, cameraMove, fade);
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
