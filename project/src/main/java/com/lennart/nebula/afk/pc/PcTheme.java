package com.lennart.nebula.afk.pc;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stil des PC-Zimmers: Blöcke, Licht, Deko und Partikel. */
public final class PcTheme {
   public static final int FX_NONE = 0;
   public static final int FX_EMBERS = 1;
   public static final int FX_SNOW = 2;
   public static final int FX_END = 3;
   public static final int FX_FIREFLIES = 4;
   public static final int FX_DUST = 5;
   final String wall;
   final String wall2;
   final String desk;
   final String floor;
   final String lantern;
   public final int accent;
   public final int accent2;
   final int ambient;
   final float ambientLevel;
   final boolean window;
   final boolean snow;
   final int sky1;
   final int sky2;
   final String[] decoLeft;
   final String[] decoRight;
   final String extra;
   final boolean extraGlow;
   public final int fx;

   private PcTheme(
      String wall,
      String wall2,
      String desk,
      String floor,
      String lantern,
      int accent,
      int accent2,
      int ambient,
      float ambientLevel,
      boolean window,
      boolean snow,
      int sky1,
      int sky2,
      String[] decoLeft,
      String[] decoRight,
      String extra,
      boolean extraGlow,
      int fx
   ) {
      this.wall = wall;
      this.wall2 = wall2;
      this.desk = desk;
      this.floor = floor;
      this.lantern = lantern;
      this.accent = accent;
      this.accent2 = accent2;
      this.ambient = ambient;
      this.ambientLevel = ambientLevel;
      this.window = window;
      this.snow = snow;
      this.sky1 = sky1;
      this.sky2 = sky2;
      this.decoLeft = decoLeft;
      this.decoRight = decoRight;
      this.extra = extra;
      this.extraGlow = extraGlow;
      this.fx = fx;
   }

   public static PcTheme of(String name) {
      return switch (name) {
         case "Cozy Wooden" -> new PcTheme(
            "spruce_planks", "stripped_dark_oak_log", "oak_planks", "spruce_planks", "ochre_froglight_side",
            0xFFFF9A3C, 0xFFFFC870, 0xFFFFB070, 0.2F, false, false, 0, 0,
            new String[]{"bookshelf", "bookshelf", "crafting_table"}, new String[]{"jack_o_lantern", "bookshelf", "bookshelf"},
            "bookshelf", false, FX_DUST
         );
         case "Nether Red" -> new PcTheme(
            "nether_bricks", "red_nether_bricks", "blackstone", "netherrack", "shroomlight",
            0xFFFF2A20, 0xFFFF6A30, 0xFFFF4020, 0.22F, false, false, 0, 0,
            new String[]{"nether_wart_block", "shroomlight", "crimson_planks"}, new String[]{"magma", "gilded_blackstone", "nether_wart_block"},
            "lava_still", true, FX_EMBERS
         );
         case "Nature Vibes" -> new PcTheme(
            "mossy_stone_bricks", "oak_log", "oak_planks", "moss_block", "verdant_froglight_side",
            0xFF6BFF5A, 0xFFB8FF70, 0xFFD8F0FF, 0.28F, true, false, 0xFF6FB4FF, 0xFFDCEFFF,
            new String[]{"moss_block", "azalea_leaves", "oak_log"}, new String[]{"flowering_azalea_leaves", "moss_block", "oak_log"},
            "mossy_cobblestone", false, FX_FIREFLIES
         );
         case "End Dimension" -> new PcTheme(
            "end_stone_bricks", "purpur_pillar", "obsidian", "end_stone", "pearlescent_froglight_side",
            0xFFC040FF, 0xFFFF60F0, 0xFF9050FF, 0.12F, false, false, 0, 0,
            new String[]{"purpur_block", "crying_obsidian", "end_stone"}, new String[]{"amethyst_block", "purpur_block", "crying_obsidian"},
            "amethyst_block", false, FX_END
         );
         case "Snow Base" -> new PcTheme(
            "stone_bricks", "packed_ice", "polished_diorite", "snow", "sea_lantern",
            0xFF40C8FF, 0xFF9FE8FF, 0xFFC8E0FF, 0.26F, true, true, 0xFF8FB0D8, 0xFFE8F0FA,
            new String[]{"snow", "blue_ice", "spruce_planks"}, new String[]{"packed_ice", "sea_lantern", "snow"},
            "blue_ice", false, FX_SNOW
         );
         default -> new PcTheme(
            "deepslate_tiles", "polished_blackstone_bricks", "polished_deepslate", "black_concrete", "sea_lantern",
            0xFF3A7BFF, 0xFFC040FF, 0xFF6050FF, 0.18F, false, false, 0, 0,
            new String[]{"amethyst_block", "redstone_lamp_on", "deepslate_bricks"}, new String[]{"sea_lantern", "amethyst_block", "polished_blackstone"},
            null, false, FX_NONE
         );
      };
   }

   /** Alle Texturen, die das Zimmer (und das Spiel) brauchen. */
   public Set<String> textures() {
      Set<String> s = new LinkedHashSet<>();

      for (String b : new String[]{
         this.wall, this.wall2, this.desk, this.floor, this.lantern, "black_concrete", "gray_concrete", "tinted_glass", "iron_bars", "stripped_spruce_log",
         "oak_leaves", "spruce_leaves", "snow", "stone", "oak_planks", "pumpkin_side", "pumpkin_top", "crafting_table_front", "crafting_table_side",
         "crafting_table_top", "terracotta", "white_terracotta", "brown_terracotta", "azalea_leaves", "ochre_froglight_side"
      }) {
         s.add("block/" + b);
      }

      for (String b : this.decoLeft) {
         s.add("block/" + b);
      }

      for (String b : this.decoRight) {
         s.add("block/" + b);
      }

      if (this.extra != null) {
         s.add("block/" + this.extra);
      }

      return s;
   }
}
