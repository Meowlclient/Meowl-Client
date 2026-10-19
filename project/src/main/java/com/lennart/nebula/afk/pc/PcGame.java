package com.lennart.nebula.afk.pc;

import com.lennart.nebula.afk.space.Tex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kleiner Taktik-Shooter aus Minecraft-Blöcken für den mittleren Bildschirm: eine Wüstenkarte (Sandstein,
 * Terrakotta, Kisten), Gegner mit echten Minecraft-Skins, die laufen, zielen, schießen und umfallen. Deine Figur läuft
 * über die Karte, sucht Gegner, zielt und schießt. Gerendert wird per Raycasting (Voxel + gedrehte Körperteile).
 */
public final class PcGame {
   public static final int W = 256;
   public static final int H = 144;
   private static final float FOV = 72.0F;
   private static final float EYE = 1.62F;
   private static final String[] MAP = {
      "################################",
      "#......#........#.......#......#",
      "#..B...#...bb...#...B...#..b...#",
      "#......D........D.......D......#",
      "#..b...#........#.......#......#",
      "####D###...TT...###D#####..B...#",
      "#..........TT..................#",
      "#...B......................bb..#",
      "#.....#####D######.....#####...#",
      "#.....#..........#.....#...#...#",
      "#..b..#...B..b...#..B..#...D...#",
      "#.....D..........D.....#...#...#",
      "#.....#..........#.....##D##...#",
      "#.....####D#######.............#",
      "#...................B..........#",
      "#..bb.......TT.........TT......#",
      "#...........TT.........TT...B..#",
      "#....B.........................#",
      "####D#####......#####D######...#",
      "#........#......#..........#...#",
      "#...b....D..B...D....bb....D...#",
      "#........#......#..........#...#",
      "#..B.....##D#####.......B..#...#",
      "#..........................##D##",
      "#......bb.......B..............#",
      "#..TT.................TT.......#",
      "#..TT.......#####D####TT...b...#",
      "#...........#........#.........#",
      "#....B......D...bb...D.....B...#",
      "#...........#........#.........#",
      "#......b....##########.........#",
      "################################"
   };
   /** Felder der Karte (je 2 x 2 Blöcke) und Weltgröße in Blöcken. */
   private static final int NC = 32;
   private static final int CELL = 2;
   private static final int MW = NC * CELL;
   private static final int MH = 9;
   private static final String[] SKINS = {
      "entity/player/wide/steve", "entity/player/slim/alex", "entity/player/wide/ari", "entity/player/wide/kai", "entity/player/slim/noor",
      "entity/player/wide/sunny", "entity/player/slim/zuri", "entity/player/wide/efe", "entity/player/slim/makena"
   };
   private static final String[] NAMES = {"Steve", "Alex", "Ari", "Kai", "Noor", "Sunny", "Zuri", "Efe", "Makena"};

   /** Texturen, die das Spiel braucht. */
   public static List<String> textures() {
      List<String> l = new ArrayList<>();

      for (String b : new String[]{
         "sandstone_top", "cut_sandstone", "chiseled_sandstone", "orange_terracotta", "white_terracotta", "spruce_planks", "barrel_side", "stone_bricks",
         "terracotta", "black_concrete", "iron_block", "gray_concrete"
      }) {
         l.add("block/" + b);
      }

      for (String s : SKINS) {
         l.add(s);
      }

      return l;
   }

   private final byte[] vox = new byte[MW * MH * MW];
   private final Tex[] mats = new Tex[16];
   private final Tex[] skins = new Tex[SKINS.length];
   private final boolean[] slim = new boolean[SKINS.length];
   private final Random rnd = new Random();
   // Spieler (du)
   private float px = 7.0F;
   private float pz = 7.0F;
   private float yaw;
   private float pitch;
   private float walk;
   private boolean moving;
   private final ArrayDeque<int[]> path = new ArrayDeque<>();
   public int health = 100;
   public int ammo = 25;
   public int kills;
   public int deaths;
   public int scoreUs;
   public int scoreThem;
   public float roundTime = 100.0F;
   private float fireCd;
   public float lastShot = -9.0F;
   public float lastHit = -9.0F;
   public float lastHurt = -9.0F;
   public float lastKill = -9.0F;
   public float deadUntil = -1.0F;
   public float reloadUntil = -1.0F;
   public String feed = "";
   public boolean headshot;
   private float recoil;
   private float time;
   /** Letzter Schuss: Ziel im Bild (für Leuchtspur), -1 = keins. */
   public float tracerX = -1.0F;
   public float tracerY = -1.0F;
   private Enemy target;
   private final List<Enemy> enemies = new ArrayList<>();
   private float spawnCd = 1.0F;
   private volatile Snapshot last;

