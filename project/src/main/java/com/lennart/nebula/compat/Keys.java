package com.lennart.nebula.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** 26.x-Fassung: Eingaben laufen über SDL. */
public final class Keys {
   private Keys() {
   }

   public static boolean keyDown(int key) {
      // SDL-Tastentabelle: ungültige Nummern (z.B. -1 = keine Taste) nicht abfragen
      return key > 0 && key < 512 && InputConstants.isKeyDown(key);
   }

   public static boolean mouseDown(int button) {
      Minecraft mc = Minecraft.getInstance();
      if (button == InputConstants.MOUSE_BUTTON_LEFT) {
         return mc.mouseHandler.isLeftPressed();
      } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
         return mc.mouseHandler.isRightPressed();
      } else {
         return button == InputConstants.MOUSE_BUTTON_MIDDLE && mc.mouseHandler.isMiddlePressed();
      }
   }

   public static boolean shiftDown() {
      return keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
   }
}
