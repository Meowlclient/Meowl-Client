package com.lennart.nebula.cosmetic;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Grundlage aller Kosmetik-Module (Hüte, Auren …): jedes Modul hat einen Netzwerk-Schlüssel, dessen Wert über das
 * Meowl-Netzwerk geteilt wird, damit alle Meowl-Spieler die Kosmetik sehen. Der Wert ist „Stil|Farbton|Größe|…“.
 */
public abstract class Cosmetic extends Module {
   public final String key;
   protected final ModeSetting style;
   protected final com.lennart.nebula.setting.HueSetting hue;
   protected final NumberSetting size;
   protected final BooleanSetting customColor = new BooleanSetting("Eigene Farbe", "Statt Farbton eine beliebige Farbe wählen (auch Schwarz, Weiß, Pastell …)", false);
   protected final com.lennart.nebula.setting.ColorSetting color = new com.lennart.nebula.setting.ColorSetting("Farbe", "Farbe der Kosmetik", 0xFFE04848, false);
   protected final BooleanSetting share = new BooleanSetting("Für andere sichtbar", "Andere Meowl-Spieler sehen diese Kosmetik", true);
   protected final BooleanSetting showOthers = new BooleanSetting("Bei anderen zeigen", "Diese Kosmetik bei anderen Meowl-Spielern anzeigen", true);
   protected final NumberSetting offX = new NumberSetting("Position seitlich", "Kleine Verschiebung zur Seite", 0.0, -1.25, 1.25, 0.25, "px");
   protected final NumberSetting offY = new NumberSetting("Position hoch/runter", "Kleine Verschiebung nach oben (+) oder unten (-)", 0.0, -1.25, 1.25, 0.25, "px");
   protected final NumberSetting offZ = new NumberSetting("Position vor/zurück", "Kleine Verschiebung nach vorne (+) oder hinten (-)", 0.0, -1.25, 1.25, 0.25, "px");
   protected final BooleanSetting outerLayer = new BooleanSetting(
      "Über 2. Skin-Ebene", "Sitzt über der äußeren Skin-Schicht (Jacke, Ärmel, Hut-Ebene) statt direkt am Körper", false
   );

   protected Cosmetic(String name, String description, String key, Item icon, String[] styles) {
      super(name, description, Category.PLAYER);
      this.key = key;
      this.section(Section.COSMETICS);
      this.icon(icon);
      this.style = this.add(new com.lennart.nebula.setting.StyleSetting("Stil", "Welche Variante", styles[0], this, this.previewFromBehind(), styles));
      this.hue = this.add(new com.lennart.nebula.setting.HueSetting("Farbton", "Verschiebt die Farben (0 = Original, Rechtsklick setzt zurück)"));
      this.size = this.add(new NumberSetting("Größe", "Größe", 1.0, 0.8, 1.3, 0.05, "x"));
      Cosmetics.register(this);
   }

   /** Am Ende des Konstruktors der Unterklasse: Gruppen anlegen (eigene Einstellungen zuerst übergeben). */
   protected void groups(Setting<?>... extra) {
      this.add(this.customColor);
      this.add(this.color);
      this.color.setVisibility(this.customColor::get);
      this.hue.setVisibility(() -> !this.customColor.get());
      Setting<?>[] main = new Setting[5 + extra.length];
      main[0] = this.style;
      main[1] = this.customColor;
      main[2] = this.hue;
      main[3] = this.color;
      main[4] = this.size;
      System.arraycopy(extra, 0, main, 5, extra.length);
      this.group("Aussehen", main);
      this.add(this.offX);
      this.add(this.offY);
      this.add(this.offZ);
      if (this.hasLayerOption()) {
         this.add(this.outerLayer);
         this.group("Position", new Setting[]{this.offX, this.offY, this.offZ, this.outerLayer});
      } else {
         this.group("Position", new Setting[]{this.offX, this.offY, this.offZ});
      }

      this.add(this.share);
      this.add(this.showOthers);
      this.group("Andere Spieler", new Setting[]{this.share, this.showOthers});
   }

   /** Eigener Wert für das Netzwerk (null = aus). */
   public String encode() {
      return this.isEnabled() ? this.encodeValue() : null;
   }

   /** Wert unabhängig davon, ob das Modul an ist (für die Vorschau). */
   public String encodeValue() {
      return CosmeticValue.of(this.style.get(), (float)this.hue.get().doubleValue(), (float)this.size.get().doubleValue(), this.extra())
         .withPlacement(
            (float)this.offX.get().doubleValue(),
            (float)this.offY.get().doubleValue(),
            (float)this.offZ.get().doubleValue(),
            this.hasLayerOption() && this.outerLayer.get()
         )
         .withColor(this.customColor.get() ? this.color.get() : 0)
         .encode();
   }

   /** Vorschaubilder eher von hinten (Rückenteile, Schwänze)? */
   protected boolean previewFromBehind() {
      return false;
   }

   /** Eigener Wert, aber mit einem anderen Stil (für die Vorschaubilder). */
   public CosmeticValue valueWithStyle(String styleName) {
      CosmeticValue v = CosmeticValue.decode(this.encodeValue());
      return new CosmeticValue(styleName, v.hue(), v.size(), v.extra(), v.ox(), v.oy(), v.oz(), v.outer(), v.color());
   }

   /** Sitzt die Kosmetik am Körper, sodass „Über 2. Skin-Ebene“ Sinn ergibt? */
   protected boolean hasLayerOption() {
      return false;
   }

   /** Zusätzliche Angaben (z.B. Variante), werden mitgeteilt. */
   protected String extra() {
      return "";
   }

   @Override
   public com.lennart.nebula.gui.ModulePreview preview() {
      return new com.lennart.nebula.gui.CosmeticPreview(() -> Cosmetics.markPreview(this));
   }

   public boolean shares() {
      return this.isEnabled() && this.share.get();
   }

   public boolean showsOthers() {
      return this.showOthers.get();
   }

   /** Zeichnen am Spieler (Körperraum, siehe {@link CosmeticContext}). */
   public abstract void render(CosmeticContext c, CosmeticValue v);

   /** Partikel auch in der eigenen Ego-Sicht (z.B. Spuren am Boden)? */
   public boolean particlesInFirstPerson() {
      return false;
   }

   /** Partikel jeden Tick (clientseitig). */
   public void particles(ClientLevel world, Player player, CosmeticValue v, Random r) {
   }
}
