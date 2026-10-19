package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.BetterChat;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Better Chat: längerer Verlauf, Filter und Hervorhebungen. */
@Mixin({ChatComponent.class})
public abstract class ChatHudMixin {
   @Unique
   private static boolean meowl$reentry;

   @Shadow
   public abstract void addMessage(Component message, MessageSignature signature, GuiMessageTag indicator);

   @ModifyConstant(
      method = {"addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V", "addMessageToDisplayQueue"},
      constant = {@Constant(intValue = 100)}
   )
   private int meowl$longerHistory(int vanilla) {
      return BetterChat.maxMessages(vanilla);
   }

   @ModifyConstant(
      method = {"addRecentChat"},
      constant = {@Constant(intValue = 100)}
   )
   private int meowl$longerSentHistory(int vanilla) {
      return BetterChat.maxSent(vanilla);
   }

   @Inject(
      method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$filter(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
      if (meowl$reentry) {
         return;
      }

      com.lennart.nebula.module.mods.Tpa.onChat(message, signature != null);

      BetterChat chat = BetterChat.get();
      if (message != null) {
         Component out = chat != null ? chat.process(message) : message;
         if (out != null && com.lennart.nebula.module.mods.ChatEmojisModule.images()) {
            out = com.lennart.nebula.chat.ChatEmojis.decorate(out);
         }

         if (out == null) {
            ci.cancel();
         } else if (out != message) {
            ci.cancel();
            meowl$reentry = true;

            try {
               this.addMessage(out, signature, indicator);
            } finally {
               meowl$reentry = false;
            }
         }
      }
   }
}
