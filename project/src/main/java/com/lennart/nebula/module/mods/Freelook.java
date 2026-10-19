package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Freelook: Taste halten – Kamera frei um dich drehen, ohne dass sich deine Figur mitdreht. */
public class Freelook extends Module {
   private static Freelook instance;
   private final KeybindSetting key = this.add(new KeybindSetting("Freelook-Taste", "Kamera frei drehen", Bind.keyboard(InputConstants.KEY_LALT)));
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Taste halten oder an/aus schalten", "Halten", "Halten", "Umschalten"));
   private final BooleanSetting thirdPerson = this.add(
      new BooleanSetting("In 3. Person", "Beim Freelook automatisch in die Ansicht von hinten wechseln", true)
   );
   private final BooleanSetting invert = this.add(new BooleanSetting("Hoch/Runter umkehren", "Maus nach oben = nach unten schauen", false));
   private boolean active;
   private boolean toggled;
   private boolean keyDown;
   private float yaw;
   private float pitch;
   private CameraType before;

   public Freelook() {
      super("Freelook", "Kamera frei um dich drehen (Taste halten), ohne dass sich deine Blickrichtung ändert.", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.ENDER_EYE);
      instance = this;
      this.group("Freelook", new Setting[]{this.key, this.mode, this.thirdPerson, this.invert});
   }

   @Override
   public void onTick() {
      boolean want;
      boolean down = this.mc.screen == null && this.key.get().isPressed();
      if (this.mode.is("Umschalten")) {
         if (down && !this.keyDown) {
            this.toggled = !this.toggled;
         }

         want = this.toggled;
      } else {
         want = down;
      }

      this.keyDown = down;
      if (want && !this.active && this.mc.player != null) {
         this.active = true;
         this.yaw = this.mc.player.getYRot();
         this.pitch = this.mc.player.getXRot();
         if (this.thirdPerson.get() && this.mc.options.getCameraType().isFirstPerson()) {
            this.before = this.mc.options.getCameraType();
            this.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         }
      } else if (!want && this.active) {
         this.stop();
      }
   }

   private void stop() {
      this.active = false;
      this.toggled = false;
      if (this.before != null) {
         this.mc.options.setCameraType(this.before);
         this.before = null;
      }
   }

   @Override
   protected void onDisable() {
      if (this.active) {
         this.stop();
      }
   }

   public static boolean active() {
      return instance != null && instance.isEnabled() && instance.active;
   }

   /** Mausbewegung im Freelook: nur die Kamera dreht sich (Werte wie bei Entity.turn). */
   public static void turn(double dx, double dy) {
      Freelook f = instance;
      f.yaw += (float)dx * 0.15F;
      f.pitch = Mth.clamp(f.pitch + (float)dy * 0.15F * (f.invert.get() ? -1.0F : 1.0F), -90.0F, 90.0F);
   }

   public static float yaw() {
      return instance.yaw;
   }

   public static float pitch() {
      return instance.pitch;
   }
}
