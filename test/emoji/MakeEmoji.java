import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Erzeugt die Meowl-Emoji-Schrift (16x16-Pixel-Art) für den Chat:
 * java test\emoji\MakeEmoji.java project\src\main\resources\assets\nebula
 * Schreibt textures/font/emoji.png und font/emoji.json. Reihenfolge = ChatEmojis.NAMES.
 */
public class MakeEmoji {
   static final int S = 16;
   static final List<String> names = new ArrayList<>();
   static final List<int[]> images = new ArrayList<>();

   static int[] img() { return new int[S * S]; }
   static void px(int[] a, int x, int y, int c) { if (x >= 0 && y >= 0 && x < S && y < S) a[y * S + x] = c; }

   /** Pixel-Karte (16 Zeilen à 16 Zeichen), Farben je Buchstabe. */
   static int[] map(String name, Map<Character, Integer> pal, String... rows) {
      int[] a = img();
      if (rows.length > S) throw new IllegalStateException(name + ": zu viele Zeilen");
      int off = (S - rows.length) / 2;
      for (int y = 0; y < rows.length; y++) {
         String r = rows[y];
         if (r.length() != S) throw new IllegalStateException(name + " Zeile " + y + " hat " + r.length() + " Zeichen: " + r);
         for (int x = 0; x < S; x++) {
            char ch = r.charAt(x);
            if (ch != '.') {
               Integer c = pal.get(ch);
               if (c == null) throw new IllegalStateException(name + ": Farbe " + ch);
               px(a, x, y + off, c);
            }
         }
      }
      return a;
   }

   static Map<Character, Integer> pal(Object... kv) {
      Map<Character, Integer> m = new HashMap<>();
      for (int i = 0; i < kv.length; i += 2) m.put((Character)kv[i], 0xFF000000 | (Integer)kv[i + 1]);
      return m;
   }

   static void add(String name, int[] a) { names.add(name); images.add(a); }

