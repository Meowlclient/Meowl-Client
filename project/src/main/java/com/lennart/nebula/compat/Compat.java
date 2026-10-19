package com.lennart.nebula.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Kleine Hilfen für Stellen, an denen sich die Minecraft-API zwischen den Versionen unterscheidet. Für 26.x gibt es
 * eine eigene Fassung dieser Klasse; der übrige Code ruft nur diese Methoden auf.
 */
public final class Compat {
   private Compat() {
   }

   /** Nachricht an den Spieler: im Chat oder über der Hotbar. */
   public static void message(Component text, boolean actionBar) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.displayClientMessage(text, actionBar);
      }
   }

   /** Gepackte Licht-Koordinaten (Block + Himmel) an einer Stelle. */
   public static int lightAt(Level level, BlockPos pos) {
      return LevelRenderer.getLightColor(level, pos);
   }

   public static void swingMainHand(Player player) {
      player.swing(InteractionHand.MAIN_HAND);
   }

   public static boolean isSwinging(LivingEntity entity) {
      return entity.swinging;
   }

   public static float attackAnim(LivingEntity entity, float tickDelta) {
      return entity.getAttackAnim(tickDelta);
   }

   /** Kleine Benachrichtigung oben rechts. */
   public static void toast(Component title, Component text) {
      Minecraft mc = Minecraft.getInstance();
      mc.getToastManager().addToast(SystemToast.multiline(mc, SystemToastId.PERIODIC_NOTIFICATION, title, text));
   }

   /** Werden Namensschilder angezeigt (HUD nicht mit F1 ausgeblendet)? */
   public static boolean renderNames() {
      return Minecraft.renderNames();
   }
}
