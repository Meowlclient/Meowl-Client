package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.module.mods.Replay;
import com.lennart.nebula.replay.ReplayData;
import com.lennart.nebula.replay.ReplayEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Gespeicherte Replay-Clips: anklicken = abspielen (Blöcke werden nicht aufgenommen – am besten am gleichen Ort). */
public class ReplayListScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private final Screen parent;
   private final List<Path> files = new ArrayList<>();
   private final List<int[]> rects = new ArrayList<>();
   private final List<Runnable> clicks = new ArrayList<>();
   private String note = "";
   private float scroll;

   public ReplayListScreen(Screen parent) {
      super(Component.literal("Replays"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.files.clear();

      try (Stream<Path> s = Files.list(Replay.folder())) {
         s.filter(p -> p.toString().endsWith(".mreplay"))
            .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
            .forEach(this.files::add);
      } catch (Exception var6) {
      }
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

   private void play(Path file) {
      this.note = "Lade …";

      try {
         ReplayData.Clip clip = ReplayData.load(file);
         if (this.minecraft.level == null) {
            this.note = "§cErst eine Welt betreten";
         } else {
            ReplayEngine.get().start(clip);
            this.minecraft.gui.setScreen(new ReplayScreen(clip, true));
         }
      } catch (Exception var3) {
         this.note = "§cKonnte nicht geladen werden: " + var3.getMessage();
      }
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.rects.clear();
      this.clicks.clear();
      ctx.fill(0, 0, this.width, this.height, 0xB005060C);
      int pw = Math.min(380, this.width - 20);
      int ph = Math.min(260, this.height - 20);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.roundRect(ctx, px, py, pw, ph, 8, 0xF0101420);
      ctx.text(this.font, Component.literal("Gespeicherte Replays").withStyle(ChatFormatting.BOLD), px + 10, py + 8, -1, true);
      ctx.text(this.font, "Ordner: meowl-replays · Blöcke werden nicht aufgenommen", px + 10, py + 20, -7564628, false);
      int top = py + 34;
      int bottom = py + ph - 22;
      ctx.enableScissor(px, top, px + pw, bottom);
      if (this.files.isEmpty()) {
         ctx.text(this.font, "Noch keine Clips – im Replay auf „Speichern“ drücken", px + 12, top + 4, -7564628, false);
      }

      for (int i = 0; i < this.files.size(); i++) {
         Path f = this.files.get(i);
         int y = top + i * 20 - Math.round(this.scroll);
         if (y + 20 < top || y > bottom) {
            continue;
         }

         boolean hot = mouseY >= top && mouseY < bottom && Render2D.inside(mouseX, mouseY, px + 10, y, pw - 20, 18);
         Render2D.roundRect(ctx, px + 10, y, pw - 20, 18, 4, hot ? 0xE0303A58 : 0xC01C2234);
         String name = f.getFileName().toString().replace("replay_", "").replace(".mreplay", "").replace('_', ' ');
         ctx.text(this.font, "▶  " + name, px + 16, y + 5, -1, false);
         if (mouseY >= top && mouseY < bottom) {
            this.rects.add(new int[]{px + 10, y, pw - 20, 18});
            this.clicks.add(() -> this.play(f));
         }
      }

      ctx.disableScissor();
      ctx.text(this.font, this.note, px + 10, py + ph - 16, this.note.startsWith("§c") ? 0xFFFF6060 : -7564628, false);
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
      this.scroll = Math.max(0.0F, Math.min(Math.max(0, this.files.size() * 20 - 150), this.scroll - (float)v * 20.0F));
      return true;
   }
}
