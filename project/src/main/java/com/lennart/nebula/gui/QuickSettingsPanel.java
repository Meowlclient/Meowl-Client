package com.lennart.nebula.gui;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.component.ComponentFactory;
import com.lennart.nebula.gui.component.SettingComponent;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.mods.DiscordPresence;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ImageLibrarySetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Kleines Einstellungsfenster oben rechts im Meowl-Menü (Zahnrad): Menü-Größe, Discord-Status, Planeten verschieben,
 * randloses Vollbild und alles aus „Client & Server“ – fest an seinem Platz, ohne Beschreibungstexte.
 */
public final class QuickSettingsPanel {
   private final List<Object> rows = new ArrayList<>();
   private final Runnable save;
   private float scroll;
   private int maxScroll;
   private int x;
   private int y;
   private int w;
   private int h;
   private boolean open;

   /** Ein Modul an/aus (z.B. Discord-Status) als Schalter. */
   private final class ModuleToggle {
      final Module module;
      final SettingComponent<?> view;

      ModuleToggle(Module module, String label) {
         this.module = module;
         BooleanSetting proxy = new BooleanSetting(label, "", module.isEnabled()) {
            @Override
            public Boolean get() {
               return module.isEnabled();
            }

            @Override
            public void set(Boolean value) {
               module.setEnabled(value);
               QuickSettingsPanel.this.save.run();
            }
         };
         this.view = ComponentFactory.create(proxy, QuickSettingsPanel.this.save);
      }
   }

   public QuickSettingsPanel(ModuleManager modules, Runnable save) {
      this.save = save;
      DiscordPresence discord = modules.get(DiscordPresence.class);
      if (discord != null) {
         this.rows.add(new ModuleToggle(discord, "Discord-Status"));
      }

      // Hauptmenü Meowl/Minecraft umschalten – gut erreichbar, auch wenn das Meowl-Hauptmenü bei jemandem nicht funktioniert
      BooleanSetting title = com.lennart.nebula.module.mods.ClientSettings.meowlTitleSetting();
      if (title != null) {
         this.rows.add(ComponentFactory.create(title, save));
      }

      for (Module m : modules.getModules()) {
         if (!m.isHidden() || m instanceof com.lennart.nebula.nametag.OwnNametag) {
            continue;
         }

         for (Setting<?> s : m.getSettings()) {
            if (s == com.lennart.nebula.module.mods.ClientSettings.meowlTitleSetting() || s.getName().equals("Keybind") || s instanceof KeybindSetting || s instanceof StringListSetting || s instanceof ImageLibrarySetting) {
               continue;
            }

            try {
               this.rows.add(ComponentFactory.create(s, save));
            } catch (RuntimeException var8) {
            }
         }
      }
   }

   public boolean isOpen() {
      return this.open;
   }

   public void toggle() {
      this.open = !this.open;
      this.scroll = 0.0F;
   }

   public void close() {
      this.open = false;
   }

   private SettingComponent<?> view(Object row) {
      return row instanceof ModuleToggle t ? t.view : (SettingComponent<?>)row;
   }

   private List<SettingComponent<?>> visible() {
      List<SettingComponent<?>> out = new ArrayList<>();

      for (Object r : this.rows) {
         SettingComponent<?> c = this.view(r);
         if (r instanceof ModuleToggle || c.getSetting().isVisible()) {
            out.add(c);
         }
      }

      return out;
   }

   public boolean contains(double mx, double my) {
      return this.open && Render2D.inside(mx, my, this.x, this.y, this.w, this.h);
   }

   /** Fenster unter dem Zahnrad, rechtsbündig. */
   public void render(GuiGraphics ctx, Font font, int right, int top, int screenW, int screenH, int mouseX, int mouseY, float delta) {
      if (!this.open) {
         return;
      }

      this.w = Math.min(250, Math.max(170, screenW / 2 - 10));
      this.h = Math.min(170, screenH - top - 30);
      this.x = right - this.w;
      this.y = top;
      Render2D.glow(ctx, this.x, this.y, this.w, this.h, 8, 0x8B5CF6, 0.4F);
      Render2D.roundRect(ctx, this.x, this.y, this.w, this.h, 8, 0xF0181C2A);
      Render2D.roundRect(ctx, this.x + 1, this.y + 1, this.w - 2, this.h - 2, 7, 0xF4101420);
      List<SettingComponent<?>> list = this.visible();
      int total = 0;

      for (SettingComponent<?> c : list) {
         total += c.getHeight();
      }

      this.maxScroll = Math.max(0, total - (this.h - 8));
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll));
      ctx.enableScissor(this.x + 2, this.y + 4, this.x + this.w - 2, this.y + this.h - 4);
      int cy = this.y + 4 - Math.round(this.scroll);

      for (SettingComponent<?> c : list) {
         c.setBounds(this.x + 2, cy, this.w - 8);
         if (cy + c.getHeight() > this.y && cy < this.y + this.h) {
            c.render(ctx, font, mouseX, mouseY, delta);
         }

         cy += c.getHeight();
      }

      ctx.disableScissor();
      if (this.maxScroll > 0) {
         int barH = Math.max(16, (this.h - 8) * (this.h - 8) / (this.h - 8 + this.maxScroll));
         int barY = this.y + 4 + Math.round((this.h - 8 - barH) * (this.scroll / this.maxScroll));
         Render2D.roundRect(ctx, this.x + this.w - 5, barY, 2, barH, 1, 0x80B8A8FF);
      }
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.contains(mx, my)) {
         return false;
      }

      for (SettingComponent<?> c : this.visible()) {
         if (c.isHovered(mx, my) && c.mouseClicked(mx, my, button)) {
            return true;
         }
      }

      return true;
   }

   public boolean mouseDragged(double mx, double my, int button) {
      if (!this.open) {
         return false;
      }

      for (SettingComponent<?> c : this.visible()) {
         if (c.mouseDragged(mx, my, button)) {
            return true;
         }
      }

      return false;
   }

   public boolean mouseReleased(double mx, double my, int button) {
      if (!this.open) {
         return false;
      }

      boolean any = false;

      for (SettingComponent<?> c : this.visible()) {
         any |= c.mouseReleased(mx, my, button);
      }

      return any;
   }

   public boolean scroll(double mx, double my, double amount) {
      if (!this.contains(mx, my)) {
         return false;
      }

      this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float)amount * 20.0F));
      return true;
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (!this.open) {
         return false;
      }

      for (SettingComponent<?> c : this.visible()) {
         if (c.isCapturingInput() && c.keyPressed(key, scan, mods)) {
            return true;
         }
      }

      return false;
   }

   public boolean charTyped(int codepoint) {
      if (!this.open) {
         return false;
      }

      for (SettingComponent<?> c : this.visible()) {
         if (c.isCapturingInput() && c.charTyped(codepoint)) {
            return true;
         }
      }

      return false;
   }

   static void saveConfig() {
      NebulaClient c = NebulaClient.getInstance();
      if (c != null && c.getConfigManager() != null) {
         c.getConfigManager().save();
      }
   }
}
