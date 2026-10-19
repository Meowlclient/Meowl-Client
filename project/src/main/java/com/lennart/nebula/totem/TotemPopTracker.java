package com.lennart.nebula.totem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class TotemPopTracker {
   private static final Map<UUID, TotemPopTracker.Entry> POPS = new ConcurrentHashMap<>();
   private static final List<TotemPopTracker.PopListener> LISTENERS = new ArrayList<>();
   private static final List<Consumer<Player>> DEATH_HOOKS = new ArrayList<>();
   private static Object lastWorld;

   public static void onPlayerDeath(Consumer<Player> hook) {
      DEATH_HOOKS.add(hook);
   }

   private TotemPopTracker() {
   }

   public static void addListener(TotemPopTracker.PopListener listener) {
      LISTENERS.add(listener);
   }

   public static void onEntityStatus(Entity entity, byte status) {
      if (entity instanceof Player player) {
         if (status == 35) {
            TotemPopTracker.Entry e = POPS.computeIfAbsent(player.getUUID(), u -> new TotemPopTracker.Entry(player.getGameProfile().name()));
            e.name = player.getGameProfile().name();
            e.pops++;
            e.lastPop = System.currentTimeMillis();
            e.lastSeen = e.lastPop;

            for (TotemPopTracker.PopListener l : LISTENERS) {
               l.onPop(player, e.pops);
            }
         } else if (status == 3) {
            reset(player, true);

            for (Consumer<Player> hook : DEATH_HOOKS) {
               hook.accept(player);
            }
         }
      }
   }

   public static void tick(Minecraft mc) {
      if (mc.level != lastWorld) {
         lastWorld = mc.level;
         POPS.clear();
      }

      if (mc.level != null) {
         long now = System.currentTimeMillis();

         for (Player p : mc.level.players()) {
            TotemPopTracker.Entry e = POPS.get(p.getUUID());
            if (e != null) {
               e.lastSeen = now;
               if (p.isDeadOrDying() || p.getHealth() <= 0.0F) {
                  reset(p, true);
               }
            }
         }

         POPS.values().removeIf(ex -> now - ex.lastSeen > 120000L);
      }
   }

   public static void reset(Player player, boolean died) {
      TotemPopTracker.Entry e = POPS.remove(player.getUUID());
      if (e != null && died) {
         for (TotemPopTracker.PopListener l : LISTENERS) {
            l.onDeath(player, e.pops);
         }
      }
   }

   public static int pops(UUID uuid) {
      TotemPopTracker.Entry e = POPS.get(uuid);
      return e == null ? 0 : e.pops;
   }

   public static List<TotemPopTracker.View> snapshot() {
      List<TotemPopTracker.View> list = new ArrayList<>();
      POPS.forEach((uuid, e) -> list.add(new TotemPopTracker.View(uuid, e.name, e.pops, e.lastPop)));
      list.sort(Comparator.comparingLong(TotemPopTracker.View::lastPop).reversed());
      return list;
   }

   public static void clear() {
      POPS.clear();
   }

   private static final class Entry {
      String name;
      int pops;
      long lastPop;
      long lastSeen;

      Entry(String name) {
         this.name = name;
      }
   }

   public interface PopListener {
      void onPop(Player var1, int var2);

      default void onDeath(Player player, int pops) {
      }
   }

   public record View(UUID uuid, String name, int pops, long lastPop) {
   }
}
