package com.lennart.nebula.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry"})
public interface KeyEntryAccessor {
   @Accessor("key")
   KeyMapping meowl$key();

   @Accessor("name")
   Component meowl$name();
}