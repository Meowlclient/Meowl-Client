package com.lennart.nebula.module.sound;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.SoundListScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.StringListSetting;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

public class SoundController extends Module {
   private final BooleanSetting muteAll = this.add(new BooleanSetting("Alle Sounds aus", "Schaltet sämtliche Sounds stumm", false));
   private final Map<SoundSource, NumberSetting> categories = new EnumMap<>(SoundSource.class);
   private final StringListSetting overrides = this.add(
      new StringListSetting("Einzelne Sounds", "Angepasste Einzel-Sounds (im Fenster \"Sounds suchen\" bearbeiten)", List.of())
   );
   private Map<Identifier, Float> parsed = Map.of();
   private List<String> parsedFrom;
   private int lastHash;

   public SoundController() {
      super("Sound Controller", "Lautstärke pro Kategorie und für einzelne Sounds (mit Suche).", Category.MISC);
      this.icon(Items.NOTE_BLOCK);
      this.addCategory(SoundSource.MASTER, "Master");
      this.addCategory(SoundSource.MUSIC, "Musik");
      this.addCategory(SoundSource.WEATHER, "Wetter");
      this.addCategory(SoundSource.BLOCKS, "Blöcke");
      this.addCategory(SoundSource.HOSTILE, "Feindliche Kreaturen");
      this.addCategory(SoundSource.NEUTRAL, "Freundliche Kreaturen");
      this.addCategory(SoundSource.PLAYERS, "Spieler");
      this.addCategory(SoundSource.AMBIENT, "Umgebung");
      this.addCategory(SoundSource.VOICE, "Stimme/Sprache");
      this.addCategory(SoundSource.UI, "UI");
      this.add(
         new ActionSetting(
            "Sounds suchen", "Einzelne Sounds suchen und anpassen", "Öffnen", () -> this.mc.gui.setScreen(new SoundListScreen(this.mc.gui.screen(), this))
         )
      );
      this.overrides.setVisibility(() -> false);
   }

   private void addCategory(SoundSource category, String name) {
      NumberSetting setting = this.add(new NumberSetting(name, "Lautstärke " + name + " (zusätzlich zu den Optionen)", 100.0, 0.0, 100.0, 1.0, "%"));
      setting.setVisibility(() -> !this.muteAll.get());
      this.categories.put(category, setting);
   }

   public Map<Identifier, Float> getOverrides() {
      if (this.parsedFrom != this.overrides.get()) {
         this.parsedFrom = this.overrides.get();
         Map<Identifier, Float> map = new HashMap<>();

         for (String entry : this.parsedFrom) {
            int eq = entry.lastIndexOf(61);
            if (eq > 0) {
               Identifier id = Identifier.tryParse(entry.substring(0, eq));

               try {
                  float pct = Float.parseFloat(entry.substring(eq + 1));
                  if (id != null) {
                     map.put(id, Math.max(0.0F, Math.min(200.0F, pct)) / 100.0F);
                  }
               } catch (NumberFormatException var7) {
               }
            }
         }

         this.parsed = map;
      }

      return this.parsed;
   }

   public void setOverride(Identifier id, float factor) {
      this.setOverride(id, factor, true);
   }

   public void setOverride(Identifier id, float factor, boolean save) {
      Map<Identifier, Float> map = new HashMap<>(this.getOverrides());
      if (Math.abs(factor - 1.0F) < 0.005F) {
         map.remove(id);
      } else {
         map.put(id, factor);
      }

      List<String> out = new ArrayList<>();
      map.forEach((k, v) -> out.add(k + "=" + Math.round(v * 100.0F)));
      out.sort(String::compareTo);
      this.overrides.set(out);
      if (save) {
         NebulaClient.getInstance().getConfigManager().save();
      }
   }

   public float getOverride(Identifier id) {
      return this.getOverrides().getOrDefault(id, 1.0F);
   }

   public float factor(SoundInstance sound) {
      if (!this.isEnabled()) {
         return 1.0F;
      } else if (this.muteAll.get()) {
         return 0.0F;
      } else {
         float f = (float)(this.categories.get(SoundSource.MASTER).get() / 100.0);
         NumberSetting cat = this.categories.get(sound.getSource());
         if (cat != null && sound.getSource() != SoundSource.MASTER) {
            f *= (float)(cat.get() / 100.0);
         }

         Identifier id = sound.getIdentifier();
         if (id != null) {
            f *= this.getOverride(id);
         }

         return f;
      }
   }

   public static float factorFor(SoundInstance sound) {
      NebulaClient client = NebulaClient.getInstance();
      if (client != null && client.getModuleManager() != null && sound != null) {
         SoundController controller = client.getModuleManager().get(SoundController.class);
         return controller == null ? 1.0F : controller.factor(sound);
      } else {
         return 1.0F;
      }
   }

   @Override
   public void onTick() {
      int hash = this.muteAll.get() ? 1 : 0;

      for (NumberSetting s : this.categories.values()) {
         hash = hash * 31 + s.get().hashCode();
      }

      hash = hash * 31 + this.overrides.get().hashCode();
      if (hash != this.lastHash) {
         this.lastHash = hash;
         this.refreshPlaying();
      }
   }

   @Override
   protected void onEnable() {
      this.refreshPlaying();
   }

   @Override
   protected void onDisable() {
      this.refreshPlaying();
   }

   public void refreshPlaying() {
      if (this.mc.getSoundManager() != null) {
         this.mc.getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
      }
   }
}
