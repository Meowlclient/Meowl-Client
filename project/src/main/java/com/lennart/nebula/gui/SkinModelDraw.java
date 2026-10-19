package com.lennart.nebula.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Spieler-Skin als 3D-Modell im Menü zeichnen (ohne Welt), mit leichter Idle-Bewegung. */
public final class SkinModelDraw {
   private PlayerModel wide;
   private PlayerModel slim;

   private PlayerModel model(boolean slimArms) {
      if (this.wide == null) {
         var models = Minecraft.getInstance().getEntityModels();
         this.wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
         this.slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
      }

      return slimArms ? this.slim : this.wide;
   }

   /** rotX/rotY in Grad; der Bereich x0..y1 ist das Zeichenfeld (wie beim Skin-Widget von Minecraft). */
   public void draw(GuiGraphics ctx, PlayerSkin skin, float rotX, float rotY, int x0, int y0, int x1, int y1, float t) {
      PlayerModel m = this.model(skin.model() == PlayerModelType.SLIM);
      pose(m.root(), t);
      float scale = 0.97F * (y1 - y0) / 2.125F;
      ctx.submitSkinRenderState(m, skin.body().texturePath(), scale, rotX, rotY, -1.0625F, x0, y0, x1, y1);
   }

   /** Ragdoll-Fassung: freie Bereichsgröße (zum Drehen), Modellhöhe bestimmt die Größe. */
   public void drawRag(GuiGraphics ctx, PlayerSkin skin, float rotX, float rotY, int x0, int y0, int x1, int y1, float modelH, float t, SkinRag rag) {
      PlayerModel m = this.model(skin.model() == PlayerModelType.SLIM);
      SkinRag.apply(m.root(), t, rag);
      float scale = 0.97F * modelH / 2.125F;
      ctx.submitSkinRenderState(m, skin.body().texturePath(), scale, rotX, rotY, -1.0625F, x0, y0, x1, y1);
   }

   /** Ruhige Idle-Animation: Arme schwingen leicht, Kopf schaut sich langsam um. */
   static void pose(ModelPart root, float t) {
      try {
         root.zRot = 0.0F;
         ModelPart head = root.getChild("head");
         ModelPart ra = root.getChild("right_arm");
         ModelPart la = root.getChild("left_arm");
         head.yRot = (float)Math.sin(t * 0.5) * 0.35F;
         head.xRot = (float)Math.sin(t * 0.37) * 0.08F;
         ra.zRot = 0.06F + (float)Math.sin(t * 1.3) * 0.04F;
         la.zRot = -0.06F - (float)Math.sin(t * 1.3) * 0.04F;
         ra.xRot = (float)Math.sin(t * 0.9) * 0.06F;
         la.xRot = -(float)Math.sin(t * 0.9) * 0.06F;
      } catch (RuntimeException var5) {
      }
   }
}
