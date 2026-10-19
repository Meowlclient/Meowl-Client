package com.lennart.nebula.afk.terra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Geräusche der Terraria-Szene aus Minecraft-Sounds (Schwert, Schleim, Zombie, Graben, Münzen, Boss). Keine Musik. */
public final class TerraSounds implements TerraSim.Events {
   private final RandomSource random = RandomSource.create();
   private boolean enabled;
   private float master;
   private float fx;
   private float fade;
   private final long[] last = new long[16];

   public void configure(boolean enabled, float master, float fx, float fade) {
      this.enabled = enabled;
      this.master = master;
      this.fx = fx;
      this.fade = fade;
   }

   private static SoundEvent event(int kind) {
      return switch (kind) {
         case TerraSim.S_SWING -> SoundEvents.PLAYER_ATTACK_SWEEP;
         case TerraSim.S_HIT -> SoundEvents.PLAYER_ATTACK_STRONG;
         case TerraSim.S_SLIME -> SoundEvents.SLIME_SQUISH_SMALL;
         case TerraSim.S_ZOMBIE -> SoundEvents.ZOMBIE_AMBIENT;
         case TerraSim.S_DIG -> SoundEvents.STONE_HIT;
         case TerraSim.S_PLACE -> SoundEvents.WOOD_PLACE;
         case TerraSim.S_COIN -> SoundEvents.EXPERIENCE_ORB_PICKUP;
         case TerraSim.S_BOW -> SoundEvents.ARROW_SHOOT;
         case TerraSim.S_ROAR -> SoundEvents.RAVAGER_ROAR;
         case TerraSim.S_HURT -> SoundEvents.PLAYER_HURT;
         case TerraSim.S_DRINK -> SoundEvents.HONEY_DRINK.value();
         case TerraSim.S_DOOR -> SoundEvents.WOODEN_DOOR_OPEN;
         case TerraSim.S_KILL -> SoundEvents.PLAYER_ATTACK_CRIT;
         default -> SoundEvents.GENERIC_EXPLODE.value();
      };
   }

   @Override
   public void sound(int kind, float vol, float pitch) {
      if (!this.enabled || this.master <= 0.0F || this.fade <= 0.02F) {
         return;
      }

      long now = System.currentTimeMillis();
      if (kind >= 0 && kind < this.last.length) {
         if (now - this.last[kind] < 60L) {
            return;
         }

         this.last[kind] = now;
      }

      float v = vol * this.master * this.fx * this.fade * 0.8F;
      if (v < 0.005F) {
         return;
      }

      try {
         Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event(kind).location(), SoundSource.AMBIENT, v, pitch, this.random, false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true));
      } catch (RuntimeException var8) {
      }
   }
}
