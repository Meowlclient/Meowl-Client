package com.lennart.nebula.tools;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Farbverläufe aus Minecraft-Blöcken: jede Zwischenfarbe bekommt den Block mit der ähnlichsten Durchschnittsfarbe. */
public final class BlockPalette {
   /** Block-ID und Durchschnittsfarbe der Textur (aus den Vanilla-Texturen berechnet). */
   private static final String[] TABLE = new String[]{
      "white_wool", "E9ECEC",
      "white_concrete", "CFD5D6",
      "white_terracotta", "D1B2A1",
      "white_concrete_powder", "E1E3E3",
      "orange_wool", "F07613",
      "orange_concrete", "E06100",
      "orange_terracotta", "A15325",
      "orange_concrete_powder", "E3831F",
      "magenta_wool", "BD44B3",
      "magenta_concrete", "A9309F",
      "magenta_terracotta", "95586C",
      "magenta_concrete_powder", "C053B8",
      "light_blue_wool", "3AAFD9",
      "light_blue_concrete", "2389C6",
      "light_blue_terracotta", "716C89",
      "light_blue_concrete_powder", "4AB4D5",
      "yellow_wool", "F8C527",
      "yellow_concrete", "F0AF15",
      "yellow_terracotta", "BA8523",
      "yellow_concrete_powder", "E8C736",
      "lime_wool", "70B919",
      "lime_concrete", "5EA818",
      "lime_terracotta", "677534",
      "lime_concrete_powder", "7DBD29",
      "pink_wool", "ED8DAC",
      "pink_concrete", "D5658E",
      "pink_terracotta", "A14E4E",
      "pink_concrete_powder", "E499B5",
      "gray_wool", "3E4447",
      "gray_concrete", "36393D",
      "gray_terracotta", "392A23",
      "gray_concrete_powder", "4C5154",
      "light_gray_wool", "8E8E86",
      "light_gray_concrete", "7D7D73",
      "light_gray_terracotta", "876A61",
      "light_gray_concrete_powder", "9A9A94",
      "cyan_wool", "158991",
      "cyan_concrete", "157788",
      "cyan_terracotta", "565B5B",
      "cyan_concrete_powder", "24939D",
      "purple_wool", "792AAC",
      "purple_concrete", "641F9C",
      "purple_terracotta", "764656",
      "purple_concrete_powder", "8337B1",
      "blue_wool", "35399D",
      "blue_concrete", "2C2E8F",
      "blue_terracotta", "4A3B5B",
      "blue_concrete_powder", "4649A6",
      "brown_wool", "724728",
      "brown_concrete", "603B1F",
      "brown_terracotta", "4D3323",
      "brown_concrete_powder", "7D5435",
      "green_wool", "546D1B",
      "green_concrete", "495B24",
      "green_terracotta", "4C532A",
      "green_concrete_powder", "61772C",
      "red_wool", "A02722",
      "red_concrete", "8E2020",
      "red_terracotta", "8F3D2E",
      "red_concrete_powder", "A83632",
      "black_wool", "141519",
      "black_concrete", "080A0F",
      "black_terracotta", "251610",
      "black_concrete_powder", "191A1F",
      "terracotta", "985E43",
      "oak_planks", "A2824E",
      "spruce_planks", "725430",
      "birch_planks", "C0AF79",
      "jungle_planks", "A07350",
      "acacia_planks", "A85A32",
      "dark_oak_planks", "422B14",
      "mangrove_planks", "753630",
      "cherry_planks", "E2B2AC",
      "bamboo_planks", "C1AD50",
      "crimson_planks", "653046",
      "warped_planks", "2B6863",
      "pale_oak_planks", "E3D9D8",
      "stone", "B9B9B9",
      "cobblestone", "7F7F7F",
      "mossy_cobblestone", "6E765E",
      "andesite", "888888",
      "polished_andesite", "848685",
      "diorite", "BCBCBC",
      "polished_diorite", "C0C1C2",
      "granite", "956755",
      "polished_granite", "9A6A59",
      "tuff", "6C6D66",
      "polished_tuff", "616863",
      "tuff_bricks", "62665F",
      "calcite", "DFE0DC",
      "dripstone_block", "866B5C",
      "stone_bricks", "7A797A",
      "mossy_stone_bricks", "737969",
      "cracked_stone_bricks", "767576",
      "bricks", "966153",
      "mud_bricks", "89674F",
      "packed_mud", "8E6A4F",
      "nether_bricks", "2C151A",
      "red_nether_bricks", "450709",
      "netherrack", "612626",
      "polished_blackstone", "353038",
      "polished_blackstone_bricks", "302A31",
      "end_stone", "DBDE9E",
      "end_stone_bricks", "DAE0A2",
      "purpur_block", "A97DA9",
      "dark_prismarine", "335B4B",
      "prismarine_bricks", "63AB9E",
      "obsidian", "0F0A18",
      "crying_obsidian", "200A3C",
      "glowstone", "AB8354",
      "gold_block", "F6D03D",
      "iron_block", "DCDCDC",
      "diamond_block", "62EDE4",
      "emerald_block", "2ACB57",
      "lapis_block", "1E438C",
      "redstone_block", "AF1805",
      "coal_block", "100F0F",
      "copper_block", "C06B4F",
      "exposed_copper", "A17D67",
      "weathered_copper", "6C996E",
      "oxidized_copper", "52A284",
      "cut_copper", "BF6A50",
      "amethyst_block", "8561BF",
      "moss_block", "596D2D",
      "packed_ice", "8DB4FA",
      "blue_ice", "74A7FD",
      "clay", "A0A6B3",
      "mud", "3C393C",
      "honeycomb_block", "E5941D",
      "sponge", "C3C04A",
      "shroomlight", "F09246",
      "nether_wart_block", "720202",
      "warped_wart_block", "167779",
      "raw_iron_block", "A6876B",
      "raw_gold_block", "DDA92E",
      "raw_copper_block", "9A694F",
      "deepslate_bricks", "464647",
      "deepslate_tiles", "363637",
      "cobbled_deepslate", "4D4D50",
      "polished_deepslate", "484849",
      "resin_bricks", "CD5818",
      "soul_sand", "513E32",
      "soul_soil", "4B392E",
      "sand", "DBCFA3",
      "red_sand", "BE6621",
      "gravel", "837F7E",
      "dirt", "866043",
      "coarse_dirt", "77553B",
      "pale_moss_block", "6A7068",
      "sculk", "0D1B21",
      "chiseled_stone_bricks", "777677",
      "smooth_basalt", "48484E",
      "snow_block", "F9FEFE",
      "smooth_quartz", "ECE6DF",
      "quartz_bricks", "EAE5DD"
   };
   private static List<Entry> entries;

