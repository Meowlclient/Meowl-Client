package com.lennart.nebula.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lennart.nebula.net.MeowlService;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Kosmetik-Abstimmung über dem Kosmetik-Planeten: alle Kosmetiks nacheinander mit großer Vorschau, ✗ = kann raus,
 * ✓ = soll bleiben (auch per Pfeiltasten), dazu ein Feld für Wünsche nach neuer Kosmetik. Stimmen gehen an den Meowl
 * Server (dort gezählt) und werden lokal gemerkt; am Ende eine Übersicht mit dem Stand aller Stimmen und Wünschen.
 */
public final class CosmeticVote {
   private static final String STATE = "cosmetic_votes";
   private static final int GREEN = 0xFF4ED18A;
   private static final int RED = 0xFFFF5F6D;
   /** Zeichnet die Vorschau eines Artikels in ein Rechteck. */
   public interface Previewer {
      void draw(GuiGraphics ctx, ShopCatalog.Item item, int x, int y, int w, int h, float t);
   }

   private record Entry(String key, String category, ShopCatalog.Item item) {
   }

   private record Button(int x, int y, int w, int h, Runnable action) {
   }

   private final Minecraft mc = Minecraft.getInstance();
   private final List<Entry> entries = new ArrayList<>();
   private final Map<String, Boolean> mine = new HashMap<>();
   private final List<Button> buttons = new ArrayList<>();
   private boolean open;
   private boolean done;
   private int index;
   private long switchedAt = System.currentTimeMillis();
   private int lastDir;
   private String wish = "";
   private boolean typing;
   private String note = "";
   private long noteAt;
   /** Zählung vom Server: Kosmetik → {bleibt, raus}. */
   private final Map<String, int[]> tally = new HashMap<>();
   private final List<String> wishes = new ArrayList<>();
   private int voters;

   public CosmeticVote(List<ShopCatalog.Category> categories) {
      for (ShopCatalog.Category c : categories) {
         for (ShopCatalog.Item it : c.items()) {
            String key = c.name() + ": " + it.name();
            this.entries.add(new Entry(key.length() > 80 ? key.substring(0, 80) : key, c.name(), it));
         }
      }

      JsonObject st = ToolState.get(STATE);
      this.done = ToolState.getBool(st, "done", false);
      if (st.has("votes") && st.get("votes").isJsonObject()) {
         for (Map.Entry<String, JsonElement> e : st.getAsJsonObject("votes").entrySet()) {
            this.mine.put(e.getKey(), e.getValue().getAsBoolean());
         }
      }

      this.index = this.firstOpen();
      // erst beim Betreten des Kosmetik-Planeten fragen, solange noch nicht alles abgestimmt ist
      this.open = !this.done && this.index < this.entries.size();
      this.fetch();
   }

   private int firstOpen() {
      for (int i = 0; i < this.entries.size(); i++) {
         if (!this.mine.containsKey(this.entries.get(i).key())) {
            return i;
         }
      }

      return this.entries.size();
   }

   private void save() {
      JsonObject st = new JsonObject();
      st.addProperty("done", this.done);
      JsonObject v = new JsonObject();
      this.mine.forEach(v::addProperty);
      st.add("votes", v);
      ToolState.put(STATE, st);
   }

   public boolean isOpen() {
      return this.open;
   }

   public void show() {
      this.open = true;
      this.index = Math.min(this.firstOpen(), this.entries.size());
      this.switchedAt = System.currentTimeMillis();
      this.fetch();
   }

   /** Stand vom Server holen; eigene Stimmen, die der Server noch nicht kennt, nachschicken. */
   private void fetch() {
      MeowlService s = MeowlService.get();
      if (!s.isConnected()) {
         return;
      }

      s.get("/votes").thenAccept(r -> {
         if (r == null || !r.ok() || r.json() == null) {
            return;
         }

         JsonObject j = r.json();
         this.mc.execute(() -> {
            this.tally.clear();
            if (j.has("tally")) {
               for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("tally").entrySet()) {
                  JsonArray a = e.getValue().getAsJsonArray();
                  this.tally.put(e.getKey(), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
               }
            }

            this.wishes.clear();
            if (j.has("wishes")) {
               for (JsonElement e : j.getAsJsonArray("wishes")) {
                  JsonObject w = e.getAsJsonObject();
                  this.wishes.add(w.get("name").getAsString() + ": " + w.get("text").getAsString());
               }
            }

            this.voters = j.has("voters") ? j.get("voters").getAsInt() : 0;
            JsonObject server = j.has("mine") ? j.getAsJsonObject("mine") : new JsonObject();
            this.mine.forEach((k, keep) -> {
               if (!server.has(k) || server.get(k).getAsBoolean() != keep) {
                  this.send(k, keep);
               }
            });
         });
      });
   }

