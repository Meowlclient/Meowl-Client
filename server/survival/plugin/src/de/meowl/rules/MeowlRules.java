package de.meowl.rules;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Survival-Regeln: Endkristalle, Seelenanker und Betten explodieren weiter, zerstören aber keine Blöcke mehr. */
public final class MeowlRules extends JavaPlugin implements Listener {
   @Override
   public void onEnable() {
      this.getServer().getPluginManager().registerEvents(this, this);
   }

   /** Endkristall-Explosion: keine Blöcke kaputt. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityExplode(EntityExplodeEvent e) {
      if (e.getEntity() instanceof EnderCrystal) {
         e.blockList().clear();
      }
   }

   /** Block-Explosionen (Seelenanker, Bett außerhalb der Oberwelt): keine Blöcke kaputt. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockExplode(BlockExplodeEvent e) {
      e.blockList().clear();
   }
}