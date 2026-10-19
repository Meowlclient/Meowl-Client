package com.lennart.nebula.afk.terra;

import static com.lennart.nebula.afk.terra.TerraTiles.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Das „Spiel“ hinter der Terraria-Szene: dein Held läuft von selbst durch die Welt, gräbt Treppen in die Tiefe, setzt
 * Fackeln, baut Erze ab, besucht NPC-Häuser und kämpft gegen Schleime, Zombies, Dämonenaugen und nachts gegen das Auge
 * von Cthulhu. Tag-Nacht-Wechsel, Schadenszahlen, Münzen und Beute wie in Terraria. Reines Java.
 */
public final class TerraSim {
   public interface Events {
      void sound(int kind, float vol, float pitch);
   }

   public static final int S_SWING = 0;
   public static final int S_HIT = 1;
   public static final int S_SLIME = 2;
   public static final int S_ZOMBIE = 3;
   public static final int S_DIG = 4;
   public static final int S_PLACE = 5;
   public static final int S_COIN = 6;
   public static final int S_BOW = 7;
   public static final int S_ROAR = 8;
   public static final int S_HURT = 9;
   public static final int S_DRINK = 10;
   public static final int S_DOOR = 11;
   public static final int S_KILL = 12;
   public static final int S_BOSS_DIE = 13;
   public static final float DAY_LEN = 320.0F;
   public static final float NIGHT_START = 215.0F;
   private static final float G = 55.0F;
   public final TerraWorld world;
   private final Random rnd;
   private Events events;
   private boolean battle = true;
   public float time;
   public float dayT = 25.0F;
   public int nights;
   public final Hero hero = new Hero();
   public final List<Mob> mobs = new ArrayList<>();
   public final List<Arrow> arrows = new ArrayList<>();
   public final List<Particle> particles = new ArrayList<>();
   public final List<Popup> popups = new ArrayList<>();
   public final List<Drop> drops = new ArrayList<>();
   public final List<Npc> npcs = new ArrayList<>();
   public final List<Message> messages = new ArrayList<>();
   public Mob boss;
   private float spawnT = 4.0F;
   private float bunnyT = 3.0F;
   private float bossWarnAt = -1.0F;
   private float bossSpawnAt = -1.0F;
   private float acc;

   // ================================================================== Figuren

   public static final int MODE_WALK = 0;
   public static final int MODE_DOWN = 1;
   public static final int MODE_TUNNEL = 2;
   public static final int MODE_UP = 3;
   public static final int ITEM_PICK = 0;
   public static final int ITEM_SWORD = 1;
   public static final int ITEM_BOW = 2;
   public static final int ITEM_TORCH = 3;
   public static final int ITEM_POTION = 4;

   public static final class Hero {
      public float x = 20.5F;
      public float y = 70.0F;
      public float vx;
      public float vy;
      public int dir = 1;
      public boolean ground;
      public boolean inWater;
      public float walk;
      public float hp = 400.0F;
      public final float maxHp = 400.0F;
      public float mana = 200.0F;
      public final float maxMana = 200.0F;
      public float immune;
      public float swing = -1.0F;
      public float swingDur = 0.3F;
      public int item = ITEM_PICK;
      public float bow = -1.0F;
      public float aim;
      public float drink = -1.0F;
      public float potionCd;
      public float death;
      public long coins;
      public boolean holdTorch;
      public int mode = MODE_WALK;
      public float drawY = 70.0F;
      // Graben
      int digCol;
      int digSteps;
      int tunnelLeft;
      int lastTorchCol = Integer.MIN_VALUE;
      float nextDig = 50.0F;
      final List<int[]> queue = new ArrayList<>();
      public int mineX = Integer.MIN_VALUE;
      public int mineY;
      public float mineProg;
      float mineNeed;
      float progressX;
      float stuckT;
      float bowCd;
      Mob target;
      float targetT;

      static final float HW = 0.42F;
      static final float H = 2.75F;
   }

   public static final int SLIME = 0;
   public static final int ZOMBIE = 1;
   public static final int EYE = 2;
   public static final int BUNNY = 3;
   public static final int BOSS = 4;
   public static final int SERVANT = 5;

   public static final class Mob {
      public int type;
      public float x;
      public float y;
      public float vx;
      public float vy;
      public float hp;
      public float max;
      public int dir = 1;
      public boolean ground;
      public float hurt;
      public float hw;
      public float h;
      public int color = -1;
      public float t;
      public float ai;
      public int state;
      public int phase = 1;
      public float angle;
      public float squash;
      public int charges;
      public float ignoreUntil;
      public int skin;
      boolean dead;
   }

   public static final class Arrow {
      public float x;
      public float y;
      public float vx;
      public float vy;
      public float life = 2.5F;
      public boolean stuck;
   }

   public static final int P_DUST = 0;
   public static final int P_GLOW = 1;
   public static final int P_GORE = 2;

   public static final class Particle {
      public float x;
      public float y;
      public float vx;
      public float vy;
      public float life;
      public float max;
      public int color;
      public float size;
      public int kind;
      /** Block-Splitter: Textur und Ausschnitt-Versatz. */
      public String tex;
      public int texOff;
   }

   /** Schadenszahl oder Aufsammel-Text, steigt auf und verblasst. */
   public static final class Popup {
      public float x;
      public float y;
      public String text;
      public int color;
      public float life = 1.1F;
      public float scale = 1.0F;
      String key;
      int count;
   }

   public static final class Drop {
      public float x;
      public float y;
      public float vx;
      public float vy;
      public String sprite;
      String name;
      int count = 1;
      long coins;
      float age;
   }

   public static final class Npc {
      public float x;
      public float y;
      public int dir = 1;
      public float walk;
      public int skin;
      public String name;
      public String bubble;
      public float bubbleT;
      float target;
      float wait;
      boolean greeted;
      final TerraWorld.House house;

      Npc(TerraWorld.House h) {
         this.house = h;
      }
   }

   public static final class Message {
      public final String text;
      public final int color;
      public float life = 9.0F;

      Message(String text, int color) {
         this.text = text;
         this.color = color;
      }
   }

   public static final String[] NPC_NAMES = {"Führer Jonas", "Händlerin Mia", "Schmied Bruno", "Krankenschwester Lea", "Zauberer Finn"};
   private static final String[] NPC_LINES = {
      "Schön, dich zu sehen! Fackeln helfen gegen die Dunkelheit.",
      "Nachts kommen die Zombies – bleib wachsam!",
      "Man sagt, tief unten glitzern Diamanten …",
      "Der Mond wirkt heute irgendwie unheimlich …",
      "Willst du was kaufen? Nein? Schade.",
      "Pass auf die Schleime auf, die kleben furchtbar!",
      "Ein riesiges Auge am Himmel? Das bildest du dir ein.",
      "Grab nicht zu tief – da unten brodelt Lava!",
      "Heilen kostet extra. Für dich heute gratis.",
      "Hast du schon mal einen Leuchtpilz probiert?"
   };
   public static final int BOSS_COLOR = 0xFFB04CFF;

   public TerraSim(long seed) {
      this.rnd = new Random(seed);
      this.world = new TerraWorld(seed);
      Hero h = this.hero;
      h.y = this.world.surface(20);
      h.drawY = h.y;
      h.progressX = h.x;
   }

   public void setEvents(Events e) {
      this.events = e;
   }

   public void configure(boolean battle) {
      this.battle = battle;
      if (!battle) {
         this.mobs.removeIf(m -> m.type != BUNNY);
         this.boss = null;
         this.bossSpawnAt = -1.0F;
         this.bossWarnAt = -1.0F;
      }
   }

   /** Tageszeit setzen (0 = Morgen, 1 = Abend, 2 = Nacht). */
   public void startAt(int which) {
      this.dayT = which == 2 ? NIGHT_START + 3.0F : (which == 1 ? NIGHT_START - 22.0F : 25.0F);
      if (which == 2) {
         this.nightBegins();
      }
   }

   private void sound(int kind, float vol, float pitch) {
      if (this.events != null) {
         this.events.sound(kind, vol, pitch);
      }
   }

   // ================================================================== Tag und Nacht

   public boolean night() {
      return this.dayT >= NIGHT_START;
   }

