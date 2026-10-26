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

   /**
    * Strg (bzw. Cmd) in den Modifier-Bits – 26.x läuft auf SDL3: LCTRL 0x40, RCTRL 0x80, LGUI 0x400, RGUI 0x800 (nicht die
    * GLFW-Bits 2/8 wie in 1.21.11). Zur Sicherheit zusätzlich der Tastenzustand.
    */
   public static boolean ctrl(int modifiers) {
      return (modifiers & 0x00C0) != 0 || (modifiers & 0x0C00) != 0 || keyDown(InputConstants.KEY_LCONTROL) || keyDown(InputConstants.KEY_RCONTROL);
   }

   /** Shift – SDL3: LSHIFT 0x01, RSHIFT 0x02. */
   public static boolean shift(int modifiers) {
      return (modifiers & 0x0003) != 0 || shiftDown();
   }

   public static boolean shiftDown() {
      return keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
   }
}
