package com.lennart.nebula.module.mods;

import com.google.gson.JsonObject;
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
import com.lennart.nebula.util.MeowlNetwork;
import com.lennart.nebula.util.WorldRender;
import com.lennart.nebula.watch.WatchModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

public class Watches extends Module {
   public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
   private static Watches instance;
   private final ModeSetting model = this.add(new ModeSetting("Modell", "Welche Uhr du trägst", "GEQI Watch", WatchModel.MODELS));
   private final ModeSetting wrist = this.add(new ModeSetting("Handgelenk", "An welchem Arm die Uhr sitzt", "Hauptarm", "Hauptarm", "Zweiter Arm"));
   private final NumberSetting size = this.add(new NumberSetting("Größe", "Größe des Uhrgehäuses", 1.0, 0.8, 1.3, 0.05, "x"));
   private final NumberSetting position = this.add(new NumberSetting("Position", "Uhr am Arm verschieben (+ Richtung Hand)", 0.0, -1.5, 1.0, 0.25, "px"));
   private final BooleanSetting customColor = this.add(new BooleanSetting("Eigene Farbe", "Uhr in einer beliebigen Farbe (Armband, Gehäuse, Steine)", false));
   private final com.lennart.nebula.setting.ColorSetting color = this.add(new com.lennart.nebula.setting.ColorSetting("Farbe", "Farbe der Uhr", 0xFF3A7BFF, false));
   private final KeybindSetting inspectKey = this.add(new KeybindSetting("Inspect-Taste", "Arm heben und auf die Uhr schauen", Bind.NONE));
   private final BooleanSetting onlyEmpty = this.add(new BooleanSetting("Nur mit leerer Hand", "Uhr nur zeigen, wenn die Hand leer ist", true));
   private final BooleanSetting thirdPerson = this.add(new BooleanSetting("In 3rd Person zeigen", "Uhr auch am Spielermodell (F5)", true));
   private final BooleanSetting timeOverlay = this.add(new BooleanSetting("Uhrzeit beim Inspect", "Große deutsche Uhrzeit beim Inspect einblenden", true));
   private final BooleanSetting sound = this.add(new BooleanSetting("Inspect-Sound", "Leises Klicken beim Hochheben", true));
   private final BooleanSetting share = this.add(new BooleanSetting("Uhr teilen", "Andere Meowl-Spieler sehen deine Uhr am Arm", true));
   private final BooleanSetting showOthers = this.add(
      new BooleanSetting("Uhren anderer zeigen", "Uhren anderer Meowl-Spieler am Arm anzeigen (auch wenn du selbst keine trägst)", true)
   );
   private boolean keyDown;
   private long inspectStart;
   private boolean inspecting;
   private static final float IN = 0.45F;
   private static final float HOLD = 2.6F;
   private static final float OUT = 0.45F;

   public Watches() {
      super("Watches", "Armbanduhr am Handgelenk – inkl. luxuriöser GEQI Watch. Inspect-Taste zeigt die deutsche Uhrzeit.", Category.PLAYER);
      this.section(Section.COSMETICS);
      this.icon(Items.CLOCK);
      instance = this;
      this.group("Uhr", new Setting[]{this.model, this.customColor, this.color, this.wrist, this.size, this.position, this.onlyEmpty, this.thirdPerson});
      this.color.setVisibility(this.customColor::get);
      this.group("Inspect", new Setting[]{this.inspectKey, this.timeOverlay, this.sound});
      this.group("Andere Spieler", new Setting[]{this.share, this.showOthers});
   }

   public static void presenceFields(JsonObject o) {
      Watches w = instance;
      if (w != null && w.isEnabled() && w.share.get()) {
         o.addProperty("watch", w.model.get());
         o.addProperty("watchOff", w.wrist.is("Zweiter Arm"));
         o.addProperty("watchSize", w.size.get());
         o.addProperty("watchPos", w.position.get());
         if (w.customColor.get()) {
            o.addProperty("watchColor", String.format("%06X", w.color.get() & 16777215));
         }
      }
   }

