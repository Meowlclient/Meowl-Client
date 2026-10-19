package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.NotesScreen;
import com.lennart.nebula.gui.widget.NotesInventoryButton;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.notes.NotesManager;
import com.lennart.nebula.notes.NotesStorage;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;

public class Notes extends Module {
   private NotesManager manager;

   public Notes() {
      super("Notes", "Notizbuch – öffnest du über das kleine Notiz-Symbol im Inventar.", Category.MISC);
      this.section(Section.MEOWL);
      this.icon(Items.WRITABLE_BOOK);
      this.setEnabled(true);
      ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
         if (this.isEnabled() && (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) {
            AbstractContainerScreen<?> handled = (AbstractContainerScreen<?>)screen;
            List<AbstractWidget> buttons = Screens.getButtons(screen);
            if (buttons.stream().noneMatch(b -> b instanceof NotesInventoryButton)) {
               buttons.add(new NotesInventoryButton(handled, () -> this.open(handled)));
            }
         }
      });
   }

   public NotesManager manager() {
      if (this.manager == null) {
         this.manager = new NotesManager(new NotesStorage(FabricLoader.getInstance().getConfigDir().resolve("meowl-notes.json")));
      }

      return this.manager;
   }

   private void open(AbstractContainerScreen<?> inventory) {
      this.mc.setScreen(new NotesScreen(inventory, this.manager()));
   }

   public void saveNow() {
      if (this.manager != null) {
         this.manager.saveNow();
      }
   }
}
