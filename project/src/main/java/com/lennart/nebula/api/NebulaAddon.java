package com.lennart.nebula.api;

import com.lennart.nebula.module.ModuleManager;

public interface NebulaAddon {
   String ENTRYPOINT = "nebula";

   void registerModules(ModuleManager var1);
}
