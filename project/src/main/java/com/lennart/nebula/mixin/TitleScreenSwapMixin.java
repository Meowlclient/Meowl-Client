package com.lennart.nebula.mixin;

import com.lennart.nebula.gui.MeowlTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Minecraft-Titelbildschirm durch das Meowl-Hauptmenü ersetzen (siehe {@link MeowlTitleScreen}). */
@Mixin({Minecraft.class})
public abstract class TitleScreenSwapMixin {
   @ModifyVariable(method = {"setScreen"}, at = @At("HEAD"), argsOnly = true)
   private Screen meowl$titleScreen(Screen next) {
      return MeowlTitleScreen.replace(next);
   }
}