   public record Entry(String id, int rgb, Item item, float[] lab) {
   }

   private BlockPalette() {
   }

   public static List<Entry> entries() {
      if (entries == null) {
         List<Entry> list = new ArrayList<>();

         for (int i = 0; i + 1 < TABLE.length; i += 2) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(TABLE[i]));
            if (item != null && item != Items.AIR) {
               int rgb = Integer.parseInt(TABLE[i + 1], 16);
               list.add(new Entry(TABLE[i], rgb, item, lab(rgb)));
            }
         }

         entries = list;
      }

      return entries;
   }

   /** Verlauf über alle Farben (2 oder mehr) mit genau {@code length} Blöcken. */
   public static List<Entry> gradient(List<Integer> colors, int length, boolean noRepeats) {
      List<Entry> out = new ArrayList<>();
      if (colors.isEmpty() || length < 1) {
         return out;
      }

      for (int i = 0; i < length; i++) {
         float t = length == 1 ? 0.0F : (float)i / (length - 1);
         int c = sample(colors, t);
         Entry best = nearest(c, noRepeats && !out.isEmpty() ? out.get(out.size() - 1) : null);
         if (best != null) {
            out.add(best);
         }
      }

      return out;
   }

   /** Farbe an Stelle t (0..1) im Verlauf, gemischt im Lab-Farbraum (sieht gleichmäßiger aus als RGB). */
   public static int sample(List<Integer> colors, float t) {
      if (colors.size() == 1) {
         return colors.get(0);
      }

      float pos = t * (colors.size() - 1);
      int i = Math.min(colors.size() - 2, (int)Math.floor(pos));
      float k = pos - i;
      float[] a = lab(colors.get(i));
      float[] b = lab(colors.get(i + 1));
      return fromLab(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k);
   }

   public static Entry nearest(int rgb, Entry avoid) {
      float[] l = lab(rgb);
      Entry best = null;
      float bestD = Float.MAX_VALUE;

      for (Entry e : entries()) {
         float d = dist(l, e.lab());
         if (e == avoid) {
            d += 60.0F;
         }

         if (d < bestD) {
            bestD = d;
            best = e;
         }
      }

      return best;
   }

   private static float dist(float[] a, float[] b) {
      float dl = a[0] - b[0];
      float da = a[1] - b[1];
      float db = a[2] - b[2];
      return dl * dl + da * da + db * db;
   }

   // ------------------------------------------------------------------ CIE-Lab

   private static float lin(int c) {
      float v = c / 255.0F;
      return v <= 0.04045F ? v / 12.92F : (float)Math.pow((v + 0.055F) / 1.055F, 2.4);
   }

   private static int gam(float v) {
      v = Math.max(0.0F, Math.min(1.0F, v));
      float s = v <= 0.0031308F ? v * 12.92F : 1.055F * (float)Math.pow(v, 1.0 / 2.4) - 0.055F;
      return Math.round(s * 255.0F);
   }

   private static float f(float t) {
      return t > 0.008856F ? (float)Math.cbrt(t) : 7.787F * t + 16.0F / 116.0F;
   }

   private static float fi(float t) {
      float c = t * t * t;
      return c > 0.008856F ? c : (t - 16.0F / 116.0F) / 7.787F;
   }

   public static float[] lab(int rgb) {
      float r = lin(rgb >> 16 & 0xFF);
      float g = lin(rgb >> 8 & 0xFF);
      float b = lin(rgb & 0xFF);
      float x = (r * 0.4124F + g * 0.3576F + b * 0.1805F) / 0.95047F;
      float y = r * 0.2126F + g * 0.7152F + b * 0.0722F;
      float z = (r * 0.0193F + g * 0.1192F + b * 0.9505F) / 1.08883F;
      float fx = f(x);
      float fy = f(y);
      float fz = f(z);
      return new float[]{116.0F * fy - 16.0F, 500.0F * (fx - fy), 200.0F * (fy - fz)};
   }

   public static int fromLab(float l, float a, float bb) {
      float fy = (l + 16.0F) / 116.0F;
      float fx = fy + a / 500.0F;
      float fz = fy - bb / 200.0F;
      float x = fi(fx) * 0.95047F;
      float y = fi(fy);
      float z = fi(fz) * 1.08883F;
      float r = x * 3.2406F + y * -1.5372F + z * -0.4986F;
      float g = x * -0.9689F + y * 1.8758F + z * 0.0415F;
      float b = x * 0.0557F + y * -0.204F + z * 1.057F;
      return gam(r) << 16 | gam(g) << 8 | gam(b);
   }
}