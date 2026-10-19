package com.lennart.nebula.afk.valley;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Geräusche im Tal des Endes: Rauschen des Wasserfalls, entfernte Schläge, Klingen, Explosionen, Donner, das Zirpen
 * des Chidori und das Surren des Rasengan. Nur Spiel-Sounds, keine Musik.
 */
public final class ValleySounds implements ValleyFight.Events {
   private static final int QUEUE = 24;
   private final RandomSource random = RandomSource.create();
   private final java.util.Random jr = new java.util.Random();
   private final long[] qTime = new long[QUEUE];
   private final SoundEvent[] qEvent = new SoundEvent[QUEUE];
   private final float[] qVol = new float[QUEUE];
   private final float[] qPitch = new float[QUEUE];
   private ValleySounds.Loop fall;
   private boolean enabled;
   private float master;
   private float explosions;
   private float shots;
   private float ambience;
   private float fade;
   private long lastClash;
   private long nextDrip;

   public void configure(boolean enabled, float master, float explosions, float shots, float ambience) {
      this.enabled = enabled;
      this.master = master;
      this.explosions = explosions;
      this.shots = shots;
      this.ambience = ambience;
   }

   public void update(float sceneFade) {
      this.fade = sceneFade;
      boolean want = this.enabled && this.master > 0.0F && this.ambience > 0.0F && sceneFade > 0.01F;
      if (want) {
         if (this.fall == null || this.fall.isStopped()) {
            // tiefer Regen klingt wie ein entfernter Wasserfall
            this.fall = new ValleySounds.Loop(SoundEvents.WEATHER_RAIN, 0.55F);
            this.play(this.fall);
         }

         this.fall.target = 0.35F * this.master * this.ambience * sceneFade;
         long now = System.currentTimeMillis();
         if (now >= this.nextDrip) {
            this.nextDrip = now + 1400L + this.jr.nextInt(2200);
            this.enqueue(SoundEvents.WATER_AMBIENT, 0.0F, 0.25F * this.master * this.ambience, 0.6F + this.jr.nextFloat() * 0.3F);
         }
      } else if (this.fall != null) {
         this.fall.target = 0.0F;
      }

      long now = System.currentTimeMillis();

      for (int i = 0; i < QUEUE; i++) {
         if (this.qEvent[i] != null && now >= this.qTime[i]) {
            float vol = this.qVol[i] * this.fade;
            if (vol > 0.003F) {
               this.play(new SimpleSoundInstance(this.qEvent[i].location(), SoundSource.AMBIENT, vol, this.qPitch[i], this.random, false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true));
            }

            this.qEvent[i] = null;
         }
      }
   }

   private void play(SoundInstance sound) {
      try {
         Minecraft.getInstance().getSoundManager().play(sound);
      } catch (RuntimeException var3) {
      }
   }

   private void enqueue(SoundEvent event, float delaySeconds, float vol, float pitch) {
      if (this.enabled) {
         for (int i = 0; i < QUEUE; i++) {
            if (this.qEvent[i] == null) {
               this.qEvent[i] = event;
               this.qTime[i] = System.currentTimeMillis() + Math.round(delaySeconds * 1000.0F);
               this.qVol[i] = vol;
               this.qPitch[i] = pitch;
               return;
            }
         }
      }
   }

   @Override
   public void clash(float power) {
      long now = System.currentTimeMillis();
      if (now - this.lastClash >= 70L) {
         this.lastClash = now;
         float vol = this.master * this.shots * (0.12F + power * 0.25F);
         if (power > 0.6F) {
            this.enqueue(SoundEvents.PLAYER_ATTACK_STRONG, 0.0F, vol, 0.7F + this.jr.nextFloat() * 0.2F);
         }

         this.enqueue(SoundEvents.ANVIL_LAND, 0.0F, vol * 0.35F, 1.8F + this.jr.nextFloat() * 0.2F);
      }
   }

   @Override
   public void boom(float power) {
      this.enqueue(SoundEvents.GENERIC_EXPLODE.value(), 0.05F, this.master * this.explosions * (0.2F + power * 0.35F), 0.55F + this.jr.nextFloat() * 0.2F);
   }

   @Override
   public void splash(float power) {
      this.enqueue(SoundEvents.GENERIC_SPLASH, 0.0F, this.master * this.ambience * (0.15F + power * 0.2F), 0.8F + this.jr.nextFloat() * 0.3F);
   }

   @Override
   public void poof() {
      this.enqueue(SoundEvents.FIRE_EXTINGUISH, 0.0F, this.master * this.shots * 0.18F, 1.6F + this.jr.nextFloat() * 0.3F);
   }

   @Override
   public void fire() {
      this.enqueue(SoundEvents.FIRECHARGE_USE, 0.0F, this.master * this.explosions * 0.35F, 0.6F);
      this.enqueue(SoundEvents.BLAZE_SHOOT, 0.1F, this.master * this.explosions * 0.3F, 0.5F);
   }

   @Override
   public void thunder(float power) {
      this.enqueue(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.0F, this.master * this.explosions * 0.35F * power, 0.9F + this.jr.nextFloat() * 0.2F);
      if (power > 0.6F) {
         this.enqueue(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.0F, this.master * this.explosions * 0.4F, 0.8F);
      }
   }

   @Override
   public void chidori(float level) {
      this.enqueue(SoundEvents.BEE_LOOP_AGGRESSIVE, 0.0F, this.master * this.shots * 0.25F * level, 1.8F);
      this.enqueue(SoundEvents.FIRE_EXTINGUISH, 0.1F, this.master * this.shots * 0.1F * level, 2.0F);
   }

   @Override
   public void rasengan(float level) {
      this.enqueue(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.2F, this.master * this.shots * 0.2F * level, 1.4F);
   }

   @Override
   public void whoosh() {
      this.enqueue(SoundEvents.PLAYER_ATTACK_SWEEP, 0.0F, this.master * this.shots * 0.15F, 1.3F + this.jr.nextFloat() * 0.3F);
   }

   public void stop() {
      if (this.fall != null) {
         this.fall.halt();
         this.fall = null;
      }

      for (int i = 0; i < QUEUE; i++) {
         this.qEvent[i] = null;
      }
   }

   private static final class Loop extends AbstractTickableSoundInstance {
      float target;
      private float current;

      Loop(SoundEvent event, float pitch) {
         super(event, SoundSource.AMBIENT, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.attenuation = Attenuation.NONE;
         this.relative = true;
         this.pitch = pitch;
         this.volume = 0.001F;
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         this.current = this.current + (this.target - this.current) * 0.08F;
         this.volume = Math.max(1.0E-4F, this.current);
      }

      void halt() {
         this.stop();
      }
   }
}
