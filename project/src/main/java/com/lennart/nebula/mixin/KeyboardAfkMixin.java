package com.lennart.nebula.mixin;

import com.lennart.nebula.afk.AfkInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardHandler.class})
public abstract class KeyboardAfkMixin {
   @Inject(
      method = {"keyPress(JILnet/minecraft/client/input/KeyEvent;)V"},
      at = {@At("HEAD")}
   )
   private void meowl$afkKey(long window, int action, KeyEvent input, CallbackInfo ci) {
      AfkInput.key(input.key(), action);
   }
}
