package com.lennart.nebula.nametag;

import com.lennart.nebula.gui.MeowlSprite;
import com.lennart.nebula.util.DynamicTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class NametagIcons {
   public static final Identifier MEOWL = Identifier.fromNamespaceAndPath("nebula", "nametag/meowl");
   private static boolean meowlUploaded;

   private NametagIcons() {
   }

   public static Identifier meowl() {
      if (!meowlUploaded) {
         meowlUploaded = true;
         int[] px = new int[256];

         for (int row = 0; row < 16; row++) {
            String line = MeowlSprite.ROWS[row];

            for (int col = 0; col < 16; col++) {
               px[row * 16 + col] = MeowlSprite.color(line.charAt(col));
            }
         }

         DynamicTextures.upload(MEOWL, null, px, 16, 16);
      }

      return MEOWL;
   }

   public static Identifier tierlist(String providerId, int screenPixels) {
      String variant = screenPixels <= 18 ? "16" : "32";
      return Identifier.fromNamespaceAndPath("nebula", "textures/tierlists/" + providerId + "_" + variant + ".png");
   }

   public static void gui(GuiGraphics ctx, Identifier tex, int x, int y, int size) {
      int texSize = tex.getPath().endsWith("_16.png") ? 16 : (tex.equals(MEOWL) ? 16 : 32);
      ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, size, size, texSize, texSize, texSize, texSize);
   }

   public static int guiScale() {
      return Math.max(1, Math.round((float)Minecraft.getInstance().getWindow().getGuiScale()));
   }
}
