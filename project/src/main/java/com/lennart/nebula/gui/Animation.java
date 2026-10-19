package com.lennart.nebula.gui;

public final class Animation {
   private final float speed;
   private float value;
   private long lastNanos = System.nanoTime();

   public Animation(float speed, float initial) {
      this.speed = speed;
      this.value = initial;
   }

   public float update(float target) {
      long now = System.nanoTime();
      float dt = Math.min((float)(now - this.lastNanos) / 1.0E9F, 0.1F);
      this.lastNanos = now;
      this.value = this.value + (target - this.value) * Math.min(1.0F, dt * this.speed);
      if (Math.abs(target - this.value) < 0.002F) {
         this.value = target;
      }

      return this.value;
   }

   public float get() {
      return this.value;
   }
}
