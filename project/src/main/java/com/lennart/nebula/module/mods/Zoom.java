package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.world.item.Items;

/** Zoom: Taste halten (oder umschalten) – weich heranzoomen, mit dem Mausrad Stärke ändern, Maus wird feiner. */
public class Zoom extends Module {
   private static Zoom instance;
   private final KeybindSetting key = this.add(new KeybindSetting("Zoom-Taste", "Zum Zoomen", Bind.keyboard(InputConstants.KEY_C)));
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Taste halten oder an/aus schalten", "Halten", "Halten", "Umschalten"));
   private final NumberSetting strength = this.add(new NumberSetting("Stärke", "Wie stark gezoomt wird", 4.0, 1.5, 15.0, 0.5, "x"));
   private final BooleanSetting scroll = this.add(new BooleanSetting("Mausrad", "Mit dem Mausrad Zoom ändern, solange gezoomt wird", true));
   private final BooleanSetting smooth = this.add(new BooleanSetting("Weich", "Weich hinein- und herauszoomen", true));
   private final BooleanSetting slowMouse = this.add(new BooleanSetting("Feinere Maus", "Maus bewegt sich beim Zoomen langsamer", true));
   private boolean toggled;
   private boolean keyDown;
   private float scrollFactor = 1.0F;
   private float current = 1.0F;
   private long last;

   public Zoom() {
      super("Zoom", "Heranzoomen per Taste – weich, mit Mausrad einstellbar und feinerer Maus.", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.SPYGLASS);
      instance = this;
      this.group("Zoom", new Setting[]{this.key, this.mode, this.strength, this.scroll, this.smooth, this.slowMouse});
      this.setEnabled(true);
   }

   private boolean wanted() {
      if (!this.isEnabled() || this.mc.player == null || this.mc.screen != null) {
         return this.mode.is("Umschalten") && this.toggled && this.isEnabled();
      } else {
         boolean down = this.key.get().isPressed();
         if (this.mode.is("Umschalten")) {
            if (down && !this.keyDown) {
               this.toggled = !this.toggled;
            }

            this.keyDown = down;
            return this.toggled;
         } else {
            this.keyDown = down;
            return down;
         }
      }
   }

   /** Ziel-Zoom (z.B. 4 = viermal näher). */
   private float target() {
      if (!this.wanted()) {
         this.scrollFactor = 1.0F;
         return 1.0F;
      } else {
         return Math.max(1.0F, (float)this.strength.get().doubleValue() * this.scrollFactor);
      }
   }

   private float update() {
      long now = System.nanoTime();
      float dt = this.last == 0L ? 0.0F : Math.min(0.1F, (now - this.last) / 1.0E9F);
      this.last = now;
      float t = this.target();
      if (!this.smooth.get()) {
         this.current = t;
      } else {
         this.current += (t - this.current) * (1.0F - (float)Math.exp(-dt * 14.0F));
      }

      return this.current;
   }

   /** Faktor für das Sichtfeld (1 = normal, 0.25 = 4x Zoom). */
   public static float fovFactor() {
      Zoom z = instance;
      if (z == null) {
         return 1.0F;
      } else {
         float zoom = z.update();
         return zoom <= 1.001F ? 1.0F : 1.0F / zoom;
      }
   }

   public static boolean zooming() {
      Zoom z = instance;
      return z != null && z.current > 1.01F;
   }

   /** Mausbewegung beim Zoomen feiner. */
   public static float mouseFactor() {
      Zoom z = instance;
      return z != null && z.slowMouse.get() && z.current > 1.01F ? 1.0F / z.current : 1.0F;
   }

   /** Mausrad beim Zoomen: Stärke ändern. true = verbraucht (kein Hotbar-Wechsel). */
   public static boolean onScroll(double amount) {
      Zoom z = instance;
      if (z != null && z.scroll.get() && z.wantedNow() && amount != 0.0) {
         z.scrollFactor = Math.max(0.25F, Math.min(4.0F, z.scrollFactor * (amount > 0.0 ? 1.15F : 1.0F / 1.15F)));
         return true;
      } else {
         return false;
      }
   }

   private boolean wantedNow() {
      return this.isEnabled() && (this.mode.is("Umschalten") ? this.toggled : this.key.get().isPressed());
   }
}
