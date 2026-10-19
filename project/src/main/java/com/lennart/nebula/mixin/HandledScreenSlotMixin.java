package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.ItemHighlighter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractContainerScreen.class})
public abstract class HandledScreenSlotMixin {
   @Inject(
      method = {"renderSlot"},
      at = {@At("HEAD")}
   )
   private void meowl$highlightUnderItem(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
      ItemHighlighter.drawUnderSlot(context, slot);
   }
}
