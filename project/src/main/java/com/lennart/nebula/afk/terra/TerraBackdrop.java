package com.lennart.nebula.afk.terra;

import com.lennart.nebula.afk.space.Noise;
import com.lennart.nebula.afk.space.Tex;
import java.util.Map;

/**
 * Parallax-Hintergründe im Terraria-Look, gezeichnet mit Minecraft-Texturen: ferne Berge (für alle Biome) sowie je Biom
 * eine mittlere und eine nahe Ebene mit Hügeln und Bäumen. Horizontal nahtlos. Reines Java.
 */
public final class TerraBackdrop {
   public static final int W = 512;
   public static final int FAR_H = 112;
   public static final int MID_H = 120;
   public static final int NEAR_H = 150;
   private static final int HAZE = 0xFFA9C9EE;
   public final Tex far;
   public final Tex[] mid = new Tex[TerraWorld.BIOMES];
   public final Tex[] near = new Tex[TerraWorld.BIOMES];
   private final Map<String, Tex> tex;
   private final Noise noise;
   private final Map<String, Integer> avg = new java.util.HashMap<>();

   public TerraBackdrop(Map<String, Tex> tex, long seed) {
      this.tex = tex;
      this.noise = new Noise(seed ^ 0x5EEDL);
      this.far = this.mountains();

      for (int b = 0; b < TerraWorld.BIOMES; b++) {
         this.mid[b] = this.hills(b, MID_H, 0.42F, 0.5F, 11 + b * 3, 2);
         this.near[b] = this.hills(b, NEAR_H, 0.2F, 0.34F, 37 + b * 5, 3);
      }
   }

   /** Nahtloses Rauschen über die Bildbreite (Kreis im Rauschraum). */
   private float loop(int x, float freq, float ch) {
      double a = x * Math.PI * 2.0 / W;
      float r = freq * W / (float)(Math.PI * 2.0);
      return this.noise.fbm((float)Math.cos(a) * r, (float)Math.sin(a) * r, ch, 4);
   }

   /** Texel einer Minecraft-Textur in grober Pixel-Größe (2 Bildpunkte je Texel). */
   private int sample(String name, int x, int y, int tint) {
      Tex t = this.tex.get(name);
      if (t == null) {
         return tint != 0 ? tint : 0xFF808080;
      }

      int c = t.firstFrame().at(x / 2, y / 2);
      if (c >>> 24 < 128) {
         // Löcher im Laub: dunkler Durchschnitt statt Schwarz
         int a = this.avg.computeIfAbsent(name, k -> t.firstFrame().average());
         c = Tex.argb(255, (a >> 16 & 255) * 0.55F, (a >> 8 & 255) * 0.55F, (a & 255) * 0.55F);
      }

      if (tint != 0) {
         c = TerraAtlas.tint(new Tex(1, 1, new int[]{c}), tint).px[0];
      }

      return c | 0xFF000000;
   }

   private static int haze(int c, float k, float shade) {
      int s = Tex.argb(255, (c >> 16 & 255) * shade, (c >> 8 & 255) * shade, (c & 255) * shade);
      return Tex.mix(s, HAZE, k);
   }

   private Tex mountains() {
      Tex t = new Tex(W, FAR_H);

      for (int x = 0; x < W; x++) {
         float n = this.loop(x, 0.012F, 1.0F);
         float r = this.loop(x, 0.04F, 2.0F);
         int top = (int)(FAR_H * (0.78F - (n - 0.3F) * 1.25F - r * 0.18F));
         top = Math.max(4, Math.min(FAR_H - 8, top / 3 * 3));

         for (int y = top; y < FAR_H; y++) {
            boolean snow = y < top + 6 + (int)(r * 6) && top < FAR_H * 0.42F;
            int c = snow ? this.sample("block/snow", x, y, 0) : this.sample("block/stone", x, y, 0);
            float shade = 0.75F + 0.25F * (1.0F - (float)(y - top) / FAR_H);
            t.set(x, y, haze(c, 0.6F, shade));
         }
      }

      return t;
   }

   /** Hügel mit Bäumen; {@code hz} = Dunst, {@code hill} = Hügelhöhe, {@code step} = Pixelstufen. */
   private Tex hills(int biome, int hgt, float hz, float hill, int ch, int step) {
      Tex t = new Tex(W, hgt);
      String ground;
      String top;
      int topTint;
      switch (biome) {
         case TerraWorld.DESERT -> {
            ground = "block/sandstone";
            top = "block/sand";
            topTint = 0;
         }
         case TerraWorld.SNOW -> {
            ground = "block/packed_ice";
            top = "block/snow";
            topTint = 0;
         }
         case TerraWorld.JUNGLE -> {
            ground = "block/mud";
            top = "block/moss_block";
            topTint = 0;
         }
         case TerraWorld.CRIMSON -> {
            ground = "block/netherrack";
            top = "block/crimson_nylium";
            topTint = 0;
         }
         default -> {
            ground = "block/dirt";
            top = "block/grass_block_top";
            topTint = TerraAtlas.GRASS_TINT;
         }
      }

      int[] surface = new int[W];

      for (int x = 0; x < W; x++) {
         float n = this.loop(x, 0.02F, ch);
         int s = (int)(hgt * (1.0F - hill * (0.35F + (n - 0.25F) * 1.3F)));
         surface[x] = Math.max(hgt / 4, Math.min(hgt - 6, s / step * step));
      }

      for (int x = 0; x < W; x++) {
         for (int y = surface[x]; y < hgt; y++) {
            int d = y - surface[x];
            int c = d < 4 ? this.sample(top, x, y, topTint) : this.sample(ground, x, y, 0);
            t.set(x, y, haze(c, hz, 0.9F - Math.min(0.3F, d / (float)hgt)));
         }
      }

      // Bäume
      java.util.Random r = new java.util.Random(ch * 7919L);
      int spacing = biome == TerraWorld.JUNGLE ? 9 : (biome == TerraWorld.DESERT ? 40 : 15);

      for (int x0 = 0; x0 < W; x0 += spacing + r.nextInt(spacing)) {
         int base = surface[x0] + 2;
         float size = (hgt / 120.0F) * (0.8F + r.nextFloat() * 0.6F);
         this.tree(t, biome, x0, base, size, hz, r);
      }

      return t;
   }

