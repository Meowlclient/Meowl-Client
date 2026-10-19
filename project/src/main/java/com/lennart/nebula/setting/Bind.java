package com.lennart.nebula.setting;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

public record Bind(Bind.Type type, int code) {
   public static final Bind NONE = new Bind(Bind.Type.NONE, -1);

   public static Bind keyboard(int glfwKey) {
      return new Bind(Bind.Type.KEYBOARD, glfwKey);
   }

   public static Bind mouse(int glfwButton) {
      return new Bind(Bind.Type.MOUSE, glfwButton);
   }

   public boolean isNone() {
      return this.type == Bind.Type.NONE || this.code < 0;
   }

   public boolean isPressed() {
      if (this.isNone()) {
         return false;
      } else {

         return switch (this.type) {
            case NONE -> false;
            case KEYBOARD -> com.lennart.nebula.compat.Keys.keyDown(this.code);
            case MOUSE -> com.lennart.nebula.compat.Keys.mouseDown(this.code);
         };
      }
   }

   public boolean matches(Key key) {
      if (!this.isNone() && key != null) {
         InputConstants.Type keyType = key.getType();

         return switch (this.type) {
            case NONE -> false;
            case KEYBOARD -> keyType == InputConstants.Type.KEYSYM && key.getValue() == this.code;
            case MOUSE -> keyType == InputConstants.Type.MOUSE && key.getValue() == this.code;
         };
      } else {
         return false;
      }
   }

   public String getDisplayName() {
      return switch (this.type) {
         case NONE -> "Keine";
         case KEYBOARD -> InputConstants.Type.KEYSYM.getOrCreate(this.code).getDisplayName().getString();
         case MOUSE -> InputConstants.Type.MOUSE.getOrCreate(this.code).getDisplayName().getString();
      };
   }

   public static enum Type {
      NONE,
      KEYBOARD,
      MOUSE;
   }
}
