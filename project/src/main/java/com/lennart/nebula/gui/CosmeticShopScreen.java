package com.lennart.nebula.gui;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.cosmetic.PreviewOverrides;
import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.emote.EmoteType;
import com.lennart.nebula.gui.component.ComponentFactory;
import com.lennart.nebula.gui.component.SettingComponent;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.ImageLibrarySetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.setting.StyleSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Der Kosmetik-Planet als Shop: links die Kategorien, in der Mitte die Artikel als Karten mit Vorschau (Kosmetik am
 * Charakter, Emotes werden vorgespielt, Todesanimationen laufen ab), Seltenheit und Preis in Sternen. Ein Klick rüstet
 * aus und öffnet rechts eine Leiste mit großer Vorschau und den Einstellungen. Noch ohne Bezahlung: dir gehört alles.
 */
public class CosmeticShopScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private final MeowlScreen parent;
   private final List<ShopCatalog.Category> categories;
   private int selected;
   private int rarityFilter = -1;
   private float scroll;
   private float scrollTarget;
   private int maxScroll;
   private final List<Hit> hits = new ArrayList<>();
   private final float[][] stars;
   private final long opened = System.currentTimeMillis();
   private String tooltip;
   private final Map<EmoteType, EmoteState.Active> emotes = new EnumMap<>(EmoteType.class);
   // Detail-Leiste rechts
   private ShopCatalog.Item detail;
   private final List<SettingComponent<?>> components = new ArrayList<>();
   private float panelScroll;
   private int panelMax;
   private int[] panelBox = new int[4];
   private int[] gridBox = new int[4];
   private float panelAnim;

   private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
   }

   public CosmeticShopScreen(MeowlScreen parent) {
      super(Component.literal("Kosmetik-Planet"));
      this.parent = parent;
      this.categories = ShopCatalog.build(NebulaClient.getInstance().getModuleManager());
      Random r = new Random(17L);
      this.stars = new float[160][];

      for (int i = 0; i < this.stars.length; i++) {
         this.stars[i] = new float[]{r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28F, r.nextFloat() < 0.12F ? 2.0F : 1.0F};
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      if (this.detail != null) {
         this.closeDetail();
      } else {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private ShopCatalog.Category current() {
      return this.categories.isEmpty() ? null : this.categories.get(Math.max(0, Math.min(this.selected, this.categories.size() - 1)));
   }

   private List<ShopCatalog.Item> visibleItems() {
      ShopCatalog.Category c = this.current();
      List<ShopCatalog.Item> out = new ArrayList<>();
      if (c != null) {
         for (ShopCatalog.Item it : c.items()) {
            if (this.rarityFilter < 0 || it.rarity().ordinal() == this.rarityFilter) {
               out.add(it);
            }
         }
      }

      return out;
   }

   private static boolean isDeath(ShopCatalog.Item it) {
      return it.module().getClass().getSimpleName().equals("DeathAnimation");
   }

   private static boolean isMood(ShopCatalog.Item it) {
      return it.module().getClass().getSimpleName().equals("MoodIcon");
   }

   // ================================================================== Zeichnen

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.hits.clear();
      this.tooltip = null;
      long now = System.currentTimeMillis();
      float t = (now - this.opened) / 1000.0F;
      this.scroll += (this.scrollTarget - this.scroll) * 0.3F;
      this.panelAnim += ((this.detail != null ? 1.0F : 0.0F) - this.panelAnim) * 0.35F;
      int w = this.width;
      int h = this.height;
      this.background(ctx, w, h, t);
      // obere Leiste
      int top = 8;
      this.pill(ctx, 8, top, "‹ Zurück", mouseX, mouseY, () -> this.minecraft.gui.setScreen(this.parent));
      ctx.text(this.font, Component.literal("✦ Kosmetik-Planet").withStyle(ChatFormatting.BOLD), 72, top + 3, 0xFFF3E9FF, false);
      ctx.text(this.font, "Alles für deinen Look an einem Ort", 72, top + 14, 0xFF8E86B0, false);
      String bal = "✦ ∞ Sterne";
      int bw = this.font.width(bal) + 16;
      Render2D.roundRect(ctx, w - bw - 8, top, bw, 18, 9, 0xC02A1F4A);
      Render2D.roundRect(ctx, w - bw - 7, top + 1, bw - 2, 16, 8, 0xE0181230);
      ctx.text(this.font, bal, w - bw, top + 5, 0xFFFFD36A, false);
      String test = "Testphase: alles freigeschaltet";
      if (w > 420) {
         ctx.text(this.font, test, w - bw - 14 - this.font.width(test), top + 5, 0xFF7CE3B0, false);
      }

      // Kategorien links
      int sideW = Math.min(150, Math.max(104, w / 5));
      int sy = 40;
      Render2D.roundRect(ctx, 6, sy - 4, sideW, h - sy - 18, 8, 0x90100C24);
      int rowH = Math.max(14, Math.min(22, (h - sy - 30) / Math.max(1, this.categories.size())));

      for (int i = 0; i < this.categories.size(); i++) {
         ShopCatalog.Category c = this.categories.get(i);
         int ry = sy + i * rowH;
         boolean sel = i == this.selected;
         boolean hov = Render2D.inside(mouseX, mouseY, 10, ry, sideW - 8, rowH - 2);
         if (sel) {
            Render2D.glow(ctx, 10, ry, sideW - 8, rowH - 2, 6, c.color() & 0xFFFFFF, 0.35F);
            Render2D.roundRect(ctx, 10, ry, sideW - 8, rowH - 2, 6, Render2D.alpha(c.color(), 0.32F));
         } else if (hov) {
            Render2D.roundRect(ctx, 10, ry, sideW - 8, rowH - 2, 6, 0x40FFFFFF);
         }

         int pr = Math.max(3, (rowH - 8) / 2);
         int pcx = 18 + pr;
         int pcy = ry + (rowH - 2) / 2;
         Render2D.circle(ctx, pcx, pcy, pr, c.color());
         Render2D.circle(ctx, pcx - pr / 3, pcy - pr / 3, Math.max(1, pr / 3), 0x60FFFFFF);
         String name = Lang.t(c.name());
         ctx.text(this.font, this.font.plainSubstrByWidth(name, sideW - 50), pcx + pr + 6, pcy - 4, sel ? -1 : 0xFFB8B2D8, false);
         String n = String.valueOf(c.items().size());
         ctx.text(this.font, n, 10 + sideW - 12 - this.font.width(n), pcy - 4, 0xFF6E6890, false);
         int idx = i;
         this.hits.add(new Hit(10, ry, sideW - 8, rowH - 2, () -> {
            this.selected = idx;
            this.scrollTarget = 0.0F;
            this.rarityFilter = -1;
            this.closeDetail();
         }, null));
      }

      // Detail-Leiste rechts (schiebt sich herein)
      int panelW = Math.round(Math.min(200, Math.max(150, w * 0.28F)) * this.panelAnim);
      ShopCatalog.Category cat = this.current();
      int cx = sideW + 16;
      int cw = w - cx - 8 - (panelW > 0 ? panelW + 8 : 0);
      if (cat != null) {
         this.content(ctx, cat, cx, 38, cw, h - 38 - 16, mouseX, mouseY, t);
      }

      if (panelW > 4 && this.detail != null) {
         this.panel(ctx, w - 8 - panelW, 36, panelW, h - 36 - 18, mouseX, mouseY, t, delta);
      } else {
         this.panelBox = new int[4];
      }

      String hint = this.detail == null ? "Klick: ausrüsten & Details  ·  Kaufen kommt später" : "Regler rechts ändern dein Ausgerüstetes sofort";
      ctx.text(this.font, hint, (w - this.font.width(hint)) / 2, h - 12, 0xFF6E6890, false);
      if (this.tooltip != null) {
         ctx.setTooltipForNextFrame(this.font, Component.literal(this.tooltip), mouseX, mouseY);
      }
   }

   private void background(GuiGraphicsExtractor ctx, int w, int h, float t) {
      ctx.fillGradient(0, 0, w, h, 0xFF070514, 0xFF140A2C);
      // Nebel als weiche Scheiben (über die Matrix vergrößert, das ist billig)
      this.bigCircle(ctx, w * 0.25F + (float)Math.sin(t * 0.05F) * 20.0F, h * 0.32F, Math.min(w, h) * 0.32F, 0x186A2BC8);
      this.bigCircle(ctx, w * 0.72F, h * 0.7F + (float)Math.cos(t * 0.04F) * 15.0F, Math.min(w, h) * 0.36F, 0x12C83A8C);
      this.bigCircle(ctx, w * 0.88F, h * 0.2F, Math.min(w, h) * 0.22F, 0x142A7AD8);

      for (float[] s : this.stars) {
         float tw = 0.45F + 0.55F * (float)Math.sin(t * (0.8F + s[2] * 2.0F) + s[3]);
         int a = Math.round(40 + 190 * tw * (0.4F + s[2] * 0.6F));
         int x = Math.round(s[0] * w);
         int y = Math.round(s[1] * h);
         int sz = Math.round(s[4]);
         ctx.fill(x, y, x + sz, y + sz, a << 24 | 0xFFFFFF);
      }

      float pr = Math.min(w, h) * 0.42F;
      float pcx = w - pr / 3.0F;
      float pcy = h + pr / 2.0F;
      this.bigCircle(ctx, pcx, pcy, pr * 1.12F, 0x20B45CFF);
      this.bigCircle(ctx, pcx, pcy, pr, 0xFF2A1450);
      this.bigCircle(ctx, pcx - pr / 8.0F, pcy - pr / 8.0F, pr * 0.86F, 0xFF341A66);
      this.ringDots(ctx, pcx, pcy, pr * 1.25F, 0x50E0B8FF, 1.0F);
   }

   /** Große Scheibe: Kreis-Textur mit Radius 64 über die Matrix skaliert (viel schneller als Zeilen-Füllen). */
   private void bigCircle(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      ctx.pose().scale(r / 64.0F, r / 64.0F);
      Render2D.circle(ctx, 0, 0, 64, color);
      ctx.pose().popMatrix();
   }

   /** Ring aus kleinen Punkten (Render2D.ring füllt innen). */
   private void ringDots(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color, float size) {
      int n = Math.max(24, Math.min(160, Math.round(r * 0.9F)));
      for (int i = 0; i < n; i++) {
         double a = i * Math.PI * 2.0 / n;
         float x = cx + (float)Math.cos(a) * r;
         float y = cy + (float)Math.sin(a) * r;
         ctx.fill(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size), color);
      }
   }

   private void content(GuiGraphicsExtractor ctx, ShopCatalog.Category cat, int x, int y, int w, int h, int mouseX, int mouseY, float t) {
      Module m = cat.module();
      ctx.text(this.font, Component.literal(Lang.t(cat.name())).withStyle(ChatFormatting.BOLD), x, y, cat.color(), false);
      ctx.text(this.font, this.font.plainSubstrByWidth(cat.blurb(), Math.max(20, w - 160)), x, y + 11, 0xFF9C94C4, false);
      String on = m.isEnabled() ? "● An" : "○ Aus";
      int ow = this.font.width(on) + 14;
      int ox = x + w - ow;
      Render2D.roundRect(ctx, ox, y - 2, ow, 16, 8, m.isEnabled() ? 0xC03ECF8E : 0xC0403A5A);
      ctx.text(this.font, on, ox + 7, y + 2, -1, false);
      this.hits.add(new Hit(ox, y - 2, ow, 16, () -> {
         m.toggle();
         this.save();
      }, null));
      int fy = y + 26;
      int fx = x;
      fx = this.chip(ctx, fx, fy, "Alle", 0xFFE8E2FF, this.rarityFilter < 0, mouseX, mouseY, () -> {
         this.rarityFilter = -1;
         this.scrollTarget = 0.0F;
      });

      for (ShopCatalog.Rarity r : ShopCatalog.Rarity.values()) {
         int ri = r.ordinal();
         fx = this.chip(ctx, fx, fy, r.label, r.color, this.rarityFilter == ri, mouseX, mouseY, () -> {
            this.rarityFilter = ri;
            this.scrollTarget = 0.0F;
         });
      }

      int gy = fy + 20;
      int gh = y + h - gy;
      this.gridBox = new int[]{x, gy, w, gh};
      List<ShopCatalog.Item> items = this.visibleItems();
      int cardW = 78;
      int cardH = 104;
      int gap = 8;
      int cols = Math.max(1, (w + gap) / (cardW + gap));
      cardW = (w - (cols - 1) * gap) / cols;
      int rows = (items.size() + cols - 1) / cols;
      this.maxScroll = Math.max(0, rows * (cardH + gap) - gap - gh);
      this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll, this.scrollTarget));
      ctx.enableScissor(x - 4, gy, x + w + 4, gy + gh);
      LocalPlayer player = this.minecraft.player;

      for (int i = 0; i < items.size(); i++) {
         ShopCatalog.Item it = items.get(i);
         int kx = x + i % cols * (cardW + gap);
         int ky = gy + i / cols * (cardH + gap) - Math.round(this.scroll);
         if (ky + cardH < gy || ky > gy + gh) {
            continue;
         }

         boolean hov = Render2D.inside(mouseX, mouseY, kx, ky, cardW, cardH) && mouseY >= gy && mouseY < gy + gh;
         boolean eq = it.equipped();
         boolean open = it == this.detail;
         int rc = it.rarity().color;
         if (hov || eq || open) {
            Render2D.glow(ctx, kx, ky, cardW, cardH, 8, rc & 0xFFFFFF, open ? 0.6F : (eq ? 0.45F : 0.3F));
         }

         Render2D.roundRect(ctx, kx, ky, cardW, cardH, 8, eq || open ? rc : Render2D.alpha(rc, hov ? 0.85F : 0.45F));
         Render2D.roundRect(ctx, kx + 1, ky + 1, cardW - 2, cardH - 2, 7, hov || open ? 0xF0201838 : 0xF0150F2A);
         int px = kx + 4;
         int py = ky + 4;
         int pw = cardW - 8;
         int ph = cardH - 36;
         ctx.fillGradient(px, py, px + pw, py + ph, 0x00000000, Render2D.alpha(rc, 0.28F));
         this.preview(ctx, it, player, px, py, pw, ph, t, false);
         String name = this.font.plainSubstrByWidth(it.name(), cardW - 8);
         ctx.text(this.font, name, kx + (cardW - this.font.width(name)) / 2, ky + cardH - 30, -1, false);
         this.small(ctx, it.rarity().label, kx + 5, ky + cardH - 19, rc);
         String price = it.price() == 0 ? "Gratis" : "✦ " + it.price();
         int pwid = Math.round(this.font.width(price) * 0.75F) + 8;
         Render2D.roundRect(ctx, kx + cardW - pwid - 4, ky + cardH - 21, pwid, 11, 5, 0xC02A2048);
         this.small(ctx, price, kx + cardW - pwid, ky + cardH - 19, it.price() == 0 ? 0xFF7CE3B0 : 0xFFFFD36A);
         String status = eq ? "✔ Ausgerüstet" : (it.owned() ? "Besitzt du" : "Kaufen");
         this.small(ctx, status, kx + 5, ky + cardH - 9, eq ? 0xFF7CE3B0 : 0xFF8E86B0);
         if (hov) {
            this.tooltip = it.emote() != null ? "Emote „" + it.name() + "“" : (eq ? "Ausgerüstet – Klick für Details" : "Klick: " + it.name() + " ausrüsten");
         }

         this.hits.add(new Hit(kx, Math.max(ky, gy), cardW, Math.min(ky + cardH, gy + gh) - Math.max(ky, gy), () -> this.select(it), null));
      }

      ctx.disableScissor();
      if (this.maxScroll > 0) {
         int barH = Math.max(20, gh * gh / (gh + this.maxScroll));
         int barY = gy + Math.round((gh - barH) * (this.scroll / this.maxScroll));
         Render2D.roundRect(ctx, x + w + 1, barY, 3, barH, 1, 0x80B8A8FF);
      }
   }

   /** Rechte Leiste: große Vorschau, Name, Preis und die kleinen Einstellungen des Moduls. */
   private void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, float t, float delta) {
      ShopCatalog.Item it = this.detail;
      this.panelBox = new int[]{x, y, w, h};
      int rc = it.rarity().color;
      Render2D.glow(ctx, x, y, w, h, 10, rc & 0xFFFFFF, 0.25F);
      Render2D.roundRect(ctx, x, y, w, h, 10, Render2D.alpha(rc, 0.7F));
      Render2D.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 9, 0xF4120C24);
      // Schließen
      boolean hx = Render2D.inside(mouseX, mouseY, x + w - 16, y + 4, 12, 12);
      ctx.text(this.font, "✕", x + w - 13, y + 6, hx ? -1 : 0xFF8E86B0, false);
      this.hits.add(new Hit(x + w - 16, y + 4, 12, 12, this::closeDetail, null));
      ctx.text(this.font, this.font.plainSubstrByWidth(it.name(), w - 26), x + 8, y + 7, -1, false);
      this.small(ctx, it.rarity().label + "  ·  " + (it.price() == 0 ? "Gratis" : "✦ " + it.price()), x + 8, y + 18, rc);
      // große Vorschau
      int py = y + 28;
      int ph = Math.min(130, Math.max(80, h / 3));
      ctx.fillGradient(x + 6, py, x + w - 6, py + ph, 0x00000000, Render2D.alpha(rc, 0.3F));
      this.preview(ctx, it, this.minecraft.player, x + 6, py, w - 12, ph, t, true);
      // Ausrüsten / Ablegen
      int by = py + ph + 6;
      boolean eq = it.equipped();
      String label = it.emote() != null ? "▶ Emote-Rad belegen" : (eq ? "✔ Ausgerüstet – ablegen" : "Ausrüsten");
      boolean hb = Render2D.inside(mouseX, mouseY, x + 6, by, w - 12, 16);
      Render2D.roundRect(ctx, x + 6, by, w - 12, 16, 8, eq ? (hb ? 0xE0405A4A : 0xC02E5040) : (hb ? 0xE09B6CFF : 0xC07A4CE0));
      ctx.centeredText(this.font, label, x + w / 2, by + 4, -1);
      this.hits.add(new Hit(x + 6, by, w - 12, 16, () -> {
         if (it.emote() != null) {
            this.equip(it);
         } else if (it.equipped()) {
            it.module().setEnabled(false);
            this.save();
         } else {
            this.equip(it);
         }
      }, null));
      // Einstellungen
      int sy = by + 22;
      ctx.text(this.font, Component.literal("Einstellungen").withStyle(ChatFormatting.BOLD), x + 8, sy, 0xFFD8D0FF, false);
      int listTop = sy + 12;
      int listH = y + h - 6 - listTop;
      int total = 0;

      for (SettingComponent<?> c : this.components) {
         if (c.getSetting().isVisible()) {
            total += c.getHeight();
         }
      }

      this.panelMax = Math.max(0, total - listH);
      this.panelScroll = Math.max(0.0F, Math.min(this.panelMax, this.panelScroll));
      ctx.enableScissor(x + 2, listTop, x + w - 2, listTop + listH);
      int cy = listTop - Math.round(this.panelScroll);

      for (SettingComponent<?> c : this.components) {
         if (!c.getSetting().isVisible()) {
            continue;
         }

         c.setBounds(x + 2, cy, w - 6);
         if (cy + c.getHeight() > listTop && cy < listTop + listH) {
            c.render(ctx, this.font, mouseX, mouseY, delta);
         }

         cy += c.getHeight();
      }

      ctx.disableScissor();
      if (this.components.isEmpty()) {
         this.small(ctx, "Keine weiteren Einstellungen", x + 8, listTop + 2, 0xFF8E86B0);
      }
   }

   /** Vorschau: Charakter mit der Variante / dem Emote / der Todesanimation, sonst großes Symbol. */
   private void preview(GuiGraphicsExtractor ctx, ShopCatalog.Item it, LocalPlayer player, int x, int y, int w, int h, float t, boolean big) {
      if (player != null && it.emote() != null) {
         EmoteState.Active a = this.emotes.get(it.emote());
         float now = EmoteState.now();
         if (a == null || !it.emote().loops() && now - a.start > it.emote().duration + 0.8F) {
            a = new EmoteState.Active(it.emote(), now);
            this.emotes.put(it.emote(), a);
         }

         this.drawPlayer(ctx, player, null, null, a, -1.0F, x, y, w, h, 155.0F + (big ? (float)Math.sin(t * 0.6F) * 30.0F : 0.0F), 1.0F);
         return;
      }

      if (player != null && isDeath(it)) {
         this.deathPreview(ctx, player, it.name(), x, y, w, h, t);
         return;
      }

      String kind = it.module().getClass().getSimpleName();
      if (player != null && kind.equals("Watches")) {
         this.drawPlayer(ctx, player, it.module(), it.mode(), null, -1.0F, x, y, w, h, 150.0F + (float)Math.sin(t * 0.7F) * 20.0F, 2.1F, 0.72F, null);
         return;
      }

      if (player != null && kind.equals("PlasmaMode")) {
         this.drawPlayer(ctx, player, it.module(), it.mode() != null ? it.mode() : "saber", null, -1.0F, x, y, w, h, 135.0F + (float)Math.sin(t * 0.6F) * 25.0F, 0.85F, -1.0F, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
         return;
      }

      if (kind.equals("ProfilePicture")) {
         this.profilePreview(ctx, player, x, y, w, h, t);
         return;
      }

      if (kind.equals("TotemInspect")) {
         this.totemPreview(ctx, it.name(), x, y, w, h, t, big);
         return;
      }

      if (it.setting() instanceof StyleSetting st && player != null) {
         float zoom = isMood(it) ? 0.72F : 1.0F;
         float yaw = (st.fromBehind() ? 20.0F : 155.0F) + (float)Math.sin(t * 0.8F) * (big ? 40.0F : 25.0F);
         this.drawPlayer(ctx, player, st, it.mode(), null, -1.0F, x, y, w, h, yaw, zoom);
         return;
      }

      ctx.pose().pushMatrix();
      ctx.pose().translate(x + w / 2.0F, y + h / 2.0F + (float)Math.sin(t * 2.0F + it.name().length()) * 2.0F);
      float s = big ? 3.4F : 2.4F;
      ctx.pose().scale(s, s);
      ctx.item(it.module().getIcon(), -8, -8);
      ctx.pose().popMatrix();
   }

   /** Profilbild: Kopf deines Charakters, darüber ein Namensschild mit deinem Bild. */
   private void profilePreview(GuiGraphicsExtractor ctx, LocalPlayer player, int x, int y, int w, int h, float t) {
      if (player != null) {
         this.drawPlayer(ctx, player, null, null, null, -1.0F, x, y + h / 6, w, h - h / 6, 155.0F + (float)Math.sin(t * 0.7F) * 20.0F, 1.6F, 1.25F, null);
      }

      String name = player == null ? "Du" : player.getName().getString();
      int icon = Math.max(8, Math.min(16, h / 6));
      int tw = Math.round(this.font.width(name) * 0.75F);
      int bw = icon + 4 + tw + 6;
      int bx = x + (w - bw) / 2;
      int by = y + 3;
      Render2D.roundRect(ctx, bx, by, bw, icon + 4, 3, 0xA0000000);
      net.minecraft.resources.Identifier pfp = player == null ? null : com.lennart.nebula.module.mods.ProfilePicture.iconFor(player);
      if (pfp != null) {
         ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, pfp, bx + 2, by + 2, 0.0F, 0.0F, icon, icon, 16, 16, 16, 16);
      } else {
         Render2D.roundRect(ctx, bx + 2, by + 2, icon, icon, 2, 0xFF8B5CF6);
         this.small(ctx, "?", bx + 2 + icon / 2 - 2, by + 2 + icon / 2 - 3, -1);
      }

      this.small(ctx, name, bx + icon + 6, by + 2 + (icon - 6) / 2, -1);
   }

   /** Totem Inspect: der Totem wird je nach Stil gedreht, verdoppelt, umkreist oder leuchtet. */
   private void totemPreview(GuiGraphicsExtractor ctx, String style, int x, int y, int w, int h, float t, boolean big) {
      net.minecraft.world.item.ItemStack totem = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
      float cx = x + w / 2.0F;
      float cy = y + h / 2.0F;
      float base = (big ? 4.2F : 2.8F) * Math.min(w, h) / 70.0F;
      float speed = style.equals("Schnell") ? 3.2F : 1.4F;
      float spin = (float)Math.cos(t * speed);
      if (style.equals("Leuchten") || style.equals("Spektakulär")) {
         float p = 0.5F + 0.5F * (float)Math.sin(t * 3.0F);
         this.bigCircle(ctx, cx, cy, base * 9.0F * (0.85F + 0.15F * p), Math.round(40 + 50 * p) << 24 | 0xFFE070);
      }

      if (style.equals("Spektakulär")) {
         for (int i = 0; i < 8; i++) {
            double a = t * 1.5F + i * Math.PI / 4.0;
            float r0 = base * 6.0F;
            float r1 = base * 11.0F;
            for (float r = r0; r < r1; r += 1.5F) {
               ctx.fill(Math.round(cx + (float)Math.cos(a) * r), Math.round(cy + (float)Math.sin(a) * r), Math.round(cx + (float)Math.cos(a) * r) + 1, Math.round(cy + (float)Math.sin(a) * r) + 1, 0x90FFF0A0);
            }
         }
      }

      if (style.equals("Partikel-Wirbel")) {
         for (int i = 0; i < 14; i++) {
            double a = t * 2.2F + i * Math.PI * 2.0 / 14.0;
            float r = base * (6.5F + (float)Math.sin(t * 2.0F + i) * 1.5F);
            int px = Math.round(cx + (float)Math.cos(a) * r);
            int py = Math.round(cy + (float)Math.sin(a) * r * 0.55F);
            ctx.fill(px, py, px + 2, py + 2, i % 2 == 0 ? 0xE07CE36A : 0xE0FFD84A);
         }
      }

      if (style.equals("Zwei Hände")) {
         this.totemIcon(ctx, totem, cx - base * 5.0F, cy + (float)Math.sin(t * 2.0F) * 2.0F, base, (float)Math.cos(t * speed + 1.0F));
         this.totemIcon(ctx, totem, cx + base * 5.0F, cy + (float)Math.sin(t * 2.0F + 1.5F) * 2.0F, base, spin);
      } else {
         this.totemIcon(ctx, totem, cx, cy + (float)Math.sin(t * 2.0F) * 2.0F, base, spin);
      }
   }

   private void totemIcon(GuiGraphicsExtractor ctx, net.minecraft.world.item.ItemStack stack, float cx, float cy, float scale, float spin) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      ctx.pose().scale(scale * Math.max(0.15F, Math.abs(spin)), scale);
      ctx.item(stack, -8, -8);
      ctx.pose().popMatrix();
   }

   /** Todesanimation: Charakter fällt um, darüber der jeweilige Effekt (Schleife alle 3 Sekunden). */
   private void deathPreview(GuiGraphicsExtractor ctx, LocalPlayer player, String style, int x, int y, int w, int h, float t) {
      float cyc = t % 3.0F;
      float fall = Math.min(1.0F, cyc / 0.6F);
      float fade = cyc > 2.6F ? 1.0F - (cyc - 2.6F) / 0.4F : 1.0F;
      if (fade > 0.05F) {
         this.drawPlayer(ctx, player, null, null, null, fall * 20.0F, x, y, w, h, 150.0F, 0.9F);
      }

      int cx = x + w / 2;
      int ground = y + h - 8;
      float k = Math.max(0.0F, cyc - 0.5F);
      int s = Math.max(1, Math.round(h / 60.0F));
      switch (style) {
         case "Lichtschlag" -> {
            if (k < 0.5F) {
               int a = Math.round(255 * (1.0F - k / 0.5F));
               int bx = cx;

               for (int yy = y; yy < ground; yy += 4 * s) {
                  int nx = cx + (int)((Math.sin(yy * 0.7F + t * 40.0F)) * 4 * s);
                  ctx.fill(Math.min(bx, nx), yy, Math.max(bx, nx) + 2 * s, yy + 4 * s, a << 24 | 0xE8F0FF);
                  bx = nx;
               }

               ctx.fill(x, y, x + w, y + h, Math.round(a * 0.25F) << 24 | 0xFFFFFF);
            }
         }
         case "Schockwelle" -> {
            for (int i = 0; i < 3; i++) {
               float r = (k - i * 0.25F) * w * 0.6F;
               if (r > 2.0F && r < w * 0.7F) {
                  int a = Math.round(220 * (1.0F - r / (w * 0.7F)));
                  this.ringDots(ctx, cx, ground - 4, r, a << 24 | 0x7CC8FF, Math.max(1, s));
               }
            }
         }
         case "Grabstein" -> {
            float rise = Math.min(1.0F, k / 0.5F);
            int gw = Math.round(w * 0.28F);
            int gh = Math.round(h * 0.42F * rise);
            int gx = cx - gw / 2 + Math.round(w * 0.12F);
            Render2D.roundRect(ctx, gx, ground - gh, gw, gh, Math.min(gw / 2, 6), 0xFF8A8C96);
            Render2D.roundRect(ctx, gx + 2, ground - gh + 2, gw - 4, Math.max(0, gh - 4), Math.min(gw / 2, 5), 0xFFA8AAB4);
            if (rise > 0.8F) {
               this.small(ctx, "RIP", gx + gw / 2 - 6, ground - gh + gh / 3, 0xFF3A3C46);
            }
         }
         case "Sternenwirbel" -> {
            for (int i = 0; i < 10; i++) {
               float a = k * 5.0F + i * 0.63F;
               float r = (8 + i * 1.6F) * s * (0.6F + k);
               int sx = cx + Math.round((float)Math.cos(a) * r);
               int sy = ground - 10 * s - Math.round(k * h * 0.25F) - Math.round((float)Math.sin(a) * r * 0.4F) - i * s;
               int al = Math.round(230 * Math.max(0.0F, 1.0F - k / 2.2F));
               ctx.fill(sx - s, sy, sx + s + 1, sy + 1, al << 24 | 0xFFE08A);
               ctx.fill(sx, sy - s, sx + 1, sy + s + 1, al << 24 | 0xFFE08A);
            }
         }
         default -> {
            // Seelen-Aufstieg: kleiner Geist steigt auf
            float up = k * h * 0.45F;
            int gx = cx;
            int gy = ground - 12 * s - Math.round(up);
            int a = Math.round(220 * Math.max(0.0F, 1.0F - k / 2.0F));
            int gw = 8 * s;
            ctx.fill(gx - gw / 2, gy - gw, gx + gw / 2, gy + gw / 2, a << 24 | 0xE8F0FF);
            ctx.fill(gx - gw / 2 + s, gy + gw / 2, gx + gw / 2 - s, gy + gw / 2 + s * 2, a << 24 | 0xE8F0FF);
            ctx.fill(gx - 2 * s, gy - gw / 2, gx - s, gy - gw / 2 + s * 2, a << 24 | 0x30304A);
            ctx.fill(gx + s, gy - gw / 2, gx + 2 * s, gy - gw / 2 + s * 2, a << 24 | 0x30304A);
         }
      }
   }

   private void drawPlayer(GuiGraphicsExtractor ctx, LocalPlayer player, StyleSetting st, String style, EmoteState.Active emote, float deathTime, int bx, int by, int bw, int bh, float yaw, float zoom) {
      this.drawPlayer(ctx, player, st == null ? null : st.target(), style, emote, deathTime, bx, by, bw, bh, yaw, zoom, -1.0F, null);
   }

   /** {@code focus}: diese Höhe (in Blöcken über den Füßen) kommt in die Bildmitte (-1 = ganze Figur). */
   private void drawPlayer(GuiGraphicsExtractor ctx, LocalPlayer player, Object target, String style, EmoteState.Active emote, float deathTime, int bx, int by, int bw, int bh, float yaw, float zoom, float focus, net.minecraft.world.item.ItemStack hand) {
      try {
         EntityRenderDispatcher manager = this.minecraft.getEntityRenderDispatcher();
         EntityRenderer<? super LocalPlayer, ?> renderer = manager.getRenderer(player);
         EntityRenderState state = renderer.createRenderState(player, 1.0F);
         state.lightCoords = 15728880;
         state.shadowPieces.clear();
         state.outlineColor = 0;
         state.nameTag = null;
         if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = yaw;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.walkAnimationSpeed = 0.0F;
            living.walkAnimationPos = 0.0F;
            living.hasRedOverlay = deathTime > 0.0F;
            living.deathTime = Math.max(0.0F, deathTime);
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
         }

         if (state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid) {
            humanoid.rightHandItemState.clear();
            humanoid.leftHandItemState.clear();
            humanoid.rightHandItemStack = hand == null ? net.minecraft.world.item.ItemStack.EMPTY : hand;
            if (hand != null) {
               // der Spieler-Layer zeichnet nur, wenn ein Item-Modell da ist (danach malt Meowl die Energieklinge)
               this.minecraft.getItemModelResolver().updateForLiving(humanoid.rightHandItemState, hand, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
            }
            humanoid.leftHandItemStack = net.minecraft.world.item.ItemStack.EMPTY;
         }

         if (target != null) {
            PreviewOverrides.put(state, target, style);
         } else {
            // Emote/Tod: ohne Kosmetik, damit man die Bewegung sieht
            PreviewOverrides.put(state, PreviewOverrides.WINGS, null);
         }

         if (emote != null) {
            EmoteState.previewState(state, emote);
         }

         int size = Math.max(10, Math.round(bh / 2.35F * zoom));
         int feet = focus < 0.0F ? by + bh - 4 : Math.round(by + bh / 2.0F + focus * size);
         float cy = (by + by + bh) / 2.0F;
         Quaternionf camera = new Quaternionf().rotateX(-0.15F);
         Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).mul(camera);
         ctx.entity(state, size, new Vector3f(0.0F, (feet - cy) / size, 0.0F), rotation, camera, bx, by, bx + bw, by + bh);
      } catch (RuntimeException var17) {
      }
   }

   private void small(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(x, y);
      ctx.pose().scale(0.75F, 0.75F);
      ctx.text(this.font, s, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   private int chip(GuiGraphicsExtractor ctx, int x, int y, String label, int color, boolean sel, int mouseX, int mouseY, Runnable action) {
      int w = this.font.width(label) + 14;
      boolean hov = Render2D.inside(mouseX, mouseY, x, y, w, 14);
      Render2D.roundRect(ctx, x, y, w, 14, 7, sel ? Render2D.alpha(color, 0.9F) : (hov ? 0x50FFFFFF : 0x30FFFFFF));
      ctx.text(this.font, label, x + 7, y + 3, sel ? 0xFF120A24 : color, false);
      this.hits.add(new Hit(x, y, w, 14, action, null));
      return x + w + 5;
   }

   private void pill(GuiGraphicsExtractor ctx, int x, int y, String label, int mouseX, int mouseY, Runnable action) {
      int w = this.font.width(label) + 14;
      boolean hov = Render2D.inside(mouseX, mouseY, x, y, w, 16);
      Render2D.roundRect(ctx, x, y, w, 16, 8, hov ? 0xE08B5CF6 : 0xC02A1F4A);
      ctx.text(this.font, label, x + 7, y + 4, -1, false);
      this.hits.add(new Hit(x, y, w, 16, action, null));
   }

   // ================================================================== Aktionen

   private void save() {
      NebulaClient c = NebulaClient.getInstance();
      if (c != null && c.getConfigManager() != null) {
         c.getConfigManager().save();
      }
   }

   /** Klick auf eine Karte: ausrüsten und die Detail-Leiste öffnen. */
   private void select(ShopCatalog.Item it) {
      if (it.emote() == null) {
         this.equip(it);
      }

      if (this.detail == null || this.detail.module() != it.module()) {
         this.components.clear();
         this.panelScroll = 0.0F;

         for (Setting<?> s : it.module().getSettings()) {
            if (s instanceof StyleSetting || s instanceof KeybindSetting || s instanceof StringListSetting || s instanceof ImageLibrarySetting) {
               continue;
            }

            if (s.getName().startsWith("Statistik")) {
               continue;
            }

            try {
               this.components.add(ComponentFactory.create(s, this::save));
            } catch (RuntimeException var5) {
            }
         }
      }

      this.detail = it;
   }

   private void closeDetail() {
      this.detail = null;
      this.components.clear();
   }

   private void equip(ShopCatalog.Item it) {
      if (!it.owned()) {
         return;
      }

      if (it.emote() != null) {
         for (Setting<?> s : it.module().getSettings()) {
            if (s instanceof ActionSetting a && s.getName().equals("Emotes auswählen")) {
               this.minecraft.gui.setScreen(this.parent);
               a.run();
               return;
            }
         }

         return;
      }

      if (it.setting() != null) {
         it.setting().set(it.mode());
      }

      if (!it.module().isEnabled()) {
         it.module().setEnabled(true);
      }

      this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.7F));
      this.save();
   }

   private void openSettings(Module m) {
      this.minecraft.gui.setScreen(this.parent);
      this.parent.openSettings(m);
   }

   // ================================================================== Eingabe

   private boolean inPanel(double mx, double my) {
      int[] p = this.panelBox;
      return this.detail != null && p[2] > 0 && Render2D.inside(mx, my, p[0], p[1], p[2], p[3]);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      // erst die Regler in der Leiste
      if (this.inPanel(click.x(), click.y())) {
         for (SettingComponent<?> c : this.components) {
            if (c.getSetting().isVisible() && c.isHovered(click.x(), click.y()) && c.mouseClicked(click.x(), click.y(), click.button())) {
               return true;
            }
         }
      }

      for (int i = this.hits.size() - 1; i >= 0; i--) {
         Hit hit = this.hits.get(i);
         if (Render2D.inside(click.x(), click.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
            Runnable a = click.button() == InputConstants.MOUSE_BUTTON_RIGHT ? hit.right() : hit.left();
            if (a != null) {
               a.run();
               return true;
            }
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      for (SettingComponent<?> c : this.components) {
         if (c.getSetting().isVisible() && c.mouseDragged(click.x(), click.y(), click.button())) {
            return true;
         }
      }

      return super.mouseDragged(click, offsetX, offsetY);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      boolean any = false;

      for (SettingComponent<?> c : this.components) {
         any |= c.mouseReleased(click.x(), click.y(), click.button());
      }

      return any || super.mouseReleased(click);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.inPanel(mouseX, mouseY)) {
         this.panelScroll = Math.max(0.0F, Math.min(this.panelMax, this.panelScroll - (float)verticalAmount * 24.0F));
      } else {
         this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll, this.scrollTarget - (float)verticalAmount * 40.0F));
      }

      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      for (SettingComponent<?> c : this.components) {
         if (c.isCapturingInput() && c.keyPressed(input.key(), input.keycode(), input.modifiers())) {
            return true;
         }
      }

      if (input.key() == InputConstants.KEY_ESCAPE) {
         this.onClose();
         return true;
      }

      return super.keyPressed(input);
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      for (SettingComponent<?> c : this.components) {
         if (c.isCapturingInput() && c.charTyped(event.codepoint())) {
            return true;
         }
      }

      return super.charTyped(event);
   }
}
