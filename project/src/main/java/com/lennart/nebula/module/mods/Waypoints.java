package com.lennart.nebula.module.mods;

import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.gui.WaypointsScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.MarkerRender;
import com.lennart.nebula.waypoints.Waypoint;
import com.lennart.nebula.waypoints.WaypointStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class Waypoints extends Module {
   private final WaypointStore store = new WaypointStore();
   private final KeybindSetting openKey = this.add(new KeybindSetting("Waypoints-Menü", "Öffnet die Waypoint-Liste", Bind.NONE));
   private final KeybindSetting quickKey = this.add(new KeybindSetting("Schnell-Waypoint", "Setzt sofort einen Waypoint an deiner Position", Bind.NONE));
   private final BooleanSetting beams = this.add(new BooleanSetting("Beam anzeigen", "Leuchtender Strahl vom Himmel zum Waypoint", true));
   private final BooleanSetting labels = this.add(new BooleanSetting("Name anzeigen", "Symbol und Name über dem Waypoint", true));
   private final BooleanSetting distance = this.add(new BooleanSetting("Entfernung anzeigen", "Entfernung unter dem Namen", true));
   private final NumberSetting maxDistance = this.add(
      new NumberSetting("Max. Entfernung", "Weiter entfernte Waypoints ausblenden (0 = unbegrenzt)", 0.0, 0.0, 10000.0, 100.0, "m")
   );
   private final NumberSetting labelSize = this.add(new NumberSetting("Schriftgröße", "Größe der Beschriftung", 1.0, 0.5, 2.0, 0.1, "x"));
   private final NumberSetting beamAlpha = this.add(new NumberSetting("Beam-Deckkraft", "Wie kräftig der Strahl leuchtet", 60.0, 10.0, 100.0, 5.0, "%"));
   private final BooleanSetting deathPoint = this.add(new BooleanSetting("Todespunkt", "Beim Sterben automatisch einen Waypoint setzen", true));
   private boolean openDown;
   private boolean quickDown;
   private boolean deathHandled;

   public Waypoints() {
      super("Waypoints", "Eigene Wegpunkte mit Beam, Symbol und Entfernung – gespeichert pro Server/Welt.", Category.WORLD);
      this.icon(Items.COMPASS);
      this.add(new ActionSetting("Waypoint-Liste", "Waypoints erstellen, umbenennen, löschen, Farbe und Symbol ändern", "Öffnen", this::open));
      this.add(new ActionSetting("Hier setzen", "Waypoint an deiner aktuellen Position", "Setzen", () -> this.addHere(null)));
      this.group("Steuerung", new Setting[]{this.openKey, this.quickKey, this.deathPoint});
      this.group("Anzeige", new Setting[]{this.beams, this.beamAlpha, this.labels, this.distance, this.labelSize, this.maxDistance});
      this.beamAlpha.setVisibility(this.beams::get);
      this.labelSize.setVisibility(this.labels::get);
      this.setEnabled(true);
      MeowlCommands.add(
         "wp",
         "wp [add|remove|list] §7– Waypoints öffnen/verwalten",
         name -> ClientCommands.literal(name)
                  .executes(ctx -> {
                     this.mc.schedule(this::open);
                     return 1;
                  })
                  .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                     this.addHere(StringArgumentType.getString(ctx, "name"));
                     return 1;
                  })))
                  .then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                     String n = StringArgumentType.getString(ctx, "name");
                     boolean removed = this.list().removeIf(w -> w.name.equalsIgnoreCase(n));
                     this.save();
                     ChatUtil.chat(removed ? "§7Waypoint §f" + n + " §7gelöscht" : "§cKein Waypoint namens §f" + n);
                     return 1;
                  })))
                  .then(
                     ClientCommands.literal("list")
                        .executes(
                           ctx -> {
                              List<Waypoint> list = this.list();
                              ChatUtil.chat(list.isEmpty() ? "§7Keine Waypoints in dieser Welt" : "§d" + list.size() + " Waypoints:");

                              for (Waypoint w : list) {
                                 ChatUtil.chat(
                                    "§f"
                                       + w.symbol
                                       + " "
                                       + w.name
                                       + (
                                          this.mc.player != null
                                             ? " §7" + MarkerRender.distance(Math.sqrt(this.mc.player.distanceToSqr(w.x, w.y, w.z)))
                                             : ""
                                       )
                                 );
                              }

                              return 1;
                           }
                        )
                  )
      );
   }

   public List<Waypoint> list() {
      return this.store.current();
   }

   public void save() {
      this.store.save();
   }

   public void open() {
      if (this.mc.player != null) {
         this.mc.gui.setScreen(new WaypointsScreen(this.mc.gui.screen(), this));
      }
   }

   public Waypoint addHere(String name) {
      if (this.mc.player != null && this.mc.level != null) {
         List<Waypoint> list = this.list();
         String n = name != null && !name.isBlank() ? name.trim() : "Waypoint " + (list.size() + 1);
         Vec3 p = this.mc.player.position();
         Waypoint w = new Waypoint(
            n,
            Math.floor(p.x) + 0.5,
            Math.floor(p.y),
            Math.floor(p.z) + 0.5,
            this.dimension(),
            Waypoint.COLORS[list.size() % Waypoint.COLORS.length],
            Waypoint.SYMBOLS[0]
         );
         list.add(w);
         this.save();
         ChatUtil.actionBar("§dWaypoint §f" + n + " §dgesetzt");
         return w;
      } else {
         return null;
      }
   }

   public String dimension() {
      return this.mc.level == null ? "" : this.mc.level.dimension().identifier().toString();
   }

   @Override
   public void onTick() {
      boolean o = this.openKey.get().isPressed();
      if (o && !this.openDown && this.mc.gui.screen() == null) {
         this.open();
      }

      this.openDown = o;
      boolean q = this.quickKey.get().isPressed();
      if (q && !this.quickDown && this.mc.gui.screen() == null) {
         this.addHere(null);
      }

      this.quickDown = q;
      if (this.mc.gui.screen() instanceof DeathScreen) {
         if (!this.deathHandled && this.deathPoint.get()) {
            this.deathHandled = true;
            Waypoint w = this.addHere("Tod " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            if (w != null) {
               w.symbol = "☠";
               w.color = 16281969;
               this.save();
            }
         }
      } else {
         this.deathHandled = false;
      }
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      if (this.mc.player != null) {
         String dim = this.dimension();
         Vec3 eye = this.mc.player.getEyePosition(tickDelta);
         double max = this.maxDistance.get();
         float alpha = (float)(this.beamAlpha.get() / 100.0);

         for (Waypoint w : this.list()) {
            if (w.visible && w.dim.equals(dim)) {
               Vec3 pos = new Vec3(w.x, w.y, w.z);
               double d = eye.distanceTo(pos);
               if (!(max > 0.0) || !(d > max)) {
                  if (this.beams.get()) {
                     MarkerRender.beam(context, pos, w.color, alpha, 0.35F);
                  }

                  if (this.labels.get()) {
                     String title = w.symbol + " " + w.name;
                     Vec3 at = pos.add(0.0, 1.6, 0.0);
                     float size = (float)this.labelSize.get().doubleValue();
                     if (this.distance.get()) {
                        MarkerRender.label(context, at, 0xFF000000 | w.color, size, title, MarkerRender.distance(d));
                     } else {
                        MarkerRender.label(context, at, 0xFF000000 | w.color, size, title);
                     }
                  }
               }
            }
         }
      }
   }
}
