package com.lennart.nebula.afk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class BattleSounds implements BattleSim.Events {
   private static final int QUEUE = 24;
   private final RandomSource random = RandomSource.create();
   private final java.util.Random jr = new java.util.Random();
   private final long[] qTime = new long[24];
   private final SoundEvent[] qEvent = new SoundEvent[24];
   private final float[] qVol = new float[24];
   private final float[] qPitch = new float[24];
   private BattleSounds.Loop wind;
   private BattleSounds.Loop rumble;
   private boolean enabled;
   private float master;
   private float explosions;
   private float shots;
   private float ambience;
   private float fade;
   private long lastShot;

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
         if (this.wind == null || this.wind.isStopped()) {
            this.wind = new BattleSounds.Loop(SoundEvents.ELYTRA_FLYING, 0.55F);
            this.play(this.wind);
         }

         if (this.rumble == null || this.rumble.isStopped()) {
            this.rumble = new BattleSounds.Loop(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), 0.7F);
            this.play(this.rumble);
         }

         float base = this.master * this.ambience * sceneFade;
         this.wind.target = 0.1F * base;
         this.rumble.target = 0.35F * base;
      } else {
         if (this.wind != null) {
            this.wind.target = 0.0F;
         }

         if (this.rumble != null) {
            this.rumble.target = 0.0F;
         }
      }

      long now = System.currentTimeMillis();

      for (int i = 0; i < 24; i++) {
         if (this.qEvent[i] != null && now >= this.qTime[i]) {
            float vol = this.qVol[i] * this.fade;
            if (vol > 0.003F) {
               this.play(
                  new SimpleSoundInstance(
                     this.qEvent[i].location(), SoundSource.AMBIENT, vol, this.qPitch[i], this.random, false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
                  )
               );
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
      for (int i = 0; i < 24; i++) {
         if (this.qEvent[i] == null) {
            this.qEvent[i] = event;
            this.qTime[i] = System.currentTimeMillis() + Math.round(delaySeconds * 1000.0F);
            this.qVol[i] = vol;
            this.qPitch[i] = pitch;
            return;
         }
      }
   }

   private static float distanceFactor(float d) {
      return Math.max(0.15F, 1.3F - d / 118.0F);
   }

   @Override
   public void explosion(float x, float d, float size) {
      if (this.enabled && !(this.explosions <= 0.0F)) {
         float vol = this.master * this.explosions * Math.min(1.0F, size / 2.0F) * distanceFactor(d) * 0.45F;
         SoundEvent event = d > 75.0F ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR : SoundEvents.GENERIC_EXPLODE.value();
         this.enqueue(event, d / 95.0F, vol, 0.45F + this.jr.nextFloat() * 0.3F);
      }
   }

   @Override
   public void shot(float x, float d, byte faction) {
      if (this.enabled && !(this.shots <= 0.0F)) {
         long now = System.currentTimeMillis();
         if (now - this.lastShot >= 140L && !(this.jr.nextFloat() > 0.35F)) {
            this.lastShot = now;
            float vol = this.master * this.shots * distanceFactor(d) * 0.09F;
            float pitch = faction == 0 ? 1.7F + this.jr.nextFloat() * 0.3F : 1.35F + this.jr.nextFloat() * 0.3F;
            this.enqueue(SoundEvents.FIREWORK_ROCKET_BLAST_FAR, d / 95.0F, vol, pitch);
         }
      }
   }

   public void stop() {
      if (this.wind != null) {
         this.wind.halt();
         this.wind = null;
      }

      if (this.rumble != null) {
         this.rumble.halt();
         this.rumble = null;
      }

      for (int i = 0; i < 24; i++) {
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
         this.x = 0.0;
         this.y = 0.0;
         this.z = 0.0;
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
