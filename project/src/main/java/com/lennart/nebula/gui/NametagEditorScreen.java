package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.hud.TotemCounter;
import com.lennart.nebula.module.mods.Friends;
import com.lennart.nebula.module.mods.Tiers;
import com.lennart.nebula.nametag.GuiCanvas;
import com.lennart.nebula.nametag.NameCanvas;
import com.lennart.nebula.nametag.NametagConfig;
import com.lennart.nebula.nametag.NametagLayout;
import com.lennart.nebula.nametag.NametagPiece;
import com.lennart.nebula.nametag.Nametags;
import com.lennart.nebula.nametag.OwnNametag;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.TextSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class NametagEditorScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int PANEL_W = 176;
   private static final int ACCENT = Section.CLIENT.color();
   private static final int[] PALETTE = new int[]{-1, -11870592, -11549697, -5215233, -10929, -22746, -44462, -36939, -5592406, -14494738};
   private static final String[] KITS = new String[]{"", "sword", "axe", "mace", "crystal", "pot", "nethpot", "smp", "uhc", "diapot", "vanilla"};
   private final Screen parent;
   private final Nametags tags;
   private final Tiers tiers;
   private final Friends friends;
   private final TotemCounter totem;
   private final OwnNametag own;
   private final List<NametagEditorScreen.Hit> hits = new ArrayList<>();
   private final Map<NametagConfig.Element, float[]> rects = new HashMap<>();
   private final Map<NametagConfig.Element, int[]> chips = new HashMap<>();
   private float[] nameRect = new float[]{0.0F, 0.0F, 0.0F, 8.0F};
   private NametagConfig.Element selected;
   private NametagConfig.Element dragging;
   private NametagConfig.Element pressed;
   private double pressX;
   private double pressY;
   private double mouseX;
   private double mouseY;
   private NametagEditorScreen.Drop drop;
   private float ox;
   private float oy;
   private float scale = 3.0F;
   private int trayY;
   private int panelX;
   private float panelScroll;
   private int py;

   public NametagEditorScreen(Screen parent) {
      super(Component.literal("Nametag-Editor"));
      this.parent = parent;
      ModuleManager mm = NebulaClient.getInstance().getModuleManager();
      this.tags = mm.get(Nametags.class);
      this.tiers = mm.get(Tiers.class);
      this.friends = Friends.get();
      this.totem = mm.get(TotemCounter.class);
      this.own = mm.get(OwnNametag.class);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   private void save() {
      NebulaClient.getInstance().getModuleManager().notifyChanged();
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      this.hits.clear();
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      Font tr = this.font;
      this.panelX = this.width - 176 - 8;
      int stageW = this.panelX - 8;
      context.fillGradient(0, 0, this.width, this.height, -16052190, -15069133);

      for (int gx = 0; gx < stageW; gx += 16) {
         context.fill(gx, 0, gx + 1, this.height, 218103807);
      }

      for (int gy = 0; gy < this.height; gy += 16) {
         context.fill(0, gy, stageW, gy + 1, 218103807);
      }

      context.fill(0, 0, this.width, 24, -1073741824);
      context.text(tr, Component.literal("✎ Nametags bearbeiten").withStyle(ChatFormatting.BOLD), 10, 8, -1578509, false);
      context.text(tr, "Ziehen = verschieben · Klick = Einstellungen", 150, 8, -10656900, false);
      this.button(context, this.width - 64, 4, 56, 16, "Fertig", ACCENT, -1, this::onClose);
      this.button(context, this.width - 150, 4, 80, 16, "Zurücksetzen", -14012603, -1578509, () -> {
         this.tags.setConfig(NametagConfig.parse(NametagConfig.DEFAULT));
         this.selected = null;
         this.save();
      });
      int cx = stageW / 2;
      this.trayY = this.height - 44;
      int feetY = this.trayY - 16;
      int bodyH = Math.max(60, Math.min(150, feetY - 110));
      if (this.minecraft.player != null) {
         InventoryScreen.extractEntityInInventoryFollowsMouse(context, cx - 60, feetY - bodyH - 10, cx + 60, feetY, (int)(bodyH / 2.1F), 0.0625F, mouseX, mouseY, this.minecraft.player);
      } else {
         PlayerFaceExtractor.extractRenderState(context, DefaultPlayerSkin.getDefaultSkin(), cx - 24, feetY - 60, 48);
      }

      NametagConfig cfg = this.tags.config().copy();
      if (this.dragging != null) {
         cfg.move(this.dragging, NametagConfig.Zone.HIDDEN, 0);
         cfg.get(NametagConfig.Zone.HIDDEN).remove(this.dragging);
      }

      NametagLayout.Row row = this.tags.previewRow(cfg, (e, p) -> new NametagEditorScreen.Tagged(e, p));
      String name = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "Spieler";
      FormattedCharSequence nameText = Component.literal(name).getVisualOrderText();
      float rowW = NametagLayout.width(tr, row.left()) + NametagLayout.width(tr, row.right()) + tr.width(nameText) + 24.0F;
      this.scale = Math.max(1.5F, Math.min(3.5F, (stageW - 40) / Math.max(60.0F, rowW))) * (float)this.tags.scaleSetting().get().doubleValue();
      this.ox = cx;
      this.oy = feetY - bodyH - 30 - 8.0F * this.scale + this.tags.rowYOffset() * this.scale;
      this.rects.clear();
      context.pose().pushMatrix();
      context.pose().translate(this.ox, this.oy);
      context.pose().scale(this.scale, this.scale);
      boolean bg = this.tiers == null || this.tiers.backgroundSetting().get();
      NameCanvas canvas = new GuiCanvas(context, 1073741824, (tag, rx) -> {
         if (tag instanceof NametagConfig.Element e) {
            this.rects.put(e, rx);
         } else if (tag == NametagLayout.NAME_TAG) {
            this.nameRect = rx;
         }
      });
      NametagLayout.drawRow(tr, canvas, nameText, -1, NametagLayout.NameMode.NORMAL, row, 0.0F, this.tags.centerGroup(), bg);
      context.pose().popMatrix();

      for (Entry<NametagConfig.Element, float[]> en : this.rects.entrySet()) {
         int[] r = this.screenRect(en.getValue());
         boolean hov = this.dragging == null && Render2D.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
         if (en.getKey() == this.selected) {
            outline(context, r, ACCENT);
         } else if (hov) {
            outline(context, r, -2130706433);
         }
      }

      this.drop = null;
      if (this.dragging != null) {
         this.drop = this.computeDrop(mouseX, mouseY, cfg);
         this.drawZones(context, tr);
         if (this.drop != null && this.drop.zone() != NametagConfig.Zone.HIDDEN) {
            context.fill(
               (int)this.drop.markerX() - 1,
               (int)this.drop.markerY() - 2,
               (int)this.drop.markerX() + 1,
               (int)(this.drop.markerY() + 10.0F * this.scale),
               ACCENT
            );
         }
      }

      this.drawTray(context, tr, cfg, stageW);
      if (this.dragging != null) {
         String label = this.dragging.label;
         int w = tr.width(label) + 12;
         Render2D.roundRect(context, mouseX + 6, mouseY + 6, w, 16, 6, -534369473);
         context.text(tr, label, mouseX + 12, mouseY + 10, -1578509, false);
      }

      this.drawPanel(context, tr, mouseX, mouseY);
   }

   private int[] screenRect(float[] r) {
      int x = Math.round(this.ox + (r[0] - 1.5F) * this.scale);
      int y = Math.round(this.oy + (r[1] - 1.5F) * this.scale);
      return new int[]{x, y, Math.round((r[2] + 3.0F) * this.scale), Math.round((r[3] + 3.0F) * this.scale)};
   }

   private static void outline(GuiGraphicsExtractor ctx, int[] r, int c) {
      ctx.fill(r[0], r[1], r[0] + r[2], r[1] + 1, c);
      ctx.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], c);
      ctx.fill(r[0], r[1], r[0] + 1, r[1] + r[3], c);
      ctx.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], c);
   }

   private void drawZones(GuiGraphicsExtractor ctx, Font tr) {
      float nameL = this.ox + this.nameRect[0] * this.scale;
      float nameR = this.ox + (this.nameRect[0] + this.nameRect[2]) * this.scale;
      int top = Math.round(this.oy - 2.0F * this.scale);
      int h = Math.round(12.0F * this.scale);
      int aboveTop = Math.round(this.oy - 16.0F * this.scale);
      NametagConfig.Zone z = this.drop == null ? null : this.drop.zone();
      zone(ctx, tr, 8, top, Math.round(nameL) - 8 - 4, h, "Links", z == NametagConfig.Zone.LEFT);
      zone(ctx, tr, Math.round(nameR) + 4, top, this.panelX - 16 - Math.round(nameR), h, "Rechts", z == NametagConfig.Zone.RIGHT);
      zone(ctx, tr, 8, aboveTop, this.panelX - 24, Math.round(12.0F * this.scale), "Darüber", z == NametagConfig.Zone.ABOVE);
   }

   private static void zone(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h, String label, boolean active) {
      if (w > 4) {
         ctx.fill(x, y, x + w, y + h, active ? 810533887 : 352321535);
         outline(ctx, new int[]{x, y, w, h}, active ? ACCENT : 822083583);
         ctx.text(tr, label, x + 4, y + 3, active ? ACCENT : -2130706433, false);
      }
   }

   private void drawTray(GuiGraphicsExtractor ctx, Font tr, NametagConfig cfg, int stageW) {
      boolean target = this.drop != null && this.drop.zone() == NametagConfig.Zone.HIDDEN;
      Render2D.roundRect(ctx, 8, this.trayY, stageW - 16, 36, 8, target ? -534631872 : -1072688352);
      outline(ctx, new int[]{8, this.trayY, stageW - 16, 36}, target ? ACCENT : 822083583);
      ctx.text(tr, "Ausgeblendet", 16, this.trayY + 4, -10656900, false);
      this.chips.clear();
      int x = 16;

      for (NametagConfig.Element e : cfg.get(NametagConfig.Zone.HIDDEN)) {
         int w = tr.width(e.label) + 14;
         boolean hov = this.dragging == null && Render2D.inside(this.mouseX, this.mouseY, x, this.trayY + 15, w, 16);
         Render2D.roundRect(ctx, x, this.trayY + 15, w, 16, 6, e == this.selected ? -14011568 : (hov ? -14407358 : -15394521));
         ctx.text(tr, e.label, x + 7, this.trayY + 19, -7564628, false);
         this.chips.put(e, new int[]{x, this.trayY + 15, w, 16});
         x += w + 6;
      }

      if (cfg.get(NametagConfig.Zone.HIDDEN).isEmpty()) {
         ctx.text(tr, "Hierher ziehen, um ein Element auszublenden", 16, this.trayY + 19, 1627389951, false);
      }
   }

   private NametagEditorScreen.Drop computeDrop(double mx, double my, NametagConfig cfg) {
      int stageW = this.panelX - 8;
      if (my >= this.trayY - 4 && mx < stageW) {
         return new NametagEditorScreen.Drop(NametagConfig.Zone.HIDDEN, 99, 0.0F, 0.0F);
      } else if (mx >= stageW) {
         return null;
      } else {
         float ux = (float)((mx - this.ox) / this.scale);
         float uy = (float)((my - this.oy) / this.scale);
         NametagConfig.Zone zone;
         if (uy < -4.0F) {
            zone = NametagConfig.Zone.ABOVE;
         } else {
            zone = ux < this.nameRect[0] + this.nameRect[2] / 2.0F ? NametagConfig.Zone.LEFT : NametagConfig.Zone.RIGHT;
         }

         List<float[]> placed = new ArrayList<>();

         for (NametagConfig.Element e : cfg.get(zone)) {
            float[] r = this.rects.get(e);
            if (r != null) {
               placed.add(r);
            }
         }

         int index = 0;

         for (float[] r : placed) {
            if (r[0] + r[2] / 2.0F < ux) {
               index++;
            }
         }

         float marker;
         if (placed.isEmpty()) {
            marker = zone == NametagConfig.Zone.LEFT
               ? this.nameRect[0] - 3.0F
               : (zone == NametagConfig.Zone.RIGHT ? this.nameRect[0] + this.nameRect[2] + 3.0F : this.nameRect[0] + this.nameRect[2] / 2.0F);
         } else if (index == 0) {
            marker = placed.get(0)[0] - 1.5F;
         } else {
            float[] rx = placed.get(index - 1);
            marker = rx[0] + rx[2] + 1.5F;
         }

         float rowY = zone == NametagConfig.Zone.ABOVE ? -11.0F + (this.tiers != null ? this.tiers.aboveOffset() : 0.0F) : 0.0F;
         return new NametagEditorScreen.Drop(zone, index, this.ox + marker * this.scale, this.oy + (rowY - 1.0F) * this.scale);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();

      for (int k = this.hits.size() - 1; k >= 0; k--) {
         NametagEditorScreen.Hit h = this.hits.get(k);
         if (Render2D.inside(mx, my, h.x, h.y, h.w, h.h)) {
            h.action.run();
            this.save();
            return true;
         }
      }

      for (Entry<NametagConfig.Element, float[]> en : this.rects.entrySet()) {
         int[] r = this.screenRect(en.getValue());
         if (Render2D.inside(mx, my, r[0], r[1], r[2], r[3])) {
            this.pressed = en.getKey();
            this.pressX = mx;
            this.pressY = my;
            return true;
         }
      }

      for (Entry<NametagConfig.Element, int[]> enx : this.chips.entrySet()) {
         int[] r = enx.getValue();
         if (Render2D.inside(mx, my, r[0], r[1], r[2], r[3])) {
            this.pressed = enx.getKey();
            this.pressX = mx;
            this.pressY = my;
            return true;
         }
      }

      if (mx < this.panelX - 8) {
         this.selected = null;
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public void mouseMoved(double mouseX, double mouseY) {
      if (this.pressed != null && this.dragging == null && Math.hypot(mouseX - this.pressX, mouseY - this.pressY) > 4.0) {
         this.dragging = this.pressed;
         this.selected = this.pressed;
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      this.mouseMoved(click.x(), click.y());
      return true;
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.dragging != null) {
         if (this.drop != null) {
            NametagConfig cfg = this.tags.config().copy();
            cfg.move(this.dragging, this.drop.zone(), this.drop.index());
            this.tags.setConfig(cfg);
            this.save();
         }

         this.dragging = null;
         this.pressed = null;
         return true;
      } else if (this.pressed != null) {
         this.selected = this.pressed == this.selected ? null : this.pressed;
         this.pressed = null;
         this.panelScroll = 0.0F;
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (mouseX >= this.panelX) {
         this.panelScroll = Math.max(0.0F, this.panelScroll - (float)verticalAmount * 16.0F);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (input.key() == InputConstants.KEY_DELETE && this.selected != null) {
         NametagConfig cfg = this.tags.config().copy();
         cfg.move(this.selected, NametagConfig.Zone.HIDDEN, 99);
         this.tags.setConfig(cfg);
         this.save();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private void drawPanel(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
      int x = this.panelX;
      int top = 30;
      Render2D.roundRect(ctx, x, top, 176, this.height - top - 8, 8, -267381984);
      outline(ctx, new int[]{x, top, 176, this.height - top - 8}, 822083583);
      ctx.enableScissor(x, top + 2, x + 176, this.height - 10);
      this.py = top + 8 - Math.round(this.panelScroll);
      if (this.selected == null) {
         this.title(ctx, tr, "Allgemein");
         this.toggle(ctx, tr, "Meowl-Nametags", () -> this.tags.isEnabled(), () -> this.tags.toggle());
         if (this.tiers != null) {
            this.toggle(ctx, tr, "Tiers am Namen", this.tiers.nametagSetting());
         }

         this.cycle(ctx, tr, "Ausrichtung", this.tags.alignSetting());
         this.stepper(ctx, tr, "Abstand", this.tags.gapSetting());
         this.stepper(ctx, tr, "Größe", this.tags.scaleSetting());
         this.stepper(ctx, tr, "Höhe", this.tags.yOffsetSetting());
         if (this.tiers != null) {
            this.toggle(ctx, tr, "Hintergrund", this.tiers.backgroundSetting());
         }

         this.title(ctx, tr, "Andere Spieler");
         this.toggle(ctx, tr, "Eigener Stil", this.tags.othersStyledSetting());
         if (this.tags.othersStyledSetting().get()) {
            this.colors(ctx, "Namensfarbe", this.tags.nameColorSetting());
            this.stepper(ctx, tr, "Deckkraft", this.tags.nameOpacitySetting());
            this.toggle(ctx, tr, "Hintergrund", this.tags.nameBackgroundSetting());
            this.toggle(ctx, tr, "Schriftrand", this.tags.nameOutlineSetting());
            if (this.tags.nameOutlineSetting().get()) {
               this.colors(ctx, "Randfarbe", this.tags.nameOutlineColorSetting());
            } else {
               this.toggle(ctx, tr, "Schatten", this.tags.nameShadowSetting());
            }

            this.toggle(ctx, tr, "Server-Nametags ersetzen", this.tags.replaceServerSetting());
         }

         this.title(ctx, tr, "Server-Nametags");
         this.toggle(ctx, tr, "Auch bei verstecktem Namen", this.tags.showWhenHiddenSetting());
         this.toggle(ctx, tr, "Namen selbst zeigen", this.tags.drawHiddenNameSetting());
         if (this.own != null) {
            this.title(ctx, tr, "Eigener Nametag (F5)");
            this.toggle(ctx, tr, "Anzeigen", () -> Nametags.get() != null ? Nametags.get().ownEnabled() : this.own.isEnabled(), () -> {
               if (Nametags.get() != null) {
                  Nametags.get().toggleOwn();
               } else {
                  this.own.toggle();
               }
            });
            this.toggle(ctx, tr, "Meowl-Zeichen bei mir", this.tags.meowlSelfSetting());
         }

         this.hint(ctx, tr, "Klicke ein Element in der Vorschau an, um es einzustellen.");
      } else {
         this.title(ctx, tr, this.selected.label);
         switch (this.selected) {
            case MEOWL:
               this.toggle(ctx, tr, "Anzeigen", this.tags.meowlSetting());
               this.stepper(ctx, tr, "Größe", this.tags.meowlSizeSetting());
               this.toggle(ctx, tr, "Auch bei mir", this.tags.meowlSelfSetting());
               this.hint(ctx, tr, "Erscheint bei Spielern mit Meowl Client.");
               break;
            case FRIEND:
               if (this.friends != null) {
                  this.toggle(ctx, tr, "Anzeigen", this.friends.markSetting());
                  this.colors(ctx, "Farbe", this.friends.markColorSetting());
               }

               this.hint(ctx, tr, "Stern bei Spielern aus deiner Freundesliste.");
               break;
            case POPS:
               if (this.totem != null) {
                  this.toggle(ctx, tr, "Anzeigen", this.totem.popsNameSetting());
                  this.colors(ctx, "Farbe", this.totem.popsColorSetting());
               }

               this.hint(ctx, tr, "Totem-Pops seit dem letzten Tod, z.B. [5 Pops].");
               break;
            case TIER_1:
            case TIER_2:
               if (this.tiers != null) {
                  boolean first = this.selected == NametagConfig.Element.TIER_1;
                  this.cycle(ctx, tr, "Tierlist", this.tiers.system(first));
                  this.kit(ctx, tr, "Kit", this.tiers.systemKit(first));
                  this.tierStyle(ctx, tr);
               }
               break;
            case TIER_KITS:
               if (this.tiers != null) {
                  this.cycle(ctx, tr, "Anzeige", this.tiers.kitModeSetting());
                  this.stepper(ctx, tr, "Anzahl", this.tiers.countSetting());
                  if (this.tiers.kitModeSetting().is("Bestimmtes Kit")) {
                     this.kit(ctx, tr, "Kit", this.tiers.kitSetting());
                  }

                  this.stepper(ctx, tr, "Höhe der Zeile", this.tiers.aboveOffsetSetting());
                  this.tierStyle(ctx, tr);
               }
         }

         NametagConfig.Zone zone = this.tags.config().zoneOf(this.selected);
         this.title(ctx, tr, "Position");
         int bw = 38;
         int bx = this.panelX + 8;

         for (NametagConfig.Zone z : NametagConfig.Zone.values()) {
            boolean on = z == zone;
            NametagConfig.Element e = this.selected;
            this.button(ctx, bx, this.py, bw - 2, 16, z.label.length() > 7 ? "Aus" : z.label, on ? ACCENT : -14012603, on ? -1 : -7564628, () -> {
               NametagConfig cfg = this.tags.config().copy();
               cfg.move(e, z, 99);
               this.tags.setConfig(cfg);
            });
            bx += bw + 2;
         }

         this.py += 22;
         this.button(ctx, this.panelX + 8, this.py, 160, 16, "← Allgemein", -14012603, -1578509, () -> this.selected = null);
         this.py += 22;
      }

      ctx.disableScissor();
   }

   private void tierStyle(GuiGraphicsExtractor ctx, Font tr) {
      this.title(ctx, tr, "Tier-Stil (alle Tiers)");
      this.cycle(ctx, tr, "Stil", this.tiers.nameStyleSetting());
      if (!this.tiers.serverStyle()) {
         this.toggle(ctx, tr, "Tierlist-Logo", this.tiers.listIconsSetting());
         this.toggle(ctx, tr, "Kit-Symbol", this.tiers.kitIconsSetting());
         this.stepper(ctx, tr, "Icon-Größe", this.tiers.iconSizeSetting());
         this.toggle(ctx, tr, "Klammern [HT1]", this.tiers.bracketsSetting());
      }
   }

   private void title(GuiGraphicsExtractor ctx, Font tr, String text) {
      this.py += 4;
      ctx.text(tr, Component.literal(text).withStyle(ChatFormatting.BOLD), this.panelX + 10, this.py, ACCENT, false);
      this.py += 14;
   }

   private void hint(GuiGraphicsExtractor ctx, Font tr, String text) {
      this.py += 4;

      for (FormattedCharSequence line : tr.split(Component.literal(text), 156)) {
         ctx.text(tr, line, this.panelX + 10, this.py, -10656900, false);
         this.py += 10;
      }
   }

   private void toggle(GuiGraphicsExtractor ctx, Font tr, String label, BooleanSetting setting) {
      this.toggle(ctx, tr, label, setting::get, setting::toggle);
   }

   private void toggle(GuiGraphicsExtractor ctx, Font tr, String label, BooleanSupplier on, Runnable flip) {
      ctx.text(tr, tr.plainSubstrByWidth(label, 124), this.panelX + 10, this.py + 3, -1578509, false);
      Render2D.toggleSwitch(ctx, this.panelX + 176 - 36, this.py, on.getAsBoolean() ? 1.0F : 0.0F);
      this.hits.add(new NametagEditorScreen.Hit(this.panelX + 4, this.py - 2, 168, 18, flip));
      this.py += 18;
   }

   private void stepper(GuiGraphicsExtractor ctx, Font tr, String label, NumberSetting s) {
      ctx.text(tr, label, this.panelX + 10, this.py + 3, -1578509, false);
      int rx = this.panelX + 176 - 10;
      this.button(ctx, rx - 14, this.py, 14, 14, "+", -14012603, -1578509, () -> s.set(Math.min(s.getMax(), s.get() + s.getStep())));
      String v = s.format();
      int vw = tr.width(v);
      ctx.text(tr, v, rx - 18 - vw, this.py + 3, -3885571, false);
      this.button(ctx, rx - 22 - vw - 14, this.py, 14, 14, "-", -14012603, -1578509, () -> s.set(Math.max(s.getMin(), s.get() - s.getStep())));
      this.py += 18;
   }

   private void cycle(GuiGraphicsExtractor ctx, Font tr, String label, ModeSetting s) {
      ctx.text(tr, label, this.panelX + 10, this.py + 3, -1578509, false);
      String v = tr.plainSubstrByWidth(s.get(), 106);
      int w = tr.width(v) + 16;
      this.button(ctx, this.panelX + 176 - 10 - w, this.py, w, 14, v, -14012603, -3885571, () -> {
         List<String> modes = s.getModes();
         s.set(modes.get((modes.indexOf(s.get()) + 1) % modes.size()));
      });
      this.py += 18;
   }

   private void kit(GuiGraphicsExtractor ctx, Font tr, String label, TextSetting s) {
      ctx.text(tr, label, this.panelX + 10, this.py + 3, -1578509, false);
      String cur = s.get().trim();
      String v = cur.isEmpty() ? "bester Tier" : cur;
      int w = tr.width(v) + 16;
      this.button(ctx, this.panelX + 176 - 10 - w, this.py, w, 14, v, -14012603, -3885571, () -> {
         int i = 0;

         for (int k = 0; k < KITS.length; k++) {
            if (KITS[k].equalsIgnoreCase(cur)) {
               i = k;
            }
         }

         s.set(KITS[(i + 1) % KITS.length]);
      });
      this.py += 18;
   }

   private void colors(GuiGraphicsExtractor ctx, String label, ColorSetting s) {
      ctx.text(this.font, label, this.panelX + 10, this.py + 3, -1578509, false);
      this.py += 14;
      int x = this.panelX + 10;

      for (int c : PALETTE) {
         boolean on = (s.get() | 0xFF000000) == c;
         Render2D.roundRect(ctx, x, this.py, 13, 13, 3, on ? -1 : 1627389951);
         Render2D.roundRect(ctx, x + 1, this.py + 1, 11, 11, 3, c);
         this.hits.add(new NametagEditorScreen.Hit(x, this.py, 13, 13, () -> s.set(c)));
         x += 15;
      }

      this.py += 20;
   }

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int bg, int fg, Runnable action) {
      boolean hov = Render2D.inside(this.mouseX, this.mouseY, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 5, hov ? Theme.lerp(bg, -1, 0.12F) : bg);
      ctx.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, fg, false);
      this.hits.add(new NametagEditorScreen.Hit(x, y, w, h, action));
   }

   private record Drop(NametagConfig.Zone zone, int index, float markerX, float markerY) {
   }

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }

   private record Tagged(NametagConfig.Element element, NametagPiece inner) implements NametagPiece {
      @Override
      public float width(Font tr) {
         return this.inner.width(tr);
      }

      @Override
      public void draw(Font tr, NameCanvas canvas, float x, float y) {
         canvas.element(this.element, x, y, this.inner.width(tr), 8.0F);
         this.inner.draw(tr, canvas, x, y);
      }
   }
}
