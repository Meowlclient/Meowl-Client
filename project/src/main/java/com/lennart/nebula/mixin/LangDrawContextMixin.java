package com.lennart.nebula.mixin;

import com.lennart.nebula.gui.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Englischer Modus: Texte (Strings) beim Zeichnen übersetzen. */
@Mixin({GuiGraphicsExtractor.class})
public abstract class LangDrawContextMixin {
   @ModifyVariable(
      method = {"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private String meowl$translate(String text) {
      return Lang.tr(text);
   }
}
