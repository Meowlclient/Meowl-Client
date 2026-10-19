package com.lennart.nebula.afk.terra;

import static com.lennart.nebula.afk.terra.TerraTiles.*;

import com.lennart.nebula.afk.space.Noise;
import java.util.ArrayList;
import java.util.List;

/**
 * Endlose 2D-Welt im Terraria-Aufbau aus Minecraft-Blöcken, in zwei Ebenen: vorne der begehbare Boden mit Höhlen, Erzen,
 * Seen und Lava, dahinter Wände, Bäume und Häuser. Spalten werden beim Laufen nach rechts erzeugt (Ringpuffer), alles
 * aus dem Seed bestimmt. Reines Java.
 */
public final class TerraWorld {
   public static final int WH = 100;
   public static final int RING = 384;
   public static final int FOREST = 0;
   public static final int DESERT = 1;
   public static final int BIRCH = 2;
   public static final int SNOW = 3;
   public static final int JUNGLE = 4;
   public static final int CRIMSON = 5;
   public static final int BIOMES = 6;
   private static final int ZONE = 230;
   public static final int HOUSE_W = 13;
   private final Noise noise;
   private final long seed;
   private final byte[] front = new byte[RING * WH];
   private final byte[] back = new byte[RING * WH];
   private final byte[] deco = new byte[RING * WH];
   private final int[] colX = new int[RING];
   private final int[] surf = new int[RING];
   private final byte[] biomeOf = new byte[RING];
   /** Häuser mit Möbeln (werden beim Erzeugen ihrer ersten Spalte angelegt). */
   public final List<House> houses = new ArrayList<>();

   public static final class House {
      public final int x0;
      public final int floor;
      public final int biome;
      public final int skin;
      public final List<int[]> props = new ArrayList<>();
      public boolean openLeft;
      public boolean openRight;
      public float doorAnimL;
      public float doorAnimR;

      House(int x0, int floor, int biome, int skin) {
         this.x0 = x0;
         this.floor = floor;
         this.biome = biome;
         this.skin = skin;
      }
   }

   /** Möbelarten (props: {Art, x, y}). */
   public static final int P_TABLE = 0;
   public static final int P_BARREL = 1;
   public static final int P_FURNACE = 2;
   public static final int P_BOOKS = 3;
   public static final int P_LANTERN = 4;

   public TerraWorld(long seed) {
      this.seed = seed;
      this.noise = new Noise(seed);
      java.util.Arrays.fill(this.colX, Integer.MIN_VALUE);
   }

   // ================================================================== Zugriff

   private static int slot(int x) {
      return Math.floorMod(x, RING);
   }

   private int idx(int x, int y) {
      int s = slot(x);
      if (this.colX[s] != x) {
         this.generate(x);
      }

      return s * WH + y;
   }

   public int tile(int x, int y) {
      return y < 0 ? BEDROCK : (y >= WH ? AIR : this.front[this.idx(x, y)]);
   }

   public int backTile(int x, int y) {
      return y < 0 ? BEDROCK : (y >= WH ? AIR : this.back[this.idx(x, y)]);
   }

   public int deco(int x, int y) {
      return y < 0 || y >= WH ? D_NONE : this.deco[this.idx(x, y)];
   }

   public void set(int x, int y, int t) {
      if (y >= 0 && y < WH) {
         this.front[this.idx(x, y)] = (byte)t;
      }
   }

   public void setDeco(int x, int y, int d) {
      if (y >= 0 && y < WH) {
         this.deco[this.idx(x, y)] = (byte)d;
      }
   }

   public boolean solid(int x, int y) {
      return TerraTiles.solid(this.tile(x, y));
   }

   /** Natürliche Oberfläche (erste Luftzeile) einer Spalte. */
   public int surface(int x) {
      this.idx(x, 0);
      return this.surf[slot(x)];
   }

   public int biome(int x) {
      this.idx(x, 0);
      return this.biomeOf[slot(x)];
   }

   /** Höchster fester Block + 1 (Stand-Höhe), ab einer Starthöhe nach unten gesucht. */
   public int groundBelow(int x, int fromY) {
      for (int y = Math.min(WH - 1, fromY); y >= 0; y--) {
         if (this.solid(x, y)) {
            return y + 1;
         }
      }

      return 0;
   }

   // ================================================================== Erzeugung