   public static MeowlNetwork.Watch othersWatch(Player player) {
      Watches w = instance;
      if (w != null && w.showOthers.get() && player != w.mc.player) {
         MeowlNetwork.Peer peer = MeowlPet.peer(player);
         return peer == null ? null : peer.watch();
      } else {
         return null;
      }
   }

   public static void drawOther(PoseStack m, VertexConsumer vc, MeowlNetwork.Watch watch, boolean right, boolean slim) {
      String model = "GEQI Watch";

      for (String known : WatchModel.MODELS) {
         if (known.equals(watch.model())) {
            model = known;
         }
      }

      WatchModel.draw(m, com.lennart.nebula.util.RecolorConsumer.wrap(vc, watch.color()), model, right, slim, watch.size(), watch.position(), 0.9F, berlinNow());
   }

   /** Vorschau im Shop: dieses Modell mit deinen aktuellen Einstellungen (Größe, Position, Farbe). */
   public static MeowlNetwork.Watch previewWatch(String model) {
      Watches w = instance;
      if (w == null) {
         return new MeowlNetwork.Watch(model, false, 1.0F, 0.0F, 0);
      }

      return new MeowlNetwork.Watch(model, false, (float)w.size.get().doubleValue(), (float)w.position.get().doubleValue(), w.customColor.get() ? w.color.get() | 0xFF000000 : 0);
   }

   public static Watches active() {
      return instance != null && instance.isEnabled() ? instance : null;
   }

   public boolean thirdPerson() {
      return this.thirdPerson.get();
   }

   public boolean onlyEmptyHand() {
      return this.onlyEmpty.get();
   }

   public HumanoidArm watchArm(Player player) {
      return this.wrist.is("Hauptarm") ? player.getMainArm() : player.getMainArm().getOpposite();
   }

   private InteractionHand handFor(Player player, HumanoidArm arm) {
      return arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
   }

   public static LocalTime berlinNow() {
      return ZonedDateTime.now(BERLIN).toLocalTime();
   }

   public void drawWatch(PoseStack m, VertexConsumer vc, boolean right, boolean slim, float light) {
      WatchModel.draw(m, com.lennart.nebula.util.RecolorConsumer.wrap(vc, this.customColor.get() ? this.color.get() | 0xFF000000 : 0), this.model.get(), right, slim, (float)this.size.get().doubleValue(), (float)this.position.get().doubleValue(), light, berlinNow());
   }

   private float inspectAmount() {
      if (!this.inspecting) {
         return 0.0F;
      } else {
         float t = (float)(System.currentTimeMillis() - this.inspectStart) / 1000.0F;
         float p;
         if (t < 0.45F) {
            p = t / 0.45F;
         } else if (t < 3.05F) {
            p = 1.0F;
         } else {
            p = 1.0F - (t - 0.45F - 2.6F) / 0.45F;
         }

         p = Math.max(0.0F, Math.min(1.0F, p));
         return p * p * (3.0F - 2.0F * p);
      }
   }

