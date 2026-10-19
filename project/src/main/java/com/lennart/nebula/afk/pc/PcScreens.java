package com.lennart.nebula.afk.pc;

import com.lennart.nebula.afk.space.Tex;

/** Bildschirm-Inhalte im Pixel-Stil: Discord (links) und Spotify (rechts) mit Minecraft-Schrift. */
public final class PcScreens {
   public static final int SW = 320;
   public static final int SH = 184;

   private PcScreens() {
   }

   private static void rect(Tex t, int x0, int y0, int x1, int y1, int c) {
      for (int y = Math.max(0, y0); y < Math.min(t.h, y1); y++) {
         for (int x = Math.max(0, x0); x < Math.min(t.w, x1); x++) {
            t.px[y * t.w + x] = c;
         }
      }
   }

   private static void circle(Tex t, int cx, int cy, int r, int c) {
      for (int y = -r; y <= r; y++) {
         for (int x = -r; x <= r; x++) {
            if (x * x + y * y <= r * r + r) {
               t.set(cx + x, cy + y, c);
            }
         }
      }
   }

   public static Tex discord(PcFont f) {
      Tex t = new Tex(SW, SH);
      rect(t, 0, 0, SW, SH, 0xFF313338);
      // Serverleiste
      rect(t, 0, 0, 26, SH, 0xFF1E1F22);
      int[] servers = {0xFF5865F2, 0xFF3BA55C, 0xFFED4245, 0xFFFAA61A, 0xFFB57BFF, 0xFF3A7BFF};

      for (int i = 0; i < servers.length; i++) {
         circle(t, 13, 14 + i * 24, 9, servers[i]);
      }

      rect(t, 3, 6, 5, 22, 0xFFFFFFFF);
      // Kanäle
      rect(t, 26, 0, 104, SH, 0xFF2B2D31);
      f.draw(t, "Meowl Server", 32, 6, 1, 0xFFF2F3F5, false);
      rect(t, 26, 17, 104, 18, 0xFF1E1F22);
      String[] ch = {"# allgemein", "# clips", "# minecraft", "# memes", "> Zocken", "> Chillen"};

      for (int i = 0; i < ch.length; i++) {
         int y = 24 + i * 13;
         if (i == 0) {
            rect(t, 29, y - 2, 101, y + 10, 0xFF404249);
         }

         f.draw(t, ch[i], 33, y, 1, i == 0 ? 0xFFFFFFFF : 0xFF949BA4, false);
      }

      // Sprachkanal mit Leuten
      String[] voice = {"Meowl", "Luna", "Rex"};

      for (int i = 0; i < voice.length; i++) {
         int y = 104 + i * 12;
         circle(t, 40, y + 4, 4, i == 0 ? 0xFFB57BFF : (i == 1 ? 0xFF3BA55C : 0xFFFAA61A));
         f.draw(t, voice[i], 48, y, 1, 0xFF949BA4, false);
      }

      // Nutzerleiste unten
      rect(t, 26, SH - 18, 104, SH, 0xFF232428);
      circle(t, 36, SH - 9, 6, 0xFFB57BFF);
      f.draw(t, "Du", 46, SH - 13, 1, 0xFFFFFFFF, false);
      // Chat
      f.draw(t, "# allgemein", 112, 6, 1, 0xFFFFFFFF, false);
      rect(t, 104, 17, SW, 18, 0xFF1E1F22);
      String[][] msgs = {
         {"Luna", "wer ist heute on?"},
         {"Rex", "gleich ranked?"},
         {"Meowl", "bin afk, gleich da"},
         {"Kiki", "schaut euch den clip an"},
         {"Rex", "1v4 clutch lol"},
         {"Luna", "gg wp!"}
      };
      int[] colors = {0xFF3BA55C, 0xFFFAA61A, 0xFFB57BFF, 0xFFED4245, 0xFFFAA61A, 0xFF3BA55C};

      for (int i = 0; i < msgs.length; i++) {
         int y = 24 + i * 24;
         circle(t, 118, y + 7, 7, colors[i]);
         f.draw(t, msgs[i][0], 130, y, 1, colors[i], false);
         f.draw(t, msgs[i][1], 130, y + 10, 1, 0xFFDBDEE1, false);
      }

      rect(t, 110, SH - 18, SW - 6, SH - 5, 0xFF383A40);
      f.draw(t, "Nachricht an #allgemein", 115, SH - 15, 1, 0xFF6D6F78, false);
      return t;
   }