   private float n1(float x, int ch) {
      return this.noise.noise(x, ch * 17.31F, 3.7F);
   }

   private float n2(float x, float y, int ch) {
      return this.noise.noise(x, y, ch * 13.7F + 0.5F);
   }

   private float rnd(int x, int y, int ch) {
      return this.noise.rand(x, y, ch + 1000);
   }

   /** Biom einer Spalte (Zonen mit leicht welligen Grenzen). */
   public int biomeAt(int x) {
      int jx = x + Math.round((this.n1(x * 0.05F, 1) - 0.5F) * 16.0F);
      int zone = Math.floorDiv(jx + 60, ZONE);
      return Math.floorMod(zone, BIOMES);
   }

   private static float amp(int b) {
      return switch (b) {
         case DESERT -> 4.0F;
         case SNOW -> 13.0F;
         case JUNGLE -> 9.0F;
         case CRIMSON -> 11.0F;
         case BIRCH -> 6.5F;
         default -> 7.5F;
      };
   }

   private float rawHeight(int x) {
      // Hügel-Stärke weich zwischen Biomen überblenden
      float a = 0.0F;
      int n = 0;

      for (int k = -24; k <= 24; k += 8) {
         a += amp(this.biomeAt(x + k));
         n++;
      }

      a /= n;
      float h = 62.0F + (this.noise.fbm(x * 0.014F, 0.5F, 1.0F, 3) - 0.5F) * 2.6F * a + (this.n1(x * 0.11F, 2) - 0.5F) * 2.2F;
      return h;
   }

   /** Haus der Zone (x0) oder MIN_VALUE, wenn das Biom keins hat. */
   private int houseX(int zone) {
      int zStart = zone * ZONE - 60;
      int x0 = zone == 0 ? 44 : zStart + 80 + (int)(this.rnd(zone, 0, 5) * 60.0F);
      int b = this.biomeAt(x0 + 6);
      return b == CRIMSON || b == DESERT && this.rnd(zone, 1, 5) < 0.5F ? Integer.MIN_VALUE : x0;
   }

   private int houseFor(int x) {
      int zone = Math.floorDiv(x + 60, ZONE);

      for (int z = zone - 1; z <= zone + 1; z++) {
         int hx = this.houseX(z);
         if (hx != Integer.MIN_VALUE && x >= hx - 6 && x <= hx + HOUSE_W + 5) {
            return hx;
         }
      }

      return Integer.MIN_VALUE;
   }

   private int height(int x) {
      int hx = this.houseFor(x);
      float h = this.rawHeight(x);
      if (hx != Integer.MIN_VALUE) {
         float flat = Math.round(this.rawHeight(hx + 6));
         float edge = Math.min(x - (hx - 6), hx + HOUSE_W + 5 - x) / 4.0F;
         h = h + (flat - h) * Math.max(0.0F, Math.min(1.0F, edge));
      }

      return Math.max(30, Math.min(WH - 20, Math.round(h)));
   }

