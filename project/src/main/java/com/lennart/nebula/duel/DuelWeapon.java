package com.lennart.nebula.duel;

public final class DuelWeapon {
   private long equipAt = System.currentTimeMillis();
   private long lastShot;
   private long reloadStart;
   private long reloadEnd;

   void equip() {
      this.equipAt = System.currentTimeMillis();
   }

   boolean canShoot(DuelSession s) {
      long now = System.currentTimeMillis();
      return s.state == DuelSession.State.ACTIVE && s.myHp > 0 && s.ammo > 0 && !s.isReloading() && now - this.lastShot >= s.cooldownMs;
   }

   void onShot() {
      this.lastShot = System.currentTimeMillis();
   }

   void onReload(long durationMs) {
      this.reloadStart = System.currentTimeMillis();
      this.reloadEnd = this.reloadStart + durationMs;
   }

   public float equipProgress() {
      return Math.min(1.0F, (float)(System.currentTimeMillis() - this.equipAt) / 350.0F);
   }

   public float recoil() {
      long dt = System.currentTimeMillis() - this.lastShot;
      if (dt < 40L) {
         return (float)dt / 40.0F;
      } else {
         float t = (float)(dt - 40L) / 360.0F;
         return t >= 1.0F ? 0.0F : (1.0F - t) * (1.0F - t);
      }
   }

   public boolean muzzleFlash() {
      return System.currentTimeMillis() - this.lastShot < 60L;
   }

   public float reloadProgress() {
      long now = System.currentTimeMillis();
      return now < this.reloadEnd && this.reloadEnd != 0L ? (float)(now - this.reloadStart) / (float)Math.max(1L, this.reloadEnd - this.reloadStart) : -1.0F;
   }
}
