package com.lennart.nebula.afk.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zeichnet die Sternenschlacht: Planet und Sterne, Schiffe/Jäger/Laser/Explosionen in Tiefenreihenfolge, darüber die
 * vorberechnete Brücke, live eingefärbt durch Fensterlicht, Explosionsblitze und Alarm. Liefert die Position, an der
 * der Spieler auf dem Steg steht.
 */
public final class SpaceView {
   public static final String BRIDGE = "bridge";
   public static final String WINDOW_LIGHT = "window_light";
   public static final String ALARM_LIGHT = "alarm_light";
   public static final String PLANET = "planet";
   public static final String PLANET_GLOW = "planet_glow";
   private static final int[] BLINK = {-1, -45240, -11534512, -11880961};
   private final Cam cam = BridgeTracer.CAM;
   private final ShipSprites sprites;
   private final List<float[]> blinkers;
   private final float[] planetLight;
   private final Random rnd = new Random();
   private final List<float[]> sparks = new ArrayList<>();
   private final float[] p = new float[3];
   private final float[] q = new float[3];
   private float lastShake;
   // aktuelle Abbildung virtuell → Bildschirm
   private float s;
   private float ox;
   private float oy;
   /** Spieler (Bildschirmkoordinaten): Mitte, Fußhöhe, Pixel pro Block. */
   public float playerX;
   public float playerFeetY;
   public float playerBlock;

   public SpaceView(ShipSprites sprites, List<float[]> blinkers, float[] planetLight) {
      this.sprites = sprites;
      this.blinkers = blinkers;
      this.planetLight = planetLight;
   }

   public static String shipId(int type) {
      return "ship_" + type;
   }

   private float sx(float vx) {
      return this.ox + vx * this.s;
   }

   private float sy(float vy) {
      return this.oy + vy * this.s;
   }

