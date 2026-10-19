package com.lennart.nebula.afk.valley;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Kampf von Naruto und Sasuke im Tal des Endes – reine Simulation in Weltkoordinaten (Blöcke): Sprünge zwischen
 * Statuen, Wasser und Wasserfall, Schlagabtausch, Kunai, Feuerball, Schattendoppelgänger, Kirin, Lauf am Wasserfall
 * und als Höhepunkt Rasengan gegen Chidori mit Kurama und Susanoo.
 */
public final class ValleyFight {
   public static final int NARUTO = 0;
   public static final int SASUKE = 1;
   // Effektarten
   public static final int SPARK = 0;
   public static final int SMOKE = 1;
   public static final int DROP = 2;
   public static final int RIPPLE = 3;
   public static final int FIREBALL = 4;
   public static final int BLAST = 5;
   public static final int BOLT = 6;
   public static final int KUNAI = 7;
   public static final int DOME = 8;
   public static final int SHOCKRING = 9;
   public static final int STEAM = 10;
   public static final int SLASH = 11;
   public static final int WIND = 12;
   public static final int EMBER = 13;
   private static final int MAX_FX = 700;
   private final ValleyTracer world;
   private final Random rnd;
   public final Fighter naruto;
   public final Fighter sasuke;
   public final List<Clone> clones = new ArrayList<>();
   public final List<Fx> fx = new ArrayList<>();
   /** Bildschirm-Effekte: Blitzlicht, Wackeln, Verdunkelung (Kirin), Avatare. */
   public float flash;
   public int flashColor = 0xFFFFFF;
   public float shake;
   public float darken;
   private float darkenTarget;
   public float kurama;
   public float susanoo;
   private float kuramaTarget;
   private float susanooTarget;
   public float time;
   private Events events = Events.NONE;
   private boolean enabled = true;
   private boolean effects = true;
   // Ablauf
   private Move move = Move.IDLE;
   private int step;
   private float timer;
   private float stepTimer;
   private int repeat;
   private int moveCount;
   private int nextFinale = 5;
   private Move last = Move.IDLE;
   private Fighter atk;
   private Fighter def;
   private float mx;
   private float my;
   private float mz;
   private int counter;
   private boolean flag;

   public interface Events {
      Events NONE = new Events() {
      };

      default void clash(float power) {
      }

      default void boom(float power) {
      }

      default void splash(float power) {
      }

      default void poof() {
      }

      default void fire() {
      }

      default void thunder(float power) {
      }

      default void chidori(float level) {
      }

      default void rasengan(float level) {
      }

      default void whoosh() {
      }
   }

   public enum Pose {
      STAND,
      RUN,
      AIR,
      PUNCH,
      KICK,
      THROW,
      SIGN,
      RAISE,
      CHARGE,
      HURT,
      DOWN,
      CROUCH
   }

   private enum Move {
      IDLE,
      CLASH,
      FLURRY,
      KUNAI,
      KATON,
      CLONES,
      KIRIN,
      WALLRUN,
      FINALE
   }

   public static final class Fighter {
      public final int who;
      public float x;
      public float y;
      public float z;
      public float face = 1.0F;
      public Pose pose = Pose.STAND;
      public float poseTime;
      private float poseDur;
      private boolean pinned;
      public float aura;
      public float charge;
      public boolean visible = true;
      public float speed;
      public final float[] trail = new float[24];
      public int trailCount;
      private float trailTimer;
      private boolean moving;
      private float sx;
      private float sy;
      private float sz;
      private float ex;
      private float ey;
      private float ez;
      private float mt;
      private float mdur;
      private float arc;
      private float rippleTimer;

      Fighter(int who) {
         this.who = who;
      }

      public boolean moving() {
         return this.moving;
      }
   }

   public static final class Clone {
      public float x;
      public float y;
      public float z;
      public float face;
      public boolean alive = true;
      boolean launched;
      float delay;
      float sx;
      float sy;
      float sz;
      float ex;
      float ey;
      float ez;
      float t;
      float dur;
   }

   public static final class Fx {
      public int type;
      public float x;
      public float y;
      public float z;
      public float vx;
      public float vy;
      public float vz;
      public float x2;
      public float y2;
      public float z2;
      public float age;
      public float dur;
      public float size;
      public int seed;
      public int color;
   }

   public ValleyFight(ValleyTracer world, long seed) {
      this.world = world;
      this.rnd = new Random(seed);
      this.naruto = new Fighter(NARUTO);
      this.sasuke = new Fighter(SASUKE);
      float[] h = this.statueHead(1);
      this.naruto.x = h[0];
      this.naruto.y = h[1];
      this.naruto.z = h[2];
      h = this.statueHead(-1);
      this.sasuke.x = h[0];
      this.sasuke.y = h[1];
      this.sasuke.z = h[2];
      this.nextFinale = 4 + this.rnd.nextInt(3);
      this.timer = -1.5F;
   }

   /** Aktueller Spielzug (für Tests). */
   public String moveName() {
      return this.move.name();
   }

   public void setEvents(Events e) {
      this.events = e == null ? Events.NONE : e;
   }

   public void configure(boolean enabled, boolean effects) {
      this.enabled = enabled;
      this.effects = effects;
   }

   // ================================================================== Orte

   private float ground(float x, float z) {
      return this.world.groundY(x, z);
   }

   /** Kopf der Statue (side -1 = Madara links, +1 = Hashirama rechts). */
   private float[] statueHead(int side) {
      float x = side * ValleyTracer.STATUE_X;
      float z = ValleyTracer.STATUE_Z;
      return new float[]{x, this.ground(x, z), z};
   }

   private float[] statueHand(int side) {
      float x = side * (ValleyTracer.STATUE_X - 3.2F);
      float z = ValleyTracer.STATUE_Z - 4.9F;
      return new float[]{x, this.ground(x, z), z};
   }

   private float[] statueShoulder(int side) {
      float x = side * (ValleyTracer.STATUE_X + 3.9F);
      float z = ValleyTracer.STATUE_Z - 5.6F;
      return new float[]{x, this.ground(x, z), z};
   }

