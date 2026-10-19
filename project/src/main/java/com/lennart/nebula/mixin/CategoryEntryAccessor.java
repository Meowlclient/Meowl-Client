package com.lennart.nebula.mixin;

import net.minecraft.client.gui.components.FocusableTextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"net.minecraft.client.gui.screens.options.controls.KeyBindsList$CategoryEntry"})
public interface CategoryEntryAccessor {
   @Accessor("categoryName")
   FocusableTextWidget meowl$categoryName();
}