   private static int alpha(int rgb, float a) {
      return Tex.clamp255(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | rgb & 16777215;
   }

   public void render(SpaceCanvas c, int width, int height, SpaceBattle b, float time, boolean cameraMove, float fade) {
      float swayX = cameraMove ? (float)(Math.sin(time * 0.21) * 3.0 + Math.sin(time * 0.13 + 1.0) * 2.0) : 0.0F;
      float swayY = cameraMove ? (float)(Math.sin(time * 0.17 + 2.0) * 2.0) : 0.0F;
      float shake = b.shake * b.shake;
      swayX += (this.rnd.nextFloat() - 0.5F) * 14.0F * shake;
      swayY += (this.rnd.nextFloat() - 0.5F) * 10.0F * shake;
      this.s = Math.max(width / (float)BridgeTracer.VW, height / (float)BridgeTracer.VH) * 1.04F;
      this.ox = (width - BridgeTracer.VW * this.s) / 2.0F + swayX * this.s;
      this.oy = (height - BridgeTracer.VH * this.s) / 2.0F + swayY * this.s;
      float fullW = BridgeTracer.VW * this.s;
      float fullH = BridgeTracer.VH * this.s;
      float cx = this.ox + fullW / 2.0F;
      float cy = this.oy + fullH / 2.0F;
      // 1) Weltraum und Planet
      c.image(PLANET, cx, cy, fullW, fullH, 0.0F, 0.0F, PlanetPainter.W, PlanetPainter.H, 0.0F, false, -1);
      float pulse = 0.45F + 0.25F * (float)Math.sin(time * 0.7) + 0.1F * (float)Math.sin(time * 2.3);
      c.image(PLANET_GLOW, cx, cy, fullW, fullH, 0.0F, 0.0F, PlanetPainter.W, PlanetPainter.H, 0.0F, false, alpha(16777215, pulse));
      // 2) Objekte von hinten nach vorne
      List<Object[]> draw = new ArrayList<>();

      for (SpaceBattle.Ship ship : b.ships) {
         if (this.cam.project(ship.x, ship.y, ship.z, this.p)) {
            draw.add(new Object[]{this.p[2], ship});
         }
      }

      for (SpaceBattle.Wreck w : b.wrecks) {
         if (this.cam.project(w.x, w.y, w.z, this.p)) {
            draw.add(new Object[]{this.p[2], w});
         }
      }

      for (SpaceBattle.Fighter f : b.fighters) {
         if (f.respawn < 0.0F && this.cam.project(f.x, f.y, f.z, this.p)) {
            draw.add(new Object[]{this.p[2], f});
         }
      }

      for (SpaceBattle.Bolt bolt : b.bolts) {
         if (this.cam.project(bolt.x, bolt.y, bolt.z, this.p)) {
            draw.add(new Object[]{this.p[2], bolt});
         }
      }

      for (SpaceBattle.Blast blast : b.blasts) {
         if (this.cam.project(blast.x, blast.y, blast.z, this.p)) {
            draw.add(new Object[]{this.p[2] - blast.size, blast});
         }
      }

      draw.sort((x, y) -> Float.compare((Float)y[0], (Float)x[0]));

      for (Object[] o : draw) {
         Object obj = o[1];
         if (obj instanceof SpaceBattle.Ship ship) {
            this.drawShip(c, ship, time);
         } else if (obj instanceof SpaceBattle.Wreck w) {
            this.drawWreck(c, w, time);
         } else if (obj instanceof SpaceBattle.Fighter f) {
            this.drawFighter(c, f, time);
         } else if (obj instanceof SpaceBattle.Bolt bolt) {
            this.drawBolt(c, bolt);
         } else if (obj instanceof SpaceBattle.Blast blast) {
            this.drawBlast(c, blast);
         }
      }

      // 3) Brücke, live beleuchtet
      c.image(BRIDGE, cx, cy, fullW, fullH, 0.0F, 0.0F, BridgeTracer.VW, BridgeTracer.VH, 0.0F, false, -1);
      int planetRgb = Tex.argb(0, this.planetLight[0] * 255.0F, this.planetLight[1] * 200.0F, this.planetLight[2] * 170.0F);
      c.image(WINDOW_LIGHT, cx, cy, fullW, fullH, 0.0F, 0.0F, BridgeTracer.VW, BridgeTracer.VH, 0.0F, false, alpha(planetRgb, 0.07F + 0.05F * pulse));
      if (b.flash > 0.01F) {
         c.image(WINDOW_LIGHT, cx, cy, fullW, fullH, 0.0F, 0.0F, BridgeTracer.VW, BridgeTracer.VH, 0.0F, false, alpha(b.flashColor, b.flash * 0.42F));
      }

      if (b.alarm > 0.0F) {
         float a = (0.5F + 0.5F * (float)Math.sin(time * 6.5)) * Math.min(1.0F, b.alarm);
         c.image(ALARM_LIGHT, cx, cy, fullW, fullH, 0.0F, 0.0F, BridgeTracer.VW, BridgeTracer.VH, 0.0F, false, alpha(16716820, 0.55F * a));
         c.fill(0.0F, 0.0F, width, height, alpha(16711680, 0.06F * a));
      }

      // Blinklampen
      for (float[] l : this.blinkers) {
         float v = (float)Math.sin(time * (1.3F + l[4] * 0.4F) + l[4]);
         int col = b.alarm > 0.0F && l[3] == 0.0F ? -45240 : BLINK[(int)l[3]];
         float a = v > 0.1F ? 0.95F : 0.25F;
         float x = this.sx(l[0]);
         float y = this.sy(l[1]);
         float r = l[2] * this.s;
         c.fill(x - r, y - r * 0.6F, x + r, y + r * 0.6F, alpha(col, a));
      }

      // Funken bei Treffern am eigenen Schiff
      if (b.shake > this.lastShake + 0.3F) {
         for (int k = 0; k < 46; k++) {
            this.sparks.add(new float[]{this.rnd.nextFloat() * BridgeTracer.VW, 30.0F + this.rnd.nextFloat() * 90.0F, (this.rnd.nextFloat() - 0.5F) * 120.0F, this.rnd.nextFloat() * 60.0F, 0.0F, 0.8F + this.rnd.nextFloat() * 1.2F});
         }
      }

      this.lastShake = b.shake;
      this.updateSparks(c);
      // Spieler-Anker
      if (this.cam.project(BridgeTracer.PLAYER_X, BridgeTracer.PLAYER_Y, BridgeTracer.PLAYER_Z, this.p)) {
         this.playerX = this.sx(this.p[0]);
         this.playerFeetY = this.sy(this.p[1]);
         this.playerBlock = this.cam.scale(this.p[2]) * this.s;
      }

      if (fade < 1.0F) {
         c.fill(0.0F, 0.0F, width, height, alpha(0, 1.0F - fade));
      }
   }

   private long lastNanos = System.nanoTime();

   private void updateSparks(SpaceCanvas c) {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - this.lastNanos) / 1.0E9F);
      this.lastNanos = now;

