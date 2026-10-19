package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.ShulkerTooltips;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemContainerContents.class})
public abstract class ContainerComponentMixin {
   @Inject(
      method = {"addToTooltip(Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;Lnet/minecraft/core/component/DataComponentGetter;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$hideList(TooltipContext context, Consumer<Component> textConsumer, TooltipFlag type, DataComponentGetter components, CallbackInfo ci) {
      ItemContainerContents self = (ItemContainerContents)(Object)this;
      if (ShulkerTooltips.hiddenWithoutShift() && self.nonEmptyItemCopyStream().findAny().isPresent()) {
         // Inhalt verdeckt: nur ein Hinweis, wie man reinschaut
         textConsumer.accept(Component.literal("Shift: Inhalt ansehen").withStyle(net.minecraft.ChatFormatting.GRAY));
         if (ShulkerTooltips.middleClickEnabled()) {
            textConsumer.accept(Component.literal("Mittelklick: alle Items durchsehen").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
         }

         ci.cancel();
      } else if (ShulkerTooltips.hideTextList()) {
         if (self.nonEmptyItemCopyStream().findAny().isPresent()) {
            ci.cancel();
         }
      }
   }
}
