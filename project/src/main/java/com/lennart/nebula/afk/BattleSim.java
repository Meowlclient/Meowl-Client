package com.lennart.nebula.afk;

import java.util.Random;

public final class BattleSim {
   public static final float D_NEAR = 22.0F;
   public static final float D_FAR = 118.0F;
   public static final float X_RANGE = 90.0F;
   public static final byte ROBOT = 0;
   public static final byte SOLDIER = 1;
   private static final byte ADVANCE = 0;
   private static final byte HOLD = 1;
   private static final byte RETREAT = 2;
   private final Random rnd;
   public final BattleSim.Density density;
   public float time;
   private BattleSim.Events events;
   private boolean battle = true;
   private boolean flyersEnabled = true;
   private boolean explosionsEnabled = true;
   public final int units;
   public final float[] ux;
   public final float[] ud;
   public final float[] phase;
   public final float[] deadT;
   public final byte[] fac;
   public final boolean[] moving;
   private final float[] offX;
   private final float[] offD;
   private final float[] pauseT;
   private final float[] fireT;
   private final short[] grp;
   public final int[] order;
   private final int groups;
   private final float[] gx;
   private final float[] gd;
   private final float[] gStand;
   private final float[] gTimer;
   private final byte[] gState;
   private final byte[] gFac;
   public final int boltCap;
   public final float[] bx;
   public final float[] bd;
   public final float[] bvx;
   public final float[] bvd;
   public final float[] bLife;
   public final byte[] bFac;
   public final int sparkCap = 48;
   public final float[] sx = new float[48];
   public final float[] sd = new float[48];
   public final float[] sAge = new float[48];
   private int sparkNext;
   public final int expCap = 28;
   public final float[] ex = new float[28];
   public final float[] ed = new float[28];
   public final float[] eSize = new float[28];
   public final float[] eAge = new float[28];
   public final int[] eSeed = new int[28];
   public static final float EXP_LIFE = 2.4F;
   private float expTimer = 1.0F;
   public final int misCap = 8;
   public final float[] mx0 = new float[8];
   public final float[] md0 = new float[8];
   public final float[] mx1 = new float[8];
   public final float[] md1 = new float[8];
   public final float[] mH = new float[8];
   public final float[] mDur = new float[8];
   public final float[] mAge = new float[8];
   public static final float MIS_TRAIL_FADE = 1.6F;
   private float misTimer = 2.0F;
   public final int fireCap = 10;
   public final float[] fx = new float[10];
   public final float[] fd = new float[10];
   public final float[] fSize = new float[10];
   public final boolean[] fPlume = new boolean[10];
   private int fireNext;
   public final int flyCap = 4;
   public final float[] flX = new float[4];
   public final float[] flY = new float[4];
   public final float[] flD = new float[4];
   public final float[] flV = new float[4];
   public final byte[] flType = new byte[4];
   public final boolean[] flAlive = new boolean[4];
   private float flyTimer = 3.0F;
   public final float[] rockX = new float[46];
   public final float[] rockD = new float[46];
   public final float[] rockS = new float[46];
   public final float[] archX = new float[4];
   public final float[] archD = new float[4];
   public final float[] archS = new float[4];
   public final float[] spireX = new float[7];
   public final float[] spireD = new float[7];
   public final float[] spireH = new float[7];
   public final long seed;