   private void send(String key, boolean keep) {
      JsonObject o = new JsonObject();
      o.addProperty("item", key);
      o.addProperty("keep", keep);
      MeowlService.get().post("/votes", o);
   }

   private void vote(boolean keep) {
      if (this.index >= this.entries.size()) {
         return;
      }

      Entry e = this.entries.get(this.index);
      Boolean before = this.mine.put(e.key(), keep);
      int[] t = this.tally.computeIfAbsent(e.key(), k -> new int[2]);
      if (before != null) {
         t[before ? 0 : 1] = Math.max(0, t[before ? 0 : 1] - 1);
      }

      t[keep ? 0 : 1]++;
      if (MeowlService.get().isConnected()) {
         this.send(e.key(), keep);
      }

      this.save();
      this.lastDir = keep ? 1 : -1;
      this.index++;
      this.switchedAt = System.currentTimeMillis();
   }

   private void back() {
      if (this.index > 0) {
         this.index--;
         this.lastDir = 0;
         this.switchedAt = System.currentTimeMillis();
      }
   }

   private void sendWish() {
      String text = this.wish.trim();
      if (text.length() < 2) {
         this.say("§cSchreib erst, was du dir wünschst");
         return;
      }

      MeowlService s = MeowlService.get();
      if (!s.isConnected()) {
         this.say("§cNicht mit dem Meowl Server verbunden");
         return;
      }

      JsonObject o = new JsonObject();
      o.addProperty("text", text);
      s.post("/votes/wish", o).whenComplete((r, err) -> this.mc.execute(() -> {
         if (err == null && r != null && r.ok()) {
            this.wishes.add(0, this.mc.getUser().getName() + ": " + text);
            this.wish = "";
            this.typing = false;
            this.say("§aDanke für deinen Wunsch!");
         } else {
            this.say("§c" + (r != null ? r.error() : "Senden ging nicht"));
         }
      }));
   }

   private void say(String text) {
      this.note = text;
      this.noteAt = System.currentTimeMillis();
   }

   private void finish() {
      this.done = true;
      this.open = false;
      this.save();
   }

   // ================================================================== Zeichnen

   public void render(GuiGraphics ctx, Font font, int sw, int sh, int mx, int my, float t, Previewer previewer) {
      this.buttons.clear();
      ctx.fill(0, 0, sw, sh, 0xC0060410);
      int w = Math.min(340, sw - 30);
      int h = Math.min(300, sh - 20);
      int x = (sw - w) / 2;
      int y = (sh - h) / 2;
      Render2D.glow(ctx, x, y, w, h, 12, 0xB98CFF, 0.25F);
      Render2D.roundRect(ctx, x, y, w, h, 12, 0xF0120C26);
      ctx.drawString(font, Component.literal("🗳 Kosmetik-Abstimmung").withStyle(ChatFormatting.BOLD), x + 12, y + 10, 0xFFF3E9FF, false);
      this.button(ctx, font, x + w - 64, y + 7, 54, 14, "Später ✕", false, mx, my, () -> this.open = false);
      if (this.index >= this.entries.size()) {
         this.summary(ctx, font, x, y, w, h, mx, my);
      } else {
         this.card(ctx, font, x, y, w, h, mx, my, t, previewer);
      }

      this.wishField(ctx, font, x, y + h - 30, w, mx, my, t);
      if (!this.note.isEmpty() && System.currentTimeMillis() - this.noteAt < 3000L) {
         ctx.drawCenteredString(font, this.note, sw / 2, y + h + 6, 0xFFFFFFFF);
      }
   }

