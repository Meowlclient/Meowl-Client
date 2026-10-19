package com.lennart.nebula.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class DynamicTextures {
   private DynamicTextures() {
   }

   public static DynamicTexture upload(Identifier id, DynamicTexture existing, int[] argb, int w, int h) {
      DynamicTexture tex = existing;
      if (existing == null || existing.getPixels() == null || existing.getPixels().getWidth() != w || existing.getPixels().getHeight() != h) {
         if (existing != null) {
            Minecraft.getInstance().getTextureManager().release(id);
         }

         tex = new DynamicTexture(id::toString, w, h, false);
         Minecraft.getInstance().getTextureManager().register(id, tex);
      }

      NativeImage image = tex.getPixels();

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            image.setPixel(x, y, argb[y * w + x]);
         }
      }

      tex.upload();
      return tex;
   }

   public static void destroy(Identifier id) {
      Minecraft.getInstance().getTextureManager().release(id);
   }
}