   public BattleSim(BattleSim.Density density, long seed) {
      this.density = density;
      this.seed = seed;
      this.rnd = new Random(seed);
      this.units = density.units;
      int n = this.units;
      this.ux = new float[n];
      this.ud = new float[n];
      this.phase = new float[n];
      this.deadT = new float[n];
      this.fac = new byte[n];
      this.moving = new boolean[n];
      this.offX = new float[n];
      this.offD = new float[n];
      this.pauseT = new float[n];
      this.fireT = new float[n];
      this.grp = new short[n];
      this.order = new int[n];
      this.boltCap = Math.max(40, n / 3);
      this.bx = new float[this.boltCap];
      this.bd = new float[this.boltCap];
      this.bvx = new float[this.boltCap];
      this.bvd = new float[this.boltCap];
      this.bLife = new float[this.boltCap];
      this.bFac = new byte[this.boltCap];
      int robots = Math.round(n * 0.64F);
      int robotGroups = Math.max(3, robots / 14);
      int soldierGroups = Math.max(3, (n - robots) / 5);
      this.groups = robotGroups + soldierGroups;
      this.gx = new float[this.groups];
      this.gd = new float[this.groups];
      this.gStand = new float[this.groups];
      this.gTimer = new float[this.groups];
      this.gState = new byte[this.groups];
      this.gFac = new byte[this.groups];

      for (int g = 0; g < this.groups; g++) {
         byte f = (byte)(g < robotGroups ? 0 : 1);
         this.gFac[g] = f;
         this.gd[g] = this.range(25.0F, 110.0F);
         this.gStand[g] = f == 0 ? this.range(6.0F, 12.0F) : this.range(7.0F, 14.0F);
         this.gx[g] = front(this.gd[g], 0.0F) + side(f) * (this.gStand[g] + this.range(0.0F, 30.0F));
         this.gState[g] = 0;
         this.gTimer[g] = this.range(2.0F, 10.0F);
      }

      for (int i = 0; i < n; this.order[i] = i++) {
         boolean robot = i < robots;
         int g = robot ? i % robotGroups : robotGroups + (i - robots) % soldierGroups;
         this.grp[i] = (short)g;
         this.fac[i] = (byte)(robot ? 0 : 1);
         if (robot) {
            int slot = i / robotGroups;
            this.offD[i] = (slot % 5 - 2) * 1.1F + this.range(-0.2F, 0.2F);
            this.offX[i] = slot / 5 * 1.3F + this.range(-0.3F, 0.3F);
         } else {
            this.offD[i] = this.range(-1.8F, 1.8F);
            this.offX[i] = this.range(-1.6F, 1.6F);
         }

         this.ux[i] = this.gx[g] + this.offX[i] * side(this.fac[i]);
         this.ud[i] = this.gd[g] + this.offD[i];
         this.phase[i] = this.range(0.0F, 6.28F);
         this.fireT[i] = this.range(0.5F, 4.0F);
      }

      for (int i = 0; i < this.rockX.length; i++) {
         this.rockD[i] = this.range(24.0F, 138.0F);
         this.rockX[i] = this.range(-1.0F, 1.0F) * this.rockD[i] * 0.75F;
         this.rockS[i] = this.range(0.3F, 1.4F);
      }

      for (int i = 0; i < this.archX.length; i++) {
         this.archD[i] = this.range(40.0F, 128.0F);
         this.archX[i] = (i % 2 == 0 ? -1 : 1) * this.range(8.0F, 0.55F * this.archD[i]);
         this.archS[i] = this.range(3.5F, 6.5F);
      }

      for (int i = 0; i < this.spireX.length; i++) {
         this.spireD[i] = this.range(50.0F, 158.0F);
         this.spireX[i] = this.range(-0.8F, 0.8F) * this.spireD[i];
         this.spireH[i] = this.range(5.0F, 14.0F);
      }

      for (int i = 0; i < 10; i++) {
         this.fd[i] = this.range(28.0F, 108.0F);
         this.fx[i] = front(this.fd[i], 0.0F) + this.range(-16.0F, 16.0F);
         this.fSize[i] = this.range(0.6F, 1.4F);
         this.fPlume[i] = i < 3;
      }

      for (int i = 0; i < 28; i++) {
         this.eAge[i] = 2.4F;
      }

      for (int i = 0; i < 8; i++) {
         this.mAge[i] = 1.0E9F;
      }

      for (int i = 0; i < 48; i++) {
         this.sAge[i] = 1.0F;
      }
   }

   public void setEvents(BattleSim.Events events) {
      this.events = events;
   }

   public void configure(boolean battle, boolean flyers, boolean explosions) {
      this.battle = battle;
      this.flyersEnabled = flyers;
      this.explosionsEnabled = explosions;
   }

   public boolean battleEnabled() {
      return this.battle;
   }

   public static float front(float d, float t) {
      return 6.0F * (float)Math.sin(t * 0.045F + d * 0.035F) + 3.0F * (float)Math.sin(t * 0.11F + d * 0.09F + 1.3F);
   }

   private static float side(byte faction) {
      return faction == 0 ? 1.0F : -1.0F;
   }

   private float range(float a, float b) {
      return a + this.rnd.nextFloat() * (b - a);
   }

