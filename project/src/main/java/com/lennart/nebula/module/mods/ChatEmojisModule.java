package com.lennart.nebula.module.mods;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import net.minecraft.world.item.Items;

/** Emojis im Chat: Meowl-Bilder (nur Meowl-Spieler sehen sie), Symbole und Text-Emoticons über einen Knopf in der Chat-Zeile. */
public class ChatEmojisModule extends Module {
   private final BooleanSetting button = this.add(new BooleanSetting("Emoji-Knopf", "Knopf rechts in der Chat-Zeile öffnet die Auswahl", true));
   private final BooleanSetting images = this.add(
      new BooleanSetting("Meowl-Emojis anzeigen", ":fire:, :heart:, … im Chat als Bild anzeigen (andere Meowl-Spieler sehen sie auch)", true)
   );

   public ChatEmojisModule() {
      super("Chat Emojis", "Emojis im Chat: eigene Meowl-Emojis, die nur Meowl-Spieler als Bild sehen, dazu Symbole und Emoticons für alle.", Category.MISC);
      this.section(Section.CLIENT);
      this.icon(Items.SUNFLOWER);
      this.setEnabled(true);
   }

   private static ChatEmojisModule get() {
      NebulaClient c = NebulaClient.getInstance();
      return c == null ? null : c.getModuleManager().get(ChatEmojisModule.class);
   }

   public static boolean button() {
      ChatEmojisModule m = get();
      return m != null && m.isEnabled() && m.button.get();
   }

   public static boolean images() {
      ChatEmojisModule m = get();
      return m != null && m.isEnabled() && m.images.get();
   }
}