package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.ShulkerTooltips;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemStack.class})
public abstract class ItemStackTooltipMixin {
   @Inject(
      method = {"getTooltipImage()Ljava/util/Optional;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void meowl$shulkerGrid(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
      if (cir.getReturnValue().isEmpty()) {
         TooltipComponent data = ShulkerTooltips.dataFor((ItemStack)(Object)this);
         if (data != null) {
            cir.setReturnValue(Optional.of(data));
         }
      }
   }
}
