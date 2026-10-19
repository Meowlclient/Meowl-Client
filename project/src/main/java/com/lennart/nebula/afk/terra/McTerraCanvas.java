package com.lennart.nebula.afk.terra;

import com.lennart.nebula.afk.space.Tex;
import com.lennart.nebula.util.DynamicTextures;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/** {@link TerraCanvas} für Minecraft: alles über die GUI-Matrix, Bilder als dynamische Texturen, Skin vom Spieler. */
public final class McTerraCanvas implements TerraCanvas {
   private final Map<Integer, Identifier> ids = new HashMap<>();
   private final Map<Integer, DynamicTexture> textures = new HashMap<>();
   private final Map<Integer, int[]> sizes = new HashMap<>();
   private GuiGraphics ctx;
   private Identifier skin;

   public McTerraCanvas bind(GuiGraphics ctx, Identifier skin) {
      this.ctx = ctx;
      this.skin = skin;
      return this;
   }

   public void upload(int id, Tex tex) {
      Identifier ident = this.ids.computeIfAbsent(id, k -> Identifier.fromNamespaceAndPath("nebula", "afk_terra/t" + k));
      this.textures.put(id, DynamicTextures.upload(ident, this.textures.get(id), tex.px, tex.w, tex.h));
      this.sizes.put(id, new int[]{tex.w, tex.h});
   }

   public boolean has(int id) {
      return this.textures.containsKey(id);
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
   public void quad(int tex, float p0x, float p0y, float p1x, float p1y, float p3x, float p3y, int u, int v, int uw, int vh, int tint) {
      if (tint >>> 24 == 0 || uw == 0 || vh == 0) {
         return;
      }

      Identifier ident;
      int tw;
      int th;
      if (tex == SKIN) {
         ident = this.skin;
         tw = 64;
         th = 64;
      } else {
         ident = this.ids.get(tex);
         int[] size = this.sizes.get(tex);
         if (ident == null || size == null) {
            return;
         }

         tw = size[0];
         th = size[1];
      }

      if (ident == null) {
         return;
      }

      Matrix3x2fStack m = this.ctx.pose();
      m.pushMatrix();
      m.mul(new Matrix3x2f((p1x - p0x) / uw, (p1y - p0y) / uw, (p3x - p0x) / vh, (p3y - p0y) / vh, p0x, p0y));
      this.ctx.blit(RenderPipelines.GUI_TEXTURED, ident, 0, 0, (float)u, (float)v, uw, vh, uw, vh, tw, th, tint);
      m.popMatrix();
   }

   @Override
   public void text(String s, float x, float y, float scale, int argb, boolean shadow) {
      if (argb >>> 24 < 8) {
         return;
      }

      Matrix3x2fStack m = this.ctx.pose();
      m.pushMatrix();
      m.translate(x, y);
      m.scale(scale, scale);
      this.ctx.drawString(Minecraft.getInstance().font, s, 0, 0, argb, shadow);
      m.popMatrix();
   }

   @Override
   public int textWidth(String s) {
      return Minecraft.getInstance().font.width(s);
   }

   public void destroy() {
      for (Identifier ident : this.ids.values()) {
         DynamicTextures.destroy(ident);
      }

      this.ids.clear();
      this.textures.clear();
      this.sizes.clear();
   }
}