   private void generate(int x) {
      int s = slot(x);
      this.colX[s] = x;
      int base = s * WH;
      int b = this.biomeAt(x);
      int h = this.height(x);
      this.surf[s] = h;
      this.biomeOf[s] = (byte)b;
      int soil = 4 + (int)(this.n1(x * 0.09F, 3) * 4.0F);
      int slate = 26 + (int)(this.n1(x * 0.07F, 4) * 4.0F);

      for (int y = 0; y < WH; y++) {
         byte t;
         if (y == 0 || y == 1 && this.rnd(x, y, 1) < 0.6F) {
            t = BEDROCK;
         } else if (y < h) {
            int d = h - 1 - y;
            t = switch (b) {
               case DESERT -> d < 5 ? SAND : (d < 10 ? SANDSTONE : STONE);
               case SNOW -> d == 0 ? SNOW_GRASS : (d < 3 ? SNOW : (d < soil + 1 ? DIRT : STONE));
               case JUNGLE -> d == 0 ? JUNGLE_GRASS : (d < soil + 4 ? MUD : STONE);
               case CRIMSON -> d == 0 ? CRIMSON_GRASS : (d < soil + 6 ? NETHERRACK : STONE);
               default -> d == 0 ? GRASS : (d < soil ? DIRT : STONE);
            };
            if (t == STONE && y < slate) {
               t = DEEPSLATE;
            }

            if (b == SNOW && t == STONE && this.n2(x * 0.08F, y * 0.1F, 9) > 0.7F) {
               t = ICE;
            }
         } else {
            t = AIR;
         }

         this.back[base + y] = t == GRASS || t == SNOW_GRASS || t == JUNGLE_GRASS || t == CRIMSON_GRASS ? t : (t == ICE ? STONE : t);
         // Erze und Taschen
         if (t == STONE || t == DEEPSLATE) {
            float fx = x * 0.3F;
            float fy = y * 0.3F;
            if (y > 30 && this.n2(fx, fy, 21) > 0.79F) {
               t = COAL;
            } else if (this.n2(fx, fy, 22) > 0.81F) {
               t = IRON;
            } else if (y < 46 && this.n2(fx, fy, 23) > 0.83F) {
               t = GOLD;
            } else if (y < 30 && this.n2(fx, fy, 24) > 0.81F) {
               t = REDSTONE;
            } else if (y < 36 && this.n2(fx, fy, 25) > 0.84F) {
               t = LAPIS;
            } else if (y < 22 && this.n2(fx, fy, 26) > 0.83F) {
               t = DIAMOND;
            } else if (y < 34 && this.n2(x * 0.09F, y * 0.09F, 27) > 0.8F) {
               t = AMETHYST;
            } else if (this.n2(x * 0.2F, y * 0.2F, 28) > 0.82F) {
               t = GRAVEL;
            }
         } else if ((t == DIRT || t == MUD) && this.n2(x * 0.25F, y * 0.25F, 29) > 0.83F) {
            t = t == MUD ? CLAY : GRAVEL;
         }

         this.front[base + y] = t;
         this.deco[base + y] = D_NONE;
      }

      // Höhlen (vorne), große Hallen auch hinten offen
      boolean shrooms = this.n1(x * 0.012F, 6) > 0.62F;

      for (int y = 3; y < h - 5; y++) {
         float tun = this.noise.ridge(x * 0.032F, y * 0.055F, 5.0F, 2);
         float hall = this.noise.fbm(x * 0.028F, y * 0.05F, 7.0F, 3);
         float near = Math.min(1.0F, (h - 5 - y) / 8.0F);
         boolean cave = tun > 0.9F - 0.03F * (1.0F - near) && y < h - 7 || hall > 0.69F - 0.02F * near && y < h - 9;
         if (cave) {
            this.front[base + y] = AIR;
            if (hall > 0.74F) {
               this.back[base + y] = AIR;
            }
         }
      }

      // Flüssigkeiten von unten nach oben (nur auf festem Boden oder anderer Flüssigkeit)
      int lavaLevel = 11 + (int)(this.n1(x * 0.04F, 7) * 5.0F);
      boolean lake = this.n1(x * 0.018F, 8) > 0.6F;
      int waterLevel = 36 + (int)(this.n1(x * 0.01F, 9) * 8.0F);

      for (int y = 2; y < h - 6; y++) {
         if (this.front[base + y] == AIR) {
            byte below = this.front[base + y - 1];
            boolean floor = TerraTiles.solid(below) || TerraTiles.liquid(below);
            if (floor && y <= lavaLevel) {
               this.front[base + y] = LAVA;
            } else if (floor && lake && y > 24 && y <= waterLevel) {
               this.front[base + y] = WATER;
            }
         }
      }

      // Höhlen-Deko und Leuchtpilz-Höhlen
      for (int y = 3; y < h - 5; y++) {
         int i = base + y;
         if (this.front[i] != AIR) {
            continue;
         }

         byte below = this.front[i - 1];
         byte above = this.front[i + 1];
         float r = this.rnd(x, y, 2);
         if (TerraTiles.solid(below)) {
            if (shrooms && y < 48) {
               this.front[i - 1] = MUSHROOM_GRASS;
               if (r < 0.45F) {
                  this.deco[i] = D_GLOW_SHROOM;
               }
            } else if (r < 0.04F && this.back[i] != AIR) {
               this.deco[i] = D_TORCH;
            } else if (r < 0.1F) {
               this.deco[i] = y < 30 ? D_CRYSTAL : (r < 0.07F ? D_RED_MUSH : D_BROWN_MUSH);
            }
         } else if (TerraTiles.solid(above) && r < 0.05F) {
            int len = 1 + (int)(this.rnd(x, y, 3) * 3.0F);

            for (int k = 0; k < len && this.front[i - k] == AIR; k++) {
               this.deco[i - k] = D_VINE;
               if (k == len - 1 || this.front[i - k - 1] != AIR) {
                  this.deco[i - k] = D_VINE_END;
               }
            }
         } else if (TerraTiles.solid(above) && r < 0.14F) {
            this.deco[i] = D_COBWEB;
         }
      }

      // Pflanzen an der Oberfläche
      int top = base + h;
      if (h < WH && this.front[top] == AIR && this.houseFor(x) == Integer.MIN_VALUE) {
         float r = this.rnd(x, h, 4);
         byte dd = switch (b) {
            case DESERT -> r < 0.1F ? D_DEAD_BUSH : D_NONE;
            case SNOW -> r < 0.08F ? D_FERN : D_NONE;
            case JUNGLE -> r < 0.45F ? D_FERN : (r < 0.62F ? D_GRASS : (r < 0.66F ? D_POPPY : D_NONE));
            case CRIMSON -> r < 0.25F ? D_CRIMSON_ROOTS : (r < 0.34F ? D_CRIMSON_FUNGUS : D_NONE);
            default -> r < 0.36F ? D_GRASS : (r < 0.43F ? D_POPPY : (r < 0.49F ? D_DANDELION : (r < 0.53F ? D_CORNFLOWER : (r < 0.56F ? D_BLUET : (r < 0.58F ? D_RED_MUSH : D_NONE)))));
         };
         this.deco[top] = dd;
      }

      this.trees(x, b);
      this.house(x);
   }

