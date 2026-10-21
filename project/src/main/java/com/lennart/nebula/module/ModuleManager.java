package com.lennart.nebula.module;

import com.lennart.nebula.util.ChatUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModuleManager {
   private final List<Module> modules = new ArrayList<>();
   private final Map<Module, Boolean> lastKeyState = new HashMap<>();
   private Runnable changeListener = () -> {};

   public void register(Module module) {
      this.modules.add(module);
   }

   public void setChangeListener(Runnable listener) {
      this.changeListener = listener == null ? () -> {} : listener;
   }

   public void notifyChanged() {
      this.changeListener.run();
   }

   public List<Module> getModules() {
      return Collections.unmodifiableList(this.modules);
   }

   public List<Module> getModules(Category category) {
      return this.modules.stream().filter(m -> m.getCategory() == category).toList();
   }

   public Optional<Module> getByName(String name) {
      return this.modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst();
   }

   public <T extends Module> T get(Class<T> type) {
      for (Module module : this.modules) {
         if (type.isInstance(module)) {
            return (T)module;
         }
      }

      return null;
   }

   public void onPreTick(Minecraft client) {
      if (client.player != null && client.level != null) {
         for (Module module : this.modules) {
            if (module.isEnabled()) {
               long t0 = com.lennart.nebula.util.Perf.start();
               module.onPreTick();
               com.lennart.nebula.util.Perf.add(module, t0);
            }
         }
      }
   }

   public void onTick(Minecraft client) {
      this.handleKeybinds(client);
      if (client.player != null && client.level != null) {
         for (Module module : this.modules) {
            if (module.isEnabled()) {
               long t0 = com.lennart.nebula.util.Perf.start();
               module.onTick();
               com.lennart.nebula.util.Perf.add(module, t0);
            }
         }
      }
   }

   public void onRenderHud(Minecraft client, GuiGraphicsExtractor context, float tickDelta) {
      if (client.player != null && client.level != null) {
         // einmal pro Bild: Messfenster für das Leistungs-Menü weiterschalten
         com.lennart.nebula.util.Perf.frame();
         for (Module module : this.modules) {
            if (module.isEnabled()) {
               long t0 = com.lennart.nebula.util.Perf.start();
               module.onRenderHud(context, tickDelta);
               com.lennart.nebula.util.Perf.add(module, t0);
            }
         }
      }
   }

   public void onRenderWorld(Minecraft client, WorldCtx context, float tickDelta) {
      if (client.player != null && client.level != null) {
         for (Module module : this.modules) {
            if (module.isEnabled()) {
               long t0 = com.lennart.nebula.util.Perf.start();
               module.onRenderWorld(context, tickDelta);
               com.lennart.nebula.util.Perf.add(module, t0);
            }
         }
      }
   }

   private void handleKeybinds(Minecraft client) {
      boolean canToggle = client.gui.screen() == null && client.isWindowActive() && client.player != null;

      for (Module module : this.modules) {
         boolean pressed = module.getKeybind().get().isPressed();
         boolean wasPressed = this.lastKeyState.getOrDefault(module, false);
         this.lastKeyState.put(module, pressed);
         if (canToggle && pressed && !wasPressed && module.isTool()) {
            module.openTool(null);
         } else if (canToggle && pressed && !wasPressed) {
            module.toggle();
            ChatUtil.actionBar(module.getName() + ": " + (module.isEnabled() ? "§aAN" : "§cAUS"));
            this.notifyChanged();
         }
      }
   }
}
