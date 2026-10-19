package com.lennart.nebula.gui;

import com.lennart.nebula.module.mods.PlasmaMode;
import com.lennart.nebula.plasma.PlasmaSword;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Hotbar-/Inventar-Symbol für Schwerter, wenn das Modul „Schwerter“ an ist. Die Bilder sind aus den echten 3D-Modellen
 * gebacken (test/icons/IconBake.java) und liegen diagonal wie Minecraft-Schwerter genau im 16x16-Feld.
 */
public final class SwordIcon {
   /** Gebackene Auflösungen (Pixel pro Symbol); gewählt wird die zur GUI-Größe passende, damit es scharf bleibt. */
   private static final int[] SIZES = {16, 32, 48, 64, 80, 96};
   private static final java.util.Map<String, Identifier> CACHE = new java.util.HashMap<>();
   private static int size = 32;

   private SwordIcon() {
   }

   private static Identifier tex(String name) {
      return CACHE.computeIfAbsent(name + "_" + size, k -> Identifier.fromNamespaceAndPath("nebula", "textures/item/swords/" + k + ".png"));
   }
   /** Zeichnet das Symbol statt des normalen Items; false = normales Item zeichnen. */
   public static boolean draw(GuiGraphics ctx, ItemStack stack, int x, int y) {
      if (!PlasmaMode.isSword(stack)) {
         return false;
      }

      // nur Hotbar/Inventar – Symbole in Meowl-Menüs (z.B. Kategorie PvP) bleiben normale Schwerter
      var screen = Minecraft.getInstance().screen;
      if (screen != null && screen.getClass().getName().startsWith("com.lennart.nebula.")) {
         return false;
      }

      int[] info = PlasmaMode.iconInfo();
      if (info == null) {
         return false;
      }

      int want = 16 * Math.max(1, Math.round((float)net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()));
      size = SIZES[0];
      for (int s : SIZES) {
         if (s <= want) {
            size = s;
         }
      }

      int finish = Math.max(0, Math.min(PlasmaSword.FINISHES.length - 1, info[2]));
      switch (info[0]) {
         case PlasmaSword.KARAMBIT -> icon(ctx, tex("karambit_" + finish), x, y, -1);
         case PlasmaSword.BUTTERFLY -> icon(ctx, tex("butterfly_" + finish), x, y, -1);
         case PlasmaSword.KATANA -> icon(ctx, tex("katana"), x, y, -1);
         default -> {
            icon(ctx, tex("saber_blade"), x, y, 0xFF000000 | info[1]);
            icon(ctx, tex("saber_hilt"), x, y, -1);
         }
      }

      return true;
   }

   private static void icon(GuiGraphics ctx, Identifier tex, int x, int y, int tint) {
      ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, 16, 16, size, size, size, size, tint);
   }
}