   /** Himmelslicht 0..1 (Morgen- und Abenddämmerung weich). */
   public float daylight() {
      float t = this.dayT;
      if (t < 12.0F) {
         return 0.25F + 0.75F * smooth(t / 12.0F);
      } else if (t < NIGHT_START - 20.0F) {
         return 1.0F;
      } else if (t < NIGHT_START) {
         return 1.0F - 0.75F * smooth((t - (NIGHT_START - 20.0F)) / 20.0F);
      } else {
         return 0.25F;
      }
   }

   /** 0 am Tag, 1 in der Dämmerung (für Orangetöne). */
   public float dusk() {
      float t = this.dayT;
      if (t < 18.0F) {
         return 1.0F - smooth(t / 18.0F) * 0.999F;
      } else if (t > NIGHT_START - 28.0F && t < NIGHT_START + 8.0F) {
         float k = t < NIGHT_START ? (t - (NIGHT_START - 28.0F)) / 28.0F : 1.0F - (t - NIGHT_START) / 8.0F;
         return smooth(k);
      } else {
         return 0.0F;
      }
   }

   static float smooth(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }

   private void nightBegins() {
      this.nights++;
      if (this.battle && (this.nights == 1 || this.rnd.nextFloat() < 0.65F)) {
         this.bossWarnAt = this.time + 7.0F;
         this.bossSpawnAt = this.time + 14.0F;
      }
   }

   // ================================================================== Aktualisieren

   public void update(float dt) {
      this.acc += Math.min(dt, 0.25F);

      while (this.acc >= 1.0F / 60.0F) {
         this.acc -= 1.0F / 60.0F;
         this.step(1.0F / 60.0F);
      }
   }

   /** Ein paar Sekunden vorlaufen lassen, damit die Szene nicht leer startet. */
   public void prewarm(float seconds) {
      for (float t = 0.0F; t < seconds; t += 1.0F / 60.0F) {
         this.step(1.0F / 60.0F);
      }
   }

   private void step(float dt) {
      this.time += dt;
      boolean wasNight = this.night();
      this.dayT += dt;
      if (this.dayT >= DAY_LEN) {
         this.dayT -= DAY_LEN;
         if (this.boss != null && !this.boss.dead) {
            this.boss.state = 9;
            this.message("Das Auge von Cthulhu ist verschwunden …", BOSS_COLOR);
         }
      }

      if (!wasNight && this.night()) {
         this.nightBegins();
      }

      if (this.bossWarnAt > 0.0F && this.time >= this.bossWarnAt) {
         this.bossWarnAt = -1.0F;
         this.message("Du spürst, wie dich etwas Böses beobachtet …", BOSS_COLOR);
      }

      if (this.bossSpawnAt > 0.0F && this.time >= this.bossSpawnAt) {
         this.bossSpawnAt = -1.0F;
         if (this.night() && this.battle) {
            this.spawnBoss();
         }
      }

      this.world.forget(this.hero.x);
      this.updateHero(dt);
      this.updateDoors(dt);
      this.updateNpcs(dt);
      this.spawn(dt);
      this.updateMobs(dt);
      this.updateArrows(dt);
      this.updateDrops(dt);
      this.updateFx(dt);
   }

   // ================================================================== Kollision

   public boolean solid(int x, int y) {
      int t = this.world.tile(x, y);
      if (t == DOOR_LOW || t == DOOR_HIGH) {
         TerraWorld.House h = this.world.houseAt(x);
         return h == null || !(x == h.x0 ? h.openLeft : h.openRight);
      }

      return TerraTiles.solid(t);
   }

   private boolean boxHits(float x, float y, float hw, float h) {
      int x0 = (int)Math.floor(x - hw);
      int x1 = (int)Math.floor(x + hw - 1.0E-4F);
      int y0 = (int)Math.floor(y + 1.0E-4F);
      int y1 = (int)Math.floor(y + h - 1.0E-4F);

      for (int yy = y0; yy <= y1; yy++) {
         for (int xx = x0; xx <= x1; xx++) {
            if (this.solid(xx, yy)) {
               return true;
            }
         }
      }

      return false;
   }

   /** Bewegt eine Box waagerecht; Stufen von 1 Block werden hochgelaufen. true = blockiert. */
   private boolean moveX(float[] p, float dx, float hw, float h, boolean canStep, boolean ground) {
      if (dx == 0.0F) {
         return false;
      }

      int steps = (int)Math.ceil(Math.abs(dx) / 0.2F);
      float sx = dx / steps;

      for (int i = 0; i < steps; i++) {
         float nx = p[0] + sx;
         if (!this.boxHits(nx, p[1], hw, h)) {
            p[0] = nx;
         } else if (canStep && ground && !this.boxHits(nx, (float)Math.floor(p[1] + 1.0E-3F) + 1.0F, hw, h) && !this.boxHits(p[0], (float)Math.floor(p[1] + 1.0E-3F) + 1.0F, hw, h)) {
            p[1] = (float)Math.floor(p[1] + 1.0E-3F) + 1.0F;
            p[0] = nx;
         } else {
            return true;
         }
      }

      return false;
   }

   /** Senkrecht; true = gelandet/angestoßen. */
   private boolean moveY(float[] p, float dy, float hw, float h) {
      int steps = (int)Math.ceil(Math.abs(dy) / 0.2F);
      if (steps == 0) {
         return false;
      }

      float sy = dy / steps;

      for (int i = 0; i < steps; i++) {
         float ny = p[1] + sy;
         if (this.boxHits(p[0], ny, hw, h)) {
            if (sy < 0.0F) {
               p[1] = (float)Math.floor(ny) + 1.0F;
            }

            return true;
         }

         p[1] = ny;
      }

      return false;
   }

   private boolean inLiquid(float x, float y, int liquid) {
      return this.world.tile((int)Math.floor(x), (int)Math.floor(y + 0.5F)) == liquid;
   }

   // ================================================================== Held