      for (int i = this.sparks.size() - 1; i >= 0; i--) {
         float[] k = this.sparks.get(i);
         k[4] += dt;
         if (k[4] > k[5]) {
            this.sparks.remove(i);
         } else {
            k[3] += 420.0F * dt;
            k[0] += k[2] * dt;
            k[1] += k[3] * dt;
            float life = 1.0F - k[4] / k[5];
            float x = this.sx(k[0]);
            float y = this.sy(k[1]);
            float r = 1.6F * this.s;
            c.fill(x - r, y - r, x + r, y + r, alpha(life > 0.5F ? -2600 : -26368, life));
         }
      }
   }

   // ------------------------------------------------------------------ Objekte

   private int distanceTint(float depth) {
      float t = Math.min(1.0F, Math.max(0.0F, (depth - 600.0F) / 3000.0F));
      int v = Math.round(255.0F - t * 70.0F);
      int b = Math.round(255.0F - t * 45.0F);
      return 0xFF000000 | v << 16 | v << 8 | b;
   }

   private void drawShip(SpaceCanvas c, SpaceBattle.Ship ship, float time) {
      ShipSprites.Sprite sp = this.sprites.sprites[ship.type];
      this.cam.project(ship.x, ship.y, ship.z, this.p);
      float k = this.cam.scale(this.p[2]) / sp.pxPerBlock * this.s;
      float w = sp.image.w * k;
      float h = sp.image.h * k;
      float x = this.sx(this.p[0]);
      float y = this.sy(this.p[1]);
      boolean flip = !ship.right();
      float appear = 1.0F - ship.arrive;
      if (ship.arrive > 0.0F) {
         // Sprung aus dem Hyperraum: Lichtstreifen, dann Blitz
         float dir = ship.right() ? -1.0F : 1.0F;
         float streak = w * (1.0F + ship.arrive * 6.0F);
         c.line(x + dir * streak, y, x, y, Math.max(1.0F, h * 0.12F * ship.arrive), alpha(14738687, ship.arrive));
         if (ship.arrive < 0.35F) {
            float r = h * 0.8F * (ship.arrive / 0.35F);
            c.fill(x - r, y - r, x + r, y + r, alpha(16777215, ship.arrive * 1.6F));
         }
      }

      if (appear > 0.02F) {
         c.image(shipId(ship.type), x, y, w, h, 0.0F, 0.0F, sp.image.w, sp.image.h, 0.0F, flip, alpha(this.distanceTint(this.p[2]), appear));
      }

      // Triebwerke
      int eng = ship.faction == SpaceBattle.MEOWL ? 7393535 : 8454000;

      for (float[] e : sp.engines) {
         float ex = x + (e[0] - sp.image.w / 2.0F) * k * (flip ? -1 : 1);
         float ey = y + (e[1] - sp.image.h / 2.0F) * k;
         float r = Math.max(1.2F, 7.0F * k);
         float fl = 0.75F + 0.25F * (float)Math.sin(time * 23.0F + e[0]);
         c.fill(ex - r * 1.8F, ey - r * 1.2F, ex + r * 1.8F, ey + r * 1.2F, alpha(eng, 0.22F * fl * appear));
         c.fill(ex - r * 0.8F, ey - r * 0.6F, ex + r * 0.8F, ey + r * 0.6F, alpha(15269887, 0.8F * fl * appear));
      }

      // Brände
      for (float[] f : ship.fires) {
         float fx = x + (f[0] - 0.5F) * w * (flip ? -1 : 1);
         float fy = y + (f[1] - 0.5F) * h;
         this.fire(c, fx, fy, Math.max(1.5F, k * 9.0F), time + f[0] * 10.0F, f[2]);
      }
   }

   private void fire(SpaceCanvas c, float x, float y, float r, float time, float age) {
      float flick = 0.7F + 0.3F * (float)Math.sin(time * 17.0F);
      c.fill(x - r, y - r * 0.8F, x + r, y + r * 0.8F, alpha(16737826, 0.85F * flick));
      c.fill(x - r * 0.5F, y - r * 0.4F, x + r * 0.5F, y + r * 0.4F, alpha(16770688, flick));
      // Rauch zieht weg
      for (int i = 0; i < 3; i++) {
         float t = (time * 0.4F + i / 3.0F) % 1.0F;
         float sx = x + t * r * 3.0F;
         float sy = y - t * r * 4.0F;
         float sr = r * (0.6F + t * 1.2F);
         c.fill(sx - sr, sy - sr, sx + sr, sy + sr, alpha(4210752, 0.45F * (1.0F - t)));
      }
   }

   private void drawWreck(SpaceCanvas c, SpaceBattle.Wreck wr, float time) {
      ShipSprites.Sprite sp = this.sprites.sprites[wr.type];
      this.cam.project(wr.x, wr.y, wr.z, this.p);
      float k = this.cam.scale(this.p[2]) / sp.pxPerBlock * this.s;
      int half = sp.image.w / 2;
      float w = half * k;
      float h = sp.image.h * k;
      float x = this.sx(this.p[0]);
      float y = this.sy(this.p[1]);
      boolean flip = !wr.right;
      float u0 = wr.front ? half : 0.0F;
      float a = wr.age > 14.0F ? Math.max(0.0F, 1.0F - (wr.age - 14.0F) / 8.0F) : 1.0F;
      c.image(shipId(wr.type), x, y, w, h, u0, 0.0F, u0 + half, sp.image.h, wr.rot, flip, alpha(9864816, a));
      this.fire(c, x, y, Math.max(2.0F, k * 14.0F), time + wr.x, wr.age);
   }

   private void drawFighter(SpaceCanvas c, SpaceBattle.Fighter f, float time) {
      ShipSprites.Sprite sp = this.sprites.sprites[f.faction == SpaceBattle.MEOWL ? ShipSprites.FIGHTER_A : ShipSprites.FIGHTER_B];
      this.cam.project(f.x, f.y, f.z, this.p);
      this.cam.project(f.x + f.vx * 0.05F, f.y + f.vy * 0.05F, f.z + f.vz * 0.05F, this.q);
      float x = this.sx(this.p[0]);
      float y = this.sy(this.p[1]);
      float dx = this.q[0] - this.p[0];
      float dy = this.q[1] - this.p[1];
      float k = this.cam.scale(this.p[2]) / sp.pxPerBlock * this.s;
      float w = sp.image.w * k;
      float h = sp.image.h * k;
      int eng = f.faction == SpaceBattle.MEOWL ? 7393535 : 8454000;
      // Triebwerksspur
      float tl = (float)Math.sqrt(dx * dx + dy * dy) + 0.001F;
      float trail = Math.min(w * 1.6F, 8.0F * this.s + w * 0.8F);
      float tw = Math.max(1.0F, Math.min(h * 0.1F, 2.5F * this.s));
      float bx = x - dx / tl * w * 0.4F;
      float by = y - dy / tl * w * 0.4F;
      c.line(bx - dx / tl * trail, by - dy / tl * trail, bx - dx / tl * trail * 0.5F, by - dy / tl * trail * 0.5F, tw, alpha(eng, 0.12F));
      c.line(bx - dx / tl * trail * 0.5F, by - dy / tl * trail * 0.5F, bx, by, tw, alpha(eng, 0.3F));
      if (w < 3.0F) {
         float r = Math.max(0.8F, w * 0.5F);
         c.fill(x - r, y - r, x + r, y + r, alpha(f.faction == SpaceBattle.MEOWL ? 14211288 : 6316128, 1.0F));
      } else {
         boolean flip = dx < 0.0F;
         float angle = (float)Math.atan2(dy, Math.abs(dx));
         angle = Math.max(-0.7F, Math.min(0.7F, angle)) * (flip ? -1 : 1);
         c.image(shipId(sp == this.sprites.sprites[ShipSprites.FIGHTER_A] ? ShipSprites.FIGHTER_A : ShipSprites.FIGHTER_B), x, y, w, h, 0.0F, 0.0F, sp.image.w, sp.image.h, angle, flip, this.distanceTint(this.p[2]));
      }
   }

   private void drawBolt(SpaceCanvas c, SpaceBattle.Bolt bolt) {
      if (this.cam.project(bolt.x, bolt.y, bolt.z, this.p) && this.cam.project(bolt.x - bolt.dx * bolt.len, bolt.y - bolt.dy * bolt.len, bolt.z - bolt.dz * bolt.len, this.q)) {
         int col = bolt.faction == SpaceBattle.MEOWL ? (bolt.heavy ? 8375551 : 11591679) : (bolt.heavy ? 8191834 : 12320660);
         float scale = this.cam.scale(this.p[2]) * this.s;
         float width = Math.max(1.0F, Math.min(7.0F * this.s, (bolt.heavy ? 3.0F : 1.2F) * scale));
         float x1 = this.sx(this.q[0]);
         float y1 = this.sy(this.q[1]);
         float x2 = this.sx(this.p[0]);
         float y2 = this.sy(this.p[1]);
         c.line(x1, y1, x2, y2, width * 2.4F, alpha(col, 0.3F));
         c.line(x1, y1, x2, y2, width, alpha(col, 1.0F));
         c.line(x1, y1, x2, y2, Math.max(0.6F, width * 0.4F), alpha(15400959, 1.0F));
      }
   }

   private static final int[] FIRE = {-1, -2400, -12256, -26368, -50384, -10080728, -12105913};

   private void drawBlast(SpaceCanvas c, SpaceBattle.Blast blast) {
      this.cam.project(blast.x, blast.y, blast.z, this.p);
      float x = this.sx(this.p[0]);
      float y = this.sy(this.p[1]);
      float radius = blast.size * this.cam.scale(this.p[2]) * this.s;
      float life = blast.age / blast.dur;
      float grow = 0.35F + 0.65F * (float)Math.sqrt(life);
      Random r = new Random(blast.seed);
      int puffs = blast.size > 20.0F ? 11 : 7;

      for (int i = 0; i < puffs; i++) {
         float ang = r.nextFloat() * 6.283F;
         float dist = r.nextFloat() * 0.75F * radius * grow;
         float px = x + (float)Math.cos(ang) * dist;
         float py = y + (float)Math.sin(ang) * dist - life * radius * 0.25F;
         float pr = radius * (0.35F + r.nextFloat() * 0.4F) * grow;
         float local = Math.min(1.0F, life + r.nextFloat() * 0.25F);
         int idx = Math.min(FIRE.length - 1, (int)(local * FIRE.length));
         float a = idx >= 5 ? 0.55F * (1.0F - life) : 1.0F - life * 0.4F;
         c.fill(px - pr, py - pr, px + pr, py + pr, alpha(FIRE[idx], a));
      }

      // Trümmer
      for (int i = 0; i < 8; i++) {
         float ang = r.nextFloat() * 6.283F;
         float dist = radius * (0.5F + life * 2.2F) * (0.6F + r.nextFloat() * 0.6F);
         float px = x + (float)Math.cos(ang) * dist;
         float py = y + (float)Math.sin(ang) * dist;
         float pr = Math.max(0.6F, radius * 0.07F);
         c.fill(px - pr, py - pr, px + pr, py + pr, alpha(life < 0.5F ? -6016 : 5263440, 1.0F - life));
      }

      if (life < 0.18F) {
         float fr = radius * 1.5F * (1.0F - life / 0.18F);
         c.fill(x - fr, y - fr, x + fr, y + fr, alpha(16777215, 0.35F * (1.0F - life / 0.18F)));
      }
   }
}
