package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Geräusche sehen (wie der Modus für Gehörlose in Fortnite, im Minecraft-Look): Schritte, Truhen, Explosionen, Pfeile,
 * Monster … erscheinen als Bögen um das Fadenkreuz – in der Richtung, aus der sie kommen, mit passendem Block-Symbol.
 */
public class SoundRadar extends Module implements SoundEventListener {
   private final NumberSetting radius = this.add(new NumberSetting("Radius", "Abstand der Anzeige vom Fadenkreuz", 70.0, 40.0, 160.0, 5.0, "px"));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", "Nur Geräusche bis zu dieser Entfernung", 24.0, 6.0, 64.0, 2.0, "Blöcke"));
   private final NumberSetting hold = this.add(new NumberSetting("Anzeigedauer", "Wie lange ein Geräusch sichtbar bleibt", 1.6, 0.6, 4.0, 0.1, "s"));
   private final BooleanSetting steps = this.add(new BooleanSetting("Schritte", "Schritte anderer Spieler und Mobs", true));
   private final BooleanSetting mobs = this.add(new BooleanSetting("Monster & Tiere", "Geräusche von Mobs", true));
   private final BooleanSetting combat = this.add(new BooleanSetting("Kampf", "Treffer, Bögen, Pfeile, Explosionen", true));
   private final BooleanSetting blocks = this.add(new BooleanSetting("Blöcke & Truhen", "Abbauen, Platzieren, Truhen, Türen", true));
   private final BooleanSetting labels = this.add(new BooleanSetting("Beschriftung", "Kurzer Text zum Geräusch (z.B. „Schritte“)", true));
   private final List<Hit> hits = new ArrayList<>();
   private boolean registered;

   private record Kind(String label, Item icon, int color, int group) {
   }

   private static final int G_STEP = 0;
   private static final int G_MOB = 1;
   private static final int G_COMBAT = 2;
   private static final int G_BLOCK = 3;

   private static final class Hit {
      Vec3 pos;
      Kind kind;
      long at;
      float loud;
   }

   public SoundRadar() {
      super("Sound Radar", "Gehörlosen-Modus wie in Fortnite: zeigt im Minecraft-Look, aus welcher Richtung Geräusche kommen.", Category.HUD);
      this.section(Section.MEOWL);
      this.icon(Items.SCULK_SENSOR);
      this.group("Anzeige", new Setting[]{this.radius, this.range, this.hold, this.labels});
      this.group("Geräusche", new Setting[]{this.steps, this.mobs, this.combat, this.blocks});
   }

   @Override
   protected void onEnable() {
      if (!this.registered) {
         this.mc.getSoundManager().addListener(this);
         this.registered = true;
      }
   }

   @Override
   protected void onDisable() {
      if (this.registered) {
         this.mc.getSoundManager().removeListener(this);
         this.registered = false;
      }

      this.hits.clear();
   }

   private static Kind classify(String path, SoundSource source) {
      if (path.contains("step") || path.contains("swim") || path.contains(".land")) {
         return new Kind("Schritte", Items.LEATHER_BOOTS, 0xFFE8E8E8, G_STEP);
      } else if (path.contains("explode") || path.contains("explosion") || path.contains("tnt") || path.contains("firework")) {
         return new Kind("Explosion", Items.TNT, 0xFFFF5030, G_COMBAT);
      } else if (path.contains("arrow") || path.contains("bow") || path.contains("crossbow") || path.contains("trident")) {
         return new Kind("Pfeil", Items.ARROW, 0xFFFFC040, G_COMBAT);
      } else if (path.contains("attack") || path.contains(".hurt") || path.contains("shield") || path.contains("crit")) {
         return new Kind("Kampf", Items.IRON_SWORD, 0xFFFF4040, G_COMBAT);
      } else if (path.contains("chest") || path.contains("barrel") || path.contains("shulker_box") || path.contains("ender_chest")) {
         return new Kind("Truhe", Items.CHEST, 0xFFFFD27A, G_BLOCK);
      } else if (path.contains("door") || path.contains("trapdoor") || path.contains("fence_gate")) {
         return new Kind("Tür", Items.OAK_DOOR, 0xFFC8A070, G_BLOCK);
      } else if (path.contains(".break") || path.contains(".place") || path.contains(".hit")) {
         return new Kind("Blöcke", Items.IRON_PICKAXE, 0xFFB0C0D0, G_BLOCK);
      } else if (path.contains("portal") || path.contains("teleport") || path.contains("enderman")) {
         return new Kind("Teleport", Items.ENDER_PEARL, 0xFFC040FF, G_MOB);
      } else if (path.contains("eat") || path.contains("drink") || path.contains("burp")) {
         return new Kind("Essen", Items.APPLE, 0xFF7AE07A, G_MOB);
      } else if (source == SoundSource.HOSTILE) {
         return new Kind("Monster", Items.ZOMBIE_HEAD, 0xFFFF6060, G_MOB);
      } else if (source == SoundSource.NEUTRAL) {
         return new Kind("Tier", Items.WHEAT, 0xFF9AE07A, G_MOB);
      } else if (source == SoundSource.PLAYERS) {
         return new Kind("Spieler", Items.PLAYER_HEAD, 0xFF80C0FF, G_MOB);
      } else {
         return null;
      }
   }

