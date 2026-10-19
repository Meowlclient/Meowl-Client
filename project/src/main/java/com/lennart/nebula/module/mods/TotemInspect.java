package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.fx.CameraFx;
import com.lennart.nebula.fx.FirstPersonFx;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TotemInspect extends Module {
   private static final String NORMAL = "Normal";
   private static final String QUICK = "Schnell";
   private static final String TWO_HANDS = "Zwei Hände";
   private static final String SPECTACULAR = "Spektakulär";
   private static final String PARTICLES = "Partikel-Wirbel";
   private static final String GLOW = "Leuchten";
   private static final String FLIP = "Salto";
   private static final String SPIN = "Drehen";
   private static final String COMBO = "Kombi";
   private final KeybindSetting key = this.add(new KeybindSetting("Inspect-Taste", "Startet die Animation", Bind.keyboard(InputConstants.KEY_Y)));
   private final ModeSetting style = this.add(
      new ModeSetting(
            "Animation",
            "Art der Animation",
            "Normal",
            "Normal",
            "Schnell",
            "Zwei Hände",
            "Spektakulär",
            "Partikel-Wirbel",
            "Leuchten",
            "Salto",
            "Drehen",
            "Kombi"
         )
         .alias(new String[]{"Stil"})
   );
   private final BooleanSetting customDuration = this.add(new BooleanSetting("Eigene Dauer", "Aus: jede Animation hat ihre passende Standard-Dauer", false));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer", "Länge einer Animation", 2.5, 0.5, 6.0, 0.1, "s"));
   private final NumberSetting strength = this.add(new NumberSetting("Stärke", "Wie weit Hand und Item ausholen", 1.0, 0.3, 1.8, 0.05, "x"));
   private final NumberSetting rotation = this.add(
      new NumberSetting("Drehung", "Wie weit das Item beim Präsentieren gedreht wird", 180.0, 45.0, 360.0, 15.0, "°")
   );
   private final NumberSetting wobble = this.add(new NumberSetting("Kleine Bewegungen", "Zusätzliche kleine Wackler (0 = ruhig)", 2.0, 0.0, 4.0, 1.0, ""));
   private final BooleanSetting particles = this.add(new BooleanSetting("Partikel", "Totem-Partikel und Funken bei passenden Animationen", true));
   private final BooleanSetting sound = this.add(new BooleanSetting("Sound", "Geräusche beim Ausholen, Werfen und Fangen", true));
   private final BooleanSetting effects = this.add(new BooleanSetting("Effekte", "Goldenes Leuchten am Bildschirmrand und Aufblitzen", true));
   private final BooleanSetting camera = this.add(new BooleanSetting("Kamera-Bewegung", "Kamera neigt sich leicht mit (und ruckelt beim Fangen)", true));
   private final BooleanSetting loop = this.add(new BooleanSetting("Wiederholen", "Animation läuft weiter, bis du die Taste erneut drückst", false));
   private final BooleanSetting onlyTotem = this.add(new BooleanSetting("Nur Totem", "Aus: auch die Items aus der Liste unten", true));
   private final StringListSetting items = this.add(
      new StringListSetting("Weitere Items", "Diese Items lassen sich auch inspizieren", List.of("minecraft:mace", "minecraft:netherite_sword"))
         .normalizer(StringListSetting::itemId, "Item-ID, z.B. diamond_sword")
   );
   private final Random random = new Random();
   private long startNanos = -1L;
   private ItemStack inspected = ItemStack.EMPTY;
   private int slot = -1;
   private boolean keyDown;
   private boolean cue1;
   private boolean cue2;
   private boolean cue3;
   private float lastT;
   private boolean rightHanded = true;

   public TotemInspect() {
      super("Totem Inspect", "Hochwertige Inspect-Animationen für dein Totem: eine oder zwei Hände, Würfe, Partikel und Effekte.", Category.PLAYER);
      this.section(Section.COSMETICS);
      this.icon(Items.TOTEM_OF_UNDYING);
      this.items.setVisibility(() -> !this.onlyTotem.get());
      this.duration.setVisibility(this.customDuration::get);
      this.rotation.setVisibility(() -> this.style.is("Normal") || this.style.is("Kombi") || this.style.is("Leuchten"));
      this.group("Animation", new Setting[]{this.key, this.style, this.customDuration, this.duration, this.strength, this.rotation, this.wobble, this.loop});
      this.group("Effekte", new Setting[]{this.particles, this.sound, this.effects, this.camera});
      this.group("Items", new Setting[]{this.onlyTotem, this.items});
      FirstPersonFx.setItemAnimation(this::apply);
      FirstPersonFx.setOffArmAnimation(this::applyOffArm);
   }

   private boolean allowed(ItemStack stack) {
      return stack.is(Items.TOTEM_OF_UNDYING)
         ? true
         : !this.onlyTotem.get() && !stack.isEmpty() && this.items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
   }

   private boolean running() {
      return this.startNanos >= 0L;
   }

   private void start() {
      this.startNanos = System.nanoTime();
      this.inspected = this.mc.player.getMainHandItem().copy();
      this.slot = this.mc.player.getInventory().getSelectedSlot();
      this.resetCues();
      this.play(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.3F, 0.5F);
   }

   private void resetCues() {
      this.cue1 = this.cue2 = this.cue3 = false;
      this.lastT = 0.0F;
   }

   private void stop() {
      this.startNanos = -1L;
      CameraFx.setTilt(0.0F, 0.0F);
   }

   @Override
   protected void onDisable() {
      this.stop();
   }

   private float styleDuration() {
      String s = this.style.get();

      return switch (s) {
         case "Schnell" -> 1.0F;
         case "Zwei Hände" -> 3.2F;
         case "Spektakulär" -> 3.4F;
         case "Partikel-Wirbel", "Leuchten" -> 3.0F;
         case "Salto" -> 1.7F;
         case "Drehen" -> 1.8F;
         case "Kombi" -> 3.0F;
         default -> 2.4F;
      };
   }

   private float duration() {
      return this.customDuration.get() ? (float)this.duration.get().doubleValue() : this.styleDuration();
   }

   private float progress() {
      return this.running() ? (float)(System.nanoTime() - this.startNanos) / 1.0E9F / this.duration() : 0.0F;
   }

   @Override
   public void onTick() {
      LocalPlayer player = this.mc.player;
      boolean down = this.key.get().isPressed() && this.mc.screen == null;
      if (down && !this.keyDown) {
         if (this.running()) {
            this.stop();
         } else if (this.allowed(player.getMainHandItem())) {
            this.start();
         }
      }

      this.keyDown = down;
      if (this.running()) {
         ItemStack now = player.getMainHandItem();
         if (player.getInventory().getSelectedSlot() == this.slot
            && ItemStack.isSameItem(now, this.inspected)
            && !com.lennart.nebula.compat.Compat.isSwinging(player)
            && !player.isUsingItem()
            && this.mc.screen == null) {
            this.tickEffects(Math.min(1.0F, this.progress()));
         } else {
            this.stop();
         }
      }
   }

   private void tickEffects(float t) {
      String s = this.style.get();
      switch (s) {
         case "Spektakulär":
            if (!this.cue1 && t > 0.2F) {
               this.cue1 = true;
               this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.35F);
            }

            if (!this.cue2 && t > 0.68F) {
               this.cue2 = true;
               this.play(SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.2F, 0.6F);
               this.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.7F);
               this.burst(ParticleTypes.TOTEM_OF_UNDYING, 26, 0.18);
               this.burst(ParticleTypes.END_ROD, 8, 0.06);
               if (this.camera.get()) {
                  CameraFx.shake(1.6F * (float)this.strength.get().doubleValue(), 350L);
               }
            }

            if (t > 0.2F && t < 0.68F) {
               this.trail(ParticleTypes.ELECTRIC_SPARK, 1);
            }
            break;
         case "Partikel-Wirbel":
            if (t > 0.1F && t < 0.9F) {
               this.swirl(t);
            }

            if (!this.cue1 && t > 0.1F) {
               this.cue1 = true;
               this.play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.5F);
            }
            break;
         case "Leuchten":
            if (t > 0.15F && t < 0.85F && this.mc.player.tickCount % 2 == 0) {
               this.trail(ParticleTypes.END_ROD, 1);
            }

            if (!this.cue1 && t > 0.45F) {
               this.cue1 = true;
               this.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 0.6F);
               this.burst(ParticleTypes.GLOW, 6, 0.04);
            }
            break;
         case "Schnell":
            if (!this.cue1 && t > 0.15F) {
               this.cue1 = true;
               this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.2F);
            }
            break;
         case "Zwei Hände":
            if (!this.cue1 && t > 0.22F) {
               this.cue1 = true;
               this.play(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.5F, 0.5F);
            }

            if (!this.cue2 && t > 0.52F) {
               this.cue2 = true;
               this.play(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2F, 0.4F);
            }
            break;
         case "Salto":
         case "Kombi":
            float a = s.equals("Salto") ? 0.28F : 0.56F;
            float b = s.equals("Salto") ? 0.72F : 0.84F;
            if (!this.cue1 && t > a) {
               this.cue1 = true;
               this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 1.8F, 0.25F);
            }

            if (!this.cue2 && t >= b) {
               this.cue2 = true;
               this.play(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.4F, 0.6F);
            }
      }

      this.lastT = t;
   }

   private Vec3 handPos(double forward, double side, double down) {
      LocalPlayer p = this.mc.player;
      Vec3 eye = p.getEyePosition(1.0F);
      Vec3 look = p.getViewVector(1.0F);
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
      if (right.lengthSqr() < 1.0E-4) {
         right = new Vec3(1.0, 0.0, 0.0);
      }

      right = right.normalize();
      Vec3 up = right.cross(look).normalize();
      double dir = this.rightHanded ? 1.0 : -1.0;
      return eye.add(look.scale(forward)).add(right.scale(side * dir)).subtract(up.scale(down));
   }

   private void burst(ParticleOptions type, int count, double speed) {
      if (this.particles.get() && this.mc.level != null) {
         Vec3 c = this.handPos(0.9, 0.18, 0.05);

         for (int i = 0; i < count; i++) {
            double vx = (this.random.nextDouble() - 0.5) * 2.0 * speed;
            double vy = this.random.nextDouble() * speed * 1.4;
            double vz = (this.random.nextDouble() - 0.5) * 2.0 * speed;
            this.mc.level.addParticle(type, c.x, c.y, c.z, vx, vy, vz);
         }
      }
   }

   private void trail(ParticleOptions type, int count) {
      if (this.particles.get() && this.mc.level != null) {
         Vec3 c = this.handPos(0.85, 0.2, -0.1);

         for (int i = 0; i < count; i++) {
            this.mc
               .level
               .addParticle(
                  type,
                  c.x + (this.random.nextDouble() - 0.5) * 0.15,
                  c.y + this.random.nextDouble() * 0.15,
                  c.z + (this.random.nextDouble() - 0.5) * 0.15,
                  0.0,
                  0.01,
                  0.0
               );
         }
      }
   }

   private void swirl(float t) {
      if (this.particles.get() && this.mc.level != null) {
         Vec3 c = this.handPos(0.9, 0.12, 0.02);
         double ang = t * Math.PI * 10.0;

         for (int k = 0; k < 2; k++) {
            double a = ang + k * Math.PI;
            double r = 0.22;
            Vec3 look = this.mc.player.getViewVector(1.0F);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 p = c.add(right.scale(Math.cos(a) * r)).add(0.0, Math.sin(a) * r * 0.6 + (t - 0.5) * 0.2, 0.0);
            this.mc.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, p.x, p.y, p.z, 0.0, 0.02, 0.0);
         }
      }
   }

   private static float seg(float t, float a, float b) {
      return Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
   }

   private static float ease(float x) {
      return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
   }

   private static float back(float x) {
      float c = 1.4F;
      float y = x - 1.0F;
      return 1.0F + (c + 1.0F) * y * y * y + c * y * y;
   }

   private float currentT() {
      if (this.running() && this.isEnabled() && this.mc.player != null) {
         float t = this.progress();
         if (t >= 1.0F) {
            if (!this.loop.get()) {
               this.stop();
               return -1.0F;
            }

            this.startNanos = System.nanoTime();
            this.resetCues();
            t = 0.0F;
         }

         return t;
      } else {
         return -1.0F;
      }
   }

   private boolean apply(PoseStack m, float tickProgress, boolean right) {
      this.rightHanded = right;
      float t = this.currentT();
      if (t < 0.0F) {
         return false;
      } else {
         float s = (float)this.strength.get().doubleValue();
         float dir = right ? 1.0F : -1.0F;
         float raise = ease(seg(t, 0.0F, 0.14F)) * (1.0F - ease(seg(t, 0.86F, 1.0F)));
         float tx = -0.16F * dir * raise * s;
         float ty = 0.09F * raise * s;
         float tz = 0.1F * raise * s;
         float rx = -12.0F * raise;
         float ry = -28.0F * dir * raise;
         float rz = 0.0F;
         float scale = 1.0F;
         float rot = (float)this.rotation.get().doubleValue();
         String st = this.style.get();
         switch (st) {
            case "Schnell": {
               float r2 = ease(seg(t, 0.0F, 0.2F)) * (1.0F - ease(seg(t, 0.8F, 1.0F)));
               tx = -0.12F * dir * r2 * s;
               ty = 0.06F * r2 * s;
               rx = -8.0F * r2;
               ry = -20.0F * dir * r2;
               float u = seg(t, 0.15F, 0.75F);
               rz += 360.0F * dir * ease(u);
               ty += 0.08F * (float)Math.sin(u * Math.PI) * s;
               break;
            }
            case "Zwei Hände":
               float center = ease(seg(t, 0.0F, 0.2F)) * (1.0F - ease(seg(t, 0.85F, 1.0F)));
               tx = -0.3F * dir * center * s;
               ty = 0.12F * center * s;
               tz = 0.05F * center * s;
               rx = -15.0F * center;
               ry = -40.0F * dir * center;
               float turn1 = ease(seg(t, 0.25F, 0.45F));
               float turn2 = ease(seg(t, 0.55F, 0.75F));
               ry += 180.0F * dir * (turn1 + turn2);
               rz += 6.0F * dir * (float)Math.sin(seg(t, 0.25F, 0.75F) * Math.PI * 2.0) * s;
               break;
            case "Spektakulär":
               float toss = seg(t, 0.2F, 0.68F);
               float h = (float)Math.sin(toss * Math.PI);
               ty += 0.55F * h * s;
               tx += 0.06F * dir * h * s;
               rx += -1080.0F * ease(toss);
               ry += 360.0F * dir * ease(toss);
               float catchT = seg(t, 0.68F, 0.8F);
               if (catchT > 0.0F && catchT < 1.0F) {
                  ty -= 0.05F * (float)Math.sin(catchT * Math.PI) * s;
               }

               float showcase = ease(seg(t, 0.78F, 0.88F)) - ease(seg(t, 0.88F, 0.97F));
               ry += 25.0F * dir * showcase;
               scale = 1.0F + 0.12F * h;
               break;
            case "Partikel-Wirbel":
               ry += 360.0F * dir * ease(seg(t, 0.12F, 0.88F));
               ty += 0.05F * (float)Math.sin(seg(t, 0.12F, 0.88F) * Math.PI) * s;
               rz += 5.0F * (float)Math.sin(t * Math.PI * 6.0) * raise;
               break;
            case "Leuchten": {
               float turn = ease(seg(t, 0.15F, 0.45F)) - ease(seg(t, 0.55F, 0.85F));
               ry += rot * dir * turn;
               ty += 0.03F * (float)Math.sin(t * Math.PI * 4.0) * raise * s;
               float pulse = (float)Math.sin(seg(t, 0.35F, 0.6F) * Math.PI);
               scale = 1.0F + 0.15F * pulse * raise;
               break;
            }
            case "Salto": {
               float u = seg(t, 0.28F, 0.72F);
               ty += 0.32F * (float)Math.sin(u * Math.PI) * s;
               rx += -360.0F * (u < 1.0F ? ease(u) : back(1.0F));
               break;
            }
            case "Drehen":
               ry += 360.0F * dir * ease(seg(t, 0.18F, 0.8F));
               rz += 8.0F * dir * (float)Math.sin(seg(t, 0.18F, 0.8F) * Math.PI * 2.0) * s;
               break;
            case "Kombi": {
               float turn = ease(seg(t, 0.14F, 0.34F)) - ease(seg(t, 0.38F, 0.52F));
               ry += rot * dir * turn;
               rz += 8.0F * dir * (float)Math.sin(seg(t, 0.14F, 0.52F) * Math.PI) * s;
               float u = seg(t, 0.56F, 0.84F);
               ty += 0.3F * (float)Math.sin(u * Math.PI) * s;
               rx += -360.0F * ease(u);
               break;
            }
            default: {
               float turn = ease(seg(t, 0.18F, 0.48F)) - ease(seg(t, 0.58F, 0.84F));
               ry += rot * dir * turn;
               rz += 10.0F * dir * (float)Math.sin(seg(t, 0.18F, 0.84F) * Math.PI) * s;
            }
         }

         int wobbleCount = this.wobble.getInt();

         for (int k = 1; k <= wobbleCount; k++) {
            float amp = 3.5F / k * raise * s;
            rz += amp * (float)Math.sin(t * Math.PI * 2.0 * (1.3F + k * 0.9F) + k);
            rx += amp * 0.6F * (float)Math.cos(t * Math.PI * 2.0 * (1.1F + k * 0.7F) + k * 2);
         }

         m.translate(tx, ty, tz);
         m.translate(0.0F, 0.1F, 0.0F);
         m.mulPose(Axis.YP.rotationDegrees(ry));
         m.mulPose(Axis.XP.rotationDegrees(rx));
         m.mulPose(Axis.ZP.rotationDegrees(rz));
         if (scale != 1.0F) {
            m.scale(scale, scale, scale);
         }

         m.translate(0.0F, -0.1F, 0.0F);
         if (this.camera.get()) {
            CameraFx.setTilt(1.4F * dir * raise * s * (float)Math.sin(t * Math.PI * 2.0), -0.8F * raise * s);
         }

         return true;
      }
   }

   private boolean applyOffArm(PoseStack m, float tickProgress, boolean right) {
      if (this.running() && this.isEnabled() && this.style.is("Zwei Hände")) {
         float t = Math.min(1.0F, this.progress());
         float s = (float)this.strength.get().doubleValue();
         float in = ease(seg(t, 0.12F, 0.3F)) * (1.0F - ease(seg(t, 0.78F, 0.92F)));
         if (in <= 0.001F) {
            return false;
         } else {
            float dir = right ? 1.0F : -1.0F;
            float push = (float)Math.sin(seg(t, 0.3F, 0.75F) * Math.PI * 2.0) * 0.04F;
            m.translate(0.45F * dir * in * s, (-0.55F + 0.62F * in) * 1.0F + push, -0.12F * in);
            m.mulPose(Axis.YP.rotationDegrees(-35.0F * dir * in));
            m.mulPose(Axis.ZP.rotationDegrees(-25.0F * dir * in));
            m.mulPose(Axis.XP.rotationDegrees(-10.0F * in));
            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      if (this.running() && this.effects.get() && this.mc.options.getCameraType().isFirstPerson()) {
         float t = Math.min(1.0F, this.progress());
         String st = this.style.get();
         float glow = 0.0F;
         float flash = 0.0F;
         if (st.equals("Leuchten")) {
            glow = (float)Math.sin(seg(t, 0.1F, 0.9F) * Math.PI);
            flash = Math.max(0.0F, 1.0F - Math.abs(t - 0.47F) / 0.06F) * 0.35F;
         } else if (st.equals("Spektakulär")) {
            glow = (float)Math.sin(seg(t, 0.2F, 0.9F) * Math.PI) * 0.7F;
            flash = Math.max(0.0F, 1.0F - Math.abs(t - 0.69F) / 0.05F) * 0.45F;
         } else if (st.equals("Partikel-Wirbel")) {
            glow = (float)Math.sin(seg(t, 0.1F, 0.9F) * Math.PI) * 0.4F;
         }

         int w = ctx.guiWidth();
         int h = ctx.guiHeight();
         if (glow > 0.01F) {
            int a = Math.round(90.0F * glow);
            int gold = a << 24 | 16762941;
            int band = Math.max(20, h / 6);
            ctx.fillGradient(0, 0, w, band, gold, 16762941);
            ctx.fillGradient(0, h - band, w, h, 16762941, gold);
         }

         if (flash > 0.01F) {
            ctx.fill(0, 0, w, h, Math.round(255.0F * flash) << 24 | 16774084);
         }
      }
   }

   private void play(SoundEvent sound, float pitch, float volume) {
      if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
      }
   }
}
