package com.lennart.nebula.mixin;

import com.lennart.nebula.nametag.Nametags;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab-Liste: Meowl-Symbol vor Spielern mit Meowl Client. */
@Mixin({PlayerTabOverlay.class})
public abstract class TabMeowlMixin {
   @Inject(method = {"getNameForDisplay"}, at = {@At("RETURN")}, cancellable = true)
   private void meowl$tabIcon(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
      Component icon = Nametags.tabIcon(info.getProfile().id(), info.getProfile().name());
      if (icon != null) {
         cir.setReturnValue(Component.empty().append(icon).append(cir.getReturnValue()));
      }
   }
}
