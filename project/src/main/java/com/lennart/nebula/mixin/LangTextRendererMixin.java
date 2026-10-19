package com.lennart.nebula.mixin;

import com.lennart.nebula.gui.Lang;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Englischer Modus: Breiten passend zum übersetzten Text messen (sonst verrutscht das Layout). */
@Mixin({Font.class})
public abstract class LangTextRendererMixin {
   @ModifyVariable(method = {"width(Ljava/lang/String;)I"}, at = @At("HEAD"), argsOnly = true)
   private String meowl$width(String text) {
      return Lang.tr(text);
   }

   @ModifyVariable(method = {"plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;"}, at = @At("HEAD"), argsOnly = true)
   private String meowl$trim(String text) {
      return Lang.tr(text);
   }
}