   private void startInspect() {
      if (this.mc.player != null) {
         HumanoidArm arm = this.watchArm(this.mc.player);
         if (this.mc.player.getItemInHand(this.handFor(this.mc.player, arm)).isEmpty()) {
            float t = (float)(System.currentTimeMillis() - this.inspectStart) / 1000.0F;
            if (this.inspecting && t < 3.05F) {
               this.inspectStart = System.currentTimeMillis() - 3050L;
            } else {
               this.inspecting = true;
               this.inspectStart = System.currentTimeMillis();
               if (this.sound.get()) {
                  this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.6F, 0.35F));
               }
            }
         }
      }
   }

   private boolean applyInspect(PoseStack matrices, boolean rightArm) {
      float p = this.inspectAmount();
      if (p <= 0.0F) {
         return false;
      } else {
         float f = rightArm ? 1.0F : -1.0F;
         float wobble = (float)Math.sin(System.currentTimeMillis() / 600.0) * 0.008F * p;
         matrices.translate(f * 0.499F * p, -1.243F * p + wobble, 0.159F * p);
         Quaternionf full = new Quaternionf()
            .rotateX((float)Math.toRadians(20.0))
            .rotateY((float)Math.toRadians(f * 45.0))
            .rotateZ((float)Math.toRadians(f * 90.0));
         matrices.mulPose(new Quaternionf().slerp(full, p));
         return true;
      }
   }

   @Override
   protected void onEnable() {
      FirstPersonFx.setWatchArms(
         (matrices, tick, rightHanded) -> this.mc.player != null
            && this.watchArm(this.mc.player) == this.mc.player.getMainArm()
            && this.applyInspect(matrices, rightHanded),
         (matrices, tick, rightHanded) -> this.mc.player != null
            && this.watchArm(this.mc.player) != this.mc.player.getMainArm()
            && this.applyInspect(matrices, !rightHanded)
      );
   }

   @Override
   protected void onDisable() {
      FirstPersonFx.setWatchArms(null, null);
      this.inspecting = false;
   }

   @Override
   public void onTick() {
      boolean d = this.inspectKey.get().isPressed();
      if (d && !this.keyDown && this.mc.screen == null) {
         this.startInspect();
      }

      this.keyDown = d;
      if (this.inspecting && (float)(System.currentTimeMillis() - this.inspectStart) / 1000.0F > 3.5F) {
         this.inspecting = false;
      }

      if (this.inspecting && this.mc.player != null) {
         HumanoidArm arm = this.watchArm(this.mc.player);
         if (!this.mc.player.getItemInHand(this.handFor(this.mc.player, arm)).isEmpty()) {
            this.inspecting = false;
         }
      }
   }

   public void renderFirstPersonArm(PoseStack matrices, SubmitNodeCollector queue, ModelPart armPart, boolean rightArm) {
      AbstractClientPlayer player = this.mc.player;
      if (player != null) {
         HumanoidArm arm = this.watchArm(player);
         if (arm == HumanoidArm.RIGHT == rightArm && (!this.onlyEmpty.get() || player.getItemInHand(this.handFor(player, arm)).isEmpty())) {
            boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
            matrices.pushPose();
            armPart.translateAndRotate(matrices);
            queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> {
               PoseStack local = new PoseStack();
               local.last().pose().set(entry.pose());
               local.last().normal().set(entry.normal());
               this.drawWatch(local, vc, rightArm, slim, 1.0F);
            });
            matrices.popPose();
         }
      }
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      float p = this.inspectAmount();
      if (p > 0.02F && this.timeOverlay.get() && this.mc.options.getCameraType().isFirstPerson()) {
         ZonedDateTime now = ZonedDateTime.now(BERLIN);
         String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
         String date = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN)
            + ", "
            + now.getDayOfMonth()
            + ". "
            + now.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN)
            + " · Europe/Berlin";
         int w = ctx.guiWidth();
         int h = ctx.guiHeight();
         int a = Math.round(255.0F * p);
         float scale = 2.0F;
         int tw = this.mc.font.width(time);
         int cx = w / 2;
         int y = Math.round(h * 0.18F) - Math.round((1.0F - p) * 10.0F);
         ctx.pose().pushMatrix();
         ctx.pose().translate(cx - tw * scale / 2.0F, y);
         ctx.pose().scale(scale, scale);
         ctx.drawString(this.mc.font, time, 0, 0, a << 24 | 16777215, true);
         ctx.pose().popMatrix();
         Component title = Component.literal(this.model.get()).withStyle(ChatFormatting.BOLD);
         int dw = this.mc.font.width(date);
         ctx.drawString(this.mc.font, date, cx - dw / 2, y + 20, a << 24 | 13225432, true);
         int ttw = this.mc.font.width(title);
         ctx.drawString(this.mc.font, title, cx - ttw / 2, y - 12, a << 24 | (this.model.is("GEQI Watch") ? 16731486 : 15124346), true);
      }
   }
}
