package com.lennart.nebula.afk.terra;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.afk.pc.PcAssets;
import com.lennart.nebula.afk.space.Tex;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * AFK-Szene „Terraria-Welt“: eine endlose Terraria-Welt aus Minecraft-Blöcken als Diorama-Schnitt. Texturen werden einmal
 * aus dem aktiven Ressourcenpaket geladen und im Hintergrund zu Atlas und Parallax-Ebenen gebacken, danach läuft das
 * Abenteuer deines Helden live.
 */
public final class TerraScene {
   private final McTerraCanvas canvas = new McTerraCanvas();
   private CompletableFuture<Built> building;
   private Built built;
   private boolean uploaded;
   private TerraSim sim;
   private TerraView view;
   private TerraSounds sounds;

   private record Built(TerraAtlas atlas, TerraBackdrop backdrop) {
   }

   public void prepare() {
      if (this.building == null) {
         Map<String, Tex> tex = PcAssets.load(TerraTiles.textures());
         long seed = System.nanoTime();
         this.building = CompletableFuture.supplyAsync(() -> new Built(new TerraAtlas(tex), new TerraBackdrop(tex, seed)));
      }
   }

   public boolean ready() {
      if (this.built == null && this.building != null && this.building.isDone()) {
         try {
            this.built = this.building.join();
         } catch (RuntimeException var2) {
            NebulaClient.LOGGER.warn("AFK-Szene Terraria-Welt konnte nicht erzeugt werden", var2);
            this.building = null;
         }
      }

      return this.built != null;
   }

   private void upload() {
      Built b = this.built;
      this.canvas.upload(TerraCanvas.ATLAS, b.atlas.image);
      this.canvas.upload(TerraCanvas.BG_FAR, b.backdrop.far);

      for (int i = 0; i < TerraWorld.BIOMES; i++) {
         this.canvas.upload(16 + i * 2, b.backdrop.mid[i]);
         this.canvas.upload(17 + i * 2, b.backdrop.near[i]);
      }

      this.view = new TerraView(b.atlas);
      this.uploaded = true;
   }

   /** Neue Welt für den nächsten AFK; {@code start}: 0 Morgen, 1 Abend, 2 Nacht. */
   public void restart(int start, boolean battle) {
      this.sim = new TerraSim(System.nanoTime());
      this.sim.configure(battle);
      this.sim.startAt(start);
      this.sim.prewarm(2.0F);
      if (this.sounds != null) {
         this.sim.setEvents(this.sounds);
      }

      if (this.view != null) {
         this.view = new TerraView(this.built.atlas);
      }
   }

   public void sounds(boolean enabled, float master, float fx, float fade) {
      if (this.sounds == null) {
         this.sounds = new TerraSounds();
      }

      if (this.sim != null) {
         this.sim.setEvents(this.sounds);
      }

      this.sounds.configure(enabled, master, fx, fade);
   }

   /** Zeichnet die Szene; false, solange sie noch erzeugt wird. */
   public boolean render(GuiGraphicsExtractor ctx, float dt, boolean cameraMove, boolean battle, float inset, boolean hud) {
      if (!this.ready()) {
         return false;
      }

      if (!this.uploaded) {
         this.upload();
      }

      if (this.sim == null) {
         this.restart(0, battle);
      }

      this.sim.configure(battle);
      this.sim.update(Math.min(dt, 0.1F));
      Minecraft mc = Minecraft.getInstance();
      Identifier skin = mc.player != null ? mc.player.getSkin().body().texturePath() : null;
      McTerraCanvas c = this.canvas.bind(ctx, skin);
      if (this.view.mapDirty) {
         this.view.mapDirty = false;
         c.upload(TerraCanvas.MAP, this.view.map);
      }

      float ratio = Math.max(1, Math.round((float)mc.getWindow().getGuiScale()));
      this.view.render(c, ctx.guiWidth(), ctx.guiHeight(), ratio, this.sim, dt, cameraMove, inset, hud);
      return true;
   }

   public void stopSounds() {
      if (this.sounds != null) {
         this.sounds.configure(false, 0.0F, 0.0F, 0.0F);
      }
   }

   public void destroy() {
      this.stopSounds();
      this.canvas.destroy();
      this.uploaded = false;
   }
}
