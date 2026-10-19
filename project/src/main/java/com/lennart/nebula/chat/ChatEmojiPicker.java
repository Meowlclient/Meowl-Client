package com.lennart.nebula.chat;

import com.lennart.nebula.mixin.ChatScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/** Emoji-Knopf rechts in der Chat-Zeile mit Auswahl: Meowl-Emojis, Symbole und Text-Emoticons. */
public final class ChatEmojiPicker {
   private static final String[] TABS = {"Meowl", "Symbole", "Emoticons"};
   private static final int CELL = 18;
   private static final int COLS = 10;
   private static boolean open;
   private static int tab;
   private static final List<Hit> HITS = new ArrayList<>();

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }

   private ChatEmojiPicker() {
   }

   public static void attach(ChatScreen screen) {
      open = false;
      ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> render((ChatScreen)s, ctx, mx, my));
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   private static void insert(ChatScreen screen, String text) {
      ((ChatScreenAccessor)screen).meowl$input().insertText(text);
   }

   private static int rows() {
      return switch (tab) {
         case 0 -> (ChatEmojis.NAMES.length + COLS - 1) / COLS;
         case 1 -> (ChatEmojis.SYMBOLS.length + COLS - 1) / COLS;
         default -> (ChatEmojis.EMOTICONS.length + 1) / 2 * 14 / CELL + 1;
      };
   }

   private static void render(ChatScreen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      HITS.clear();
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      int w = screen.width;
      int h = screen.height;
      // Knopf
      int bx = w - 16;
      int by = h - 14;
      boolean hovB = inside(mouseX, mouseY, bx, by, 14, 12);
      ctx.fill(bx, by, bx + 14, by + 12, open ? 0xFF7A4CE0 : (hovB ? 0xC0505070 : 0x90303040));
      ctx.text(font, ChatEmojis.icon(0), bx + 3, by + 2, -1, false);
      HITS.add(new Hit(bx, by, 14, 12, () -> open = !open));
      if (hovB && !open) {
         ctx.setTooltipForNextFrame(font, Component.literal("Emojis & Symbole"), mouseX, mouseY);
      }

      if (!open) {
         return;
      }

      int pw = COLS * CELL + 8;
      int ph = 16 + rows() * CELL + 16;
      int px = w - 2 - pw;
      int py = by - 3 - ph;
      ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF4A3A7A);
      ctx.fill(px, py, px + pw, py + ph, 0xF0151724);
      // Reiter
      int tw = (pw - 8) / TABS.length;

      for (int i = 0; i < TABS.length; i++) {
         int tx = px + 4 + i * tw;
         boolean hov = inside(mouseX, mouseY, tx, py + 3, tw - 2, 11);
         ctx.fill(tx, py + 3, tx + tw - 2, py + 14, i == tab ? 0xFF7A4CE0 : (hov ? 0xFF2E3046 : 0xFF22243A));
         ctx.centeredText(font, TABS[i], tx + (tw - 2) / 2, py + 5, i == tab ? -1 : 0xFFA8ACC8);
         int t = i;
         HITS.add(new Hit(tx, py + 3, tw - 2, 11, () -> tab = t));
      }

      int gy = py + 17;
      String tip = null;
      if (tab == 0 || tab == 1) {
         int n = tab == 0 ? ChatEmojis.NAMES.length : ChatEmojis.SYMBOLS.length;

         for (int i = 0; i < n; i++) {
            int cx = px + 4 + i % COLS * CELL;
            int cy = gy + i / COLS * CELL;
            boolean hov = inside(mouseX, mouseY, cx, cy, CELL - 1, CELL - 1);
            if (hov) {
               ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF34365A);
            }

            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + CELL / 2.0F, cy + CELL / 2.0F);
            if (tab == 0) {
               ctx.pose().scale(1.5F, 1.5F);
               ctx.text(font, ChatEmojis.icon(i), -4, -4, -1, false);
            } else {
               String sym = ChatEmojis.SYMBOLS[i];
               ctx.pose().scale(1.25F, 1.25F);
               ctx.text(font, sym, -font.width(sym) / 2, -4, -1, false);
            }

            ctx.pose().popMatrix();
            int idx = i;
            if (tab == 0) {
               HITS.add(new Hit(cx, cy, CELL - 1, CELL - 1, () -> insert(screen, ChatEmojis.token(idx))));
               if (hov) {
                  tip = ChatEmojis.token(i);
               }
            } else {
               HITS.add(new Hit(cx, cy, CELL - 1, CELL - 1, () -> insert(screen, ChatEmojis.SYMBOLS[idx])));
            }
         }
      } else {
         int cw = (pw - 10) / 2;

         for (int i = 0; i < ChatEmojis.EMOTICONS.length; i++) {
            int cx = px + 4 + i % 2 * (cw + 2);
            int cy = gy + i / 2 * 14;
            boolean hov = inside(mouseX, mouseY, cx, cy, cw, 13);
            ctx.fill(cx, cy, cx + cw, cy + 13, hov ? 0xFF34365A : 0xFF1E2032);
            String e = ChatEmojis.EMOTICONS[i];
            ctx.text(font, font.plainSubstrByWidth(e, cw - 6), cx + 3, cy + 3, -1, false);
            int idx = i;
            HITS.add(new Hit(cx, cy, cw, 13, () -> insert(screen, ChatEmojis.EMOTICONS[idx])));
         }
      }

      String hint = tab == 0 ? "Bilder sehen nur Meowl-Spieler, andere sehen :name:" : "Sieht jeder Spieler";
      ctx.text(font, font.plainSubstrByWidth(hint, pw - 8), px + 4, py + ph - 11, 0xFF8A8EA8, false);
      if (tip != null) {
         ctx.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
      }
   }

   /** Klick in der Chat-Zeile; true = vom Emoji-Knopf/der Auswahl verbraucht. */
   public static boolean click(double x, double y) {
      for (int i = HITS.size() - 1; i >= 0; i--) {
         Hit hit = HITS.get(i);
         if (inside(x, y, hit.x(), hit.y(), hit.w(), hit.h())) {
            hit.action().run();
            return true;
         }
      }

      if (open) {
         open = false;
         return false;
      }

      return false;
   }

   public static boolean isOpen() {
      return open;
   }

   public static void close() {
      open = false;
   }
}
