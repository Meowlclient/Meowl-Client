package com.lennart.nebula.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hitcolor: Zugriff auf die Textur, aus der Minecraft das rote Treffer-Aufblitzen nimmt. */
@Mixin({OverlayTexture.class})
public interface OverlayTextureAccessor {
   @Accessor("texture")
   DynamicTexture meowl$texture();
}
