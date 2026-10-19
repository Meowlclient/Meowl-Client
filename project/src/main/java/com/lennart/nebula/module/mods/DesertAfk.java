package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.afk.AfkInput;
import com.lennart.nebula.afk.BattleRenderer;
import com.lennart.nebula.afk.BattleSim;
import com.lennart.nebula.afk.BattleSounds;
import com.lennart.nebula.afk.McCanvas;
import com.lennart.nebula.afk.SitPose;

import com.lennart.nebula.afk.space.PlanetPainter;
import com.lennart.nebula.afk.space.SpaceScene;
import com.lennart.nebula.afk.space.SpaceView;
import com.lennart.nebula.afk.valley.ValleyScene;
import com.lennart.nebula.afk.valley.ValleyView;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.setting.TextSetting;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DesertAfk extends Module implements AfkInput.Listener {
   private static DesertAfk instance;
   public static final String DESERT = "Wüstenschlacht";
   public static final String SPACE = "Sternenschlacht";
   public static final String VALLEY = "Tal des Endes";
   public static final String PC = "PC-Setup";
   public static final String TERRA = "Terraria-Welt";
   public static final String DATE = "E-Date";
   private final ModeSetting scene = this.add(
      new ModeSetting(
         "Szene",
         "Wüstenschlacht: Felsen. Sternenschlacht: Brücke eines Raumschiffs. Tal des Endes: Naruto gegen Sasuke. PC-Setup: Blick auf deinen Gaming-Schreibtisch. Terraria-Welt: dein Held erkundet eine Terraria-Welt aus Minecraft-Blöcken. E-Date: ihr schaut euch zu zweit den Sonnenaufgang an",
         DESERT,
         DESERT,
         SPACE,
         VALLEY,
         PC,
         TERRA,
         DATE
      )
   );
   private final ModeSetting partnerSkin = this.add(
      new ModeSetting("Partner-Skin", "Wer neben dir sitzt (Standard-Skins oder ein beliebiger Spielername)", "Alex", "Alex", "Steve", "Ari", "Efe", "Kai", "Makena", "Noor", "Sunny", "Zuri", "Spielername")
   );
   private final TextSetting partnerName = this.add(new TextSetting("Partner-Name", "Minecraft-Name, dessen Skin neben dir sitzt", "", 16));
   private final BooleanSetting dateHearts = this.add(new BooleanSetting("Herzen", "Ab und zu steigen kleine Herzen zwischen euch auf", true));
   private com.lennart.nebula.afk.date.DateScene date;
   private String partnerKey;
   private net.minecraft.world.entity.player.PlayerSkin partnerCache;
   private static final Object PARTNER = new Object();
   private final ModeSetting terraStart = this.add(
      new ModeSetting("Startzeit", "Zu welcher Tageszeit die Terraria-Welt beginnt (nachts kann das Auge von Cthulhu erscheinen)", "Morgen", "Morgen", "Abend", "Nacht")
   );
   private final BooleanSetting terraHud = this.add(
      new BooleanSetting("Terraria-HUD", "Hotbar, Herzen, Mana, Minikarte und Meldungen wie in Terraria", true)
   );
   private com.lennart.nebula.afk.terra.TerraScene terra;
   private final ModeSetting pcTheme = this.add(
      new ModeSetting("PC-Stil", "Look des Zimmers im PC-Setup", com.lennart.nebula.afk.pc.PcScene.THEMES[0], com.lennart.nebula.afk.pc.PcScene.THEMES)
   );
   private final BooleanSetting shareAfk = this.add(
      new BooleanSetting("AFK für andere zeigen", "Andere Meowl-Spieler sehen „meowl afk“ über dir, solange die Szene läuft", true)
   );
   private final BooleanSetting showOthersAfk = this.add(
      new BooleanSetting("AFK anderer zeigen", "„meowl afk“ über anderen Meowl-Spielern, die gerade im AFK-Bildschirm sind", true)
   );
   private com.lennart.nebula.afk.pc.PcScene pc;
   private final ModeSetting planet = this.add(
      new ModeSetting("Planet", "Welcher Planet unter der Schlacht liegt", "Lavawelt", "Lavawelt", "Stadtwelt", "Eiswelt")
   );
   private final BooleanSetting shipHits = this.add(
      new BooleanSetting("Treffer am eigenen Schiff", "Ab und zu wird die Brücke getroffen: Erschütterung, Funken und roter Alarm", true)
   );
   private final NumberSetting afkTime = this.add(
      new NumberSetting("AFK-Zeit", "Nach wie vielen Sekunden/Minuten ohne Eingabe die Szene startet", 5.0, 1.0, 120.0, 1.0, "")
   );
   private final ModeSetting unit = this.add(new ModeSetting("Zeit-Einheit", "Ob die AFK-Zeit in Sekunden oder Minuten gilt", "Minuten", "Sekunden", "Minuten"));
   private final BooleanSetting endMouse = this.add(new BooleanSetting("Mausbewegung beendet AFK", "Maus bewegen oder Mausrad drehen beendet die Szene", true));
   private final BooleanSetting endKeys = this.add(
      new BooleanSetting("Tastatur beendet AFK", "Jede Taste (außer ignorierten) beendet die Szene. ESC beendet immer.", true)
   );
   private final BooleanSetting endClick = this.add(new BooleanSetting("Klick beendet AFK", "Ein Mausklick beendet die Szene", true));
   private final StringListSetting ignored = this.add(
      new StringListSetting("Ignorierte Tasten", "Diese Tasten beenden AFK nicht und setzen den Timer nicht zurück (z.B. F2 für Screenshots)", List.of("F2"))
         .normalizer(DesertAfk::keyName, "Taste, z.B. F2 oder LEFT_ALT")
   );
   private final BooleanSetting startAnim = this.add(
      new BooleanSetting("Start-Animation", "Weicher Kino-Übergang beim Start (aus = schnelles Einblenden)", true)
   );
   private final NumberSetting transIn = this.add(new NumberSetting("Übergangsdauer", "Wie lange das Einblenden dauert", 2.2, 1.0, 3.0, 0.1, "s"));
   private final NumberSetting transOut = this.add(
      new NumberSetting("Ausblenddauer", "Wie lange das Ausblenden beim Zurückkommen dauert", 1.4, 1.0, 2.0, 0.1, "s")
   );
   private final BooleanSetting cameraPull = this.add(new BooleanSetting("Kamera-Rückzug", "Kamera zieht sich beim Übergang leicht zurück", true));
   private final BooleanSetting filter = this.add(new BooleanSetting("Wüsten-Filter", "Warmer, sandiger Farbschleier mit Vignette", true));
   private final NumberSetting filterStrength = this.add(new NumberSetting("Filterstärke", "Wie stark der Wüsten-Look ist", 60.0, 0.0, 100.0, 5.0, "%"));
   private final BooleanSetting letterbox = this.add(new BooleanSetting("Kinobalken", "Schwarze Balken oben und unten", true));
   private final BooleanSetting grain = this.add(new BooleanSetting("Filmkorn", "Leichtes Filmrauschen", true));
   private final BooleanSetting ambient = this.add(new BooleanSetting("Staub & Dunst", "Schwebende Sandkörner und Dunst", true));
   private final BooleanSetting battle = this.add(new BooleanSetting("Kampf-Simulation", "Die beiden Armeen kämpfen (aus = nur Landschaft)", true));
   private final ModeSetting density = this.add(
      new ModeSetting("Schlacht-Dichte", "Wie viele Einheiten – höher kostet mehr FPS", "Mittel", "Niedrig", "Mittel", "Hoch", "Ultra")
   );
   private final BooleanSetting flyers = this.add(new BooleanSetting("Flugeinheiten", "Landungsschiffe und Jäger am Himmel", true));
   private final BooleanSetting explosions = this.add(new BooleanSetting("Explosionen", "Einschläge, Raketen, Feuer und Rauch", true));
   private final BooleanSetting cameraMove = this.add(new BooleanSetting("Kamerabewegung", "Ganz langsames Schwenken und „Atmen“ der Kamera", true));
   private final BooleanSetting sit = this.add(new BooleanSetting("Charakter setzt sich", "Dein Charakter setzt sich auf den Felsen (aus = er steht)", true));
   private final NumberSetting viewAngle = this.add(
      new NumberSetting("Blickwinkel", "Wie weit man den Charakter von der Seite sieht (positiv = Blick nach rechts zur Schlacht)", 25.0, -60.0, 60.0, 5.0, "°")
   );
   private final BooleanSetting sound = this.add(new BooleanSetting("Sound", "Atmosphärische Geräusche (keine Musik)", true));
   private final NumberSetting volume = this.add(new NumberSetting("Lautstärke", "Gesamtlautstärke der Szene", 60.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting volExplosions = this.add(new NumberSetting("Lautstärke Explosionen", "Entfernte Einschläge", 80.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting volShots = this.add(new NumberSetting("Lautstärke Schüsse", "Entfernte Schüsse", 50.0, 0.0, 100.0, 5.0, "%"));
   private final NumberSetting volAmbience = this.add(new NumberSetting("Lautstärke Wind & Kampflärm", "Wind und dumpfes Grollen", 70.0, 0.0, 100.0, 5.0, "%"));
   private final BooleanSetting hud = this.add(new BooleanSetting("AFK-Anzeige", "Dezenter Text während der Szene", false));
   private final TextSetting hudText = this.add(new TextSetting("Text", "Was angezeigt wird", "Meowl • AFK", 32));
   private final ModeSetting hudPos = this.add(
      new ModeSetting("Position", "Wo der Text steht", "Unten Mitte", "Unten Mitte", "Unten links", "Unten rechts", "Oben Mitte")
   );
   private final NumberSetting hudOpacity = this.add(new NumberSetting("Deckkraft", "Wie sichtbar der Text ist", 45.0, 10.0, 100.0, 5.0, "%"));
   private final ColorSetting hudColor = this.add(new ColorSetting("Farbe", "Textfarbe", -7504, false));
   private final BooleanSetting hudTime = this.add(new BooleanSetting("AFK-Dauer anzeigen", "Zeigt, wie lange du schon AFK bist", true));
   private final McCanvas canvas = new McCanvas();
   private long lastInput = System.currentTimeMillis();
   private long ignoreMoveUntil;
   private long forceStartAt;
   private long afkSince;
   private boolean active;
   private float amount;
   private float fovAmount;
   private float sceneTime;
   private long lastFrame = System.nanoTime();
   private BattleSim sim;
   private BattleRenderer renderer;
   private BattleSounds sounds;
   private SpaceScene space;
   private ValleyScene valley;

   public DesertAfk() {
      super(
         "AFK-Szenen",
         "Bist du AFK, startet eine Szene: Wüstenschlacht, Sternenschlacht oder Tal des Endes (Naruto gegen Sasuke) – rein clientseitig.",
         Category.MISC
      );
      this.section(Section.MEOWL);
      this.icon(Items.RED_SAND);
      this.legacy("Wüstenschlacht AFK");
      instance = this;
      this.add(new ActionSetting("Vorschau", "Startet die Szene sofort (Menü schließt sich)", "Jetzt starten", this::startPreview));
      this.group("Szene", new Setting[]{this.scene, this.pcTheme, this.terraStart, this.terraHud, this.partnerSkin, this.partnerName, this.dateHearts, this.planet, this.shipHits});
      this.group("Andere Spieler", new Setting[]{this.shareAfk, this.showOthersAfk});
      this.pcTheme.setVisibility(this::isPc);
      this.terraStart.setVisibility(this::isTerra);
      this.terraHud.setVisibility(this::isTerra);
      this.partnerSkin.setVisibility(this::isDate);
      this.partnerName.setVisibility(() -> this.isDate() && "Spielername".equals(this.partnerSkin.get()));
      this.dateHearts.setVisibility(this::isDate);
      this.group("Auslöser", new Setting[]{this.afkTime, this.unit, this.endMouse, this.endKeys, this.endClick, this.ignored});
      this.group("Übergang", new Setting[]{this.startAnim, this.transIn, this.transOut, this.cameraPull});
      this.group("Wüsten-Look", new Setting[]{this.filter, this.filterStrength, this.letterbox, this.grain, this.ambient});
      this.group("Schlacht", new Setting[]{this.battle, this.density, this.flyers, this.explosions, this.cameraMove});
      this.group("Charakter", new Setting[]{this.sit, this.viewAngle});
      this.group("Sound", new Setting[]{this.sound, this.volume, this.volExplosions, this.volShots, this.volAmbience});
      this.group("AFK-Anzeige", new Setting[]{this.hud, this.hudText, this.hudPos, this.hudOpacity, this.hudColor, this.hudTime});
      this.filterStrength.setVisibility(() -> this.filter.get() && this.isDesert());
      this.filter.setVisibility(this::isDesert);
      this.ambient.setVisibility(this::isDesert);
      this.sit.setVisibility(this::isDesert);

      this.density.setVisibility(() -> !this.isValley() && !this.isTerra() && !this.isPc() && !this.isDate() && this.battle.get());
      this.flyers.setVisibility(() -> !this.isValley() && !this.isTerra() && !this.isPc() && !this.isDate());
      this.planet.setVisibility(this::isSpace);
      this.shipHits.setVisibility(this::isSpace);
      this.transIn.setVisibility(this.startAnim::get);

      for (Setting<?> s : new Setting[]{this.volume, this.volExplosions, this.volShots, this.volAmbience}) {
         s.setVisibility(this.sound::get);
      }

      for (Setting<?> s : new Setting[]{this.hudText, this.hudPos, this.hudOpacity, this.hudColor, this.hudTime}) {
         s.setVisibility(this.hud::get);
      }

      AfkInput.setListener(this);
   }

   public boolean isSpace() {
      return SPACE.equals(this.scene.get());
   }

   public boolean isDesert() {
      return DESERT.equals(this.scene.get());
   }

   public boolean isValley() {
      return VALLEY.equals(this.scene.get());
   }

   /** Tal des Endes schon vor dem AFK-Start im Hintergrund rendern, damit es sofort fertig ist. */
   private boolean isPc() {
      return PC.equals(this.scene.get());
   }

   private boolean isTerra() {
      return TERRA.equals(this.scene.get());
   }

   private boolean isDate() {
      return DATE.equals(this.scene.get());
   }

   /** E-Date-Landschaft schon vor dem AFK-Start im Hintergrund rendern. */
   private void ensureDate() {
      if (this.date == null) {
         this.date = new com.lennart.nebula.afk.date.DateScene();
      }

      this.date.prepare();
   }

   /** Skin der Person neben dir (Standard-Skin oder per Name geladen; bis er da ist: Alex). */
   private net.minecraft.world.entity.player.PlayerSkin partnerSkin() {
      String sel = this.partnerSkin.get();
      String name = this.partnerName.get().trim();
      if ("Spielername".equals(sel)) {
         if (com.lennart.nebula.module.mods.pet.SkinPet.validName(name)) {
            net.minecraft.world.entity.player.PlayerSkin s = com.lennart.nebula.module.mods.pet.SkinPet.textures(name);
            if (s != null) {
               return s;
            }
         }

         sel = "Alex";
      }

      if (!sel.equals(this.partnerKey) || this.partnerCache == null) {
         boolean slim = java.util.Set.of("Alex", "Noor", "Sunny", "Zuri", "Makena").contains(sel);
         net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.withDefaultNamespace(
            "entity/player/" + (slim ? "slim" : "wide") + "/" + sel.toLowerCase(Locale.ROOT)
         );
         this.partnerCache = net.minecraft.world.entity.player.PlayerSkin.insecure(
            new net.minecraft.core.ClientAsset.ResourceTexture(id), null, null, slim ? net.minecraft.world.entity.player.PlayerModelType.SLIM : net.minecraft.world.entity.player.PlayerModelType.WIDE
         );
         this.partnerKey = sel;
      }

      return this.partnerCache;
   }

   /** Terraria-Welt schon vor dem AFK-Start im Hintergrund backen. */
   private void ensureTerra() {
      if (this.terra == null) {
         this.terra = new com.lennart.nebula.afk.terra.TerraScene();
      }

      this.terra.prepare();
   }

   /** Läuft die AFK-Szene gerade (für das Meowl-Netzwerk)? */
   public static void presenceFields(com.google.gson.JsonObject o) {
      DesertAfk a = instance;
      if (a != null && a.isEnabled() && a.active && a.shareAfk.get()) {
         o.addProperty("afk", true);
      }
   }

   public static boolean showOthersAfk() {
      DesertAfk a = instance;
      return a == null || a.showOthersAfk.get();
   }

   private void ensureValley() {
      if (this.valley == null) {
         this.valley = new ValleyScene();
      }

      this.valley.prepare();
   }

   public static DesertAfk get() {
      return instance;
   }

   public static String keyName(String text) {
      String t = text.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('.', '_');
      return t.isEmpty() ? null : t;
   }

   private static String nameOf(int glfwKey) {
      String tk = Type.KEYSYM.getOrCreate(glfwKey).getName();
      String prefix = "key.keyboard.";
      return keyName(tk.startsWith(prefix) ? tk.substring(prefix.length()) : tk);
   }

   private long thresholdMs() {
      double v = this.afkTime.get();
      return this.unit.get().equals("Sekunden") ? Math.max(5000L, Math.round(v * 1000.0)) : Math.round(v * 60000.0);
   }

   private void startPreview() {
      if (!this.isEnabled()) {
         this.setEnabled(true);
      }

      this.mc.setScreen(null);
      this.forceStartAt = System.currentTimeMillis() + 500L;
   }

   public boolean isActive() {
      return this.active;
   }

   public float amount() {
      return this.amount;
   }

   public static float fovFactor() {
      DesertAfk a = instance;
      return a != null && a.isEnabled() && a.cameraPull.get() && a.fovAmount > 0.0F ? 1.0F + 0.1F * a.fovAmount : 1.0F;
   }

   @Override
   public void onInput(AfkInput.Kind kind, int code) {
      long now = System.currentTimeMillis();
      if (kind != AfkInput.Kind.KEY || code == InputConstants.KEY_ESCAPE || !this.ignored.get().contains(nameOf(code))) {
         this.lastInput = now;
         if (this.active) {
            boolean end = switch (kind) {
               case KEY -> code == InputConstants.KEY_ESCAPE || this.endKeys.get();
               case CLICK -> this.endClick.get();
               case SCROLL -> this.endMouse.get();
               case MOVE -> this.endMouse.get() && now >= this.ignoreMoveUntil;
            };
            if (end) {
               this.end();
            }
         }
      }
   }

   public void tickAlways(Minecraft client) {
      if (!this.isEnabled()) {
         if (this.amount > 0.0F || this.active || this.sim != null) {
            this.release();
         }
      } else if (client.player != null && client.level != null) {
         long now = System.currentTimeMillis();
         if (this.isSpace()) {
            this.ensureSpace();
         } else if (this.isValley()) {
            this.ensureValley();
         } else if (this.isTerra()) {
            this.ensureTerra();
         } else if (this.isDate()) {
            this.ensureDate();
         }

         if (this.active && client.screen != null) {
            this.end();
         }

         if (!this.active && client.screen == null && client.player.isAlive()) {
            boolean forced = this.forceStartAt > 0L && now >= this.forceStartAt;
            if (forced || now - this.lastInput >= this.thresholdMs()) {
               this.start();
            }
         }

         // Vorschau wartet, bis die Szene fertig gebaut ist (höchstens 15 s)
         if (this.forceStartAt > 0L && now >= this.forceStartAt && (this.active || now - this.forceStartAt > 15000L)) {
            this.forceStartAt = 0L;
         }
      } else {
         this.release();
         this.lastInput = System.currentTimeMillis();
      }
   }

   /** Sternenschlacht schon vor dem AFK-Start im Hintergrund bauen, damit sie sofort fertig ist. */
   private void ensureSpace() {
      PlanetPainter.Planet type = PlanetPainter.Planet.byLabel(this.planet.get());
      int dens = this.density.getIndex();
      if (this.space == null || this.space.planet() != type || this.space.density() != dens) {
         if (this.active || this.amount > 0.0F) {
            return;
         }

         if (this.space != null) {
            this.space.destroy();
         }

         this.space = new SpaceScene(type, dens);
      }

      this.space.prepare();
   }

   private void start() {
      if (this.terra != null) {
         this.terra.stopSounds();
      }

      if (this.isDate()) {
         this.ensureDate();
         if (!this.date.ready()) {
            return;
         }

         this.active = true;
         this.afkSince = this.lastInput;
         this.ignoreMoveUntil = System.currentTimeMillis() + 1300L;
         this.date.restart();
         if (this.sounds != null) {
            this.sounds.stop();
            this.sounds = null;
         }

         if (this.space != null) {
            this.space.stopSounds();
         }

         if (this.valley != null) {
            this.valley.stopSounds();
         }

         this.sim = null;
         this.renderer = null;
         return;
      }

      if (this.isTerra()) {
         this.ensureTerra();
         if (!this.terra.ready()) {
            return;
         }

         this.active = true;
         this.afkSince = this.lastInput;
         this.ignoreMoveUntil = System.currentTimeMillis() + 1300L;
         this.terra.restart(this.terraStart.getIndex(), this.battle.get());
         if (this.sounds != null) {
            this.sounds.stop();
            this.sounds = null;
         }

         if (this.space != null) {
            this.space.stopSounds();
         }

         if (this.valley != null) {
            this.valley.stopSounds();
         }

         this.sim = null;
         this.renderer = null;
         return;
      }

      if (this.isPc()) {
         if (this.pc == null) {
            this.pc = new com.lennart.nebula.afk.pc.PcScene();
         }

         this.active = true;
         this.afkSince = this.lastInput;
         this.ignoreMoveUntil = System.currentTimeMillis() + 1300L;
         this.pc.restart();
         if (this.sounds != null) {
            this.sounds.stop();
            this.sounds = null;
         }

         if (this.space != null) {
            this.space.stopSounds();
         }

         if (this.valley != null) {
            this.valley.stopSounds();
         }

         this.sim = null;
         this.renderer = null;
         return;
      }

      if (this.isValley()) {
         // Tal des Endes: erst starten, wenn die Landschaft fertig gerendert ist
         this.ensureValley();
         if (!this.valley.ready()) {
            return;
         }

         this.active = true;
         this.afkSince = this.lastInput;
         this.ignoreMoveUntil = System.currentTimeMillis() + 1300L;
         this.valley.restart();
         this.sceneTime = 0.0F;
         this.lastFrame = System.nanoTime();
         if (this.sounds != null) {
            this.sounds.stop();
            this.sounds = null;
         }

         if (this.space != null) {
            this.space.stopSounds();
         }

         this.sim = null;
         this.renderer = null;
         return;
      }

      if (this.isSpace()) {
         this.ensureSpace();
         if (this.space == null || !this.space.ready()) {
            return;
         }
      }

      this.active = true;
      this.afkSince = this.lastInput;
      this.ignoreMoveUntil = System.currentTimeMillis() + 1300L;
      if (this.isSpace()) {
         this.space.restartBattle();
         this.sceneTime = 0.0F;
         this.lastFrame = System.nanoTime();
         if (this.sounds != null) {
            this.sounds.stop();
            this.sounds = null;
         }

         this.sim = null;
         this.renderer = null;
         return;
      }

      if (this.space != null) {
         this.space.stopSounds();
      }

      if (this.valley != null) {
         this.valley.stopSounds();
      }

      if (this.sim == null || this.sim.density != BattleSim.Density.byIndex(this.density.getIndex())) {
         long seed = System.nanoTime();
         this.sim = new BattleSim(BattleSim.Density.byIndex(this.density.getIndex()), seed);
         this.renderer = new BattleRenderer(seed);
         this.sim.configure(this.battle.get(), this.flyers.get(), this.explosions.get());
         this.sim.prewarm(10.0F);
         this.sceneTime = 0.0F;
      }

      if (this.sounds == null) {
         this.sounds = new BattleSounds();
      }

      this.sim.setEvents(this.sounds);
      this.lastFrame = System.nanoTime();
   }

   private void end() {
      this.active = false;
      this.lastInput = System.currentTimeMillis();
      SitPose.setAfk(false);
   }

   private void release() {
      this.active = false;
      this.amount = 0.0F;
      this.fovAmount = 0.0F;
      SitPose.setAfk(false);
      if (this.sounds != null) {
         this.sounds.stop();
      }

      this.sounds = null;
      this.sim = null;
      this.renderer = null;
      if (this.space != null) {
         this.space.stopSounds();
      }

      if (this.valley != null) {
         this.valley.stopSounds();
      }

      if (this.terra != null) {
         this.terra.stopSounds();
      }
   }

   @Override
   protected void onDisable() {
      this.release();
      if (this.space != null) {
         this.space.destroy();
         this.space = null;
      }

      if (this.valley != null) {
         this.valley.destroy();
         this.valley = null;
      }

      if (this.terra != null) {
         this.terra.destroy();
         this.terra = null;
      }

      if (this.date != null) {
         this.date.destroy();
         this.date = null;
      }
   }

   private static float ease(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }

   public void renderOverlay(GuiGraphics ctx) {
      long nowNs = System.nanoTime();
      float dt = Math.min(0.1F, (float)(nowNs - this.lastFrame) / 1.0E9F);
      this.lastFrame = nowNs;
      if (this.isEnabled() && this.date != null && this.isDate() && this.sim == null && this.mc.player != null && (this.active || this.amount > 0.0F)) {
         this.renderDate(ctx, dt);
      } else if (this.isEnabled() && this.terra != null && this.isTerra() && this.sim == null && this.mc.player != null && (this.active || this.amount > 0.0F)) {
         this.renderTerra(ctx, dt);
      } else if (this.isEnabled() && this.pc != null && this.isPc() && this.sim == null && this.mc.player != null && (this.active || this.amount > 0.0F)) {
         this.renderPc(ctx, dt);
      } else if (this.isEnabled() && this.valley != null && this.isValley() && this.sim == null && this.mc.player != null && (this.active || this.amount > 0.0F)) {
         this.renderValley(ctx, dt);
      } else if (this.isEnabled() && this.space != null && this.isSpace() && this.sim == null && this.mc.player != null && (this.active || this.amount > 0.0F)) {
         this.renderSpace(ctx, dt);
      } else if (this.isEnabled() && this.renderer != null && this.sim != null && this.mc.player != null) {
         float inTime = this.startAnim.get() ? this.transIn.get().floatValue() : 0.4F;
         float outTime = this.transOut.get().floatValue();
         if (this.active) {
            this.amount = Math.min(1.0F, this.amount + dt / inTime);
         } else {
            this.amount = Math.max(0.0F, this.amount - dt / outTime);
         }

         if (!this.active && this.amount <= 0.0F) {
            this.release();
         } else {
            float e = this.amount;
            float filterAmt = ease(e / 0.4F);
            this.fovAmount = ease(e / 0.55F);
            float sceneA = ease((e - 0.3F) / 0.5F);
            float playerVis = ease((e - 0.8F) / 0.2F);
            float bars = ease((e - 0.25F) / 0.6F);
            float introZoom = 1.0F + 0.12F * (1.0F - sceneA);
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            this.sceneTime += dt;
            this.sim.configure(this.battle.get(), this.flyers.get(), this.explosions.get());
            this.sounds
               .configure(
                  this.sound.get(),
                  this.volume.get().floatValue() / 100.0F,
                  this.volExplosions.get().floatValue() / 100.0F,
                  this.volShots.get().floatValue() / 100.0F,
                  this.volAmbience.get().floatValue() / 100.0F
               );
            if (sceneA > 0.0F) {
               this.sim.update(dt);
            }

            this.sounds.update(sceneA);
            SitPose.setAfk(this.active && this.sit.get() && e >= 0.8F);
            McCanvas c = this.canvas.bind(ctx);
            this.renderer.setup(w, h, this.sceneTime, this.cameraMove.get(), introZoom, sceneA);
            if (sceneA > 0.002F) {
               this.renderer.drawScene(c, this.sim);
            }

            if (sceneA >= 0.995F && playerVis > 0.0F) {
               int[] box = this.drawPlayer(ctx, h);
               ctx.nextStratum();
               if (box != null && playerVis < 1.0F) {
                  this.renderer.drawCover(c, box[0], box[1], box[2], box[3], 1.0F - playerVis);
               }
            }

            float strength = this.filter.get() ? this.filterStrength.get().floatValue() / 100.0F : 0.0F;
            this.renderer
               .drawAtmosphere(
                  c, w, h, this.sceneTime, filterAmt * strength, this.ambient.get() ? filterAmt : 0.0F, this.grain.get(), this.letterbox.get() ? bars : 0.0F
               );
            if (this.hud.get() && sceneA > 0.05F) {
               ctx.nextStratum();
               this.drawHud(ctx, w, h, sceneA, this.letterbox.get() ? bars : 0.0F);
            }
         }
      } else if ((this.amount > 0.0F || this.sim != null) && (!this.isEnabled() || this.mc.player == null)) {
         this.release();
      }
   }

   private int[] drawPlayer(GuiGraphics ctx, int h) {
      return this.drawPlayer(ctx, h, this.renderer.playerX, this.renderer.playerFeetY, this.renderer.playerBlock, this.sit.get(), this.viewAngle.get().floatValue(), -0.22F);
   }

   private int[] drawPlayer(GuiGraphics ctx, int h, int px, int feet, int size, boolean ledge, float bodyYaw, float tilt) {
      LocalPlayer player = this.mc.player;
      if (player == null) {
         return null;
      } else {
         try {
            EntityRenderDispatcher manager = this.mc.getEntityRenderDispatcher();
            EntityRenderer<? super LocalPlayer, ?> entityRenderer = manager.getRenderer(player);
            EntityRenderState state = entityRenderer.createRenderState(player, 1.0F);
            state.lightCoords = 15728880;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.nameTag = null;
            if (state instanceof LivingEntityRenderState living) {
               living.bodyRot = bodyYaw;
               living.yRot = -6.0F;
               living.xRot = 4.0F;
               living.walkAnimationSpeed = 0.0F;
               living.walkAnimationPos = 0.0F;
               living.hasRedOverlay = false;
               living.deathTime = 0.0F;
               living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
               living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
               living.scale = 1.0F;
            }

            int x1 = px - size * 2;
            int x2 = px + size * 2;
            int y1 = feet - Math.round(size * 2.4F);
            int y2 = Math.min(h, feet + Math.round(size * 0.6F));
            float cy = (y1 + y2) / 2.0F;
            Quaternionf camera = new Quaternionf().rotateX(tilt);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
            ctx.fill(px - Math.round(size * 0.42F), feet - 1, px + Math.round(size * 0.42F), feet + 1, 1428163588);
            if (ledge) {
               ctx.enableScissor(x1, y1, x2, feet + 1);
            }

            ctx.submitEntityRenderState(state, size, new Vector3f(0.0F, (feet - cy) / size, 0.0F), rotation, camera, x1, y1, x2, y2);
            if (ledge) {
               ctx.disableScissor();
            }

            return new int[]{x1, y1, x2, y2};
         } catch (RuntimeException var18) {
            return null;
         }
      }
   }

   /** PC-Setup: Schreibtisch im Minecraft-Stil, Hände an Tastatur und Maus. */
   private void renderPc(GuiGraphics ctx, float dt) {
      float inTime = this.startAnim.get() ? this.transIn.get().floatValue() : 0.4F;
      float outTime = this.transOut.get().floatValue();
      if (this.active) {
         this.amount = Math.min(1.0F, this.amount + dt / inTime);
      } else {
         this.amount = Math.max(0.0F, this.amount - dt / outTime);
      }

      if (!this.active && this.amount <= 0.0F) {
         this.release();
         return;
      }

      float e = this.amount;
      this.fovAmount = ease(e / 0.55F);
      float sceneA = ease((e - 0.3F) / 0.5F);
      int w = ctx.guiWidth();
      int h = ctx.guiHeight();
      SitPose.setAfk(false);
      if (sceneA > 0.002F) {
         this.pc.render(ctx, w, h, dt, this.pcTheme.get(), sceneA);
      }

      // Ein-/Ausblenden über schwarz (immer obenauf, damit die Szene danach voll hell ist)
      float black = sceneA > 0.002F ? 1.0F - sceneA : Math.min(1.0F, ease(e / 0.3F));
      if (black > 0.004F) {
         ctx.nextStratum();
         ctx.fill(0, 0, w, h, Math.round(black * 255.0F) << 24);
      }

      if (this.hud.get() && sceneA > 0.05F) {
         ctx.nextStratum();
         this.drawHud(ctx, w, h, sceneA, 0.0F);
      }
   }

   /** E-Date: zu zweit auf der Decke an der Klippe, die Sonne geht über dem Meer auf. */
   private void renderDate(GuiGraphics ctx, float dt) {
      float inTime = this.startAnim.get() ? this.transIn.get().floatValue() : 0.4F;
      float outTime = this.transOut.get().floatValue();
      if (this.active) {
         this.amount = Math.min(1.0F, this.amount + dt / inTime);
      } else {
         this.amount = Math.max(0.0F, this.amount - dt / outTime);
      }

      if (!this.active && this.amount <= 0.0F) {
         this.release();
         return;
      }

      float e = this.amount;
      this.fovAmount = ease(e / 0.55F);
      float sceneA = ease((e - 0.3F) / 0.5F);
      float bars = this.letterbox.get() ? ease((e - 0.25F) / 0.6F) : 0.0F;
      int w = ctx.guiWidth();
      int h = ctx.guiHeight();
      SitPose.setAfk(this.active && sceneA > 0.5F);
      boolean drawn = sceneA > 0.002F && this.date.render(ctx, w, h, dt, sceneA, this.dateHearts.get());
      if (drawn) {
         float[] seat = this.date.seat;
         int size = Math.max(8, Math.round(seat[3]));
         int feet = Math.round(seat[2]);
         // leicht zueinander gedreht; ab und zu schauen sie sich an
         float t = (System.currentTimeMillis() % 600000L) / 1000.0F;
         float look = Math.max(0.0F, (float)Math.sin(t * 0.21F) * 1.6F - 0.6F);
         ctx.nextStratum();
         this.drawDatePlayer(ctx, Math.round(seat[1]), feet, size, -8.0F, -26.0F * look, this.partnerSkin());
         ctx.nextStratum();
         this.drawDatePlayer(ctx, Math.round(seat[0]), feet, size, 8.0F, 26.0F * look, null);
         ctx.nextStratum();
         this.date.renderFront(ctx, w, h, dt, this.dateHearts.get());
         int edge = Math.max(8, w / 9);

         for (int i = 0; i < 8; i++) {
            int a = Math.round(50.0F * (1.0F - i / 8.0F));
            int sw = edge / 8;
            ctx.fill(i * sw, 0, (i + 1) * sw, h, a << 24);
            ctx.fill(w - (i + 1) * sw, 0, w - i * sw, h, a << 24);
         }
      } else if (sceneA > 0.002F) {
         ctx.drawCenteredString(this.mc.font, "Der Morgen bricht an …", w / 2, h / 2, 0xFF808080);
      }

      ctx.nextStratum();
      int bar = Math.round(h * 0.075F * bars);
      if (bar > 0) {
         ctx.fill(0, 0, w, bar, -16777216);
         ctx.fill(0, h - bar, w, h, -16777216);
      }

      float black = drawn ? 1.0F - sceneA : Math.min(1.0F, ease(e / 0.3F));
      if (black > 0.004F) {
         ctx.fill(0, 0, w, h, Math.round(black * 255.0F) << 24);
      }

      if (this.hud.get() && sceneA > 0.05F) {
         ctx.nextStratum();
         this.drawHud(ctx, w, h, sceneA, bars);
      }
   }

   /** Sitzende Figur von hinten; mit {@code skin} wird der Skin ersetzt und Kosmetik weggelassen (Partner). */
   private void drawDatePlayer(GuiGraphics ctx, int px, int feet, int size, float bodyYaw, float headYaw, net.minecraft.world.entity.player.PlayerSkin skin) {
      LocalPlayer player = this.mc.player;
      if (player == null) {
         return;
      }

      try {
         EntityRenderDispatcher manager = this.mc.getEntityRenderDispatcher();
         EntityRenderer<? super LocalPlayer, ?> entityRenderer = manager.getRenderer(player);
         EntityRenderState state = entityRenderer.createRenderState(player, 1.0F);
         state.lightCoords = 15728880;
         state.shadowPieces.clear();
         state.outlineColor = 0;
         state.nameTag = null;
         if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = bodyYaw;
            living.yRot = headYaw;
            living.xRot = -4.0F;
            living.walkAnimationSpeed = 0.0F;
            living.walkAnimationPos = 0.0F;
            living.hasRedOverlay = false;
            living.deathTime = 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
         }

         if (state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid) {
            humanoid.rightHandItemState.clear();
            humanoid.leftHandItemState.clear();
            humanoid.rightHandItemStack = net.minecraft.world.item.ItemStack.EMPTY;
            humanoid.leftHandItemStack = net.minecraft.world.item.ItemStack.EMPTY;
         }

         if (skin != null && state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar) {
            avatar.skin = skin;
            avatar.showCape = false;
            avatar.headEquipment = net.minecraft.world.item.ItemStack.EMPTY;
            avatar.chestEquipment = net.minecraft.world.item.ItemStack.EMPTY;
            avatar.legsEquipment = net.minecraft.world.item.ItemStack.EMPTY;
            avatar.feetEquipment = net.minecraft.world.item.ItemStack.EMPTY;
            avatar.headItem.clear();
            avatar.parrotOnLeftShoulder = null;
            avatar.parrotOnRightShoulder = null;
            com.lennart.nebula.cosmetic.PreviewOverrides.put(state, PARTNER, null);
         }

         int x1 = px - size * 2;
         int x2 = px + size * 2;
         int y1 = feet - Math.round(size * 2.4F);
         int y2 = feet + Math.round(size * 0.6F);
         float cy = (y1 + y2) / 2.0F;
         Quaternionf camera = new Quaternionf().rotateX(-0.3F);
         Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).mul(camera);
         // Beine hängen über die Kante: unterhalb der Kante abschneiden
         ctx.enableScissor(x1, y1, x2, feet + 1);
         ctx.submitEntityRenderState(state, size, new Vector3f(0.0F, (feet - cy) / size, 0.0F), rotation, camera, x1, y1, x2, y2);
         ctx.disableScissor();
      } catch (RuntimeException var14) {
      }
   }

   /** Terraria-Welt: Diorama-Schnitt mit Terraria-HUD, Vignette und Kinobalken. */
   private void renderTerra(GuiGraphics ctx, float dt) {
      float inTime = this.startAnim.get() ? this.transIn.get().floatValue() : 0.4F;
      float outTime = this.transOut.get().floatValue();
      if (this.active) {
         this.amount = Math.min(1.0F, this.amount + dt / inTime);
      } else {
         this.amount = Math.max(0.0F, this.amount - dt / outTime);
      }

      if (!this.active && this.amount <= 0.0F) {
         this.release();
         return;
      }

      float e = this.amount;
      this.fovAmount = ease(e / 0.55F);
      float sceneA = ease((e - 0.3F) / 0.5F);
      float bars = this.letterbox.get() ? ease((e - 0.25F) / 0.6F) : 0.0F;
      int w = ctx.guiWidth();
      int h = ctx.guiHeight();
      SitPose.setAfk(false);
      this.terra.sounds(this.sound.get(), this.volume.get().floatValue() / 100.0F, Math.max(this.volShots.get().floatValue(), this.volExplosions.get().floatValue()) / 100.0F, sceneA);
      ctx.fill(0, 0, w, h, Math.round(Math.min(1.0F, ease(e / 0.3F)) * 255.0F) << 24);
      int bar = Math.round(h * 0.075F * bars);
      boolean drawn = sceneA > 0.002F && this.terra.render(ctx, dt, this.cameraMove.get(), this.battle.get(), bar, this.terraHud.get());
      ctx.nextStratum();
      if (drawn) {
         int edge = Math.max(8, w / 10);

         for (int i = 0; i < 8; i++) {
            int a = Math.round(55.0F * (1.0F - i / 8.0F));
            int sw = edge / 8;
            ctx.fill(i * sw, 0, (i + 1) * sw, h, a << 24);
            ctx.fill(w - (i + 1) * sw, 0, w - i * sw, h, a << 24);
         }
      } else if (sceneA > 0.002F) {
         ctx.drawCenteredString(this.mc.font, "Baue die Welt …", w / 2, h / 2, 0xFF808080);
      }

      if (bar > 0) {
         ctx.fill(0, 0, w, bar, -16777216);
         ctx.fill(0, h - bar, w, h, -16777216);
      }

      // Ein-/Ausblenden über schwarz
      float black = 1.0F - sceneA;
      if (black > 0.004F && drawn) {
         ctx.fill(0, 0, w, h, Math.round(black * 255.0F) << 24);
      }

      if (this.hud.get() && sceneA > 0.05F) {
         ctx.nextStratum();
         this.drawHud(ctx, w, h, sceneA, bars);
      }
   }

   /** Tal des Endes: Plateau über dem Fluss, Naruto gegen Sasuke, stehender Charakter, Kino-Look. */
   private void renderValley(GuiGraphics ctx, float dt) {
      float inTime = this.startAnim.get() ? this.transIn.get().floatValue() : 0.4F;
      float outTime = this.transOut.get().floatValue();
      if (this.active) {
         this.amount = Math.min(1.0F, this.amount + dt / inTime);
      } else {
         this.amount = Math.max(0.0F, this.amount - dt / outTime);
      }

      if (!this.active && this.amount <= 0.0F) {
         this.release();
         return;
      }

      float e = this.amount;
      this.fovAmount = ease(e / 0.55F);
      float sceneA = ease((e - 0.3F) / 0.5F);
      float playerVis = ease((e - 0.8F) / 0.2F);
      float bars = ease((e - 0.25F) / 0.6F);
      int w = ctx.guiWidth();
      int h = ctx.guiHeight();
      SitPose.setAfk(false);
      this.valley.configure(this.battle.get(), this.explosions.get());
      this.valley.sounds(
         this.sound.get(),
         this.volume.get().floatValue() / 100.0F,
         this.volExplosions.get().floatValue() / 100.0F,
         this.volShots.get().floatValue() / 100.0F,
         this.volAmbience.get().floatValue() / 100.0F,
         sceneA
      );
      // dunkle Überblendung, während die Szene gebaut wird
      ctx.fill(0, 0, w, h, Math.round(Math.min(1.0F, ease(e / 0.3F)) * 255.0F) << 24);
      boolean drawn = sceneA > 0.002F && this.valley.render(ctx, dt, this.cameraMove.get(), sceneA);
      if (drawn && sceneA >= 0.995F && playerVis > 0.0F) {
         ValleyView v = this.valley.view();
         ctx.nextStratum();
         this.drawPlayer(ctx, h, Math.round(v.playerX), Math.round(v.playerFeetY), Math.max(8, Math.round(v.playerBlock)), false, this.viewAngle.get().floatValue() * 0.4F, -0.2F);
      }

      // Vignette, Filmkorn, Kinobalken
      ctx.nextStratum();
      int edge = Math.max(8, w / 9);

      for (int i = 0; i < 8; i++) {
         int a = Math.round(60.0F * (1.0F - i / 8.0F) * sceneA);
         int sw = edge / 8;
         ctx.fill(i * sw, 0, (i + 1) * sw, h, a << 24);
         ctx.fill(w - (i + 1) * sw, 0, w - i * sw, h, a << 24);
      }

      if (this.grain.get()) {
         java.util.Random r = new java.util.Random(System.nanoTime());

         for (int k = 0; k < 90; k++) {
            int px = r.nextInt(Math.max(1, w));
            int py = r.nextInt(Math.max(1, h));
            ctx.fill(px, py, px + 1, py + 1, Math.round(16.0F * sceneA) << 24 | (k % 2 == 0 ? 16777215 : 0));
         }
      }

      if (this.letterbox.get() && bars > 0.001F) {
         int bar = Math.round(h * 0.075F * bars);
         ctx.fill(0, 0, w, bar, -16777216);
         ctx.fill(0, h - bar, w, h, -16777216);
      }

      if (this.hud.get() && sceneA > 0.05F) {
         ctx.nextStratum();
         this.drawHud(ctx, w, h, sceneA, this.letterbox.get() ? bars : 0.0F);
      }
   }

   /** Sternenschlacht: Brücke, Schlacht, stehender Charakter, Kino-Look. */
   private void renderSpace(GuiGraphics ctx, float dt) {
      float inTime = this.startAnim.get() ? this.transIn.get().floatValue() : 0.4F;
      float outTime = this.transOut.get().floatValue();
      if (this.active) {
         this.amount = Math.min(1.0F, this.amount + dt / inTime);
      } else {
         this.amount = Math.max(0.0F, this.amount - dt / outTime);
      }

      if (!this.active && this.amount <= 0.0F) {
         this.release();
         return;
      }

      float e = this.amount;
      this.fovAmount = ease(e / 0.55F);
      float sceneA = ease((e - 0.3F) / 0.5F);
      float playerVis = ease((e - 0.8F) / 0.2F);
      float bars = ease((e - 0.25F) / 0.6F);
      int w = ctx.guiWidth();
      int h = ctx.guiHeight();
      SitPose.setAfk(false);
      this.space.configure(this.battle.get(), this.flyers.get(), this.explosions.get(), this.shipHits.get());
      this.space.sounds(
         this.sound.get(),
         this.volume.get().floatValue() / 100.0F,
         this.volExplosions.get().floatValue() / 100.0F,
         this.volShots.get().floatValue() / 100.0F,
         this.volAmbience.get().floatValue() / 100.0F,
         sceneA
      );
      // dunkle Überblendung, während die Szene gebaut wird
      ctx.fill(0, 0, w, h, Math.round(Math.min(1.0F, ease(e / 0.3F)) * 255.0F) << 24);
      boolean drawn = sceneA > 0.002F && this.space.render(ctx, dt, this.cameraMove.get(), sceneA, true);

      if (drawn && sceneA >= 0.995F && playerVis > 0.0F) {
         SpaceView v = this.space.view();
         ctx.nextStratum();
         // ohne schwarzen Abdeck-Kasten (der war beim Ein-/Ausblenden als Rechteck zu sehen)
         this.drawPlayer(ctx, h, Math.round(v.playerX), Math.round(v.playerFeetY), Math.max(8, Math.round(v.playerBlock)), false, this.viewAngle.get().floatValue() * 0.4F, -0.36F);
      }

      // Vignette, Filmkorn, Kinobalken
      ctx.nextStratum();
      int edge = Math.max(8, w / 9);

      for (int i = 0; i < 8; i++) {
         int a = Math.round(70.0F * (1.0F - i / 8.0F) * sceneA);
         int sw = edge / 8;
         ctx.fill(i * sw, 0, (i + 1) * sw, h, a << 24);
         ctx.fill(w - (i + 1) * sw, 0, w - i * sw, h, a << 24);
      }

      if (this.grain.get()) {
         java.util.Random r = new java.util.Random(System.nanoTime());

         for (int k = 0; k < 90; k++) {
            int px = r.nextInt(Math.max(1, w));
            int py = r.nextInt(Math.max(1, h));
            ctx.fill(px, py, px + 1, py + 1, Math.round(18.0F * sceneA) << 24 | (k % 2 == 0 ? 16777215 : 0));
         }
      }

      if (this.letterbox.get() && bars > 0.001F) {
         int bar = Math.round(h * 0.075F * bars);
         ctx.fill(0, 0, w, bar, -16777216);
         ctx.fill(0, h - bar, w, h, -16777216);
      }

      if (this.hud.get() && sceneA > 0.05F) {
         ctx.nextStratum();
         this.drawHud(ctx, w, h, sceneA, this.letterbox.get() ? bars : 0.0F);
      }
   }

   private void drawHud(GuiGraphics ctx, int w, int h, float sceneA, float bars) {
      String text = this.hudText.get();
      if (this.hudTime.get()) {
         long secs = Math.max(0L, (System.currentTimeMillis() - this.afkSince) / 1000L);
         text = text + "  " + String.format(Locale.ROOT, "%d:%02d", secs / 60L, secs % 60L);
      }

      int tw = this.mc.font.width(text);
      int bar = Math.round(h * 0.075F * bars);
      String pos = this.hudPos.get();

      int x = switch (pos) {
         case "Unten links" -> 12;
         case "Unten rechts" -> w - tw - 12;
         default -> (w - tw) / 2;
      };
      int y = pos.equals("Oben Mitte") ? Math.max(8, bar + 6) : h - Math.max(14, bar + 12);
      float op = this.hudOpacity.get().floatValue() / 100.0F * sceneA;
      int color = Math.round(op * 255.0F) << 24 | this.hudColor.get() & 16777215;
      ctx.drawString(this.mc.font, text, x, y, color, false);
   }
}
