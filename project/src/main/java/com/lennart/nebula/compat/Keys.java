package com.lennart.nebula.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Tasten- und Maustastenabfrage, unabhängig von der Minecraft-Version (1.21.11: GLFW, 26.x: SDL). */
public final class Keys {
   private Keys() {
   }

   public static boolean keyDown(int key) {
      return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
   }

   public static boolean mouseDown(int button) {
      return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == 1;
   }

   /** Strg (bzw. Cmd) in den Modifier-Bits eines Tastendrucks – 1.21.11: GLFW (CONTROL = 2, SUPER = 8). */
   public static boolean ctrl(int modifiers) {
      return (modifiers & 2) != 0 || (modifiers & 8) != 0;
   }

   /** Shift in den Modifier-Bits eines Tastendrucks – GLFW: SHIFT = 1. */
   public static boolean shift(int modifiers) {
      return (modifiers & 1) != 0;
   }

   public static boolean shiftDown() {
      return keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
   }
}
