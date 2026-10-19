package com.lennart.nebula.mixin;

import com.lennart.nebula.module.hud.InventoryScale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x: nach einer Fenster-Größenänderung wieder die Inventar-Größe setzen. */
@Mixin({Minecraft.class})
public abstract class InventoryScaleResizeMixin {
   @Inject(method = {"resizeGui"}, at = {@At("TAIL")})
   private void meowl$inventoryScaleResize(CallbackInfo ci) {
      InventoryScale.afterResize(Minecraft.getInstance().gui.screen());
   }
}
