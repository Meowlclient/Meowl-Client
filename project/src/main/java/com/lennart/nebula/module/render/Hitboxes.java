package com.lennart.nebula.module.render;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.mods.Friends;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.WorldCtx;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hitboxen wie F3+B, aber einstellbar: Farben je Art (Spieler, Freunde, Monster, Tiere, Items), Linienstärke,
 * gefüllte Flächen, Augenhöhe, Blickrichtung, Färbung beim Treffer und das anvisierte Ziel hervorgehoben.
 */
public class Hitboxes extends Module {
   private final BooleanSetting players = this.add(new BooleanSetting("Spieler", "Hitboxen von Spielern", true));
   private final BooleanSetting mobs = this.add(new BooleanSetting("Monster", "Hitboxen von feindlichen Mobs", true));
   private final BooleanSetting animals = this.add(new BooleanSetting("Tiere & andere", "Hitboxen von friedlichen Mobs", false));
   private final BooleanSetting items = this.add(new BooleanSetting("Items & Pfeile", "Hitboxen von Items und Geschossen", false));
   private final BooleanSetting self = this.add(new BooleanSetting("Eigene (3. Person)", "Deine eigene Hitbox in der Third-Person-Ansicht", false));
   private final ColorSetting playerColor = this.add(new ColorSetting("Farbe Spieler", "", 0xFFFFFFFF, true));
   private final ColorSetting friendColor = this.add(new ColorSetting("Farbe Freunde", "Meowl-Freunde bekommen eine eigene Farbe", 0xFF5BE38A, true));
   private final ColorSetting mobColor = this.add(new ColorSetting("Farbe Monster", "", 0xFFFF5F5F, true));
   private final ColorSetting animalColor = this.add(new ColorSetting("Farbe Tiere", "", 0xFFFFC857, true));
   private final ColorSetting itemColor = this.add(new ColorSetting("Farbe Items", "", 0xFF7FD0FF, true));
   private final ColorSetting targetColor = this.add(new ColorSetting("Farbe Ziel", "Farbe der Hitbox, die du gerade anvisierst", 0xFF8B5CF6, true));
   private final ColorSetting hitColor = this.add(new ColorSetting("Farbe Treffer", "Farbe kurz nachdem jemand getroffen wurde", 0xFFFF2020, true));
   private final NumberSetting width = this.add(new NumberSetting("Linienstärke", "Dicke der Kanten", 2.0, 0.5, 6.0, 0.5, "px"));
   private final NumberSetting fill = this.add(new NumberSetting("Füllung", "Flächen leicht einfärben (0 = nur Kanten)", 0.0, 0.0, 60.0, 5.0, "%"));
   private final BooleanSetting eyes = this.add(new BooleanSetting("Augenhöhe", "Rote Linie auf Augenhöhe wie bei F3+B", true));
   private final BooleanSetting look = this.add(new BooleanSetting("Blickrichtung", "Linie in Blickrichtung", true));
   private final BooleanSetting highlightTarget = this.add(new BooleanSetting("Ziel hervorheben", "Die anvisierte Hitbox in eigener Farbe", true));
   private final BooleanSetting showHits = this.add(new BooleanSetting("Treffer zeigen", "Hitbox färbt sich kurz, wenn jemand Schaden nimmt", true));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", "Nur Entities in dieser Entfernung", 48.0, 8.0, 128.0, 4.0, "Blöcke"));

   public Hitboxes() {
      super("Hitboxes", "Hitboxen mit eigenen Farben je Art, Linienstärke, Füllung, Ziel-Hervorhebung und Treffer-Färbung.", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.ARMOR_STAND);
      this.group("Was", new Setting[]{this.players, this.mobs, this.animals, this.items, this.self, this.range});
      this.group(
         "Farben",
         new Setting[]{this.playerColor, this.friendColor, this.mobColor, this.animalColor, this.itemColor, this.targetColor, this.hitColor}
      );
      this.group("Aussehen", new Setting[]{this.width, this.fill, this.eyes, this.look, this.highlightTarget, this.showHits});
   }

   private Integer colorFor(Entity e) {
      if (e instanceof Player p) {
         if (!this.players.get()) {
            return null;
         } else {
            return Friends.isFriend(p) ? this.friendColor.get() : this.playerColor.get();
         }
      } else if (e instanceof Enemy) {
         return this.mobs.get() ? this.mobColor.get() : null;
      } else if (e instanceof LivingEntity) {
         return this.animals.get() ? this.animalColor.get() : null;
      } else if (e instanceof ItemEntity || e instanceof Projectile) {
         return this.items.get() ? this.itemColor.get() : null;
      } else {
         return null;
      }
   }

