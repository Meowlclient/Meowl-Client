package com.lennart.nebula.util;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.mods.BindCommands;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class KeybindConflicts {
   private KeybindConflicts() {
   }

   public static List<String> find(Bind bind, Object owner) {
      List<String> result = new ArrayList<>();
      if (bind != null && !bind.isNone()) {
         NebulaClient client = NebulaClient.getInstance();
         if (client != null && client.getModuleManager() != null) {
            for (Module module : client.getModuleManager().getModules()) {
               for (Setting<?> setting : module.getSettings()) {
                  if (setting != owner && setting instanceof KeybindSetting ks && bind.equals(ks.get())) {
                     result.add(setting == module.getKeybind() ? module.getName() : module.getName() + " › " + setting.getName());
                  }
               }

               if (module instanceof BindCommands commands) {
                  for (BindCommands.CommandBind cb : commands.getBinds()) {
                     if (cb != owner && bind.equals(cb.bind())) {
                        result.add("Befehl " + cb.command());
                     }
                  }
               }
            }
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc.options != null) {
            for (KeyMapping key : mc.options.keyMappings) {
               if (bind.matches(KeyMappingHelper.getBoundKeyOf(key))) {
                  result.add(Component.translatable(key.getName()).getString());
               }
            }
         }

         return result;
      } else {
         return result;
      }
   }
}