   public void prewarm(float seconds) {
      BattleSim.Events saved = this.events;
      this.events = null;

      for (float t = 0.0F; t < seconds; t += 0.05F) {
         this.update(0.05F);
      }

      this.events = saved;
   }

   public void update(float dt) {
      dt = Math.max(0.0F, Math.min(0.1F, dt));
      this.time += dt;
      if (this.battle) {
         this.updateGroups(dt);
         this.updateUnits(dt);
         this.updateBolts(dt);
      }

      this.updateExplosions(dt);
      this.updateMissiles(dt);
      this.updateFlyers(dt);

      for (int i = 0; i < 48; i++) {
         this.sAge[i] = this.sAge[i] + dt;
      }
   }

   private void updateGroups(float dt) {
      for (int g = 0; g < this.groups; g++) {
         byte f = this.gFac[g];
         this.gTimer[g] = this.gTimer[g] - dt;
         if (this.gTimer[g] <= 0.0F) {
            float r = this.rnd.nextFloat();
            this.gState[g] = (byte)(r < 0.5F ? 0 : (r < 0.85F ? 1 : 2));
            if (this.gState[g] == 0) {
               this.gStand[g] = f == 0 ? this.range(4.0F, 10.0F) : this.range(5.0F, 12.0F);
            }

            this.gTimer[g] = this.range(4.0F, 12.0F);
         }

         float desired = front(this.gd[g], this.time) + side(f) * (this.gStand[g] + (this.gState[g] == 2 ? 12.0F : 0.0F));
         float speed = (f == 0 ? 1.1F : 1.8F) * (this.gState[g] == 2 ? 0.8F : 1.0F);
         if (this.gState[g] == 1) {
            speed *= 0.25F;
         }

         float diff = desired - this.gx[g];
         float step = Math.min(Math.abs(diff), speed * dt);
         this.gx[g] = this.gx[g] + Math.signum(diff) * step;
         this.gd[g] = this.gd[g] + (float)Math.sin(this.time * 0.07F + g * 1.7F) * 0.12F * dt;
         this.gd[g] = Math.max(24.0F, Math.min(114.0F, this.gd[g]));
      }
   }

   private void updateUnits(float dt) {
      for (int i = 0; i < this.units; i++) {
         int g = this.grp[i];
         byte f = this.fac[i];
         if (this.deadT[i] > 0.0F) {
            this.deadT[i] = this.deadT[i] - dt;
            this.moving[i] = false;
            if (this.deadT[i] <= 0.0F) {
               this.deadT[i] = 0.0F;
               this.ux[i] = this.gx[g] + side(f) * this.range(22.0F, 34.0F);
               this.ud[i] = this.gd[g] + this.offD[i];
            }
         } else {
            float tx = this.gx[g] + this.offX[i] * side(f);
            float td = this.gd[g] + this.offD[i];
            if (this.pauseT[i] > 0.0F) {
               this.pauseT[i] = this.pauseT[i] - dt;
            } else if (this.rnd.nextFloat() < dt * 0.04F) {
               this.pauseT[i] = this.range(1.0F, 4.0F);
            }

            float dx = tx - this.ux[i];
            float speed = (f == 0 ? 1.4F : 2.4F) * (Math.abs(dx) > 6.0F ? 1.8F : 1.0F);
            boolean walk = this.pauseT[i] <= 0.0F && Math.abs(dx) > 0.15F;
            if (walk) {
               this.ux[i] = this.ux[i] + Math.signum(dx) * Math.min(Math.abs(dx), speed * dt);
               this.phase[i] = this.phase[i] + dt * (f == 0 ? 7.0F : 9.0F);
            }

            this.ud[i] = this.ud[i] + (td - this.ud[i]) * Math.min(1.0F, dt * 0.8F);
            this.moving[i] = walk;
            float distToFront = Math.abs(this.ux[i] - front(this.ud[i], this.time));
            if (distToFront < 22.0F) {
               this.fireT[i] = this.fireT[i] - dt;
               if (this.fireT[i] <= 0.0F) {
                  this.fireT[i] = f == 0 ? this.range(1.0F, 3.6F) : this.range(1.4F, 4.2F);
                  this.fire(i);
               }
            }
         }
      }
   }