   @Override
   public void onPlaySound(SoundInstance sound, WeighedSoundEvents events, float range) {
      if (!this.isEnabled() || this.mc.player == null || sound.isRelative()) {
         return;
      }

      SoundSource src = sound.getSource();
      if (src == SoundSource.MUSIC || src == SoundSource.RECORDS || src == SoundSource.AMBIENT || src == SoundSource.MASTER || src == SoundSource.WEATHER) {
         return;
      }

      Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
      double dist = pos.distanceTo(this.mc.player.position());
      // eigene Geräusche (direkt bei dir) nicht anzeigen
      if (dist < 1.6 || dist > this.range.get()) {
         return;
      }

      Kind k = classify(sound.getIdentifier().getPath().toLowerCase(Locale.ROOT), src);
      if (k == null) {
         return;
      }

      boolean on = switch (k.group()) {
         case G_STEP -> this.steps.get();
         case G_COMBAT -> this.combat.get();
         case G_BLOCK -> this.blocks.get();
         default -> this.mobs.get();
      };
      if (!on) {
         return;
      }

      long now = System.currentTimeMillis();

      // gleiches Geräusch an fast gleicher Stelle: nur auffrischen
      for (Hit h : this.hits) {
         if (h.kind.label().equals(k.label()) && h.pos.distanceToSqr(pos) < 4.0) {
            h.pos = pos;
            h.at = now;
            return;
         }
      }

      Hit h = new Hit();
      h.pos = pos;
      h.kind = k;
      h.at = now;
      h.loud = (float)Math.max(0.25, 1.0 - dist / this.range.get());
      this.hits.add(h);
      if (this.hits.size() > 24) {
         this.hits.remove(0);
      }
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      if (this.mc.player == null || this.hits.isEmpty()) {
         return;
      }

      long now = System.currentTimeMillis();
      long life = Math.round(this.hold.get() * 1000.0);
      int cx = ctx.guiWidth() / 2;
      int cy = ctx.guiHeight() / 2;
      float r = this.radius.get().floatValue();
      Vec3 eye = this.mc.player.getEyePosition(tickDelta);
      float yaw = this.mc.player.getViewYRot(tickDelta);
      Iterator<Hit> it = this.hits.iterator();

      while (it.hasNext()) {
         Hit h = it.next();
         long age = now - h.at;
         if (age > life) {
            it.remove();
            continue;
         }

         float a = 1.0F - (float)age / life;
         a = Math.min(1.0F, a * 2.0F);
         // Richtung relativ zum Blick: 0 = vorne (oben auf dem Bildschirm), im Uhrzeigersinn
         double dx = h.pos.x - eye.x;
         double dz = h.pos.z - eye.z;
         double world = Math.toDegrees(Math.atan2(-dx, dz));
         float rel = (float)Math.toRadians(world - yaw);
         float sx = (float)Math.sin(rel);
         float sy = -(float)Math.cos(rel);
         int ax = Math.round(cx + sx * r);
         int ay = Math.round(cy + sy * r);
         // Bogen aus Pixel-Blöcken (Minecraft-Look), lauter = breiter
         int seg = 3 + Math.round(4 * h.loud);

         for (int i = -seg; i <= seg; i++) {
            float ang = rel + i * 0.045F;
            int bx = Math.round(cx + (float)Math.sin(ang) * (r + 10.0F));
            int by = Math.round(cy - (float)Math.cos(ang) * (r + 10.0F));
            float edge = 1.0F - Math.abs(i) / (float)(seg + 1);
            int col = Math.round(255.0F * a * (0.35F + 0.65F * edge)) << 24 | h.kind.color() & 16777215;
            ctx.fill(bx - 2, by - 2, bx + 2, by + 2, col);
         }

         // Symbol im dunklen Kästchen
         int box = 20;
         Render2D.roundRect(ctx, ax - box / 2, ay - box / 2, box, box, 4, Math.round(170.0F * a) << 24 | 0x101418);
         Render2D.roundRect(ctx, ax - box / 2, ay + box / 2 - 2, box, 2, 1, Math.round(255.0F * a) << 24 | h.kind.color() & 16777215);
         if (a > 0.35F) {
            ctx.renderItem(new ItemStack(h.kind.icon()), ax - 8, ay - 8);
         }

         // höher oder tiefer?
         double dy = h.pos.y - eye.y;
         if (Math.abs(dy) > 2.5) {
            String arrow = dy > 0 ? "▲" : "▼";
            ctx.drawCenteredString(this.mc.font, arrow, ax + box / 2 + 4, ay - 4, Math.round(255.0F * a) << 24 | 0xFFFFFF);
         }

         if (this.labels.get()) {
            ctx.drawCenteredString(this.mc.font, h.kind.label(), ax, ay + box / 2 + 2, Math.round(230.0F * a) << 24 | h.kind.color() & 16777215);
         }
      }
   }
}