   @Override
   public void onRenderWorld(WorldCtx ctx, float tickDelta) {
      if (this.mc.level == null || this.mc.player == null) {
         return;
      }

      Vec3 cam = ctx.cameraPos();
      double maxSq = this.range.get() * this.range.get();
      Entity target = this.mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
      boolean firstPerson = this.mc.options.getCameraType().isFirstPerson();
      VertexConsumer vc = ctx.buffer(WorldRender.translucentColor());
      Matrix4f mat = ctx.matrices().last().pose();

      for (Entity e : this.mc.level.entitiesForRendering()) {
         if (e == this.mc.player && (firstPerson || !this.self.get()) || e.isInvisible() && e != this.mc.player) {
            continue;
         }

         if (e.distanceToSqr(cam) > maxSq) {
            continue;
         }

         Integer base = this.colorFor(e);
         if (base == null) {
            continue;
         }

         int color = base;
         if (this.showHits.get() && e instanceof LivingEntity living && living.hurtTime > 0) {
            float k = living.hurtTime / 10.0F;
            color = mix(color, this.hitColor.get(), Math.min(1.0F, k * 1.4F));
         } else if (this.highlightTarget.get() && e == target) {
            color = this.targetColor.get();
         }

         // geglättete Position (wie beim Zeichnen der Entity)
         Vec3 now = e.position();
         Vec3 lerp = e.getPosition(tickDelta);
         AABB box = e.getBoundingBox().move(lerp.x - now.x - cam.x, lerp.y - now.y - cam.y, lerp.z - now.z - cam.z);
         float w = (float)this.width.get().doubleValue();
         this.box(vc, mat, box, color, w);
         if (this.fill.get() > 0.0) {
            int a = (int)Math.round(this.fill.get() / 100.0 * 255.0 * ((color >>> 24) / 255.0));
            this.fillBox(vc, mat, box, a << 24 | color & 16777215);
         }

         if (e instanceof LivingEntity) {
            double eyeY = box.minY + e.getEyeHeight();
            if (this.eyes.get()) {
               float ew = w * 0.8F;
               int red = 0xFFFF3030;
               this.line(vc, mat, box.minX, eyeY, box.minZ, box.maxX, eyeY, box.minZ, ew, red);
               this.line(vc, mat, box.maxX, eyeY, box.minZ, box.maxX, eyeY, box.maxZ, ew, red);
               this.line(vc, mat, box.maxX, eyeY, box.maxZ, box.minX, eyeY, box.maxZ, ew, red);
               this.line(vc, mat, box.minX, eyeY, box.maxZ, box.minX, eyeY, box.minZ, ew, red);
            }

            if (this.look.get()) {
               Vec3 dir = e.getViewVector(tickDelta).scale(2.0);
               double cx = (box.minX + box.maxX) / 2.0;
               double cz = (box.minZ + box.maxZ) / 2.0;
               this.line(vc, mat, cx, eyeY, cz, cx + dir.x, eyeY + dir.y, cz + dir.z, w * 0.8F, 0xFF4F8BFF);
            }
         }
      }
   }

   private void box(VertexConsumer vc, Matrix4f m, AABB b, int color, float w) {
      double x1 = b.minX;
      double y1 = b.minY;
      double z1 = b.minZ;
      double x2 = b.maxX;
      double y2 = b.maxY;
      double z2 = b.maxZ;
      this.line(vc, m, x1, y1, z1, x2, y1, z1, w, color);
      this.line(vc, m, x2, y1, z1, x2, y1, z2, w, color);
      this.line(vc, m, x2, y1, z2, x1, y1, z2, w, color);
      this.line(vc, m, x1, y1, z2, x1, y1, z1, w, color);
      this.line(vc, m, x1, y2, z1, x2, y2, z1, w, color);
      this.line(vc, m, x2, y2, z1, x2, y2, z2, w, color);
      this.line(vc, m, x2, y2, z2, x1, y2, z2, w, color);
      this.line(vc, m, x1, y2, z2, x1, y2, z1, w, color);
      this.line(vc, m, x1, y1, z1, x1, y2, z1, w, color);
      this.line(vc, m, x2, y1, z1, x2, y2, z1, w, color);
      this.line(vc, m, x2, y1, z2, x2, y2, z2, w, color);
      this.line(vc, m, x1, y1, z2, x1, y2, z2, w, color);
   }

   private void fillBox(VertexConsumer vc, Matrix4f m, AABB b, int c) {
      float x1 = (float)b.minX;
      float y1 = (float)b.minY;
      float z1 = (float)b.minZ;
      float x2 = (float)b.maxX;
      float y2 = (float)b.maxY;
      float z2 = (float)b.maxZ;
      quad(vc, m, c, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
      quad(vc, m, c, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
      quad(vc, m, c, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
      quad(vc, m, c, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
      quad(vc, m, c, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
      quad(vc, m, c, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
   }

   /** Kante als flaches Band, das immer zur Kamera zeigt; Breite in Bildschirm-Pixeln (ungefähr). */
   private void line(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz, float px, int color) {
      Vector3f a = new Vector3f((float)ax, (float)ay, (float)az);
      Vector3f b = new Vector3f((float)bx, (float)by, (float)bz);
      Vector3f dir = new Vector3f(b).sub(a);
      Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
      Vector3f side = new Vector3f(dir).cross(mid);
      if (side.lengthSquared() < 1.0E-8F) {
         side.set(0.0F, 1.0F, 0.0F);
      }

      float half = px * 0.0012F * Math.max(1.0F, mid.length()) / 2.0F;
      side.normalize().mul(half);
      quad(vc, m, color, a.x - side.x, a.y - side.y, a.z - side.z, a.x + side.x, a.y + side.y, a.z + side.z, b.x + side.x, b.y + side.y, b.z + side.z, b.x - side.x, b.y - side.y, b.z - side.z);
   }

   private static void quad(
      VertexConsumer vc, Matrix4f m, int c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4
   ) {
      vc.addVertex(m, x1, y1, z1).setColor(c);
      vc.addVertex(m, x2, y2, z2).setColor(c);
      vc.addVertex(m, x3, y3, z3).setColor(c);
      vc.addVertex(m, x4, y4, z4).setColor(c);
   }

   private static int mix(int a, int b, float t) {
      int aa = Math.round((a >>> 24) * (1.0F - t) + (b >>> 24) * t);
      int r = Math.round((a >> 16 & 255) * (1.0F - t) + (b >> 16 & 255) * t);
      int g = Math.round((a >> 8 & 255) * (1.0F - t) + (b >> 8 & 255) * t);
      int bl = Math.round((a & 255) * (1.0F - t) + (b & 255) * t);
      return aa << 24 | r << 16 | g << 8 | bl;
   }
}