   private void fire(int i) {
      int slot = -1;

      for (int b = 0; b < this.boltCap; b++) {
         if (this.bLife[b] <= 0.0F) {
            slot = b;
            break;
         }
      }

      if (slot >= 0) {
         byte f = this.fac[i];
         float tx = front(this.ud[i], this.time) - side(f) * this.range(3.0F, 13.0F);
         float td = this.ud[i] + this.range(-4.0F, 4.0F);
         float dx = tx - this.ux[i];
         float dd = td - this.ud[i];
         float len = (float)Math.sqrt(dx * dx + dd * dd);
         if (!(len < 0.5F)) {
            float speed = 42.0F;
            this.bx[slot] = this.ux[i];
            this.bd[slot] = this.ud[i];
            this.bvx[slot] = dx / len * speed;
            this.bvd[slot] = dd / len * speed;
            this.bLife[slot] = len / speed;
            this.bFac[slot] = f;
            if (this.events != null) {
               this.events.shot(this.ux[i], this.ud[i], f);
            }
         }
      }
   }

   private void updateBolts(float dt) {
      for (int b = 0; b < this.boltCap; b++) {
         if (this.bLife[b] > 0.0F) {
            this.bx[b] = this.bx[b] + this.bvx[b] * dt;
            this.bd[b] = this.bd[b] + this.bvd[b] * dt;
            this.bLife[b] = this.bLife[b] - dt;
            if (this.bLife[b] <= 0.0F) {
               this.spark(this.bx[b], this.bd[b]);
               if (this.rnd.nextFloat() < 0.06F) {
                  this.killNear(this.bx[b], this.bd[b], (byte)(1 - this.bFac[b]), 3.0F, 1);
               }
            }
         }
      }
   }

   private void spark(float x, float d) {
      int s = this.sparkNext;
      this.sparkNext = (this.sparkNext + 1) % 48;
      this.sx[s] = x;
      this.sd[s] = d;
      this.sAge[s] = 0.0F;
   }

   private void killNear(float x, float d, byte faction, float radius, int max) {
      int killed = 0;
      int start = this.rnd.nextInt(this.units);

      for (int k = 0; k < this.units && killed < max; k++) {
         int i = (start + k) % this.units;
         if (this.deadT[i] <= 0.0F && (faction < 0 || this.fac[i] == faction)) {
            float dx = this.ux[i] - x;
            float dd = this.ud[i] - d;
            if (dx * dx + dd * dd < radius * radius) {
               this.deadT[i] = this.range(4.5F, 9.0F);
               killed++;
            }
         }
      }
   }

   private void updateExplosions(float dt) {
      for (int i = 0; i < 28; i++) {
         if (this.eAge[i] < 2.4F) {
            this.eAge[i] = this.eAge[i] + dt;
         }
      }

      if (this.explosionsEnabled) {
         this.expTimer = this.expTimer - dt * this.density.activity;
         if (this.expTimer <= 0.0F) {
            this.expTimer = this.range(0.7F, 2.6F);
            float d = this.range(26.0F, 114.0F);
            this.explode(front(d, this.time) + this.range(-15.0F, 15.0F), d, this.range(0.8F, 2.2F));
         }

         this.misTimer = this.misTimer - dt * this.density.activity;
         if (this.misTimer <= 0.0F) {
            this.misTimer = this.range(2.0F, 6.0F);
            this.launchMissile();
         }
      }
   }

   public void explode(float x, float d, float size) {
      int slot = 0;
      float oldest = -1.0F;

      for (int i = 0; i < 28; i++) {
         if (this.eAge[i] > oldest) {
            oldest = this.eAge[i];
            slot = i;
         }
      }

      this.ex[slot] = x;
      this.ed[slot] = d;
      this.eSize[slot] = size;
      this.eAge[slot] = 0.0F;
      this.eSeed[slot] = this.rnd.nextInt();
      if (this.battle) {
         this.killNear(x, d, (byte)-1, size * 1.6F, 2 + Math.round(size * 2.0F));
      }

      if (size > 1.7F) {
         int f = this.fireNext;
         this.fireNext = (this.fireNext + 1) % 10;
         this.fx[f] = x;
         this.fd[f] = d;
         this.fSize[f] = this.range(0.7F, 1.4F);
      }

      if (this.events != null) {
         this.events.explosion(x, d, size);
      }
   }