   private void updateHero(float dt) {
      Hero h = this.hero;
      if (h.death > 0.0F) {
         h.death -= dt;
         if (h.death <= 0.0F) {
            h.hp = h.maxHp;
            h.immune = 3.0F;
            h.vy = 0.0F;
            h.mode = h.y < this.world.surface((int)Math.floor(h.x)) - 3 ? MODE_UP : MODE_WALK;
            h.queue.clear();
            this.message("Du bist wieder da!", 0xFFFFE070);
         }

         return;
      }

      h.immune = Math.max(0.0F, h.immune - dt);
      h.potionCd = Math.max(0.0F, h.potionCd - dt);
      h.bowCd = Math.max(0.0F, h.bowCd - dt);
      h.hp = Math.min(h.maxHp, h.hp + dt * (h.target == null ? 6.0F : 1.5F));
      h.mana = Math.min(h.maxMana, h.mana + dt * 12.0F);
      int fx = (int)Math.floor(h.x);
      int fy = (int)Math.floor(h.y + 1.0E-3F);
      int surf = this.world.surface(fx);
      boolean underground = fy < surf - 3;
      h.holdTorch = (this.daylight() < 0.5F || underground) && h.bow < 0.0F;
      // Trank
      if (h.drink >= 0.0F) {
         h.drink += dt / 0.6F;
         if (h.drink >= 1.0F) {
            h.drink = -1.0F;
            h.hp = Math.min(h.maxHp, h.hp + 150.0F);
            this.popup(h.x, h.y + 3.2F, "150", 0xFF40FF60, 1.1F);
         }
      } else if (h.hp < h.maxHp * 0.4F && h.potionCd <= 0.0F) {
         h.drink = 0.0F;
         h.potionCd = 30.0F;
         h.item = ITEM_POTION;
         this.sound(S_DRINK, 0.6F, 1.0F);
      }

      // Ziel suchen
      Mob tgt = this.findTarget();
      if (tgt != h.target) {
         h.target = tgt;
         h.targetT = 0.0F;
      }

      float intent = 0.0F;
      if (tgt != null) {
         h.targetT += dt;
         float dx = tgt.x - h.x;
         float dy = tgt.y + tgt.h / 2.0F - (h.y + 1.6F);
         float dist = (float)Math.hypot(dx, dy);
         h.dir = dx >= 0.0F ? 1 : -1;
         h.aim = (float)Math.atan2(dy, Math.abs(dx));
         boolean melee = dist < (tgt.type == BOSS ? 3.6F : 2.5F);
         if (melee) {
            if (h.swing < 0.0F && h.drink < 0.0F) {
               this.startSwing(ITEM_SWORD, 0.3F);
            }
         } else if (h.bowCd <= 0.0F && h.swing < 0.0F && h.drink < 0.0F && this.clearLine(h.x, h.y + 1.9F, tgt.x, tgt.y + tgt.h / 2.0F)) {
            if (h.bow < 0.0F) {
               h.bow = 0.0F;
               h.item = ITEM_BOW;
            }
         } else if (h.bow < 0.0F && h.swing < 0.0F && tgt.type != BOSS && h.targetT > 4.0F) {
            // nicht erreichbar → in Ruhe lassen
            tgt.ignoreUntil = this.time + 12.0F;
         }

         // dem Boss ausweichen
         if (tgt.type == BOSS && tgt.state == 2 && Math.abs(dx) < 9.0F && h.ground) {
            h.vy = 19.0F;
         }

         if (tgt.type == ZOMBIE && dist > 3.0F && dist < 6.0F && h.bowCd > 0.2F) {
            intent = 0.0F;
         }
      } else {
         h.aim = 0.0F;
         intent = this.explore(dt, fx, fy, surf);
      }

      // Bogen
      if (h.bow >= 0.0F) {
         h.bow += dt / 0.32F;
         if (h.bow >= 1.0F) {
            h.bow = -1.0F;
            h.bowCd = 0.42F;
            if (tgt != null) {
               this.shoot(tgt);
            }
         }
      }

      // Schwung (Schwert / Spitzhacke)
      if (h.swing >= 0.0F) {
         float before = h.swing;
         h.swing += dt / h.swingDur;
         if (h.item == ITEM_SWORD && before < 0.45F && h.swing >= 0.45F) {
            this.swordHit();
         }

         if (h.item == ITEM_PICK && before < 0.6F && h.swing >= 0.6F) {
            this.pickHit();
         }

         if (h.swing >= 1.0F) {
            h.swing = -1.0F;
         }
      }

      // Bewegung
      h.inWater = this.inLiquid(h.x, h.y, WATER);
      boolean inLava = this.inLiquid(h.x, h.y, LAVA);
      float speed = h.inWater ? 3.0F : 5.2F;
      float want = intent * speed;
      h.vx += (want - h.vx) * Math.min(1.0F, dt * (h.ground ? 14.0F : 5.0F));
      if (Math.abs(h.vx) > 0.2F) {
         h.dir = tgt != null ? h.dir : (h.vx > 0.0F ? 1 : -1);
      }

      h.vy -= (h.inWater ? 16.0F : G) * dt;
      h.vy = Math.max(h.inWater ? -5.0F : -32.0F, h.vy);
      if (h.inWater && this.inLiquid(h.x, h.y + 2.0F, WATER)) {
         h.vy += 30.0F * dt;
      }

      float[] p = {h.x, h.y};
      boolean blocked = this.moveX(p, h.vx * dt, Hero.HW, Hero.H, true, h.ground);
      boolean hit = this.moveY(p, h.vy * dt, Hero.HW, Hero.H);
      h.ground = hit && h.vy <= 0.0F;
      if (hit) {
         h.vy = 0.0F;
      }

      h.x = p[0];
      h.y = p[1];
      if (blocked && h.ground && intent != 0.0F) {
         this.obstacle(h);
      }

      if (h.ground && Math.abs(h.vx) > 0.3F) {
         h.walk += Math.abs(h.vx) * dt * 2.1F;
      } else {
         h.walk = h.walk * 0.85F;
      }

      // weich nachziehen (Stufen)
      h.drawY += (h.y - h.drawY) * Math.min(1.0F, dt * 18.0F);
      if (Math.abs(h.y - h.drawY) > 3.0F) {
         h.drawY = h.y;
      }

      if (inLava && h.immune <= 0.0F) {
         this.hurtHero(40.0F, 0, "Lava");
      }

      if (h.y < 2.0F) {
         h.y = this.world.surface(fx);
      }

      // festgefahren?
      if (Math.abs(h.x - h.progressX) > 1.0F) {
         h.progressX = h.x;
         h.stuckT = 0.0F;
      } else if (tgt == null && h.mineX == Integer.MIN_VALUE) {
         h.stuckT += dt;
         if (h.stuckT > 5.0F) {
            h.stuckT = 0.0F;
            int ax = fx + h.dir;

            for (int k = 0; k < 3; k++) {
               h.queue.add(new int[]{ax, fy + k});
            }

            h.queue.add(new int[]{fx, fy + 3});
         }
      }
   }

   private void startSwing(int item, float dur) {
      Hero h = this.hero;
      h.item = item;
      h.swing = 0.0F;
      h.swingDur = dur;
      this.sound(S_SWING, item == ITEM_SWORD ? 0.5F : 0.25F, item == ITEM_SWORD ? 1.0F + this.rnd.nextFloat() * 0.2F : 1.5F);
   }

   /** Laufen und Graben ohne Gegner. Liefert die gewünschte Laufrichtung. */
   private float explore(float dt, int fx, int fy, int surf) {
      Hero h = this.hero;
      // erst die Grab-Liste abarbeiten
      while (!h.queue.isEmpty()) {
         int[] c = h.queue.get(0);
         if (!this.diggable(c[0], c[1])) {
            h.queue.remove(0);
            continue;
         }

         if (h.mineX != c[0] || h.mineY != c[1]) {
            h.mineX = c[0];
            h.mineY = c[1];
            h.mineProg = 0.0F;
            h.mineNeed = hardness(this.world.tile(c[0], c[1]));
         }

         h.dir = c[0] > fx ? 1 : (c[0] < fx ? -1 : h.dir);
         if (h.swing < 0.0F) {
            this.startSwing(ITEM_PICK, 0.24F);
         }

         return 0.0F;
      }

      h.mineX = Integer.MIN_VALUE;
      int dir = 1;
      switch (h.mode) {
         case MODE_DOWN -> {
            if (fx == h.digCol && h.ground) {
               h.digSteps--;
               this.maybeTorch(fx, fy);
               if (h.digSteps <= 0 || this.liquidNear(fx + dir, fy - 2)) {
                  h.mode = MODE_TUNNEL;
                  h.tunnelLeft = 14 + this.rnd.nextInt(14);
                  h.digCol = fx + dir;
               } else {
                  h.digCol = fx + dir;
                  this.queueCells(h.digCol, fy - 1, fy + 2);
                  this.oresAround(h.digCol, fy - 2, fy + 3);
               }

               return 0.0F;
            }

            if (h.digCol != fx + dir && h.digCol != fx) {
               h.digCol = fx + dir;
               this.queueCells(h.digCol, fy - 1, fy + 2);
            }

            return dir;
         }
         case MODE_TUNNEL -> {
            if (fx == h.digCol) {
               h.tunnelLeft--;
               this.maybeTorch(fx, fy);
               if (h.tunnelLeft <= 0 || this.liquidNear(fx + dir, fy) || this.liquidNear(fx + 2 * dir, fy)) {
                  h.mode = MODE_UP;
               }

               h.digCol = fx + dir;
               this.queueCells(h.digCol, fy, fy + 2);
               this.oresAround(h.digCol, fy - 1, fy + 3);
               return 0.0F;
            }

            if (h.digCol != fx + dir) {
               h.digCol = fx + dir;
               this.queueCells(h.digCol, fy, fy + 2);
            }

            return dir;
         }
         case MODE_UP -> {
            if (fy >= surf - 1 && this.skyAbove(fx, fy + 3)) {
               h.mode = MODE_WALK;
               h.nextDig = this.time + 110.0F + this.rnd.nextFloat() * 90.0F;
               return dir;
            }

            if (h.digCol != fx + dir) {
               this.maybeTorch(fx, fy);
               h.digCol = fx + dir;
               this.queueCells(h.digCol, fy + 1, fy + 3);
               h.queue.add(new int[]{fx, fy + 3});
               this.oresAround(h.digCol, fy, fy + 4);
               return 0.0F;
            }

            return dir;
         }
         default -> {
            boolean house = this.world.houseAt(fx + 6) != null || this.world.houseAt(fx - 4) != null || this.world.houseAt(fx + 14) != null;
            if (this.time >= h.nextDig && h.ground && !this.night() && !house && this.daylight() > 0.9F) {
               h.mode = MODE_DOWN;
               h.digSteps = 9 + this.rnd.nextInt(7);
               h.digCol = fx + dir;
               this.queueCells(h.digCol, fy - 1, fy + 2);
               this.message("Zeit, nach Erzen zu graben!", 0xFFFFFFFF);
               return 0.0F;
            }

            return dir;
         }
      }
   }

