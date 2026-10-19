package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.ClientSettings;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Randloses Vollbild: statt exklusivem Vollbild ein rahmenloses Fenster über den ganzen Bildschirm (1 Pixel höher, damit
 * Windows es nicht doch als Vollbild-Spiel behandelt). Dann bremst ein Discord-Stream Minecraft nicht mehr aus.
 */
@Mixin({Window.class})
public abstract class BorderlessFullscreenMixin {
   @Shadow
   @Final
   private long handle;
   @Shadow
   private boolean fullscreen;
   @Shadow
   @Final
   private ScreenManager screenManager;
   @Shadow
   private int x;
   @Shadow
   private int y;
   @Shadow
   private int width;
   @Shadow
   private int height;
   @Shadow
   private int windowedX;
   @Shadow
   private int windowedY;
   @Shadow
   private int windowedWidth;
   @Shadow
   private int windowedHeight;
   @Unique
   private boolean meowl$borderless;

   @Inject(method = {"setMode"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$borderlessMode(CallbackInfo ci) {
      if (!this.fullscreen || !ClientSettings.borderless()) {
         if (this.meowl$borderless) {
            // zurück ins Fenster: Rahmen wieder an, den Rest macht Minecraft
            this.meowl$borderless = false;
            GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
         }

         return;
      }

      Monitor monitor = this.screenManager.findBestMonitor((Window)(Object)this);
      if (monitor == null) {
         return;
      }

      if (!this.meowl$borderless && GLFW.glfwGetWindowMonitor(this.handle) == 0L) {
         this.windowedX = this.x;
         this.windowedY = this.y;
         this.windowedWidth = this.width;
         this.windowedHeight = this.height;
      }

      VideoMode mode = monitor.getCurrentMode();
      this.x = monitor.getX();
      this.y = monitor.getY();
      this.width = mode.getWidth();
      this.height = mode.getHeight();
      GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
      GLFW.glfwSetWindowMonitor(this.handle, 0L, this.x, this.y, this.width, this.height + 1, GLFW.GLFW_DONT_CARE);
      this.meowl$borderless = true;
      ci.cancel();
   }
}