   private float[] waterSpot(float minX, float maxX, float minZ, float maxZ) {
      for (int i = 0; i < 30; i++) {
         float x = minX + this.rnd.nextFloat() * (maxX - minX);
         float z = minZ + this.rnd.nextFloat() * (maxZ - minZ);
         if (this.ground(x, z) <= 0.5F) {
            return new float[]{x, 0.0F, z};
         }
      }

      return new float[]{0.0F, 0.0F, 34.0F};
   }

   private float[] anchor(Fighter f) {
      float r = this.rnd.nextFloat();
      int side = this.rnd.nextBoolean() ? 1 : -1;
      float[] p;
      if (r < 0.55F) {
         p = this.waterSpot(-15.0F, 15.0F, 24.0F, 50.0F);
      } else if (r < 0.7F) {
         p = this.statueHead(side);
      } else if (r < 0.82F) {
         p = this.statueHand(side);
      } else {
         p = this.statueShoulder(side);
      }

      Fighter o = f == this.naruto ? this.sasuke : this.naruto;
      if (Math.abs(p[0] - o.x) < 2.0F && Math.abs(p[2] - o.z) < 2.0F) {
         p = this.waterSpot(-15.0F, 15.0F, 24.0F, 50.0F);
      }

      return p;
   }

   // ================================================================== Bewegung

   private void jump(Fighter f, float x, float y, float z, float dur, float arc) {
      f.sx = f.x;
      f.sy = f.y;
      f.sz = f.z;
      f.ex = x;
      f.ey = y;
      f.ez = z;
      f.mt = 0.0F;
      f.mdur = Math.max(0.1F, dur);
      f.arc = arc;
      f.moving = true;
      if (Math.abs(x - f.x) > 0.2F) {
         f.face = Math.signum(x - f.x);
      }

      if (f.y < 0.3F && arc > 0.8F) {
         this.splash(f.x, 0.0F, f.z, 5, 0.5F);
      }
   }