   public PcGame(Map<String, Tex> tex) {
      String[] names = {
         null, "sandstone_top", "cut_sandstone", "chiseled_sandstone", "orange_terracotta", "white_terracotta", "spruce_planks", "barrel_side", "stone_bricks",
         "terracotta", "black_concrete", "iron_block"
      };

      for (int i = 1; i < names.length; i++) {
         Tex t = tex.get("block/" + names[i]);
         this.mats[i] = t != null ? t.firstFrame() : Tex.solid(0xFFC8A878);
      }

      for (int i = 0; i < SKINS.length; i++) {
         this.skins[i] = tex.get(SKINS[i]);
         this.slim[i] = SKINS[i].contains("/slim/");
      }

      this.buildMap();
      this.respawn();
   }

   // ================================================================== Karte

   private void set(int x, int y, int z, int m) {
      if (x >= 0 && y >= 0 && z >= 0 && x < MW && y < MH && z < MW) {
         this.vox[(y * MW + z) * MW + x] = (byte)m;
      }
   }

   private int get(int x, int y, int z) {
      return x >= 0 && y >= 0 && z >= 0 && x < MW && y < MH && z < MW ? this.vox[(y * MW + z) * MW + x] : (y < 0 ? 1 : 0);
   }

   private void buildMap() {
      for (int z = 0; z < MW; z++) {
         for (int x = 0; x < MW; x++) {
            // Boden ist die Ebene unter y = 0 (siehe castVoxel)
            char c = MAP[z / CELL].charAt(x / CELL);
            switch (c) {
               case '#':
                  for (int y = 0; y <= 4; y++) {
                     this.set(x, y, z, y == 0 ? 8 : (y == 2 ? 4 : (y == 4 ? 3 : 2)));
                  }
                  break;
               case 'D':
                  this.set(x, 3, z, 3);
                  this.set(x, 4, z, 2);
                  break;
               case 'T':
                  for (int y = 0; y <= 6; y++) {
                     this.set(x, y, z, y == 6 ? 4 : 5);
                  }
                  break;
               case 'B':
                  this.set(x, 0, z, 6);
                  break;
               case 'b':
                  this.set(x, 0, z, 7);
                  this.set(x, 1, z, 6);
                  break;
               default:
            }
         }
      }
   }

   /** Begehbares Feld (Feldkoordinaten). */
   private boolean walkable(int x, int z) {
      if (x <= 0 || z <= 0 || x >= NC - 1 || z >= NC - 1) {
         return false;
      } else {
         char c = MAP[z].charAt(x);
         return c == '.' || c == 'D';
      }
   }

   private static float center(int cell) {
      return cell * CELL + CELL / 2.0F;
   }

   private int[] randomOpen() {
      for (int i = 0; i < 200; i++) {
         int x = 1 + this.rnd.nextInt(NC - 2);
         int z = 1 + this.rnd.nextInt(NC - 2);
         if (this.walkable(x, z)) {
            return new int[]{x, z};
         }
      }

      return new int[]{3, 3};
   }

   /** Kürzester Weg (Breitensuche) von a nach b über freie Felder. */
   private void route(float fx, float fz, int[] goal, ArrayDeque<int[]> out) {
      out.clear();
      int sx = (int)(fx / CELL);
      int sz = (int)(fz / CELL);
      int[] prev = new int[NC * NC];
      java.util.Arrays.fill(prev, -2);
      ArrayDeque<Integer> q = new ArrayDeque<>();
      q.add(sz * NC + sx);
      prev[sz * NC + sx] = -1;
      int target = goal[1] * NC + goal[0];

      while (!q.isEmpty()) {
         int cur = q.poll();
         if (cur == target) {
            break;
         }

         int cx = cur % NC;
         int cz = cur / NC;

         for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = cx + d[0];
            int nz = cz + d[1];
            if (this.walkable(nx, nz) && prev[nz * NC + nx] == -2) {
               prev[nz * NC + nx] = cur;
               q.add(nz * NC + nx);
            }
         }
      }

      if (prev[target] == -2) {
         return;
      }

