package com.lennart.nebula.mixin;

import com.lennart.nebula.gui.Lang;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Englischer Modus: feste Meowl-Texte in Text.literal(...) übersetzen (nur exakte Treffer). */
@Mixin({MutableComponent.class})
public abstract class LangMutableTextMixin {
   @ModifyVariable(method = {"create(Lnet/minecraft/network/chat/ComponentContents;)Lnet/minecraft/network/chat/MutableComponent;"}, at = @At("HEAD"), argsOnly = true)
   private static ComponentContents meowl$translate(ComponentContents content) {
      if (Lang.english() && content instanceof PlainTextContents.LiteralContents lit) {
         String en = Lang.exactOnly(lit.text());
         if (!en.equals(lit.text())) {
            return PlainTextContents.create(en);
         }
      }

      return content;
   }
}
