package com.lennart.nebula.module.hud;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Inventar-HUD: zeigt den Inhalt deines Inventars (3 Reihen, optional Hotbar) im Spiel – im HUD-Editor verschiebbar. */
public class InventoryHud extends Module {
   private static final String VANILLA = "Vanilla";
   private static final String MEOWL = "Meowl";
   private final ModeSetting style = this.add(new ModeSetting("Stil", "Wie die normale Hotbar oder als eigener Kasten", VANILLA, VANILLA, MEOWL));
   private final BooleanSetting showHud = this.add(
      new BooleanSetting("HUD anzeigen", "Inventar im Spiel anzeigen – aus = nur die Inventar-Größe nutzen, ohne das Modul auszuschalten", true)
   );
   private final BooleanSetting hotbar = this.add(new BooleanSetting("Hotbar mitzeigen", "Die Hotbar als vierte Reihe darunter", false));
   private final BooleanSetting counts = this.add(new BooleanSetting("Anzahl", "Stapelgröße und Haltbarkeit an den Items", true));
   private final BooleanSetting hideEmpty = this.add(new BooleanSetting("Bei leerem Inventar ausblenden", "Nichts anzeigen, wenn das Inventar leer ist", true));
   private final NumberSetting spacing = this.add(new NumberSetting("Abstand", "Abstand zwischen den Slots (Meowl-Stil)", 1.0, 0.0, 6.0, 1.0, "px"));
   private final ModeSetting openScale = this.add(
      new ModeSetting(
         "Größe im Inventar",
         "GUI-Größe, solange ein Inventar, eine Truhe oder ein Ofen offen ist (Normal = wie im restlichen Spiel). Gilt auch, wenn das HUD aus ist.",
         "Normal",
         "Normal",
         "1",
         "2",
         "3",
         "4",
         "Maximal"
      )
   );
   private final HudBox box = this.hudBox("Inventar", 99.0, 40.0);
   private static InventoryHud instance;

   public InventoryHud() {
      super("Inventar-HUD", "Zeigt dein Inventar im Spiel an – Position und Größe im HUD-Editor einstellbar.", Category.HUD);
      this.section(Section.PVP);
      this.icon(Items.CHEST);
      this.spacing.setVisibility(() -> this.style.is(MEOWL));
      this.box.background.setVisibility(() -> this.style.is(MEOWL));
      this.group("Offenes Inventar", new com.lennart.nebula.setting.Setting[]{this.openScale});
      this.group("HUD", new com.lennart.nebula.setting.Setting[]{this.showHud, this.style, this.hotbar, this.counts, this.hideEmpty, this.spacing});
      instance = this;
   }

   public static InventoryHud instance() {
      return instance;
   }

   /** GUI-Größe für offene Inventare, 0 = normal. */
   public int inventoryGuiScale() {
      if (this.openScale.is("Normal")) {
         return 0;
      } else if (this.openScale.is("Maximal")) {
         return 99;
      } else {
         try {
            return Integer.parseInt(this.openScale.get());
         } catch (NumberFormatException var2) {
            // alte Werte 5/6: größer als 4 geht auf den meisten Bildschirmen nicht – Maximal nehmen
            return 99;
         }
      }
   }

   @Override
   public void onTick() {
      // Einstellung bei offenem Inventar sofort übernehmen
      InventoryScale.refresh(this.mc.screen);
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      LocalPlayer player = this.mc.player;
      if (player != null && !this.mc.options.hideGui && (this.showHud.get() || HudLayout.isEditing())) {
         int rows = this.hotbar.get() ? 4 : 3;
         ItemStack[] items = new ItemStack[rows * 9];
         boolean any = false;

         for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
               ItemStack s = player.getInventory().getItem(9 + r * 9 + c);
               items[r * 9 + c] = s;
               any |= !s.isEmpty();
            }
         }

         if (rows == 4) {
            for (int c = 0; c < 9; c++) {
               items[27 + c] = player.getInventory().getItem(c);
            }
         }

         boolean editing = HudLayout.isEditing();
         if (any || editing || !this.hideEmpty.get()) {
            boolean vanilla = this.style.is(VANILLA);
            int cell = vanilla ? 20 : 18 + this.spacing.getInt();
            int w = vanilla ? VanillaSlots.stripWidth(9) : 9 * cell + 2;
            int h = vanilla ? rows * 20 + 2 + (rows == 4 ? 2 : 0) : rows * cell + 2 + (rows == 4 ? 3 : 0);
            this.box.begin(ctx, w, h, !vanilla && this.box.background.get());

            for (int r = 0; r < rows; r++) {
               int gap = r == 3 ? (vanilla ? 2 : 3) : 0;
               int y = r * (vanilla ? 20 : cell) + gap;
               if (vanilla) {
                  VanillaSlots.strip(ctx, 0, y, 9, VanillaSlots.white(this.box.alpha()));
               }

               for (int c = 0; c < 9; c++) {
                  int x = vanilla ? 3 + c * 20 : 1 + c * cell;
                  int iy = vanilla ? y + 3 : y + 1;
                  if (!vanilla) {
                     Render2D.roundRect(ctx, x, iy, 18 - 1, 18 - 1, 3, this.box.fade(0x40FFFFFF));
                  }

                  ItemStack s = items[r * 9 + c];
                  if (s != null && !s.isEmpty()) {
                     ctx.renderItem(s, vanilla ? x : x + 1, vanilla ? iy : iy + 1);
                     if (this.counts.get()) {
                        ctx.renderItemDecorations(this.mc.font, s, vanilla ? x : x + 1, vanilla ? iy : iy + 1);
                     }
                  }
               }
            }

            this.box.end(ctx);
         }
      }
   }
}
