package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.mods.BlockImages;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.util.DynamicTextures;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Meowl Paint: Pixel-Bilder malen, speichern und als Block Image für alle Meowl-Spieler teilen. */
public class PaintScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ACCENT = Section.GAMES.color();
   private static final Identifier TEX = Identifier.fromNamespaceAndPath("nebula", "paint/canvas");
   private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("meowl-paint");
   private static final int[] PALETTE = new int[]{
      0xFF000000, 0xFF3F3F3F, 0xFF7F7F7F, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF6B3F1F, 0xFFA0662C, 0xFFE0B080,
      0xFF8B0000, 0xFFE02020, 0xFFFF7070, 0xFFFF8C00, 0xFFFFC020, 0xFFFFF070, 0xFF2F6B1F, 0xFF40C040,
      0xFFA0F080, 0xFF006B6B, 0xFF20C0C0, 0xFF80F0F0, 0xFF1F2F8B, 0xFF3070F0, 0xFF90B0FF, 0xFF4B1F8B,
      0xFF9040E0, 0xFFC890FF, 0xFF8B1F5B, 0xFFE040A0, 0xFFFF9AD0, 0xFFB06AFF, 0xFF4FC3F7, 0xFF5BE38A
   };
   private final Screen parent;
   private int size = 64;
   private int[] pixels;
   private final Deque<int[]> undo = new ArrayDeque<>();
   private final Deque<int[]> redo = new ArrayDeque<>();
   private PaintScreen.Tool tool = PaintScreen.Tool.BRUSH;
   private int color = 0xFF000000;
   private int baseColor = 0xFFE02020;
   private int brush = 1;
   private boolean grid = true;
   private boolean dirty = true;
   private DynamicTexture texture;
   private int lastPx = -1;
   private int lastPy = -1;
   private int lineStart = -1;
   private boolean painting;
   private String status = "Malen mit Linksklick · Rechtsklick = Pipette";
   private boolean busy;
   private boolean showFiles;
   private List<Path> files = List.of();
   private final List<PaintScreen.Btn> buttons = new ArrayList<>();
   private int cx;
   private int cy;
   private int cs;

   public PaintScreen(Screen parent) {
      super(Component.literal("Meowl Paint"));
      this.parent = parent;
      this.newCanvas(64);
   }

   private enum Tool {
      BRUSH("✎ Pinsel"),
      ERASER("⌫ Radierer"),
      FILL("▣ Füllen"),
      LINE("╱ Linie"),
      PICKER("◉ Pipette");

      final String label;

      Tool(String label) {
         this.label = label;
      }
   }

   private void newCanvas(int size) {
      this.size = size;
      this.pixels = new int[size * size];
      Arrays.fill(this.pixels, 0xFFFFFFFF);
      this.undo.clear();
      this.redo.clear();
      this.dirty = true;
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
   public void removed() {
      if (this.texture != null) {
         DynamicTextures.destroy(TEX);
         this.texture = null;
      }
   }

   // ------------------------------------------------------------ Zeichnen

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(0, 0, this.width, this.height, Theme.OVERLAY);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      this.renderBackground(ctx, mx, my, delta);
      this.buttons.clear();
      int pw = Math.min(this.width - 12, 440);
      int ph = Math.min(this.height - 12, 290);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.glow(ctx, px, py, pw, ph, 8, ACCENT, 0.4F);
      Render2D.roundRect(ctx, px, py, pw, ph, 8, Theme.PANEL_BG);
      Render2D.roundRect(ctx, px, py, pw, 24, 8, Theme.HEADER);
      ctx.fill(px, py + 16, px + pw, py + 24, Theme.HEADER);
      ctx.fill(px, py + 24, px + pw, py + 25, Render2D.alpha(ACCENT, 0.7F));
      ctx.text(this.font, Component.literal("Meowl Paint").withStyle(ChatFormatting.BOLD), px + 10, py + 8, Theme.TEXT, false);
      ctx.text(this.font, this.size + "×" + this.size, px + 90, py + 8, Theme.TEXT_MUTED, false);
      this.button(ctx, px + pw - 20, py + 5, 14, 14, "×", mx, my, this::onClose, false);
      // links: Werkzeuge
      int tx = px + 8;
      int ty = py + 30;
      int tw = 66;

      for (PaintScreen.Tool t : PaintScreen.Tool.values()) {
         this.button(ctx, tx, ty, tw, 15, t.label, mx, my, () -> {
            this.tool = t;
            this.lineStart = -1;
         }, this.tool == t);
         ty += 17;
      }

      ty += 4;
      ctx.text(this.font, "Größe " + this.brush, tx, ty + 3, Theme.TEXT_DIM, false);
      this.button(ctx, tx + 44, ty, 10, 13, "-", mx, my, () -> this.brush = Math.max(1, this.brush - 1), false);
      this.button(ctx, tx + 56, ty, 10, 13, "+", mx, my, () -> this.brush = Math.min(8, this.brush + 1), false);
      ty += 18;
      this.button(ctx, tx, ty, 32, 14, "↶", mx, my, this::undo, false);
      this.button(ctx, tx + 34, ty, 32, 14, "↷", mx, my, this::redo, false);
      ty += 17;
      this.button(ctx, tx, ty, tw, 14, this.grid ? "Raster: an" : "Raster: aus", mx, my, () -> this.grid = !this.grid, false);
      ty += 17;
      this.button(ctx, tx, ty, tw, 14, "Leeren", mx, my, () -> {
         this.pushUndo();
         Arrays.fill(this.pixels, 0xFFFFFFFF);
         this.dirty = true;
      }, false);
      // Mitte: Leinwand
      int areaX = tx + tw + 8;
      int areaW = pw - (areaX - px) - 92;
      int areaH = ph - 30 - 26;
      this.cs = Math.max(1, Math.min(areaW, areaH) / this.size);
      int canvas = this.cs * this.size;
      this.cx = areaX + (areaW - canvas) / 2;
      this.cy = py + 30 + (areaH - canvas) / 2;
      this.renderCanvas(ctx, mx, my);
      // rechts: Farben
      int rx = px + pw - 86;
      int ry = py + 30;
      Render2D.roundRect(ctx, rx, ry, 78, 20, 4, Theme.CARD);
      this.checker(ctx, rx + 4, ry + 4, 12, 12, 4);
      ctx.fill(rx + 4, ry + 4, rx + 16, ry + 16, this.color);
      ctx.text(this.font, String.format("#%06X", this.color & 16777215), rx + 21, ry + 6, Theme.TEXT_DIM, false);
      ry += 24;
      int sw = 9;

      for (int i = 0; i < PALETTE.length; i++) {
         int x = rx + i % 8 * (sw + 1);
         int y = ry + i / 8 * (sw + 1);
         int c = PALETTE[i];
         boolean sel = c == this.color;
         if (sel) {
            ctx.fill(x - 1, y - 1, x + sw + 1, y + sw + 1, -1);
         }

         ctx.fill(x, y, x + sw, y + sw, c);
         this.buttons.add(new PaintScreen.Btn(x, y, sw, sw, () -> {
            this.color = c;
            this.baseColor = c;
            if (this.tool == PaintScreen.Tool.ERASER || this.tool == PaintScreen.Tool.PICKER) {
               this.tool = PaintScreen.Tool.BRUSH;
            }
         }));
      }

      ry += 4 * (sw + 1) + 4;
      // Helligkeit des aktuellen Farbtons
      ctx.text(this.font, "Ton", rx, ry + 1, Theme.TEXT_MUTED, false);
      int hx = rx + 18;
      int hw = 60;

      for (int i = 0; i < hw; i++) {
         ctx.fill(hx + i, ry, hx + i + 1, ry + 9, 0xFF000000 | java.awt.Color.HSBtoRGB(i / (float)hw, 0.85F, 0.95F));
      }

      this.buttons.add(new PaintScreen.Btn(hx, ry, hw, 9, null));
      ry += 12;
      ctx.text(this.font, "Hell", rx, ry + 1, Theme.TEXT_MUTED, false);

      for (int i = 0; i < hw; i++) {
         ctx.fill(hx + i, ry, hx + i + 1, ry + 9, shade(this.baseColor, i / (float)(hw - 1)));
      }

      this.buttons.add(new PaintScreen.Btn(hx, ry, hw, 9, null));
      ry += 16;
      // Datei
      this.button(ctx, rx, ry, 78, 14, "Speichern", mx, my, this::save, false);
      ry += 16;
      this.button(ctx, rx, ry, 78, 14, this.showFiles ? "Laden schließen" : "Laden …", mx, my, () -> {
         this.showFiles = !this.showFiles;
         this.files = listFiles();
      }, false);
      ry += 16;
      this.button(ctx, rx, ry, 25, 14, "32", mx, my, () -> this.resize(32), this.size == 32);
      this.button(ctx, rx + 26, ry, 26, 14, "64", mx, my, () -> this.resize(64), this.size == 64);
      this.button(ctx, rx + 53, ry, 25, 14, "128", mx, my, () -> this.resize(128), this.size == 128);
      ry += 18;
      this.bigButton(ctx, rx, ry, 78, 20, this.busy ? "Lädt hoch …" : "Als Block Image", mx, my, this::share);
      if (this.showFiles) {
         this.renderFiles(ctx, mx, my);
      }

      // Statuszeile
      String s = this.font.plainSubstrByWidth(this.status, pw - 20);
      ctx.text(this.font, s, px + 10, py + ph - 16, Theme.TEXT_DIM, false);
   }

   private void renderCanvas(GuiGraphicsExtractor ctx, int mx, int my) {
      int canvas = this.cs * this.size;
      Render2D.roundRect(ctx, this.cx - 3, this.cy - 3, canvas + 6, canvas + 6, 3, Theme.CARD);
      this.checker(ctx, this.cx, this.cy, canvas, canvas, Math.max(4, this.cs * 4));
      if (this.dirty) {
         this.texture = DynamicTextures.upload(TEX, this.texture, this.pixels, this.size, this.size);
         this.dirty = false;
      }

      ctx.blit(RenderPipelines.GUI_TEXTURED, TEX, this.cx, this.cy, 0.0F, 0.0F, canvas, canvas, this.size, this.size, this.size, this.size);
      if (this.grid && this.cs >= 4) {
         int gc = 0x18000000;

         for (int i = 1; i < this.size; i++) {
            ctx.fill(this.cx + i * this.cs, this.cy, this.cx + i * this.cs + 1, this.cy + canvas, gc);
            ctx.fill(this.cx, this.cy + i * this.cs, this.cx + canvas, this.cy + i * this.cs + 1, gc);
         }
      }

      int[] p = this.pixelAt(mx, my);
      if (p != null && !this.showFiles) {
         int r = this.tool == PaintScreen.Tool.BRUSH || this.tool == PaintScreen.Tool.ERASER ? this.brush : 1;
         int x0 = p[0] - (r - 1) / 2;
         int y0 = p[1] - (r - 1) / 2;
         int c = this.tool == PaintScreen.Tool.ERASER ? 0x66FFFFFF : (this.color & 16777215 | 0x88000000);
         ctx.fill(this.cx + x0 * this.cs, this.cy + y0 * this.cs, this.cx + (x0 + r) * this.cs, this.cy + (y0 + r) * this.cs, c);
         if (this.tool == PaintScreen.Tool.LINE && this.lineStart >= 0) {
            this.plotLine(this.lineStart % this.size, this.lineStart / this.size, p[0], p[1], (x, y) -> ctx.fill(this.cx + x * this.cs, this.cy + y * this.cs, this.cx + (x + 1) * this.cs, this.cy + (y + 1) * this.cs, this.color & 16777215 | 0x99000000));
         }
      }
   }

   private void renderFiles(GuiGraphicsExtractor ctx, int mx, int my) {
      int canvas = this.cs * this.size;
      int x = this.cx;
      int y = this.cy;
      Render2D.roundRect(ctx, x, y, canvas, canvas, 4, 0xF0141420);
      ctx.text(this.font, Component.literal("Gespeicherte Bilder").withStyle(ChatFormatting.BOLD), x + 6, y + 6, ACCENT, false);
      if (this.files.isEmpty()) {
         ctx.text(this.font, "§7Noch nichts gespeichert", x + 6, y + 20, Theme.TEXT_DIM, false);
      }

      int ry = y + 20;

      for (Path f : this.files) {
         if (ry > y + canvas - 16) {
            break;
         }

         String name = f.getFileName().toString().replace(".png", "");
         this.button(ctx, x + 6, ry, canvas - 12, 14, this.font.plainSubstrByWidth(name, canvas - 20), mx, my, () -> this.load(f), false);
         ry += 16;
      }
   }

   private void checker(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int cell) {
      for (int yy = 0; yy < h; yy += cell) {
         for (int xx = 0; xx < w; xx += cell) {
            boolean dark = (xx / cell + yy / cell) % 2 == 0;
            ctx.fill(x + xx, y + yy, x + Math.min(w, xx + cell), y + Math.min(h, yy + cell), dark ? 0xFF9A9AA6 : 0xFFC8C8D2);
         }
      }
   }

   private static int shade(int argb, float t) {
      float[] hsb = java.awt.Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
      // links dunkel, Mitte Originalfarbe, rechts hell
      float b = t < 0.5F ? t * 2.0F * Math.max(0.15F, hsb[2]) : hsb[2] + (1.0F - hsb[2]) * (t - 0.5F) * 2.0F;
      float s = t < 0.5F ? hsb[1] : hsb[1] * (1.0F - (t - 0.5F) * 2.0F);
      return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], s, Math.min(1.0F, b));
   }

   // ------------------------------------------------------------ Knöpfe

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int mx, int my, Runnable action, boolean active) {
      boolean hov = Render2D.inside(mx, my, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, Math.min(5, h / 2), active ? Render2D.alpha(ACCENT, 0.45F) : (hov ? Theme.CARD_HOVER : Theme.CARD));
      String l = this.font.plainSubstrByWidth(label, w - 4);
      ctx.text(this.font, l, x + (w - this.font.width(l)) / 2, y + (h - 8) / 2, hov || active ? Theme.TEXT : Theme.TEXT_DIM, false);
      this.buttons.add(new PaintScreen.Btn(x, y, w, h, action));
   }

   private void bigButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int mx, int my, Runnable action) {
      boolean hov = Render2D.inside(mx, my, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 6, hov ? ACCENT : Render2D.alpha(ACCENT, 0.75F));
      Component t = Component.literal(label).withStyle(ChatFormatting.BOLD);
      ctx.text(this.font, t, x + (w - this.font.width(t)) / 2, y + (h - 8) / 2, -15724528, false);
      this.buttons.add(new PaintScreen.Btn(x, y, w, h, action));
   }

   // ------------------------------------------------------------ Malen

   private int[] pixelAt(double mx, double my) {
      int canvas = this.cs * this.size;
      if (mx < this.cx || my < this.cy || mx >= this.cx + canvas || my >= this.cy + canvas) {
         return null;
      } else {
         return new int[]{(int)((mx - this.cx) / this.cs), (int)((my - this.cy) / this.cs)};
      }
   }

   private void pushUndo() {
      this.undo.push(this.pixels.clone());
      while (this.undo.size() > 40) {
         this.undo.removeLast();
      }

      this.redo.clear();
   }

   private void undo() {
      if (!this.undo.isEmpty()) {
         this.redo.push(this.pixels);
         this.pixels = this.undo.pop();
         this.dirty = true;
      }
   }

   private void redo() {
      if (!this.redo.isEmpty()) {
         this.undo.push(this.pixels);
         this.pixels = this.redo.pop();
         this.dirty = true;
      }
   }

   private void dab(int x, int y) {
      int c = this.tool == PaintScreen.Tool.ERASER ? 0 : this.color;
      int r = this.brush;
      int x0 = x - (r - 1) / 2;
      int y0 = y - (r - 1) / 2;

      for (int yy = y0; yy < y0 + r; yy++) {
         for (int xx = x0; xx < x0 + r; xx++) {
            if (xx >= 0 && yy >= 0 && xx < this.size && yy < this.size) {
               this.pixels[yy * this.size + xx] = c;
            }
         }
      }

      this.dirty = true;
   }

   private interface Plot {
      void at(int x, int y);
   }

   private void plotLine(int x0, int y0, int x1, int y1, PaintScreen.Plot plot) {
      int dx = Math.abs(x1 - x0);
      int dy = -Math.abs(y1 - y0);
      int sx = x0 < x1 ? 1 : -1;
      int sy = y0 < y1 ? 1 : -1;
      int err = dx + dy;

      while (true) {
         plot.at(x0, y0);
         if (x0 == x1 && y0 == y1) {
            return;
         }

         int e2 = 2 * err;
         if (e2 >= dy) {
            err += dy;
            x0 += sx;
         }

         if (e2 <= dx) {
            err += dx;
            y0 += sy;
         }
      }
   }

   private void fill(int x, int y) {
      int target = this.pixels[y * this.size + x];
      if (target == this.color) {
         return;
      }

      Deque<Integer> stack = new ArrayDeque<>();
      stack.push(y * this.size + x);

      while (!stack.isEmpty()) {
         int i = stack.pop();
         if (this.pixels[i] == target) {
            this.pixels[i] = this.color;
            int px = i % this.size;
            int py = i / this.size;
            if (px > 0) {
               stack.push(i - 1);
            }

            if (px < this.size - 1) {
               stack.push(i + 1);
            }

            if (py > 0) {
               stack.push(i - this.size);
            }

            if (py < this.size - 1) {
               stack.push(i + this.size);
            }
         }
      }

      this.dirty = true;
   }

   private void paintAt(int[] p, boolean start, int button) {
      if (button == InputConstants.MOUSE_BUTTON_RIGHT || this.tool == PaintScreen.Tool.PICKER) {
         int c = this.pixels[p[1] * this.size + p[0]];
         if ((c >>> 24) != 0) {
            this.color = c | 0xFF000000;
            if (this.tool == PaintScreen.Tool.PICKER) {
               this.tool = PaintScreen.Tool.BRUSH;
            }
         }

         return;
      }

      switch (this.tool) {
         case FILL:
            if (start) {
               this.pushUndo();
               this.fill(p[0], p[1]);
            }
            break;
         case LINE:
            if (start) {
               if (this.lineStart < 0) {
                  this.lineStart = p[1] * this.size + p[0];
               } else {
                  this.pushUndo();
                  this.plotLine(this.lineStart % this.size, this.lineStart / this.size, p[0], p[1], this::dab);
                  this.lineStart = -1;
               }
            }
            break;
         default:
            if (start) {
               this.pushUndo();
               this.dab(p[0], p[1]);
            } else if (this.lastPx >= 0) {
               this.plotLine(this.lastPx, this.lastPy, p[0], p[1], this::dab);
            }

            this.lastPx = p[0];
            this.lastPy = p[1];
      }
   }

   private void resize(int newSize) {
      if (newSize != this.size) {
         int[] old = this.pixels;
         int oldSize = this.size;
         this.newCanvas(newSize);

         for (int y = 0; y < newSize; y++) {
            for (int x = 0; x < newSize; x++) {
               this.pixels[y * newSize + x] = old[y * oldSize / newSize * oldSize + x * oldSize / newSize];
            }
         }

         this.status = "Leinwand jetzt " + newSize + "×" + newSize + " (Bild wurde skaliert)";
      }
   }

   // ------------------------------------------------------------ Dateien & Teilen

   private byte[] png() throws Exception {
      BufferedImage img = new BufferedImage(this.size, this.size, BufferedImage.TYPE_INT_ARGB);
      img.setRGB(0, 0, this.size, this.size, this.pixels, 0, this.size);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
   }

   private void save() {
      try {
         Files.createDirectories(DIR);
         String name = "bild-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".png";
         Files.write(DIR.resolve(name), this.png());
         this.status = "§aGespeichert: §f" + name + " §7(config/meowl-paint)";
      } catch (Exception var2) {
         this.status = "§cSpeichern fehlgeschlagen: " + var2.getMessage();
      }
   }

   private static List<Path> listFiles() {
      if (!Files.isDirectory(DIR)) {
         return List.of();
      } else {
         try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".png"))
               .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
               .limit(30L)
               .toList();
         } catch (Exception var5) {
            return List.of();
         }
      }
   }

   private void load(Path file) {
      try {
         BufferedImage img = ImageIO.read(file.toFile());
         if (img == null) {
            throw new IllegalStateException("kein Bild");
         }

         int s = img.getWidth() <= 32 && img.getHeight() <= 32 ? 32 : (img.getWidth() <= 64 && img.getHeight() <= 64 ? 64 : 128);
         this.newCanvas(s);

         for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
               int sx = x * img.getWidth() / s;
               int sy = y * img.getHeight() / s;
               this.pixels[y * s + x] = img.getRGB(sx, sy);
            }
         }

         this.showFiles = false;
         this.status = "Geladen: " + file.getFileName();
      } catch (Exception var7) {
         this.status = "§cLaden fehlgeschlagen: " + var7.getMessage();
      }
   }

   private void share() {
      if (this.busy) {
         return;
      }

      MeowlService svc = MeowlService.get();
      BlockImages images = NebulaClient.getInstance().getModuleManager().get(BlockImages.class);
      if (!svc.isConnected()) {
         this.status = "§cNicht mit dem Meowl Server verbunden";
         return;
      } else if (svc.baseUrl() == null || !svc.baseUrl().startsWith("https://")) {
         this.status = "§cTeilen braucht einen Meowl Server mit HTTPS-Adresse";
         return;
      } else if (images == null) {
         return;
      }

      byte[] data;
      try {
         data = this.png();
      } catch (Exception var5) {
         this.status = "§c" + var5.getMessage();
         return;
      }

      this.busy = true;
      this.status = "Lade hoch …";
      svc.request("POST", "/paint", null, data).whenComplete((r, e) -> this.minecraft.execute(() -> {
         this.busy = false;
         if (e != null || !r.ok() || r.json() == null || !r.json().has("id")) {
            String err = e != null ? e.getMessage() : (r.status() == 404 ? "Meowl Server zu alt – bitte Server-Update einspielen" : r.error());
            this.status = "§cHochladen fehlgeschlagen: " + err;
         } else {
            String url = svc.baseUrl() + "/paint/" + r.json().get("id").getAsString() + ".png";
            if (images.addPainted(url, 2, 2)) {
               if (!images.isEnabled()) {
                  images.setEnabled(true);
               }

               this.status = "§aIn Block Images ausgewählt! §7Platzier-Modus an, dann Rechtsklick auf eine Wand";
            } else {
               this.status = "§cBlock-Images-Liste ist voll";
            }
         }
      }));
   }

   // ------------------------------------------------------------ Eingabe

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();

      for (PaintScreen.Btn b : List.copyOf(this.buttons)) {
         if (Render2D.inside(mx, my, b.x, b.y, b.w, b.h)) {
            if (b.action != null) {
               if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                  b.action.run();
               }
            } else {
               this.slider(b, mx);
            }

            return true;
         }
      }

      int[] p = this.showFiles ? null : this.pixelAt(mx, my);
      if (p != null) {
         this.painting = true;
         this.lastPx = -1;
         this.paintAt(p, true, click.button());
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   private void slider(PaintScreen.Btn b, double mx) {
      float t = (float)Math.max(0.0, Math.min(1.0, (mx - b.x) / (b.w - 1)));
      // erster Regler = Farbton, zweiter = Helligkeit
      boolean hue = this.buttons.stream().filter(x -> x.action == null).findFirst().orElse(null) == b;
      if (hue) {
         this.baseColor = 0xFF000000 | java.awt.Color.HSBtoRGB(t, 0.85F, 0.95F);
         this.color = this.baseColor;
      } else {
         this.color = shade(this.baseColor, t);
      }

      if (this.tool == PaintScreen.Tool.ERASER || this.tool == PaintScreen.Tool.PICKER) {
         this.tool = PaintScreen.Tool.BRUSH;
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.painting && (this.tool == PaintScreen.Tool.BRUSH || this.tool == PaintScreen.Tool.ERASER) && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         int[] p = this.pixelAt(click.x(), click.y());
         if (p != null) {
            this.paintAt(p, false, 0);
         } else {
            this.lastPx = -1;
         }

         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      this.painting = false;
      this.lastPx = -1;
      return super.mouseReleased(click);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      boolean ctrl = com.lennart.nebula.compat.Keys.ctrl(input.modifiers());
      if (ctrl && input.key() == InputConstants.KEY_Z) {
         this.undo();
         return true;
      } else if (ctrl && input.key() == InputConstants.KEY_Y) {
         this.redo();
         return true;
      } else if (ctrl && input.key() == InputConstants.KEY_S) {
         this.save();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private record Btn(int x, int y, int w, int h, Runnable action) {
   }
}
