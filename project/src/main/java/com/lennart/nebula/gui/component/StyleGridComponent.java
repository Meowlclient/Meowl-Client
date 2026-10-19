package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.cosmetic.PreviewOverrides;
import com.lennart.nebula.gui.CosmeticPreview;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.StyleSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Stil-Auswahl als Raster mit Vorschaubildern (wie bei Essential): jedes Feld zeigt deinen Charakter mit genau
 * diesem Stil. Anklicken wählt ihn.
 */
public class StyleGridComponent extends SettingComponent<StyleSetting> {
   private static final int HEADER = 18;
   private static final int GAP = 4;
   private static final int LABEL = 11;

   public StyleGridComponent(StyleSetting setting, Runnable onChange) {
      super(setting, onChange);
   }

   private int cols() {
      return Math.max(2, Math.min(6, (this.width - 20 + GAP) / (58 + GAP)));
   }

   private int tileW() {
      int c = this.cols();
      return (this.width - 20 - (c - 1) * GAP) / c;
   }

   private int tileH() {
      return this.tileW() + LABEL;
   }

   private int rows() {
      int c = this.cols();
      return (this.setting.getModes().size() + c - 1) / c;
   }

   @Override
   public int getHeight() {
      return HEADER + this.rows() * (this.tileH() + GAP) + 4;
   }

   private int tileX(int i) {
      return this.x + 10 + i % this.cols() * (this.tileW() + GAP);
   }

   private int tileY(int i) {
      return this.y + HEADER + i / this.cols() * (this.tileH() + GAP);
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      this.drawLabel(ctx, text, HEADER, this.width / 2);
      String cur = this.setting.get();
      ctx.drawString(text, trim(text, cur, this.width / 2 - 14), this.x + this.width - 10 - Math.min(text.width(cur), this.width / 2 - 14), this.y + 5, -3885571, false);
      List<String> modes = this.setting.getModes();
      int tw = this.tileW();
      int th = this.tileH();
      LocalPlayer player = Minecraft.getInstance().player;

      for (int i = 0; i < modes.size(); i++) {
         int tx = this.tileX(i);
         int ty = this.tileY(i);
         String mode = modes.get(i);
         boolean sel = this.setting.is(mode);
         boolean hot = Render2D.inside(mouseX, mouseY, tx, ty, tw, th);
         Render2D.roundRect(ctx, tx, ty, tw, th, 5, sel ? 0xFF8B5CF6 : (hot ? 0xFF3A4260 : 0xFF232838));
         Render2D.roundRect(ctx, tx + 1, ty + 1, tw - 2, th - 2, 4, hot ? 0xFF1E2436 : 0xFF161A26);
         if (player != null && visible(ctx, tx, ty, tw, th)) {
            this.drawPlayer(ctx, player, mode, tx + 1, ty + 1, tw - 2, tw - 2);
         }

         String label = trim(text, mode, tw - 4);
         ctx.drawString(text, label, tx + (tw - text.width(label)) / 2, ty + th - LABEL + 1, sel ? -1 : (hot ? -1578509 : -7564628), false);
      }

      if (player == null) {
         ctx.drawString(text, "Vorschaubilder in einer Welt", this.x + 10, this.y + HEADER + 4, -7564628, false);
      }
   }

   /** Nur Felder zeichnen, die im sichtbaren (Scroll-)Bereich liegen – spart viel Leistung. */
   private static boolean visible(GuiGraphics ctx, int x, int y, int w, int h) {
      Vector2f a = ctx.pose().transformPosition(x + w / 2.0F, y + 2.0F, new Vector2f());
      Vector2f b = ctx.pose().transformPosition(x + w / 2.0F, y + h - 2.0F, new Vector2f());
      return ctx.containsPointInScissor(Math.round(a.x), Math.round(a.y)) || ctx.containsPointInScissor(Math.round(b.x), Math.round(b.y));
   }

   private void drawPlayer(GuiGraphics ctx, LocalPlayer player, String style, int bx, int by, int bw, int bh) {
      try {
         EntityRenderDispatcher manager = Minecraft.getInstance().getEntityRenderDispatcher();
         EntityRenderer<? super LocalPlayer, ?> renderer = manager.getRenderer(player);
         EntityRenderState state = renderer.createRenderState(player, 1.0F);
         state.lightCoords = 15728880;
         state.shadowPieces.clear();
         state.outlineColor = 0;
         state.nameTag = null;
         float sway = CosmeticPreview.isPaused() ? 0.0F : (float)(System.currentTimeMillis() / 1000.0 * 35.0 % 360.0);
         if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = (this.setting.fromBehind() ? 20.0F : 155.0F) + sway;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.walkAnimationSpeed = 0.0F;
            living.walkAnimationPos = 0.0F;
            living.hasRedOverlay = false;
            living.deathTime = 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
         }

         PreviewOverrides.put(state, this.setting.target(), style);
         int size = Math.max(10, Math.round(bh / 2.35F));
         int feet = by + bh - 4;
         int x1 = bx;
         int x2 = bx + bw;
         int y1 = by;
         int y2 = by + bh;
         float cy = (y1 + y2) / 2.0F;
         Quaternionf camera = new Quaternionf().rotateX(-0.15F);
         Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).mul(camera);
         ctx.enableScissor(x1, y1, x2, y2);
         ctx.submitEntityRenderState(state, size, new Vector3f(0.0F, (feet - cy) / size, 0.0F), rotation, camera, x1, y1, x2, y2);
         ctx.disableScissor();
      } catch (RuntimeException var18) {
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == InputConstants.MOUSE_BUTTON_LEFT) {
         List<String> modes = this.setting.getModes();

         for (int i = 0; i < modes.size(); i++) {
            if (Render2D.inside(mouseX, mouseY, this.tileX(i), this.tileY(i), this.tileW(), this.tileH())) {
               this.setting.set(modes.get(i));
               this.onChange.run();
               return true;
            }
         }
      }

      return false;
   }
}
