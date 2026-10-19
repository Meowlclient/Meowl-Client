package com.lennart.nebula.afk.space;

import com.lennart.nebula.util.DynamicTextures;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** {@link SpaceCanvas} für Minecraft: Gleitkomma-Rechtecke über die GUI-Matrix, Bilder als dynamische Texturen. */
public final class McSpaceCanvas implements SpaceCanvas {
   private final Map<String, Identifier> ids = new HashMap<>();
   private final Map<String, DynamicTexture> textures = new HashMap<>();
   private final Map<String, int[]> sizes = new HashMap<>();
   private GuiGraphicsExtractor ctx;

   public McSpaceCanvas bind(GuiGraphicsExtractor ctx) {
      this.ctx = ctx;
      return this;
   }

   @Override
   public void fill(float x1, float y1, float x2, float y2, int argb) {
      if (x2 > x1 && y2 > y1 && argb >>> 24 != 0) {
         Matrix3x2fStack m = this.ctx.pose();
         m.pushMatrix();
         m.translate(x1, y1);
         m.scale(x2 - x1, y2 - y1);
         this.ctx.fill(0, 0, 1, 1, argb);
         m.popMatrix();
      }
   }

   @Override
   public void gradient(float x1, float y1, float x2, float y2, int top, int bottom) {
      if (x2 > x1 && y2 > y1) {
         Matrix3x2fStack m = this.ctx.pose();
         m.pushMatrix();
         m.translate(x1, y1);
         m.scale((x2 - x1) / 100.0F, (y2 - y1) / 100.0F);
         this.ctx.fillGradient(0, 0, 100, 100, top, bottom);
         m.popMatrix();
      }
   }

   @Override
   public void line(float x1, float y1, float x2, float y2, float width, int argb) {
      float len = (float)Math.hypot(x2 - x1, y2 - y1);
      if (len >= 0.01F && argb >>> 24 != 0) {
         Matrix3x2fStack m = this.ctx.pose();
         m.pushMatrix();
         m.translate(x1, y1);
         m.rotate((float)Math.atan2(y2 - y1, x2 - x1));
         m.scale(len, width);
         m.translate(0.0F, -0.5F);
         this.ctx.fill(0, 0, 1, 1, argb);
         m.popMatrix();
      }
   }

   @Override
   public void upload(String id, Tex tex) {
      Identifier ident = this.ids.computeIfAbsent(id, k -> Identifier.fromNamespaceAndPath("nebula", "afk_space/" + k));
      this.textures.put(id, DynamicTextures.upload(ident, this.textures.get(id), tex.px, tex.w, tex.h));
      this.sizes.put(id, new int[]{tex.w, tex.h});
   }

   @Override
   public boolean has(String id) {
      return this.textures.containsKey(id);
   }

   @Override
   public void image(String id, float cx, float cy, float w, float h, float u0, float v0, float u1, float v1, float angle, boolean flip, int tint) {
      Identifier ident = this.ids.get(id);
      int[] size = this.sizes.get(id);
      int rw = Math.round(u1 - u0);
      int rh = Math.round(v1 - v0);
      if (ident != null && size != null && rw > 0 && rh > 0 && w > 0.0F && h > 0.0F && tint >>> 24 != 0) {
         Matrix3x2fStack m = this.ctx.pose();
         m.pushMatrix();
         m.translate(cx, cy);
         if (angle != 0.0F) {
            m.rotate(angle);
         }

         m.scale(w / rw, h / rh);
         m.translate(-rw / 2.0F, -rh / 2.0F);
         int u = Math.round(u0);
         this.ctx.blit(RenderPipelines.GUI_TEXTURED, ident, 0, 0, flip ? u + rw : u, Math.round(v0), rw, rh, flip ? -rw : rw, rh, size[0], size[1], tint);
         m.popMatrix();
      }
   }

   /** Alle Texturen freigeben. */
   public void destroy() {
      for (Identifier ident : this.ids.values()) {
         DynamicTextures.destroy(ident);
      }

      this.ids.clear();
      this.textures.clear();
      this.sizes.clear();
   }
}
