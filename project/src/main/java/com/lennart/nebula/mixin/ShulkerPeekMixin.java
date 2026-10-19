package com.lennart.nebula.mixin;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.ShulkerViewScreen;
import com.lennart.nebula.module.mods.ShulkerTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shulker Tooltips: Mittelklick auf eine Shulker-Kiste öffnet die Durchsehen-Ansicht (nur ansehen). */
@Mixin({AbstractContainerScreen.class})
public abstract class ShulkerPeekMixin {
   @Shadow
   protected Slot hoveredSlot;

   @Inject(method = {"mouseClicked"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$peekShulker(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (click.button() == InputConstants.MOUSE_BUTTON_MIDDLE && ShulkerTooltips.middleClickEnabled() && this.hoveredSlot != null) {
         ItemStack stack = this.hoveredSlot.getItem();
         if (!stack.isEmpty() && ShulkerTooltips.contents(stack) != null) {
            Minecraft.getInstance().setScreen(new ShulkerViewScreen((Screen)(Object)this, stack));
            cir.setReturnValue(true);
         }
      }
   }
}
