package com.lennart.nebula.afk.space;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Geräusche der Brücke: tiefes Schiffsbrummen, gedämpfte Explosionen und Laser (im All hört man sie nur durch den
 * Rumpf), vorbeirasende Jäger, Alarm-Hupe bei Treffern am eigenen Schiff. Nur Spiel-Sounds, keine Musik.
 */
public final class SpaceSounds implements SpaceBattle.Events {
   private static final int QUEUE = 24;
   private final RandomSource random = RandomSource.create();
   private final java.util.Random jr = new java.util.Random();
   private final long[] qTime = new long[QUEUE];
   private final SoundEvent[] qEvent = new SoundEvent[QUEUE];
   private final float[] qVol = new float[QUEUE];
   private final float[] qPitch = new float[QUEUE];
   private SpaceSounds.Loop hum;
   private SpaceSounds.Loop rumble;
   private boolean enabled;
   private float master;
   private float explosions;
   private float shots;
   private float ambience;
   private float fade;
   private long lastShot;
   private long lastBoom;

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
         if (this.hum == null || this.hum.isStopped()) {
            this.hum = new SpaceSounds.Loop(SoundEvents.BEACON_AMBIENT, 0.5F);
            this.play(this.hum);
         }

         if (this.rumble == null || this.rumble.isStopped()) {
            this.rumble = new SpaceSounds.Loop(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), 0.5F);
            this.play(this.rumble);
         }

         float base = this.master * this.ambience * sceneFade;
         this.hum.target = 0.5F * base;
         this.rumble.target = 0.22F * base;
      } else {
         if (this.hum != null) {
            this.hum.target = 0.0F;
         }

         if (this.rumble != null) {
            this.rumble.target = 0.0F;
         }
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

   private static SoundEvent of(Holder<SoundEvent> e) {
      return e.value();
   }

   private static float near(float d) {
      return Math.max(0.12F, Math.min(1.0F, 900.0F / Math.max(1.0F, d)));
   }

   @Override
   public void explosion(float d, float size) {
      if (this.enabled && this.explosions > 0.0F) {
         long now = System.currentTimeMillis();
         if (now - this.lastBoom >= 90L) {
            this.lastBoom = now;
            float vol = this.master * this.explosions * Math.min(1.0F, size / 18.0F) * near(d) * 0.4F;
            SoundEvent event = d > 1800.0F ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR : of(SoundEvents.GENERIC_EXPLODE);
            this.enqueue(event, Math.min(1.2F, d / 3000.0F), vol, 0.35F + this.jr.nextFloat() * 0.25F);
         }
      }
   }

   @Override
   public void shot(float d, byte faction, boolean capital) {
      if (this.enabled && this.shots > 0.0F) {
         long now = System.currentTimeMillis();
         if (now - this.lastShot >= 120L && this.jr.nextFloat() <= (capital ? 0.7F : 0.25F)) {
            this.lastShot = now;
            float vol = this.master * this.shots * near(d) * (capital ? 0.13F : 0.06F);
            float pitch = faction == SpaceBattle.MEOWL ? 1.6F + this.jr.nextFloat() * 0.3F : 1.2F + this.jr.nextFloat() * 0.25F;
            this.enqueue(capital ? SoundEvents.BLAZE_SHOOT : SoundEvents.FIREWORK_ROCKET_BLAST_FAR, 0.0F, vol, pitch);
         }
      }
   }

   @Override
   public void flyby() {
      if (this.enabled && this.ambience > 0.0F) {
         float vol = this.master * this.ambience * 0.5F;
         this.enqueue(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.15F, vol, 0.5F);
         this.enqueue(SoundEvents.ELYTRA_FLYING, 0.0F, vol * 0.5F, 1.6F);
      }
   }

   @Override
   public void hullHit() {
      if (this.enabled) {
         float vol = this.master * Math.max(this.explosions, 0.2F);
         this.enqueue(of(SoundEvents.GENERIC_EXPLODE), 0.0F, vol * 0.7F, 0.5F);
         this.enqueue(SoundEvents.ANVIL_LAND, 0.05F, vol * 0.25F, 0.5F);
         // Alarm-Hupe: zweitönig, ein paar Mal
         for (int i = 0; i < 6; i++) {
            this.enqueue(of(SoundEvents.NOTE_BLOCK_BIT), 0.6F + i * 0.45F, this.master * 0.3F, i % 2 == 0 ? 0.75F : 0.6F);
         }
      }
   }

   @Override
   public void jump(float d) {
      if (this.enabled && this.ambience > 0.0F) {
         this.enqueue(SoundEvents.BEACON_ACTIVATE, 0.0F, this.master * this.ambience * near(d) * 0.5F, 1.6F);
      }
   }

   public void stop() {
      if (this.hum != null) {
         this.hum.halt();
         this.hum = null;
      }

      if (this.rumble != null) {
         this.rumble.halt();
         this.rumble = null;
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
