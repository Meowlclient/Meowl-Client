package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.module.mods.Replay;
import com.lennart.nebula.replay.ReplayData;
import com.lennart.nebula.replay.ReplayEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Wiedergabe: Welt bleibt sichtbar, unten Zeitleiste, Tempo, Spieler zum Folgen. Leertaste = Pause, ← → = 5 s. */
public class ReplayScreen extends Screen {
   private static final float[] SPEEDS = {0.25F, 0.5F, 1.0F, 2.0F, 4.0F};
   private final ReplayData.Clip clip;
   private final boolean fromFile;
   private final List<int[]> rects = new ArrayList<>();
   private final List<Runnable> clicks = new ArrayList<>();
   private boolean dragging;
   private int barX;
   private int barW;
   private boolean hideUi;

   public ReplayScreen(ReplayData.Clip clip, boolean fromFile) {
      super(Component.literal("Replay"));
      this.clip = clip;
      this.fromFile = fromFile;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void tick() {
      ReplayEngine.get().tick(this.minecraft);
      if (!ReplayEngine.get().playing()) {
         this.onClose();
      }
   }

   @Override
   public void onClose() {
      ReplayEngine.get().stop();
      this.minecraft.setScreen(null);
   }

   private void button(GuiGraphics ctx, int x, int y, int w, String text, boolean on, int mouseX, int mouseY, Runnable action) {
      boolean hot = Render2D.inside(mouseX, mouseY, x, y, w, 16);
      Render2D.roundRect(ctx, x, y, w, 16, 4, on ? 0xE08B5CF6 : (hot ? 0xE0303A58 : 0xD01C2234));
      ctx.drawCenteredString(this.font, text, x + w / 2, y + 4, -1);
      this.rects.add(new int[]{x, y, w, 16});
      this.clicks.add(action);
   }

   private static String time(float frames) {
      int s = (int)(frames / 20.0F);
      return String.format("%d:%02d", s / 60, s % 60);
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.rects.clear();
      this.clicks.clear();
      ReplayEngine e = ReplayEngine.get();
      if (this.hideUi) {
         ctx.drawString(this.font, "H = Bedienung zeigen", 6, this.height - 12, 0x80FFFFFF, true);
         return;
      }

      int h = 58;
      int y = this.height - h;
      ctx.fillGradient(0, y - 20, this.width, this.height, 0, 0xD0080A10);
      // Kopfzeile
      Render2D.roundRect(ctx, 6, 6, 74, 14, 4, 0xD0B01030);
      ctx.drawString(this.font, "● REPLAY", 12, 9, -1, false);
      ctx.drawString(this.font, "Leertaste Pause · ← → 5 s · Tab Spieler · H Bedienung aus · ESC Ende", 88, 9, 0xB0FFFFFF, true);
      // Zeitleiste
      this.barX = 50;
      this.barW = this.width - 100;
      int by = y + 8;
      float len = Math.max(1, e.length() - 1);
      float p = e.position() / len;
      Render2D.roundRect(ctx, this.barX, by, this.barW, 4, 2, 0x60FFFFFF);
      Render2D.roundRect(ctx, this.barX, by, Math.round(this.barW * p), 4, 2, 0xFF8B5CF6);
      Render2D.circle(ctx, this.barX + Math.round(this.barW * p), by + 2, 5, -1);
      ctx.drawString(this.font, time(e.position()), 8, by - 2, -1, false);
      ctx.drawString(this.font, time(e.length()), this.width - 42, by - 2, -1, false);
      // Knöpfe
      int ry = y + 22;
      int x = 8;
      this.button(ctx, x, ry, 24, e.paused() ? "▶" : "❚❚", false, mouseX, mouseY, () -> e.setPaused(!e.paused()));
      x += 28;

      for (float s : SPEEDS) {
         String label = (s < 1.0F ? String.valueOf(s).replace("0.", ",") : String.valueOf((int)s)) + "x";
         this.button(ctx, x, ry, 28, label, e.speed() == s, mouseX, mouseY, () -> e.setSpeed(s));
         x += 30;
      }

      x += 6;
      CameraType cam = this.minecraft.options.getCameraType();
      this.button(ctx, x, ry, 64, cam.isFirstPerson() ? "Ego-Sicht" : (cam.isMirrored() ? "Von vorn" : "Von hinten"), false, mouseX, mouseY, () -> {
         this.minecraft.options.setCameraType(this.minecraft.options.getCameraType().cycle());
      });
      x += 68;
      if (!this.fromFile) {
         this.button(ctx, x, ry, 64, "Speichern", false, mouseX, mouseY, () -> Replay.saveClip(this.clip));
         x += 68;
      }

      // Spieler zum Folgen
      int px = x + 6;
      ctx.drawString(this.font, "Folgen:", px, ry + 4, 0xB0FFFFFF, false);
      px += this.font.width("Folgen:") + 4;
      this.button(ctx, px, ry, 70, "Eigene Sicht", e.follow() == null, mouseX, mouseY, () -> e.setFollow(null));
      px += 74;

      for (Map.Entry<UUID, String> en : e.playersNow().entrySet()) {
         int bw = this.font.width(en.getValue()) + 10;
         if (px + bw > this.width - 6) {
            break;
         }

         UUID id = en.getKey();
         this.button(ctx, px, ry, bw, en.getValue(), id.equals(e.follow()), mouseX, mouseY, () -> e.setFollow(id));
         px += bw + 4;
      }
   }

   private void seekTo(double mx) {
      ReplayEngine e = ReplayEngine.get();
      float k = (float)Math.max(0.0, Math.min(1.0, (mx - this.barX) / this.barW));
      e.seek(k * (e.length() - 1));
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (!this.hideUi && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         int by = this.height - 58 + 8;
         if (click.y() >= by - 6 && click.y() <= by + 10 && click.x() >= this.barX - 4 && click.x() <= this.barX + this.barW + 4) {
            this.dragging = true;
            this.seekTo(click.x());
            return true;
         }

         for (int i = this.rects.size() - 1; i >= 0; i--) {
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
   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      if (this.dragging) {
         this.seekTo(click.x());
         return true;
      } else {
         return super.mouseDragged(click, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      this.dragging = false;
      return super.mouseReleased(click);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      ReplayEngine e = ReplayEngine.get();
      switch (input.key()) {
         case InputConstants.KEY_SPACE:
            e.setPaused(!e.paused());
            return true;
         case InputConstants.KEY_LEFT:
            e.seek(e.position() - 100.0F);
            return true;
         case InputConstants.KEY_RIGHT:
            e.seek(e.position() + 100.0F);
            return true;
         case InputConstants.KEY_H:
            this.hideUi = !this.hideUi;
            return true;
         case InputConstants.KEY_TAB: {
            List<UUID> ids = new ArrayList<>(e.playersNow().keySet());
            if (!ids.isEmpty()) {
               int i = ids.indexOf(e.follow());
               e.setFollow(ids.get((i + 1) % ids.size()));
            }

            return true;
         }
         default:
            return super.keyPressed(input);
      }
   }
}
