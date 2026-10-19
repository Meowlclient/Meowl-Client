package com.lennart.nebula.module.render;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import java.util.Random;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;

/**
 * Partikel einstellen: Menge je Art (Treffer, Explosionen, Totem, Tränke, Feuer &amp; Rauch, Wetter, Blöcke, Rest).
 * 0 % = aus, Treffer-Partikel lassen sich bis 300 % verstärken.
 */
public class Particles extends Module {
   private static Particles instance;
   private final NumberSetting hits = this.add(new NumberSetting("Treffer", "Krit-, Verzauberungs-, Schwung- und Schadens-Partikel (bis 300 %)", 100.0, 0.0, 300.0, 10.0, "%"));
   private final NumberSetting explosions = this.add(new NumberSetting("Explosionen", "TNT, Creeper, Kristalle, Windladungen", 100.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting totem = this.add(new NumberSetting("Totem", "Totem-Partikel beim Auslösen", 100.0, 0.0, 300.0, 10.0, "%"));
   private final NumberSetting potions = this.add(new NumberSetting("Tränke & Effekte", "Wirbel von Trank-Effekten und Wurftränken", 100.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting fire = this.add(new NumberSetting("Feuer & Rauch", "Flammen, Rauch, Lagerfeuer, Lava", 100.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting weather = this.add(new NumberSetting("Wetter & Wasser", "Regentropfen, Spritzer, Tropfen", 100.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting blocks = this.add(new NumberSetting("Blöcke", "Splitter beim Abbauen und Zerbrechen von Blöcken", 100.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting others = this.add(new NumberSetting("Alle anderen", "Alle übrigen Partikel", 100.0, 0.0, 100.0, 5.0, "%"));
   private final BooleanSetting ownEffects = this.add(
      new BooleanSetting("Eigene Effekt-Partikel aus", "Keine Trank-Wirbel um dich selbst (z.B. Stärke, Speed)", false)
   );
   private final Random random = new Random();
   private boolean spawningExtra;

   public Particles() {
      super("Particles", "Partikel einstellen: Menge je Art – Treffer verstärken, Explosionen oder Regen weniger, Blocksplitter aus …", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.BLAZE_POWDER);
      instance = this;
      this.group("Menge", new Setting[]{this.hits, this.totem, this.explosions, this.potions, this.fire, this.weather, this.blocks, this.others, this.ownEffects});
   }

   public static Particles active() {
      return instance != null && instance.isEnabled() ? instance : null;
   }

   private NumberSetting category(ParticleType<?> t) {
      if (t == ParticleTypes.CRIT || t == ParticleTypes.ENCHANTED_HIT || t == ParticleTypes.SWEEP_ATTACK || t == ParticleTypes.DAMAGE_INDICATOR) {
         return this.hits;
      } else if (t == ParticleTypes.EXPLOSION || t == ParticleTypes.EXPLOSION_EMITTER || t == ParticleTypes.GUST || t == ParticleTypes.GUST_EMITTER_LARGE || t == ParticleTypes.GUST_EMITTER_SMALL) {
         return this.explosions;
      } else if (t == ParticleTypes.TOTEM_OF_UNDYING) {
         return this.totem;
      } else if (t == ParticleTypes.ENTITY_EFFECT || t == ParticleTypes.EFFECT || t == ParticleTypes.INSTANT_EFFECT || t == ParticleTypes.WITCH) {
         return this.potions;
      } else if (t == ParticleTypes.FLAME
         || t == ParticleTypes.SOUL_FIRE_FLAME
         || t == ParticleTypes.SMALL_FLAME
         || t == ParticleTypes.SMOKE
         || t == ParticleTypes.LARGE_SMOKE
         || t == ParticleTypes.CAMPFIRE_COSY_SMOKE
         || t == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
         || t == ParticleTypes.LAVA) {
         return this.fire;
      } else if (t == ParticleTypes.RAIN || t == ParticleTypes.SPLASH || t == ParticleTypes.DRIPPING_WATER || t == ParticleTypes.FALLING_WATER || t == ParticleTypes.BUBBLE) {
         return this.weather;
      } else if (t == ParticleTypes.BLOCK || t == ParticleTypes.DUST_PILLAR || t == ParticleTypes.FALLING_DUST) {
         return this.blocks;
      } else {
         return this.others;
      }
   }

   /**
    * Wie oft ein Partikel erzeugt wird: 0 = gar nicht, 1 = normal, mehr = zusätzliche Kopien. Kopien selbst werden
    * nicht noch einmal vervielfacht.
    */
   public int copies(ParticleOptions options, double x, double y, double z) {
      if (this.spawningExtra) {
         return 1;
      }

      if (this.ownEffects.get() && this.mc.player != null && options.getType() == ParticleTypes.ENTITY_EFFECT && this.mc.player.distanceToSqr(x, y, z) < 4.0) {
         return 0;
      }

      double f = this.category(options.getType()).get() / 100.0;
      int whole = (int)Math.floor(f);
      return whole + (this.random.nextDouble() < f - whole ? 1 : 0);
   }

   public void beginExtra() {
      this.spawningExtra = true;
   }

   public void endExtra() {
      this.spawningExtra = false;
   }

   /** Blocksplitter beim Abbauen/Zerbrechen erlaubt? */
   public boolean blockEffects() {
      return this.random.nextDouble() * 100.0 < this.blocks.get();
   }
}
