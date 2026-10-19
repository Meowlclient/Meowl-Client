package com.lennart.nebula.mixin;

import com.lennart.nebula.afk.AfkInput;
import com.lennart.nebula.module.mods.NoHotbarScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MouseHandler.class})
public abstract class MouseAfkMixin {
   @Inject(
      method = {"onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"},
      at = {@At("HEAD")}
   )
   private void meowl$afkClick(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
      AfkInput.click(input.button(), action);
   }

   @Inject(
      method = {"onScroll(JDD)V"},
      at = {@At("HEAD")}
   )
   private void meowl$afkScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      AfkInput.scroll();
      // Zoom: Mausrad ändert die Zoom-Stärke statt den Hotbar-Slot
      if (Minecraft.getInstance().screen == null && com.lennart.nebula.module.mods.Zoom.onScroll(vertical)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"onMove(JDD)V"},
      at = {@At("HEAD")}
   )
   private void meowl$afkMove(long window, double x, double y, CallbackInfo ci) {
      AfkInput.cursor(x, y);
   }

   @Inject(
      method = {"onScroll(JDD)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"
      )},
      cancellable = true
   )
   private void meowl$noHotbarScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (NoHotbarScroll.blocks()) {
         ci.cancel();
      }
   }
}