   /** Hindernis beim Laufen: hochspringen oder durchgraben. */
   private void obstacle(Hero h) {
      int fx = (int)Math.floor(h.x);
      int fy = (int)Math.floor(h.y + 1.0E-3F);
      int ax = (int)Math.floor(h.x + h.dir * (Hero.HW + 0.3F));
      int climb = 0;

      while (climb < 8 && (this.solid(ax, fy + climb) || this.solid(ax, fy + climb + 1) || this.solid(ax, fy + climb + 2))) {
         climb++;
      }

      boolean room = true;

      for (int k = 3; k < 3 + climb; k++) {
         if (this.solid(fx, fy + k)) {
            room = false;
         }
      }

      if (climb <= 4 && room) {
         h.vy = (float)Math.sqrt(2.0F * G * (climb + 0.7F));
      } else if (h.queue.isEmpty()) {
         this.queueCells(ax, fy, fy + 2);
      }
   }

   private void queueCells(int x, int y0, int y1) {
      for (int y = y1; y >= y0; y--) {
         if (this.diggable(x, y)) {
            this.hero.queue.add(new int[]{x, y});
         }
      }
   }

   private void oresAround(int x, int yLow, int yHigh) {
      for (int y : new int[]{yLow, yHigh}) {
         int t = this.world.tile(x, y);
         if (t >= COAL && t <= LAPIS || t == AMETHYST) {
            this.hero.queue.add(new int[]{x, y});
         }
      }
   }

   private boolean diggable(int x, int y) {
      int t = this.world.tile(x, y);
      return TerraTiles.solid(t) && t != BEDROCK && t != DOOR_LOW && t != DOOR_HIGH && y > 1;
   }

   private boolean liquidNear(int x, int y) {
      for (int dy = -2; dy <= 3; dy++) {
         if (TerraTiles.liquid(this.world.tile(x, y + dy))) {
            return true;
         }
      }

      return false;
   }

   private boolean skyAbove(int x, int y) {
      for (int yy = y; yy < TerraWorld.WH; yy++) {
         if (this.solid(x, yy)) {
            return false;
         }
      }

      return true;
   }

   private void maybeTorch(int fx, int fy) {
      Hero h = this.hero;
      if (Math.abs(fx - h.lastTorchCol) >= 7 && fy < this.world.surface(fx) - 3) {
         int ty = fy + 1;
         if (this.world.tile(fx, ty) == AIR && this.world.deco(fx, ty) == D_NONE && this.world.backTile(fx, ty) != AIR) {
            this.world.setDeco(fx, ty, D_TORCH);
            h.lastTorchCol = fx;
            this.sound(S_PLACE, 0.4F, 1.2F);
         }
      }
   }

   static float hardness(int t) {
      return switch (t) {
         case DIRT, GRASS, SAND, SNOW, SNOW_GRASS, MUD, JUNGLE_GRASS, GRAVEL, CLAY, OAK_LEAVES, MUSHROOM_GRASS, CRIMSON_GRASS -> 1.0F;
         case DEEPSLATE, DIAMOND, REDSTONE -> 3.0F;
         default -> 2.0F;
      };
   }

   private void pickHit() {
      Hero h = this.hero;
      if (h.mineX == Integer.MIN_VALUE || !this.diggable(h.mineX, h.mineY)) {
         return;
      }

      h.mineProg += 1.0F;
      int t = this.world.tile(h.mineX, h.mineY);
      String tex = TerraTiles.TEX[t][0];
      this.chips(h.mineX + 0.5F, h.mineY + 0.5F, tex, 3);
      this.sound(S_DIG, 0.35F, 0.9F + this.rnd.nextFloat() * 0.3F);
      if (h.mineProg >= h.mineNeed) {
         this.world.set(h.mineX, h.mineY, AIR);
         if (this.world.deco(h.mineX, h.mineY + 1) != D_TORCH && this.world.deco(h.mineX, h.mineY + 1) != D_NONE && !this.solid(h.mineX, h.mineY)) {
            this.world.setDeco(h.mineX, h.mineY + 1, D_NONE);
         }

         this.chips(h.mineX + 0.5F, h.mineY + 0.5F, tex, 8);
         this.blockDrop(h.mineX + 0.5F, h.mineY + 0.3F, t);
         if (!h.queue.isEmpty()) {
            h.queue.remove(0);
         }

         h.mineX = Integer.MIN_VALUE;
      }
   }

   private void blockDrop(float x, float y, int t) {
      String sprite;
      String name;
      switch (t) {
         case COAL -> {
            sprite = "item/coal";
            name = "Kohle";
         }
         case IRON -> {
            sprite = "item/raw_iron";
            name = "Eisenerz";
         }
         case GOLD -> {
            sprite = "item/raw_gold";
            name = "Golderz";
         }
         case DIAMOND -> {
            sprite = "item/diamond";
            name = "Diamant";
         }
         case REDSTONE -> {
            sprite = "item/redstone";
            name = "Redstone";
         }
         case LAPIS -> {
            sprite = "item/lapis_lazuli";
            name = "Lapislazuli";
         }
         case AMETHYST -> {
            sprite = "item/amethyst_shard";
            name = "Amethyst";
         }
         case DIRT, GRASS, MUD, JUNGLE_GRASS -> {
            sprite = "block/dirt";
            name = "Erdblock";
         }
         case SAND -> {
            sprite = "block/sand";
            name = "Sandblock";
         }
         default -> {
            sprite = TerraTiles.TEX[t][0];
            name = switch (t) {
               case STONE -> "Steinblock";
               case DEEPSLATE -> "Tiefenschiefer";
               case NETHERRACK, CRIMSON_GRASS -> "Purpurstein";
               case SANDSTONE -> "Sandstein";
               case SNOW, SNOW_GRASS -> "Schneeblock";
               case ICE -> "Eisblock";
               case GRAVEL -> "Kies";
               case CLAY -> "Tonblock";
               case MUSHROOM_GRASS -> "Pilzgras";
               case COBBLE -> "Bruchstein";
               default -> "Block";
            };
         }
      }

      Drop d = new Drop();
      d.x = x;
      d.y = y;
      d.vx = (this.rnd.nextFloat() - 0.5F) * 3.0F;
      d.vy = 5.0F;
      d.sprite = sprite;
      d.name = name;
      this.drops.add(d);
   }

   private void swordHit() {
      Hero h = this.hero;

      for (Mob m : this.mobs) {
         if (m.dead || m.type == BUNNY) {
            continue;
         }

         float dx = m.x - h.x;
         float dy = m.y + m.h / 2.0F - (h.y + 1.5F);
         float reach = m.type == BOSS ? 4.6F : 3.0F;
         if (dx * h.dir > -0.6F && Math.abs(dx) < reach + m.hw && Math.abs(dy) < 2.6F + m.h / 2.0F) {
            this.damage(m, 26.0F + this.rnd.nextFloat() * 12.0F, h.dir);
         }
      }
   }

   private void shoot(Mob tgt) {
      Hero h = this.hero;
      Arrow a = new Arrow();
      a.x = h.x + h.dir * 0.6F;
      a.y = h.y + 1.9F;
      float tx = tgt.x + tgt.vx * 0.15F;
      float ty = tgt.y + tgt.h / 2.0F + tgt.vy * 0.15F;
      float dx = tx - a.x;
      float dy = ty - a.y;
      float d = (float)Math.hypot(dx, dy);
      float sp = 32.0F;
      a.vx = dx / d * sp;
      a.vy = dy / d * sp + d * 0.1F;
      this.arrows.add(a);
      this.sound(S_BOW, 0.4F, 1.0F + this.rnd.nextFloat() * 0.2F);
   }

