package com.lennart.nebula.nametag;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class LabelCanvas implements NameCanvas {
   private final SubmitNodeCollector queue;
   private final PoseStack matrices;
   private final boolean notSneaking;
   private final int light;
   private final int backgroundColor;
   private boolean polygonOffset;

   public LabelCanvas(SubmitNodeCollector queue, PoseStack matrices, boolean notSneaking, int light, int backgroundColor) {
      this.queue = queue;
      this.matrices = matrices;
      this.notSneaking = notSneaking;
      this.light = light;
      this.backgroundColor = backgroundColor;
   }

   public LabelCanvas polygonOffset() {
      this.polygonOffset = true;
      return this;
   }

   private DisplayMode textLayer() {
      return this.polygonOffset ? DisplayMode.POLYGON_OFFSET : DisplayMode.NORMAL;
   }

   private RenderType iconLayer(Identifier texture) {
      return this.polygonOffset ? RenderTypes.textPolygonOffset(texture) : RenderTypes.text(texture);
   }

   @Override
   public org.joml.Matrix4f pose() {
      return this.matrices.last().pose();
   }

   @Override
   public int backgroundColor() {
      return this.backgroundColor;
   }

   private static int withAlpha(int color, int alpha) {
      int a = Math.min(color >>> 24 & 0xFF, alpha);
      return a << 24 | color & 16777215;
   }

   private static int opaque(int color) {
      return color >>> 24 == 0 ? color | 0xFF000000 : color;
   }

   @Override
   public void text(FormattedCharSequence text, float x, float y, int color) {
      color = opaque(color);
      if (this.notSneaking) {
         // wie Vanilla: nicht schleichende Spieler sieht man (halb durchsichtig) auch durch Blöcke
         this.queue.submitText(this.matrices, x, y, text, false, DisplayMode.SEE_THROUGH, this.light, withAlpha(color, 128), 0, 0);
         this.queue.submitText(this.matrices, x, y, text, false, this.textLayer(), LightTexture.lightCoordsWithEmission(this.light, 2), color, 0, 0);
      } else {
         this.queue.submitText(this.matrices, x, y, text, false, this.textLayer(), this.light, withAlpha(color, 128), 0, 0);
      }
   }

   @Override
   public void vanillaName(FormattedCharSequence text, float x, float y, int color, boolean background) {
      color = opaque(color);
      int bg = background ? this.backgroundColor : 0;
      if (this.notSneaking) {
         this.queue.submitText(this.matrices, x, y, text, false, DisplayMode.SEE_THROUGH, this.light, withAlpha(color, 128), bg, 0);
         this.queue.submitText(this.matrices, x, y, text, false, DisplayMode.NORMAL, LightTexture.lightCoordsWithEmission(this.light, 2), color, 0, 0);
      } else {
         this.queue.submitText(this.matrices, x, y, text, false, DisplayMode.NORMAL, this.light, withAlpha(color, 128), bg, 0);
      }
   }

   @Override
   public void icon(Identifier texture, float x, float y, float size, int tint) {
      tint = opaque(tint);
      if (this.notSneaking) {
         this.quad(RenderTypes.textSeeThrough(texture), x, y, size, withAlpha(tint, 128), this.light);
         this.quad(this.iconLayer(texture), x, y, size, tint, LightTexture.lightCoordsWithEmission(this.light, 2));
      } else {
         this.quad(this.iconLayer(texture), x, y, size, withAlpha(tint, 128), this.light);
      }
   }

   private void quad(RenderType layer, float x, float y, float size, int color, int light) {
      this.queue.submitCustomGeometry(this.matrices, layer, (entry, vc) -> {
         vc.addVertex(entry, x, y, 0.0F).setColor(color).setUv(0.0F, 0.0F).setLight(light);
         vc.addVertex(entry, x, y + size, 0.0F).setColor(color).setUv(0.0F, 1.0F).setLight(light);
         vc.addVertex(entry, x + size, y + size, 0.0F).setColor(color).setUv(1.0F, 1.0F).setLight(light);
         vc.addVertex(entry, x + size, y, 0.0F).setColor(color).setUv(1.0F, 0.0F).setLight(light);
      });
   }

   @Override
   public void background(float x1, float y1, float x2, float y2, int color) {
      if (color >>> 24 != 0 && x2 > x1) {
         int l = this.light;
         this.queue.submitCustomGeometry(this.matrices, RenderTypes.textBackground(), (entry, vc) -> {
            vc.addVertex(entry, x1, y1, -0.01F).setColor(color).setLight(l);
            vc.addVertex(entry, x1, y2, -0.01F).setColor(color).setLight(l);
            vc.addVertex(entry, x2, y2, -0.01F).setColor(color).setLight(l);
            vc.addVertex(entry, x2, y1, -0.01F).setColor(color).setLight(l);
         });
      }
   }
}
