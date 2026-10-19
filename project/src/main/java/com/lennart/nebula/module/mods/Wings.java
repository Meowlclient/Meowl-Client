package com.lennart.nebula.module.mods;

import com.google.gson.JsonObject;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.MeowlNetwork;
import com.lennart.nebula.wings.WingsModel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Wings: Flügel am Rücken – für alle Spieler, andere Meowl-Spieler sehen sie auch. */
public class Wings extends Module {
   private static Wings instance;
   private final ModeSetting style = this.add(
      new com.lennart.nebula.setting.StyleSetting(
         "Stil", "Welche Flügel du trägst", WingsModel.PHOENIX, com.lennart.nebula.cosmetic.PreviewOverrides.WINGS, true, WingsModel.STYLES
      )
   );
   private final NumberSetting size = this.add(new NumberSetting("Größe", "Größe der Flügel", 0.8, 0.4, 1.4, 0.05, "x"));
   private final NumberSetting height = this.add(new NumberSetting("Höhe", "Flügel höher (+) oder tiefer (-) am Rücken", 0.0, -6.0, 6.0, 0.25, "px"));
   private final NumberSetting depth = this.add(new NumberSetting("Abstand", "Flügel näher am Rücken (-) oder weiter weg (+)", 0.0, -6.0, 6.0, 0.25, "px"));
   private final NumberSetting tilt = this.add(new NumberSetting("Neigung", "Flügel nach vorn (-) oder nach hinten (+) kippen", 0.0, -40.0, 40.0, 1.0, "°"));
   private final NumberSetting spread = this.add(new NumberSetting("Öffnung", "Flügel weiter ausbreiten (+) oder enger anlegen (-)", 0.0, -30.0, 30.0, 1.0, "°"));
   private final NumberSetting angle = this.add(new NumberSetting("Winkel", "Flügelspitzen nach oben (+) oder unten (-) drehen", 0.0, -45.0, 45.0, 1.0, "°"));
   private final NumberSetting flapSpeed = this.add(new NumberSetting("Flügelschlag", "Wie lebhaft die Flügel schlagen", 1.0, 0.2, 2.0, 0.1, "x"));
   private final com.lennart.nebula.setting.HueSetting hue = this.add(
      new com.lennart.nebula.setting.HueSetting("Farbton", "Verschiebt alle Farben des Stils (0 = Original, Rechtsklick setzt zurück)")
         .preview(() -> WingsModel.palette(WingsModel.known(this.style.get())))
   );
   private final NumberSetting effects = this.add(new NumberSetting("Effekte", "Stärke von Leuchten, Flammen, Blitzen und Glanz", 100.0, 0.0, 100.0, 5.0, "%"));
   private final BooleanSetting particles = this.add(new BooleanSetting("Partikel", "Flügel geben passende Partikel ab (Funken, Flammen, Sterne …)", true));
   private final ModeSetting particleAmount = this.add(new ModeSetting("Partikelmenge", "Wie viele Partikel", "Normal", "Wenig", "Normal", "Viel"));
   private final BooleanSetting firstPerson = this.add(new BooleanSetting("Partikel in Ego-Sicht", "Eigene Flügel-Partikel auch in der Ego-Perspektive", false));
   private final BooleanSetting replaceElytra = this.add(
      new BooleanSetting("Als Elytra", "Ziehst du eine Elytra an, werden deine Flügel zur Elytra (statt der normalen Elytra) und breiten sich beim Gleiten aus", true)
   );
   private final BooleanSetting onlyElytra = this.add(new BooleanSetting("Nur mit Elytra", "Flügel nur zeigen, wenn eine Elytra angezogen ist", false));
   private final BooleanSetting share = this.add(new BooleanSetting("Flügel teilen", "Andere Meowl-Spieler sehen deine Flügel", true));
   private final BooleanSetting showOthers = this.add(new BooleanSetting("Flügel anderer zeigen", "Flügel anderer Meowl-Spieler anzeigen", true));

   public Wings() {
      super(
         "Wings",
         "Große Flügel im Lunar-Stil: Seraph, Schmetterling, Libelle, Höllenfürst, Phönix, Drache, Galaxie, Kristall und mehr – Position frei einstellbar.",
         Category.PLAYER
      );
      this.section(Section.COSMETICS);
      this.icon(Items.FEATHER);
      instance = this;
      this.group("Flügel", new Setting[]{this.style, this.size, this.flapSpeed});
      this.group("Position", new Setting[]{this.height, this.depth, this.tilt, this.spread, this.angle});
      this.group("Elytra", new Setting[]{this.replaceElytra, this.onlyElytra});
      this.group("Effekte", new Setting[]{this.hue, this.effects, this.particles, this.particleAmount, this.firstPerson});
      this.group("Andere Spieler", new Setting[]{this.share, this.showOthers});
      this.particleAmount.setVisibility(this.particles::get);
      this.firstPerson.setVisibility(this.particles::get);
   }

