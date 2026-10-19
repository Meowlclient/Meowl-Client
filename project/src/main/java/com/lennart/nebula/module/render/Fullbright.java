package com.lennart.nebula.module.render;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

public class Fullbright extends Module {
   public static final String MODE_GAMMA = "Gamma";
   public static final String MODE_NIGHT_VISION = "Night Vision";
   private final ModeSetting mode = this.add(
      new ModeSetting("Mode", "Gamma = Helligkeit überschreiben, Night Vision = clientseitiger Nachtsicht-Effekt", "Gamma", "Gamma", "Night Vision")
   );
   private final NumberSetting brightness = this.add(
      new NumberSetting("Helligkeit", "Gamma-Wert im Modus Gamma (Vanilla-Maximum ist 100 %)", 1600.0, 100.0, 1600.0, 50.0, "%")
   );
   private boolean addedNightVision;

   public Fullbright() {
      super("Fullbright", "Macht die Welt dauerhaft hell.", Category.RENDER);
      this.icon(Items.TORCH);
      this.brightness.setVisibility(() -> this.mode.is("Gamma"));
   }

   public static Double gammaOverride() {
      NebulaClient client = NebulaClient.getInstance();
      if (client != null && client.getModuleManager() != null) {
         Fullbright module = client.getModuleManager().get(Fullbright.class);
         return module != null && module.isEnabled() && module.mode.is("Gamma") ? module.brightness.get() / 100.0 : null;
      } else {
         return null;
      }
   }

   @Override
   public void onTick() {
      LocalPlayer player = this.mc.player;
      if (player != null) {
         if (this.mode.is("Night Vision")) {
            if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
               player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
               this.addedNightVision = true;
            }
         } else {
            this.removeOwnNightVision();
         }
      }
   }

   @Override
   protected void onDisable() {
      this.removeOwnNightVision();
   }

   private void removeOwnNightVision() {
      LocalPlayer player = this.mc.player;
      if (this.addedNightVision && player != null) {
         MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
         if (effect != null && effect.isInfiniteDuration() && !effect.isVisible()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
         }

         this.addedNightVision = false;
      } else {
         this.addedNightVision = false;
      }
   }
}
