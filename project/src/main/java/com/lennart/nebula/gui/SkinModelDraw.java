package com.lennart.nebula.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** 26.x-Fassung: das Skin-Modell ist hier ein einfaches Model.Simple. */
public final class SkinModelDraw {
   private Model.Simple wide;
   private Model.Simple slim;

   private Model.Simple model(boolean slimArms) {
      if (this.wide == null) {
         var models = Minecraft.getInstance().getEntityModels();
         this.wide = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
         this.slim = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
      }

      return slimArms ? this.slim : this.wide;
   }

   public void draw(GuiGraphicsExtractor ctx, PlayerSkin skin, float rotX, float rotY, int x0, int y0, int x1, int y1, float t) {
      Model.Simple m = this.model(skin.model() == PlayerModelType.SLIM);
      pose(m.root(), t);
      float scale = 0.97F * (y1 - y0) / 2.125F;
      ctx.skin(m, skin.body().texturePath(), scale, rotX, rotY, -1.0625F, x0, y0, x1, y1);
   }

   /** Ragdoll-Fassung: freie Bereichsgröße (zum Drehen), Modellhöhe bestimmt die Größe. */
   public void drawRag(GuiGraphicsExtractor ctx, PlayerSkin skin, float rotX, float rotY, int x0, int y0, int x1, int y1, float modelH, float t, SkinRag rag) {
      Model.Simple m = this.model(skin.model() == PlayerModelType.SLIM);
      SkinRag.apply(m.root(), t, rag);
      float scale = 0.97F * modelH / 2.125F;
      ctx.skin(m, skin.body().texturePath(), scale, rotX, rotY, -1.0625F, x0, y0, x1, y1);
   }

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
