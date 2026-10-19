package com.lennart.nebula.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;

public final class ChatUtil {
   private static final String PREFIX = "§5[§dMeowl§5]§r ";

   private ChatUtil() {
   }

   public static void actionBar(String message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         com.lennart.nebula.compat.Compat.message(Component.literal("§5[§dMeowl§5]§r " + com.lennart.nebula.gui.Lang.tr(message)), true);
      }
   }

   public static void chat(Component message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         com.lennart.nebula.compat.Compat.message(Component.literal("§5[§dMeowl§5]§r ").append(message), false);
      }
   }

   public static Component button(String label, int color, String command, String hover) {
      return Component.literal(label)
         .withStyle(s -> s.withColor(color).withBold(true).withClickEvent(new RunCommand(command)).withHoverEvent(new ShowText(Component.literal(hover))));
   }

   public static void chat(String message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         com.lennart.nebula.compat.Compat.message(Component.literal("§5[§dMeowl§5]§r " + com.lennart.nebula.gui.Lang.tr(message)), false);
      }
   }
}