   private boolean clearLine(float x0, float y0, float x1, float y1) {
      float d = (float)Math.hypot(x1 - x0, y1 - y0);
      int n = Math.max(2, (int)(d * 2.0F));

      for (int i = 1; i < n; i++) {
         float t = (float)i / n;
         if (this.solid((int)Math.floor(x0 + (x1 - x0) * t), (int)Math.floor(y0 + (y1 - y0) * t))) {
            return false;
         }
      }

      return true;
   }

   private Mob findTarget() {
      Hero h = this.hero;
      if (this.boss != null && !this.boss.dead && this.boss.state != 9 && Math.abs(this.boss.x - h.x) < 30.0F) {
         return this.boss;
      }

      Mob best = null;
      float bd = Float.MAX_VALUE;

      for (Mob m : this.mobs) {
         if (m.dead || m.type == BUNNY || m.ignoreUntil > this.time) {
            continue;
         }

         float dx = m.x - h.x;
         float dy = m.y - h.y;
         if (Math.abs(dx) < 12.0F && Math.abs(dy) < 8.0F && (Math.abs(dx) < 2.5F && Math.abs(dy) < 2.5F || this.clearLine(h.x, h.y + 1.6F, m.x, m.y + m.h / 2.0F))) {
            float d = dx * dx + dy * dy;
            if (d < bd) {
               bd = d;
               best = m;
            }
         }
      }

      return best;
   }

   private void hurtHero(float dmg, int knock, String by) {
      Hero h = this.hero;
      if (h.death > 0.0F) {
         return;
      }

      int d = Math.round(dmg * (0.85F + this.rnd.nextFloat() * 0.3F));
      h.hp -= d;
      h.immune = 0.8F;
      if (knock != 0) {
         h.vx = knock * 9.0F;
         h.vy = 9.0F;
      }

      this.popup(h.x, h.y + 3.0F, String.valueOf(d), 0xFFFF4040, 1.0F);
      this.sound(S_HURT, 0.5F, 1.0F);
      for (int i = 0; i < 6; i++) {
         this.particle(h.x, h.y + 1.5F, (this.rnd.nextFloat() - 0.5F) * 6.0F, this.rnd.nextFloat() * 6.0F, 0xFFB01010, 0.12F, P_DUST, 0.6F);
      }

      if (h.hp <= 0.0F) {
         h.hp = 0.0F;
         h.death = 4.0F;
         h.queue.clear();
         this.message("Du wurdest von " + by + " besiegt.", 0xFFFF5050);
      }
   }

   // ================================================================== Gegner

   private void spawn(float dt) {
      Hero h = this.hero;
      this.bunnyT -= dt;
      int fx = (int)Math.floor(h.x);
      boolean underground = h.y < this.world.surface(fx) - 5;
      if (this.bunnyT <= 0.0F) {
         this.bunnyT = 14.0F + this.rnd.nextFloat() * 14.0F;
         if (!this.night() && !underground && this.count(BUNNY) < 2) {
            this.spawnWalker(BUNNY, h.x + (this.rnd.nextBoolean() ? 1 : -1) * (14.0F + this.rnd.nextFloat() * 8.0F));
         }
      }

      if (!this.battle || h.death > 0.0F) {
         return;
      }

      this.spawnT -= dt;
      if (this.spawnT > 0.0F) {
         return;
      }

      float side = this.rnd.nextFloat() < 0.75F ? 1.0F : -1.0F;
      float sx = h.x + side * (24.0F + this.rnd.nextFloat() * 6.0F);
      if (underground) {
         this.spawnT = 9.0F + this.rnd.nextFloat() * 8.0F;
         if (this.count(SLIME) < 3) {
            Mob m = this.spawnCave(sx);
            if (m != null) {
               m.color = this.rnd.nextBoolean() ? 0xFF4C9CFF : (this.rnd.nextBoolean() ? 0xFFB060F0 : 0xFFF05050);
            }
         }
      } else if (this.night()) {
         this.spawnT = 5.0F + this.rnd.nextFloat() * 5.0F;
         if (this.rnd.nextFloat() < 0.65F) {
            if (this.count(ZOMBIE) < 4) {
               Mob z = this.spawnWalker(ZOMBIE, sx);
               if (z != null) {
                  this.sound(S_ZOMBIE, 0.35F, 0.9F + this.rnd.nextFloat() * 0.2F);
               }
            }
         } else if (this.count(EYE) < 2) {
            this.spawnFlyer(EYE, sx, h.y + 7.0F + this.rnd.nextFloat() * 5.0F);
         }
      } else {
         this.spawnT = 7.0F + this.rnd.nextFloat() * 8.0F;
         if (this.count(SLIME) < 3) {
            Mob m = this.spawnWalker(SLIME, sx);
            if (m != null) {
               m.color = this.rnd.nextFloat() < 0.6F ? 0xFF5CE05C : 0xFF4C9CFF;
            }
         }
      }
   }

   private int count(int type) {
      int n = 0;

      for (Mob m : this.mobs) {
         if (m.type == type && !m.dead) {
            n++;
         }
      }

      return n;
   }

   private Mob make(int type) {
      Mob m = new Mob();
      m.type = type;
      switch (type) {
         case SLIME -> {
            m.hw = 0.6F;
            m.h = 0.9F;
            m.max = 50.0F;
         }
         case ZOMBIE -> {
            m.hw = 0.4F;
            m.h = 2.75F;
            m.max = 90.0F;
         }
         case EYE -> {
            m.hw = 0.55F;
            m.h = 0.8F;
            m.max = 70.0F;
         }
         case BUNNY -> {
            m.hw = 0.35F;
            m.h = 0.6F;
            m.max = 5.0F;
         }
         case SERVANT -> {
            m.hw = 0.45F;
            m.h = 0.7F;
            m.max = 24.0F;
         }
         default -> {
            m.hw = 2.0F;
            m.h = 4.0F;
            m.max = 1500.0F;
         }
      }

      m.hp = m.max;
      m.ai = 0.5F + this.rnd.nextFloat();
      m.skin = this.rnd.nextInt(4);
      return m;
   }

   private Mob spawnWalker(int type, float sx) {
      int x = (int)Math.floor(sx);
      int gy = this.world.groundBelow(x, (int)this.hero.y + 12);
      if (Math.abs(gy - this.hero.y) > 12.0F || this.boxHits(x + 0.5F, gy, 0.4F, 2.8F)) {
         return null;
      }

      Mob m = this.make(type);
      m.x = x + 0.5F;
      m.y = gy;
      m.dir = m.x < this.hero.x ? 1 : -1;
      this.mobs.add(m);
      return m;
   }

   private Mob spawnCave(float sx) {
      int x0 = (int)Math.floor(sx);
      int hy = (int)Math.floor(this.hero.y);

      for (int k = 0; k < 12; k++) {
         int x = x0 + (k % 2 == 0 ? k : -k);

         for (int y = hy - 6; y <= hy + 6; y++) {
            if (!this.solid(x, y) && this.solid(x, y - 1) && !this.solid(x, y + 1) && !TerraTiles.liquid(this.world.tile(x, y))) {
               Mob m = this.make(SLIME);
               m.x = x + 0.5F;
               m.y = y;
               this.mobs.add(m);
               return m;
            }
         }
      }

      return null;
   }

   private Mob spawnFlyer(int type, float x, float y) {
      Mob m = this.make(type);
      m.x = x;
      m.y = y;
      m.dir = x < this.hero.x ? 1 : -1;
      this.mobs.add(m);
      return m;
   }

   private void spawnBoss() {
      Mob b = this.make(BOSS);
      b.x = this.hero.x + 26.0F;
      b.y = this.hero.y + 16.0F;
      b.state = 0;
      b.ai = 3.0F;
      this.boss = b;
      this.mobs.add(b);
      this.message("Das Auge von Cthulhu ist erwacht!", BOSS_COLOR);
      this.sound(S_ROAR, 0.8F, 0.6F);
   }