   private void card(GuiGraphics ctx, Font font, int x, int y, int w, int h, int mx, int my, float t, Previewer previewer) {
      Entry e = this.entries.get(this.index);
      int n = this.entries.size();
      ctx.drawString(font, "Soll das im Meowl Client bleiben?", x + 12, y + 22, 0xFF9A8CC0, false);
      // Fortschritt
      String prog = (this.index + 1) + " / " + n;
      ctx.drawString(font, prog, x + w - 12 - font.width(prog), y + 24, 0xFF9A8CC0, false);
      Render2D.roundRect(ctx, x + 12, y + 36, w - 24, 3, 1, 0x40FFFFFF);
      Render2D.roundRect(ctx, x + 12, y + 36, Math.max(3, Math.round((w - 24) * (float)this.index / n)), 3, 1, 0xFFB98CFF);
      // Karte fliegt beim Abstimmen kurz zur Seite, die nächste gleitet herein
      float in = Math.min(1.0F, (System.currentTimeMillis() - this.switchedAt) / 220.0F);
      in = 1.0F - (1.0F - in) * (1.0F - in);
      int slide = Math.round((1.0F - in) * 40.0F * (this.lastDir == 0 ? -1 : this.lastDir));
      int py = y + 46;
      int ph = h - 46 - 74;
      int pw = Math.min(w - 120, ph + 40);
      int px = x + (w - pw) / 2 + slide;
      Render2D.roundRect(ctx, px, py, pw, ph, 10, 0xC01C1438);
      ctx.enableScissor(px, py, px + pw, py + ph);
      previewer.draw(ctx, e.item(), px, py, pw, ph, t);
      ctx.disableScissor();
      ctx.drawCenteredString(font, Component.literal(e.item().name()).withStyle(ChatFormatting.BOLD), x + w / 2 + slide, py + ph + 4, 0xFFFFFFFF);
      ctx.drawCenteredString(font, e.category(), x + w / 2 + slide, py + ph + 15, 0xFF9A8CC0);
      Boolean my0 = this.mine.get(e.key());
      int[] tl = this.tally.get(e.key());
      if (tl != null && (tl[0] + tl[1]) > 0) {
         String s = "§a✓ " + tl[0] + "  §c✗ " + tl[1];
         ctx.drawCenteredString(font, s, x + w / 2, py + 6, 0xFFFFFFFF);
      }

      // ✗ und ✓ links und rechts der Vorschau
      int r = 22;
      int cy = py + ph / 2;
      this.round(ctx, font, x + (px - slide - x) / 2, cy, r, "✗", RED, my0 != null && !my0, mx, my, () -> this.vote(false));
      this.round(ctx, font, x + w - (px - slide - x) / 2, cy, r, "✓", GREEN, my0 != null && my0, mx, my, () -> this.vote(true));
      if (this.index > 0) {
         this.button(ctx, font, x + 12, py + ph + 4, 44, 14, "‹ Zurück", false, mx, my, this::back);
      }

      ctx.drawString(font, "← / → zum Abstimmen", x + w - 12 - font.width("← / → zum Abstimmen"), py + ph + 8, 0xFF6E6688, false);
   }

   private void summary(GuiGraphics ctx, Font font, int x, int y, int w, int h, int mx, int my) {
      ctx.drawString(font, "Danke fürs Abstimmen! " + (this.voters > 0 ? "§7(" + this.voters + " Spieler haben abgestimmt)" : ""), x + 12, y + 24, 0xFFB8F5D0, false);
      // Stand: am beliebtesten / am ehesten raus
      List<Map.Entry<String, int[]>> list = new ArrayList<>(this.tally.entrySet());
      list.sort((a, b) -> Double.compare(score(b.getValue()), score(a.getValue())));
      int col = (w - 36) / 2;
      ctx.drawString(font, "§a✓ Bleibt sicher", x + 12, y + 40, 0xFFFFFFFF, false);
      ctx.drawString(font, "§c✗ Wackelt", x + 24 + col, y + 40, 0xFFFFFFFF, false);
      for (int i = 0; i < Math.min(6, list.size()); i++) {
         Map.Entry<String, int[]> top = list.get(i);
         Map.Entry<String, int[]> low = list.get(list.size() - 1 - i);
         ctx.drawString(font, font.plainSubstrByWidth(top.getKey(), col - 34) + " §7" + top.getValue()[0] + "/" + (top.getValue()[0] + top.getValue()[1]), x + 12, y + 54 + i * 11, 0xFFE0E0E8, false);
         if (list.size() > 6) {
            ctx.drawString(font, font.plainSubstrByWidth(low.getKey(), col - 34) + " §7" + low.getValue()[1] + "/" + (low.getValue()[0] + low.getValue()[1]), x + 24 + col, y + 54 + i * 11, 0xFFE0E0E8, false);
         }
      }

      int wy = y + 126;
      ctx.drawString(font, "Neueste Wünsche", x + 12, wy, 0xFFB98CFF, false);
      for (int i = 0; i < Math.min(5, this.wishes.size()); i++) {
         ctx.drawString(font, font.plainSubstrByWidth("• " + this.wishes.get(i), w - 24), x + 12, wy + 12 + i * 10, 0xFFB8B0D0, false);
      }

      this.button(ctx, font, x + w - 112, y + h - 54, 100, 16, "Fertig", true, mx, my, this::finish);
      this.button(ctx, font, x + 12, y + h - 54, 100, 16, "Nochmal ansehen", false, mx, my, () -> {
         this.index = 0;
         this.lastDir = 0;
         this.switchedAt = System.currentTimeMillis();
      });
   }