   private void tree(Tex t, int biome, int x0, int base, float size, float hz, java.util.Random r) {
      String log = switch (biome) {
         case TerraWorld.SNOW -> "block/spruce_log";
         case TerraWorld.JUNGLE -> "block/jungle_log";
         case TerraWorld.CRIMSON -> "block/crimson_stem";
         case TerraWorld.BIRCH -> "block/birch_log";
         case TerraWorld.DESERT -> "block/cactus_side";
         default -> "block/oak_log";
      };
      String leaf = switch (biome) {
         case TerraWorld.SNOW -> "block/spruce_leaves";
         case TerraWorld.JUNGLE -> "block/jungle_leaves";
         case TerraWorld.CRIMSON -> "block/nether_wart_block";
         case TerraWorld.BIRCH -> "block/birch_leaves";
         default -> "block/oak_leaves";
      };
      int leafTint = switch (biome) {
         case TerraWorld.SNOW -> 0xFF4E8A50;
         case TerraWorld.JUNGLE -> 0xFF3FB517;
         case TerraWorld.CRIMSON -> 0;
         case TerraWorld.BIRCH -> 0xFF80A755;
         default -> 0xFF5DA82F;
      };
      int trunkH = Math.round((biome == TerraWorld.JUNGLE ? 34 : (biome == TerraWorld.DESERT ? 12 : 20)) * size);
      int tw = Math.max(2, Math.round(3 * size));
      int topY = base - trunkH;

      for (int y = topY; y < base; y++) {
         for (int x = x0 - tw / 2; x < x0 - tw / 2 + tw; x++) {
            int xx = Math.floorMod(x, W);
            t.set(xx, y, haze(this.sample(log, xx, y, 0), hz, 0.8F));
         }
      }

      if (biome == TerraWorld.DESERT) {
         // Kaktusarme
         int arm = topY + trunkH / 3;
         for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < tw * 2; k++) {
               int xx = Math.floorMod(x0 + s * (tw / 2 + k), W);
               t.set(xx, arm, haze(this.sample(log, xx, arm, 0), hz, 0.8F));
               t.set(xx, arm + 1, haze(this.sample(log, xx, arm + 1, 0), hz, 0.8F));
            }

            for (int y = arm - tw * 2; y < arm; y++) {
               int xx = Math.floorMod(x0 + s * (tw / 2 + tw * 2 - 1), W);
               t.set(xx, y, haze(this.sample(log, xx, y, 0), hz, 0.8F));
            }
         }

         return;
      }

      if (biome == TerraWorld.SNOW) {
         // Tannenkegel
         int levels = 4;

         for (int lv = 0; lv < levels; lv++) {
            int ly = topY + lv * Math.round(6 * size);
            int half = Math.round((4 + lv * 3) * size);

            for (int y = ly; y < ly + Math.round(8 * size); y++) {
               int hw = half * (y - ly) / Math.max(1, Math.round(8 * size)) + 1;

               for (int x = x0 - hw; x <= x0 + hw; x++) {
                  int xx = Math.floorMod(x, W);
                  int c = y - ly < 2 ? this.sample("block/snow", xx, y, 0) : this.sample(leaf, xx, y, leafTint);
                  t.set(xx, y, haze(c, hz, 0.85F));
               }
            }
         }

         return;
      }

      // runde Laubkrone (Terraria-Baum), einige Blobs
      float rad = (biome == TerraWorld.JUNGLE ? 11 : 9) * size;
      int blobs = 4;

      for (int k = 0; k < blobs; k++) {
         float bx = x0 + (r.nextFloat() - 0.5F) * rad * 1.2F;
         float by = topY + (r.nextFloat() - 0.6F) * rad * 0.7F;
         float br = rad * (0.6F + r.nextFloat() * 0.4F);

         for (int y = (int)(by - br); y <= (int)(by + br); y++) {
            for (int x = (int)(bx - br); x <= (int)(bx + br); x++) {
               float dx = x - bx;
               float dy = y - by;
               if (dx * dx + dy * dy <= br * br && y >= 0 && y < t.h) {
                  int xx = Math.floorMod(x, W);
                  float shade = 0.7F + 0.3F * (1.0F - (dy + br) / (2.0F * br));
                  t.set(xx, y, haze(this.sample(leaf, xx, y, leafTint), hz, shade));
               }
            }
         }
      }
   }
}
