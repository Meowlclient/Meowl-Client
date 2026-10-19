package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.BindsScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.util.ChatUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.Items;

public class BindCommands extends Module {
   private final StringListSetting storage = this.add(new StringListSetting("Befehle", "Befehls-Binds (im Fenster \"Binds verwalten\" bearbeiten)", List.of()));
   private final List<BindCommands.CommandBind> binds = new ArrayList<>();
   private final Map<BindCommands.CommandBind, Boolean> lastState = new HashMap<>();
   private List<String> parsedFrom;

   public BindCommands() {
      super("Bind Commands", "Alle Keybinds verwalten + eigene Befehle auf Tasten legen.", Category.MISC);
      this.icon(Items.COMMAND_BLOCK);
      this.add(
         new ActionSetting(
            "Binds verwalten", "Öffnet die Übersicht aller Tastenbelegungen", "Öffnen", () -> this.mc.setScreen(new BindsScreen(this.mc.screen))
         )
      );
      this.storage.setVisibility(() -> false);
   }

   public List<BindCommands.CommandBind> getBinds() {
      if (this.parsedFrom != this.storage.get()) {
         this.parsedFrom = this.storage.get();
         this.binds.clear();

         for (String entry : this.parsedFrom) {
            BindCommands.CommandBind cb = parse(entry);
            if (cb != null) {
               this.binds.add(cb);
            }
         }
      }

      return List.copyOf(this.binds);
   }

   public void setBinds(List<BindCommands.CommandBind> list) {
      List<String> out = new ArrayList<>();

      for (BindCommands.CommandBind cb : list) {
         out.add(cb.bind().type().name() + ":" + cb.bind().code() + "|" + cb.command());
      }

      this.storage.set(out);
      this.lastState.clear();
   }

   private static BindCommands.CommandBind parse(String entry) {
      try {
         int bar = entry.indexOf(124);
         String[] key = entry.substring(0, bar).split(":");
         Bind.Type type = Bind.Type.valueOf(key[0]);
         Bind bind = type == Bind.Type.NONE ? Bind.NONE : new Bind(type, Integer.parseInt(key[1]));
         return new BindCommands.CommandBind(bind, entry.substring(bar + 1));
      } catch (RuntimeException var51) {
         return null;
      }
   }

   @Override
   public void onTick() {
      boolean canUse = this.mc.screen == null && this.mc.isWindowActive() && this.mc.player != null;

      for (BindCommands.CommandBind cb : this.getBinds()) {
         boolean pressed = cb.bind().isPressed();
         boolean was = this.lastState.getOrDefault(cb, false);
         this.lastState.put(cb, pressed);
         if (canUse && pressed && !was && !cb.command().isBlank()) {
            this.send(cb.command().trim());
         }
      }
   }

   private void send(String text) {
      ClientPacketListener handler = this.mc.getConnection();
      if (handler != null) {
         if (text.startsWith("/")) {
            handler.sendCommand(text.substring(1));
         } else {
            handler.sendChat(text);
         }

         ChatUtil.actionBar("Bind: " + text);
      }
   }

   public record CommandBind(Bind bind, String command) {
   }
}
