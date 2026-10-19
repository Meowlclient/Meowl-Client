package com.lennart.nebula.module.mods;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class ItemBeam extends Module {
   private final StringListSetting items = this.add(
      new StringListSetting(
            "Items", "Welche Items einen Strahl bekommen – Item-ID eingeben (z.B. diamond, elytra) oder unten das Item in der Hand übernehmen", List.of()
         )
         .normalizer(StringListSetting::itemId, "Item-ID, z.B. diamond")
         .alias(new String[]{"Eigene Items"})
   );
   private final ColorSetting color = this.add(
      new ColorSetting("Standardfarbe", "Farbe für Items ohne eigene Farbe", -14494738, false).alias(new String[]{"Farbe", "Farbe: Eigene Items"})
   );
   private final ColorSetting cTotem = this.add(new ColorSetting("Farbe: Totem", "Totem der Unsterblichkeit", -10929, false));
   private final ColorSetting cNetherite = this.add(
      new ColorSetting("Farbe: Netherite", "Netherite-Barren, -Schrott, Antiker Schutt und Netherite-Ausrüstung", -6526242, false)
   );
   private final ColorSetting cDiamond = this.add(new ColorSetting("Farbe: Diamant", "Diamanten und Diamant-Ausrüstung", -11541520, false));
   private final ColorSetting cEmerald = this.add(new ColorSetting("Farbe: Smaragd", "Smaragde", -12723068, false));
   private final ColorSetting cGapple = this.add(new ColorSetting("Farbe: Goldäpfel", "Goldene und verzauberte goldene Äpfel", -22746, false));
   private final ColorSetting cElytra = this.add(new ColorSetting("Farbe: Elytra", "Elytren", -5005861, false));
   private final ColorSetting cShulker = this.add(new ColorSetting("Farbe: Shulker-Kisten", "Alle Shulker-Kisten", -2068225, false));
   private final ColorSetting cWeapon = this.add(new ColorSetting("Farbe: Mace & Dreizack", "Streitkolben und Dreizack", -44462, false));
   private final StringListSetting customColors = this.add(
      new StringListSetting("Eigene Farben", "Farbe für ein bestimmtes Item, Format: item=#RRGGBB (z.B. golden_carrot=#FFAA00)", List.of())
         .normalizer(ItemBeam::normalizeColorEntry, "item=#RRGGBB")
   );
   private final NumberSetting opacity = this.add(new NumberSetting("Transparenz", "Deckkraft des Strahls", 70.0, 10.0, 100.0, 5.0, "%"));
   private final NumberSetting height = this.add(
      new NumberSetting("Beam-Höhe", "Höhe des Strahls", 40.0, 4.0, 160.0, 2.0, "Blöcke").alias(new String[]{"Höhe"})
   );
   private final NumberSetting width = this.add(
      new NumberSetting("Beam-Breite", "Breite des Strahls", 0.3, 0.1, 1.5, 0.05, "Blöcke").alias(new String[]{"Breite"})
   );
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", "Bis zu dieser Entfernung", 96.0, 16.0, 256.0, 8.0, "Blöcke"));
   private final BooleanSetting label = this.add(new BooleanSetting("Name anzeigen", "Name und Anzahl über dem Item", true));
   private final NumberSetting labelRange = this.add(new NumberSetting("Name bis", "Name nur bis zu dieser Entfernung", 24.0, 4.0, 96.0, 2.0, "Blöcke"));
   private static final int ITEM_SLOTS = 32;
   private List<ItemBeam.Found> found = List.of();
   private int scanTimer;

   public ItemBeam() {
      super("Item Beam", "Leuchtender Strahl über den Items, die du auswählst – Farbe je Item (Totem, Netherite, Diamant …).", Category.RENDER);
      this.section(Section.CLIENT);
      this.icon(Items.BEACON);
      this.add(new ActionSetting("Item in der Hand", "Das Item in deiner Hand zur Liste hinzufügen", "Hinzufügen", () -> {
         if (this.mc.player != null && !this.mc.player.getMainHandItem().isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(this.mc.player.getMainHandItem().getItem()).toString();
            this.items.addEntry(id);
            NebulaClient.getInstance().getConfigManager().save();
            ChatUtil.chat("§aItem Beam: §f" + id + " §7hinzugefügt");
         }
      }));
      // je hinzugefügtem Item eine eigene Farbe (gespeichert in „Eigene Farben“)
      List<Setting<?>> itemGroup = new ArrayList<>();
      itemGroup.add(this.items);

      for (int i = 0; i < ITEM_SLOTS; i++) {
         ItemBeam.ItemColor slot = this.add(new ItemBeam.ItemColor(i));
         itemGroup.add(slot);
      }

      this.customColors.setVisibility(() -> false);
      this.group("Items", itemGroup.toArray(new Setting[0]));
      this.group("Strahl", new Setting[]{this.opacity, this.height, this.width, this.range});
      this.group(
         "Farben je Item",
         new Setting[]{
            this.color, this.cTotem, this.cNetherite, this.cDiamond, this.cEmerald, this.cGapple, this.cElytra, this.cShulker, this.cWeapon, this.customColors
         }
      );
      this.group("Extras", new Setting[]{this.label, this.labelRange});
      this.labelRange.setVisibility(this.label::get);
   }

   private int classify(ItemStack stack) {
      String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
      return this.items.contains(id) ? 0xFF000000 | this.colorFor(id) : 0;
   }

   public int colorFor(String id) {
      for (String entry : this.customColors.get()) {
         int eq = entry.indexOf(61);
         if (eq > 0 && entry.substring(0, eq).equals(id)) {
            try {
               return Integer.parseInt(entry.substring(eq + 2), 16) & 16777215;
            } catch (NumberFormatException var6) {
            }
         }
      }

      String path = id.startsWith("minecraft:") ? id.substring(10) : id;
      ColorSetting c;
      if (path.equals("totem_of_undying")) {
         c = this.cTotem;
      } else if (path.contains("netherite") || path.equals("ancient_debris")) {
         c = this.cNetherite;
      } else if (path.contains("diamond")) {
         c = this.cDiamond;
      } else if (path.contains("emerald")) {
         c = this.cEmerald;
      } else if (path.equals("golden_apple") || path.equals("enchanted_golden_apple")) {
         c = this.cGapple;
      } else if (path.equals("elytra")) {
         c = this.cElytra;
      } else if (path.endsWith("shulker_box")) {
         c = this.cShulker;
      } else if (!path.equals("mace") && !path.equals("trident")) {
         c = this.color;
      } else {
         c = this.cWeapon;
      }

      return c.get() & 16777215;
   }

   /** Farbe ohne eigenen Eintrag (Kategorie-Farbe). */
   private int baseColorFor(String id) {
      String path = id.startsWith("minecraft:") ? id.substring(10) : id;
      ColorSetting c;
      if (path.equals("totem_of_undying")) {
         c = this.cTotem;
      } else if (path.contains("netherite") || path.equals("ancient_debris")) {
         c = this.cNetherite;
      } else if (path.contains("diamond")) {
         c = this.cDiamond;
      } else if (path.contains("emerald")) {
         c = this.cEmerald;
      } else if (path.equals("golden_apple") || path.equals("enchanted_golden_apple")) {
         c = this.cGapple;
      } else if (path.equals("elytra")) {
         c = this.cElytra;
      } else if (path.endsWith("shulker_box")) {
         c = this.cShulker;
      } else if (!path.equals("mace") && !path.equals("trident")) {
         c = this.color;
      } else {
         c = this.cWeapon;
      }

      return c.get() & 16777215;
   }

   private void setCustomColor(String id, Integer rgb) {
      List<String> list = new ArrayList<>();

      for (String e : this.customColors.get()) {
         int eq = e.indexOf(61);
         if (eq <= 0 || !e.substring(0, eq).equals(id)) {
            list.add(e);
         }
      }

      if (rgb != null) {
         list.add(id + "=#" + String.format("%06X", rgb & 16777215));
      }

      this.customColors.set(list);
   }

   private static String shortName(String id) {
      String path = id.startsWith("minecraft:") ? id.substring(10) : id;
      net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.tryParse(id));
      return item != null && item != Items.AIR ? new ItemStack(item).getHoverName().getString() : path;
   }

   /** Farbwahl für das i-te Item der Liste – zeigt den Item-Namen und speichert in „Eigene Farben“. */
   private final class ItemColor extends ColorSetting {
      private final int index;

      ItemColor(int index) {
         super("Item-Farbe " + (index + 1), "Farbe des Strahls für dieses Item", -14494738, false);
         this.index = index;
         this.setVisibility(() -> this.id() != null);
      }

      private String id() {
         List<String> list = ItemBeam.this.items.get();
         return this.index < list.size() ? list.get(this.index) : null;
      }

      @Override
      public String getDisplayName() {
         String id = this.id();
         return id == null ? this.getName() : "Farbe: " + shortName(id);
      }

      @Override
      public Integer get() {
         String id = this.id();
         return id == null ? -14494738 : 0xFF000000 | ItemBeam.this.colorFor(id);
      }

      @Override
      public Integer getDefault() {
         String id = this.id();
         return id == null ? -14494738 : 0xFF000000 | ItemBeam.this.baseColorFor(id);
      }

      @Override
      public void set(Integer value) {
         String id = this.id();
         if (id != null && value != null) {
            // Standardfarbe gewählt = eigenen Eintrag entfernen
            ItemBeam.this.setCustomColor(id, (value & 16777215) == ItemBeam.this.baseColorFor(id) ? null : value);
         }
      }

      @Override
      public void reset() {
         String id = this.id();
         if (id != null) {
            ItemBeam.this.setCustomColor(id, null);
         }
      }

      @Override
      public void fromJson(com.google.gson.JsonElement json) {
         // nichts laden – die Farben stehen in „Eigene Farben“
      }
   }

   public static String normalizeColorEntry(String text) {
      int eq = text.indexOf(61);
      if (eq <= 0) {
         return null;
      } else {
         String id = StringListSetting.itemId(text.substring(0, eq));
         String hex = text.substring(eq + 1).trim();
         if (hex.startsWith("#")) {
            hex = hex.substring(1);
         }

         return id != null && hex.matches("[0-9A-Fa-f]{6}") ? id + "=#" + hex.toUpperCase(Locale.ROOT) : null;
      }
   }

   @Override
   protected void onDisable() {
      this.found = List.of();
   }

   @Override
   public void onTick() {
      if (this.mc.level != null && this.mc.player != null && --this.scanTimer <= 0) {
         this.scanTimer = 5;
         double max = this.range.get() * this.range.get();
         List<ItemBeam.Found> list = new ArrayList<>();

         for (Entity e : this.mc.level.entitiesForRendering()) {
            if (e instanceof ItemEntity item && !(e.distanceToSqr(this.mc.player) > max)) {
               ItemStack stack = item.getItem();
               int color = stack.isEmpty() ? 0 : this.classify(stack);
               if (color != 0) {
                  String text = stack.getHoverName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : "");
                  list.add(new ItemBeam.Found(item, color, text));
               }
            }
         }

         this.found = list;
      }
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      if (!this.found.isEmpty()) {
         Vec3 cam = context.cameraPos();
         float baseAlpha = (float)(this.opacity.get() / 100.0);
         double time = System.currentTimeMillis() / 1000.0;
         float w = (float)this.width.get().doubleValue();
         float h = (float)this.height.get().doubleValue();
         VertexConsumer vc = context.buffer(WorldRender.glow());
         this.forEachBeam(context, cam, tickDelta, (mat, fx, grow, rx, rz, p) -> {
            beamQuad(vc, mat, rx, rz, w * 0.18F * grow, h, fx.color(), 0.95F * baseAlpha * p, 0.15F);
            beamQuad(vc, mat, rx, rz, w * 0.5F * grow, h, fx.color(), 0.5F * baseAlpha * p, 0.0F);
            beamQuad(vc, mat, rx, rz, w * 1.2F * grow, h * 0.6F, fx.color(), 0.18F * baseAlpha * p, 0.0F);
            ring(vc, mat, 0.45F + 0.05F * (float)Math.sin(time * 4.0), fx.color(), 0.7F * baseAlpha * p);
         });
         if (this.label.get()) {
            Font tr = this.mc.font;

            for (ItemBeam.Found f : this.found) {
               ItemEntity e = f.entity();
               if (!e.isRemoved()) {
                  Vec3 pos = e.getPosition(tickDelta);
                  if (pos.distanceTo(cam) <= this.labelRange.get()) {
                     this.drawLabel(context, tr, f, pos, cam);
                  }
               }
            }
         }
      }
   }

   private void forEachBeam(WorldCtx context, Vec3 cam, float tickDelta, ItemBeam.BeamDraw draw) {
      PoseStack m = context.matrices();
      double time = System.currentTimeMillis() / 1000.0;

      for (ItemBeam.Found f : this.found) {
         ItemEntity e = f.entity();
         if (!e.isRemoved()) {
            Vec3 pos = e.getPosition(tickDelta);
            double dx = pos.x - cam.x;
            double dy = pos.y - cam.y;
            double dz = pos.z - cam.z;
            double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);
            float p = (float)(0.88 + 0.12 * Math.sin(time * 3.0 + e.getId()));
            float grow = (float)Math.max(1.0, dist / 32.0);
            float rx = (float)(-dz);
            float rz = (float)dx;
            float len = (float)Math.sqrt(rx * rx + rz * rz);
            if (len < 1.0E-4F) {
               rx = 1.0F;
               rz = 0.0F;
               len = 1.0F;
            }

            m.pushPose();
            m.translate(dx, dy, dz);
            draw.draw(m.last().pose(), f, grow, rx / len, rz / len, p);
            m.popPose();
         }
      }
   }

   private static void beamQuad(VertexConsumer vc, Matrix4f mat, float rx, float rz, float halfW, float h, int rgb, float alpha, float whiten) {
      int bottom = color(rgb, alpha, whiten);
      int top = color(rgb, 0.0F, whiten);
      vc.addVertex(mat, -rx * halfW, 0.0F, -rz * halfW).setColor(bottom);
      vc.addVertex(mat, rx * halfW, 0.0F, rz * halfW).setColor(bottom);
      vc.addVertex(mat, rx * halfW, h, rz * halfW).setColor(top);
      vc.addVertex(mat, -rx * halfW, h, -rz * halfW).setColor(top);
   }

   static void ring(VertexConsumer vc, Matrix4f mat, float r, int rgb, float alpha) {
      int inner = color(rgb, 0.0F, 0.0F);
      int outer = color(rgb, alpha, 0.1F);
      int seg = 24;
      float r2 = r * 0.72F;

      for (int i = 0; i < seg; i++) {
         double a0 = i * Math.PI * 2.0 / seg;
         double a1 = (i + 1) * Math.PI * 2.0 / seg;
         float c0 = (float)Math.cos(a0);
         float s0 = (float)Math.sin(a0);
         float c1 = (float)Math.cos(a1);
         float s1 = (float)Math.sin(a1);
         vc.addVertex(mat, c0 * r2, 0.02F, s0 * r2).setColor(inner);
         vc.addVertex(mat, c1 * r2, 0.02F, s1 * r2).setColor(inner);
         vc.addVertex(mat, c1 * r, 0.02F, s1 * r).setColor(outer);
         vc.addVertex(mat, c0 * r, 0.02F, s0 * r).setColor(outer);
      }
   }

   static int color(int rgb, float alpha, float whiten) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      r = Math.round(r + (255 - r) * whiten);
      g = Math.round(g + (255 - g) * whiten);
      b = Math.round(b + (255 - b) * whiten);
      int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private void drawLabel(WorldCtx context, Font tr, ItemBeam.Found f, Vec3 pos, Vec3 cam) {
      PoseStack m = context.matrices();
      m.pushPose();
      m.translate(pos.x - cam.x, pos.y + 0.9 - cam.y, pos.z - cam.z);
      m.mulPose(context.cameraOrientation());
      m.scale(0.025F, -0.025F, 0.025F);
      float x = -tr.width(f.label()) / 2.0F;
      context.text(tr, f.label(), x, 0.0F, 0xFF000000 | f.color() & 16777215, false, m.last().pose(), false, 1342177280, 15728880
      );
      m.popPose();
   }

   private interface BeamDraw {
      void draw(Matrix4f var1, ItemBeam.Found var2, float var3, float var4, float var5, float var6);
   }

   private record Found(ItemEntity entity, int color, String label) {
   }
}
