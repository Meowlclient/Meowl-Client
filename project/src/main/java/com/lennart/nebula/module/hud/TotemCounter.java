package com.lennart.nebula.module.hud;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.nametag.NametagPiece;
import com.lennart.nebula.nametag.TextPiece;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.totem.TotemPopTracker;
import com.lennart.nebula.util.ChatUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemCounter extends Module {
   private static final String ICON_TEXT = "Icon + Text";
   private static final String ICON_NUMBER = "Icon + Zahl";
   private static final String VANILLA = "Vanilla-Slot";
   private static final String TEXT_ONLY = "Nur Text";
   private static final String NUMBER_ONLY = "Nur Zahl";
   private final ModeSetting style = this.add(
      new ModeSetting("Stil", "Wie der Zähler aussieht", "Icon + Text", "Icon + Text", "Icon + Zahl", "Vanilla-Slot", "Nur Text", "Nur Zahl")
   );
   private final TextSetting text = this.add(new TextSetting("Text", "Text vor der Zahl", "TOTEMS: ", 32));
   private final ModeSetting iconSide = this.add(new ModeSetting("Icon-Seite", "Icon links oder rechts vom Text", "Links", "Links", "Rechts"));
   private final ColorSetting labelColor = this.add(new ColorSetting("Text-Farbe", "Farbe des Textes", -1578509));
   private final ColorSetting color = this.add(new ColorSetting("Zahl-Farbe", "Farbe der Zahl", -11930));
   private final BooleanSetting shadow = this.add(new BooleanSetting("Schatten", "Schrift mit Schatten", true));
   private final NumberSetting warnAt = this.add(new NumberSetting("Warnen ab", "Warnfarbe, wenn höchstens so viele übrig sind", 1.0, 0.0, 10.0, 1.0, ""));
   private final ColorSetting warnColor = this.add(new ColorSetting("Warn-Farbe", "Farbe bei wenigen Totems", -495247));
   private final BooleanSetting warnBlink = this.add(new BooleanSetting("Warnung blinkt", "Bei wenigen Totems blinken", true));
   private final BooleanSetting offhandWarn = this.add(new BooleanSetting("Offhand-Warnung", "Roter Rahmen, wenn kein Totem in der Offhand ist", false));
   private final BooleanSetting hideZero = this.add(new BooleanSetting("Bei 0 ausblenden", "Nichts anzeigen, wenn du keine Totems hast", false));
   private final HudBox box = this.hudBox("Totem Counter", 1.0, 40.0);
   private final BooleanSetting popsEnabled = this.add(new BooleanSetting("Pop-Zähler", "Totem-Pops von dir und anderen Spielern zählen und anzeigen", true));
   private final BooleanSetting popsSelf = this.add(new BooleanSetting("Eigene Pops", "Deine eigenen Pops in der Liste", true));
   private final BooleanSetting popsOthers = this.add(new BooleanSetting("Pops anderer", "Pops anderer Spieler in der Liste", true));
   private final NumberSetting popsMax = this.add(new NumberSetting("Max. Spieler", "Wie viele Spieler höchstens angezeigt werden", 5.0, 1.0, 12.0, 1.0, ""));
   private final NumberSetting popsHide = this.add(
      new NumberSetting("Ausblenden nach", "Eintrag ausblenden, wenn so lange kein Pop kam (0 = nie)", 60.0, 0.0, 300.0, 10.0, "s")
   );
   private final ModeSetting popsLayout = this.add(
      new ModeSetting("Pop-Layout", "Name und Pops untereinander oder in einer Zeile", "Zweizeilig", "Zweizeilig", "Einzeilig")
   );
   private final ColorSetting popsNameColor = this.add(new ColorSetting("Namens-Farbe", "Farbe der Spielernamen", -1));
   private final ColorSetting popsColor = this.add(new ColorSetting("Pop-Farbe", "Farbe der Pop-Anzahl", -11930));
   private final BooleanSetting popsName = this.add(
      new BooleanSetting("Pops am Namen", "Pops direkt am Namensschild zeigen, z.B. „Name [5 Pops]“ (setzt sich beim Tod zurück)", true)
   );
   private final BooleanSetting popsChat = this.add(new BooleanSetting("Chat-Meldung", "Bei jedem Pop und Tod eine kurze Meldung im Chat", false));
   private final HudBox popsBox = this.hudBox("Totem-Pops", 1.0, 52.0);
   // erst beim Zeichnen anlegen: beim Start sind Item-Daten in 26.x noch nicht fertig
   private static ItemStack totemIcon;

   private static ItemStack totemIcon() {
      if (totemIcon == null) {
         totemIcon = new ItemStack(Items.TOTEM_OF_UNDYING);
      }

      return totemIcon;
   }
   private int count;
   private LocalPlayer countedFor;

   public TotemCounter() {
      super("Totem Counter", "Deine Totems und die Totem-Pops aller Spieler (Zähler setzt sich nach dem Tod zurück).", Category.HUD);
      this.section(Section.PVP);
      this.icon(Items.TOTEM_OF_UNDYING);
      this.text.setVisibility(() -> this.style.is("Icon + Text") || this.style.is("Nur Text"));
      this.labelColor.setVisibility(this.text::isVisible);
      this.iconSide.setVisibility(() -> this.style.is("Icon + Text"));
      this.group(
         "Eigene Totems",
         new Setting[]{
            this.style,
            this.text,
            this.iconSide,
            this.labelColor,
            this.color,
            this.shadow,
            this.warnAt,
            this.warnColor,
            this.warnBlink,
            this.offhandWarn,
            this.hideZero,
            this.box.scale,
            this.box.opacity,
            this.box.background,
            this.box.backgroundColor,
            this.box.rounding
         }
      );
      this.group(
         "Totem-Pops (alle Spieler)",
         new Setting[]{
            this.popsEnabled,
            this.popsName,
            this.popsSelf,
            this.popsOthers,
            this.popsMax,
            this.popsHide,
            this.popsLayout,
            this.popsNameColor,
            this.popsColor,
            this.popsChat,
            this.popsBox.scale,
            this.popsBox.opacity,
            this.popsBox.background,
            this.popsBox.backgroundColor,
            this.popsBox.rounding
         }
      );

      for (Setting<?> st : new Setting[]{
         this.popsName,
         this.popsSelf,
         this.popsOthers,
         this.popsMax,
         this.popsHide,
         this.popsLayout,
         this.popsNameColor,
         this.popsColor,
         this.popsChat,
         this.popsBox.scale,
         this.popsBox.opacity,
         this.popsBox.background
      }) {
         st.setVisibility(this.popsEnabled::get);
      }

      this.popsBox.backgroundColor.setVisibility(() -> this.popsEnabled.get() && this.popsBox.background.get());
      this.popsBox.rounding.setVisibility(() -> this.popsEnabled.get() && this.popsBox.background.get());
      TotemPopTracker.addListener(new TotemPopTracker.PopListener() {
         @Override
         public void onPop(Player player, int pops) {
            if (TotemCounter.this.isEnabled() && TotemCounter.this.popsEnabled.get() && TotemCounter.this.popsChat.get()) {
               ChatUtil.chat("§6" + player.getGameProfile().name() + " §7hat ein Totem gepoppt §8(§e" + pops + "§8)");
            }
         }

         @Override
         public void onDeath(Player player, int pops) {
            if (TotemCounter.this.isEnabled() && TotemCounter.this.popsEnabled.get() && TotemCounter.this.popsChat.get() && pops > 0) {
               ChatUtil.chat("§c" + player.getGameProfile().name() + " §7ist nach §e" + pops + " §7Pops gestorben");
            }
         }
      });
   }

   public boolean popsInName() {
      return this.popsEnabled.get() && this.popsName.get();
   }

   public BooleanSetting popsNameSetting() {
      return this.popsName;
   }

   public ColorSetting popsColorSetting() {
      return this.popsColor;
   }

   public static NametagPiece namePiece(Player player) {
      NebulaClient c = NebulaClient.getInstance();
      TotemCounter t = c == null ? null : c.getModuleManager().get(TotemCounter.class);
      if (t != null && t.isEnabled() && t.popsEnabled.get() && t.popsName.get()) {
         int pops = TotemPopTracker.pops(player.getUUID());
         return pops <= 0 ? null : TextPiece.of("[" + pops + (pops == 1 ? " Pop]" : " Pops]"), t.popsColor.get());
      } else {
         return null;
      }
   }

   private void renderPops(GuiGraphicsExtractor ctx, boolean editing) {
      List<TotemPopTracker.View> list = new ArrayList<>();
      long now = System.currentTimeMillis();
      long hide = Math.round(this.popsHide.get() * 1000.0);
      UUID self = this.mc.player != null ? this.mc.player.getUUID() : null;

      for (TotemPopTracker.View v : TotemPopTracker.snapshot()) {
         boolean mine = v.uuid().equals(self);
         if (mine ? this.popsSelf.get() : this.popsOthers.get() && (hide <= 0L || now - v.lastPop() <= hide)) {
            list.add(v);
         }
      }

      if (list.isEmpty() && editing) {
         list.add(new TotemPopTracker.View(new UUID(0L, 0L), "PlayerName", 3, now));
         list.add(new TotemPopTracker.View(new UUID(0L, 1L), "Gegner", 1, now));
      }

      if (!list.isEmpty()) {
         list = list.subList(0, Math.min(this.popsMax.getInt(), list.size()));
         Font tr = this.mc.font;
         boolean two = this.popsLayout.is("Zweizeilig");
         int w = 0;

         for (TotemPopTracker.View vx : list) {
            String line = two ? vx.name() : vx.name() + "  Pops: " + vx.pops();
            w = Math.max(w, Math.max(tr.width(line), tr.width("Pops: " + vx.pops())));
         }

         int rowH = two ? 21 : 11;
         int h = list.size() * rowH + 4;
         w += 24;
         this.popsBox.begin(ctx, w, h);
         int y = 3;

         for (TotemPopTracker.View vx : list) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(3.0F, y + (two ? 2.0F : 0.0F));
            ctx.pose().scale(0.95F, 0.95F);
            ctx.item(totemIcon(), 0, 0);
            ctx.pose().popMatrix();
            int tx = 21;
            if (two) {
               ctx.text(tr, vx.name(), tx, y, this.popsBox.fade(this.popsNameColor.get()), this.shadow.get());
               ctx.text(tr, "Pops: ", tx, y + 10, this.popsBox.fade(-7564628), this.shadow.get());
               ctx.text(tr, String.valueOf(vx.pops()), tx + tr.width("Pops: "), y + 10, this.popsBox.fade(this.popsColor.get()), this.shadow.get());
            } else {
               ctx.text(tr, vx.name(), tx, y + 1, this.popsBox.fade(this.popsNameColor.get()), this.shadow.get());
               String pops = "Pops: " + vx.pops();
               ctx.text(tr, pops, w - 4 - tr.width(pops), y + 1, this.popsBox.fade(this.popsColor.get()), this.shadow.get());
            }

            y += rowH;
         }

         this.popsBox.end(ctx);
      }
   }

   @Override
   protected void onEnable() {
      this.countedFor = null;
      this.count = 0;
   }

   @Override
   public void onTick() {
      LocalPlayer player = this.mc.player;
      if (player != this.countedFor) {
         this.countedFor = player;
         this.count = 0;
      }

      if (player != null && !player.isDeadOrDying()) {
         this.count = countTotems(player);
      } else {
         this.count = 0;
      }
   }

   public static int countTotems(LocalPlayer player) {
      Inventory inv = player.getInventory();
      int n = 0;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.is(Items.TOTEM_OF_UNDYING)) {
            n += stack.getCount();
         }
      }

      if (player.containerMenu != null) {
         ItemStack cursor = player.containerMenu.getCarried();
         if (cursor.is(Items.TOTEM_OF_UNDYING)) {
            n += cursor.getCount();
         }
      }

      return n;
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      LocalPlayer player = this.mc.player;
      boolean editing = HudLayout.isEditing();
      if (this.popsEnabled.get() && player != null && (!this.mc.gui.hud.isHidden() || editing)) {
         this.renderPops(ctx, editing);
      }

      if (player != null && !this.mc.gui.hud.isHidden() && (editing || !player.isDeadOrDying() && (!this.hideZero.get() || this.count != 0))) {
         int count = editing && this.count == 0 ? 2 : this.count;
         boolean warn = count <= this.warnAt.getInt();
         boolean blinkOff = warn && this.warnBlink.get() && Util.getMillis() / 400L % 2L == 1L;
         int numColor = this.box.fade(warn ? (blinkOff ? dim(this.warnColor.get()) : this.warnColor.get()) : this.color.get());
         int lblColor = this.box.fade(this.labelColor.get());
         boolean noOffhand = this.offhandWarn.get() && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
         String num = String.valueOf(count);
         Font tr = this.mc.font;
         String var13 = this.style.get();
         switch (var13) {
            case "Vanilla-Slot": {
               int w = VanillaSlots.stripWidth(1);
               int h = 22;
               this.box.begin(ctx, w, h, false);
               VanillaSlots.cell(ctx, 0, 0, VanillaSlots.white(this.box.alpha()));
               ctx.item(totemIcon(), 3, 3);
               this.drawCornerNumber(ctx, num, 3, 3, numColor);
               this.outline(ctx, w, h, noOffhand);
               this.box.end(ctx);
               break;
            }
            case "Icon + Zahl": {
               int w = 22;
               int h = 22;
               this.box.begin(ctx, w, h);
               ctx.item(totemIcon(), 3, 3);
               this.drawCornerNumber(ctx, num, 3, 3, numColor);
               this.outline(ctx, w, h, noOffhand);
               this.box.end(ctx);
               break;
            }
            case "Nur Zahl": {
               int w = tr.width(num) + 8;
               int h = 13;
               this.box.begin(ctx, w, h);
               ctx.text(tr, num, 4, 3, numColor, this.shadow.get());
               this.outline(ctx, w, h, noOffhand);
               this.box.end(ctx);
               break;
            }
            case "Nur Text": {
               String label = this.text.get();
               int w = tr.width(label) + tr.width(num) + 8;
               int h = 13;
               this.box.begin(ctx, w, h);
               ctx.text(tr, label, 4, 3, lblColor, this.shadow.get());
               ctx.text(tr, num, 4 + tr.width(label), 3, numColor, this.shadow.get());
               this.outline(ctx, w, h, noOffhand);
               this.box.end(ctx);
               break;
            }
            default: {
               String label = this.text.get();
               int textW = tr.width(label) + tr.width(num);
               int w = textW + 8 + 18;
               int h = 20;
               boolean right = this.iconSide.is("Rechts");
               this.box.begin(ctx, w, h);
               int tx = right ? 4 : 22;
               ctx.item(totemIcon(), right ? w - 18 : 2, 2);
               ctx.text(tr, label, tx, 6, lblColor, this.shadow.get());
               ctx.text(tr, num, tx + tr.width(label), 6, numColor, this.shadow.get());
               this.outline(ctx, w, h, noOffhand);
               this.box.end(ctx);
            }
         }
      }
   }

   private void drawCornerNumber(GuiGraphicsExtractor ctx, String num, int x, int y, int color) {
      ctx.text(this.mc.font, num, x + 17 - this.mc.font.width(num), y + 9, color, true);
   }

   private void outline(GuiGraphicsExtractor ctx, int w, int h, boolean show) {
      if (show) {
         int c = this.box.fade(this.warnColor.get());
         ctx.fill(0, 0, w, 1, c);
         ctx.fill(0, h - 1, w, h, c);
         ctx.fill(0, 0, 1, h, c);
         ctx.fill(w - 1, 0, w, h, c);
      }
   }

   private static int dim(int argb) {
      int a = (argb >>> 24) * 35 / 100;
      return a << 24 | argb & 16777215;
   }
}
