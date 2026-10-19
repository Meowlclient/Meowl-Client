package com.lennart.nebula.module.mods;

import com.google.gson.JsonObject;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.MarkerRender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class FriendLocator extends Module {
   private static final String BOTH = "Beam & Marker";
   private static final String BEAM = "Nur Beam";
   private static final String MARKER = "Nur Marker";
   private final StringListSetting legacyFriends = this.add(new StringListSetting("Freunde", "Alte Liste (wird ins Modul „Freunde“ übernommen)", List.of()));
   private final KeybindSetting shareKey = this.add(new KeybindSetting("Standort-Taste", "Standort für Freunde ein-/ausschalten", Bind.NONE));
   private final KeybindSetting panicKey = this.add(new KeybindSetting("Panic-Taste", "Notruf: Freunde sehen dich rot pulsierend + Warnung", Bind.NONE));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer", "Wie lange dein Standort geteilt wird", 60.0, 10.0, 600.0, 10.0, "s"));
   private final NumberSetting panicDuration = this.add(new NumberSetting("Panic-Dauer", "Wie lange ein Notruf läuft", 120.0, 10.0, 600.0, 10.0, "s"));
   private final BooleanSetting live = this.add(new BooleanSetting("Live mitbewegen", "Position alle 2 Sekunden aktualisieren, solange geteilt wird", true));
   private final ModeSetting method = this.add(
      new ModeSetting("Methode", "Wie Freunde angezeigt werden", "Beam & Marker", "Beam & Marker", "Nur Beam", "Nur Marker")
   );
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe für normale Standorte", -11549697, false));
   private final ColorSetting panicColor = this.add(new ColorSetting("Panic-Farbe", "Farbe für Notrufe", -50373, false));
   private final BooleanSetting showDistance = this.add(new BooleanSetting("Entfernung anzeigen", "Entfernung unter dem Namen (keine Koordinaten)", true));
   private final NumberSetting maxDistance = this.add(
      new NumberSetting("Max. Entfernung", "Weiter entfernte Freunde ausblenden (0 = unbegrenzt)", 0.0, 0.0, 10000.0, 100.0, "m")
   );
   private final BooleanSetting panicSound = this.add(new BooleanSetting("Panic-Sound", "Warnton, wenn ein Freund einen Notruf sendet", true));
   private final BooleanSetting pickPlayer = this.add(
      new BooleanSetting("Spieler auswählen", "Beim Teilen und beim Notruf erst die Freunde auswählen, die dich sehen sollen", true)
   );
   private final Map<UUID, FriendLocator.Marker> markers = new LinkedHashMap<>();
   private List<String> shareTo = List.of();
   private long shareUntil;
   private boolean sharePanic;
   private long nextSend;
   private boolean shareDown;
   private boolean panicDown;

   public FriendLocator() {
      super("Friend Locator", "Panic-Button & Standort teilen: Meowl-Freunde sehen dich als Beam oder Marker – nie als Koordinaten.", Category.MEOWL);
      this.section(Section.MEOWL);
      this.icon(Items.RECOVERY_COMPASS);
      this.add(new ActionSetting("Standort teilen", "Teilt deinen Standort jetzt mit deinen Freunden", "Teilen", () -> this.share(false)));
      this.add(new ActionSetting("Panic", "Notruf an alle Freunde auf diesem Server", "Panic!", () -> this.share(true)));
      this.add(new ActionSetting("Freunde-Status", "Deine Freunde aus dem Modul „Freunde“ (gegenseitig/online)", "Anzeigen", () -> {
         if (Friends.get() != null) {
            Friends.get().printFriends();
         }
      }));
      this.legacyFriends.setVisibility(() -> false);
      this.group("Teilen", new Setting[]{this.shareKey, this.panicKey, this.pickPlayer, this.duration, this.panicDuration, this.live});
      this.group("Anzeige", new Setting[]{this.method, this.color, this.panicColor, this.showDistance, this.maxDistance, this.panicSound});
      this.setEnabled(true);
      MeowlService s = MeowlService.get();
      s.on("friend_locate", this::onLocate);
      s.on("friend_locate_stop", d -> {
         if (d.has("uuid")) {
            this.markers.remove(UUID.fromString(d.get("uuid").getAsString()));
         }
      });
   }

   public List<String> takeLegacyFriends() {
      List<String> old = this.legacyFriends.get();
      if (!old.isEmpty()) {
         this.legacyFriends.set(List.of());
      }

      return old;
   }

   public void share(boolean panic) {
      if (this.mc.player != null) {
         if (!MeowlService.get().isConnected()) {
            ChatUtil.chat("§cNicht mit dem Meowl Server verbunden");
         } else if (!panic && this.shareUntil > System.currentTimeMillis() && !this.sharePanic) {
            this.stopSharing();
            ChatUtil.actionBar("§7Standort wird nicht mehr geteilt");
         } else if (this.pickPlayer.get()) {
            // erst auswählen, wer den Standort (oder Notruf) sieht
            net.minecraft.client.gui.screens.Screen before = this.mc.gui.screen();
            this.mc.gui.setScreen(new com.lennart.nebula.gui.FriendPickScreen(panic, names -> {
               this.mc.gui.setScreen(before);
               this.start(panic, names);
            }));
         } else {
            this.start(panic, List.of());
         }
      }
   }

   private void start(boolean panic, List<String> to) {
      if (this.mc.player != null) {
         this.shareTo = to;
         this.sharePanic = panic;
         this.shareUntil = System.currentTimeMillis() + (long)((panic ? this.panicDuration.get() : this.duration.get()) * 1000.0);
         this.nextSend = 0L;
         this.send(true);
      }
   }

   private void stopSharing() {
      this.shareUntil = 0L;
      if (MeowlService.get().isConnected()) {
         MeowlService.get().post("/locate/stop", new JsonObject());
      }
   }

   private void send(boolean announce) {
      if (this.mc.player != null && this.mc.level != null) {
         Vec3 p = this.mc.player.position();
         JsonObject o = new JsonObject();
         o.addProperty("x", p.x);
         o.addProperty("y", p.y);
         o.addProperty("z", p.z);
         o.addProperty("dim", this.mc.level.dimension().identifier().toString());
         o.addProperty("panic", this.sharePanic);
         o.addProperty("duration", Math.max(5L, (this.shareUntil - System.currentTimeMillis()) / 1000L));
         if (!this.shareTo.isEmpty()) {
            com.google.gson.JsonArray to = new com.google.gson.JsonArray();
            this.shareTo.forEach(to::add);
            o.add("to", to);
         }

         MeowlService.get()
            .post("/locate/update", o)
            .whenComplete(
               (r, e) -> this.mc
                  .execute(
                     () -> {
                        if (announce) {
                           if (e == null && r.ok()) {
                              int sent = r.json().get("sent").getAsInt();
                              ChatUtil.actionBar(
                                 sent == 0
                                    ? "§7Kein gegenseitiger Meowl-Freund auf diesem Server online (Liste im Modul „Freunde“)"
                                    : (this.sharePanic ? "§c⚠ Notruf an " + sent + " Freund(e) gesendet" : "§bStandort für " + sent + " Freund(e) sichtbar")
                              );
                           } else {
                              ChatUtil.chat("§cStandort konnte nicht geteilt werden" + (r != null ? ": " + r.error() : ""));
                           }
                        }
                     }
                  )
            );
      }
   }

   private void onLocate(JsonObject d) {
      UUID uuid = UUID.fromString(d.get("uuid").getAsString());
      boolean panic = d.has("panic") && d.get("panic").getAsBoolean();
      String name = d.get("name").getAsString();
      // der Meowl Server schickt Standorte nur von gegenseitigen Freunden – keine zweite lokale Prüfung (Listen können abweichen)
      {
         FriendLocator.Marker old = this.markers.get(uuid);
         long until = System.currentTimeMillis() + d.get("duration").getAsLong() * 1000L;
         this.markers
            .put(
               uuid,
               new FriendLocator.Marker(
                  uuid, name, new Vec3(d.get("x").getAsDouble(), d.get("y").getAsDouble(), d.get("z").getAsDouble()), d.get("dim").getAsString(), panic, until
               )
            );
         if (old == null || old.panic() != panic) {
            if (panic) {
               ChatUtil.chat("§c§l⚠ NOTRUF §r§f" + name + " §cbraucht Hilfe! §7Folge dem roten Beam.");
               if (this.panicSound.get()) {
                  this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.7F, 1.0F));
                  this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4F, 0.9F));
               }
            } else {
               ChatUtil.actionBar("§b" + name + " §7teilt den Standort mit dir");
            }
         }
      }
   }

   @Override
   public void onTick() {
      boolean s = this.shareKey.get().isPressed();
      if (s && !this.shareDown && this.mc.gui.screen() == null) {
         this.share(false);
      }

      this.shareDown = s;
      boolean p = this.panicKey.get().isPressed();
      if (p && !this.panicDown && this.mc.gui.screen() == null) {
         this.share(true);
      }

      this.panicDown = p;
      long now = System.currentTimeMillis();
      if (this.shareUntil > 0L) {
         if (now >= this.shareUntil) {
            this.stopSharing();
         } else if (this.live.get() && now >= this.nextSend) {
            this.nextSend = now + 2000L;
            this.send(false);
         }
      }

      this.markers.values().removeIf(m -> now > m.until());
   }

   @Override
   protected void onDisable() {
      if (this.shareUntil > 0L) {
         this.stopSharing();
      }

      this.markers.clear();
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      if (!this.markers.isEmpty() && this.mc.player != null && this.mc.level != null) {
         String dim = this.mc.level.dimension().identifier().toString();
         Vec3 eye = this.mc.player.getEyePosition(tickDelta);
         double time = System.currentTimeMillis() / 1000.0;
         double max = this.maxDistance.get();

         for (FriendLocator.Marker m : this.markers.values()) {
            if (m.dim().equals(dim)) {
               Vec3 pos = m.pos();
               Player live = this.mc.level.getPlayerByUUID(m.uuid());
               if (live != null) {
                  pos = live.getPosition(tickDelta);
               }

               double d = eye.distanceTo(pos);
               if (!(max > 0.0) || !(d > max)) {
                  int rgb = m.panic() ? this.panicColor.get() : this.color.get() & 16777215;
                  float pulse = m.panic() ? (float)(0.55 + 0.45 * Math.sin(time * 7.0)) : (float)(0.8 + 0.2 * Math.sin(time * 2.0));
                  if (!this.method.is("Nur Marker")) {
                     MarkerRender.beam(context, pos, rgb, 0.75F * pulse, m.panic() ? 0.6F : 0.4F);
                  }

                  if (!this.method.is("Nur Beam")) {
                     MarkerRender.diamond(context, pos.add(0.0, 2.6 + 0.12 * Math.sin(time * 3.0), 0.0), rgb, 0.9F * pulse, 0.35F);
                  }

                  String title = (m.panic() ? "⚠ " : "◆ ") + m.name();
                  if (this.showDistance.get()) {
                     MarkerRender.label(context, pos.add(0.0, 3.3, 0.0), 0xFF000000 | rgb, 1.0F, title, MarkerRender.distance(d));
                  } else {
                     MarkerRender.label(context, pos.add(0.0, 3.3, 0.0), 0xFF000000 | rgb, 1.0F, title);
                  }
               }
            }
         }
      }
   }

   private record Marker(UUID uuid, String name, Vec3 pos, String dim, boolean panic, long until) {
   }
}