   private void updateMobs(float dt) {
      Hero h = this.hero;
      this.mobs.removeIf(m -> {
         boolean gone = m.dead || Math.abs(m.x - h.x) > 48.0F && m.type != BOSS || m.type == ZOMBIE && !this.night() && Math.abs(m.x - h.x) > 22.0F;
         if (gone && m == this.boss) {
            this.boss = null;
         }

         return gone;
      });

      // Kopie: der Boss ruft während der Schleife Diener herbei
      for (Mob m : new ArrayList<>(this.mobs)) {
         m.t += dt;
         m.hurt = Math.max(0.0F, m.hurt - dt);
         if (m.dead) {
            continue;
         }

         switch (m.type) {
            case SLIME -> this.slime(m, dt);
            case ZOMBIE -> this.zombie(m, dt);
            case BUNNY -> this.bunny(m, dt);
            case EYE, SERVANT -> this.eye(m, dt, m.type == EYE ? 7.5F : 9.5F, m.type == EYE ? 8.0F : 14.0F);
            default -> this.bossAi(m, dt);
         }

         // Berührung
         if (m.type != BUNNY && h.immune <= 0.0F && h.death <= 0.0F && Math.abs(m.x - h.x) < m.hw + Hero.HW && m.y < h.y + Hero.H && m.y + m.h > h.y) {
            float dmg = switch (m.type) {
               case SLIME -> 14.0F;
               case ZOMBIE -> 20.0F;
               case EYE -> 18.0F;
               case SERVANT -> 12.0F;
               default -> m.phase == 1 ? 20.0F : 28.0F;
            };
            String by = switch (m.type) {
               case SLIME -> "einem Schleim";
               case ZOMBIE -> "einem Zombie";
               case EYE, SERVANT -> "einem Dämonenauge";
               default -> "dem Auge von Cthulhu";
            };
            this.hurtHero(dmg, m.x < h.x ? 1 : -1, by);
            if (m.type == EYE || m.type == SERVANT) {
               m.vx = -m.vx * 0.8F;
               m.vy = Math.abs(m.vy) * 0.5F + 4.0F;
            }
         }
      }
   }

   private void walkerPhysics(Mob m, float dt, boolean step) {
      m.vy = Math.max(-30.0F, m.vy - G * dt);
      float[] p = {m.x, m.y};
      boolean blocked = this.moveX(p, m.vx * dt, m.hw, m.h, step, m.ground);
      boolean hit = this.moveY(p, m.vy * dt, m.hw, m.h);
      boolean wasGround = m.ground;
      m.ground = hit && m.vy <= 0.0F;
      if (hit) {
         if (m.type == SLIME && !wasGround && m.ground) {
            m.squash = 1.0F;
            if (Math.abs(m.x - this.hero.x) < 16.0F) {
               this.sound(S_SLIME, 0.25F, 1.0F + this.rnd.nextFloat() * 0.3F);
            }
         }

         m.vy = 0.0F;
      }

      if (blocked) {
         m.vx = 0.0F;
         if (m.type == ZOMBIE && m.ground) {
            m.vy = 15.0F;
         }
      }

      m.x = p[0];
      m.y = p[1];
      if (m.y < 2.0F) {
         m.dead = true;
      }
   }

   private void slime(Mob m, float dt) {
      m.squash = Math.max(0.0F, m.squash - dt * 4.0F);
      if (m.ground) {
         m.vx *= 0.8F;
         m.ai -= dt;
         if (m.ai <= 0.0F) {
            float dx = this.hero.x - m.x;
            m.dir = Math.abs(dx) < 28.0F ? (dx > 0.0F ? 1 : -1) : m.dir;
            m.vx = m.dir * (3.2F + this.rnd.nextFloat() * 2.6F);
            m.vy = 11.0F + this.rnd.nextFloat() * 6.0F;
            m.ground = false;
            m.ai = 0.7F + this.rnd.nextFloat() * 1.3F;
         }
      }

      this.walkerPhysics(m, dt, false);
   }

   private void zombie(Mob m, float dt) {
      float dx = this.hero.x - m.x;
      m.dir = dx > 0.0F ? 1 : -1;
      if (m.hurt <= 0.0F || m.ground) {
         m.vx += (m.dir * 2.1F - m.vx) * Math.min(1.0F, dt * 6.0F);
      }

      this.walkerPhysics(m, dt, true);
   }

   private void bunny(Mob m, float dt) {
      if (m.ground) {
         m.vx *= 0.7F;
         m.ai -= dt;
         if (m.ai <= 0.0F) {
            float dx = m.x - this.hero.x;
            m.dir = Math.abs(dx) < 5.0F ? (dx > 0.0F ? 1 : -1) : (this.rnd.nextBoolean() ? 1 : -1);
            m.vx = m.dir * 3.5F;
            m.vy = 7.0F;
            m.ground = false;
            m.ai = 0.4F + this.rnd.nextFloat() * 2.0F;
         }
      }

      this.walkerPhysics(m, dt, true);
   }

   private void eye(Mob m, float dt, float max, float accel) {
      Hero h = this.hero;
      float tx = h.x;
      float ty = h.y + 1.6F + (float)Math.sin(m.t * 1.7F) * 1.5F;
      float dx = tx - m.x;
      float dy = ty - (m.y + m.h / 2.0F);
      float d = Math.max(0.1F, (float)Math.hypot(dx, dy));
      m.vx += dx / d * accel * dt;
      m.vy += dy / d * accel * dt;
      float sp = (float)Math.hypot(m.vx, m.vy);
      if (sp > max) {
         m.vx = m.vx / sp * max;
         m.vy = m.vy / sp * max;
      }

      m.x += m.vx * dt;
      m.y += m.vy * dt;
      m.dir = m.vx >= 0.0F ? 1 : -1;
      m.angle = (float)Math.atan2(m.vy, Math.abs(m.vx));
   }

   /** Auge von Cthulhu: schweben und Diener rufen, dann Sturmangriffe; ab halber Kraft Verwandlung ins Maul. */
   private void bossAi(Mob m, float dt) {
      Hero h = this.hero;
      float cx = m.x;
      float cy = m.y + m.h / 2.0F;
      float hx = h.x;
      float hy = h.y + 1.6F;
      m.ai -= dt;
      if (m.state == 9) {
         m.vy += 30.0F * dt;
         m.y += m.vy * dt;
         m.angle = (float)(-Math.PI / 2);
         if (m.y > h.y + 40.0F) {
            m.dead = true;
         }

         return;
      }

      if (m.phase == 1 && m.hp < m.max * 0.5F && m.state != 3) {
         m.state = 3;
         m.ai = 2.4F;
         this.sound(S_ROAR, 0.7F, 0.7F);
      }

      switch (m.state) {
         case 0 -> {
            // schweben über dem Helden
            float side = m.x < hx ? -1.0F : 1.0F;
            float tx = hx + side * 7.0F;
            float ty = hy + 6.5F;
            m.vx += ((tx - cx) * 2.6F - m.vx) * Math.min(1.0F, dt * 3.0F);
            m.vy += ((ty - cy) * 2.6F - m.vy) * Math.min(1.0F, dt * 3.0F);
            m.angle = lerpAngle(m.angle, (float)Math.atan2(hy - cy, hx - cx), dt * 6.0F);
            if (m.phase == 1 && this.rnd.nextFloat() < dt * 0.9F && this.count(SERVANT) < 3) {
               Mob s = this.spawnFlyer(SERVANT, cx, cy);
               s.vx = (float)Math.cos(m.angle) * 8.0F;
               s.vy = (float)Math.sin(m.angle) * 8.0F;
            }

            if (m.ai <= 0.0F) {
               m.state = 1;
               m.charges = m.phase == 1 ? 3 : 5;
               m.ai = 0.25F;
            }
         }
         case 1 -> {
            // kurz ausholen, dann losstürmen
            m.vx *= 0.9F;
            m.vy *= 0.9F;
            m.angle = lerpAngle(m.angle, (float)Math.atan2(hy - cy, hx - cx), dt * 10.0F);
            if (m.ai <= 0.0F) {
               float sp = m.phase == 1 ? 23.0F : 29.0F;
               m.vx = (float)Math.cos(m.angle) * sp;
               m.vy = (float)Math.sin(m.angle) * sp;
               m.state = 2;
               m.ai = 0.75F;
               this.sound(S_ROAR, 0.35F, 1.3F);
            }
         }
         case 2 -> {
            m.angle = (float)Math.atan2(m.vy, m.vx);
            if (m.ai < 0.3F) {
               m.vx *= 0.92F;
               m.vy *= 0.92F;
            }

            if (m.ai <= 0.0F) {
               m.charges--;
               if (m.charges > 0) {
                  m.state = 1;
                  m.ai = m.phase == 1 ? 0.3F : 0.18F;
               } else {
                  m.state = 0;
                  m.ai = m.phase == 1 ? 2.8F : 1.5F;
               }
            }
         }
         case 3 -> {
            // Verwandlung: wildes Drehen, Fetzen fliegen
            m.vx *= 0.94F;
            m.vy *= 0.94F;
            m.angle += dt * (6.0F + 18.0F * (1.0F - m.ai / 2.4F));
            if (this.rnd.nextFloat() < dt * 12.0F) {
               this.particle(cx, cy, (this.rnd.nextFloat() - 0.5F) * 14.0F, (this.rnd.nextFloat() - 0.2F) * 12.0F, this.rnd.nextBoolean() ? 0xFFB02020 : 0xFFE8DCC8, 0.25F, P_GORE, 1.4F);
            }

            if (m.ai <= 0.0F) {
               m.phase = 2;
               m.state = 0;
               m.ai = 1.0F;
               for (int i = 0; i < 18; i++) {
                  this.particle(cx, cy, (this.rnd.nextFloat() - 0.5F) * 18.0F, (this.rnd.nextFloat() - 0.3F) * 16.0F, i % 3 == 0 ? 0xFFD04020 : 0xFFB02020, 0.3F, P_GORE, 1.8F);
               }
            }
         }
         default -> {
         }
      }

      m.x += m.vx * dt;
      m.y += m.vy * dt;
      m.dir = Math.cos(m.angle) >= 0.0 ? 1 : -1;
   }

