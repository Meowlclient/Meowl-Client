package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.world.item.Items;

/**
 * Andere Clients: Meowl-Kosmetik und Meowl-Emotes ausblenden, damit Kosmetik und Emotes anderer Mods (z.B. Essential,
 * NoRisk) zu sehen sind – so, als wäre Meowl nicht installiert.
 */
public class OtherClients extends Module {
   private static OtherClients instance;
   private final BooleanSetting cosmetics = this.add(
      new BooleanSetting("Meowl-Kosmetik zeigen", "Aus = keine Meowl-Hüte, -Flügel, -Uhren usw. – Kosmetik anderer Mods bleibt sichtbar", true)
   );
   private final BooleanSetting emotes = this.add(
      new BooleanSetting("Meowl-Emotes zeigen", "Aus = Meowl-Emotes nicht abspielen – Emotes anderer Mods bleiben sichtbar", true)
   );

   public OtherClients() {
      super(
         "Andere Clients",
         "Kosmetik und Emotes anderer Mods (Essential, NoRisk …) sehen: Meowl-Kosmetik oder -Emotes einzeln ausschalten.",
         Category.MISC
      );
      this.section(Section.COSMETICS);
      this.icon(Items.ENDER_EYE);
      instance = this;
      this.group("Anzeigen", new Setting[]{this.cosmetics, this.emotes});
      this.setEnabled(true);
   }

   private static boolean on() {
      return instance != null && instance.isEnabled();
   }

   public static boolean showMeowlCosmetics() {
      return !on() || instance.cosmetics.get();
   }

   public static boolean showMeowlEmotes() {
      return !on() || instance.emotes.get();
   }
}