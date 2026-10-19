package com.lennart.nebula.afk.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulation der Raumschlacht vor der Brücke: zwei Flotten (Meowl-Kreuzer gegen dunklen Schwarm) ziehen langsam
 * aneinander vorbei und beschießen sich, Jäger kurven dazwischen, Schiffe brennen, zerbrechen und stürzen auf den
 * Planeten, Verstärkung springt aus dem Hyperraum. Ab und zu trifft es auch das eigene Schiff (Alarm).
 * Einheiten: Minecraft-Blöcke, Kamera = {@link BridgeTracer#CAM}.
 */
public final class SpaceBattle {
   public static final byte MEOWL = 0;
   public static final byte SWARM = 1;
   private final Random rnd;
   private final ShipSprites sprites;
   public final List<Ship> ships = new ArrayList<>();
   public final List<Fighter> fighters = new ArrayList<>();
   public final List<Bolt> bolts = new ArrayList<>();
   public final List<Blast> blasts = new ArrayList<>();
   public final List<Wreck> wrecks = new ArrayList<>();
   private Events events = Events.NONE;
   private boolean battle = true;
   private boolean flyers = true;
   private boolean explosions = true;
   private boolean alarms = true;
   private float flybyTimer = 12.0F;
   private float alarmTimer = 35.0F;
   public float time;
   /** Laufender Alarm (Sekunden übrig) und Erschütterung (0..1). */
   public float alarm;
   public float shake;
   /** Lichtblitz durch die Fenster (0..1) und seine Farbe. */
   public float flash;
   public int flashColor = -21952;
   private final int fighterCount;

   public interface Events {
      Events NONE = new Events() {
      };

      /** d = Entfernung in Blöcken, size = Größe der Explosion. */
      default void explosion(float d, float size) {
      }

      default void shot(float d, byte faction, boolean capital) {
      }

      default void flyby() {
      }

      default void hullHit() {
      }

      default void jump(float d) {
      }
   }

   public static final class Ship {
      public int type;
      public byte faction;
      public float x;
      public float y;
      public float z;
      public float speed;
      public float hp;
      public float maxHp;
      public float fireCd;
      public float dying = -1.0F;
      public float arrive;
      /** Brände: Sprite-Anteile u, v (0..1) und Alter. */
      public final List<float[]> fires = new ArrayList<>();

      public boolean right() {
         return this.faction == MEOWL;
      }

      public boolean alive() {
         return this.dying < 0.0F;
      }
   }

   public static final class Fighter {
      public byte faction;
      public float x;
      public float y;
      public float z;
      public float vx;
      public float vy;
      public float vz;
      public float cd;
      public int burst;
      public Object target;
      public float respawn = -1.0F;
      public boolean flyby;
   }

   public static final class Bolt {
      public float x;
      public float y;
      public float z;
      public float dx;
      public float dy;
      public float dz;
      public float speed;
      public float life;
      public float len;
      public byte faction;
      public boolean heavy;
      public Object target;
      public boolean hits;
      public float hitT;
      public float tu;
      public float tv;
   }

   public static final class Blast {
      public float x;
      public float y;
      public float z;
      public float size;
      public float age;
      public float dur;
      public long seed;
      public boolean flak;
   }

   public static final class Wreck {
      public int type;
      public boolean right;
      public boolean front;
      public float x;
      public float y;
      public float z;
      public float vx;
      public float vy;
      public float rot;
      public float spin;
      public float age;
      public float smoke;
   }

   public SpaceBattle(ShipSprites sprites, int density, long seed) {
      this.sprites = sprites;
      this.rnd = new Random(seed);
      int caps = 2 + density;
      this.fighterCount = new int[]{8, 16, 26, 40}[Math.max(0, Math.min(3, density))];

      for (int i = 0; i < caps; i++) {
         this.ships.add(this.spawnShip(MEOWL, i, caps, true));
         this.ships.add(this.spawnShip(SWARM, i, caps, true));
      }

      for (int i = 0; i < this.fighterCount; i++) {
         this.fighters.add(this.spawnFighter((byte)(i & 1), true));
      }
   }

   public void setEvents(Events events) {
      this.events = events == null ? Events.NONE : events;
   }

   public void configure(boolean battle, boolean flyers, boolean explosions, boolean alarms) {
      this.battle = battle;
      this.flyers = flyers;
      this.explosions = explosions;
      this.alarms = alarms;
   }

   private float range(float a, float b) {
      return a + this.rnd.nextFloat() * (b - a);
   }

   // ------------------------------------------------------------------ Erzeugen

   private Ship spawnShip(byte faction, int slot, int count, boolean initial) {
      Ship s = new Ship();
      s.faction = faction;
      boolean big = slot % 2 == 0;
      s.type = faction == MEOWL ? (big ? ShipSprites.CRUISER : ShipSprites.FRIGATE_A) : (big ? ShipSprites.CARRIER : ShipSprites.FRIGATE_B);
      // eigene Flotte näher, Gegner weiter weg
      s.z = faction == MEOWL ? this.range(1500.0F, 3000.0F) : this.range(2800.0F, 5200.0F);
      if (!big) {
         s.z *= 0.75F;
      }

      float halfView = s.z * 0.78F;
      s.y = this.range(-0.1F, 0.08F) * s.z;
      s.speed = this.range(5.0F, 11.0F) * (big ? 1.0F : 1.5F);
      if (initial) {
         s.x = -halfView + (slot + this.range(0.1F, 0.9F)) / count * 2.0F * halfView;
         s.arrive = 0.0F;
      } else {
         float len = this.sprites.sprites[s.type].length;
         s.x = s.right() ? -halfView - len * 0.3F + this.range(0.0F, halfView * 0.6F) : halfView + len * 0.3F - this.range(0.0F, halfView * 0.6F);
         s.arrive = 1.0F;
         this.events.jump(s.z);
      }

      s.maxHp = big ? 260.0F : 130.0F;
      s.hp = s.maxHp;
      s.fireCd = this.range(0.5F, 3.0F);
      return s;
   }

   private Fighter spawnFighter(byte faction, boolean initial) {
      Fighter f = new Fighter();
      f.faction = faction;
      Ship home = this.randomShip(faction);
      if (home != null && !initial) {
         f.x = home.x;
         f.y = home.y;
         f.z = home.z;
      } else {
         f.x = this.range(-700.0F, 700.0F);
         f.y = this.range(-150.0F, 150.0F);
         f.z = this.range(400.0F, 2600.0F);
      }

      float a = this.rnd.nextFloat() * 6.283F;
      float sp = this.range(90.0F, 140.0F);
      f.vx = (float)Math.cos(a) * sp;
      f.vz = (float)Math.sin(a) * sp * 0.4F;
      f.vy = this.range(-10.0F, 10.0F);
      f.cd = this.range(0.5F, 2.0F);
      return f;
   }

   private Ship randomShip(byte faction) {
      List<Ship> list = new ArrayList<>();

      for (Ship s : this.ships) {
         if (s.faction == faction && s.alive() && s.arrive <= 0.0F) {
            list.add(s);
         }
      }

      return list.isEmpty() ? null : list.get(this.rnd.nextInt(list.size()));
   }

   // ------------------------------------------------------------------ Schritt

   public void prewarm(float seconds) {
      Events saved = this.events;
      this.events = Events.NONE;

      for (float t = 0.0F; t < seconds; t += 0.05F) {
         this.update(0.05F);
      }

      this.events = saved;
      this.flash = 0.0F;
      this.shake = 0.0F;
      this.alarm = 0.0F;
   }

   public void update(float dt) {
      this.time += dt;
      this.flash = Math.max(0.0F, this.flash - dt * 2.2F);
      this.shake = Math.max(0.0F, this.shake - dt * 1.3F);
      this.alarm = Math.max(0.0F, this.alarm - dt);
      this.updateShips(dt);
      this.updateFighters(dt);
      this.updateBolts(dt);

      for (int i = this.blasts.size() - 1; i >= 0; i--) {
         Blast b = this.blasts.get(i);
         b.age += dt;
         if (b.age >= b.dur) {
            this.blasts.remove(i);
         }
      }

      for (int i = this.wrecks.size() - 1; i >= 0; i--) {
         Wreck w = this.wrecks.get(i);
         w.age += dt;
         w.x += w.vx * dt;
         w.y += w.vy * dt;
         w.vy -= 3.0F * dt;
         w.rot += w.spin * dt;
         w.smoke -= dt;
         if (w.smoke <= 0.0F && this.explosions && w.age < 14.0F) {
            w.smoke = this.range(0.15F, 0.6F);
            float len = this.sprites.sprites[w.type].length * 0.5F;
            this.blast(w.x + this.range(-0.4F, 0.4F) * len, w.y, w.z - 5.0F, this.range(8.0F, 22.0F), false, w.age < 4.0F);
         }

         if (w.age > 22.0F) {
            this.wrecks.remove(i);
         }
      }

      // gelegentlicher Treffer am eigenen Schiff
      if (this.alarms && this.battle) {
         this.alarmTimer -= dt;
         if (this.alarmTimer <= 0.0F) {
            this.alarmTimer = this.range(30.0F, 70.0F);
            this.hitOwnShip();
         }
      }

      if (this.flyers && this.battle) {
         this.flybyTimer -= dt;
         if (this.flybyTimer <= 0.0F) {
            this.flybyTimer = this.range(14.0F, 32.0F);
            this.flyby();
         }
      }
   }

   private void updateShips(float dt) {
      for (int i = 0; i < this.ships.size(); i++) {
         Ship s = this.ships.get(i);
         s.arrive = Math.max(0.0F, s.arrive - dt * 1.4F);
         if (s.alive()) {
            s.x += (s.right() ? s.speed : -s.speed) * dt;
         } else {
            s.dying += dt;
            s.x += (s.right() ? s.speed : -s.speed) * dt * 0.5F;
            if (this.explosions && this.rnd.nextFloat() < dt * 7.0F) {
               float len = this.sprites.sprites[s.type].length;
               this.blast(s.x + this.range(-0.45F, 0.45F) * len, s.y + this.range(-0.05F, 0.08F) * len, s.z - 4.0F, this.range(10.0F, 30.0F), false, true);
               s.fires.add(new float[]{this.range(0.1F, 0.9F), this.range(0.3F, 0.7F), 0.0F});
            }

            if (s.dying > 3.6F) {
               this.breakApart(s);
               this.ships.set(i, this.spawnShip(s.faction, this.rnd.nextInt(4), 4, false));
               continue;
            }
         }

         for (float[] f : s.fires) {
            f[2] += dt;
         }

         s.fires.removeIf(f -> f[2] > 40.0F);
         float halfView = s.z * 0.8F + this.sprites.sprites[s.type].length * 0.6F;
         if (s.right() && s.x > halfView || !s.right() && s.x < -halfView) {
            // aus dem Bild gezogen: neues Schiff springt auf der anderen Seite ein
            this.ships.set(i, this.spawnShip(s.faction, this.rnd.nextInt(4), 4, false));
            continue;
         }

         if (this.battle && s.alive() && s.arrive <= 0.0F) {
            s.fireCd -= dt;
            if (s.fireCd <= 0.0F) {
               s.fireCd = this.range(0.35F, 2.2F);
               this.fireCapital(s);
            }
         }
      }
   }

   /** Weltposition eines Sprite-Punkts (Pixel im Sprite) eines Schiffs. */
   public float[] spritePoint(Ship s, float px, float py) {
      ShipSprites.Sprite sp = this.sprites.sprites[s.type];
      float u = px / sp.image.w - 0.5F;
      float v = py / sp.image.h - 0.5F;
      float wx = u * sp.image.w / sp.pxPerBlock * (s.right() ? 1 : -1);
      float wy = -v * sp.image.h / sp.pxPerBlock;
      return new float[]{s.x + wx, s.y + wy, s.z - 2.0F};
   }

   private void fireCapital(Ship s) {
      ShipSprites.Sprite sp = this.sprites.sprites[s.type];
      if (!sp.guns.isEmpty()) {
         Ship target = this.randomShip((byte)(1 - s.faction));
         if (target != null) {
            float[] g = sp.guns.get(this.rnd.nextInt(sp.guns.size()));
            float[] from = this.spritePoint(s, g[0], g[1]);
            ShipSprites.Sprite ts = this.sprites.sprites[target.type];
            float tu = this.range(0.2F, 0.8F);
            float tv = this.range(0.35F, 0.65F);
            float[] to = this.spritePoint(target, tu * ts.image.w, tv * ts.image.h);
            // zielt auf die künftige Position
            float dist = dist(from, to);
            float travel = dist / 1500.0F;
            to[0] += (target.right() ? target.speed : -target.speed) * travel;
            int shots = 1 + this.rnd.nextInt(3);

            for (int k = 0; k < shots; k++) {
               Bolt b = new Bolt();
               b.faction = s.faction;
               b.heavy = true;
               b.speed = 1500.0F;
               b.len = 120.0F;
               b.x = from[0];
               b.y = from[1];
               b.z = from[2];
               float spread = k * 6.0F;
               aim(b, to[0] + spread, to[1] + spread * 0.2F, to[2]);
               b.life = travel + 0.5F + k * 0.12F;
               b.target = target;
               b.hits = this.rnd.nextFloat() < 0.72F;
               b.hitT = travel;
               b.tu = tu;
               b.tv = tv;
               // Salve leicht versetzt
               b.x -= b.dx * spread * 4.0F;
               b.y -= b.dy * spread * 4.0F;
               b.z -= b.dz * spread * 4.0F;
               b.hitT += spread * 4.0F / b.speed;
               this.bolts.add(b);
            }

            this.events.shot(from[2], s.faction, true);
         }
      }
   }

   private static void aim(Bolt b, float tx, float ty, float tz) {
      float dx = tx - b.x;
      float dy = ty - b.y;
      float dz = tz - b.z;
      float l = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      b.dx = dx / l;
      b.dy = dy / l;
      b.dz = dz / l;
   }

   private static float dist(float[] a, float[] b) {
      float dx = a[0] - b[0];
      float dy = a[1] - b[1];
      float dz = a[2] - b[2];
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private void updateFighters(float dt) {
      for (int i = this.fighters.size() - 1; i >= 0; i--) {
         Fighter f = this.fighters.get(i);
         if (f.respawn >= 0.0F) {
            f.respawn -= dt;
            if (f.respawn < 0.0F) {
               this.fighters.set(i, this.spawnFighter(f.faction, false));
            }
         } else if (f.flyby) {
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.z += f.vz * dt;
            f.cd -= dt;
            if (f.cd <= 0.0F && f.faction == MEOWL) {
               f.cd = 0.18F;
               this.fighterShot(f, f.vx, f.vy, f.vz, null);
            }

            if (Math.abs(f.x) > 900.0F) {
               this.fighters.remove(i);
            }
         } else {
            if (!this.flyers) {
               continue;
            }

            if (f.target == null || this.rnd.nextFloat() < dt * 0.15F || f.target instanceof Fighter t && t.respawn >= 0.0F) {
               f.target = this.pickTarget(f);
            }

            float tx;
            float ty;
            float tz;
            if (f.target instanceof Fighter t) {
               tx = t.x;
               ty = t.y;
               tz = t.z;
            } else if (f.target instanceof Ship s) {
               tx = s.x + (float)Math.sin(this.time * 0.3F + i) * 200.0F;
               ty = s.y + 40.0F;
               tz = s.z - 60.0F;
            } else {
               tx = 0.0F;
               ty = 0.0F;
               tz = 1000.0F;
            }

            // in der Bühne halten
            if (f.z < 250.0F) {
               tz = 900.0F;
            }

            float dx = tx - f.x;
            float dy = ty - f.y;
            float dz = tz - f.z;
            float dl = (float)Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.001F;
            float sp = (float)Math.sqrt(f.vx * f.vx + f.vy * f.vy + f.vz * f.vz) + 0.001F;
            float turn = Math.min(1.0F, dt * 1.7F);
            f.vx += (dx / dl * sp - f.vx) * turn;
            f.vy += (dy / dl * sp - f.vy) * turn;
            f.vz += (dz / dl * sp - f.vz) * turn;
            float nl = (float)Math.sqrt(f.vx * f.vx + f.vy * f.vy + f.vz * f.vz) + 0.001F;
            f.vx = f.vx / nl * sp;
            f.vy = f.vy / nl * sp;
            f.vz = f.vz / nl * sp;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.z += f.vz * dt;
            f.cd -= dt;
            float align = (f.vx * dx + f.vy * dy + f.vz * dz) / (sp * dl);
            if (this.battle && f.cd <= 0.0F && align > 0.93F && dl < 520.0F) {
               if (f.burst <= 0) {
                  f.burst = 3 + this.rnd.nextInt(3);
               }

               f.burst--;
               f.cd = f.burst > 0 ? 0.11F : this.range(0.8F, 2.2F);
               this.fighterShot(f, f.vx, f.vy, f.vz, f.target);
            }
         }
      }

      if (this.flyers) {
         int active = 0;

         for (Fighter f : this.fighters) {
            if (!f.flyby) {
               active++;
            }
         }

         while (active < this.fighterCount) {
            this.fighters.add(this.spawnFighter((byte)(active & 1), false));
            active++;
         }
      }
   }

   private Object pickTarget(Fighter f) {
      List<Fighter> enemies = new ArrayList<>();

      for (Fighter o : this.fighters) {
         if (o.faction != f.faction && o.respawn < 0.0F && !o.flyby) {
            enemies.add(o);
         }
      }

      if (!enemies.isEmpty() && this.rnd.nextFloat() < 0.75F) {
         return enemies.get(this.rnd.nextInt(enemies.size()));
      } else {
         return this.randomShip((byte)(1 - f.faction));
      }
   }

   private void fighterShot(Fighter f, float vx, float vy, float vz, Object target) {
      Bolt b = new Bolt();
      b.faction = f.faction;
      b.speed = 700.0F;
      b.len = 14.0F;
      b.x = f.x;
      b.y = f.y;
      b.z = f.z;
      float l = (float)Math.sqrt(vx * vx + vy * vy + vz * vz) + 0.001F;
      b.dx = vx / l;
      b.dy = vy / l;
      b.dz = vz / l;
      b.life = 0.9F;
      b.target = target;
      if (target instanceof Fighter t) {
         float d = (float)Math.sqrt((t.x - f.x) * (t.x - f.x) + (t.y - f.y) * (t.y - f.y) + (t.z - f.z) * (t.z - f.z));
         b.hitT = d / b.speed;
         b.hits = this.rnd.nextFloat() < 0.16F;
      }

      this.bolts.add(b);
      if (this.rnd.nextFloat() < 0.3F) {
         this.events.shot(f.z, f.faction, false);
      }
   }

   private void updateBolts(float dt) {
      for (int i = this.bolts.size() - 1; i >= 0; i--) {
         Bolt b = this.bolts.get(i);
         b.x += b.dx * b.speed * dt;
         b.y += b.dy * b.speed * dt;
         b.z += b.dz * b.speed * dt;
         b.life -= dt;
         b.hitT -= dt;
         if (b.hits && b.hitT <= 0.0F) {
            b.hits = false;
            this.impact(b);
            this.bolts.remove(i);
         } else if (b.life <= 0.0F) {
            this.bolts.remove(i);
         }
      }
   }

   private void impact(Bolt b) {
      if (b.target instanceof Ship s && s.alive()) {
         ShipSprites.Sprite sp = this.sprites.sprites[s.type];
         float[] p = this.spritePoint(s, b.tu * sp.image.w, b.tv * sp.image.h);
         if (this.explosions) {
            this.blast(p[0], p[1], p[2] - 3.0F, this.range(6.0F, 14.0F), false, true);
         }

         s.hp -= this.range(2.0F, 5.0F);
         if (this.rnd.nextFloat() < 0.18F && s.fires.size() < 7) {
            s.fires.add(new float[]{b.tu, b.tv, 0.0F});
         }

         if (s.hp <= 0.0F) {
            s.dying = 0.0F;
         }
      } else if (b.target instanceof Fighter f && f.respawn < 0.0F && !f.flyby) {
         if (this.explosions) {
            this.blast(f.x, f.y, f.z, this.range(5.0F, 9.0F), false, true);
         }

         f.respawn = this.range(3.0F, 7.0F);
      }
   }

   private void breakApart(Ship s) {
      if (this.explosions) {
         this.blast(s.x, s.y, s.z - 6.0F, this.sprites.sprites[s.type].length * 0.35F, false, true);
      }

      for (int k = 0; k < 2; k++) {
         Wreck w = new Wreck();
         w.type = s.type;
         w.right = s.right();
         w.front = k == 1;
         float len = this.sprites.sprites[s.type].length;
         float dir = (s.right() ? 1 : -1) * (w.front ? 1 : -1);
         w.x = s.x + dir * len * 0.25F;
         w.y = s.y;
         w.z = s.z;
         w.vx = (s.right() ? s.speed : -s.speed) * 0.4F + dir * this.range(4.0F, 9.0F);
         w.vy = -this.range(2.0F, 6.0F);
         w.spin = dir * this.range(0.04F, 0.1F) * (s.right() ? 1 : -1);
         w.smoke = 0.1F;
         this.wrecks.add(w);
      }

      if (s.faction == MEOWL) {
         this.flash = Math.max(this.flash, 0.5F);
      }
   }

   public void blast(float x, float y, float z, float size, boolean flak, boolean sound) {
      Blast b = new Blast();
      b.x = x;
      b.y = y;
      b.z = z;
      b.size = size;
      b.dur = flak ? 0.5F : 0.9F + size / 40.0F;
      b.seed = this.rnd.nextLong();
      b.flak = flak;
      this.blasts.add(b);
      // Lichtblitz auf der Brücke, abhängig von Größe und Nähe
      float strength = size / Math.max(60.0F, z) * 9.0F;
      if (strength > 0.05F) {
         this.flash = Math.min(1.0F, this.flash + strength);
         this.flashColor = -21952;
      }

      if (sound) {
         this.events.explosion(z, size);
      }
   }

   private void flyby() {
      float side = this.rnd.nextBoolean() ? 1.0F : -1.0F;
      float z = this.range(40.0F, 90.0F);
      float y = this.range(-6.0F, 14.0F);
      float speed = this.range(170.0F, 240.0F);

      for (int k = 0; k < 2; k++) {
         Fighter f = new Fighter();
         f.flyby = true;
         f.faction = (byte)(k == 0 ? SWARM : MEOWL);
         f.x = -side * (420.0F + k * 70.0F);
         f.y = y + k * 3.0F;
         f.z = z + k * 12.0F;
         f.vx = side * speed;
         f.vy = this.range(-2.0F, 2.0F);
         f.vz = this.range(-6.0F, 6.0F);
         f.cd = 0.4F;
         this.fighters.add(f);
      }

      this.events.flyby();
   }

   private void hitOwnShip() {
      this.alarm = 6.0F;
      this.shake = 1.0F;
      this.flash = 1.0F;
      this.flashColor = -26816;
      if (this.explosions) {
         this.blast(this.range(-14.0F, 14.0F), this.range(-2.0F, 8.0F), this.range(30.0F, 45.0F), this.range(9.0F, 14.0F), false, false);
      }

      this.events.hullHit();
   }
}