   private static float lerpAngle(float a, float b, float t) {
      float d = (float)Math.atan2(Math.sin(b - a), Math.cos(b - a));
      return a + d * Math.min(1.0F, t);
   }

   private void damage(Mob m, float base, int dir) {
      boolean crit = this.rnd.nextFloat() < 0.1F;
      int d = Math.round(base * (crit ? 2.0F : 1.0F) * (0.9F + this.rnd.nextFloat() * 0.2F));
      m.hp -= d;
      m.hurt = 0.25F;
      this.popup(m.x, m.y + m.h + 0.4F, String.valueOf(d), crit ? 0xFFFF8020 : 0xFFFFB040, crit ? 1.35F : 1.0F);
      this.sound(S_HIT, 0.45F, m.type == SLIME ? 1.4F : 1.0F);
      if (m.type != BOSS) {
         m.vx = dir * (m.type == ZOMBIE ? 5.0F : 7.0F);
         m.vy = m.type == EYE || m.type == SERVANT ? m.vy + 3.0F : 6.0F;
         m.ground = false;
      }

      int col = this.mobColor(m);

      for (int i = 0; i < 5; i++) {
         this.particle(m.x, m.y + m.h / 2.0F, (this.rnd.nextFloat() - 0.5F) * 7.0F + dir * 2.0F, this.rnd.nextFloat() * 6.0F, col, 0.12F, P_DUST, 0.7F);
      }

      if (m.hp <= 0.0F) {
         this.kill(m);
      }
   }

   private int mobColor(Mob m) {
      return switch (m.type) {
         case SLIME -> m.color;
         case ZOMBIE -> 0xFF5A8A3A;
         case BUNNY -> 0xFFE0E0E0;
         default -> 0xFFC02020;
      };
   }

   private void kill(Mob m) {
      m.dead = true;
      int col = this.mobColor(m);
      float cy = m.y + m.h / 2.0F;
      int n = m.type == BOSS ? 60 : 14;

      for (int i = 0; i < n; i++) {
         float s = m.type == BOSS ? 18.0F : 8.0F;
         this.particle(m.x, cy, (this.rnd.nextFloat() - 0.5F) * s, (this.rnd.nextFloat() - 0.2F) * s, i % 4 == 0 ? 0xFFFFFFFF : col, m.type == BOSS ? 0.3F : 0.16F, i % 3 == 0 ? P_GORE : P_DUST, 1.2F);
      }

      switch (m.type) {
         case SLIME -> {
            this.coins(m.x, cy, 25 + this.rnd.nextInt(20));
            this.itemDrop(m.x, cy, "item/slime_ball", "Glibber", 1 + this.rnd.nextInt(2));
            this.sound(S_SLIME, 0.5F, 0.7F);
         }
         case ZOMBIE -> {
            this.coins(m.x, cy, 60 + this.rnd.nextInt(40));
            if (this.rnd.nextFloat() < 0.5F) {
               this.itemDrop(m.x, cy, "item/rotten_flesh", "Verrottetes Fleisch", 1);
            }

            this.sound(S_KILL, 0.5F, 0.8F);
         }
         case EYE -> {
            this.coins(m.x, cy, 75 + this.rnd.nextInt(30));
            if (this.rnd.nextFloat() < 0.35F) {
               this.itemDrop(m.x, cy, "item/ender_eye", "Linse", 1);
            }

            this.sound(S_KILL, 0.5F, 1.2F);
         }
         case BOSS -> {
            this.coins(m.x, cy, 3000);
            this.itemDrop(m.x, cy, "item/diamond", "Diamant", 5);
            this.itemDrop(m.x, cy, "item/ender_eye", "Linse", 3);
            this.message("Das Auge von Cthulhu wurde besiegt!", BOSS_COLOR);
            this.sound(S_BOSS_DIE, 0.9F, 0.8F);
            this.boss = null;

            for (Mob o : this.mobs) {
               if (o.type == SERVANT) {
                  o.dead = true;
               }
            }
         }
         default -> this.sound(S_KILL, 0.3F, 1.4F);
      }
   }

   // ================================================================== Pfeile, Beute, Effekte

   private void updateArrows(float dt) {
      Iterator<Arrow> it = this.arrows.iterator();

      while (it.hasNext()) {
         Arrow a = it.next();
         a.life -= dt;
         if (a.life <= 0.0F) {
            it.remove();
            continue;
         }

         if (a.stuck) {
            continue;
         }

         a.vy -= 9.0F * dt;
         a.x += a.vx * dt;
         a.y += a.vy * dt;
         if (this.solid((int)Math.floor(a.x), (int)Math.floor(a.y))) {
            a.stuck = true;
            a.life = Math.min(a.life, 1.2F);
            continue;
         }

         for (Mob m : this.mobs) {
            if (!m.dead && m.type != BUNNY && Math.abs(a.x - m.x) < m.hw + 0.2F && a.y > m.y - 0.2F && a.y < m.y + m.h + 0.2F) {
               this.damage(m, 18.0F + this.rnd.nextFloat() * 8.0F, a.vx > 0.0F ? 1 : -1);
               it.remove();
               break;
            }
         }
      }
   }

   private void coins(float x, float y, int value) {
      int left = value;

      while (left > 0) {
         int v;
         String spr;
         if (left >= 1000) {
            v = 1000;
            spr = "p/coin_gold";
         } else if (left >= 100) {
            v = 100;
            spr = "p/coin_silver";
         } else {
            v = Math.min(left, 25);
            spr = "p/coin_copper";
         }

         left -= v;
         Drop d = new Drop();
         d.x = x;
         d.y = y;
         d.vx = (this.rnd.nextFloat() - 0.5F) * 6.0F;
         d.vy = 4.0F + this.rnd.nextFloat() * 6.0F;
         d.sprite = spr;
         d.coins = v;
         this.drops.add(d);
      }
   }

   private void itemDrop(float x, float y, String sprite, String name, int count) {
      Drop d = new Drop();
      d.x = x;
      d.y = y;
      d.vx = (this.rnd.nextFloat() - 0.5F) * 5.0F;
      d.vy = 6.0F;
      d.sprite = sprite;
      d.name = name;
      d.count = count;
      this.drops.add(d);
   }