   private static double score(int[] c) {
      int n = c[0] + c[1];
      return n == 0 ? 0.5 : (c[0] + 1.0) / (n + 2.0);
   }

   private void wishField(GuiGraphics ctx, Font font, int x, int y, int w, int mx, int my, float t) {
      int fx = x + 12;
      int fw = w - 24 - 70;
      boolean hov = Render2D.inside(mx, my, fx, y, fw, 18);
      Render2D.roundRect(ctx, fx, y, fw, 18, 6, this.typing ? 0xFF2A1F4A : (hov ? 0xE0221838 : 0xC01A1430));
      if (this.typing) {
         Render2D.roundRect(ctx, fx, y + 17, fw, 1, 0, 0xFFB98CFF);
      }

      String shown = this.wish.isEmpty() && !this.typing ? "✎ Neue Kosmetik wünschen …" : this.wish;
      String clipped = shown;
      while (font.width(clipped) > fw - 12 && clipped.length() > 1) {
         clipped = clipped.substring(1);
      }

      boolean cursor = this.typing && (System.currentTimeMillis() / 500L) % 2L == 0L;
      ctx.drawString(font, clipped + (cursor ? "_" : ""), fx + 6, y + 5, this.wish.isEmpty() && !this.typing ? 0xFF7E739E : 0xFFFFFFFF, false);
      this.buttons.add(new Button(fx, y, fw, 18, () -> this.typing = true));
      this.button(ctx, font, x + w - 12 - 64, y + 1, 64, 16, "Wünschen", true, mx, my, this::sendWish);
   }

   private void button(GuiGraphics ctx, Font font, int x, int y, int w, int h, String text, boolean main, int mx, int my, Runnable action) {
      boolean hov = Render2D.inside(mx, my, x, y, w, h);
      int bg = main ? (hov ? 0xFF8B5CF6 : 0xFF6A45C8) : (hov ? 0xE03A2F5A : 0xC02A1F4A);
      Render2D.roundRect(ctx, x, y, w, h, h / 2, bg);
      ctx.drawCenteredString(font, font.plainSubstrByWidth(text, w - 4), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
      this.buttons.add(new Button(x, y, w, h, action));
   }

   private void round(GuiGraphics ctx, Font font, int cx, int cy, int r, String sym, int color, boolean chosen, int mx, int my, Runnable action) {
      boolean hov = (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= r * r;
      if (hov || chosen) {
         Render2D.glow(ctx, cx - r, cy - r, r * 2, r * 2, r, color & 0xFFFFFF, 0.45F);
      }

      Render2D.circle(ctx, cx, cy, r, chosen ? color : (hov ? Render2D.alpha(color, 0.85F) : Render2D.alpha(color, 0.55F)));
      Render2D.circle(ctx, cx, cy, r - 3, chosen || hov ? color : 0xE0181230);
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      ctx.pose().scale(2.2F, 2.2F);
      ctx.drawCenteredString(font, sym, 0, -4, 0xFFFFFFFF);
      ctx.pose().popMatrix();
      this.buttons.add(new Button(cx - r, cy - r, r * 2, r * 2, action));
   }

   // ================================================================== Eingabe (true = verbraucht)

   public boolean mouseClicked(double mx, double my, int button) {
      if (button != InputConstants.MOUSE_BUTTON_LEFT) {
         return true;
      }

      boolean hitField = false;
      for (int i = this.buttons.size() - 1; i >= 0; i--) {
         Button b = this.buttons.get(i);
         if (Render2D.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
            b.action().run();
            hitField = true;
            break;
         }
      }

      if (!hitField) {
         this.typing = false;
      }

      return true;
   }

   public boolean keyPressed(int key) {
      if (this.typing) {
         if (key == InputConstants.KEY_BACKSPACE && !this.wish.isEmpty()) {
            this.wish = this.wish.substring(0, this.wish.length() - 1);
         } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            this.sendWish();
         } else if (key == InputConstants.KEY_ESCAPE) {
            this.typing = false;
         }

         return true;
      }

      if (key == InputConstants.KEY_ESCAPE) {
         this.open = false;
      } else if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_A) {
         this.vote(false);
      } else if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_D) {
         this.vote(true);
      } else if (key == InputConstants.KEY_BACKSPACE) {
         this.back();
      }

      return true;
   }

   public boolean charTyped(int codepoint) {
      if (this.typing && codepoint >= 32 && this.wish.length() < 200) {
         this.wish = this.wish + new String(Character.toChars(codepoint));
      }

      return true;
   }
}
