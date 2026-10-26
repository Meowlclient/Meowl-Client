package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.config.ConfigManager;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
   private static final int SNAP = 4;
   private static final int GRID = 8;
   private final MeowlScreen parent;
   private final ModuleManager modules;
   private final ConfigManager config;
   private final List<HudEditorScreen.Element> elements = new ArrayList<>();
   private HudEditorScreen.Element selected;
   private HudEditorScreen.Element dragging;
   // Größe ziehen: 1 = Breite (rechter Rand), 2 = Höhe (unterer Rand), 3 = beides (Ecke)
   private HudEditorScreen.Element resizing;
   private int resizeMode;
   private double dragOffX;
   private double dragOffY;
   private boolean grid = true;
   private boolean guides = true;
   private Float guideX;
   private Float guideY;
   private long lastClick;
   private HudEditorScreen.Element lastClicked;
   private long resetAllArmed;
   private final List<HudEditorScreen.Button> buttons = new ArrayList<>();
   // Elemente-Liste: verschiebbar und einklappbar (bleibt für die Sitzung gemerkt)
   private static float listOffX = Float.NaN;
   private static float listOffY = 6.0F;
   private static boolean listCollapsed;
   private boolean draggingList;
   private double listGrabX;
   private double listGrabY;
   private int listX;
   private int listY;
   private int listW;

   public HudEditorScreen(MeowlScreen parent, ModuleManager modules, ConfigManager config) {
      super(Component.literal("HUD bearbeiten"));
      this.parent = parent;
      this.modules = modules;
      this.config = config;
   }

   @Override
   protected void init() {
      HudLayout.setEditing(true);
      this.elements.clear();

      for (Module m : this.modules.getModules()) {
         for (HudBox box : m.getHudBoxes()) {
            this.elements.add(new HudEditorScreen.Element(m, box));
         }
      }
   }

   @Override
   public void removed() {
      HudLayout.setEditing(false);
      this.config.save();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
      if (this.grid) {
         for (int x = 0; x < this.width; x += 16) {
            context.fill(x, 0, x + 1, this.height, 285212671);
         }

         for (int y = 0; y < this.height; y += 16) {
            context.fill(0, y, this.width, y + 1, 285212671);
         }
      }

      context.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 419430399);
      context.fill(0, this.height / 2, this.width, this.height / 2 + 1, 419430399);
   }

   private boolean visible(HudEditorScreen.Element e) {
      return e.module().isEnabled() && e.box().isVisibleNow();
   }

   private HudEditorScreen.Element elementAt(double mx, double my) {
      for (int i = this.elements.size() - 1; i >= 0; i--) {
         HudEditorScreen.Element e = this.elements.get(i);
         if (this.visible(e)
            && mx >= e.box().screenX() - 2.0F
            && mx <= e.box().screenX() + e.box().screenW() + 2.0F
            && my >= e.box().screenY() - 2.0F
            && my <= e.box().screenY() + e.box().screenH() + 2.0F) {
            return e;
         }
      }

      return null;
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      this.buttons.clear();
      HudEditorScreen.Element hovered = this.dragging != null ? this.dragging : this.elementAt(mouseX, mouseY);

      for (HudEditorScreen.Element e : this.elements) {
         if (this.visible(e)) {
            HudBox b = e.box();
            int x = Math.round(b.screenX());
            int y = Math.round(b.screenY());
            int w = Math.round(b.screenW());
            int h = Math.round(b.screenH());
            boolean sel = e == this.selected;
            boolean hov = e == hovered;
            int color = sel ? -7643914 : (hov ? -1060850179 : 1627389951);
            context.fill(x - 1, y - 1, x + w + 1, y, color);
            context.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
            context.fill(x - 1, y, x, y + h, color);
            context.fill(x + w, y, x + w + 1, y + h, color);
            if (sel || hov) {
               context.fill(x, y, x + w, y + h, sel ? 546004214 : 285212671);
               String label = e.label() + (b.isAnchored() ? " · an Hotbar" : "");
               int lw = this.font.width(label) + 6;
               int ly = y >= 12 ? y - 12 : y + h + 2;
               Render2D.roundRect(context, x, ly, lw, 11, 3, sel ? -7643914 : -534764490);
               context.drawString(this.font, label, x + 3, ly + 2, -1, false);
            }
         }
      }

      if (this.selected != null && this.dragging == null && this.visible(this.selected) && !this.selected.box().isAnchored()) {
         this.renderHandles(context, mouseX, mouseY, this.selected.box());
      }

      if (this.dragging != null && this.guides) {
         if (this.guideX != null) {
            context.fill(Math.round(this.guideX), 0, Math.round(this.guideX) + 1, this.height, -14494738);
         }

         if (this.guideY != null) {
            context.fill(0, Math.round(this.guideY), this.width, Math.round(this.guideY) + 1, -14494738);
         }
      }

      this.renderElementList(context, mouseX, mouseY);
      this.renderToolbar(context, mouseX, mouseY);
      if (this.selected != null && this.dragging == null && this.visible(this.selected)) {
         this.renderInspector(context, mouseX, mouseY, this.selected);
      }

      if (this.minecraft.options.hideGui) {
         String warn = "HUD ist ausgeblendet (F1) – drücke F1, um es zu sehen";
         context.drawString(this.font, warn, (this.width - this.font.width(warn)) / 2, this.height / 2 - 4, -278748, true);
      }
   }

   /** Griffe am ausgewählten Element: rechter Rand = Breite, unterer Rand = Höhe, Ecke = beides. */
   private int[][] handles(HudBox b) {
      int x = Math.round(b.screenX());
      int y = Math.round(b.screenY());
      int w = Math.round(b.screenW());
      int h = Math.round(b.screenH());
      if (b.isReflow()) {
         // Text-Elemente: nur die Breite ist verstellbar (Text wird nicht verzerrt)
         return new int[][]{{x + w - 2, y + h / 2 - 5, 5, 10, 1}};
      }

      return new int[][]{{x + w - 2, y + h / 2 - 5, 5, 10, 1}, {x + w / 2 - 5, y + h - 2, 10, 5, 2}, {x + w - 3, y + h - 3, 7, 7, 3}};
   }

   private int handleAt(double mx, double my) {
      if (this.selected != null && this.visible(this.selected) && !this.selected.box().isAnchored()) {
         for (int[] r : this.handles(this.selected.box())) {
            if (Render2D.inside(mx, my, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4)) {
               return r[4];
            }
         }
      }

      return 0;
   }

   private void renderHandles(GuiGraphics ctx, int mouseX, int mouseY, HudBox b) {
      int hot = this.resizing != null ? this.resizeMode : this.handleAt(mouseX, mouseY);

      for (int[] r : this.handles(b)) {
         Render2D.roundRect(ctx, r[0], r[1], r[2], r[3], 2, r[4] == hot ? -1 : -7643914);
      }

      if (hot != 0) {
         String info = b.isReflow() ? String.format("Breite %.2fx", b.stretchX.get()) : String.format("Breite %.2fx · Höhe %.2fx", b.stretchX.get(), b.stretchY.get());
         int iw = this.font.width(info) + 6;
         int ix = Math.round(Math.min(this.width - iw - 2, b.screenX() + b.screenW() + 6.0F));
         int iy = Math.round(b.screenY() + b.screenH() + 6.0F);
         Render2D.roundRect(ctx, ix, iy, iw, 11, 3, -534764490);
         ctx.drawString(this.font, info, ix + 3, iy + 2, -1, false);
      }
   }

   private void renderToolbar(GuiGraphics ctx, int mouseX, int mouseY) {
      String[] labels = new String[]{
         (this.grid ? "☑" : "☐") + " Raster",
         (this.guides ? "☑" : "☐") + " Hilfslinien",
         (listCollapsed ? "☐" : "☑") + " Liste",
         System.currentTimeMillis() - this.resetAllArmed < 3000L ? "Wirklich alles?" : "Alles zurücksetzen",
         "Fertig"
      };
      Runnable[] actions = new Runnable[]{() -> this.grid = !this.grid, () -> this.guides = !this.guides, () -> listCollapsed = !listCollapsed, this::resetAll, this::onClose};
      int total = 0;

      for (String l : labels) {
         total += this.font.width(l) + 16 + 4;
      }

      int x = (this.width - total) / 2;
      int y = this.height - 22;
      Render2D.roundRect(ctx, x - 6, y - 4, total + 8, 22, 8, -535686112);

      for (int i = 0; i < labels.length; i++) {
         int w = this.font.width(labels[i]) + 16;
         boolean hov = Render2D.inside(mouseX, mouseY, x, y, w, 14);
         boolean primary = i == labels.length - 1;
         Render2D.roundRect(ctx, x, y, w, 14, 5, primary ? (hov ? -8635667 : -7643914) : (hov ? -13748912 : -14933965));
         ctx.drawString(this.font, labels[i], x + 8, y + 3, -1, false);
         this.buttons.add(new HudEditorScreen.Button(x, y, w, 14, actions[i]));
         x += w + 4;
      }

      String help = "Ziehen: verschieben · Ränder ziehen: Breite/Höhe · Mausrad: Größe · Pfeile: fein · R: zurücksetzen · Doppelklick: Einstellungen";
      int hw = Math.round(this.font.width(help) * 0.75F);
      ctx.pose().pushMatrix();
      ctx.pose().translate((this.width - hw) / 2.0F, this.height - 32);
      ctx.pose().scale(0.75F, 0.75F);
      ctx.drawString(this.font, help, 0, 0, -1056964609, true);
      ctx.pose().popMatrix();
   }

   private void renderElementList(GuiGraphics ctx, int mouseX, int mouseY) {
      int w = 0;

      for (HudEditorScreen.Element e : this.elements) {
         w = Math.max(w, this.font.width(e.label()));
      }

      w += 22;
      if (Float.isNaN(listOffX)) {
         listOffX = this.width - w - 6;
      }

      int x = Math.round(Math.max(4.0F, Math.min(this.width - w - 4, listOffX)));
      int y = Math.round(Math.max(4.0F, Math.min(this.height - 20, listOffY)));
      this.listX = x;
      this.listY = y;
      this.listW = w;
      int bodyH = listCollapsed ? 0 : this.elements.size() * 12;
      Render2D.roundRect(ctx, x - 4, y - 4, w + 8, bodyH + 18, 6, -804121568);
      boolean headHov = Render2D.inside(mouseX, mouseY, x - 4, y - 4, w - 10, 14);
      ctx.drawString(this.font, Component.literal((listCollapsed ? "▸ " : "▾ ") + "Elemente").withStyle(ChatFormatting.BOLD), x, y, headHov ? -1 : -3885571, false);
      ctx.drawString(this.font, "⠿", x + w - 8, y, -7564628, false);
      this.buttons.add(new HudEditorScreen.Button(x - 4, y - 4, 14, 14, () -> listCollapsed = !listCollapsed));
      if (listCollapsed) {
         return;
      }

      y += 12;

      for (HudEditorScreen.Element e : this.elements) {
         boolean on = e.module().isEnabled() && e.box().shown.get();
         boolean hov = Render2D.inside(mouseX, mouseY, x - 2, y - 1, w + 4, 11);
         if (hov || e == this.selected) {
            Render2D.roundRect(ctx, x - 2, y - 1, w + 4, 11, 3, e == this.selected ? 1619746038 : 822083583);
         }

         Render2D.circle(ctx, x + 4, y + 4, 3, on ? -11870592 : -11841432);
         ctx.drawString(this.font, e.label(), x + 12, y + 1, on ? -1578509 : -7564628, false);
         this.buttons.add(new HudEditorScreen.Button(x - 2, y - 1, w + 4, 11, () -> {
            // nur die Anzeige im HUD umschalten – das Modul selbst bleibt an (ist es aus, wird es eingeschaltet)
            if (!e.module().isEnabled()) {
               e.module().setEnabled(true);
               e.box().shown.set(true);
            } else {
               e.box().shown.set(!e.box().shown.get());
            }

            this.selected = e;
            this.config.save();
         }));
         y += 12;
      }
   }

   private void renderInspector(GuiGraphics ctx, int mouseX, int mouseY, HudEditorScreen.Element e) {
      HudBox b = e.box();
      int w = 150;
      int h = 76;
      int x = Math.round(Math.max(4.0F, Math.min((float)(this.width - w - 4), b.screenX())));
      int y = Math.round(b.screenY() + b.screenH() + 16.0F);
      if (y + h > this.height - 40) {
         y = Math.round(b.screenY()) - h - 16;
      }

      y = Math.max(4, y);
      Render2D.roundRect(ctx, x, y, w, h, 6, -267250656);
      this.row(
         ctx,
         mouseX,
         mouseY,
         x + 6,
         y + 5,
         w - 12,
         "Größe",
         String.format("%.2fx", b.scale.get()),
         () -> b.scaleAroundCenter(b.scale.get() - 0.05, this.width, this.height),
         () -> b.scaleAroundCenter(b.scale.get() + 0.05, this.width, this.height)
      );
      this.row(
         ctx,
         mouseX,
         mouseY,
         x + 6,
         y + 18,
         w - 12,
         "Deckkraft",
         b.opacity.getInt() + "%",
         () -> b.opacity.set(b.opacity.get() - 5.0),
         () -> b.opacity.set(b.opacity.get() + 5.0)
      );
      this.row(
         ctx,
         mouseX,
         mouseY,
         x + 6,
         y + 31,
         w - 12,
         "Breite",
         String.format("%.2fx", b.stretchX.get()),
         () -> b.resizeTo(b.screenW() * (float)((b.stretchX.get() - 0.05) / b.stretchX.get()), -1.0F, this.width, this.height),
         () -> b.resizeTo(b.screenW() * (float)((b.stretchX.get() + 0.05) / b.stretchX.get()), -1.0F, this.width, this.height)
      );
      this.row(
         ctx,
         mouseX,
         mouseY,
         x + 6,
         y + 44,
         w - 12,
         "Höhe",
         String.format("%.2fx", b.stretchY.get()),
         () -> b.resizeTo(-1.0F, b.screenH() * (float)((b.stretchY.get() - 0.05) / b.stretchY.get()), this.width, this.height),
         () -> b.resizeTo(-1.0F, b.screenH() * (float)((b.stretchY.get() + 0.05) / b.stretchY.get()), this.width, this.height)
      );
      int by = y + 59;
      int bw = (w - 12 - 8) / 3;
      String[] labels = new String[]{b.background.isVisible() ? (b.background.get() ? "☑ Kasten" : "☐ Kasten") : "–", "Zurücksetzen", "Optionen"};
      Runnable[] actions = new Runnable[]{() -> {
         if (b.background.isVisible()) {
            b.background.toggle();
         }
      }, () -> {
         b.resetLayout();
         this.config.save();
      }, () -> {
         this.parent.openSettings(e.module());
         this.minecraft.setScreen(this.parent);
      }};

      for (int i = 0; i < 3; i++) {
         int bx = x + 6 + i * (bw + 4);
         boolean hov = Render2D.inside(mouseX, mouseY, bx, by, bw, 12);
         Render2D.roundRect(ctx, bx, by, bw, 12, 4, hov ? -13748912 : -14933965);
         int tw = Math.round(this.font.width(labels[i]) * 0.75F);
         ctx.pose().pushMatrix();
         ctx.pose().translate(bx + (bw - tw) / 2.0F, by + 3);
         ctx.pose().scale(0.75F, 0.75F);
         ctx.drawString(this.font, labels[i], 0, 0, -1578509, false);
         ctx.pose().popMatrix();
         this.buttons.add(new HudEditorScreen.Button(bx, by, bw, 12, actions[i]));
      }
   }

   private void row(GuiGraphics ctx, int mouseX, int mouseY, int x, int y, int w, String label, String value, Runnable minus, Runnable plus) {
      ctx.drawString(this.font, label, x, y + 2, -7564628, false);
      int bx = x + w - 60;

      for (int i = 0; i < 2; i++) {
         int px = i == 0 ? bx : bx + 48;
         boolean hov = Render2D.inside(mouseX, mouseY, px, y, 12, 11);
         Render2D.roundRect(ctx, px, y, 12, 11, 3, hov ? -13748912 : -14933965);
         ctx.drawString(this.font, i == 0 ? "−" : "+", px + 3, y + 2, -1, false);
         Runnable a = i == 0 ? minus : plus;
         this.buttons.add(new HudEditorScreen.Button(px, y, 12, 11, () -> {
            a.run();
            this.config.save();
         }));
      }

      ctx.drawString(this.font, value, bx + 30 - this.font.width(value) / 2, y + 2, -1578509, false);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         // Titelzeile der Liste: ziehen verschiebt die Liste
         if (Render2D.inside(mx, my, this.listX + 10, this.listY - 4, this.listW, 14)) {
            this.draggingList = true;
            this.listGrabX = mx - this.listX;
            this.listGrabY = my - this.listY;
            return true;
         }

         for (HudEditorScreen.Button b : List.copyOf(this.buttons)) {
            if (b.hit(mx, my)) {
               b.action().run();
               return true;
            }
         }
      }

      int handle = click.button() == InputConstants.MOUSE_BUTTON_LEFT ? this.handleAt(mx, my) : 0;
      if (handle != 0) {
         this.resizing = this.selected;
         this.resizeMode = handle;
         return true;
      }

      HudEditorScreen.Element e = this.elementAt(mx, my);
      if (e != null) {
         long now = System.currentTimeMillis();
         if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && e == this.lastClicked && now - this.lastClick < 350L) {
            this.parent.openSettings(e.module());
            this.minecraft.setScreen(this.parent);
            return true;
         } else {
            this.lastClick = now;
            this.lastClicked = e;
            this.selected = e;
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
               this.dragging = e;
               this.dragOffX = mx - e.box().screenX();
               this.dragOffY = my - e.box().screenY();
            }

            return true;
         }
      } else {
         this.selected = null;
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.draggingList) {
         listOffX = (float)(click.x() - this.listGrabX);
         listOffY = (float)(click.y() - this.listGrabY);
         return true;
      } else if (this.resizing != null) {
         HudBox b = this.resizing.box();
         float nw = this.resizeMode != 2 ? (float)Math.max(6.0, click.x() - b.screenX()) : -1.0F;
         float nh = this.resizeMode != 1 ? (float)Math.max(6.0, click.y() - b.screenY()) : -1.0F;
         if (this.resizeMode == 3 && com.lennart.nebula.compat.Keys.shiftDown()) {
            // Shift an der Ecke: Seitenverhältnis behalten
            float k = Math.max(nw / Math.max(1.0F, b.screenW()), nh / Math.max(1.0F, b.screenH()));
            nw = b.screenW() * k;
            nh = b.screenH() * k;
         }

         b.resizeTo(nw, nh, this.width, this.height);
         return true;
      } else if (this.dragging == null) {
         return super.mouseDragged(click, offsetX, offsetY);
      } else {
         HudBox b = this.dragging.box();
         if (b.isAnchored()) {
            b.detach();
            return true;
         } else {
            float w = b.screenW();
            float h = b.screenH();
            float nx = (float)(click.x() - this.dragOffX);
            float ny = (float)(click.y() - this.dragOffY);
            boolean shift = com.lennart.nebula.compat.Keys.shiftDown();
            this.guideX = null;
            this.guideY = null;
            if (this.guides && !shift) {
               float[] snappedX = this.snap(nx, w, true);
               float[] snappedY = this.snap(ny, h, false);
               nx = snappedX[0];
               ny = snappedY[0];
               this.guideX = snappedX[1] >= 0.0F ? snappedX[1] : null;
               this.guideY = snappedY[1] >= 0.0F ? snappedY[1] : null;
            }

            if (this.grid && !shift && this.guideX == null) {
               nx = Math.round(nx / 8.0F) * 8;
            }

            if (this.grid && !shift && this.guideY == null) {
               ny = Math.round(ny / 8.0F) * 8;
            }

            nx = Math.max(0.0F, Math.min(this.width - w, nx));
            ny = Math.max(0.0F, Math.min(this.height - h, ny));
            b.moveTo(nx, ny, this.width, this.height);
            return true;
         }
      }
   }

   private float[] snap(float pos, float size, boolean horizontal) {
      float screen = horizontal ? this.width : this.height;
      List<Float> lines = new ArrayList<>(List.of(0.0F, screen / 2.0F, screen));

      for (HudEditorScreen.Element e : this.elements) {
         if (e != this.dragging && this.visible(e)) {
            float a = horizontal ? e.box().screenX() : e.box().screenY();
            float s = horizontal ? e.box().screenW() : e.box().screenH();
            lines.add(a);
            lines.add(a + s / 2.0F);
            lines.add(a + s);
         }
      }

      float best = 5.0F;
      float result = pos;
      float guide = -1.0F;
      float[] anchors = new float[]{0.0F, size / 2.0F, size};

      for (float line : lines) {
         for (float anchor : anchors) {
            float d = Math.abs(pos + anchor - line);
            if (d < best) {
               best = d;
               result = line - anchor;
               guide = line;
            }
         }
      }

      return new float[]{result, guide};
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.draggingList) {
         this.draggingList = false;
         return true;
      } else if (this.resizing != null) {
         this.resizing = null;
         this.resizeMode = 0;
         this.config.save();
         return true;
      } else if (this.dragging != null) {
         this.dragging = null;
         this.guideX = null;
         this.guideY = null;
         this.config.save();
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      HudEditorScreen.Element e = this.elementAt(mouseX, mouseY);
      if (e == null) {
         e = this.selected;
      }

      if (e != null && this.visible(e)) {
         boolean shift = com.lennart.nebula.compat.Keys.shiftDown();
         double step = shift ? 0.25 : 0.05;
         e.box().scaleAroundCenter(e.box().scale.get() + Math.signum(verticalAmount) * step, this.width, this.height);
         this.selected = e;
         this.config.save();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.selected != null && this.visible(this.selected)) {
         int step = com.lennart.nebula.compat.Keys.shift(input.modifiers()) ? 10 : 1;
         HudBox b = this.selected.box();
         float dx = 0.0F;
         float dy = 0.0F;
         switch (input.key()) {
            case InputConstants.KEY_R:
               b.resetLayout();
               this.config.save();
               return true;
            case InputConstants.KEY_RIGHT:
               dx = step;
               break;
            case InputConstants.KEY_LEFT:
               dx = -step;
               break;
            case InputConstants.KEY_DOWN:
               dy = step;
               break;
            case InputConstants.KEY_UP:
               dy = -step;
         }

         if (dx != 0.0F || dy != 0.0F) {
            b.detach();
            b.moveTo(
               Math.max(0.0F, Math.min(this.width - b.screenW(), b.screenX() + dx)),
               Math.max(0.0F, Math.min(this.height - b.screenH(), b.screenY() + dy)),
               this.width,
               this.height
            );
            this.config.save();
            return true;
         }
      }

      return super.keyPressed(input);
   }

   private void resetAll() {
      long now = System.currentTimeMillis();
      if (now - this.resetAllArmed > 3000L) {
         this.resetAllArmed = now;
      } else {
         for (HudEditorScreen.Element e : this.elements) {
            e.box().resetLayout();
         }

         this.resetAllArmed = 0L;
         this.config.save();
      }
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   private record Button(int x, int y, int w, int h, Runnable action) {
      boolean hit(double mx, double my) {
         return Render2D.inside(mx, my, this.x, this.y, this.w, this.h);
      }
   }

   private record Element(Module module, HudBox box) {
      String label() {
         return this.box.label();
      }
   }
}