   private static float dist(Fighter f, float x, float y, float z) {
      float dx = x - f.x;
      float dy = y - f.y;
      float dz = z - f.z;
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private void pose(Fighter f, Pose p, float dur) {
      f.pose = p;
      f.poseTime = 0.0F;
      f.poseDur = dur;
      f.pinned = dur < 0.0F;
   }

   private void unpin(Fighter f) {
      f.pinned = false;
      f.poseDur = 0.0F;
      f.pose = f.moving ? Pose.AIR : Pose.STAND;
   }

   private void updateFighter(Fighter f, Fighter other, float dt) {
      if (f.moving) {
         f.mt += dt;
         float u = Math.min(1.0F, f.mt / f.mdur);
         float ox = f.x;
         float oy = f.y;
         float oz = f.z;
         f.x = f.sx + (f.ex - f.sx) * u;
         f.y = f.sy + (f.ey - f.sy) * u + f.arc * 4.0F * u * (1.0F - u);
         f.z = f.sz + (f.ez - f.sz) * u;
         float d = (float)Math.sqrt((f.x - ox) * (f.x - ox) + (f.y - oy) * (f.y - oy) + (f.z - oz) * (f.z - oz));
         f.speed = dt > 0.0F ? d / dt : 0.0F;
         if (!f.pinned && f.poseDur <= 0.0F) {
            f.pose = f.arc > 0.8F ? Pose.AIR : Pose.RUN;
         }

         if (u >= 1.0F) {
            f.moving = false;
            f.speed = 0.0F;
            if (!f.pinned && f.poseDur <= 0.0F) {
               f.pose = Pose.STAND;
            }

            if (f.y < 0.3F) {
               this.splash(f.x, 0.0F, f.z, f.arc > 1.5F ? 8 : 3, f.arc > 1.5F ? 0.8F : 0.4F);
               this.ripple(f.x, f.z, 2.5F, 0.9F);
            } else if (f.arc > 1.5F) {
               this.dust(f.x, f.y, f.z, 4);
            }
         }
      } else {
         f.speed = 0.0F;
         if (!f.pinned && f.poseDur <= 0.0F && Math.abs(other.x - f.x) > 0.3F) {
            f.face = Math.signum(other.x - f.x);
         }

         if (f.y < 0.3F) {
            f.rippleTimer -= dt;
            if (f.rippleTimer <= 0.0F) {
               f.rippleTimer = 0.7F + this.rnd.nextFloat() * 0.5F;
               this.ripple(f.x, f.z, 1.6F, 1.4F);
            }
         }
      }

      f.poseTime += dt;
      if (!f.pinned && f.poseDur > 0.0F && f.poseTime >= f.poseDur) {
         f.poseDur = 0.0F;
         f.pose = f.moving ? (f.arc > 0.8F ? Pose.AIR : Pose.RUN) : Pose.STAND;
      }

      // Nachbilder bei hoher Geschwindigkeit
      f.trailTimer -= dt;
      if (f.trailTimer <= 0.0F) {
         f.trailTimer = 0.035F;
         if (f.speed > 14.0F) {
            System.arraycopy(f.trail, 0, f.trail, 3, f.trail.length - 3);
            f.trail[0] = f.x;
            f.trail[1] = f.y;
            f.trail[2] = f.z;
            f.trailCount = Math.min(8, f.trailCount + 1);
         } else if (f.trailCount > 0) {
            f.trailCount--;
         }
      }
   }

   // ================================================================== Effekte

   private Fx add(int type, float x, float y, float z, float dur, float size) {
      Fx e = new Fx();
      e.type = type;
      e.x = x;
      e.y = y;
      e.z = z;
      e.dur = dur;
      e.size = size;
      e.seed = this.rnd.nextInt();
      if (this.fx.size() < MAX_FX) {
         this.fx.add(e);
      }

      return e;
   }

   private void sparks(float x, float y, float z, int n, float power) {
      for (int i = 0; i < n; i++) {
         Fx e = this.add(SPARK, x, y, z, 0.18F + this.rnd.nextFloat() * 0.3F, 0.12F + this.rnd.nextFloat() * 0.1F);
         float a = this.rnd.nextFloat() * 6.283F;
         float b = (this.rnd.nextFloat() - 0.3F) * 2.0F;
         float sp = (5.0F + this.rnd.nextFloat() * 9.0F) * power;
         e.vx = (float)Math.cos(a) * sp;
         e.vy = b * sp * 0.7F;
         e.vz = (float)Math.sin(a) * sp * 0.6F;
         e.color = this.rnd.nextFloat() < 0.5F ? 0xFFF4B0 : 0xFFFFFF;
      }
   }

   private void splash(float x, float y, float z, int n, float power) {
      for (int i = 0; i < n; i++) {
         Fx e = this.add(DROP, x + (this.rnd.nextFloat() - 0.5F) * 0.8F, y + 0.1F, z + (this.rnd.nextFloat() - 0.5F) * 0.8F, 0.8F, 0.14F + this.rnd.nextFloat() * 0.12F);
         float a = this.rnd.nextFloat() * 6.283F;
         float sp = (1.5F + this.rnd.nextFloat() * 3.0F) * power;
         e.vx = (float)Math.cos(a) * sp;
         e.vz = (float)Math.sin(a) * sp;
         e.vy = (4.0F + this.rnd.nextFloat() * 6.0F) * power;
      }

      if (n > 6) {
         this.events.splash(power);
      }
   }

   private void ripple(float x, float z, float radius, float dur) {
      Fx e = this.add(RIPPLE, x, 0.04F, z, dur, radius);
      e.color = 0xFFFFFF;
   }

   private void smoke(float x, float y, float z, int n, float size) {
      for (int i = 0; i < n; i++) {
         Fx e = this.add(SMOKE, x + (this.rnd.nextFloat() - 0.5F) * size, y + this.rnd.nextFloat() * size, z + (this.rnd.nextFloat() - 0.5F) * size, 0.5F + this.rnd.nextFloat() * 0.35F, size * (0.4F + this.rnd.nextFloat() * 0.4F));
         e.vx = (this.rnd.nextFloat() - 0.5F) * 2.0F;
         e.vy = 0.6F + this.rnd.nextFloat() * 1.2F;
         e.vz = (this.rnd.nextFloat() - 0.5F) * 2.0F;
      }
   }

   private void steam(float x, float y, float z, int n, float spread) {
      for (int i = 0; i < n; i++) {
         Fx e = this.add(STEAM, x + (this.rnd.nextFloat() - 0.5F) * spread, y + this.rnd.nextFloat(), z + (this.rnd.nextFloat() - 0.5F) * spread, 1.6F + this.rnd.nextFloat() * 1.4F, 1.0F + this.rnd.nextFloat() * 1.5F);
         e.vy = 1.5F + this.rnd.nextFloat() * 2.0F;
         e.vx = (this.rnd.nextFloat() - 0.5F) * 1.5F;
         e.vz = -0.5F - this.rnd.nextFloat();
      }
   }

   private void dust(float x, float y, float z, int n) {
      for (int i = 0; i < n; i++) {
         Fx e = this.add(SMOKE, x + (this.rnd.nextFloat() - 0.5F), y + 0.1F, z + (this.rnd.nextFloat() - 0.5F), 0.5F, 0.35F + this.rnd.nextFloat() * 0.3F);
         e.vx = (this.rnd.nextFloat() - 0.5F) * 3.0F;
         e.vy = 0.5F + this.rnd.nextFloat();
         e.color = 1;
      }
   }

   private void bolt(float x, float y, float z, float x2, float y2, float z2, float dur, float size, int color) {
      Fx e = this.add(BOLT, x, y, z, dur, size);
      e.x2 = x2;
      e.y2 = y2;
      e.z2 = z2;
      e.color = color;
   }

   private void blast(float x, float y, float z, float size, int color) {
      Fx e = this.add(BLAST, x, y, z, 0.9F, size);
      e.color = color;
   }

   private void hitFlash(float a, int color) {
      if (this.effects && a > this.flash) {
         this.flash = a;
         this.flashColor = color;
      }
   }

   private static float[] hand(Fighter f) {
      return new float[]{f.x + f.face * 0.55F, f.y + 1.15F, f.z - 0.1F};
   }

   private void updateFx(float dt) {
      for (int i = this.fx.size() - 1; i >= 0; i--) {
         Fx e = this.fx.get(i);
         e.age += dt;
         switch (e.type) {
            case SPARK:
               e.vy -= 22.0F * dt;
               e.x += e.vx * dt;
               e.y += e.vy * dt;
               e.z += e.vz * dt;
               break;
            case DROP:
               e.vy -= 25.0F * dt;
               e.x += e.vx * dt;
               e.y += e.vy * dt;
               e.z += e.vz * dt;
               if (e.y < 0.0F && e.vy < 0.0F) {
                  e.age = e.dur;
               }
               break;
            case SMOKE:
            case STEAM:
            case EMBER:
            case WIND:
               e.x += e.vx * dt;
               e.y += e.vy * dt;
               e.z += e.vz * dt;
               e.vx *= 1.0F - dt * 1.5F;
               e.vz *= 1.0F - dt * 1.5F;
               break;
            case KUNAI:
            case FIREBALL:
               float u = Math.min(1.0F, e.age / e.dur);
               e.x = e.vx + (e.x2 - e.vx) * u;
               e.y = e.vy + (e.y2 - e.vy) * u;
               e.z = e.vz + (e.z2 - e.vz) * u;
               if (e.type == FIREBALL && this.rnd.nextFloat() < 0.35F) {
                  Fx s = this.add(EMBER, e.x, e.y, e.z, 0.45F, e.size * 0.12F * Math.min(1.0F, e.age * 3.0F));
                  s.vy = 0.8F;
                  s.vx = (this.rnd.nextFloat() - 0.5F);
               }

               if (e.age >= e.dur) {
                  this.arrive(e);
               }
               break;
            default:
         }

         if (e.age >= e.dur) {
            this.fx.remove(i);
         }
      }
   }

   private void arrive(Fx e) {
      if (e.type == KUNAI) {
         this.sparks(e.x, e.y, e.z, 6, 0.7F);
         this.events.clash(0.3F);
         Fighter d = dist(this.naruto, e.x, e.y, e.z) < dist(this.sasuke, e.x, e.y, e.z) ? this.naruto : this.sasuke;
         this.pose(d, Pose.PUNCH, 0.15F);
      } else {
         this.blast(e.x, e.y, e.z, 4.2F, 0);
         this.smoke(e.x, e.y, e.z, 6, 2.2F);
         if (e.y < 2.5F) {
            this.steam(e.x, 0.0F, e.z, 12, 5.0F);
            this.splash(e.x, 0.0F, e.z, 18, 1.3F);
            this.ripple(e.x, e.z, 7.0F, 1.6F);
         } else {
            this.dust(e.x, e.y, e.z, 8);
         }

         this.shake = Math.max(this.shake, 0.35F);
         this.hitFlash(0.22F, 0xFFB060);
         this.events.boom(0.8F);
      }
   }

   // ================================================================== Ablauf

   public void prewarm(float seconds) {
      for (float t = 0.0F; t < seconds; t += 0.05F) {
         this.update(0.05F);
      }
   }

   public void update(float dt) {
      dt = Math.min(dt, 0.1F);
      this.time += dt;
      this.flash = Math.max(0.0F, this.flash - dt * 2.2F);
      this.shake = Math.max(0.0F, this.shake - dt * 1.6F);
      this.darken += (this.darkenTarget - this.darken) * Math.min(1.0F, dt * 2.0F);
      this.kurama += (this.kuramaTarget - this.kurama) * Math.min(1.0F, dt * (this.kuramaTarget > this.kurama ? 1.2F : 5.0F));
      this.susanoo += (this.susanooTarget - this.susanoo) * Math.min(1.0F, dt * (this.susanooTarget > this.susanoo ? 1.2F : 5.0F));
      if (this.enabled) {
         this.timer += dt;
         this.stepTimer += dt;
         this.updateMove(dt);
      }

      this.updateFighter(this.naruto, this.sasuke, dt);
      this.updateFighter(this.sasuke, this.naruto, dt);
      this.updateClones(dt);
      this.updateFx(dt);
      this.ambientFx(dt);
   }

   private void ambientFx(float dt) {
      // Kurama-Chakra: Flammen um Naruto, Chidori knistert, Rasengan saugt Wind an
      Fighter n = this.naruto;
      if (n.aura > 0.05F && this.rnd.nextFloat() < n.aura * 0.9F) {
         Fx e = this.add(EMBER, n.x + (this.rnd.nextFloat() - 0.5F) * 0.9F, n.y + this.rnd.nextFloat() * 1.8F, n.z, 0.45F, 0.16F);
         e.vy = 2.2F;
         e.color = 1;
      }

      if (n.charge > 0.05F && this.rnd.nextFloat() < 0.7F) {
         float[] h = hand(n);
         float a = this.rnd.nextFloat() * 6.283F;
         float r = 1.0F + n.charge * 1.5F;
         Fx e = this.add(WIND, h[0] + (float)Math.cos(a) * r, h[1] + (float)Math.sin(a) * r, h[2], 0.25F, 0.12F);
         e.vx = -(float)Math.cos(a) * r * 3.5F;
         e.vy = -(float)Math.sin(a) * r * 3.5F;
      }

      Fighter s = this.sasuke;
      if (s.charge > 0.05F && this.rnd.nextFloat() < 0.35F + s.charge * 0.5F) {
         float[] h = hand(s);
         float a = this.rnd.nextFloat() * 6.283F;
         float len = 0.6F + s.charge * (0.8F + this.rnd.nextFloat() * 1.6F);
         this.bolt(h[0], h[1], h[2], h[0] + (float)Math.cos(a) * len, h[1] + (float)Math.sin(a) * len, h[2], 0.07F, 0.5F, 0xC8E8FF);
         if (s.charge > 0.6F && this.rnd.nextFloat() < 0.12F) {
            this.bolt(h[0], h[1], h[2], s.x + (this.rnd.nextFloat() - 0.5F) * 3.0F, s.y - 0.5F, s.z, 0.08F, 0.6F, 0xA0C8FF);
         }
      }
   }

   private void updateClones(float dt) {
      for (int i = this.clones.size() - 1; i >= 0; i--) {
         Clone c = this.clones.get(i);
         if (!c.alive) {
            this.clones.remove(i);
         } else if (c.launched) {
            c.t += dt;
            float u = Math.min(1.0F, c.t / c.dur);
            c.x = c.sx + (c.ex - c.sx) * u;
            c.y = c.sy + (c.ey - c.sy) * u + 2.5F * 4.0F * u * (1.0F - u);
            c.z = c.sz + (c.ez - c.sz) * u;
            if (u >= 1.0F) {
               c.alive = false;
               this.smoke(c.x, c.y + 0.6F, c.z, 5, 1.2F);
               this.sparks(c.x, c.y + 1.0F, c.z, 3, 0.6F);
               this.events.poof();
               this.pose(this.sasuke, this.counter++ % 2 == 0 ? Pose.PUNCH : Pose.KICK, 0.14F);
               this.sasuke.face = Math.signum(c.x - this.sasuke.x + 0.001F);
               Fx sl = this.add(SLASH, this.sasuke.x + this.sasuke.face * 0.6F, this.sasuke.y + 1.0F, this.sasuke.z, 0.18F, 1.2F);
               sl.color = this.sasuke.face > 0.0F ? 1 : -1;
            }
         }
      }
   }

   private void startMove() {
      Move m;
      if (this.moveCount >= this.nextFinale) {
         m = Move.FINALE;
      } else {
         Move[] pool = {Move.CLASH, Move.CLASH, Move.FLURRY, Move.FLURRY, Move.KUNAI, Move.KATON, Move.CLONES, Move.KIRIN, Move.WALLRUN};

         do {
            m = pool[this.rnd.nextInt(pool.length)];
         } while (m == this.last);
      }

      this.move = m;
      this.last = m;
      this.step = 0;
      this.stepTimer = 0.0F;
      this.counter = 0;
      this.flag = false;
      this.repeat = 1 + this.rnd.nextInt(3);
      this.moveCount++;
      boolean n = this.rnd.nextBoolean();
      this.atk = n ? this.naruto : this.sasuke;
      this.def = n ? this.sasuke : this.naruto;
      if (m == Move.KATON || m == Move.KIRIN) {
         this.atk = this.sasuke;
         this.def = this.naruto;
      } else if (m == Move.CLONES) {
         this.atk = this.naruto;
         this.def = this.sasuke;
      }
   }

   private void next() {
      this.step++;
      this.stepTimer = 0.0F;
   }

   private void done() {
      this.move = Move.IDLE;
      this.timer = -(0.4F + this.rnd.nextFloat() * 0.9F);
      this.unpin(this.naruto);
      this.unpin(this.sasuke);
   }

   private boolean bothLanded() {
      return !this.naruto.moving && !this.sasuke.moving;
   }

   private void updateMove(float dt) {
      Fighter n = this.naruto;
      Fighter s = this.sasuke;
      switch (this.move) {
         case IDLE:
            if (this.timer >= 0.0F && this.bothLanded()) {
               this.startMove();
            }
            break;
         case CLASH:
            this.clash();
            break;
         case FLURRY:
            this.flurry(dt);
            break;
         case KUNAI:
            this.kunai();
            break;
         case KATON:
            this.katon();
            break;
         case CLONES:
            this.clonesMove();
            break;
         case KIRIN:
            this.kirin();
            break;
         case WALLRUN:
            this.wallrun(dt);
            break;
         case FINALE:
            this.finale(dt);
      }
   }

   /** Treffen in der Luft, Funken, Abprall. */
   private void clash() {
      Fighter n = this.naruto;
      Fighter s = this.sasuke;
      switch (this.step) {
         case 0: {
            this.mx = -9.0F + this.rnd.nextFloat() * 18.0F;
            this.my = 10.0F + this.rnd.nextFloat() * 22.0F;
            this.mz = 38.0F + this.rnd.nextFloat() * 18.0F;
            Fighter left = n.x < s.x ? n : s;
            Fighter right = left == n ? s : n;
            float dur = Math.max(0.35F, Math.min(0.8F, Math.max(dist(left, this.mx, this.my, this.mz), dist(right, this.mx, this.my, this.mz)) / 32.0F));
            this.jump(left, this.mx - 0.55F, this.my - 0.9F, this.mz, dur, 2.0F);
            this.jump(right, this.mx + 0.55F, this.my - 0.9F, this.mz, dur, 2.0F);
            this.events.whoosh();
            this.next();
            break;
         }
         case 1:
            if (this.bothLanded()) {
               this.pose(n, Pose.PUNCH, 0.2F);
               this.pose(s, Pose.PUNCH, 0.2F);
               this.sparks(this.mx, this.my, this.mz, 14, 1.0F);
               this.hitFlash(0.2F, 0xFFFFFF);
               this.shake = Math.max(this.shake, 0.18F);
               this.events.clash(0.7F);
               float[] a = this.anchor(n);
               float[] b = this.anchor(s);
               this.jump(n, a[0], a[1], a[2], 0.75F + this.rnd.nextFloat() * 0.3F, 4.0F);
               this.jump(s, b[0], b[1], b[2], 0.75F + this.rnd.nextFloat() * 0.3F, 4.0F);
               this.next();
            }
            break;
         default:
            if (this.bothLanded()) {
               if (--this.repeat > 0) {
                  this.step = 0;
               } else {
                  this.done();
               }
            }
      }
   }

   /** Schlagabtausch auf dem Wasser, endet mit einem Tritt. */
   private void flurry(float dt) {
      Fighter n = this.naruto;
      Fighter s = this.sasuke;
      switch (this.step) {
         case 0: {
            float[] w = this.waterSpot(-10.0F, 10.0F, 28.0F, 46.0F);
            this.mx = w[0];
            this.mz = w[2];
            Fighter left = n.x < s.x ? n : s;
            Fighter right = left == n ? s : n;
            float dur = Math.max(0.4F, Math.min(1.0F, Math.max(dist(left, this.mx, 0.0F, this.mz), dist(right, this.mx, 0.0F, this.mz)) / 26.0F));
            this.jump(left, this.mx - 0.7F, 0.0F, this.mz, dur, left.y > 1.0F ? 3.0F : 0.4F);
            this.jump(right, this.mx + 0.7F, 0.0F, this.mz, dur, right.y > 1.0F ? 3.0F : 0.4F);
            this.next();
            break;
         }
         case 1:
            if (this.bothLanded()) {
               this.my = 1.8F + this.rnd.nextFloat() * 0.8F;
               this.counter = 0;
               this.next();
            }
            break;
         case 2: {
            // beide driften gemeinsam übers Wasser
            float drift = (float)Math.sin(this.stepTimer * 2.3F) * 2.2F * dt;
            n.x += drift;
            s.x += drift;
            if (this.stepTimer > this.counter * 0.13F) {
               this.counter++;
               Fighter a = this.counter % 2 == 0 ? n : s;
               Fighter b = a == n ? s : n;
               this.pose(a, this.rnd.nextFloat() < 0.6F ? Pose.PUNCH : Pose.KICK, 0.11F);
               this.pose(b, Pose.PUNCH, 0.08F);
               float cx = (n.x + s.x) / 2.0F;
               float cy = 0.9F + this.rnd.nextFloat() * 0.6F;
               this.sparks(cx, cy, n.z, 3, 0.5F);
               if (this.counter % 2 == 0) {
                  this.events.clash(0.25F);
               }

               if (this.counter % 3 == 0) {
                  this.ripple(cx, n.z, 2.2F, 0.8F);
               }
            }

            if (this.stepTimer > this.my) {
               Fighter winner = this.rnd.nextBoolean() ? n : s;
               Fighter loser = winner == n ? s : n;
               this.pose(winner, Pose.KICK, 0.3F);
               this.pose(loser, Pose.HURT, -1.0F);
               this.sparks((n.x + s.x) / 2.0F, 1.2F, n.z, 10, 1.0F);
               this.hitFlash(0.14F, 0xFFFFFF);
               this.shake = Math.max(this.shake, 0.15F);
               this.events.clash(0.8F);
               float dir = Math.signum(loser.x - winner.x + 0.001F);
               float[] w = this.waterSpot(loser.x + dir * 6.0F, loser.x + dir * 11.0F, loser.z - 3.0F, loser.z + 3.0F);
               this.jump(loser, w[0], 0.0F, w[2], 0.55F, 1.2F);
               loser.face = -dir;
               this.next();
            }
            break;
         }
         case 3:
            if (this.bothLanded()) {
               Fighter loser = n.pose == Pose.HURT ? n : s;
               this.splash(loser.x, 0.0F, loser.z, 10, 0.9F);
               this.ripple(loser.x, loser.z, 4.0F, 1.2F);
               this.pose(loser, Pose.CROUCH, 0.45F);
               loser.pinned = false;
               this.next();
            }
            break;
         default:
            if (this.stepTimer > 0.5F) {
               this.done();
            }
      }
   }

   /** Drei Kunai, die abgewehrt werden. */
   private void kunai() {
      Fighter a = this.atk;
      Fighter d = this.def;
      switch (this.step) {
         case 0:
            if (dist(a, d.x, d.y, d.z) < 7.0F) {
               float[] w = this.waterSpot(-15.0F, 15.0F, 24.0F, 50.0F);
               this.jump(a, w[0], w[1], w[2], 0.6F, 3.5F);
            }

            this.next();
            break;
         case 1:
            if (!a.moving && this.stepTimer > 0.15F + this.counter * 0.27F) {
               if (this.counter >= 3) {
                  if (this.stepTimer > 1.4F) {
                     this.done();
                  }
               } else {
                  this.counter++;
                  this.pose(a, Pose.THROW, 0.18F);
                  float[] h = hand(a);
                  float tx = d.x + (this.rnd.nextFloat() - 0.5F) * 0.4F;
                  float ty = d.y + 0.9F + this.rnd.nextFloat() * 0.5F;
                  float tz = d.z;
                  float len = (float)Math.sqrt((tx - h[0]) * (tx - h[0]) + (ty - h[1]) * (ty - h[1]) + (tz - h[2]) * (tz - h[2]));
                  Fx k = this.add(KUNAI, h[0], h[1], h[2], Math.max(0.12F, len / 42.0F), 0.5F);
                  k.vx = h[0];
                  k.vy = h[1];
                  k.vz = h[2];
                  k.x2 = tx;
                  k.y2 = ty;
                  k.z2 = tz;
                  this.events.whoosh();
               }
            }
      }
   }

   /** Sasukes Feuerball (Katon: Gōkakyū no Jutsu). */
   private void katon() {
      Fighter s = this.sasuke;
      Fighter n = this.naruto;
      switch (this.step) {
         case 0:
            if (dist(s, n.x, n.y, n.z) < 11.0F) {
               float dir = Math.signum(s.x - n.x + 0.001F);
               float[] w = this.waterSpot(n.x + dir * 13.0F, n.x + dir * 18.0F, 26.0F, 46.0F);
               this.jump(s, w[0], w[1], w[2], 0.75F, 3.5F);
            }

            this.next();
            break;
         case 1:
            if (!s.moving) {
               s.face = Math.signum(n.x - s.x + 0.001F);
               this.pose(s, Pose.SIGN, -1.0F);
               this.next();
            }
            break;
         case 2:
            if (this.stepTimer > 0.8F) {
               this.pose(s, Pose.CHARGE, -1.0F);
               float sx = s.x + s.face * 0.8F;
               float sy = s.y + 1.5F;
               float tx = n.x;
               float ty = n.y + 0.9F;
               float tz = n.z;
               float len = (float)Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy) + (tz - s.z) * (tz - s.z));
               Fx f = this.add(FIREBALL, sx, sy, s.z, Math.max(0.6F, len / 15.0F), 3.4F);
               f.vx = sx;
               f.vy = sy;
               f.vz = s.z;
               f.x2 = tx;
               f.y2 = ty;
               f.z2 = tz;
               this.events.fire();
               this.flag = false;
               this.next();
            }
            break;
         case 3: {
            Fx ball = null;

            for (Fx e : this.fx) {
               if (e.type == FIREBALL) {
                  ball = e;
               }
            }

            if (ball != null && !this.flag && ball.dur - ball.age < 0.38F) {
               this.flag = true;
               float[] p = this.anchor(n);
               this.jump(n, p[0], p[1], p[2], 0.65F, 4.5F);
            }

            if (this.stepTimer > 0.9F) {
               this.unpin(s);
            }

            if (ball == null && this.bothLanded()) {
               this.done();
            }
         }
      }
   }

   /** Schattendoppelgänger stürmen auf Sasuke ein. */
   private void clonesMove() {
      Fighter n = this.naruto;
      Fighter s = this.sasuke;
      switch (this.step) {
         case 0:
            if (!n.moving) {
               this.pose(n, Pose.SIGN, -1.0F);
               this.next();
            }
            break;
         case 1:
            if (this.stepTimer > 0.45F) {
               this.unpin(n);
               int count = 5 + this.rnd.nextInt(3);

               for (int i = 0; i < count; i++) {
                  Clone c = new Clone();
                  float a = this.rnd.nextFloat() * 6.283F;
                  float r = 1.4F + this.rnd.nextFloat() * 2.4F;
                  c.x = n.x + (float)Math.cos(a) * r;
                  c.z = n.z + (float)Math.sin(a) * r * 0.6F;
                  c.y = n.y > 1.0F ? n.y + (this.rnd.nextFloat() - 0.3F) * 2.0F : 0.0F;
                  c.face = n.face;
                  c.delay = 0.35F + i * 0.17F;
                  this.clones.add(c);
                  this.smoke(c.x, c.y + 0.6F, c.z, 4, 1.1F);
               }

               this.events.poof();
               this.next();
            }
            break;
         case 2: {
            boolean all = true;

            for (Clone c : this.clones) {
               if (!c.launched) {
                  all = false;
                  if (this.stepTimer > c.delay) {
                     c.launched = true;
                     c.sx = c.x;
                     c.sy = c.y;
                     c.sz = c.z;
                     c.ex = s.x + (this.rnd.nextFloat() - 0.5F) * 1.2F;
                     c.ey = s.y + 0.2F;
                     c.ez = s.z + (this.rnd.nextFloat() - 0.5F) * 0.6F;
                     c.face = Math.signum(s.x - c.x + 0.001F);
                     c.dur = 0.42F + this.rnd.nextFloat() * 0.15F;
                  }
               }
            }

            if (all && this.clones.isEmpty()) {
               this.jump(n, s.x - Math.signum(s.x - n.x + 0.001F) * 0.6F, s.y, s.z, 0.42F, 1.5F);
               this.events.whoosh();
               this.next();
            }
            break;
         }
         case 3:
            if (!n.moving) {
               this.pose(n, Pose.PUNCH, 0.3F);
               this.pose(s, Pose.HURT, -1.0F);
               this.sparks((n.x + s.x) / 2.0F, s.y + 1.2F, s.z, 12, 1.1F);
               this.hitFlash(0.2F, 0xFFFFFF);
               this.shake = Math.max(this.shake, 0.25F);
               this.events.clash(1.0F);
               float dir = Math.signum(s.x - n.x + 0.001F);
               float[] w = this.waterSpot(s.x + dir * 8.0F, s.x + dir * 12.0F, 26.0F, 48.0F);
               this.jump(s, w[0], 0.0F, w[2], 0.6F, 1.8F);
               this.next();
            }
            break;
         case 4:
            if (!s.moving) {
               this.splash(s.x, 0.0F, s.z, 12, 1.0F);
               this.ripple(s.x, s.z, 4.5F, 1.3F);
               this.pose(s, Pose.CROUCH, 0.5F);
               s.pinned = false;
               this.next();
            }
            break;
         default:
            if (this.stepTimer > 0.6F) {
               this.done();
            }
      }
   }

   /** Kirin: Blitz aus dem Himmel. */
   private void kirin() {
      Fighter s = this.sasuke;
      Fighter n = this.naruto;
      switch (this.step) {
         case 0: {
            float[] h = this.rnd.nextBoolean() ? this.statueHead(-1) : this.statueHand(-1);
            this.jump(s, h[0], h[1], h[2], 1.0F, 6.0F);
            float[] w = this.waterSpot(-8.0F, 8.0F, 30.0F, 44.0F);
            this.jump(n, w[0], 0.0F, w[2], 0.8F, 3.0F);
            this.next();
            break;
         }
         case 1:
            if (this.bothLanded()) {
               this.pose(s, Pose.RAISE, -1.0F);
               this.darkenTarget = 0.6F;
               this.events.thunder(0.3F);
               this.counter = 0;
               this.next();
            }
            break;
         case 2:
            s.charge = Math.min(0.75F, this.stepTimer / 1.8F);
            if (this.stepTimer > this.counter * 0.22F) {
               this.counter++;
               float x = -30.0F + this.rnd.nextFloat() * 60.0F;
               float z = 50.0F + this.rnd.nextFloat() * 50.0F;
               this.bolt(x, 85.0F, z, x + (this.rnd.nextFloat() - 0.5F) * 20.0F, 70.0F, z, 0.12F, 0.7F, 0xD8E8FF);
            }

            if (!this.flag && this.stepTimer > 1.66F) {
               this.flag = true;
               this.mx = n.x;
               this.mz = n.z;
               float[] w = this.waterSpot(n.x - 14.0F, n.x + 14.0F, 24.0F, 48.0F);
               this.jump(n, w[0], 0.0F, w[2], 0.55F, 3.2F);
            }

            if (this.stepTimer > 1.8F) {
               this.bolt(this.mx + 3.0F, 92.0F, this.mz + 8.0F, this.mx, 0.0F, this.mz, 0.55F, 3.0F, 0xE8F4FF);
               this.blast(this.mx, 0.5F, this.mz, 5.0F, 1);
               this.steam(this.mx, 0.0F, this.mz, 10, 6.0F);
               this.splash(this.mx, 0.0F, this.mz, 26, 1.6F);
               this.ripple(this.mx, this.mz, 14.0F, 2.2F);
               this.ripple(this.mx, this.mz, 8.0F, 1.4F);
               this.hitFlash(0.95F, 0xDDEEFF);
               this.shake = Math.max(this.shake, 0.85F);
               this.events.thunder(1.0F);
               this.events.boom(0.6F);
               this.next();
            }
            break;
         default:
            s.charge = Math.max(0.0F, s.charge - 0.05F);
            if (this.stepTimer > 0.5F) {
               this.darkenTarget = 0.0F;
               this.unpin(s);
            }

            if (this.stepTimer > 1.2F && this.bothLanded()) {
               s.charge = 0.0F;
               this.done();
            }
      }
   }

   /** Lauf den Wasserfall hinauf mit Schlagabtausch, oben ein Zusammenprall. */
   private void wallrun(float dt) {
      Fighter n = this.naruto;
      Fighter s = this.sasuke;
      float wz = ValleyTracer.FALL_FRONT - 0.6F;
      switch (this.step) {
         case 0: {
            Fighter left = n.x < s.x ? n : s;
            Fighter right = left == n ? s : n;
            this.jump(left, -3.5F, 5.0F, wz, 0.9F, 4.0F);
            this.jump(right, 3.5F, 5.0F, wz, 0.9F, 4.0F);
            this.next();
            break;
         }
         case 1:
            if (this.bothLanded()) {
               this.counter = 0;
               this.next();
            }
            break;
         case 2: {
            float t = this.stepTimer;
            float y = 5.0F + t * 13.0F;
            float sep = 1.0F + 2.6F * Math.abs((float)Math.sin(t * 3.4F));
            Fighter left = n.x < s.x ? n : s;
            Fighter right = left == n ? s : n;
            left.x = -sep;
            right.x = sep;
            left.y = y;
            right.y = y + 0.4F * (float)Math.sin(t * 9.0F);
            left.face = 1.0F;
            right.face = -1.0F;
            if (left.poseDur <= 0.0F) {
               left.pose = Pose.RUN;
            }

            if (right.poseDur <= 0.0F) {
               right.pose = Pose.RUN;
            }

            if (this.rnd.nextFloat() < 0.5F) {
               Fighter f = this.rnd.nextBoolean() ? left : right;
               Fx e = this.add(DROP, f.x, f.y, wz - 0.2F, 0.7F, 0.14F);
               e.vx = (this.rnd.nextFloat() - 0.5F) * 2.0F;
               e.vy = 1.0F;
               e.vz = -2.0F - this.rnd.nextFloat() * 2.0F;
            }

            if (t > 0.2F + this.counter * 0.32F && sep < 1.6F) {
               this.counter++;
               this.pose(left, this.rnd.nextBoolean() ? Pose.PUNCH : Pose.KICK, 0.12F);
               this.pose(right, this.rnd.nextBoolean() ? Pose.PUNCH : Pose.KICK, 0.12F);
               this.sparks(0.0F, y + 1.1F, wz - 0.3F, 6, 0.7F);
               this.events.clash(0.45F);
            }

            if (y >= ValleyTracer.FALL_TOP - 6.0F) {
               this.mx = (this.rnd.nextFloat() - 0.5F) * 6.0F;
               this.my = ValleyTracer.FALL_TOP + 3.0F;
               this.mz = 58.0F;
               this.jump(left, this.mx - 0.55F, this.my, this.mz, 0.5F, 2.0F);
               this.jump(right, this.mx + 0.55F, this.my, this.mz, 0.5F, 2.0F);
               this.events.whoosh();
               this.next();
            }
            break;
         }
         case 3:
            if (this.bothLanded()) {
               this.pose(n, Pose.KICK, 0.25F);
               this.pose(s, Pose.PUNCH, 0.25F);
               this.sparks(this.mx, this.my + 0.9F, this.mz, 18, 1.2F);
               this.hitFlash(0.3F, 0xFFFFFF);
               this.shake = Math.max(this.shake, 0.3F);
               this.events.clash(1.0F);
               float[] a = this.anchor(n);
               float[] b = this.anchor(s);
               this.jump(n, a[0], a[1], a[2], 1.0F, 5.0F);
               this.jump(s, b[0], b[1], b[2], 1.0F, 5.0F);
               this.next();
            }
            break;
         default:
            if (this.bothLanded()) {
               this.done();
            }
      }
   }

   /** Rasengan gegen Chidori: von den Statuenköpfen, Kurama und Susanoo erscheinen, riesige Kugel in der Mitte. */
   private void finale(float dt) {
      Fighter n = this.naruto;
      Fighter s = this.sasuke;
      switch (this.step) {
         case 0: {
            float[] a = this.statueHead(1);
            float[] b = this.statueHead(-1);
            this.jump(n, a[0], a[1], a[2], 1.2F, 7.0F);
            this.jump(s, b[0], b[1], b[2], 1.2F, 7.0F);
            this.next();
            break;
         }
         case 1:
            if (this.bothLanded()) {
               n.face = -1.0F;
               s.face = 1.0F;
               this.pose(n, Pose.CHARGE, -1.0F);
               this.pose(s, Pose.CHARGE, -1.0F);
               this.counter = 0;
               this.next();
            }
            break;
         case 2: {
            float t = this.stepTimer;
            n.aura = Math.min(1.0F, t / 1.2F);
            n.charge = Math.min(1.0F, t / 3.0F);
            s.charge = Math.min(1.0F, t / 3.0F);
            if (this.effects) {
               this.kuramaTarget = 0.62F;
               this.susanooTarget = 0.62F;
            }

            if (t > this.counter * 0.45F) {
               this.counter++;
               this.events.chidori(s.charge);
               this.events.rasengan(n.charge);
            }

            if (t > 3.6F) {
               float cy = 33.0F;
               this.mx = 0.0F;
               this.my = cy;
               this.mz = ValleyTracer.STATUE_Z;
               this.jump(n, 0.55F, cy - 1.1F, this.mz, 0.55F, 2.5F);
               this.jump(s, -0.55F, cy - 1.1F, this.mz, 0.55F, 2.5F);
               this.kuramaTarget = 0.0F;
               this.susanooTarget = 0.0F;
               this.events.whoosh();
               this.next();
            }
            break;
         }
         case 3:
            if (this.bothLanded()) {
               Fx d = this.add(DOME, this.mx, this.my, this.mz, 3.4F, 15.0F);
               d.color = 0;
               Fx r = this.add(SHOCKRING, this.mx, 0.06F, this.mz - 4.0F, 2.6F, 46.0F);
               r.color = 0xFFFFFF;
               this.ripple(this.mx, this.mz - 4.0F, 30.0F, 2.0F);
               this.steam(this.mx, 0.0F, this.mz - 6.0F, 18, 24.0F);
               this.splash(this.mx, 0.0F, this.mz - 6.0F, 30, 2.2F);
               this.hitFlash(1.0F, 0xFFFFFF);
               this.shake = 1.0F;
               this.events.boom(1.0F);
               this.events.thunder(0.8F);
               n.visible = false;
               s.visible = false;
               n.aura = 0.0F;
               n.charge = 0.0F;
               s.charge = 0.0F;
               this.next();
            }
            break;
         case 4:
            if (this.stepTimer > 0.6F && this.stepTimer < 2.0F && this.rnd.nextFloat() < 0.5F) {
               float a = this.rnd.nextFloat() * 6.283F;
               float r = 12.0F + this.rnd.nextFloat() * 3.0F;
               this.bolt(this.mx, this.my, this.mz, this.mx + (float)Math.cos(a) * r, this.my + (float)Math.sin(a) * r, this.mz - 3.0F, 0.08F, 1.0F, 0xE0D0FF);
            }

            if (this.stepTimer > 1.5F) {
               n.visible = true;
               s.visible = true;
               n.x = 1.5F;
               n.y = 30.0F;
               s.x = -1.5F;
               s.y = 30.0F;
               this.pose(n, Pose.HURT, -1.0F);
               this.pose(s, Pose.HURT, -1.0F);
               this.jump(n, 14.0F, this.ground(14.0F, 47.0F), 47.0F, 1.2F, 2.0F);
               this.jump(s, -14.0F, this.ground(-14.0F, 47.0F), 47.0F, 1.2F, 2.0F);
               this.next();
            }
            break;
         case 5:
            if (this.bothLanded()) {
               this.pose(n, Pose.DOWN, -1.0F);
               this.pose(s, Pose.DOWN, -1.0F);
               this.dust(n.x, n.y, n.z, 5);
               this.dust(s.x, s.y, s.z, 5);
               this.next();
            }
            break;
         case 6:
            if (this.stepTimer > 4.0F) {
               this.pose(n, Pose.CROUCH, -1.0F);
               this.pose(s, Pose.CROUCH, -1.0F);
               this.next();
            }
            break;
         default:
            if (this.stepTimer > 0.9F) {
               this.moveCount = 0;
               this.nextFinale = 5 + this.rnd.nextInt(3);
               this.done();
            }
      }
   }
}