   // ------------------------------------------------------------ Bäume (hintere Ebene)

   private void trees(int x, int b) {
      int s0 = Math.floorDiv(x, 5);

      for (int sl = s0 - 2; sl <= s0 + 2; sl++) {
         float chance = switch (this.biomeAt(sl * 5)) {
            case DESERT -> 0.3F;
            case SNOW -> 0.7F;
            case JUNGLE -> 0.95F;
            case CRIMSON -> 0.6F;
            case BIRCH -> 0.75F;
            default -> 0.6F;
         };
         if (this.rnd(sl, 0, 6) >= chance) {
            continue;
         }

         int tx = sl * 5 + (int)(this.rnd(sl, 1, 6) * 3.0F);
         if (Math.abs(tx - x) > 4 || this.houseFor(tx) != Integer.MIN_VALUE) {
            continue;
         }

         int tb = this.biomeAt(tx);
         int gh = tx == x ? this.surf[slot(x)] : this.height(tx);
         float hr = this.rnd(sl, 2, 6);
         byte log;
         byte leaf;
         int height;
         switch (tb) {
            case DESERT -> {
               log = CACTUS;
               leaf = AIR;
               height = 2 + (int)(hr * 3.0F);
            }
            case SNOW -> {
               log = SPRUCE_LOG;
               leaf = SPRUCE_LEAVES;
               height = 7 + (int)(hr * 5.0F);
            }
            case JUNGLE -> {
               log = JUNGLE_LOG;
               leaf = JUNGLE_LEAVES;
               height = 10 + (int)(hr * 7.0F);
            }
            case CRIMSON -> {
               log = CRIMSON_STEM;
               leaf = WART;
               height = 5 + (int)(hr * 4.0F);
            }
            case BIRCH -> {
               log = BIRCH_LOG;
               leaf = BIRCH_LEAVES;
               height = 6 + (int)(hr * 4.0F);
            }
            default -> {
               boolean birch = this.rnd(sl, 3, 6) < 0.2F;
               log = birch ? BIRCH_LOG : OAK_LOG;
               leaf = birch ? BIRCH_LEAVES : OAK_LEAVES;
               height = 5 + (int)(hr * 5.0F);
            }
         }

         int topY = gh + height - 1;
         int base = slot(x) * WH;
         if (x == tx) {
            for (int y = gh; y <= topY && y < WH; y++) {
               if (this.back[base + y] == AIR) {
                  this.back[base + y] = log;
               }
            }
         }

         if (leaf == AIR) {
            continue;
         }

         int dx = x - tx;

         for (int y = gh + 2; y <= topY + 3 && y < WH; y++) {
            int dy = y - topY;
            boolean in;
            if (leaf == SPRUCE_LEAVES) {
               // Kegel mit Stufen: Spitze oben, unten breiter
               int depth = topY + 1 - y;
               int rr = Math.min(3, depth / 2);
               if (depth % 3 == 0) {
                  rr = Math.max(0, rr - 1);
               }

               in = y >= gh + 3 && depth >= 0 && Math.abs(dx) <= rr;
            } else {
               float rad = leaf == JUNGLE_LEAVES ? 3.4F : 2.5F;
               float ry = dy * (leaf == JUNGLE_LEAVES ? 1.25F : 1.15F) - 0.6F;
               in = dx * dx + ry * ry <= rad * rad && dy > -3 && !(Math.abs(dx) == (int)rad && this.rnd(x, y, 7) < 0.5F);
            }

            if (in && this.back[base + y] == AIR) {
               this.back[base + y] = leaf;
            }
         }
      }
   }