   public static Wings active() {
      return instance != null && instance.isEnabled() ? instance : null;
   }

   private WingsModel.Spec ownSpec() {
      return new WingsModel.Spec(
         WingsModel.known(this.style.get()),
         (float)this.size.get().doubleValue(),
         (float)this.hue.get().doubleValue(),
         (float)(this.effects.get() / 100.0),
         this.replaceElytra.get(),
         this.onlyElytra.get(),
         (float)this.height.get().doubleValue(),
         (float)this.depth.get().doubleValue(),
         (float)this.tilt.get().doubleValue(),
         (float)this.spread.get().doubleValue(),
         (float)this.angle.get().doubleValue()
      );
   }

   /** Eigene Flügel, aber in einem anderen Stil (Vorschaubilder im Menü). */
   public static WingsModel.Spec previewSpec(String styleName) {
      Wings w = instance;
      if (w == null) {
         return null;
      } else {
         WingsModel.Spec own = w.ownSpec();
         return new WingsModel.Spec(WingsModel.known(styleName), own.scale(), own.hue(), own.fx(), own.replaceElytra(), own.onlyElytra(), own.height(), own.depth(), own.tilt(), own.spread(), own.angle());
      }
   }

   public static boolean wearsElytra(Player player) {
      net.minecraft.world.item.ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      return chest.is(Items.ELYTRA) || chest.has(net.minecraft.core.component.DataComponents.GLIDER);
   }

   /** Eingestellte Flügel eines Spielers, ohne Rücksicht auf die Elytra. */
   private static WingsModel.Spec rawSpec(Player player) {
      Wings w = instance;
      if (w == null) {
         return null;
      } else if (player == Minecraft.getInstance().player) {
         return w.isEnabled() || previewing() ? w.ownSpec() : null;
      } else if (!w.showOthers.get()) {
         return null;
      } else {
         MeowlNetwork.Peer peer = MeowlPet.peer(player);
         MeowlNetwork.WingsInfo info = peer == null ? null : peer.wings();
         return info == null ? null : WingsModel.Spec.decode(info.style(), Math.max(0.4F, Math.min(1.4F, info.size())));
      }
   }

   private static long previewUntil;

   private static boolean previewing() {
      return System.currentTimeMillis() < previewUntil;
   }

   @Override
   public com.lennart.nebula.gui.ModulePreview preview() {
      return new com.lennart.nebula.gui.CosmeticPreview(() -> previewUntil = System.currentTimeMillis() + 300L);
   }

   /** Flügel eines Spielers (eigene oder geteilte), null = keine. */
   public static WingsModel.Spec specFor(Player player) {
      WingsModel.Spec spec = rawSpec(player);
      if (spec == null) {
         return null;
      } else if (player == Minecraft.getInstance().player && previewing() && !instance.isEnabled()) {
         return spec;
      } else if (wearsElytra(player)) {
         return spec.replaceElytra() ? spec : null;
      } else {
         return spec.onlyElytra() ? null : spec;
      }
   }

   /** Ersetzen die Flügel die Elytra dieses Spielers (dann wird die normale Elytra nicht gezeichnet)? */
   public static boolean replacesElytra(int entityId) {
      Minecraft mc = Minecraft.getInstance();
      if (OtherClients.showMeowlCosmetics() && mc.level != null && mc.level.getEntity(entityId) instanceof Player p && wearsElytra(p)) {
         WingsModel.Spec spec = rawSpec(p);
         return spec != null && spec.replaceElytra();
      } else {
         return false;
      }
   }

   public static float ownFlapSpeed() {
      return instance == null ? 1.0F : (float)instance.flapSpeed.get().doubleValue();
   }

   /** Partikel: 0 = aus, sonst Faktor. */
   public static float particleRate(boolean own) {
      Wings w = instance;
      if (w == null || !w.particles.get()) {
         return 0.0F;
      } else if (own && !w.firstPerson.get() && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
         return 0.0F;
      } else {
         return switch (w.particleAmount.get()) {
            case "Wenig" -> 0.45F;
            case "Viel" -> 2.0F;
            default -> 1.0F;
         };
      }
   }

   /** Für das Meowl-Netzwerk: welche Flügel man trägt (Stil|Farbton|Effekt). */
   public static void presenceFields(JsonObject o) {
      Wings w = instance;
      if (w != null && w.isEnabled() && w.share.get()) {
         o.addProperty("wings", w.ownSpec().encode());
         o.addProperty("wingsSize", w.size.get());
      }
   }

   public static MeowlNetwork.WingsInfo othersWings(Player player) {
      Wings w = instance;
      if (w != null && w.showOthers.get()) {
         MeowlNetwork.Peer peer = MeowlPet.peer(player);
         return peer == null ? null : peer.wings();
      } else {
         return null;
      }
   }
}
