package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.emote.EmoteType;
import com.lennart.nebula.gui.EmoteWheelScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.MeowlCommands;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.Items;

/**
 * Emotes mit Emote-Rad. Alle Meowl-Spieler auf demselben Minecraft-Server sehen deine Emotes (über den Meowl
 * Server); du siehst ihre. Die Tasten (Emote-Rad, Emote beenden) liegen in den Minecraft-Steuerungen.
 */
public class Emotes extends Module {
   private final BooleanSetting thirdPerson = this.add(
      new BooleanSetting("Third-Person bei Emotes", "Wechselt während eines Emotes in die Ansicht von hinten und danach zurück", true)
         .alias(new String[]{"Third-Person beim Sitzen"})
   );
   private final BooleanSetting share = this.add(
      new BooleanSetting("Für andere sichtbar", "Andere Spieler mit Meowl Client sehen deine Emotes (braucht den Meowl Server)", true)
   );
   private final BooleanSetting showOthers = this.add(
      new BooleanSetting("Emotes anderer anzeigen", "Emotes anderer Meowl-Spieler an ihren Charakteren zeigen", true)
   );
   /** Belegung der 8 Plätze im Emote-Rad (IDs mit Komma getrennt, leer = freier Platz). */
   private final TextSetting loadout = this.add(new TextSetting("Rad-Belegung", "Welche Emotes im Emote-Rad liegen", DEFAULT_LOADOUT, 200));
   public static final String DEFAULT_LOADOUT = "wave,cheer,dance,griddy,six_seven,dab,clap,laugh";
   public static final int SLOTS = 8;
   private boolean wheelDown;
   private boolean stopDown;
   private boolean attackDown;
   private CameraType before;
   private boolean switched;

   public Emotes() {
      super("Emotes", "Emote-Rad mit Winken, Tanzen, Jubeln, Fahrzeugen und mehr – alle Meowl-Spieler sehen deine Emotes. Tasten: Minecraft-Steuerung → Meowl Client.", Category.PLAYER);
      this.section(Section.COSMETICS);
      this.icon(Items.ARMOR_STAND);
      this.add(new ActionSetting("Emote-Rad öffnen", "Öffnet das Emote-Rad", "Öffnen", () -> this.mc.schedule(() -> this.openWheel(-1))));
      this.add(new ActionSetting("Emotes auswählen", "Alle Emotes ansehen und die 8 Plätze im Emote-Rad belegen", "Auswahl öffnen", () -> this.mc.schedule(this::openPicker)));
      this.loadout.setVisibility(() -> false);
      this.group("Emote-Rad", new Setting[]{this.thirdPerson});
      this.group("Mit anderen", new Setting[]{this.share, this.showOthers});
      this.setEnabled(true);
      EmoteState.hook();
      MeowlCommands.add(
         "emote",
         "emote <name>|stop|list §7– Emote abspielen (ohne Namen: Emote-Rad)",
         root -> ClientCommands.literal(root)
            .executes(c -> {
               this.mc.schedule(() -> this.openWheel(-1));
               return 1;
            })
            .then(ClientCommands.literal("stop").executes(c -> {
               this.mc.schedule(this::stopEmote);
               return 1;
            }))
            .then(ClientCommands.literal("list").executes(c -> {
               this.mc.schedule(this::printList);
               return 1;
            }))
            .then(ClientCommands.argument("name", StringArgumentType.greedyString()).suggests((c, b) -> {
               for (EmoteType t : EmoteType.listed()) {
                  if (t.id.startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) {
                     b.suggest(t.id);
                  }
               }

               return b.buildFuture();
            }).executes(c -> {
               String name = StringArgumentType.getString(c, "name").trim();
               this.mc.schedule(() -> {
                  EmoteType t = EmoteType.byId(name);
                  if (t == null) {
                     ChatUtil.chat("§cUnbekannter Emote §f" + name + "§c – §f/meowl emote list");
                  } else {
                     this.play(t);
                  }
               });
               return 1;
            })),
         "emotes"
      );
   }

   /** Die 8 Plätze im Rad (null = frei). */
   public EmoteType[] loadout() {
      EmoteType[] out = new EmoteType[SLOTS];
      String[] ids = this.loadout.get().split(",", -1);

      for (int i = 0; i < SLOTS && i < ids.length; i++) {
         EmoteType t = ids[i].isBlank() ? null : EmoteType.byId(ids[i].trim());
         out[i] = t != null && t.offered() ? t : null;
      }

      return out;
   }

