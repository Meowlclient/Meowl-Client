package com.lennart.nebula.gui;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.mods.Performance;
import com.lennart.nebula.util.Perf;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Leistungs-Menü: was kostet wie viel Rechenzeit pro Bild (in ms und als Anteil der Bildzeit), dazu die CPU-Last der
 * Hintergrund-Aufgaben. Jeder Eintrag hat einen Schalter; oben „Alles sauber machen“ und die Leistungs-Optionen.
 */
public class PerformanceScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int[] RANGES = {16, 24, 32, 48, 64, 96, 128};
   private final Screen parent;
   private final Performance perf;
   private final List<int[]> rects = new ArrayList<>();
   private final List<Runnable> clicks = new ArrayList<>();
   /** Hier abgeschaltete Einträge bleiben in der Liste, damit man sie wieder anschalten kann. */
   private final Set<Object> touched = new LinkedHashSet<>();
   private float scroll;
   private int listRows;

   public PerformanceScreen(Screen parent, Performance perf) {
      super(Component.literal("Leistung"));
      this.parent = parent;
      this.perf = perf;
   }

   /** 26.x: keine Texteingabe nötig. */
   protected boolean textInput() {
      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   /** Hintergrund-Thread bzw. Hilfsprozess → Modul, das ihn startet (zum Abschalten), sonst null. */
   public static Module ownerOf(String thread) {
      ModuleManager mm = NebulaClient.getInstance().getModuleManager();
      String t = thread.toLowerCase();
      if (t.startsWith("meowl-watch")) {
         return mm.get(com.lennart.nebula.module.hud.Streaming.class);
      } else if (t.startsWith("meowl-facecam")) {
         return mm.get(com.lennart.nebula.module.mods.Facecam.class);
      } else if (t.startsWith("meowl-spotify") || t.contains("powershell")) {
         return mm.get(com.lennart.nebula.module.hud.SpotifyOverlay.class);
      } else if (t.startsWith("meowl-discord")) {
         return mm.get(com.lennart.nebula.module.mods.DiscordPresence.class);
      } else if (t.startsWith("meowl-images")) {
         return mm.get(com.lennart.nebula.module.mods.BlockImages.class);
      } else if (t.startsWith("meowl-chess")) {
         return mm.get(com.lennart.nebula.module.mods.Chess.class);
      } else {
         return t.startsWith("meowl-afk") ? mm.get(com.lennart.nebula.module.mods.DesertAfk.class) : null;
      }
   }

   /** Arbeitet dieses Modul gerade spürbar im Hintergrund? */
   public static boolean backgroundModule(Module m) {
      for (Map.Entry<String, Double> e : Perf.background().entrySet()) {
         if (e.getValue() > 0.3 && ownerOf(e.getKey()) == m) {
            return true;
         }
      }

      return false;
   }

   private record Row(Object key, String name, String kind, double ms, double cpu, boolean on, Runnable toggle) {
   }

   private List<Row> rows() {
      Map<Object, Double> cost = Perf.msPerFrame();
      List<Row> out = new ArrayList<>();
      Set<Object> keys = new LinkedHashSet<>();
      for (Map.Entry<Object, Double> e : Perf.sorted(cost)) {
         if (e.getValue() >= 0.005) {
            keys.add(e.getKey());
         }
      }

      keys.addAll(this.touched);
      // wirklich alle Module, auch die ohne messbare Kosten oder ausgeschaltete
      for (Module m : NebulaClient.getInstance().getModuleManager().getModules()) {
         if (!m.isHidden()) {
            keys.add(m);
            if (m instanceof Cosmetic c) {
               keys.add(c.othersKey());
            }
         }
      }
      for (Object k : keys) {
         double ms = cost.getOrDefault(k, 0.0);
         if (k instanceof Cosmetic.Others o) {
            Cosmetic c = o.cosmetic();
            out.add(new Row(k, c.getName(), "Kosmetik anderer", ms, 0.0, c.showsOthers(), () -> c.setShowOthers(!c.showsOthers())));
         } else if (k instanceof Module m) {
            String kind = m instanceof Cosmetic ? "Eigene Kosmetik" : "Modul";
            Runnable toggle = Performance.switchable(m) ? () -> m.setEnabled(!m.isEnabled()) : null;
            out.add(new Row(k, m.getName(), kind, ms, 0.0, m.isEnabled(), toggle));
         }
      }

      // Hintergrund: Threads/Prozesse zusammengefasst nach Modul
      Map<String, Double> bg = Perf.background();
      for (Map.Entry<String, Double> e : Perf.sorted(bg)) {
         if (e.getValue() < 0.1) {
            continue;
         }

         Module owner = ownerOf(e.getKey());
         String label = e.getKey().startsWith("Prozess: ") ? e.getKey() : e.getKey().replace("Meowl-", "");
         Runnable toggle = Performance.switchable(owner) ? () -> owner.setEnabled(!owner.isEnabled()) : null;
         out.add(new Row("bg:" + e.getKey(), owner == null ? label : owner.getName() + " · " + label, "Hintergrund", 0.0, e.getValue(), owner == null || owner.isEnabled(), toggle));
      }

      out.sort((a, b) -> Double.compare(b.ms + b.cpu * 0.02, a.ms + a.cpu * 0.02));
      return out;
   }

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, String text, boolean main, int mx, int my, Runnable action) {
      boolean hov = Render2D.inside(mx, my, x, y, w, 16);
      int bg = main ? (hov ? 0xFF7A52C8 : 0xFF5E3FA6) : (hov ? 0xE0303A58 : 0xC01C2234);
      Render2D.roundRect(ctx, x, y, w, 16, 4, bg);
      ctx.centeredText(this.font, this.font.plainSubstrByWidth(text, w - 6), x + w / 2, y + 4, 0xFFFFFFFF);
      this.rects.add(new int[]{x, y, w, 16});
      this.clicks.add(action);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.rects.clear();
      this.clicks.clear();
      ctx.fill(0, 0, this.width, this.height, 0xB005060C);
      int pw = Math.min(420, this.width - 20);
      int ph = Math.min(300, this.height - 20);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.roundRect(ctx, px, py, pw, ph, 8, 0xF0101420);
      ctx.text(this.font, Component.literal("Leistung").withStyle(ChatFormatting.BOLD), px + 10, py + 8, -1, true);
      List<Row> rows = this.rows();
      double frame = Perf.frameMs();
      double client = 0.0;
      for (Row r : rows) {
         client += r.ms;
      }

      boolean inWorld = this.minecraft.level != null;
      String head = !inWorld
         ? "Messung läuft nur in einer Welt"
         : Perf.fps() + " FPS · " + String.format("%.1f", frame) + " ms · Meowl kostet −" + String.format("%.0f", Perf.fpsCost(client)) + " FPS ("
            + Math.round(frame > 0.0 ? client / frame * 100.0 : 0.0) + " %)";
      ctx.text(this.font, head, px + 70, py + 8, Theme.TEXT_DIM, false);
      // Knöpfe und Optionen
      int by = py + 24;
      int bw = (pw - 30) / 2;
      int tw = (pw - 40) / 3;
      this.button(ctx, px + 10, by, tw, "Alles sauber machen", true, mouseX, mouseY, () -> this.perf.clean());
      if (this.perf.hasCleaned()) {
         this.button(ctx, px + 20 + tw, by, tw, "Rückgängig", false, mouseX, mouseY, this.perf::restore);
      }

      var overlay = this.perf.overlaySetting();
      this.button(ctx, px + 30 + tw * 2, by, tw, "Overlay im Spiel: " + (overlay.get() ? "an" : "aus"), false, mouseX, mouseY, () -> overlay.set(!overlay.get()));

      by += 20;
      int ow = (pw - 40) / 3;
      var bgFps = this.perf.backgroundFpsSetting();
      this.button(ctx, px + 10, by, ow, "Hintergrund: " + (bgFps.is("Aus") ? "aus" : bgFps.get() + " FPS"), false, mouseX, mouseY, () -> {
         String[] opts = {"Aus", "10", "30", "60"};
         int i = java.util.Arrays.asList(opts).indexOf(bgFps.get());
         bgFps.set(opts[(i + 1) % opts.length]);
      });
      var range = this.perf.cosmeticRangeSetting();
      this.button(ctx, px + 20 + ow, by, ow, "Kosmetik bis " + range.getInt() + " Blöcke", false, mouseX, mouseY, () -> {
         int cur = range.getInt();
         int next = RANGES[0];
         for (int r : RANGES) {
            if (r > cur) {
               next = r;
               break;
            }
         }

         range.set((double)next);
      });
      var parts = this.perf.cosmeticParticlesSetting();
      this.button(ctx, px + 30 + ow * 2, by, ow, "Kosmetik-Partikel: " + (parts.get() ? "an" : "aus"), false, mouseX, mouseY, () -> parts.set(!parts.get()));
      // Liste
      int top = by + 24;
      int bottom = py + ph - 18;
      int rowH = 18;
      this.listRows = rows.size();
      this.scroll = Math.max(0.0F, Math.min(Math.max(0, rows.size() * rowH - (bottom - top)), this.scroll));
      ctx.enableScissor(px, top, px + pw, bottom);
      if (rows.isEmpty()) {
         ctx.text(this.font, inWorld ? "Nichts vom Meowl Client kostet gerade messbar Leistung." : "Betritt eine Welt, um zu messen.", px + 12, top + 4, Theme.TEXT_DIM, false);
      }

      for (int i = 0; i < rows.size(); i++) {
         Row r = rows.get(i);
         int y = top + i * rowH - Math.round(this.scroll);
         if (y + rowH < top || y > bottom) {
            continue;
         }

         int x0 = px + 10;
         int w0 = pw - 20;
         Render2D.roundRect(ctx, x0, y, w0, rowH - 2, 4, 0xC01C2234);
         // Balken: Anteil an der Bildzeit (bzw. CPU-Last eines Kerns im Hintergrund)
         double share = r.cpu > 0.0 ? r.cpu / 100.0 : (frame > 0.0 ? r.ms / frame : 0.0);
         int barW = (int)Math.round(Math.min(1.0, share * 4.0) * (w0 - 4));
         int barCol = share > 0.08 ? 0x70FF5F5F : (share > 0.02 ? 0x70FFC857 : 0x705BE38A);
         ctx.fill(x0 + 2, y + rowH - 5, x0 + 2 + barW, y + rowH - 3, barCol);
         int textCol = r.on ? 0xFFFFFFFF : 0xFF8A8AA0;
         ctx.text(this.font, this.font.plainSubstrByWidth(r.name, w0 - 230), x0 + 6, y + 4, textCol, false);
         ctx.text(this.font, r.kind, x0 + w0 - 222, y + 4, Theme.TEXT_MUTED, false);
         String val = r.cpu > 0.0
            ? String.format("%.1f %% CPU", r.cpu)
            : (!r.on && r.ms <= 0.0 ? "aus" : String.format("−%.1f FPS · %.2f ms", Perf.fpsCost(r.ms), r.ms));
         ctx.text(this.font, val, x0 + w0 - 30 - this.font.width(val), y + 4, share > 0.08 ? 0xFFFF8080 : Theme.TEXT_DIM, false);
         if (r.toggle != null) {
            int sx = x0 + w0 - 24;
            Render2D.roundRect(ctx, sx, y + 3, 20, 10, 5, r.on ? Theme.ACCENT : Theme.SWITCH_OFF);
            ctx.fill(r.on ? sx + 11 : sx + 2, y + 4, r.on ? sx + 19 : sx + 10, y + 12, Theme.KNOB);
            if (mouseY >= top && mouseY < bottom) {
               Object key = r.key;
               Runnable t = r.toggle;
               this.rects.add(new int[]{x0, y, w0, rowH - 2});
               this.clicks.add(() -> {
                  this.touched.add(key);
                  t.run();
                  NebulaClient.getInstance().getModuleManager().notifyChanged();
               });
            }
         }
      }

      ctx.disableScissor();
      ctx.text(this.font, "Gemessen wird die Rechenzeit (CPU) pro Bild – Grafikkarten-Last zählt nicht mit.", px + 10, py + ph - 13, Theme.TEXT_MUTED, false);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         for (int i = 0; i < this.rects.size(); i++) {
            int[] r = this.rects.get(i);
            if (Render2D.inside(click.x(), click.y(), r[0], r[1], r[2], r[3])) {
               this.clicks.get(i).run();
               return true;
            }
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      this.scroll -= (float)v * 18.0F;
      return true;
   }
}
