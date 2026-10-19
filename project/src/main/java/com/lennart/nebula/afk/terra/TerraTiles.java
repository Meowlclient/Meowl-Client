package com.lennart.nebula.afk.terra;

/** Block- und Deko-Arten der Terraria-Welt mit ihren Minecraft-Texturen. */
public final class TerraTiles {
   private TerraTiles() {
   }

   // ------------------------------------------------------------ Blöcke
   public static final byte AIR = 0;
   public static final byte GRASS = 1;
   public static final byte DIRT = 2;
   public static final byte STONE = 3;
   public static final byte DEEPSLATE = 4;
   public static final byte COAL = 5;
   public static final byte IRON = 6;
   public static final byte GOLD = 7;
   public static final byte DIAMOND = 8;
   public static final byte REDSTONE = 9;
   public static final byte LAPIS = 10;
   public static final byte SAND = 11;
   public static final byte SANDSTONE = 12;
   public static final byte SNOW_GRASS = 13;
   public static final byte SNOW = 14;
   public static final byte ICE = 15;
   public static final byte JUNGLE_GRASS = 16;
   public static final byte MUD = 17;
   public static final byte CRIMSON_GRASS = 18;
   public static final byte NETHERRACK = 19;
   public static final byte OAK_LOG = 20;
   public static final byte OAK_LEAVES = 21;
   public static final byte BIRCH_LOG = 22;
   public static final byte BIRCH_LEAVES = 23;
   public static final byte SPRUCE_LOG = 24;
   public static final byte SPRUCE_LEAVES = 25;
   public static final byte JUNGLE_LOG = 26;
   public static final byte JUNGLE_LEAVES = 27;
   public static final byte CRIMSON_STEM = 28;
   public static final byte WART = 29;
   public static final byte PLANKS = 30;
   public static final byte DARK_PLANKS = 31;
   public static final byte STONE_BRICKS = 32;
   public static final byte GLASS = 33;
   public static final byte WATER = 34;
   public static final byte LAVA = 35;
   public static final byte BEDROCK = 36;
   public static final byte GRAVEL = 37;
   public static final byte AMETHYST = 38;
   public static final byte MUSHROOM_GRASS = 39;
   public static final byte CACTUS = 40;
   public static final byte CLAY = 41;
   public static final byte COBBLE = 42;
   public static final byte MOSSY_COBBLE = 43;
   public static final byte SPRUCE_PLANKS = 44;
   public static final byte DOOR_LOW = 45;
   public static final byte DOOR_HIGH = 46;
   public static final byte STRIPPED_LOG = 47;
   public static final byte COUNT = 48;

   /** Texturen je Block: {vorne, oben, Seite, unten}; null = wie vorne. */
   static final String[][] TEX = new String[COUNT][];
   static final boolean[] SOLID = new boolean[COUNT];
   /** Durchsichtig (Nachbarflächen werden gezeichnet): Blätter, Glas, Flüssigkeiten. */
   static final boolean[] SEE_THROUGH = new boolean[COUNT];
   /** Hält Himmelslicht nicht auf (Bäume stehen vor dem Himmel). */
   static final boolean[] SKY_PASS = new boolean[COUNT];
   static final float[][] GLOW = new float[COUNT][];

   private static void def(byte id, boolean solid, String front, String top, String side, String bottom) {
      TEX[id] = new String[]{front, top == null ? front : top, side == null ? front : side, bottom == null ? front : bottom};
      SOLID[id] = solid;
   }

