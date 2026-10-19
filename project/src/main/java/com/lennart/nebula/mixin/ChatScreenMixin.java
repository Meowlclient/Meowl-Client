package com.lennart.nebula.mixin;

import com.lennart.nebula.chat.ChatEmojiPicker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emoji-Auswahl im Chat: Klicks auf Knopf/Auswahl abfangen, ESC schließt erst die Auswahl. */
@Mixin({ChatScreen.class})
public abstract class ChatScreenMixin {
   @Inject(method = {"mouseClicked"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$emojiClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && ChatEmojiPicker.click(click.x(), click.y())) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = {"keyPressed"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$emojiEsc(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (event.key() == InputConstants.KEY_ESCAPE && ChatEmojiPicker.isOpen()) {
         ChatEmojiPicker.close();
         cir.setReturnValue(true);
      }
   }
}