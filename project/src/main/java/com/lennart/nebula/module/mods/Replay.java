package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.ReplayScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.replay.ReplayData;
import com.lennart.nebula.replay.ReplayEngine;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.ChatUtil;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Items;

/**
 * Replay: nimmt ständig die letzten Minuten auf (alle Spieler in der Nähe) und spielt sie per Taste ab – Kamera folgt
 * jedem Spieler, mit Zeitleiste, Pause und Zeitlupe/Zeitraffer. Clips lassen sich speichern.
 */
public class Replay extends Module {
   private final NumberSetting length = this.add(new NumberSetting("Aufnahmelänge", "Wie viele Minuten rückwirkend gespeichert werden", 2.0, 0.5, 10.0, 0.5, "min"));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", "Spieler bis zu dieser Entfernung aufnehmen", 64.0, 16.0, 160.0, 8.0, "Blöcke"));
   private final KeybindSetting key = this.add(new KeybindSetting("Replay-Taste", "Öffnet die Wiedergabe der letzten Minuten", Bind.NONE));
   private final KeybindSetting saveKey = this.add(new KeybindSetting("Clip-Taste", "Speichert die letzten Minuten sofort als Datei", Bind.NONE));
   private boolean keyDown;
   private boolean saveDown;

   public Replay() {
      super("Replay", "Nimmt die letzten Minuten auf (Spieler in der Nähe) und spielt sie ab – Kamera folgt jedem Spieler, Zeitleiste, Zeitlupe, Clips speichern.", Category.MISC);
      this.section(Section.TOOLS);
      this.icon(Items.ENDER_EYE);
      ActionSetting open = this.add(new ActionSetting("Ansehen", "Wiedergabe der letzten Minuten starten", "Öffnen", () -> this.mc.schedule(this::open)));
      ActionSetting list = this.add(new ActionSetting("Gespeicherte Clips", "Clips aus dem Replay-Ordner abspielen", "Liste", () -> this.mc.schedule(() -> {
         this.mc.setScreen(new com.lennart.nebula.gui.ReplayListScreen(this.mc.screen));
      })));
      this.group("Aufnahme", new Setting[]{this.length, this.range});
      this.group("Steuerung", new Setting[]{this.key, this.saveKey, open, list});
   }

   public static Path folder() {
      return FabricLoader.getInstance().getGameDir().resolve("meowl-replays");
   }

   private void open() {
      if (this.mc.player == null) {
         return;
      }

      ReplayData.Clip clip = ReplayEngine.get().snapshot();
      if (clip.frames().size() < 20) {
         ChatUtil.actionBar("§cNoch nichts aufgenommen – kurz warten");
      } else {
         ReplayEngine.get().start(clip);
         this.mc.setScreen(new ReplayScreen(clip, false));
      }
   }

   public static void saveClip(ReplayData.Clip clip) {
      java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
      Path file = folder().resolve("replay_" + java.time.LocalDateTime.now().format(fmt) + ".mreplay");
      java.util.concurrent.CompletableFuture.runAsync(() -> {
         try {
            ReplayData.save(clip, file);
            net.minecraft.client.Minecraft.getInstance().execute(() -> ChatUtil.actionBar("§aClip gespeichert: §f" + file.getFileName()));
         } catch (Exception var3) {
            net.minecraft.client.Minecraft.getInstance().execute(() -> ChatUtil.actionBar("§cSpeichern fehlgeschlagen: " + var3.getMessage()));
         }
      });
   }

   @Override
   protected void onDisable() {
      ReplayEngine.get().stop();
      ReplayEngine.get().clearBuffer();
   }

   @Override
   public void onTick() {
      ReplayEngine.get().record(this.mc, (int)Math.round(this.length.get() * 60.0), this.range.get());
      boolean k = this.key.get().isPressed();
      if (k && !this.keyDown && this.mc.screen == null) {
         this.open();
      }

      this.keyDown = k;
      boolean s = this.saveKey.get().isPressed();
      if (s && !this.saveDown && this.mc.screen == null) {
         saveClip(ReplayEngine.get().snapshot());
      }

      this.saveDown = s;
   }
}
