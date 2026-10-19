package com.lennart.nebula.mixin;

import com.lennart.nebula.emote.EmoteBody;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Emotes in der Ich-Ansicht: den eigenen Spieler trotz Ego-Kamera zeichnen (siehe {@link EmoteBody}). */
@Mixin({LevelRenderer.class})
public abstract class FirstPersonBodyMixin {
   @Redirect(method = {"extractVisibleEntities"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
   private boolean meowl$ownBody(Camera camera) {
      return camera.isDetached() || EmoteBody.showOwnBody();
   }
}