   private void launchMissile() {
      for (int m = 0; m < 8; m++) {
         if (this.mAge[m] > this.mDur[m] + 1.6F) {
            boolean fromLeft = this.rnd.nextBoolean();
            this.md0[m] = this.range(32.0F, 118.0F);
            this.mx0[m] = front(this.md0[m], this.time) + (fromLeft ? -1 : 1) * this.range(25.0F, 45.0F);
            this.md1[m] = this.md0[m] + this.range(-12.0F, 12.0F);
            this.md1[m] = Math.max(26.0F, Math.min(118.0F, this.md1[m]));
            this.mx1[m] = front(this.md1[m], this.time) + (fromLeft ? 1 : -1) * this.range(2.0F, 12.0F);
            this.mH[m] = this.range(14.0F, 30.0F);
            this.mDur[m] = this.range(2.4F, 4.0F);
            this.mAge[m] = 0.0F;
            return;
         }
      }
   }

   private void updateMissiles(float dt) {
      for (int m = 0; m < 8; m++) {
         if (this.mAge[m] <= this.mDur[m] + 1.6F) {
            float before = this.mAge[m];
            this.mAge[m] = this.mAge[m] + dt;
            if (before < this.mDur[m] && this.mAge[m] >= this.mDur[m]) {
               this.explode(this.mx1[m], this.md1[m], this.range(1.6F, 2.4F));
            }
         }
      }
   }

   public void missileAt(int m, float t, float[] out) {
      out[0] = this.mx0[m] + (this.mx1[m] - this.mx0[m]) * t;
      out[1] = 4.0F * this.mH[m] * t * (1.0F - t);
      out[2] = this.md0[m] + (this.md1[m] - this.md0[m]) * t;
   }

   private void updateFlyers(float dt) {
      for (int f = 0; f < 4; f++) {
         if (this.flAlive[f]) {
            this.flX[f] = this.flX[f] + this.flV[f] * dt;
            if (Math.abs(this.flX[f]) > 90.0F * (this.flD[f] / 118.0F) + 40.0F) {
               this.flAlive[f] = false;
            }
         }
      }

      if (this.flyersEnabled) {
         this.flyTimer -= dt;
         if (this.flyTimer <= 0.0F) {
            this.flyTimer = this.range(6.0F, 16.0F) / Math.max(0.7F, this.density.activity);

            for (int fx = 0; fx < 4; fx++) {
               if (!this.flAlive[fx]) {
                  boolean fighter = this.rnd.nextFloat() < 0.35F;
                  this.flType[fx] = (byte)(fighter ? 1 : 0);
                  this.flD[fx] = this.range(36.0F, 108.0F);
                  this.flY[fx] = this.range(12.0F, 26.0F) * (this.flD[fx] / 70.0F);
                  float dir = this.rnd.nextBoolean() ? 1.0F : -1.0F;
                  this.flX[fx] = -dir * (90.0F * (this.flD[fx] / 118.0F) + 30.0F);
                  this.flV[fx] = dir * (fighter ? this.range(22.0F, 30.0F) : this.range(5.0F, 9.0F));
                  this.flAlive[fx] = true;
                  break;
               }
            }
         }
      }
   }

   public void sortByDepth() {
      int[] o = this.order;

      for (int i = 1; i < o.length; i++) {
         int v = o[i];
         float key = this.ud[v];

         int j;
         for (j = i - 1; j >= 0 && this.ud[o[j]] < key; j--) {
            o[j + 1] = o[j];
         }

         o[j + 1] = v;
      }
   }

   public int aliveCount() {
      int n = 0;

      for (int i = 0; i < this.units; i++) {
         if (this.deadT[i] <= 0.0F) {
            n++;
         }
      }

      return n;
   }

   public static enum Density {
      LOW(90, 0.55F),
      MEDIUM(240, 1.0F),
      HIGH(420, 1.45F),
      ULTRA(700, 1.9F);

      public final int units;
      public final float activity;

      private Density(int units, float activity) {
         this.units = units;
         this.activity = activity;
      }

      public static BattleSim.Density byIndex(int i) {
         BattleSim.Density[] v = values();
         return v[Math.max(0, Math.min(v.length - 1, i))];
      }
   }

   public interface Events {
      void explosion(float var1, float var2, float var3);

      void shot(float var1, float var2, byte var3);
   }
}
