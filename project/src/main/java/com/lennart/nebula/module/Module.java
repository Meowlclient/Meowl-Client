package com.lennart.nebula.module;

import com.google.gson.JsonObject;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.ModulePreview;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public abstract class Module {
   protected final Minecraft mc = Minecraft.getInstance();
   private final String name;
   private final String description;
   private final Category category;
   private final List<Setting<?>> settings = new ArrayList<>();
   private final KeybindSetting keybind = new KeybindSetting("Keybind", "Taste zum Ein- und Ausschalten des Moduls", Bind.NONE);
   private boolean enabled;
   private Item icon;
   private boolean hidden;
   private final List<HudBox> hudBoxes = new ArrayList<>();
   private final List<String> groupOrder = new ArrayList<>();
   private final List<String> legacyNames = new ArrayList<>();
   private Section section = Section.CLIENT;
   private boolean tool;

   protected Module(String name, String description, Category category) {
      this.name = name;
      this.description = description;
      this.category = category;
   }

   protected <S extends Setting<?>> S add(S setting) {
      if (this.settings.stream().anyMatch(s -> s.getName().equals(setting.getName()))) {
         NebulaClient.LOGGER.error("Modul {}: Einstellung \"{}\" gibt es doppelt", this.name, setting.getName());
      }

      this.settings.add(setting);
      return setting;
   }

   protected void group(String name, Setting<?>... members) {
      for (Setting<?> s : members) {
         s.setGroup(name);
      }

      if (!this.groupOrder.contains(name)) {
         this.groupOrder.add(name);
      }
   }

   public List<Setting<?>> getSettingsForMenu() {
      if (this.groupOrder.isEmpty()) {
         return this.getSettings();
      } else {
         List<Setting<?>> out = new ArrayList<>();

         for (Setting<?> s : this.settings) {
            if (s.getGroup() == null) {
               out.add(s);
            }
         }

         for (String g : this.groupOrder) {
            for (Setting<?> sx : this.settings) {
               if (g.equals(sx.getGroup())) {
                  out.add(sx);
               }
            }
         }

         return out;
      }
   }

   protected void legacy(String... oldModuleNames) {
      this.legacyNames.addAll(Arrays.asList(oldModuleNames));
   }

   public void applyLegacy(String legacyName, boolean legacyEnabled, JsonObject ownSettings) {
   }

   public List<String> getLegacyNames() {
      return Collections.unmodifiableList(this.legacyNames);
   }

   public ModulePreview preview() {
      return null;
   }

   /** Item, das beim Ziehen an einem Regler dieses Moduls ingame in der Hand gezeigt wird (null = keine Ingame-Vorschau). */
   public net.minecraft.world.item.ItemStack ingamePreviewItem() {
      return null;
   }

   /** Blendet das Menü beim Ziehen an Reglern aus, damit man die Wirkung ingame sieht (z.B. Crosshair, Items in der Hand). */
   public boolean hasIngamePreview() {
      return this.ingamePreviewItem() != null;
   }

   /** Wird beim Ziehen an einem Regler (Ingame-Vorschau) laufend aufgerufen, z.B. um eine Animation abzuspielen. */
   public void ingamePreviewTick() {
   }

   /** Ingame-Vorschau in der Nebenhand statt der Haupthand? */
   public boolean ingamePreviewOffhand() {
      return false;
   }

   protected void section(Section section) {
      this.section = section;
   }

   public Section getSection() {
      return this.section;
   }

   protected void tool() {
      this.tool = true;
      this.enabled = true;
      this.keybind.setDescription("Taste zum direkten Öffnen");
   }

   public boolean isTool() {
      return this.tool;
   }

   public Screen createScreen(Screen parent) {
      return null;
   }

   public void openTool(Screen parent) {
      Screen screen = this.createScreen(parent);
      if (screen != null) {
         this.mc.gui.setScreen(screen);
      }
   }

   protected void icon(Item item) {
      this.icon = item;
   }

   protected void hidden() {
      this.hidden = true;
   }

   protected HudBox hudBox(String label, double x, double y) {
      Set<String> taken = new HashSet<>();
      this.settings.forEach(s -> taken.add(s.getName()));
      String[] own = new String[]{"Position X", "Position Y", "Größe", "Transparenz", "Hintergrund", "Hintergrund-Farbe", "Ecken-Rundung"};
      boolean clash = Arrays.stream(own).anyMatch(taken::contains);
      UnaryOperator<String> naming = clash ? n -> label + ": " + n : UnaryOperator.identity();
      HudBox box = new HudBox(this::add, label, x, y, naming);
      this.hudBoxes.add(box);
      return box;
   }

   protected void onEnable() {
   }

   protected void onDisable() {
   }

   public void onPreTick() {
   }

   public void onTick() {
   }

   public void onRenderHud(GuiGraphicsExtractor context, float tickDelta) {
   }

   public void onRenderWorld(WorldCtx context, float tickDelta) {
   }

   public void onConfigLoaded() {
   }

   public boolean persistEnabled() {
      return !this.tool;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      if (!this.tool || enabled) {
         if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
               this.onEnable();
            } else {
               this.onDisable();
            }
         }
      }
   }

   public void toggle() {
      this.setEnabled(!this.enabled);
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public Category getCategory() {
      return this.category;
   }

   public ItemStack getIcon() {
      return this.icon != null ? com.lennart.nebula.util.SafeStack.of(this.icon) : this.category.icon();
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public List<HudBox> getHudBoxes() {
      return Collections.unmodifiableList(this.hudBoxes);
   }

   public KeybindSetting getKeybind() {
      return this.keybind;
   }

   public List<Setting<?>> getSettings() {
      List<Setting<?>> all = new ArrayList<>(this.settings);
      all.add(this.keybind);
      return Collections.unmodifiableList(all);
   }
}