   /**
    * @param title  laufender Song (null = nichts)
    * @param cover  Cover-Pixel 64 x 64 oder null
    */
   public static Tex spotify(PcFont f, String title, String artist, boolean playing, int[] cover) {
      Tex t = new Tex(SW, SH);
      rect(t, 0, 0, SW, SH, 0xFF121212);
      // Seitenleiste
      rect(t, 0, 0, 74, SH, 0xFF000000);
      circle(t, 12, 11, 6, 0xFF1DB954);
      rect(t, 9, 9, 16, 10, 0xFF000000);
      rect(t, 9, 12, 15, 13, 0xFF000000);
      f.draw(t, "Spotify", 22, 7, 1, 0xFFFFFFFF, false);
      String[] side = {"Start", "Suche", "Bibliothek"};

      for (int i = 0; i < side.length; i++) {
         f.draw(t, side[i], 10, 26 + i * 12, 1, i == 0 ? 0xFFFFFFFF : 0xFFB3B3B3, false);
      }

      for (int i = 0; i < 5; i++) {
         rect(t, 8, 70 + i * 18, 22, 84 + i * 18, 0xFF282828 + i * 0x00080808);
         rect(t, 26, 73 + i * 18, 64, 76 + i * 18, 0xFF3E3E3E);
      }

      // großes Cover und Titel
      int cx = 84;
      int cy = 12;
      int cs = 62;
      rect(t, 74, 0, SW, 84, 0xFF1F3A2A);
      if (cover != null && cover.length == 64 * 64) {
         for (int y = 0; y < cs; y++) {
            for (int x = 0; x < cs; x++) {
               t.set(cx + x, cy + y, cover[y * 64 / cs * 64 + x * 64 / cs] | 0xFF000000);
            }
         }
      } else {
         for (int y = 0; y < cs; y++) {
            for (int x = 0; x < cs; x++) {
               t.set(cx + x, cy + y, Tex.mix(0xFF1DB954, 0xFF0B3D20, (x + y) / (2.0F * cs)));
            }
         }

         f.draw(t, "#", cx + cs / 2 - 6, cy + cs / 2 - 8, 2, 0xFFFFFFFF, false);
      }

      int tx = cx + cs + 8;
      String head = title == null ? "Nichts laeuft" : title;
      String sub = title == null ? "Spotify starten" : artist;
      f.draw(t, playing ? "WIRD GESPIELT" : (title == null ? "SPOTIFY" : "PAUSIERT"), tx, cy + 6, 1, 0xFFB3B3B3, false);
      f.draw(t, f.fit(head, 2, SW - tx - 6), tx, cy + 20, 2, 0xFFFFFFFF, true);
      f.draw(t, f.fit(sub == null ? "" : sub, 1, SW - tx - 6), tx, cy + 42, 1, 0xFFD0D0D0, false);
      // Liste
      String[] q = {"Better Days", "Night Drive", "Lofi Beats", "Sunset Run", "Blocky Dreams"};

      for (int i = 0; i < q.length; i++) {
         int y = 92 + i * 15;
         f.draw(t, String.valueOf(i + 1), 82, y + 3, 1, 0xFFB3B3B3, false);
         rect(t, 92, y, 104, y + 12, 0xFF000000 | (0x203040 + i * 0x101808));
         f.draw(t, q[i], 110, y + 3, 1, i == 0 && title == null ? 0xFF1DB954 : 0xFFFFFFFF, false);
         f.draw(t, (2 + i) + ":" + (10 + i * 7), SW - 30, y + 3, 1, 0xFFB3B3B3, false);
      }

      // Wiedergabeleiste
      rect(t, 74, SH - 16, SW, SH, 0xFF181818);
      circle(t, 197, SH - 8, 5, 0xFFFFFFFF);
      if (playing) {
         rect(t, 195, SH - 10, 196, SH - 6, 0xFF000000);
         rect(t, 198, SH - 10, 199, SH - 6, 0xFF000000);
      } else {
         rect(t, 196, SH - 10, 199, SH - 6, 0xFF000000);
      }

      rect(t, 150, SH - 3, 244, SH - 2, 0xFF4D4D4D);
      return t;
   }
}