   static {
      def(GRASS, true, "block/grass_block_side", "block/grass_block_top", "block/grass_block_side", "block/dirt");
      def(DIRT, true, "block/dirt", null, null, null);
      def(STONE, true, "block/stone", null, null, null);
      def(DEEPSLATE, true, "block/deepslate", "block/deepslate_top", null, "block/deepslate_top");
      def(COAL, true, "block/coal_ore", null, null, null);
      def(IRON, true, "block/iron_ore", null, null, null);
      def(GOLD, true, "block/gold_ore", null, null, null);
      def(DIAMOND, true, "block/deepslate_diamond_ore", null, null, null);
      def(REDSTONE, true, "block/deepslate_redstone_ore", null, null, null);
      def(LAPIS, true, "block/lapis_ore", null, null, null);
      def(SAND, true, "block/sand", null, null, null);
      def(SANDSTONE, true, "block/sandstone", "block/sandstone_top", null, "block/sandstone_top");
      def(SNOW_GRASS, true, "block/grass_block_snow", "block/snow", "block/grass_block_snow", "block/dirt");
      def(SNOW, true, "block/snow", null, null, null);
      def(ICE, true, "block/packed_ice", null, null, null);
      def(JUNGLE_GRASS, true, "block/moss_block", null, null, "block/mud");
      def(MUD, true, "block/mud", null, null, null);
      def(CRIMSON_GRASS, true, "block/crimson_nylium_side", "block/crimson_nylium", "block/crimson_nylium_side", "block/netherrack");
      def(NETHERRACK, true, "block/netherrack", null, null, null);
      def(OAK_LOG, true, "block/oak_log", "block/oak_log_top", null, "block/oak_log_top");
      def(OAK_LEAVES, true, "block/oak_leaves", null, null, null);
      def(BIRCH_LOG, true, "block/birch_log", "block/birch_log_top", null, "block/birch_log_top");
      def(BIRCH_LEAVES, true, "block/birch_leaves", null, null, null);
      def(SPRUCE_LOG, true, "block/spruce_log", "block/spruce_log_top", null, "block/spruce_log_top");
      def(SPRUCE_LEAVES, true, "block/spruce_leaves", null, null, null);
      def(JUNGLE_LOG, true, "block/jungle_log", "block/jungle_log_top", null, "block/jungle_log_top");
      def(JUNGLE_LEAVES, true, "block/jungle_leaves", null, null, null);
      def(CRIMSON_STEM, true, "block/crimson_stem", "block/crimson_stem_top", null, "block/crimson_stem_top");
      def(WART, true, "block/nether_wart_block", null, null, null);
      def(PLANKS, true, "block/oak_planks", null, null, null);
      def(DARK_PLANKS, true, "block/dark_oak_planks", null, null, null);
      def(STONE_BRICKS, true, "block/stone_bricks", null, null, null);
      def(GLASS, true, "block/glass", null, null, null);
      def(WATER, false, "block/water_still#0", null, null, null);
      def(LAVA, false, "block/lava_still#0", null, null, null);
      def(BEDROCK, true, "block/bedrock", null, null, null);
      def(GRAVEL, true, "block/gravel", null, null, null);
      def(AMETHYST, true, "block/amethyst_block", null, null, null);
      def(MUSHROOM_GRASS, true, "block/mud", "block/warped_nylium", "block/mud", "block/mud");
      def(CACTUS, true, "block/cactus_side", "block/cactus_top", null, "block/cactus_top");
      def(CLAY, true, "block/clay", null, null, null);
      def(COBBLE, true, "block/cobblestone", null, null, null);
      def(MOSSY_COBBLE, true, "block/mossy_cobblestone", null, null, null);
      def(SPRUCE_PLANKS, true, "block/spruce_planks", null, null, null);
      def(DOOR_LOW, true, "block/oak_door_bottom", "block/oak_planks", "block/oak_planks", "block/oak_planks");
      def(DOOR_HIGH, true, "block/oak_door_top", "block/oak_planks", "block/oak_planks", "block/oak_planks");
      def(STRIPPED_LOG, true, "block/stripped_oak_log", null, null, null);
      for (byte b : new byte[]{OAK_LEAVES, BIRCH_LEAVES, SPRUCE_LEAVES, JUNGLE_LEAVES, GLASS, WATER, LAVA}) {
         SEE_THROUGH[b] = true;
      }

      for (byte b : new byte[]{AIR, OAK_LOG, OAK_LEAVES, BIRCH_LOG, BIRCH_LEAVES, SPRUCE_LOG, SPRUCE_LEAVES, JUNGLE_LOG, JUNGLE_LEAVES, CRIMSON_STEM, WART, CACTUS, GLASS, WATER}) {
         SKY_PASS[b] = true;
      }

      GLOW[LAVA] = new float[]{1.0F, 0.5F, 0.16F};
      GLOW[AMETHYST] = new float[]{0.45F, 0.25F, 0.7F};
      GLOW[REDSTONE] = new float[]{0.55F, 0.08F, 0.05F};
      GLOW[MUSHROOM_GRASS] = new float[]{0.08F, 0.3F, 0.55F};
   }

   public static boolean solid(int t) {
      return SOLID[t];
   }

   public static boolean liquid(int t) {
      return t == WATER || t == LAVA;
   }