      for (int c = target; c != -1; c = prev[c]) {
         out.addFirst(new int[]{c % NC, c / NC});
      }
   }

   /** Sichtlinie durch die Voxel (Augenhöhe). */
   private boolean sees(float ax, float ay, float az, float bx, float by, float bz) {
      float dx = bx - ax;
      float dy = by - ay;
      float dz = bz - az;
      float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      int steps = (int)(len * 4.0F);

      for (int i = 1; i < steps; i++) {
         float k = (float)i / steps;
         if (this.get((int)Math.floor(ax + dx * k), (int)Math.floor(ay + dy * k), (int)Math.floor(az + dz * k)) != 0) {
            return false;
         }
      }

      return true;
   }

   // ================================================================== Gegner

   private static final class Enemy {
      float x;
      float z;
      float yaw;
      float walk;
      boolean moving;
      int skin;
      String name;
      int hp = 100;
      float deadAt = -1.0F;
      float aim;
      float fireCd;
      float lastShot = -9.0F;
      final ArrayDeque<int[]> path = new ArrayDeque<>();
   }

   private void spawnEnemy() {
      Enemy e = new Enemy();
      int[] p;
      int tries = 0;

      do {
         p = this.randomOpen();
      } while (tries++ < 50 && Math.hypot(center(p[0]) - this.px, center(p[1]) - this.pz) < 18.0);

      e.x = center(p[0]);
      e.z = center(p[1]);
      e.skin = this.rnd.nextInt(SKINS.length);
      e.name = NAMES[e.skin];
      this.enemies.add(e);
   }

   private void respawn() {
      int[] p = this.randomOpen();
      this.px = center(p[0]);
      this.pz = center(p[1]);
      this.health = 100;
      this.ammo = 25;
      this.path.clear();
   }

   // ================================================================== Ablauf

   private static float wrap(float a) {
      while (a > 180.0F) {
         a -= 360.0F;
      }

      while (a < -180.0F) {
         a += 360.0F;
      }

      return a;
   }

   /** Richtung (Grad, 0 = +z, positiv nach +x) von (ax, az) nach (bx, bz). */
   private static float heading(float ax, float az, float bx, float bz) {
      return (float)Math.toDegrees(Math.atan2(bx - ax, bz - az));
   }

   public void update(float dt) {
      dt = Math.min(dt, 0.05F);
      this.time += dt;
      this.roundTime -= dt;
      if (this.roundTime <= 0.0F) {
         this.roundTime = 100.0F;
         this.scoreThem++;
      }

      this.recoil = Math.max(0.0F, this.recoil - dt * 6.0F);
      this.fireCd -= dt;
      this.spawnCd -= dt;
      long alive = this.enemies.stream().filter(en -> en.deadAt < 0.0F).count();
      if (this.spawnCd <= 0.0F && alive < 3) {
         this.spawnEnemy();
         this.spawnCd = 2.5F + this.rnd.nextFloat() * 3.0F;
      }

      this.enemies.removeIf(en -> en.deadAt >= 0.0F && this.time - en.deadAt > 3.5F);
      if (this.deadUntil > 0.0F) {
         if (this.time > this.deadUntil) {
            this.deadUntil = -1.0F;
            this.respawn();
         }

         this.moving = false;
         return;
      }

      if (this.reloadUntil > 0.0F && this.time > this.reloadUntil) {
         this.reloadUntil = -1.0F;
         this.ammo = 25;
      }

      // Ziel suchen: sichtbarer Gegner vor dir
      Enemy best = null;
      float bestD = 22.0F;

      for (Enemy e : this.enemies) {
         if (e.deadAt < 0.0F) {
            float d = (float)Math.hypot(e.x - this.px, e.z - this.pz);
            float ang = Math.abs(wrap(heading(this.px, this.pz, e.x, e.z) - this.yaw));
            if (d < bestD && (ang < 70.0F || e == this.target) && this.sees(this.px, EYE, this.pz, e.x, 1.5F, e.z)) {
               best = e;
               bestD = d;
            }
         }
      }

      this.target = best;
      if (best != null) {
         // zielen: Kopf anvisieren, dann feuern
         float want = heading(this.px, this.pz, best.x, best.z);
         float dyaw = wrap(want - this.yaw);
         this.yaw += dyaw * Math.min(1.0F, dt * 9.0F);
         float wantPitch = (float)Math.toDegrees(Math.atan2(1.55F - EYE, bestD));
         this.pitch += (wantPitch - this.pitch) * Math.min(1.0F, dt * 8.0F);
         this.moving = Math.abs(dyaw) > 25.0F;
         if (Math.abs(dyaw) < 4.0F && this.fireCd <= 0.0F && this.reloadUntil < 0.0F) {
            this.shoot(best, bestD);
         }
      } else {
         // weiterlaufen
         this.pitch += (0.0F - this.pitch) * Math.min(1.0F, dt * 4.0F);
         if (this.path.isEmpty()) {
            this.route(this.px, this.pz, this.randomOpen(), this.path);
         }

         this.moving = !this.path.isEmpty();
         if (this.moving) {
            int[] next = this.path.peekFirst();
            float tx = center(next[0]);
            float tz = center(next[1]);
            float d = (float)Math.hypot(tx - this.px, tz - this.pz);
            if (d < 0.25F) {
               this.path.pollFirst();
            } else {
               float want = heading(this.px, this.pz, tx, tz);
               this.yaw += wrap(want - this.yaw) * Math.min(1.0F, dt * 5.0F);
               float sp = 4.2F * dt;
               this.px += (tx - this.px) / d * Math.min(sp, d);
               this.pz += (tz - this.pz) / d * Math.min(sp, d);
            }
         }
      }

      if (this.moving) {
         this.walk += dt * 9.0F;
      }

      // Gegner bewegen und zurückschießen
      for (Enemy e : this.enemies) {
         if (e.deadAt >= 0.0F) {
            continue;
         }

         float d = (float)Math.hypot(this.px - e.x, this.pz - e.z);
         boolean see = d < 22.0F && this.sees(e.x, 1.5F, e.z, this.px, EYE, this.pz);
         e.fireCd -= dt;
         if (see) {
            float want = heading(e.x, e.z, this.px, this.pz);
            e.yaw += wrap(want - e.yaw) * Math.min(1.0F, dt * 6.0F);
            e.aim = Math.min(1.0F, e.aim + dt * 3.0F);
            e.moving = false;
            if (e.aim > 0.9F && e.fireCd <= 0.0F) {
               e.fireCd = 0.18F + this.rnd.nextFloat() * 0.5F;
               e.lastShot = this.time;
               if (this.rnd.nextFloat() < 0.1F) {
                  this.health -= 18 + this.rnd.nextInt(10);
                  this.lastHurt = this.time;
                  if (this.health <= 0) {
                     this.health = 0;
                     this.deaths++;
                     this.deadUntil = this.time + 3.0F;
                     this.feed = e.name + "  ➜  Du";
                     this.headshot = false;
                     this.lastKill = this.time;
                  }
               }
            }
         } else {
            e.aim = Math.max(0.0F, e.aim - dt * 2.0F);
            if (e.path.isEmpty()) {
               this.route(e.x, e.z, this.rnd.nextFloat() < 0.5F ? new int[]{(int)(this.px / CELL), (int)(this.pz / CELL)} : this.randomOpen(), e.path);
            }

            e.moving = !e.path.isEmpty();
            if (e.moving) {
               int[] next = e.path.peekFirst();
               float tx = center(next[0]);
               float tz = center(next[1]);
               float dd = (float)Math.hypot(tx - e.x, tz - e.z);
               if (dd < 0.25F) {
                  e.path.pollFirst();
               } else {
                  e.yaw += wrap(heading(e.x, e.z, tx, tz) - e.yaw) * Math.min(1.0F, dt * 6.0F);
                  float sp = 3.6F * dt;
                  e.x += (tx - e.x) / dd * Math.min(sp, dd);
                  e.z += (tz - e.z) / dd * Math.min(sp, dd);
               }
            }
         }

         if (e.moving) {
            e.walk += dt * 9.0F;
         }
      }
   }

   private void shoot(Enemy e, float dist) {
      this.fireCd = 0.11F;
      this.lastShot = this.time;
      this.recoil = Math.min(3.0F, this.recoil + 0.9F);
      this.ammo--;
      if (this.ammo <= 0) {
         this.reloadUntil = this.time + 2.0F;
      }

      float chance = Math.max(0.25F, 0.75F - dist * 0.02F);
      if (this.rnd.nextFloat() < chance) {
         boolean head = this.rnd.nextFloat() < 0.3F;
         e.hp -= head ? 150 : 32;
         this.lastHit = this.time;
         if (e.hp <= 0) {
            e.deadAt = this.time;
            this.kills++;
            this.lastKill = this.time;
            this.headshot = head;
            this.feed = "Du  ➜  " + e.name;
            if (this.kills % 5 == 0) {
               this.scoreUs++;
               this.roundTime = 100.0F;
            }
         }
      }
   }

   public float time() {
      return this.time;
   }

   public boolean moving() {
      return this.moving;
   }

   public float walkPhase() {
      return this.walk;
   }

   public float recoil() {
      return this.recoil;
   }

   // ================================================================== Momentaufnahme fürs Rendern

   /** Unveränderliche Lage für den Render-Thread. */
   public static final class Snapshot {
      float cx;
      float cy;
      float cz;
      float yaw;
      float pitch;
      final List<Part> parts = new ArrayList<>();
      final List<float[]> bounds = new ArrayList<>();
      final List<float[]> flashes = new ArrayList<>();
      // HUD
      int health;
      int ammo;
      int scoreUs;
      int scoreThem;
      float roundTime;
      float time;
      float lastShot;
      float lastHit;
      float lastHurt;
      float lastKill;
      boolean dead;
      boolean reloading;
      float reloadLeft;
      boolean moving;
      float walk;
      boolean headshot;
      String feed = "";
      float px;
      float pz;
      final List<float[]> dots = new ArrayList<>();
   }

   /** Gedrehter Körperteil: Welt→lokal, Maße in Skin-Pixeln, Skin-UV. */
   static final class Part {
      final Matrix4f inv = new Matrix4f();
      float w;
      float h;
      float d;
      int u;
      int v;
      Tex skin;
      int enemy;
      boolean gun;
   }

   public Snapshot snapshot() {
      Snapshot s = new Snapshot();
      float bob = this.moving ? (float)Math.sin(this.walk) * 0.04F : 0.0F;
      s.cx = this.px;
      s.cy = EYE + bob + (this.deadUntil > 0.0F ? -1.1F : 0.0F);
      s.cz = this.pz;
      s.yaw = this.yaw + (float)Math.sin(this.time * 0.7F) * 0.6F;
      s.pitch = this.pitch + this.recoil * 1.2F;
      s.health = this.health;
      s.ammo = this.ammo;
      s.scoreUs = this.scoreUs;
      s.scoreThem = this.scoreThem;
      s.roundTime = this.roundTime;
      s.time = this.time;
      s.lastShot = this.lastShot;
      s.lastHit = this.lastHit;
      s.lastHurt = this.lastHurt;
      s.lastKill = this.lastKill;
      s.dead = this.deadUntil > 0.0F;
      s.reloading = this.reloadUntil > 0.0F;
      s.reloadLeft = this.reloadUntil - this.time;
      s.moving = this.moving;
      s.walk = this.walk;
      s.headshot = this.headshot;
      s.feed = this.feed;
      s.px = this.px;
      s.pz = this.pz;
      s.dots.addAll(this.enemyDots());

      for (int i = 0; i < this.enemies.size(); i++) {
         Enemy e = this.enemies.get(i);
         this.parts(e, i, s);
         if (e.deadAt < 0.0F && this.time - e.lastShot < 0.06F) {
            double a = Math.toRadians(e.yaw);
            s.flashes.add(new float[]{e.x + (float)Math.sin(a) * 0.7F - (float)Math.cos(a) * 0.3F, 1.35F, e.z + (float)Math.cos(a) * 0.7F + (float)Math.sin(a) * 0.3F});
         }
      }

      this.last = s;
      return s;
   }

   private void parts(Enemy e, int id, Snapshot s) {
      Tex skin = this.skins[e.skin];
      if (skin == null) {
         return;
      }

      boolean sl = this.slim[e.skin];
      float swing = e.moving ? (float)Math.sin(e.walk) * 0.8F : 0.0F;
      float fall = e.deadAt >= 0.0F ? Math.min(1.0F, (this.time - e.deadAt) * 2.5F) * 1.5F : 0.0F;
      float aim = e.aim;
      // Grundmatrix: Position, Blickrichtung (Gesicht zeigt in Laufrichtung), Umfallen, Skin-Pixel → Blöcke
      Matrix4f base = new Matrix4f().translate(e.x, 0.0F, e.z).rotateY((float)Math.toRadians(e.yaw + 180.0F)).rotateX(fall).scale(0.9F / 16.0F);
      s.bounds.add(new float[]{e.x, 0.9F, e.z, 1.4F, id});
      // Kopf, Körper, Arme, Beine: Drehpunkt (x, y, z), Box-Mitte relativ dazu, Maße, Skin-UV, Drehung
      this.part(s, base, id, skin, 0, 24, 0, 0, 4, 0, 8, 8, 8, 0, 0, 0.0F);
      this.part(s, base, id, skin, 0, 18, 0, 0, 0, 0, 8, 12, 4, 16, 16, 0.0F);
      float armW = sl ? 3 : 4;
      float armAim = 1.45F * aim;
      // rechte Seite der Figur = +x (sie schaut nach −z)
      this.part(s, base, id, skin, 4 + armW / 2, 22, 0, 0, -4, 0, armW, 12, 4, 40, 16, armAim - swing * (1.0F - aim));
      this.part(s, base, id, skin, -(4 + armW / 2), 22, 0, 0, -4, 0, armW, 12, 4, 32, 48, armAim * 0.85F + swing * (1.0F - aim));
      this.part(s, base, id, skin, 2, 12, 0, 0, -6, 0, 4, 12, 4, 0, 16, swing);
      this.part(s, base, id, skin, -2, 12, 0, 0, -6, 0, 4, 12, 4, 16, 48, -swing);
      // Waffe in der rechten Hand
      Part g = this.part(s, base, id, skin, 4 + armW / 2, 22, 0, 0, -12, -1.5F, 2, 9, 3, 0, 0, armAim - swing * (1.0F - aim));
      g.gun = true;
   }

   private Part part(Snapshot s, Matrix4f base, int id, Tex skin, float px, float py, float pz, float ox, float oy, float oz, float w, float h, float d, int u, int v, float rot) {
      Matrix4f m = new Matrix4f(base).translate(px, py, pz).rotateX(rot).translate(ox, oy, oz);
      Part p = new Part();
      m.invert(p.inv);
      p.w = w;
      p.h = h;
      p.d = d;
      p.u = u;
      p.v = v;
      p.skin = skin;
      p.enemy = id;
      s.parts.add(p);
      return p;
   }

   // ================================================================== Raycasting

   private static final int SKY_TOP = 0xFF3F7CD8;
   private static final int SKY_LOW = 0xFFBCD8F0;

   /** Ein Bild rendern (im Hintergrund aufrufen); Rückgabe ARGB, W x H. */
   public int[] render(Snapshot s) {
      int[] out = new int[W * H];
      int[] who = new int[W * H];
      float focal = H / 2.0F / (float)Math.tan(Math.toRadians(FOV / 2.0));
      double y = Math.toRadians(s.yaw);
      double p = Math.toRadians(s.pitch);
      float fx = (float)(Math.sin(y) * Math.cos(p));
      float fy = (float)Math.sin(p);
      float fz = (float)(Math.cos(y) * Math.cos(p));
      // rechts = (cos y, 0, -sin y) in dieser Blickkonvention gespiegelt: Bildschirm-rechts zeigt nach −x bei Blick +z
      float rx = (float)-Math.cos(y);
      float rz = (float)Math.sin(y);
      float ux = -fy * (float)Math.sin(y);
      float uy = (float)Math.cos(p);
      float uz = -fy * (float)Math.cos(y);
      Vector3f o = new Vector3f();
      Vector3f dd = new Vector3f();

      for (int py = 0; py < H; py++) {
         for (int pxl = 0; pxl < W; pxl++) {
            float a = (pxl + 0.5F - W / 2.0F) / focal;
            float b = (H / 2.0F - py - 0.5F) / focal;
            float dx = fx + rx * a + ux * b;
            float dy = fy + uy * b;
            float dz = fz + rz * a + uz * b;
            float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;
            float[] hit = this.castVoxel(s.cx, s.cy, s.cz, dx, dy, dz);
            float tv = hit[0];
            int color = (int)hit[1];
            int whoId = -1;
            // Körperteile
            for (float[] bnd : s.bounds) {
               float cx = bnd[0] - s.cx;
               float cy = bnd[1] - s.cy;
               float cz = bnd[2] - s.cz;
               float proj = cx * dx + cy * dy + cz * dz;
               float d2 = cx * cx + cy * cy + cz * cz - proj * proj;
               if (proj <= 0.0F || d2 > bnd[3] * bnd[3] || proj - bnd[3] > tv) {
                  continue;
               }

               int eid = (int)bnd[4];

               for (Part pt : s.parts) {
                  if (pt.enemy != eid) {
                     continue;
                  }

                  pt.inv.transformPosition(s.cx, s.cy, s.cz, o);
                  pt.inv.transformDirection(dx, dy, dz, dd);
                  float t = slab(o, dd, pt.w / 2, pt.h / 2, pt.d / 2);
                  if (t > 0.0F && t < tv) {
                     tv = t;
                     whoId = eid;
                     color = this.skinColor(pt, o.x + dd.x * t, o.y + dd.y * t, o.z + dd.z * t);
                  }
               }
            }

            if (color == 0) {
               color = sky(dy, dx, dz);
            } else {
               // Dunst in der Ferne
               float fog = Math.min(1.0F, Math.max(0.0F, (tv - 14.0F) / 22.0F));
               color = Tex.mix(color, SKY_LOW, fog * 0.6F);
            }

            out[py * W + pxl] = color | 0xFF000000;
            who[py * W + pxl] = whoId;
         }
      }

      // rote Umrandung um Gegner (wie im Spiel)
      for (int py = 1; py < H - 1; py++) {
         for (int pxl = 1; pxl < W - 1; pxl++) {
            int i = py * W + pxl;
            if (who[i] < 0 && (who[i - 1] >= 0 || who[i + 1] >= 0 || who[i - W] >= 0 || who[i + W] >= 0)) {
               out[i] = 0xFFFF3A4A;
            }
         }
      }

      return out;
   }

   private static float slab(Vector3f o, Vector3f d, float hx, float hy, float hz) {
      float t0 = -Float.MAX_VALUE;
      float t1 = Float.MAX_VALUE;
      float[] oo = {o.x, o.y, o.z};
      float[] dv = {d.x, d.y, d.z};
      float[] h = {hx, hy, hz};

      for (int k = 0; k < 3; k++) {
         if (Math.abs(dv[k]) < 1.0E-7F) {
            if (oo[k] < -h[k] || oo[k] > h[k]) {
               return -1.0F;
            }
         } else {
            float a = (-h[k] - oo[k]) / dv[k];
            float b = (h[k] - oo[k]) / dv[k];
            if (a > b) {
               float s = a;
               a = b;
               b = s;
            }

            t0 = Math.max(t0, a);
            t1 = Math.min(t1, b);
            if (t0 > t1) {
               return -1.0F;
            }
         }
      }

      return t0 > 0.0F ? t0 : -1.0F;
   }

   /** Farbe eines Körperteils an einem lokalen Punkt (Skin-Abwicklung wie im Spiel). */
   private int skinColor(Part p, float x, float y, float z) {
      if (p.gun) {
         Tex g = this.mats[10];
         return shadeRgb(g.at((int)((x + 1) * 2), (int)((y + 2) * 2)), 0.7F);
      }

      float w = p.w;
      float h = p.h;
      float d = p.d;
      float ax = Math.abs(x) / (w / 2);
      float ay = Math.abs(y) / (h / 2);
      float az = Math.abs(z) / (d / 2);
      int su;
      int sv;
      float shade;
      if (ay >= ax && ay >= az) {
         // oben/unten
         float fu = (w / 2 - x) / w;
         float fv = y > 0 ? (d / 2 - z) / d : (z + d / 2) / d;
         su = (int)(p.u + d + (y > 0 ? 0 : w) + fu * w);
         sv = (int)(p.v + fv * d);
         shade = y > 0 ? 1.0F : 0.5F;
      } else if (az >= ax) {
         // vorne (−z) / hinten (+z)
         float fv = (h / 2 - y) / h;
         if (z < 0) {
            su = (int)(p.u + d + (w / 2 - x) / w * w);
         } else {
            su = (int)(p.u + 2 * d + w + (x + w / 2) / w * w);
         }

         sv = (int)(p.v + d + fv * h);
         shade = z < 0 ? 0.9F : 0.75F;
      } else {
         float fv = (h / 2 - y) / h;
         if (x > 0) {
            // rechte Seite der Figur
            su = (int)(p.u + (d / 2 - z) / d * d);
         } else {
            su = (int)(p.u + d + w + (z + d / 2) / d * d);
         }

         sv = (int)(p.v + d + fv * h);
         shade = 0.7F;
      }

      int c = p.skin.get(Math.min(p.skin.w - 1, Math.max(0, su)), Math.min(p.skin.h - 1, Math.max(0, sv)));
      if ((c >>> 24) < 20) {
         c = 0xFF5A4A3A;
      }

      return shadeRgb(c, shade);
   }

   private static int shadeRgb(int c, float k) {
      int r = Math.min(255, Math.round((c >> 16 & 255) * k));
      int g = Math.min(255, Math.round((c >> 8 & 255) * k));
      int b = Math.min(255, Math.round((c & 255) * k));
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int sky(float dy, float dx, float dz) {
      float k = Math.max(0.0F, Math.min(1.0F, dy * 2.2F + 0.1F));
      int c = Tex.mix(SKY_LOW, SKY_TOP, k);
      // blockige Wolken auf einer Ebene über der Karte
      if (dy > 0.02F) {
         float t = 22.0F / dy;
         int cx = (int)Math.floor((dx * t) / 3.0F);
         int cz = (int)Math.floor((dz * t) / 3.0F);
         int hsh = cx * 73856093 ^ cz * 19349663;
         if ((hsh >>> 8 & 7) == 0) {
            c = Tex.mix(c, 0xFFFFFFFF, 0.8F * Math.min(1.0F, dy * 4.0F));
         }
      }

      return c;
   }

   /** Voxel-Strahl (DDA): {Abstand, Farbe}; Farbe 0 = Himmel. */
   private float[] castVoxel(float ox, float oy, float oz, float dx, float dy, float dz) {
      int x = (int)Math.floor(ox);
      int y = (int)Math.floor(oy);
      int z = (int)Math.floor(oz);
      int sx = dx > 0 ? 1 : -1;
      int sy = dy > 0 ? 1 : -1;
      int sz = dz > 0 ? 1 : -1;
      float tdx = Math.abs(1.0F / dx);
      float tdy = Math.abs(1.0F / dy);
      float tdz = Math.abs(1.0F / dz);
      float tx = (dx > 0 ? x + 1 - ox : ox - x) * tdx;
      float ty = (dy > 0 ? y + 1 - oy : oy - y) * tdy;
      float tz = (dz > 0 ? z + 1 - oz : oz - z) * tdz;
      int face = 0;
      float t = 0.0F;

      for (int i = 0; i < 160 && t < 48.0F; i++) {
         if (tx < ty && tx < tz) {
            t = tx;
            x += sx;
            tx += tdx;
            face = 0;
         } else if (ty < tz) {
            t = ty;
            y += sy;
            ty += tdy;
            face = 1;
         } else {
            t = tz;
            z += sz;
            tz += tdz;
            face = 2;
         }

         if (y < 0) {
            float hx = ox + dx * t;
            float hz = oz + dz * t;
            int floor = ((int)Math.floor(hx / CELL) + (int)Math.floor(hz / CELL)) % 7 == 0 ? 8 : 1;
            return new float[]{t, this.texel(floor, hx, oy + dy * t, hz, 1, 1)};
         }

         if (y >= MH && dy > 0) {
            return new float[]{t, 0.0F};
         }

         int m = this.get(x, y, z);
         if (m != 0) {
            return new float[]{t, this.texel(m, ox + dx * t, oy + dy * t, oz + dz * t, face, face == 1 ? -sy : (face == 0 ? -sx : -sz))};
         }
      }

      return new float[]{t, 0.0F};
   }

   /** Texel einer Blockfläche mit Minecraft-Schattierung und Sonnenlicht. */
   private int texel(int m, float hx, float hy, float hz, int face, int dir) {
      Tex t = this.mats[m];
      float u;
      float v;
      float shade;
      if (face == 1) {
         u = hx;
         v = hz;
         shade = dir > 0 ? 1.0F : 0.5F;
      } else if (face == 0) {
         u = hz;
         v = -hy;
         shade = dir > 0 ? 0.62F : 0.82F;
      } else {
         u = hx;
         v = -hy;
         shade = dir > 0 ? 0.92F : 0.72F;
      }

      int c = t.at((int)Math.floor(u * 16.0F), (int)Math.floor(v * 16.0F));
      // warmes Sonnenlicht
      int r = Math.min(255, Math.round((c >> 16 & 255) * shade * 1.08F));
      int g = Math.min(255, Math.round((c >> 8 & 255) * shade * 1.02F));
      int b = Math.min(255, Math.round((c & 255) * shade * 0.95F));
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   /** Weltpunkt → Bildpunkt im Spielbild (x, y) oder null. */
   public float[] project(Snapshot s, float wx, float wy, float wz) {
      float focal = H / 2.0F / (float)Math.tan(Math.toRadians(FOV / 2.0));
      double y = Math.toRadians(s.yaw);
      double p = Math.toRadians(s.pitch);
      float fx = (float)(Math.sin(y) * Math.cos(p));
      float fy = (float)Math.sin(p);
      float fz = (float)(Math.cos(y) * Math.cos(p));
      float rx = (float)-Math.cos(y);
      float rz = (float)Math.sin(y);
      float ux = -fy * (float)Math.sin(y);
      float uy = (float)Math.cos(p);
      float uz = -fy * (float)Math.cos(y);
      float vx = wx - s.cx;
      float vy = wy - s.cy;
      float vz = wz - s.cz;
      float depth = vx * fx + vy * fy + vz * fz;
      if (depth < 0.1F) {
         return null;
      } else {
         float a = (vx * rx + vz * rz) / depth;
         float b = (vx * ux + vy * uy + vz * uz) / depth;
         return new float[]{W / 2.0F + a * focal, H / 2.0F - b * focal};
      }
   }

   /** Minikarte: Wände (true) als Raster. */
   public boolean wall(int x, int z) {
      return !this.walkable(x, z);
   }

   /** Felder je Kartenseite (Minikarte). */
   public static int mapSize() {
      return NC;
   }

   public static float cellSize() {
      return CELL;
   }

   /** Nur für Tests: Gegner direkt vor dich stellen. */
   public void debugEnemyAhead(float dist, boolean aiming, boolean walking) {
      Enemy e = new Enemy();
      double a = Math.toRadians(this.yaw);
      e.x = this.px + (float)Math.sin(a) * dist;
      e.z = this.pz + (float)Math.cos(a) * dist;
      e.yaw = heading(e.x, e.z, this.px, this.pz) + (walking ? 60.0F : 0.0F);
      e.aim = aiming ? 1.0F : 0.0F;
      e.moving = walking;
      e.walk = 1.2F;
      e.skin = this.enemies.size() % SKINS.length;
      this.enemies.clear();
      this.enemies.add(e);
      this.pitch = 0.0F;
   }

   public boolean hasTarget() {
      return this.target != null;
   }

   public float px() {
      return this.px;
   }

   public float pz() {
      return this.pz;
   }

   public float yaw() {
      return this.yaw;
   }

   /** Lebende Gegner: {x, z, sichtbar} für die Minikarte. */
   public List<float[]> enemyDots() {
      List<float[]> l = new ArrayList<>();

      for (Enemy e : this.enemies) {
         if (e.deadAt < 0.0F) {
            l.add(new float[]{e.x, e.z, e == this.target ? 1.0F : 0.0F});
         }
      }

      return l;
   }
}