   // ------------------------------------------------------------ Häuser

   private void house(int x) {
      int hx = this.houseFor(x);
      if (hx == Integer.MIN_VALUE || x < hx || x >= hx + HOUSE_W) {
         return;
      }

      int floor = this.height(hx + 6);
      int b = this.biomeAt(hx + 6);
      byte plank = b == SNOW ? SPRUCE_PLANKS : (b == DESERT ? SANDSTONE : PLANKS);
      byte frame = b == DESERT ? SANDSTONE : (b == SNOW ? SPRUCE_LOG : STRIPPED_LOG);
      byte roof = b == DESERT ? SANDSTONE : DARK_PLANKS;
      int base = slot(x) * WH;
      int lx = x - hx;
      // Boden
      this.front[base + floor - 1] = plank;
      this.front[base + floor - 2] = b == DESERT ? SANDSTONE : COBBLE;

      for (int y = floor; y < floor + 9 && y < WH; y++) {
         this.front[base + y] = AIR;
         this.deco[base + y] = D_NONE;
         this.back[base + y] = AIR;
      }

      // Wände mit Türen
      if (lx == 0 || lx == HOUSE_W - 1) {
         this.front[base + floor] = DOOR_LOW;
         this.front[base + floor + 1] = DOOR_LOW;
         this.front[base + floor + 2] = DOOR_HIGH;

         for (int y = floor + 3; y < floor + 6; y++) {
            this.front[base + y] = frame;
         }
      }

      // Rückwand mit Fenstern
      for (int y = floor; y < floor + 6; y++) {
         boolean window = (lx == 3 || lx == 4 || lx == 8 || lx == 9) && (y == floor + 3 || y == floor + 4);
         this.back[base + y] = window ? GLASS : (lx == 0 || lx == HOUSE_W - 1 ? frame : plank);
      }

      // Dach (vorne und hinten), giebelförmig
      int peak = Math.min(lx, HOUSE_W - 1 - lx);
      this.front[base + floor + 6] = roof;
      this.back[base + floor + 6] = roof;
      if (peak >= 2) {
         this.front[base + floor + 7] = roof;
         this.back[base + floor + 7] = roof;
      }

      if (peak >= 4) {
         this.front[base + floor + 8] = roof;
         this.back[base + floor + 8] = roof;
      }

      if (lx == 2 || lx == HOUSE_W - 3) {
         this.deco[base + floor + 3] = D_TORCH;
      }

      if (lx == 0) {
         this.registerHouse(hx, floor, b);
      }
   }

   private void registerHouse(int hx, int floor, int b) {
      for (House h : this.houses) {
         if (h.x0 == hx) {
            return;
         }
      }

      House h = new House(hx, floor, b, (int)(this.rnd(hx, 0, 8) * 5.0F));
      h.props.add(new int[]{P_TABLE, hx + 2, floor});
      h.props.add(new int[]{P_BARREL, hx + 3, floor});
      h.props.add(new int[]{P_FURNACE, hx + 9, floor});
      h.props.add(new int[]{P_BOOKS, hx + 10, floor});
      h.props.add(new int[]{P_BOOKS, hx + 10, floor + 1});
      h.props.add(new int[]{P_LANTERN, hx + 6, floor + 5});
      this.houses.add(h);
   }

   /** Häuser weit hinter dem Helden vergessen. */
   public void forget(float heroX) {
      this.houses.removeIf(h -> h.x0 < heroX - RING / 2.0F + 20);
   }

   public House houseAt(int x) {
      for (House h : this.houses) {
         if (x >= h.x0 && x < h.x0 + HOUSE_W) {
            return h;
         }
      }

      return null;
   }
}