   public void setLoadout(EmoteType[] slots) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < SLOTS; i++) {
         if (i > 0) {
            sb.append(',');
         }

         if (i < slots.length && slots[i] != null) {
            sb.append(slots[i].id);
         }
      }

      this.loadout.set(sb.toString());
      com.lennart.nebula.NebulaClient.getInstance().getConfigManager().save();
   }

   public void resetLoadout() {
      this.loadout.set(DEFAULT_LOADOUT);
      com.lennart.nebula.NebulaClient.getInstance().getConfigManager().save();
   }

   public void openPicker() {
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }

      this.mc.gui.setScreen(new com.lennart.nebula.gui.EmotePickerScreen(this));
   }

   public boolean shares() {
      return this.share.get();
   }

   private void printList() {
      StringBuilder sb = new StringBuilder("§dEmotes: §f");

      for (EmoteType t : EmoteType.listed()) {
         sb.append(t.id).append(t.loops() ? "§7*§f" : "").append("  ");
      }

      ChatUtil.chat(sb.toString().trim());
      ChatUtil.chat("§7* = Dauer-Emote (läuft, bis du dich bewegst) · §f/meowl emote <name>");
   }

   public void openWheel(int holdKey) {
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }

      if (this.mc.player != null) {
         this.mc.gui.setScreen(new EmoteWheelScreen(this, holdKey));
      }
   }

   /** Emote abspielen (ein laufender Dauer-Emote desselben Typs wird beendet). */
   public void play(EmoteType type) {
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }

      if (this.mc.player != null && type != null && type.offered()) {
         EmoteState.play(type);
         if (EmoteState.playing()) {
            this.thirdPersonOn();
            ChatUtil.actionBar("§d" + type.label + (type.loops() ? " §7– erneut auslösen zum Beenden (du kannst dabei laufen)" : ""));
         } else {
            this.restorePerspective();
         }
      }
   }

   public void stopEmote() {
      EmoteState.stop();
      this.restorePerspective();
   }

   private void thirdPersonOn() {
      if (this.thirdPerson.get() && !this.switched && this.mc.options.getCameraType() == CameraType.FIRST_PERSON) {
         this.before = this.mc.options.getCameraType();
         this.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         this.switched = true;
      }
   }

   private void restorePerspective() {
      if (this.switched && this.mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && this.before != null) {
         this.mc.options.setCameraType(this.before);
      }

      this.switched = false;
      this.before = null;
   }

   @Override
   public void onTick() {
      EmoteState.configure(this.showOthers.get(), this.share.get());
      EmoteState.tick();
      // Tasten liegen in den Minecraft-Steuerungen (Kategorie „Meowl Client“)
      boolean wheelPressed = com.lennart.nebula.NebulaClient.down(com.lennart.nebula.NebulaClient.emoteWheelKey);
      if (wheelPressed && !this.wheelDown && this.mc.gui.screen() == null) {
         this.openWheel(com.lennart.nebula.NebulaClient.keyCode(com.lennart.nebula.NebulaClient.emoteWheelKey));
      }

      this.wheelDown = wheelPressed;
      boolean stopPressed = com.lennart.nebula.NebulaClient.down(com.lennart.nebula.NebulaClient.emoteStopKey);
      if (stopPressed && !this.stopDown && this.mc.gui.screen() == null) {
         this.stopEmote();
      }

      this.stopDown = stopPressed;
      // Schlagen bricht jeden laufenden Emote ab
      boolean attack = this.mc.gui.screen() == null && this.mc.options.keyAttack.isDown();
      if (attack && !this.attackDown && this.mc.player != null && EmoteState.playing()) {
         this.stopEmote();
      }

      this.attackDown = attack;
      if (this.mc.player != null) {
         if (EmoteState.playing()) {
            // mit Emotes darf man laufen – nur Reiten oder Sterben beendet sie
            if (this.mc.player.isPassenger() || !this.mc.player.isAlive()) {
               this.stopEmote();
            }
         } else if (this.switched && EmoteState.local() == null) {
            this.restorePerspective();
         }
      }
   }
   @Override
   protected void onDisable() {
      this.stopEmote();
      EmoteState.clearRemote();
   }

   @Override
   public com.lennart.nebula.gui.ModulePreview preview() {
      return new com.lennart.nebula.gui.EmotePreview();
   }
}