   // ---------------------------------------------------------------- Gesichter
   static int[] face(int fill, int shade, int outline) {
      int[] a = img();
      for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
         double dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.sqrt(dx * dx + dy * dy);
         if (d <= 7.6) {
            int c = d > 6.6 ? outline : (dx + dy > 6.5 ? shade : fill);
            px(a, x, y, c);
         }
      }
      px(a, 5, 3, 0xFFFFF4C0); px(a, 4, 4, 0xFFFFF4C0);
      return a;
   }

   static final int Y = 0xFFFFCC33, YS = 0xFFF0A020, YO = 0xFF8A5A00, D = 0xFF3A2200, RED = 0xFFE83040, BLUE = 0xFF5AB4FF, WHITE = 0xFFFFFFFF;

   static void dots(int[] a, int... xy) { for (int i = 0; i < xy.length; i += 2) px(a, xy[i], xy[i + 1], D); }
   static void col(int[] a, int c, int... xy) { for (int i = 0; i < xy.length; i += 2) px(a, xy[i], xy[i + 1], c); }

   static void smile(int[] a) { dots(a, 4, 10, 5, 11, 6, 11, 7, 11, 8, 11, 9, 11, 10, 11, 11, 10); }
   static void eyes(int[] a) { dots(a, 5, 6, 5, 7, 10, 6, 10, 7); }

   public static void main(String[] args) throws Exception {
      File base = new File(args[0]);
      int[] f;
      // :) lächeln
      f = face(Y, YS, YO); eyes(f); smile(f); add("smile", f);
      // lachen mit Tränen
      f = face(Y, YS, YO); dots(f, 3, 7, 4, 6, 5, 6, 6, 7, 9, 7, 10, 6, 11, 6, 12, 7);
      for (int x = 4; x <= 11; x++) for (int y = 9; y <= 12; y++) if (!(y == 12 && (x == 4 || x == 11))) px(f, x, y, D);
      for (int x = 5; x <= 10; x++) px(f, x, 9, WHITE);
      for (int x = 6; x <= 9; x++) px(f, x, 12, 0xFFE8506A);
      col(f, BLUE, 2, 8, 2, 9, 1, 10, 13, 8, 13, 9, 14, 10); add("lol", f);
      // zwinkern
      f = face(Y, YS, YO); dots(f, 5, 6, 5, 7, 9, 7, 10, 7, 11, 7); smile(f); add("wink", f);
      // Sonnenbrille
      f = face(Y, YS, YO);
      for (int x = 2; x <= 13; x++) px(f, x, 6, D);
      for (int x = 3; x <= 7; x++) for (int y = 7; y <= 8; y++) px(f, x, y, D);
      for (int x = 9; x <= 12; x++) for (int y = 7; y <= 8; y++) px(f, x, y, D);
      col(f, 0xFF5A5A6A, 4, 7, 10, 7);
      dots(f, 6, 11, 7, 11, 8, 11, 9, 10, 10, 10); add("cool", f);
      // weinen
      f = face(Y, YS, YO); dots(f, 4, 6, 5, 7, 6, 6, 9, 6, 10, 7, 11, 6, 5, 11, 6, 10, 7, 10, 8, 10, 9, 10, 10, 11);
      for (int y = 8; y <= 13; y++) { px(f, 5, y, BLUE); px(f, 10, y, BLUE); }
      add("cry", f);
      // wütend
      f = face(0xFFF0583A, 0xFFC8381E, 0xFF6A1000);
      dots(f, 3, 4, 4, 5, 5, 6, 12, 4, 11, 5, 10, 6, 5, 7, 10, 7, 5, 11, 6, 10, 7, 10, 8, 10, 9, 10, 10, 11); add("angry", f);
      // verliebt
      f = face(Y, YS, YO);
      for (int[] h : new int[][]{{3, 5}, {9, 5}}) {
         int x = h[0], y = h[1];
         col(f, RED, x, y, x + 2, y, x, y + 1, x + 1, y + 1, x + 2, y + 1, x + 3, y + 1, x - 1 + 1, y + 2, x + 1, y + 2, x + 2, y + 2, x + 1, y + 3, x + 3, y);
         px(f, x + 1, y, Y);
      }
      smile(f); add("love", f);
      // erschrocken
      f = face(Y, YS, YO); col(f, WHITE, 4, 5, 5, 5, 4, 6, 10, 5, 11, 5, 11, 6);
      dots(f, 5, 6, 4, 7, 5, 7, 10, 6, 10, 7, 11, 7, 7, 9, 8, 9, 6, 10, 9, 10, 6, 11, 9, 11, 7, 12, 8, 12); add("shock", f);
      // Clown
      f = face(0xFFF8F6F0, 0xFFD8D4CC, 0xFF6A6460);
      col(f, 0xFFFF7A1E, 1, 4, 2, 3, 1, 5, 0, 6, 14, 4, 13, 3, 14, 5, 15, 6, 1, 7, 14, 7);
      col(f, 0xFF3A6AE8, 5, 5, 5, 8, 10, 5, 10, 8);
      dots(f, 5, 6, 5, 7, 10, 6, 10, 7);
      col(f, RED, 7, 8, 8, 8, 7, 9, 8, 9, 4, 10, 5, 11, 6, 12, 7, 12, 8, 12, 9, 12, 10, 11, 11, 10);
      add("clown", f);

      // ---------------------------------------------------------------- Bilder
      add("heart", map("heart", pal('K', 0x5A0A12, 'R', 0xE8202C, 'W', 0xFF9AA4, 'D', 0xB01020),
         "................",
         "..KKKK....KKKK..",
         ".KRRRRK..KRRRRK.",
         "KRRWWRRKKRRRRRRK",
         "KRWWRRRRRRRRRRRK",
         "KRWRRRRRRRRRRRRK",
         "KRRRRRRRRRRRRRDK",
         ".KRRRRRRRRRRRDK.",
         "..KRRRRRRRRRDK..",
         "...KRRRRRRRDK...",
         "....KRRRRRDK....",
         ".....KRRRDK.....",
         "......KRDK......",
         ".......KK.......",
         "................"));
      add("fire", map("fire", pal('K', 0x7A1E08, 'O', 0xFF8A1E, 'R', 0xE8401C, 'Y', 0xFFD23A, 'W', 0xFFF6C0),
         "......K.........",
         ".....KOK........",
         ".....KOOK...K...",
         "....KOOOK..KOK..",
         "...KOOROOK.KOK..",
         "..KOORROOOKKOOK.",
         "..KORRYROOOOOOK.",
         ".KORRYYRROOROOK.",
         ".KORYYYYRRRROOK.",
         ".KORYYWYYRYRROK.",
         ".KORYWWWYYYRROK.",
         ".KORYWWWWYYRROK.",
         "..KORYWWWYYROK..",
         "...KORYYYYROK...",
         "....KKORRRKK....",
         "......KKKK......"));
      add("skull", map("skull", pal('K', 0x2A2A2A, 'W', 0xF2F0E6, 'G', 0xC8C4B8),
         "....KKKKKKKK....",
         "...KWWWWWWWWK...",
         "..KWWWWWWWWWWK..",
         ".KWWWWWWWWWWWWK.",
         ".KWWKKKWWKKKWWK.",
         ".KWKKKKWWKKKKWK.",
         ".KWKKKKWWKKKKWK.",
         ".KWWKKWWWWKKWWK.",
         ".KWWWWWKKWWWWGK.",
         "..KWWWWKKWWWGK..",
         "...KWWWWWWWGK...",
         "...KWKWKWKWGK...",
         "...KWKWKWKWGK...",
         "....KKKKKKKK...."));
      add("like", map("like", pal('K', 0x8A5A00, 'Y', 0xFFCC33, 'B', 0x3A6AD8),
         "......KK........",
         ".....KYYK.......",
         ".....KYYK.......",
         "....KYYYK.......",
         "....KYYK........",
         "...KYYYKKKKKK...",
         ".KKKYYYYYYYYYK..",
         ".KBKYYYYYKKKKK..",
         ".KBKYYYYYYYYYK..",
         ".KBKYYYYYKKKK...",
         ".KBKYYYYYYYYK...",
         ".KBKYYYYYKKK....",
         ".KBKYYYYYYYK....",
         ".KKKKKKKKKKK...."));
      add("star", map("star", pal('K', 0xA86A00, 'Y', 0xFFD43A, 'W', 0xFFF8D0),
         ".......KK.......",
         "......KYYK......",
         "......KYYK......",
         ".....KYYYYK.....",
         "KKKKKKYYYYKKKKKK",
         ".KYYYYYWYYYYYYK.",
         "..KYYYWWYYYYYK..",
         "...KYYYYYYYYK...",
         "....KYYYYYYK....",
         "...KYYYYYYYYK...",
         "...KYYYKKYYYK...",
         "..KYYYK..KYYYK..",
         "..KYYK....KYYK..",
         ".KYK........KYK.",
         ".KK..........KK."));
      add("crown", map("crown", pal('K', 0x8A5200, 'Y', 0xFFC83A, 'O', 0xE09A20, 'R', 0xE83040, 'B', 0x3A8AF0),
         "..K....KK....K..",
         ".KYK..KYYK..KYK.",
         ".KYK..KYYK..KYK.",
         ".KYYK.KYYK.KYYK.",
         ".KYYYKYYYYKYYYK.",
         ".KYYYYYYYYYYYYK.",
         ".KYYRYYYBYYYRYK.",
         ".KYYYYYYYYYYYYK.",
         ".KOOOOOOOOOOOOK.",
         ".KYYYYYYYYYYYYK.",
         ".KKKKKKKKKKKKKK."));
      add("100", map("100", pal('R', 0xE8202C),
         ".R..RRRR..RRRR..",
         "RR.RR..RRRR..RR.",
         ".R.RR..RRRR..RR.",
         ".R.RR..RRRR..RR.",
         ".R.RR..RRRR..RR.",
         ".R.RR..RRRR..RR.",
         ".R..RRRR..RRRR..",
         "................",
         "RRRRRRRRRRRRRRR.",
         "................",
         ".RRRRRRRRRRRRRR."));
      add("gg", map("gg", pal('K', 0x0A0E14, 'D', 0x1E2430, 'G', 0x5CE87A),
         ".KKKKKKKKKKKKKK.",
         "KDDDDDDDDDDDDDDK",
         "KDDGGGGDDGGGGDDK",
         "KDDGDDDDDGDDDDDK",
         "KDDGDGGDDGDGGDDK",
         "KDDGDDGDDGDDGDDK",
         "KDDGGGGDDGGGGDDK",
         "KDDDDDDDDDDDDDDK",
         ".KKKKKKKKKKKKKK."));
      add("eyes", map("eyes", pal('K', 0x1A1A1A, 'W', 0xF8F8F8),
         "..KKKK....KKKK..",
         ".KWWWWK..KWWWWK.",
         "KWWWWWWKKWWWWWWK",
         "KWWWKKWKKWWWKKWK",
         "KWWWKKWKKWWWKKWK",
         "KWWWWWWKKWWWWWWK",
         "KWWWWWWKKWWWWWWK",
         ".KWWWWK..KWWWWK.",
         "..KKKK....KKKK.."));
      add("ghost", map("ghost", pal('K', 0x3A3A5A, 'W', 0xF4F6FF),
         ".....KKKKKK.....",
         "....KWWWWWWK....",
         "...KWWWWWWWWK...",
         "..KWWWWWWWWWWK..",
         "..KWWKKWWKKWWK..",
         "..KWWKKWWKKWWK..",
         "..KWWWWWWWWWWK..",
         "..KWWWWKKWWWWK..",
         "..KWWWWKKWWWWK..",
         "..KWWWWWWWWWWK..",
         "..KWWWWWWWWWWK..",
         "..KWWWWWWWWWWK..",
         "..KWWKWWKWWKWK..",
         "..KWK.KK.KK.KK.."));
      // Creeper: klassisches 8x8-Gesicht, doppelt groß
      String[] cr = {"GGLGGGLG", "GLGGGGGG", "GKKGGKKG", "GKKGGKKG", "LGGKKGGL", "GGKKKKGG", "GGKKKKGL", "GLKGGKGG"};
      f = img();
      Map<Character, Integer> cp = pal('G', 0x4CB848, 'L', 0x7AD86A, 'K', 0x101810);
      for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
         int c = cp.get(cr[y].charAt(x));
         px(f, x * 2, y * 2, c); px(f, x * 2 + 1, y * 2, c); px(f, x * 2, y * 2 + 1, c); px(f, x * 2 + 1, y * 2 + 1, c);
      }
      add("creeper", f);
      add("diamond", map("diamond", pal('K', 0x0E5A6A, 'C', 0x4AE0E8, 'L', 0xA8F4F8, 'W', 0xFFFFFF, 'D', 0x2AA8C0),
         "....KKKKKKKK....",
         "...KWLLCCLLWK...",
         "..KWLLCCCCLLWK..",
         ".KLLLCCCCCCLLLK.",
         ".KDDDDDDDDDDDDK.",
         "..KLCCCCCCCCLK..",
         "...KLCCCCCCLK...",
         "....KLCCCCLK....",
         ".....KLCCLK.....",
         "......KLLK......",
         ".......KK......."));
      // Schwert: diagonal, Griff unten links
      f = img();
      for (int i = 0; i < 9; i++) {
         int x = 5 + i, y = 10 - i;
         px(f, x, y, 0xFFE8F0F8); px(f, x + 1, y, 0xFFA8B8C8); px(f, x, y - 1, 0xFF3A4A5A); px(f, x + 1, y + 1, 0xFF3A4A5A);
      }
      px(f, 14, 1, 0xFF3A4A5A); px(f, 14, 2, 0xFF3A4A5A);
      for (int i = 0; i < 5; i++) px(f, 2 + i, 8 + i, 0xFFC89A2A);
      px(f, 3, 8, 0xFF7A5A10); px(f, 7, 12, 0xFF7A5A10);
      for (int i = 0; i < 3; i++) { px(f, 4 - i, 11 + i, 0xFF6A3A1A); px(f, 3 - i, 11 + i, 0xFF8A5A2A); }
      px(f, 1, 14, 0xFFC89A2A); px(f, 0, 15, 0xFFC89A2A);
      add("sword", f);
      // Party: Tüte mit Konfetti
      f = img();
      for (int y = 6; y < 16; y++) for (int x = 0; x <= (y - 6); x++) {
         int xx = x, yy = y;
         px(f, xx, yy, (x + y) % 4 < 2 ? 0xFFFFC83A : 0xFFE8402C);
      }
      int[][] conf = {{9, 2, 0xFF5AB4FF}, {12, 4, 0xFFE8402C}, {14, 1, 0xFF5CE87A}, {11, 7, 0xFFFFC83A}, {6, 1, 0xFFB45AFF}, {14, 8, 0xFFFF7AC0}, {8, 5, 0xFF5CE87A}, {13, 11, 0xFF5AB4FF}, {10, 10, 0xFFB45AFF}};
      for (int[] c : conf) { px(f, c[0], c[1], c[2]); px(f, c[0] + 1, c[1], c[2]); }
      add("party", f);
      add("sus", map("sus", pal('K', 0x2A0A0A, 'R', 0xE02A2A, 'S', 0xA01A1A, 'C', 0x9AD8F0, 'W', 0xFFFFFF),
         ".....KKKKKK.....",
         "....KRRRRRRK....",
         "...KRRRRRRRRK...",
         "...KRRKKKKKKKK..",
         "..KKRKCCCCCCCK..",
         ".KRKRKCWWCCCCK..",
         ".KRKRKCCCCCCCK..",
         ".KRKRRKKKKKKK...",
         ".KRKRRRRRRRRK...",
         ".KSKRRRRRRRSK...",
         ".KKKRRRRRRRSK...",
         "...KRRRKKRRSK...",
         "...KRRSKKRRSK...",
         "...KKKKK.KKKK..."));
      add("meowl", map("meowl", pal('K', 0x2A1450, 'P', 0x9A6CFF, 'D', 0x7A4CE0, 'L', 0xFF9AD0, 'W', 0xFFFFFF),
         ".KK..........KK.",
         ".KPK........KPK.",
         ".KPPK......KPPK.",
         ".KPPPKKKKKKPPPK.",
         ".KPPPPPPPPPPPPK.",
         "KPPPPPPPPPPPPPPK",
         "KPPWWKPPPPWWKPPK",
         "KPPWKKPPPPWKKPPK",
         "KPPPPPPPPPPPPPPK",
         "KPLLPPPKKPPPLLPK",
         "KPPPPPKPPKPPPPDK",
         ".KPPPPPPPPPPPDK.",
         "..KDPPPPPPPPDK..",
         "...KKKKKKKKKK..."));

      // ---------------------------------------------------------------- Ausgabe
      int perRow = 16;
      int rows = (images.size() + perRow - 1) / perRow;
      BufferedImage out = new BufferedImage(perRow * S, rows * S, BufferedImage.TYPE_INT_ARGB);
      StringBuilder json = new StringBuilder("{\n  \"providers\": [\n    {\n      \"type\": \"bitmap\",\n      \"file\": \"nebula:font/emoji.png\",\n      \"ascent\": 8,\n      \"height\": 9,\n      \"chars\": [\n");
      for (int r = 0; r < rows; r++) {
         StringBuilder line = new StringBuilder();
         for (int c = 0; c < perRow; c++) {
            int i = r * perRow + c;
            if (i < images.size()) {
               int[] a = images.get(i);
               for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) out.setRGB(c * S + x, r * S + y, a[y * S + x]);
               line.append(String.format("\\u%04X", 0xE900 + i));
            } else {
               line.append("\\u0000");
            }
         }
         json.append("        \"").append(line).append(r < rows - 1 ? "\",\n" : "\"\n");
      }
      json.append("      ]\n    }\n  ]\n}\n");
      new File(base, "textures/font").mkdirs();
      new File(base, "font").mkdirs();
      ImageIO.write(out, "png", new File(base, "textures/font/emoji.png"));
      java.nio.file.Files.writeString(new File(base, "font/emoji.json").toPath(), json.toString());
      System.out.println(images.size() + " Emojis: " + String.join(",", names));
   }
}