   // ------------------------------------------------------------ Deko (nicht fest, als Bild gezeichnet)
   public static final byte D_NONE = 0;
   public static final byte D_GRASS = 1;
   public static final byte D_FERN = 2;
   public static final byte D_POPPY = 3;
   public static final byte D_DANDELION = 4;
   public static final byte D_CORNFLOWER = 5;
   public static final byte D_BLUET = 6;
   public static final byte D_DEAD_BUSH = 7;
   public static final byte D_RED_MUSH = 8;
   public static final byte D_BROWN_MUSH = 9;
   public static final byte D_GLOW_SHROOM = 10;
   public static final byte D_CRIMSON_FUNGUS = 11;
   public static final byte D_CRIMSON_ROOTS = 12;
   public static final byte D_TORCH = 13;
   public static final byte D_COBWEB = 14;
   public static final byte D_CRYSTAL = 15;
   public static final byte D_VINE = 16;
   public static final byte D_VINE_END = 17;
   public static final byte D_COUNT = 18;
   static final String[] DECO_TEX = {
      null, "block/short_grass", "block/fern", "block/poppy", "block/dandelion", "block/cornflower", "block/azure_bluet", "block/dead_bush",
      "block/red_mushroom", "block/brown_mushroom", "block/warped_fungus", "block/crimson_fungus", "block/crimson_roots", "block/torch",
      "block/cobweb", "block/amethyst_cluster", "block/cave_vines_plant_lit", "block/cave_vines_lit"
   };
   static final float[][] DECO_GLOW = new float[D_COUNT][];

   static {
      DECO_GLOW[D_TORCH] = new float[]{1.0F, 0.8F, 0.5F};
      DECO_GLOW[D_GLOW_SHROOM] = new float[]{0.2F, 0.5F, 1.0F};
      DECO_GLOW[D_CRYSTAL] = new float[]{0.7F, 0.38F, 1.0F};
      DECO_GLOW[D_VINE] = new float[]{0.3F, 0.22F, 0.08F};
      DECO_GLOW[D_VINE_END] = new float[]{0.7F, 0.5F, 0.22F};
      DECO_GLOW[D_CRIMSON_FUNGUS] = new float[]{0.25F, 0.05F, 0.03F};
   }

   /** Alle Texturen, die die Szene braucht (Pfade unter textures/, ohne .png). */
   public static java.util.List<String> textures() {
      java.util.Set<String> s = new java.util.LinkedHashSet<>();
      for (String[] t : TEX) {
         if (t != null) {
            for (String n : t) {
               s.add(n.contains("#") ? n.substring(0, n.indexOf('#')) : n);
            }
         }
      }

      for (String n : DECO_TEX) {
         if (n != null) {
            s.add(n);
         }
      }

      s.add("block/grass_block_side_overlay");
      s.add("block/crafting_table_front");
      s.add("block/crafting_table_top");
      s.add("block/crafting_table_side");
      s.add("block/barrel_side");
      s.add("block/barrel_top");
      s.add("block/furnace_front");
      s.add("block/furnace_side");
      s.add("block/furnace_top");
      s.add("block/bookshelf");
      s.add("block/lantern");
      for (int i = 0; i < 10; i++) {
         s.add("block/destroy_stage_" + i);
      }

      for (String n : new String[]{
         "item/diamond_pickaxe", "item/diamond_sword", "item/bow", "item/bow_pulling_2", "item/arrow", "item/potion", "item/potion_overlay",
         "item/slime_ball", "item/rotten_flesh", "item/ender_eye", "item/raw_iron", "item/raw_gold", "item/coal", "item/diamond", "item/redstone",
         "item/lapis_lazuli", "item/amethyst_shard", "item/glow_berries", "item/stick"
      }) {
         s.add(n);
      }

      s.add("entity/zombie/zombie");
      s.add("entity/player/wide/kai");
      s.add("entity/player/slim/alex");
      s.add("entity/player/wide/noor");
      s.add("entity/player/wide/efe");
      s.add("entity/player/slim/sunny");
      s.add("entity/player/wide/steve");
      s.add("environment/celestial/sun");
      s.add("environment/celestial/moon/full_moon");
      s.add("environment/clouds");
      s.add("gui/sprites/hud/heart/full");
      s.add("gui/sprites/hud/heart/container");
      return new java.util.ArrayList<>(s);
   }
}
