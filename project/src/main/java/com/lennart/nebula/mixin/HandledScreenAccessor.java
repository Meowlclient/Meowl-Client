package com.lennart.nebula.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractContainerScreen.class})
public interface HandledScreenAccessor {
   @Accessor("hoveredSlot")
   Slot meowl$getFocusedSlot();

   @Accessor("leftPos")
   int meowl$getX();

   @Accessor("topPos")
   int meowl$getY();

   @Accessor("imageWidth")
   int meowl$getBackgroundWidth();
}
