package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

/**
 * Dein eigener Nametag in der 3. Person (F5) – genau der Minecraft-Nametag, den andere über dir sehen (Server-Farben,
 * Ränge, Teams, Punkte darunter). Funktion wie die Mod „Third Person Nametags“ von nathancord (MIT-Lizenz), hier als
 * eigenes Meowl-Modul umgesetzt.
 */
public class ThirdPersonNametag extends Module {
   private static ThirdPersonNametag instance;

   public ThirdPersonNametag() {
      super("3rd Person Nametag", "Dein eigener Nametag in F5 – genau so, wie ihn andere über dir sehen (Server-Farben, Ränge, Teams).", Category.RENDER);
      this.icon(Items.NAME_TAG);
      instance = this;
      // Teil von „Nametags“ (Schalter „Eigener Nametag“)
      this.hidden();
   }

   /** Soll der eigene Spieler seinen Vanilla-Nametag sehen? */
   public static boolean showOwn(Entity entity) {
      ThirdPersonNametag m = instance;
      Minecraft mc = Minecraft.getInstance();
      return m != null
         && m.isEnabled()
         && entity == mc.player
         && !mc.options.getCameraType().isFirstPerson()
         && !mc.options.hideGui
         && !entity.isInvisibleTo(mc.player)
         && !entity.isVehicle();
   }
}