package com.lennart.nebula.afk.valley;

import com.lennart.nebula.afk.space.Cam;
import com.lennart.nebula.afk.space.SpaceCanvas;
import com.lennart.nebula.afk.space.Tex;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zeichnet das Tal des Endes: vorberechnete Landschaft, animierter Wasserfall und Fluss, Gischt, Vögel und darüber
 * den kleinen Kampf mit allen Effekten – in Tiefenreihenfolge, hinter Statuen und Felsen verdeckt. Liefert die
 * Position des Spielers auf dem Plateau.
 */
public final class ValleyView {
   public static final String BG = "valley_bg";
   public static final String FALL = "valley_fall";
   public static final String RIVER = "valley_river";
   private final Cam cam = ValleyTracer.CAM;
   private final ValleyTracer world;
   private final Random rnd = new Random();
   private final List<float[]> mist = new ArrayList<>();
   private final float[] p = new float[3];
   private float s;
   private float ox;
   private float oy;
   private float mistTimer;
   // Projektion des zuletzt projizierten Punkts
   private float px;
   private float py;
   private float pk;
   private float pd;
   public float playerX;
   public float playerFeetY;
   public float playerBlock;

   public ValleyView(ValleyTracer world) {
      this.world = world;
   }

   private static int alpha(int rgb, float a) {
      return Tex.clamp255(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | rgb & 16777215;
   }

   private float sx(float vx) {
      return this.ox + vx * this.s;
   }

   private float sy(float vy) {
      return this.oy + vy * this.s;
   }

   /** Weltpunkt → Bildschirm (px, py), Pixel pro Block (pk), Abstand (pd). */
   private boolean proj(float x, float y, float z) {
      if (!this.cam.project(x, y, z, this.p)) {
         return false;
      } else {
         this.px = this.sx(this.p[0]);
         this.py = this.sy(this.p[1]);
         this.pk = this.cam.scale(this.p[2]) * this.s;
         float dx = x - this.cam.x;
         float dy = y - this.cam.y;
         float dz = z - this.cam.z;
         this.pd = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         return true;
      }
   }

   private static float dist(float x, float y, float z, Cam c) {
      float dx = x - c.x;
      float dy = y - c.y;
      float dz = z - c.z;
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Ist der Punkt von der Kamera aus sichtbar (nicht hinter Statue/Fels)? */
   private boolean visible(float x, float y, float z) {
      if (!this.cam.project(x, y, z, this.p)) {
         return false;
      } else {
         int ix = (int)this.p[0];
         int iy = (int)this.p[1];
         if (ix >= 0 && iy >= 0 && ix < ValleyTracer.VW && iy < ValleyTracer.VH) {
            float d = dist(x, y, z, this.cam);
            return d <= this.world.depth[iy * ValleyTracer.VW + ix] + 0.9F;
         } else {
            return true;
         }
      }
   }

   public void render(SpaceCanvas c, int width, int height, ValleyFight f, float time, float dt, boolean cameraMove, float fade) {
      float swayX = cameraMove ? (float)(Math.sin(time * 0.19) * 2.5 + Math.sin(time * 0.11 + 1.0) * 1.5) : 0.0F;
      float swayY = cameraMove ? (float)(Math.sin(time * 0.15 + 2.0) * 1.6) : 0.0F;
      float shake = f.shake * f.shake;
      swayX += (this.rnd.nextFloat() - 0.5F) * 12.0F * shake;
      swayY += (this.rnd.nextFloat() - 0.5F) * 9.0F * shake;
      this.s = Math.max(width / (float)ValleyTracer.VW, height / (float)ValleyTracer.VH) * 1.04F;
      this.ox = (width - ValleyTracer.VW * this.s) / 2.0F + swayX * this.s;
      this.oy = (height - ValleyTracer.VH * this.s) / 2.0F + swayY * this.s;
      float fullW = ValleyTracer.VW * this.s;
      float fullH = ValleyTracer.VH * this.s;
      // 1) Landschaft, Wasserfall und Fluss
      c.image(BG, this.ox + fullW / 2.0F, this.oy + fullH / 2.0F, fullW, fullH, 0.0F, 0.0F, ValleyTracer.VW, ValleyTracer.VH, 0.0F, false, -1);
      int[] fb = this.world.fallBox;
      int ff = (int)(time * 10.0F) % ValleyTracer.FALL_FRAMES;
      this.region(c, FALL, fb, ff, -1);
      int[] rb = this.world.riverBox;
      float rt = time / 0.4F;
      int r0 = (int)Math.floor(rt) % ValleyTracer.RIVER_FRAMES;
      int r1 = (r0 + 1) % ValleyTracer.RIVER_FRAMES;
      float rf = rt - (float)Math.floor(rt);
      this.region(c, RIVER, rb, r0, -1);
      this.region(c, RIVER, rb, r1, alpha(16777215, rf));
      // Kirin: Himmel verdunkelt sich
      if (f.darken > 0.01F) {
         c.fill(0.0F, 0.0F, width, height, alpha(1052712, f.darken * 0.5F));
      }

      // 2) Gischt am Fuß des Wasserfalls, Vögel
      this.mist(c, dt);
      this.birds(c, time);
      // 3) Kampf in Tiefenreihenfolge
      List<Object[]> draw = new ArrayList<>();
      if (f.kurama > 0.01F) {
         draw.add(new Object[]{dist(f.naruto.x, f.naruto.y, f.naruto.z, this.cam) + 8.0F, "kurama"});
      }

      if (f.susanoo > 0.01F) {
         draw.add(new Object[]{dist(f.sasuke.x, f.sasuke.y, f.sasuke.z, this.cam) + 8.0F, "susanoo"});
      }

      for (ValleyFight.Fighter fi : new ValleyFight.Fighter[]{f.naruto, f.sasuke}) {
         if (fi.visible) {
            draw.add(new Object[]{dist(fi.x, fi.y, fi.z, this.cam), fi});
         }
      }

      for (ValleyFight.Clone cl : f.clones) {
         draw.add(new Object[]{dist(cl.x, cl.y, cl.z, this.cam), cl});
      }

      for (ValleyFight.Fx e : f.fx) {
         float d = dist(e.x, e.y, e.z, this.cam);
         if (e.type == ValleyFight.DOME) {
            d -= 6.0F;
         }

         draw.add(new Object[]{d, e});
      }

      draw.sort((a, b) -> Float.compare((Float)b[0], (Float)a[0]));

      for (Object[] o : draw) {
         Object obj = o[1];
         if (obj instanceof ValleyFight.Fighter fi) {
            this.fighter(c, fi, time, f);
         } else if (obj instanceof ValleyFight.Clone cl) {
            if (this.visible(cl.x, cl.y + 1.4F, cl.z) && this.proj(cl.x, cl.y, cl.z)) {
               this.figure(c, ValleyFight.NARUTO, ValleyFight.Pose.AIR, cl.face, this.px, this.py, this.pk, 0.92F, time);
            }
         } else if (obj instanceof ValleyFight.Fx e) {
            this.effect(c, e, time);
         } else if ("kurama".equals(obj)) {
            this.kurama(c, f.naruto, f.kurama, time);
         } else if ("susanoo".equals(obj)) {
            this.susanoo(c, f.sasuke, f.susanoo, time);
         }
      }

      // 4) Blitzlicht
      if (f.flash > 0.01F) {
         c.fill(0.0F, 0.0F, width, height, alpha(f.flashColor, f.flash * 0.65F));
      }

      // Spieler-Anker
      if (this.proj(ValleyTracer.PLAYER_X, ValleyTracer.PLAYER_Y, ValleyTracer.PLAYER_Z)) {
         this.playerX = this.px;
         this.playerFeetY = this.py;
         this.playerBlock = this.pk;
      }

      if (fade < 1.0F) {
         c.fill(0.0F, 0.0F, width, height, alpha(0, 1.0F - fade));
      }
   }

   /** Ausschnitt einer Animations-Bildfolge an ihrer Stelle im Bild zeichnen. */
   private void region(SpaceCanvas c, String id, int[] box, int frame, int tint) {
      if (box[2] > 1 && c.has(id)) {
         float x = this.sx(box[0]);
         float y = this.sy(box[1]);
         float w = box[2] * this.s;
         float h = box[3] * this.s;
         c.image(id, x + w / 2.0F, y + h / 2.0F, w, h, 0.0F, frame * box[3], box[2], (frame + 1) * box[3], 0.0F, false, tint);
      }
   }

   // ------------------------------------------------------------------ Umgebung

   private void mist(SpaceCanvas c, float dt) {
      this.mistTimer += dt;

      while (this.mistTimer > 0.11F) {
         this.mistTimer -= 0.11F;
         if (this.mist.size() < 90) {
            float x = (this.rnd.nextFloat() * 2.0F - 1.0F) * (ValleyTracer.FALL_HALF + 1.0F);
            this.mist.add(new float[]{x, this.rnd.nextFloat() * 1.5F, 66.0F + this.rnd.nextFloat() * 2.5F, 0.0F, 2.5F + this.rnd.nextFloat() * 1.8F, 1.0F + this.rnd.nextFloat() * 0.8F, (this.rnd.nextFloat() - 0.5F) * 1.2F, 0.7F + this.rnd.nextFloat() * 0.9F, -0.6F - this.rnd.nextFloat() * 1.6F});
         }
      }

      for (int i = this.mist.size() - 1; i >= 0; i--) {
         float[] m = this.mist.get(i);
         m[3] += dt;
         if (m[3] >= m[4]) {
            this.mist.remove(i);
         } else {
            m[0] += m[6] * dt;
            m[1] += m[7] * dt;
            m[2] += m[8] * dt;
            float life = m[3] / m[4];
            if (this.proj(m[0], m[1], m[2])) {
               float r = m[5] * (1.0F + life * 2.4F) * this.pk * 0.5F;
               float a = 0.2F * (float)Math.sin(life * Math.PI);
               c.fill(this.px - r, this.py - r, this.px + r, this.py + r, alpha(15661055, a));
            }
         }
      }
   }

   private void birds(SpaceCanvas c, float time) {
      for (int i = 0; i < 4; i++) {
         float period = 34.0F + i * 7.0F;
         float t = (time + i * 13.0F) % period / period;
         float x = -110.0F + t * 220.0F;
         float y = 66.0F + i * 4.0F + (float)Math.sin(time * 0.6F + i) * 2.0F;
         float z = 110.0F + i * 18.0F;
         if (i % 2 == 1) {
            x = -x;
         }

         if (this.proj(x, y, z)) {
            float w = Math.max(1.5F, this.pk * 1.4F);
            float flap = (float)Math.sin(time * 9.0F + i * 2.0F) * w * 0.6F;
            int col = alpha(3160124, 0.75F);
            c.line(this.px - w, this.py - flap, this.px, this.py, Math.max(1.0F, w * 0.25F), col);
            c.line(this.px, this.py, this.px + w, this.py - flap, Math.max(1.0F, w * 0.25F), col);
         }
      }
   }

   // ------------------------------------------------------------------ Figuren

   private void fighter(SpaceCanvas c, ValleyFight.Fighter fi, float time, ValleyFight f) {
      // Nachbilder
      for (int i = fi.trailCount - 1; i >= 0; i--) {
         float tx = fi.trail[i * 3];
         float ty = fi.trail[i * 3 + 1];
         float tz = fi.trail[i * 3 + 2];
         if (this.visible(tx, ty + 1.4F, tz) && this.proj(tx, ty, tz)) {
            this.figure(c, fi.who, ValleyFight.Pose.AIR, fi.face, this.px, this.py, this.pk, 0.32F * (1.0F - i / 8.0F), time);
         }
      }

      if (this.visible(fi.x, fi.y + 1.4F, fi.z) && this.proj(fi.x, fi.y, fi.z)) {
         float x = this.px;
         float y = this.py;
         float k = this.pk;
         float u = k * 1.8F / 16.0F;
         // Chakra-Aura (Kurama-Modus) bzw. dunkle Aura bei Sasukes Chidori
         if (fi.aura > 0.02F) {
            float fl = 0.75F + 0.25F * (float)Math.sin(time * 19.0F);
            float r = Math.max(2.0F, u * 4.2F);
            c.fill(x - r, y - u * 17.5F, x + r, y + u * 0.5F, alpha(16748064, 0.22F * fi.aura * fl));
            c.fill(x - r * 0.7F, y - u * 19.0F, x + r * 0.7F, y - u * 15.0F, alpha(16764006, 0.3F * fi.aura * fl));
         }

         if (fi.who == ValleyFight.SASUKE && fi.charge > 0.3F) {
            float r = Math.max(2.0F, u * 4.0F);
            c.fill(x - r, y - u * 17.0F, x + r, y + u * 0.5F, alpha(9067007, 0.16F * fi.charge));
         }

         this.figure(c, fi.who, fi.pose, fi.face, x, y, k, 1.0F, time);
         // Rasengan / Chidori an der Hand
         if (fi.charge > 0.02F) {
            float hx = x + fi.face * u * 4.6F;
            float hy = y - u * (fi.pose == ValleyFight.Pose.RAISE ? 20.0F : 8.0F);
            if (fi.who == ValleyFight.NARUTO) {
               this.rasengan(c, hx, hy, Math.max(1.6F, (0.22F + 0.55F * fi.charge) * k), time);
            } else {
               this.chidori(c, hx, hy, Math.max(1.4F, (0.18F + 0.4F * fi.charge) * k), time);
            }
         }
      }
   }

   private void rasengan(SpaceCanvas c, float x, float y, float r, float time) {
      this.disc(c, x, y, r * 2.0F, alpha(4892927, 0.22F));
      this.disc(c, x, y, r, alpha(7327999, 0.9F));

      for (int i = 0; i < 3; i++) {
         double a = time * 14.0 + i * 2.094;
         float ex = x + (float)Math.cos(a) * r * 0.85F;
         float ey = y + (float)Math.sin(a) * r * 0.55F;
         c.line(x, y, ex, ey, Math.max(0.8F, r * 0.22F), alpha(15400959, 0.85F));
      }

      this.disc(c, x, y, r * 0.42F, -1);
   }

   private void chidori(SpaceCanvas c, float x, float y, float r, float time) {
      float fl = 0.7F + 0.3F * (float)Math.sin(time * 41.0F);
      this.disc(c, x, y, r * 2.2F, alpha(10541311, 0.25F * fl));
      this.disc(c, x, y, r, alpha(13166847, 0.95F));
      this.disc(c, x, y, r * 0.5F, -1);
   }

   /**
    * Kleine Minecraft-artige Figur (16 Einheiten hoch = 1,8 Blöcke): Füße bei (x, y), Blickrichtung face.
    */
   private void figure(SpaceCanvas c, int who, ValleyFight.Pose pose, float face, float x, float y, float k, float a, float time) {
      float u = Math.max(0.5F, k * 1.8F / 16.0F);
      boolean n = who == ValleyFight.NARUTO;
      int hair = n ? 16241214 : 1710632;
      int skin = n ? 15911072 : 15716024;
      int top = n ? 15891230 : 3815760;
      int topDark = n ? 1973796 : 2763578;
      int legs = n ? 15034898 : 2894906;
      int feet = n ? 2503242 : 2763312;
      int belt = n ? 1973796 : 7229352;
      if (pose == ValleyFight.Pose.DOWN) {
         // liegend: Kopf in Blickrichtung
         float d = face;
         this.rect(c, x, y, u, d, -8.0F, -1.6F, -2.0F, 0.0F, legs, a);
         this.rect(c, x, y, u, d, -2.0F, -1.9F, 3.0F, 0.0F, top, a);
         this.rect(c, x, y, u, d, 3.0F, -1.8F, 6.5F, 0.0F, skin, a);
         this.rect(c, x, y, u, d, 5.0F, -2.2F, 7.2F, -0.4F, hair, a);
         return;
      }

      boolean crouch = pose == ValleyFight.Pose.CROUCH;
      float lift = crouch ? 3.0F : 0.0F;
      float lean = pose == ValleyFight.Pose.RUN ? 0.8F : (pose == ValleyFight.Pose.HURT ? -1.2F : 0.0F);
      // Beine
      if (pose == ValleyFight.Pose.AIR) {
         this.rect(c, x, y, u, face, -2.0F - 0.8F, -7.0F, -0.2F - 0.8F, -2.5F, legs, a);
         this.rect(c, x, y, u, face, 0.2F - 0.4F, -7.0F, 2.0F - 0.4F, -3.2F, legs, a);
      } else if (pose == ValleyFight.Pose.KICK) {
         this.rect(c, x, y, u, face, -2.0F, -6.0F, -0.2F, 0.0F, legs, a);
         this.rect(c, x, y, u, face, 0.0F, -6.0F, 6.0F, -4.4F, legs, a);
         this.rect(c, x, y, u, face, 5.2F, -6.2F, 6.6F, -4.2F, feet, a);
      } else if (pose == ValleyFight.Pose.RUN) {
         float st = (float)Math.sin(time * 22.0F) * 1.6F;
         this.rect(c, x, y, u, face, -2.0F + st, -6.0F, -0.2F + st, 0.0F, legs, a);
         this.rect(c, x, y, u, face, 0.2F - st, -6.0F, 2.0F - st, 0.0F, legs, a);
      } else {
         this.rect(c, x, y, u, face, -2.0F, -6.0F + lift, -0.2F, 0.0F, legs, a);
         this.rect(c, x, y, u, face, 0.2F, -6.0F + lift, 2.0F, 0.0F, legs, a);
         this.rect(c, x, y, u, face, -2.2F, -1.0F, -0.1F, 0.0F, feet, a);
         this.rect(c, x, y, u, face, 0.1F, -1.0F, 2.2F, 0.0F, feet, a);
      }

      float b = lift;
      // Oberkörper
      this.rect(c, x, y, u, face, -2.0F + lean, -11.0F + b, 2.0F + lean, -6.0F + b, top, a);
      this.rect(c, x, y, u, face, -2.0F + lean, -11.0F + b, 2.0F + lean, -9.6F + b, topDark, a);
      this.rect(c, x, y, u, face, -2.0F + lean, -6.8F + b, 2.0F + lean, -6.0F + b, belt, a);
      // Arme
      switch (pose) {
         case PUNCH:
            this.rect(c, x, y, u, face, -3.0F + lean, -11.0F + b, -2.0F + lean, -6.5F + b, top, a);
            this.rect(c, x, y, u, face, 1.5F + lean, -10.5F + b, 5.0F + lean, -9.0F + b, top, a);
            this.rect(c, x, y, u, face, 5.0F + lean, -10.6F + b, 6.2F + lean, -8.9F + b, skin, a);
            break;
         case THROW:
         case RAISE:
            this.rect(c, x, y, u, face, -3.0F + lean, -11.0F + b, -2.0F + lean, -6.5F + b, top, a);
            float tip = pose == ValleyFight.Pose.RAISE ? -19.5F : -15.0F;
            this.rect(c, x, y, u, face, 2.0F + lean, tip + b, 3.2F + lean, -10.0F + b, top, a);
            this.rect(c, x, y, u, face, 2.0F + lean, tip - 1.0F + b, 3.2F + lean, tip + b, skin, a);
            break;
         case SIGN:
            this.rect(c, x, y, u, face, 0.6F + lean, -9.6F + b, 3.0F + lean, -7.8F + b, top, a);
            this.rect(c, x, y, u, face, 2.6F + lean, -10.2F + b, 3.6F + lean, -7.6F + b, skin, a);
            break;
         case CHARGE:
            this.rect(c, x, y, u, face, -3.0F + lean, -11.0F + b, -2.0F + lean, -6.5F + b, top, a);
            this.rect(c, x, y, u, face, 1.5F + lean, -9.0F + b, 4.0F + lean, -7.4F + b, top, a);
            this.rect(c, x, y, u, face, 4.0F + lean, -9.1F + b, 5.0F + lean, -7.2F + b, skin, a);
            break;
         case HURT:
         case AIR:
            this.rect(c, x, y, u, face, -3.6F + lean, -12.5F + b, -2.0F + lean, -9.5F + b, top, a);
            this.rect(c, x, y, u, face, 2.0F + lean, -12.5F + b, 3.6F + lean, -9.5F + b, top, a);
            break;
         default:
            float sw = pose == ValleyFight.Pose.RUN ? (float)Math.sin(time * 22.0F) * 1.2F : 0.0F;
            this.rect(c, x, y, u, face, -3.0F + lean - sw, -11.0F + b, -2.0F + lean - sw, -6.5F + b, top, a);
            this.rect(c, x, y, u, face, 2.0F + lean + sw, -11.0F + b, 3.0F + lean + sw, -6.5F + b, top, a);
            this.rect(c, x, y, u, face, -3.0F + lean - sw, -6.5F + b, -2.0F + lean - sw, -5.6F + b, skin, a);
            this.rect(c, x, y, u, face, 2.0F + lean + sw, -6.5F + b, 3.0F + lean + sw, -5.6F + b, skin, a);
      }

      // Kopf und Haare
      float hl = lean * 1.3F;
      this.rect(c, x, y, u, face, -2.0F + hl, -15.0F + b, 2.0F + hl, -11.0F + b, skin, a);
      this.rect(c, x, y, u, face, 0.6F + hl, -13.6F + b, 1.4F + hl, -12.9F + b, n ? 2245802 : 1710632, a);
      if (n) {
         // Naruto: gelbe Stachelhaare, Stirnband
         this.rect(c, x, y, u, face, -2.4F + hl, -16.4F + b, 2.4F + hl, -14.2F + b, hair, a);
         this.rect(c, x, y, u, face, -2.8F + hl, -17.4F + b, -1.2F + hl, -15.6F + b, hair, a);
         this.rect(c, x, y, u, face, 0.2F + hl, -17.6F + b, 1.6F + hl, -15.8F + b, hair, a);
         this.rect(c, x, y, u, face, -2.2F + hl, -14.5F + b, 2.2F + hl, -13.9F + b, 1973796, a);
      } else {
         // Sasuke: dunkle Haare, hinten abstehend
         this.rect(c, x, y, u, face, -2.6F + hl, -16.2F + b, 2.2F + hl, -14.0F + b, hair, a);
         this.rect(c, x, y, u, face, -3.8F + hl, -16.6F + b, -1.6F + hl, -13.0F + b, hair, a);
         this.rect(c, x, y, u, face, 1.2F + hl, -14.6F + b, 2.4F + hl, -12.2F + b, hair, a);
      }
   }

   /** Rechteck in Figuren-Einheiten; x-Achse folgt der Blickrichtung. */
   private void rect(SpaceCanvas c, float x, float y, float u, float face, float x1, float y1, float x2, float y2, int rgb, float a) {
      float ax = face >= 0.0F ? x1 : -x2;
      float bx = face >= 0.0F ? x2 : -x1;
      c.fill(x + ax * u, y + y1 * u, x + bx * u, y + y2 * u, alpha(rgb, a));
   }

   /** Blockiger Kreis aus Zeilen. */
   private void disc(SpaceCanvas c, float x, float y, float r, int argb) {
      if (r < 1.5F) {
         c.fill(x - r, y - r, x + r, y + r, argb);
      } else {
         int rows = Math.max(3, Math.min(28, (int)(r / 1.5F)));
         float step = r * 2.0F / rows;

         for (int i = 0; i < rows; i++) {
            float yy = -r + (i + 0.5F) * step;
            float w = (float)Math.sqrt(Math.max(0.0F, r * r - yy * yy));
            c.fill(x - w, y + yy - step / 2.0F, x + w, y + yy + step / 2.0F, argb);
         }
      }
   }

   // ------------------------------------------------------------------ Avatare

   private void kurama(SpaceCanvas c, ValleyFight.Fighter n, float a, float time) {
      if (this.proj(n.x, n.y, n.z)) {
         float h = 27.0F * this.pk;
         // Naruto steht im Kopf/Brustbereich des Avatars
         float x = this.px - n.face * h * 0.12F;
         float y = this.py + h * 0.62F;
         float d = n.face;
         float fl = 0.85F + 0.15F * (float)Math.sin(time * 13.0F);
         int body = alpha(16747038, 0.3F * a * fl);
         int edge = alpha(16765024, 0.4F * a * fl);
         // neun Schwänze
         for (int t = 0; t < 9; t++) {
            float base = -1.25F + t * 0.31F;

            for (int s = 0; s < 11; s++) {
               float q = s / 10.0F;
               float ang = base + (float)Math.sin(time * 1.8F + t * 0.8F + q * 3.0F) * 0.35F * q;
               float len = h * (0.18F + q * 0.42F);
               float tx = x - d * (h * 0.18F + (float)Math.cos(ang) * len * 0.9F);
               float ty = y - h * 0.38F - (float)Math.sin(ang + 0.6F) * len * 0.7F;
               this.disc(c, tx, ty, h * (0.065F - q * 0.035F), body);
            }
         }

         // Körper, Kopf, Ohren, Augen
         this.ellipse(c, x, y - h * 0.4F, h * 0.25F, h * 0.3F, body);
         this.ellipse(c, x + d * h * 0.06F, y - h * 0.4F, h * 0.19F, h * 0.24F, edge);
         float hx = x + d * h * 0.14F;
         float hy = y - h * 0.78F;
         this.disc(c, hx, hy, h * 0.16F, body);
         this.ellipse(c, hx + d * h * 0.15F, hy + h * 0.04F, h * 0.09F, h * 0.06F, body);

         for (int e = -1; e <= 1; e += 2) {
            float ex = hx + e * h * 0.09F;

            for (int s = 0; s < 6; s++) {
               float w = h * (0.05F - s * 0.008F);
               c.fill(ex - w, hy - h * (0.12F + s * 0.035F), ex + w, hy - h * (0.09F + s * 0.035F), body);
            }
         }

         float eyeY = hy - h * 0.02F;
         c.fill(hx + d * h * 0.03F - h * 0.035F, eyeY - h * 0.012F, hx + d * h * 0.03F + h * 0.035F, eyeY + h * 0.012F, alpha(16773296, 0.85F * a));
         c.fill(hx + d * h * 0.12F - h * 0.025F, eyeY - h * 0.012F, hx + d * h * 0.12F + h * 0.025F, eyeY + h * 0.012F, alpha(16773296, 0.85F * a));
         // flackernde Chakra-Flammen am Rand
         Random r = new Random((long)(time * 12.0F));

         for (int i = 0; i < 26; i++) {
            float ang = r.nextFloat() * 6.283F;
            float fx = x + (float)Math.cos(ang) * h * 0.28F;
            float fy = y - h * 0.45F + (float)Math.sin(ang) * h * 0.36F;
            float w = h * (0.015F + r.nextFloat() * 0.02F);
            c.fill(fx - w, fy - w * 3.0F, fx + w, fy + w, alpha(16766816, 0.35F * a));
         }
      }
   }

   private void susanoo(SpaceCanvas c, ValleyFight.Fighter s, float a, float time) {
      if (this.proj(s.x, s.y, s.z)) {
         float h = 27.0F * this.pk;
         float x = this.px - s.face * h * 0.1F;
         float y = this.py + h * 0.66F;
         float d = s.face;
         float fl = 0.85F + 0.15F * (float)Math.sin(time * 11.0F + 1.0F);
         int body = alpha(10116351, 0.3F * a * fl);
         int light = alpha(14203135, 0.42F * a * fl);
         // Rock/Unterkörper
         for (int i = 0; i < 6; i++) {
            float q = i / 5.0F;
            float w = h * (0.2F + q * 0.08F);
            c.fill(x - w, y - h * (0.42F - q * 0.07F), x + w, y - h * (0.36F - q * 0.07F), alpha(10116351, 0.26F * a * (1.0F - q * 0.6F)));
         }

         // Brustkorb (Trapez) mit Rippen
         for (int i = 0; i < 8; i++) {
            float q = i / 7.0F;
            float w = h * (0.18F + q * 0.1F);
            float yy = y - h * (0.42F + q * 0.3F);
            c.fill(x - w, yy - h * 0.04F, x + w, yy, body);
            if (i % 2 == 0) {
               c.fill(x - w * 0.8F, yy - h * 0.012F, x + w * 0.8F, yy, light);
            }
         }

         // Schultern, Kopf mit Kamm
         this.ellipse(c, x - h * 0.27F, y - h * 0.73F, h * 0.09F, h * 0.06F, light);
         this.ellipse(c, x + h * 0.27F, y - h * 0.73F, h * 0.09F, h * 0.06F, light);
         float hx = x + d * h * 0.04F;
         float hy = y - h * 0.86F;
         this.disc(c, hx, hy, h * 0.1F, body);
         c.fill(hx - h * 0.02F, hy - h * 0.2F, hx + h * 0.02F, hy - h * 0.08F, light);
         c.line(hx, hy - h * 0.06F, hx + d * h * 0.16F, hy - h * 0.12F, Math.max(1.0F, h * 0.025F), light);
         c.fill(hx + d * h * 0.04F - h * 0.025F, hy - h * 0.01F, hx + d * h * 0.04F + h * 0.025F, hy + h * 0.012F, alpha(16764159, 0.9F * a));
         // Arm mit leuchtendem Schwert
         float shX = x + d * h * 0.22F;
         float shY = y - h * 0.7F;
         float hdX = x + d * h * 0.5F;
         float hdY = y - h * 0.58F;
         c.line(shX, shY, hdX, hdY, Math.max(1.5F, h * 0.07F), body);
         float swing = (float)Math.sin(time * 1.4F) * 0.15F;
         float tipX = hdX + d * h * (0.3F + swing);
         float tipY = hdY - h * (0.45F - swing);
         c.line(hdX, hdY, tipX, tipY, Math.max(3.0F, h * 0.06F), alpha(13145855, 0.65F * a));
         c.line(hdX, hdY, tipX, tipY, Math.max(1.0F, h * 0.018F), alpha(16117503, 0.85F * a));
         Random r = new Random((long)(time * 10.0F) + 7L);

         for (int i = 0; i < 22; i++) {
            float ang = r.nextFloat() * 6.283F;
            float fx = x + (float)Math.cos(ang) * h * 0.3F;
            float fy = y - h * 0.55F + (float)Math.sin(ang) * h * 0.33F;
            float w = h * (0.012F + r.nextFloat() * 0.02F);
            c.fill(fx - w, fy - w * 3.0F, fx + w, fy + w, alpha(12884223, 0.3F * a));
         }
      }
   }

   private void ellipse(SpaceCanvas c, float x, float y, float rx, float ry, int argb) {
      int rows = Math.max(3, Math.min(28, (int)(ry / 1.5F)));
      float step = ry * 2.0F / rows;

      for (int i = 0; i < rows; i++) {
         float yy = -ry + (i + 0.5F) * step;
         float w = rx * (float)Math.sqrt(Math.max(0.0F, 1.0F - yy * yy / (ry * ry)));
         c.fill(x - w, y + yy - step / 2.0F, x + w, y + yy + step / 2.0F, argb);
      }
   }

   // ------------------------------------------------------------------ Effekte

   private static final int[] FIRE = {-1, -2400, -12256, -26368, -50384, -10080728, -12105913};
   private static final int[] ELECTRIC = {-1, -1052673, -3349249, -7155457, -10706689, -12950360, -13421721};

   private void effect(SpaceCanvas c, ValleyFight.Fx e, float time) {
      float life = Math.min(1.0F, e.age / Math.max(0.001F, e.dur));
      switch (e.type) {
         case ValleyFight.SPARK: {
            if (this.visible(e.x, e.y, e.z) && this.proj(e.x, e.y, e.z)) {
               float r = Math.max(0.7F, e.size * this.pk);
               c.fill(this.px - r, this.py - r, this.px + r, this.py + r, alpha(e.color, 1.0F - life * 0.7F));
            }
            break;
         }
         case ValleyFight.DROP: {
            if (this.visible(e.x, e.y, e.z) && this.proj(e.x, e.y, e.z)) {
               float r = Math.max(0.6F, e.size * this.pk);
               c.fill(this.px - r, this.py - r, this.px + r, this.py + r, alpha(14544895, 0.85F * (1.0F - life * 0.5F)));
            }
            break;
         }
         case ValleyFight.SMOKE:
         case ValleyFight.STEAM: {
            if (this.proj(e.x, e.y, e.z)) {
               float r = e.size * this.pk * (0.5F + life * 1.3F);
               int col = e.type == ValleyFight.STEAM ? 15659767 : (e.color == 1 ? 12101776 : 15263976);
               float a = (e.type == ValleyFight.STEAM ? 0.32F : 0.75F) * (1.0F - life);
               c.fill(this.px - r, this.py - r, this.px + r, this.py + r, alpha(col, a));
            }
            break;
         }
         case ValleyFight.EMBER:
         case ValleyFight.WIND: {
            if (this.proj(e.x, e.y, e.z)) {
               float r = Math.max(0.6F, e.size * this.pk);
               int col = e.type == ValleyFight.WIND ? 14217983 : (e.color == 1 ? 16768064 : 16740896);
               c.fill(this.px - r, this.py - r, this.px + r, this.py + r, alpha(col, 0.8F * (1.0F - life)));
            }
            break;
         }
         case ValleyFight.RIPPLE:
         case ValleyFight.SHOCKRING:
            this.ring(c, e, life);
            break;
         case ValleyFight.FIREBALL: {
            if (this.proj(e.x, e.y, e.z)) {
               float r = Math.max(1.5F, e.size * this.pk * Math.min(1.0F, 0.25F + e.age * 2.5F) * 0.5F);
               float fl = 0.88F + 0.12F * (float)Math.sin(time * 30.0F);
               this.disc(c, this.px, this.py, r * 1.6F, alpha(16736288, 0.25F));
               this.disc(c, this.px, this.py, r * 1.1F * fl, alpha(14696474, 0.7F));
               this.disc(c, this.px, this.py, r * fl, alpha(16747040, 0.9F));
               this.disc(c, this.px, this.py, r * 0.65F, alpha(16764992, 0.95F));
               this.disc(c, this.px, this.py, r * 0.3F, -1);
            }
            break;
         }
         case ValleyFight.BLAST:
            this.blast(c, e, life);
            break;
         case ValleyFight.BOLT:
            this.bolt(c, e, life, time);
            break;
         case ValleyFight.KUNAI: {
            float len = (float)Math.sqrt((e.x2 - e.vx) * (e.x2 - e.vx) + (e.y2 - e.vy) * (e.y2 - e.vy) + (e.z2 - e.vz) * (e.z2 - e.vz)) + 0.001F;
            float bx = e.x - (e.x2 - e.vx) / len * 0.6F;
            float by = e.y - (e.y2 - e.vy) / len * 0.6F;
            float bz = e.z - (e.z2 - e.vz) / len * 0.6F;
            if (this.proj(bx, by, bz)) {
               float x1 = this.px;
               float y1 = this.py;
               if (this.proj(e.x, e.y, e.z)) {
                  c.line(x1, y1, this.px, this.py, Math.max(0.8F, this.pk * 0.08F), alpha(3158072, 1.0F));
                  c.fill(this.px - 0.7F, this.py - 0.7F, this.px + 0.7F, this.py + 0.7F, alpha(15724543, 1.0F));
               }
            }
            break;
         }
         case ValleyFight.SLASH: {
            if (this.proj(e.x, e.y, e.z)) {
               float r = e.size * this.pk;
               float prev = 0.0F;

               for (int i = 0; i <= 6; i++) {
                  float ang = -1.2F + i * 0.4F;
                  if (i > 0) {
                     float x1 = this.px + e.color * (float)Math.cos(prev) * r;
                     float y1 = this.py + (float)Math.sin(prev) * r;
                     float x2 = this.px + e.color * (float)Math.cos(ang) * r;
                     float y2 = this.py + (float)Math.sin(ang) * r;
                     c.line(x1, y1, x2, y2, Math.max(0.8F, r * 0.12F), alpha(15790335, 0.9F * (1.0F - life)));
                  }

                  prev = ang;
               }
            }
            break;
         }
         case ValleyFight.DOME:
            this.dome(c, e, time);
      }
   }

   private void ring(SpaceCanvas c, ValleyFight.Fx e, float life) {
      boolean big = e.type == ValleyFight.SHOCKRING;
      float grow = big ? 1.0F - (1.0F - life) * (1.0F - life) : 0.25F + 0.75F * life;
      float r = e.size * grow;
      int segs = big ? 48 : 20;
      float a = (big ? 0.75F : 0.5F) * (1.0F - life);
      float lx = 0.0F;
      float ly = 0.0F;
      boolean has = false;

      for (int i = 0; i <= segs; i++) {
         double ang = i * Math.PI * 2.0 / segs;
         float wx = e.x + (float)Math.cos(ang) * r;
         float wz = e.z + (float)Math.sin(ang) * r;
         if (this.proj(wx, e.y, wz)) {
            if (has && this.visible(wx, e.y + 0.1F, wz)) {
               c.line(lx, ly, this.px, this.py, Math.max(0.8F, this.pk * (big ? 0.35F : 0.1F)), alpha(e.color, a));
            }

            lx = this.px;
            ly = this.py;
            has = true;
         } else {
            has = false;
         }
      }
   }

   private void blast(SpaceCanvas c, ValleyFight.Fx e, float life) {
      if (this.proj(e.x, e.y, e.z)) {
         int[] pal = e.color == 1 ? ELECTRIC : FIRE;
         float radius = e.size * this.pk;
         float grow = 0.35F + 0.65F * (float)Math.sqrt(life);
         Random r = new Random(e.seed);

         for (int i = 0; i < 10; i++) {
            float ang = r.nextFloat() * 6.283F;
            float d = r.nextFloat() * 0.75F * radius * grow;
            float x = this.px + (float)Math.cos(ang) * d;
            float y = this.py + (float)Math.sin(ang) * d - life * radius * 0.3F;
            float pr = radius * (0.3F + r.nextFloat() * 0.4F) * grow;
            float local = Math.min(1.0F, life + r.nextFloat() * 0.25F);
            int idx = Math.min(pal.length - 1, (int)(local * pal.length));
            float a = idx >= 5 ? 0.55F * (1.0F - life) : 1.0F - life * 0.4F;
            c.fill(x - pr, y - pr, x + pr, y + pr, alpha(pal[idx], a));
         }

         if (life < 0.2F) {
            float fr = radius * 1.4F * (1.0F - life / 0.2F);
            this.disc(c, this.px, this.py, fr, alpha(16777215, 0.4F * (1.0F - life / 0.2F)));
         }
      }
   }

   private void bolt(SpaceCanvas c, ValleyFight.Fx e, float life, float time) {
      if (this.proj(e.x, e.y, e.z)) {
         float x1 = this.px;
         float y1 = this.py;
         float k = this.pk;
         if (this.proj(e.x2, e.y2, e.z2)) {
            float x2 = this.px;
            float y2 = this.py;
            float dx = x2 - x1;
            float dy = y2 - y1;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len >= 0.5F) {
               float nx = -dy / len;
               float ny = dx / len;
               Random r = new Random(e.seed + (int)(time * 25.0F));
               int segs = Math.max(4, Math.min(24, (int)(len / 8.0F)));
               float fl = r.nextFloat() < 0.15F ? 0.4F : 1.0F;
               float a = (1.0F - life * 0.6F) * fl;
               float glowW = Math.max(2.0F, e.size * k * 0.45F);
               float coreW = Math.max(0.8F, e.size * k * 0.14F);
               float lx = x1;
               float ly = y1;

               for (int i = 1; i <= segs; i++) {
                  float t = (float)i / segs;
                  float off = i == segs ? 0.0F : (r.nextFloat() - 0.5F) * len * 0.16F;
                  float cx = x1 + dx * t + nx * off;
                  float cy = y1 + dy * t + ny * off;
                  c.line(lx, ly, cx, cy, glowW, alpha(e.color, 0.35F * a));
                  c.line(lx, ly, cx, cy, coreW, alpha(16777215, a));
                  if (r.nextFloat() < 0.18F) {
                     float bl = len * 0.12F;
                     c.line(cx, cy, cx + nx * bl * (r.nextFloat() - 0.5F) * 2.0F + dx / len * bl * 0.5F, cy + ny * bl * (r.nextFloat() - 0.5F) * 2.0F + dy / len * bl * 0.5F, coreW * 0.7F, alpha(e.color, 0.8F * a));
                  }

                  lx = cx;
                  ly = cy;
               }
            }
         }
      }
   }

   /** Zusammenprall von Rasengan und Chidori: wachsende Kugel aus Licht mit dunklem Rand. */
   private void dome(SpaceCanvas c, ValleyFight.Fx e, float time) {
      if (this.proj(e.x, e.y, e.z)) {
         float t = e.age;
         float grow = Math.min(1.0F, t / 1.3F);
         grow = 1.0F - (1.0F - grow) * (1.0F - grow) * (1.0F - grow);
         float fade = t < 2.0F ? 1.0F : Math.max(0.0F, 1.0F - (t - 2.0F) / (e.dur - 2.0F));
         float r = e.size * this.pk * grow;
         float fl = 0.92F + 0.08F * (float)Math.sin(time * 37.0F);
         this.disc(c, this.px, this.py, r * 1.25F, alpha(5909146, 0.3F * fade));
         this.disc(c, this.px, this.py, r * fl, alpha(1706032, 0.6F * fade));
         this.disc(c, this.px, this.py, r * 0.86F, alpha(9408511, 0.55F * fade));
         this.disc(c, this.px, this.py, r * 0.72F, alpha(13166847, 0.8F * fade));
         this.disc(c, this.px, this.py, r * 0.5F, alpha(16777215, 0.92F * fade));
         // wirbelnde Schlieren
         for (int i = 0; i < 5; i++) {
            double ang = time * (i % 2 == 0 ? 3.0 : -2.4) + i * 1.25;
            float x1 = this.px + (float)Math.cos(ang) * r * 0.55F;
            float y1 = this.py + (float)Math.sin(ang) * r * 0.55F;
            float x2 = this.px + (float)Math.cos(ang + 0.9) * r * 0.82F;
            float y2 = this.py + (float)Math.sin(ang + 0.9) * r * 0.82F;
            c.line(x1, y1, x2, y2, Math.max(1.0F, r * 0.05F), alpha(i % 2 == 0 ? 7327999 : 11299071, 0.6F * fade));
         }
      }
   }
}
