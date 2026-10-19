package com.lennart.nebula.tiers;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.util.DynamicTextures;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class KitIcons {
   private static final String BASE = "https://www.mcpvp.com/rankings/";
   private static final Map<String, String> OFFICIAL = Map.ofEntries(
      Map.entry("smp", "ender-pearl"),
      Map.entry("shield", "shield"),
      Map.entry("late-game", "netherite-chestplate"),
      Map.entry("cart", "cart"),
      Map.entry("crystal", "crystal"),
      Map.entry("end-game", "crystal"),
      Map.entry("early-game", "early"),
      Map.entry("mace", "mace"),
      Map.entry("sword", "sword"),
      Map.entry("diamond-smp", "diamond-chestplate"),
      Map.entry("bow", "bow"),
      Map.entry("pot", "pot"),
      Map.entry("spear", "spear"),
      Map.entry("netherite-pot", "netherite-sword"),
      Map.entry("creeper", "creeper"),
      Map.entry("uhc", "uhc"),
      Map.entry("vanilla", "vanilla-hq/crystal"),
      Map.entry("axe", "vanilla-hq/axe"),
      Map.entry("nethop", "vanilla-hq/nether")
   );
   private static final Map<String, Identifier> LOADED = new ConcurrentHashMap<>();
   private static final Map<String, Boolean> REQUESTED = new ConcurrentHashMap<>();
   private static HttpClient http;

   private KitIcons() {
   }

   private static KitIcons.Icon of(String texture, Item item) {
      return new KitIcons.Icon(Identifier.withDefaultNamespace("textures/item/" + texture + ".png"), null, 0, item, false);
   }

   private static Identifier official(String mode) {
      String key = mode.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
      String file = OFFICIAL.get(key);
      if (file == null) {
         return null;
      } else {
         Identifier id = LOADED.get(file);
         if (id == null && REQUESTED.putIfAbsent(file, Boolean.TRUE) == null) {
            download(file);
         }

         return id;
      }
   }

   private static synchronized HttpClient client() {
      if (http == null) {
         http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NORMAL).build();
      }

      return http;
   }

   private static void download(String file) {
      HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.mcpvp.com/rankings/" + file + ".png"))
         .timeout(Duration.ofSeconds(15L))
         .header("User-Agent", "Meowl-Client")
         .GET()
         .build();
      client().sendAsync(req, BodyHandlers.ofByteArray()).thenAccept(resp -> {
         if (resp.statusCode() == 200 && resp.body().length > 0 && resp.body().length < 2000000) {
            Minecraft.getInstance().execute(() -> {
               try (NativeImage img = NativeImage.read(new ByteArrayInputStream((byte[])resp.body()))) {
                  int[] px = resample(img, 32);
                  Identifier id = Identifier.fromNamespaceAndPath("nebula", "kit/" + file.replace('/', '_').replace('-', '_'));
                  DynamicTextures.upload(id, null, px, 32, 32);
                  LOADED.put(file, id);
               } catch (Exception var7) {
                  NebulaClient.LOGGER.debug("Kit-Icon {} konnte nicht geladen werden: {}", file, var7.toString());
               }
            });
         }
      }).exceptionally(e -> {
         REQUESTED.remove(file);
         return null;
      });
   }

   private static int[] resample(NativeImage img, int size) {
      int w = img.getWidth();
      int h = img.getHeight();
      int[] out = new int[size * size];
      float scale = (float)Math.max(w, h) / size;
      int offX = Math.round((size - w / scale) / 2.0F);
      int offY = Math.round((size - h / scale) / 2.0F);

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float sx0 = (x - offX) * scale;
            float sy0 = (y - offY) * scale;
            if (!(sx0 < 0.0F) && !(sy0 < 0.0F) && !(sx0 >= w) && !(sy0 >= h)) {
               if (!(scale <= 1.0F) && w > 64) {
                  long a = 0L;
                  long r = 0L;
                  long g = 0L;
                  long b = 0L;
                  int n = 0;

                  for (int yy = (int)sy0; yy < Math.min(h, (int)(sy0 + scale)); yy++) {
                     for (int xx = (int)sx0; xx < Math.min(w, (int)(sx0 + scale)); xx++) {
                        int c = img.getPixel(xx, yy);
                        int ca = c >>> 24;
                        a += ca;
                        r += (c >> 16 & 0xFF) * ca;
                        g += (c >> 8 & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                        n++;
                     }
                  }

                  if (n > 0 && a > 0L) {
                     out[y * size + x] = (int)(a / n) << 24 | (int)(r / a) << 16 | (int)(g / a) << 8 | (int)(b / a);
                  }
               } else {
                  out[y * size + x] = img.getPixel(Math.min(w - 1, (int)sx0), Math.min(h - 1, (int)sy0));
               }
            }
         }
      }

      return out;
   }

   public static KitIcons.Icon forMode(String mode) {
      if (mode == null) {
         return null;
      } else {
         KitIcons.Icon fallback = fallback(mode);
         Identifier off = official(mode);
         return off != null ? new KitIcons.Icon(off, null, 0, fallback != null ? fallback.item() : Items.PAPER, true) : fallback;
      }
   }

   private static KitIcons.Icon fallback(String mode) {
      String k = mode.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

      return switch (k) {
         case "vanilla", "crystal", "cpvp", "endgame", "ogvanilla" -> of("end_crystal", Items.END_CRYSTAL);
         case "sword" -> of("diamond_sword", Items.DIAMOND_SWORD);
         case "axe", "shield" -> of("diamond_axe", Items.DIAMOND_AXE);
         case "pot", "potion", "diapot", "diamondpot" -> new KitIcons.Icon(
            Identifier.withDefaultNamespace("textures/item/splash_potion.png"),
            Identifier.withDefaultNamespace("textures/item/potion_overlay.png"),
            -515037,
            Items.SPLASH_POTION,
            false
         );
         case "nethop", "nethpot", "netheritepot", "netherite", "nethpotion", "lategame" -> of("netherite_chestplate", Items.NETHERITE_CHESTPLATE);
         case "smp" -> of("ender_pearl", Items.ENDER_PEARL);
         case "diasmp", "diamondsmp", "diamondsurvival", "diasurvival" -> of("diamond_chestplate", Items.DIAMOND_CHESTPLATE);
         case "earlygame" -> of("iron_chestplate", Items.IRON_CHESTPLATE);
         case "uhc", "builduhc" -> of("lava_bucket", Items.LAVA_BUCKET);
         case "mace" -> of("mace", Items.MACE);
         case "spear" -> of("diamond_spear", Items.DIAMOND_SPEAR);
         case "creeper" -> of("creeper_spawn_egg", Items.CREEPER_SPAWN_EGG);
         case "cart", "minecart" -> of("tnt_minecart", Items.TNT_MINECART);
         case "bow" -> of("bow", Items.BOW);
         case "trident" -> of("trident", Items.TRIDENT);
         case "elytra" -> of("elytra", Items.ELYTRA);
         case "speed" -> of("sugar", Items.SUGAR);
         case "manhunt" -> of("compass_00", Items.COMPASS);
         case "gapple", "goldenapple" -> of("golden_apple", Items.GOLDEN_APPLE);
         default -> null;
      };
   }

   public static void draw(GuiGraphics ctx, KitIcons.Icon icon, int x, int y, int size) {
      if (icon != null) {
         if (icon.official()) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), x, y, 0.0F, 0.0F, size, size, 32, 32, 32, 32);
         } else {
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().scale(size / 16.0F, size / 16.0F);
            ctx.renderItem(icon.stack(), 0, 0);
            ctx.pose().popMatrix();
         }
      }
   }

   public record Icon(Identifier texture, Identifier overlay, int tint, Item item, boolean official) {
      public ItemStack stack() {
         return new ItemStack(this.item);
      }
   }
}
