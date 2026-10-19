package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.totem.TotemPopTracker;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class DeathAnimation extends Module {
   private static final String SOUL = "Seelen-Aufstieg";
   private static final String STRIKE = "Lichtschlag";
   private static final String SHOCK = "Schockwelle";
   private static final String GRAVE = "Grabstein";
   private static final String STARS = "Sternenwirbel";
   private static final int MAX = 16;
   private final BooleanSetting custom = this.add(
      new BooleanSetting("Eigene Animation", "Die Meowl-Todesanimation abspielen (aus = nur die Vanilla-Animation ausschalten, ohne Ersatz)", true)
   );
   private final ModeSetting style = this.add(
      new ModeSetting("Stil", "Wie die Animation aussieht", "Seelen-Aufstieg", "Seelen-Aufstieg", "Lichtschlag", "Schockwelle", "Grabstein", "Sternenwirbel")
   );
   private final NumberSetting duration = this.add(new NumberSetting("Dauer", "Wie lange die Animation sichtbar ist", 3.0, 1.0, 10.0, 0.5, "s"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe der Animation", -5215233, false));
   private final BooleanSetting self = this.add(new BooleanSetting("Bei mir", "Auch bei deinem eigenen Tod", true));
   private final BooleanSetting others = this.add(new BooleanSetting("Bei anderen", "Bei anderen Spielern", true));
   private final BooleanSetting particles = this.add(new BooleanSetting("Partikel", "Zusätzliche Partikel (wirken nur bei dir)", true));
   private final BooleanSetting sound = this.add(new BooleanSetting("Sound", "Kurzer Klang beim Auslösen", false));
   private final BooleanSetting noVanilla = this.add(
      new BooleanSetting("Vanilla-Todesanimation aus", "Spieler kippen beim Tod nicht mehr um und verschwinden ohne Rauchwolke (geht auch, wenn die eigene Animation aus ist)", false)
   );
   private static DeathAnimation instance;
   private final List<DeathAnimation.Death> active = new ArrayList<>();

   public DeathAnimation() {
      super("Death Animation", "Kurze Animation an der Todesposition eines Spielers – mehrere Stile, Dauer einstellbar.", Category.RENDER);
      this.section(Section.COSMETICS);
      this.icon(Items.SKELETON_SKULL);
      this.group("Animation", new Setting[]{this.custom, this.style, this.duration, this.color});
      for (Setting<?> s : new Setting[]{this.style, this.duration, this.color, this.self, this.others, this.particles, this.sound}) {
         s.setVisibility(this.custom::get);
      }

      this.group("Wann", new Setting[]{this.self, this.others, this.particles, this.sound});
      this.group("Vanilla", new Setting[]{this.noVanilla});
      instance = this;
      TotemPopTracker.onPlayerDeath(this::onDeath);
   }

   /** Ist „Vanilla-Todesanimation aus“ aktiv? (gilt, solange das Modul an ist) */
   public static boolean vanillaDisabled() {
      DeathAnimation d = instance;
      return d != null && d.isEnabled() && d.noVanilla.get();
   }

   private void onDeath(Player player) {
      if (this.isEnabled() && this.custom.get() && this.mc.player != null) {
         boolean me = player == this.mc.player;
         if (me ? this.self.get() : this.others.get()) {
            if (this.active.size() >= 16) {
               this.active.remove(0);
            }

            this.active
               .add(
                  new DeathAnimation.Death(
                     player.position(), System.currentTimeMillis(), this.style.get(), this.color.get() & 16777215, Math.round(this.duration.get() * 1000.0)
                  )
               );
            if (this.sound.get()) {
               this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.6F));
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      this.active.clear();
   }

   @Override
   public void onTick() {
      if (this.mc.level == null) {
         this.active.clear();
      } else {
         Iterator<DeathAnimation.Death> it = this.active.iterator();

         while (it.hasNext()) {
            DeathAnimation.Death d = it.next();
            float age = d.age();
            if (age >= 1.0F) {
               it.remove();
            } else if (this.particles.get() && age < 0.85F) {
               this.emit(d, age);
            }
         }
      }
   }

   private void emit(DeathAnimation.Death d, float age) {
      ClientLevel w = this.mc.level;
      RandomSource r = w.getRandom();
      Vec3 p = d.pos();
      String var6 = d.style();
      switch (var6) {
         case "Seelen-Aufstieg":
            if (r.nextInt(2) == 0) {
               w.addParticle(
                  ParticleTypes.SOUL, p.x + (r.nextDouble() - 0.5) * 0.6, p.y + 0.2 + age * 2.0, p.z + (r.nextDouble() - 0.5) * 0.6, 0.0, 0.05, 0.0
               );
            }
            break;
         case "Lichtschlag":
            if (age < 0.2F) {
               w.addParticle(
                  ParticleTypes.ELECTRIC_SPARK, p.x + (r.nextDouble() - 0.5), p.y + r.nextDouble() * 2.0, p.z + (r.nextDouble() - 0.5), 0.0, 0.0, 0.0
               );
            }
            break;
         case "Schockwelle":
            if (age < 0.1F) {
               for (int i = 0; i < 6; i++) {
                  double ax = r.nextDouble() * Math.PI * 2.0;
                  w.addParticle(ParticleTypes.CLOUD, p.x, p.y + 0.2, p.z, Math.cos(ax) * 0.35, 0.02, Math.sin(ax) * 0.35);
               }
            }
            break;
         case "Grabstein":
            if (age < 0.25F) {
               w.addParticle(ParticleTypes.SMOKE, p.x + (r.nextDouble() - 0.5) * 0.8, p.y + 0.1, p.z + (r.nextDouble() - 0.5) * 0.8, 0.0, 0.02, 0.0);
            }
            break;
         default:
            double a = age * 20.0 + r.nextDouble() * 0.4;
            ParticleOptions fx = r.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.WAX_OFF;
            w.addParticle(fx, p.x + Math.cos(a) * 0.7, p.y + 0.2 + age * 2.4, p.z + Math.sin(a) * 0.7, 0.0, 0.0, 0.0);
      }
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      if (!this.active.isEmpty()) {
         Vec3 cam = context.cameraPos();
         PoseStack m = context.matrices();
         VertexConsumer glow = context.buffer(WorldRender.glow());

         for (DeathAnimation.Death d : this.active) {
            float age = Math.min(1.0F, Math.max(0.0F, d.age()));
            float fade = age < 0.12F ? age / 0.12F : (age > 0.75F ? (1.0F - age) / 0.25F : 1.0F);
            m.pushPose();
            m.translate(d.pos().x - cam.x, d.pos().y - cam.y, d.pos().z - cam.z);
            Matrix4f mat = m.last().pose();
            float dx = (float)(d.pos().x - cam.x);
            float dz = (float)(d.pos().z - cam.z);
            String var13 = d.style();
            switch (var13) {
               case "Seelen-Aufstieg":
                  float rise = age * 2.2F;
                  billboardColumn(glow, mat, dx, dz, 0.35F, rise, rise + 1.6F, d.rgb(), 0.55F * fade);
                  ring(glow, mat, 0.05F, 0.4F + age * 0.6F, 0.55F + age * 0.6F, d.rgb(), 0.6F * fade);
                  break;
               case "Lichtschlag":
                  float flash = age < 0.15F ? 1.0F : Math.max(0.0F, 1.0F - (age - 0.15F) / 0.5F);
                  billboardColumn(glow, mat, dx, dz, 0.18F + 0.25F * flash, 0.0F, 40.0F, d.rgb(), 0.9F * flash * fade);
                  billboardColumn(glow, mat, dx, dz, 0.06F, 0.0F, 40.0F, 16777215, 0.8F * flash * fade);
                  ring(glow, mat, 0.04F, 0.2F, 0.6F + age * 2.0F, d.rgb(), 0.6F * fade);
                  break;
               case "Schockwelle":
                  float r1 = 0.3F + age * 4.0F;
                  ring(glow, mat, 0.05F, r1 - 0.35F, r1, d.rgb(), 0.85F * fade);
                  ring(glow, mat, 0.05F, r1 * 0.6F - 0.2F, r1 * 0.6F, 16777215, 0.35F * fade);
                  billboardColumn(glow, mat, dx, dz, 0.5F * (1.0F - age), 0.0F, 1.8F, d.rgb(), 0.4F * fade);
                  break;
               case "Grabstein":
                  ring(glow, mat, 0.03F, 0.3F, 0.9F, d.rgb(), 0.35F * fade);
                  break;
               default:
                  for (int k = 0; k < 3; k++) {
                     float rr = 0.5F + k * 0.25F;
                     float y = 0.1F + age * 2.2F + k * 0.25F;
                     ringAt(glow, mat, y, rr - 0.08F, rr, d.rgb(), 0.5F * fade, age * 6.0F + k);
                  }
            }

            m.popPose();
         }

         VertexConsumer solid = null;

         for (DeathAnimation.Death d : this.active) {
            if ("Grabstein".equals(d.style())) {
               if (solid == null) {
                  solid = context.buffer(WorldRender.solidColor());
               }

               float age = Math.min(1.0F, Math.max(0.0F, d.age()));
               float up = Math.min(1.0F, age / 0.2F);
               float sink = age > 0.8F ? (age - 0.8F) / 0.2F : 0.0F;
               float h = 1.0F * up * (1.0F - sink);
               if (h > 0.02F) {
                  m.pushPose();
                  m.translate(d.pos().x - cam.x, d.pos().y - cam.y, d.pos().z - cam.z);
                  WorldRender.box(m, solid, -0.32F, 0.0F, -0.08F, 0.32F, h * 0.75F, 0.08F, -7696487, 1.0F);
                  WorldRender.box(m, solid, -0.22F, h * 0.75F, -0.08F, 0.22F, h * 0.95F, 0.08F, -7696487, 1.0F);
                  WorldRender.box(m, solid, -0.04F, h * 0.4F, -0.1F, 0.04F, h * 0.82F, 0.1F, 0xFF000000 | d.rgb(), 1.2F);
                  WorldRender.box(m, solid, -0.14F, h * 0.62F, -0.1F, 0.14F, h * 0.7F, 0.1F, 0xFF000000 | d.rgb(), 1.2F);
                  m.popPose();
               }
            }
         }
      }
   }

   private static void billboardColumn(VertexConsumer vc, Matrix4f mat, float dx, float dz, float halfW, float y1, float y2, int rgb, float alpha) {
      if (!(alpha <= 0.01F)) {
         float rx = -dz;
         float rz = dx;
         float len = (float)Math.sqrt(rx * rx + dx * dx);
         if (len < 1.0E-4F) {
            rx = 1.0F;
            rz = 0.0F;
            len = 1.0F;
         }

         rx = rx / len * halfW;
         rz = rz / len * halfW;
         int bottom = ItemBeam.color(rgb, alpha, 0.2F);
         int top = ItemBeam.color(rgb, 0.0F, 0.2F);
         vc.addVertex(mat, -rx, y1, -rz).setColor(bottom);
         vc.addVertex(mat, rx, y1, rz).setColor(bottom);
         vc.addVertex(mat, rx, y2, rz).setColor(top);
         vc.addVertex(mat, -rx, y2, -rz).setColor(top);
      }
   }

   private static void ring(VertexConsumer vc, Matrix4f mat, float y, float r1, float r2, int rgb, float alpha) {
      ringAt(vc, mat, y, r1, r2, rgb, alpha, 0.0F);
   }

   private static void ringAt(VertexConsumer vc, Matrix4f mat, float y, float r1, float r2, int rgb, float alpha, float phase) {
      if (!(alpha <= 0.01F) && !(r2 <= 0.0F)) {
         r1 = Math.max(0.0F, r1);
         int seg = 32;

         for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2.0 / seg;
            double a1 = (i + 1) * Math.PI * 2.0 / seg;
            float boost = phase == 0.0F ? 1.0F : 0.5F + 0.5F * (float)Math.max(0.0, Math.cos(a0 - phase));
            int inner = ItemBeam.color(rgb, 0.0F, 0.0F);
            int outer = ItemBeam.color(rgb, alpha * boost, 0.15F);
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            vc.addVertex(mat, c0 * r1, y, s0 * r1).setColor(inner);
            vc.addVertex(mat, c1 * r1, y, s1 * r1).setColor(inner);
            vc.addVertex(mat, c1 * r2, y, s1 * r2).setColor(outer);
            vc.addVertex(mat, c0 * r2, y, s0 * r2).setColor(outer);
         }
      }
   }

   private record Death(Vec3 pos, long start, String style, int rgb, long durationMs) {
      float age() {
         return (float)(System.currentTimeMillis() - this.start) / (float)this.durationMs;
      }
   }
}
