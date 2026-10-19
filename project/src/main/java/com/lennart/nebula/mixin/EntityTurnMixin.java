package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.Freelook;
import com.lennart.nebula.module.mods.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mausbewegung des eigenen Spielers: Freelook (nur Kamera dreht) und feinere Maus beim Zoomen. */
@Mixin({Entity.class})
public abstract class EntityTurnMixin {
   @Unique
   private static boolean meowl$inner;

   @Inject(method = {"turn"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$turn(double dx, double dy, CallbackInfo ci) {
      if (meowl$inner || (Object)this != Minecraft.getInstance().player) {
         return;
      }

      if (Freelook.active()) {
         Freelook.turn(dx, dy);
         ci.cancel();
      } else {
         float f = Zoom.mouseFactor();
         if (f != 1.0F) {
            ci.cancel();
            meowl$inner = true;

            try {
               ((Entity)(Object)this).turn(dx * f, dy * f);
            } finally {
               meowl$inner = false;
            }
         }
      }
   }
}