   private void updateDrops(float dt) {
      Hero h = this.hero;
      Iterator<Drop> it = this.drops.iterator();

      while (it.hasNext()) {
         Drop d = it.next();
         d.age += dt;
         float dx = h.x - d.x;
         float dy = h.y + 1.2F - d.y;
         float dist = (float)Math.hypot(dx, dy);
         if (d.age > 0.5F && dist < 5.5F && h.death <= 0.0F) {
            // zum Helden gezogen
            d.vx += dx / dist * 50.0F * dt;
            d.vy += dy / dist * 50.0F * dt;
            d.vx *= 0.93F;
            d.vy *= 0.93F;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            if (dist < 0.8F) {
               it.remove();
               this.collect(d);
            }

            continue;
         }

         d.vy = Math.max(-20.0F, d.vy - 30.0F * dt);
         float nx = d.x + d.vx * dt;
         float ny = d.y + d.vy * dt;
         if (this.solid((int)Math.floor(nx), (int)Math.floor(d.y))) {
            d.vx = -d.vx * 0.3F;
            nx = d.x;
         }

         if (this.solid((int)Math.floor(nx), (int)Math.floor(ny))) {
            ny = (float)Math.floor(d.y);
            d.vy = 0.0F;
            d.vx *= 0.8F;
         }

         d.x = nx;
         d.y = Math.max(ny, 1.0F);
         if (d.age > 40.0F || Math.abs(d.x - h.x) > 50.0F) {
            it.remove();
         }
      }
   }

   private void collect(Drop d) {
      Hero h = this.hero;
      if (d.coins > 0) {
         h.coins += d.coins;
         this.sound(S_COIN, 0.3F, 1.0F + this.rnd.nextFloat() * 0.4F);
         long c = d.coins;
         String name = c >= 1000 ? "Goldmünze" : (c >= 100 ? "Silbermünze" : "Kupfermünze");
         this.pickup(name, c >= 1000 ? (int)(c / 1000) : (c >= 100 ? (int)(c / 100) : (int)c), c >= 1000 ? 0xFFFFD84A : (c >= 100 ? 0xFFE0E4EC : 0xFFF09A60));
      } else {
         this.sound(S_COIN, 0.2F, 1.6F);
         this.pickup(d.name, d.count, 0xFFFFFFFF);
      }
   }

   /** Aufsammel-Text über dem Helden; gleicher Name zählt hoch. */
   private void pickup(String name, int count, int color) {
      Hero h = this.hero;

      for (Popup p : this.popups) {
         if (name.equals(p.key) && p.life > 0.5F) {
            p.count += count;
            p.text = p.count > 1 ? name + " (" + p.count + ")" : name;
            p.life = 1.6F;
            return;
         }
      }

      Popup p = this.popup(h.x, h.y + 3.4F, count > 1 ? name + " (" + count + ")" : name, color, 0.75F);
      p.key = name;
      p.count = count;
      p.life = 1.6F;
   }

   private Popup popup(float x, float y, String text, int color, float scale) {
      Popup p = new Popup();
      p.x = x + (this.rnd.nextFloat() - 0.5F) * 0.6F;
      p.y = y;
      p.text = text;
      p.color = color;
      p.scale = scale;
      this.popups.add(p);
      if (this.popups.size() > 30) {
         this.popups.remove(0);
      }

      return p;
   }

   private void chips(float x, float y, String tex, int n) {
      for (int i = 0; i < n; i++) {
         Particle p = this.particle(x + (this.rnd.nextFloat() - 0.5F) * 0.8F, y + (this.rnd.nextFloat() - 0.5F) * 0.8F, (this.rnd.nextFloat() - 0.5F) * 5.0F, this.rnd.nextFloat() * 5.0F, -1, 0.14F, P_DUST, 0.6F);
         p.tex = tex;
         p.texOff = this.rnd.nextInt(12);
      }
   }

   private Particle particle(float x, float y, float vx, float vy, int color, float size, int kind, float life) {
      Particle p = new Particle();
      p.x = x;
      p.y = y;
      p.vx = vx;
      p.vy = vy;
      p.color = color;
      p.size = size;
      p.kind = kind;
      p.life = life;
      p.max = life;
      this.particles.add(p);
      if (this.particles.size() > 500) {
         this.particles.remove(0);
      }

      return p;
   }

   private void message(String s, int color) {
      this.messages.add(new Message(s, color));
      if (this.messages.size() > 6) {
         this.messages.remove(0);
      }
   }

   private void updateFx(float dt) {
      Iterator<Particle> it = this.particles.iterator();

      while (it.hasNext()) {
         Particle p = it.next();
         p.life -= dt;
         if (p.life <= 0.0F) {
            it.remove();
            continue;
         }

         if (p.kind != P_GLOW) {
            p.vy -= 22.0F * dt;
         }

         p.x += p.vx * dt;
         p.y += p.vy * dt;
         if (p.kind != P_GLOW && this.solid((int)Math.floor(p.x), (int)Math.floor(p.y))) {
            p.y = (float)Math.floor(p.y) + 1.0F;
            p.vy = 0.0F;
            p.vx *= 0.6F;
         }
      }

      Iterator<Popup> pi = this.popups.iterator();

      while (pi.hasNext()) {
         Popup p = pi.next();
         p.life -= dt;
         p.y += dt * 1.6F;
         if (p.life <= 0.0F) {
            pi.remove();
         }
      }

      Iterator<Message> mi = this.messages.iterator();

      while (mi.hasNext()) {
         Message m = mi.next();
         m.life -= dt;
         if (m.life <= 0.0F) {
            mi.remove();
         }
      }
   }

   // ================================================================== Türen und NPCs

   private void updateDoors(float dt) {
      Hero h = this.hero;

      for (TerraWorld.House house : this.world.houses) {
         boolean nearL = Math.abs(h.x - (house.x0 + 0.5F)) < 2.4F && Math.abs(h.y - house.floor) < 3.0F;
         boolean nearR = Math.abs(h.x - (house.x0 + TerraWorld.HOUSE_W - 0.5F)) < 2.4F && Math.abs(h.y - house.floor) < 3.0F;
         if (nearL != house.openLeft || nearR != house.openRight) {
            if (Math.abs(h.x - house.x0) < 20.0F) {
               this.sound(S_DOOR, 0.35F, nearL || nearR ? 1.0F : 0.9F);
            }
         }

         house.openLeft = nearL;
         house.openRight = nearR;
         house.doorAnimL += ((nearL ? 1.0F : 0.0F) - house.doorAnimL) * Math.min(1.0F, dt * 12.0F);
         house.doorAnimR += ((nearR ? 1.0F : 0.0F) - house.doorAnimR) * Math.min(1.0F, dt * 12.0F);
         boolean has = false;

         for (Npc n : this.npcs) {
            if (n.house == house) {
               has = true;
               break;
            }
         }

         if (!has) {
            Npc n = new Npc(house);
            n.x = house.x0 + 6.5F;
            n.y = house.floor;
            n.skin = house.skin;
            n.name = NPC_NAMES[house.skin % NPC_NAMES.length];
            n.target = n.x;
            this.npcs.add(n);
         }
      }

      this.npcs.removeIf(n -> !this.world.houses.contains(n.house));
   }

   private void updateNpcs(float dt) {
      Hero h = this.hero;

      for (Npc n : this.npcs) {
         float dxh = h.x - n.x;
         n.bubbleT = Math.max(0.0F, n.bubbleT - dt);
         if (!n.greeted && Math.abs(dxh) < 8.0F && Math.abs(h.y - n.y) < 4.0F) {
            n.greeted = true;
            n.bubble = NPC_LINES[this.rnd.nextInt(NPC_LINES.length)];
            n.bubbleT = 5.0F;
         }

         if (Math.abs(dxh) < 6.0F) {
            n.dir = dxh > 0.0F ? 1 : -1;
            n.walk *= 0.8F;
            continue;
         }

         n.wait -= dt;
         if (n.wait <= 0.0F && Math.abs(n.target - n.x) < 0.1F) {
            n.target = n.house.x0 + 1.8F + this.rnd.nextFloat() * (TerraWorld.HOUSE_W - 3.6F);
            n.wait = 2.0F + this.rnd.nextFloat() * 4.0F;
         }

         float d = n.target - n.x;
         if (Math.abs(d) > 0.1F && n.wait <= 0.0F) {
            float s = Math.signum(d) * Math.min(Math.abs(d), 1.4F * dt);
            n.x += s;
            n.dir = s > 0.0F ? 1 : -1;
            n.walk += Math.abs(s) * 2.1F;
         } else {
            n.walk *= 0.8F;
         }
      }
   }
}
