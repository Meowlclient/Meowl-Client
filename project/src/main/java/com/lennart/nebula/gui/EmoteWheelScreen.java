package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.emote.EmoteType;
import com.lennart.nebula.module.mods.Emotes;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.update.Version;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Emote-Rad: Taste halten, Maus in Richtung des Emotes bewegen, loslassen (oder klicken). In der Mitte zeigt dein
 * Charakter den gewählten Emote als Vorschau. Mausrad blättert, Rechtsklick beendet den laufenden Emote.
 */
public class EmoteWheelScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int PER_PAGE = 8;
   private static final int ACCENT = -7643914;
   private final Emotes module;
   private final int holdKey;
   private int page;
   private int hovered = -1;
   private float open;
   private long opened = System.currentTimeMillis();

   public EmoteWheelScreen(Emotes module, int holdKey) {
      super(Component.literal("Emotes"));
      this.module = module;
      this.holdKey = holdKey;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   /** Emotes, die nicht im Rad liegen (weitere Seiten). */
   private java.util.List<EmoteType> others() {
      java.util.List<EmoteType> in = java.util.Arrays.asList(this.module.loadout());
      java.util.List<EmoteType> out = new java.util.ArrayList<>();

      for (EmoteType t : EmoteType.listed()) {
         if (!in.contains(t)) {
            out.add(t);
         }
      }

      return out;
   }

   private int pages() {
      return 1 + (this.others().size() + PER_PAGE - 1) / PER_PAGE;
   }

   private int count() {
      return this.page == 0 ? PER_PAGE : Math.min(PER_PAGE, this.others().size() - (this.page - 1) * PER_PAGE);
   }

   /** Seite 0 = deine Rad-Belegung, danach alle übrigen Emotes. */
   private EmoteType at(int slot) {
      if (slot < 0) {
         return null;
      } else if (this.page == 0) {
         EmoteType[] l = this.module.loadout();
         return slot < l.length ? l[slot] : null;
      } else {
         java.util.List<EmoteType> o = this.others();
         int i = (this.page - 1) * PER_PAGE + slot;
         return i < o.size() ? o.get(i) : null;
      }
   }

   private float outer() {
      return Math.max(60.0F, Math.min(128.0F, this.height / 2.0F - 34.0F));
   }

   private int slotAt(double mx, double my) {
      float cx = this.width / 2.0F;
      float cy = this.height / 2.0F;
      double dx = mx - cx;
      double dy = my - cy;
      double d = Math.sqrt(dx * dx + dy * dy);
      if (d < this.outer() * 0.3F) {
         return -1;
      } else {
         int n = this.count();
         double seg = Math.PI * 2.0 / n;
         double ang = Math.atan2(dy, dx) + Math.PI / 2.0 + seg / 2.0;
         ang = (ang % (Math.PI * 2.0) + Math.PI * 2.0) % (Math.PI * 2.0);
         return Math.min(n - 1, (int)(ang / seg));
      }
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      long now = System.currentTimeMillis();
      this.open = Math.min(1.0F, (now - this.opened) / 140.0F);
      float e = 1.0F - (1.0F - this.open) * (1.0F - this.open);
      ctx.fill(0, 0, this.width, this.height, Math.round(110.0F * e) << 24);
      this.hovered = this.slotAt(mouseX, mouseY);
      int cx = this.width / 2;
      int cy = this.height / 2;
      float ro = this.outer() * (0.85F + 0.15F * e);
      float ri = ro * 0.46F;
      int n = this.count();
      EmoteState.Active current = EmoteState.local();
      this.drawRing(ctx, cx, cy, ri, ro, n, current);
      Matrix3x2fStack m = ctx.pose();

      // Trennlinien
      for (int i = 0; i < n; i++) {
         double a = -Math.PI / 2.0 + (i - 0.5) * Math.PI * 2.0 / n;
         m.pushMatrix();
         m.translate(cx, cy);
         m.rotate((float)a);
         m.translate(ri, -1.0F);
         m.scale(ro - ri, 2.0F);
         ctx.fill(0, 0, 1, 1, 0x55000000);
         m.popMatrix();
      }

      // Beschriftung
      for (int i = 0; i < n; i++) {
         EmoteType t = this.at(i);
         double a = -Math.PI / 2.0 + i * Math.PI * 2.0 / n;
         float r = (ri + ro) / 2.0F;
         float lx = cx + (float)Math.cos(a) * r;
         float ly = cy + (float)Math.sin(a) * r;
         if (t == null) {
            ctx.centeredText(this.font, "+", Math.round(lx), Math.round(ly - 4.0F), -10656900);
            continue;
         }

         boolean hot = i == this.hovered;
         boolean running = current != null && current.type == t && current.end < 0.0F;
         m.pushMatrix();
         m.translate(lx, ly - 9.0F);
         m.scale(1.5F, 1.5F);
         ctx.centeredText(this.font, t.icon, 0, -2, hot ? -1 : (running ? -11870592 : -3885571));
         m.popMatrix();
         String label = t.label;
         int lw = this.font.width(label);
         float maxW = (float)(Math.PI * 2.0 * r / n) - 6.0F;
         m.pushMatrix();
         m.translate(lx, ly + 4.0F);
         if (lw > maxW) {
            float s = Math.max(0.6F, maxW / lw);
            m.scale(s, s);
         }

         ctx.centeredText(this.font, label, 0, 0, hot ? -1 : -1578509);
         m.popMatrix();
         if (!t.loops()) {
            continue;
         }

         m.pushMatrix();
         m.translate(lx, ly + 14.0F);
         m.scale(0.6F, 0.6F);
         ctx.centeredText(this.font, running ? "läuft · stoppen" : "Dauer-Emote", 0, 0, running ? -11870592 : -7564628);
         m.popMatrix();
      }

      // Vorschau in der Mitte
      EmoteType show = this.at(this.hovered);
      EmoteState.preview(show != null ? show : (current != null && current.end < 0.0F ? current.type : null));
      if (this.minecraft.player != null) {
         int size = Math.round(ri * 0.55F);
         InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, Math.round(cx - ri), Math.round(cy - ri), Math.round(cx + ri), Math.round(cy + ri * 0.95F), size, 0.0625F, cx, cy - ri * 0.25F, this.minecraft.player);
      }

      // Kopf- und Fußzeile
      String title = show != null ? show.label : "Emote-Rad";
      ctx.centeredText(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD), cx, Math.round(cy - ro - 22.0F), -1);
      String hint = "Maus auf einen Emote · loslassen oder klicken · Rechtsklick stoppt · E = Emotes auswählen";
      ctx.centeredText(this.font, hint, cx, Math.round(cy + ro + 10.0F), -7564628);
      if (this.pages() > 1) {
         int dots = this.pages();
         int dx0 = cx - (dots * 10 - 4) / 2;

         for (int p = 0; p < dots; p++) {
            Render2D.circle(ctx, dx0 + p * 10 + 3, Math.round(cy + ro + 26.0F), 3, p == this.page ? ACCENT : 0x60FFFFFF);
         }

         ctx.centeredText(this.font, this.page == 0 ? "Dein Rad · Mausrad = alle weiteren Emotes" : "Weitere Emotes · Seite " + (this.page + 1) + " von " + this.pages(), cx, Math.round(cy + ro + 34.0F), -10656900);
      }

      String net = this.networkHint();
      if (net != null) {
         ctx.centeredText(this.font, net, cx, this.height - 14, -278748);
      }
   }

   private String networkHint() {
      MeowlService s = MeowlService.get();
      if (!this.module.shares()) {
         return "Teilen ist aus – nur du siehst deine Emotes";
      } else if (!s.isConnected()) {
         return "Nicht mit dem Meowl Server verbunden – andere Meowl-Spieler sehen deine Emotes nicht";
      } else {
         Version have = s.serverVersion() == null ? null : Version.parse(s.serverVersion());
         Version need = Version.parse("1.7.0");
         return have != null && need != null && need.isNewerThan(have)
            ? "Meowl Server " + s.serverVersion() + " ist zu alt – Server-Update einspielen, damit andere deine Emotes sehen"
            : null;
      }
   }

   /** Ring als waagerechte Streifen, Segmentgrenzen exakt berechnet. */
   private void drawRing(GuiGraphicsExtractor ctx, int cx, int cy, float ri, float ro, int n, EmoteState.Active current) {
      double seg = Math.PI * 2.0 / n;
      int r0 = (int)Math.ceil(ro);

      for (int y = -r0; y <= r0; y++) {
         float fy = y + 0.5F;
         if (Math.abs(fy) <= ro) {
            float xo = (float)Math.sqrt(ro * ro - fy * fy);
            float xi = Math.abs(fy) < ri ? (float)Math.sqrt(ri * ri - fy * fy) : -1.0F;
            if (xi < 0.0F) {
               this.span(ctx, cx, cy, y, -xo, xo, seg, n, current);
            } else {
               this.span(ctx, cx, cy, y, -xo, -xi, seg, n, current);
               this.span(ctx, cx, cy, y, xi, xo, seg, n, current);
            }
         }
      }

      Render2D.circle(ctx, cx, cy, Math.round(ri) - 2, 0xCC0C0F18);
   }

   private void span(GuiGraphicsExtractor ctx, int cx, int cy, int y, float x0, float x1, double seg, int n, EmoteState.Active current) {
      float fy = y + 0.5F;
      // Schnittpunkte mit den Segmentgrenzen in dieser Zeile
      float[] cuts = new float[n + 2];
      int c = 0;
      cuts[c++] = x0;

      for (int i = 0; i < n; i++) {
         double a = -Math.PI / 2.0 + (i - 0.5) * seg;
         double sin = Math.sin(a);
         if (Math.abs(sin) > 1.0E-6 && Math.signum(sin) == Math.signum(fy)) {
            float x = (float)(fy * Math.cos(a) / sin);
            if (x > x0 && x < x1) {
               cuts[c++] = x;
            }
         }
      }

      cuts[c++] = x1;
      java.util.Arrays.sort(cuts, 0, c);

      for (int k = 0; k + 1 < c; k++) {
         float a = cuts[k];
         float b = cuts[k + 1];
         if (b - a >= 0.01F) {
            int slot = this.slotAt(cx + (a + b) / 2.0F, cy + fy);
            EmoteType t = this.at(slot);
            int col;
            if (slot == this.hovered && slot >= 0) {
               col = 0xD08B5CF6;
            } else if (current != null && t == current.type && current.end < 0.0F) {
               col = 0xA0287A4A;
            } else {
               col = (slot & 1) == 0 ? 0xC0151A2A : 0xC01C2236;
            }

            Matrix3x2fStack m = ctx.pose();
            m.pushMatrix();
            m.translate(cx + a, cy + y);
            m.scale(b - a, 1.0F);
            ctx.fill(0, 0, 1, 1, col);
            m.popMatrix();
         }
      }
   }

   private void choose(int slot) {
      EmoteType t = this.at(slot);
      this.onClose();
      if (t != null) {
         this.module.play(t);
      }
   }

   @Override
   public boolean keyReleased(KeyEvent input) {
      if (this.holdKey >= 0 && input.key() == this.holdKey) {
         if (System.currentTimeMillis() - this.opened < 180L && this.hovered < 0) {
            // nur kurz getippt: Rad bleibt offen, Auswahl per Klick
            return true;
         } else {
            this.choose(this.hovered);
            return true;
         }
      } else {
         return super.keyReleased(input);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      int k = input.key();
      if (k == InputConstants.KEY_E) {
         EmoteState.preview(null);
         this.module.openPicker();
         return true;
      } else if (k >= InputConstants.KEY_1 && k <= InputConstants.KEY_8) {
         this.choose(k - InputConstants.KEY_1);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
         this.onClose();
         this.module.stopEmote();
         return true;
      } else if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         this.choose(this.slotAt(click.x(), click.y()));
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      if (this.pages() > 1 && v != 0.0) {
         this.page = (this.page + (v < 0.0 ? 1 : this.pages() - 1)) % this.pages();
      }

      return true;
   }

   @Override
   public void onClose() {
      EmoteState.preview(null);
      super.onClose();
   }

   @Override
   public void removed() {
      EmoteState.preview(null);
      super.removed();
   }
}
