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

   public static boolean shiftDown() {
      return keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
   }
}
