package com.lennart.nebula.gui;

import com.google.gson.JsonObject;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.mods.ClientSettings;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Meowl-Hauptmenü statt des Minecraft-Titelbildschirms: interaktiver Sternenhimmel (Sternbilder zur Maus, Klick =
 * Welle + Sternschnuppe), Planet mit Ring und Mond (Klick lässt ihn schneller drehen), der Meowl-Avatar mit Sprechblase,
 * Begrüßung je nach Tageszeit und „Weiterspielen“ auf dem zuletzt besuchten Server. Festes Layout.
 */
public final class MeowlTitleScreen extends Screen {
   /** Klicks notfalls selbst auslösen, falls eine andere Mod sie im Menü abfängt. */
   private final ClickFallback clickFallback = new ClickFallback();

   /** 26.x: keine Texteingabe nötig – sonst nimmt die Windows-Texteingabe Minecraft Fokus und Klicks weg. */
   protected boolean textInput() {
      return false;
   }
   private static final int ACCENT = 0xFFB57BFF;
   private static final String[] TIPS = {
      "Klick in den Himmel für Sternschnuppen ✦",
      "Rechte Shift-Taste öffnet das Meowl-Menü",
      "B halten öffnet das Emote-Rad",
      "Klick auf den Planeten …",
      "Miau! Schön, dass du da bist",
      "Mit G schaust du dir dein Messer an"
   };
   private final long opened = System.currentTimeMillis();
   private final List<MeowlTitleScreen.Hit> hits = new ArrayList<>();
   private final float[] hover = new float[8];
   /** Übergang beim Verlassen: Warp-Animation, danach wird die Aktion ausgeführt. */
   private Runnable pending;
   private long transitionAt;
   private float warpX;
   private float warpY;
   private final SkinModelDraw skinDraw = new SkinModelDraw();
   private java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> skin;
   private float skinYaw = 25.0F;
   /** Skin hat einmal einen Fehler geworfen (z.B. durch eine andere Skin-Mod): für diese Sitzung aus. */
   private static boolean skinBroken;
   private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();

   /** Einen Zeichenteil abgesichert ausführen: Fehler nur einmal melden, der Rest des Menüs läuft weiter. */
   private void safe(String part, Runnable r) {
      try {
         r.run();
      } catch (RuntimeException | LinkageError var4) {
         if ("Skin".equals(part)) {
            skinBroken = true;
         }

         if (REPORTED.add(part)) {
            NebulaClient.LOGGER.warn("Meowl-Hauptmenü: {} konnte nicht gezeichnet werden", part, var4);
         }
      }
   }

   /** Klickflächen von Menü und Umschalter (gleiche Positionen wie beim Zeichnen). */
   private void layoutHits(int w, int h) {
      List<MeowlTitleScreen.Entry> list = this.entries();
      int bh = 26;
      int gap = 7;
      int y0 = Math.max(150, h / 2 - (list.size() * (bh + gap)) / 2 + 30);
      for (int i = 0; i < list.size(); i++) {
         this.hits.add(new MeowlTitleScreen.Hit(28, y0 + i * (bh + gap), 190, bh, list.get(i).action()));
      }

      String note = "Nicht von Mojang oder Microsoft";
      String label = "Meowl-Menü";
      int sx = w - 22 - this.font.width(note) - 16 - 26;
      this.hits.add(new MeowlTitleScreen.Hit(sx - 6 - this.font.width(label), h - 26, this.font.width(label) + 6 + 26, 18, () -> {
         ClientSettings.setMeowlTitle(false);
         this.minecraft.setScreen(new TitleScreen());
      }));
   }

   /** Layout-Skalierung (1 = normal, kleiner bei kleinen Fenstern). */
   private float ls = 1.0F;
   // Schwerelos-Ragdoll: Position (Mitte des Modells), Geschwindigkeit, Drehung
   private final SkinRag rag = new SkinRag();
   private boolean ragInit;
   private float rx;
   private float ry;
   private float vx;
   private float vy;
   private float rollVel;
   private float spinVel;
   private boolean grabbed;
   private float grabDx;
   private float grabDy;
   private long releasedAt;
   private long lastPhys = System.nanoTime();
   private long lastSkinClick;
   private int skinBoxW;
   private int skinBoxH;
   private float lastMx;
   private float lastMy;
   private float spin;
   private float spinBoost;
   private int planetX;
   private int planetY;
   private int planetR;

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }

   private record Entry(String icon, String label, Runnable action) {
   }

   public MeowlTitleScreen() {
      super(Component.literal("Meowl Client"));
   }

   /**
    * Wird von setScreen aufgerufen: Minecraft-Titelbildschirm und -Serverliste durch die Meowl-Fassungen ersetzen;
    * schließt sich ein Meowl-Menü ohne Welt, geht es zurück ins Meowl-Hauptmenü statt ins Minecraft-Menü.
    */
   public static Screen replace(Screen next) {
      // erster Start: erst fragen, welches Hauptmenü man will
      if (next instanceof TitleScreen && ClientSettings.askMenu()) {
         return new MenuChoiceScreen();
      }

      if (!ClientSettings.meowlTitle()) {
         return next;
      }

      Minecraft mc = Minecraft.getInstance();
      if (next instanceof TitleScreen) {
         return new MeowlTitleScreen();
      } else if (next instanceof JoinMultiplayerScreen) {
         return new MeowlMultiplayerScreen();
      } else if (next == null && mc.level == null && mc.screen != null && mc.screen.getClass().getName().startsWith("com.lennart.nebula.")) {
         return new MeowlTitleScreen();
      } else {
         return next;
      }
   }

   /** Merkt sich den zuletzt besuchten Server für „Weiterspielen“. */
   public static void registerHooks() {
      // im Minecraft-Menü: kleiner Knopf zurück zum Meowl-Hauptmenü
      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, sw, shh) -> {
         // Sicherheitsnetz: andere Mods (z.B. Essential, FancyMenu) setzen den Titelbildschirm manchmal an setScreen
         // vorbei – dann nachträglich durch das Meowl-Menü bzw. die Abfrage beim ersten Start ersetzen
         if (screen instanceof TitleScreen && (ClientSettings.meowlTitle() || ClientSettings.askMenu())) {
            if (REPORTED.add("swap:" + screen.getClass().getName())) {
               NebulaClient.LOGGER.info("Meowl-Hauptmenü: Titelbildschirm ({}) kam an setScreen vorbei – wird ersetzt", screen.getClass().getName());
            }

            client.execute(() -> {
               if (client.screen == screen) {
                  client.setScreen(ClientSettings.askMenu() ? new MenuChoiceScreen() : new MeowlTitleScreen());
               }
            });
            return;
         }

         if (screen instanceof JoinMultiplayerScreen && ClientSettings.meowlTitle()) {
            if (REPORTED.add("swap:" + screen.getClass().getName())) {
               NebulaClient.LOGGER.info("Meowl-Serverliste: Mehrspieler-Bildschirm ({}) kam an setScreen vorbei – wird ersetzt", screen.getClass().getName());
            }

            client.execute(() -> {
               if (client.screen == screen) {
                  client.setScreen(new MeowlMultiplayerScreen());
               }
            });
            return;
         }

         if (screen instanceof TitleScreen && !ClientSettings.meowlTitle()) {
            net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen)
               .add(net.minecraft.client.gui.components.Button.builder(Component.literal("✦ Meowl-Menü"), b -> {
                  ClientSettings.setMeowlTitle(true);
                  client.setScreen(new MeowlTitleScreen());
               }).bounds(4, 4, 96, 20).build());
         }
      });
      ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
         ServerData sd = client.getCurrentServer();
         if (sd != null && !sd.isLan() && sd.ip != null && !sd.ip.isBlank()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", sd.name == null ? sd.ip : sd.name);
            o.addProperty("ip", sd.ip);
            client.execute(() -> ToolState.put("last_server", o));
         }
      });
   }

   static void connect(Screen parent, ServerData d) {
      Minecraft mc = Minecraft.getInstance();
      ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(d.ip), d, false, null);
   }

   @Override
   protected void init() {
      if (this.skin == null) {
         try {
            this.skin = this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), false);
         } catch (RuntimeException var2) {
         }
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
   }

   // ================================================================== Zeichnen

   static float ease(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
   }

   static int mix(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   private static int a(int rgb, float alpha) {
      return MeowlSpace.withAlpha(rgb, alpha);
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.clickFallback.pump(this, this.minecraft);
      this.hits.clear();
      float t = (System.currentTimeMillis() - this.opened) / 1000.0F;
      float intro = ease(t / 0.9F);
      MeowlSpace.draw(ctx, this.width, this.height, mouseX, mouseY);
      // festes Layout für mindestens 640x360: bei kleinen Fenstern/großer GUI wird alles verkleinert, damit nichts herausfällt
      this.ls = Math.min(1.0F, Math.min(this.width / 640.0F, this.height / 360.0F));
      int w = Math.round(this.width / this.ls);
      int h = Math.round(this.height / this.ls);
      int mx = Math.round(mouseX / this.ls);
      int my = Math.round(mouseY / this.ls);
      // Klickflächen zuerst (unabhängig vom Zeichnen): selbst wenn ein Teil beim Zeichnen scheitert, bleibt alles klickbar
      this.layoutHits(w, h);
      ctx.pose().pushMatrix();
      ctx.pose().scale(this.ls, this.ls);
      this.safe("Planet", () -> this.planet(ctx, w, h, t, intro, mx, my));
      if (!skinBroken) {
         this.safe("Skin", () -> this.skin(ctx, w, h, t, mx, my));
      }

      this.safe("Logo", () -> this.logo(ctx, t, mx, my));
      this.safe("Uhr", () -> this.greeting(ctx, w, t));
      this.safe("Menü", () -> this.menu(ctx, h, t, mx, my));
      this.safe("Fußzeile", () -> this.footer(ctx, w, h, t));
      if (intro < 1.0F) {
         ctx.fill(0, 0, w, h, a(0x05060F, 1.0F - intro));
      }

      this.safe("Übergang", () -> this.warp(ctx, w, h));
      this.runPendingIfDue();
      ctx.pose().popMatrix();
   }

   /** Hyperraum-Sprung: Lichtstreifen schießen vom angeklickten Punkt nach außen, das Bild blendet in Dunkel. */
   private void warp(GuiGraphics ctx, int w, int h) {
      if (this.pending == null) {
         return;
      }

      float p = Math.min(1.0F, (System.currentTimeMillis() - this.transitionAt) / 420.0F);
      float e = p * p;
      float maxR = (float)Math.hypot(w, h);
      java.util.Random r = new java.util.Random(77L);
      for (int i = 0; i < 90; i++) {
         double ang = r.nextDouble() * Math.PI * 2.0;
         float speed = 0.5F + r.nextFloat();
         float inner = e * maxR * speed * 0.7F;
         float len = 10.0F + 120.0F * e * speed;
         float cos = (float)Math.cos(ang);
         float sin = (float)Math.sin(ang);
         int col = r.nextInt(3) == 0 ? 0xFF7EB6 : (r.nextBoolean() ? 0xB57BFF : 0xFFFFFF);
         // Streifen in wenigen Stücken mit abnehmender Helligkeit statt Punkt für Punkt
         int seg = 6;
         for (int k = 0; k < seg; k++) {
            float d0 = inner + len * k / seg;
            float d1 = inner + len * (k + 1) / seg;
            MeowlSpace.line(ctx, this.warpX + cos * d0, this.warpY + sin * d0, this.warpX + cos * d1, this.warpY + sin * d1, a(col, (1.0F - (k + 0.5F) / seg) * (0.4F + 0.6F * p)));
         }
      }

      // heller Blitz am Startpunkt, dann alles dunkel
      MeowlSpace.disc(ctx, this.warpX, this.warpY, 6.0F + 50.0F * e, a(0xD9C2FF, 0.35F * (1.0F - p)));
      ctx.fill(0, 0, w, h, a(0x05060F, e * 0.95F));
   }

   /**
    * Menüpunkt nach der Warp-Animation ausführen – unabhängig davon, ob das Zeichnen klappt (sonst bliebe die Aktion hängen
    * und jeder weitere Klick würde ignoriert) und nicht mitten im Zeichnen, sondern danach (26.x: eigene Zeichenphase).
    */
   private void runPendingIfDue() {
      if (this.pending != null && System.currentTimeMillis() - this.transitionAt >= 420L) {
         Runnable action = this.pending;
         this.pending = null;
         this.minecraft.execute(() -> {
            try {
               action.run();
            } catch (RuntimeException | LinkageError var3) {
               NebulaClient.LOGGER.error("Meowl-Hauptmenü: Menüpunkt fehlgeschlagen", var3);
            }
         });
      }
   }

   @Override
   public void tick() {
      super.tick();
      this.runPendingIfDue();
   }

   private void transition(Runnable action, float x, float y) {
      if (this.pending == null) {
         this.pending = action;
         this.transitionAt = System.currentTimeMillis();
         this.warpX = x;
         this.warpY = y;
      }
   }

   /** Planet rechts mit drehendem Ring und Mond. */
   private void planet(GuiGraphics ctx, int w, int h, float t, float intro, int mx, int my) {
      this.spinBoost *= 0.985F;
      this.spin += 0.004F * (1.0F + this.spinBoost);
      float cx = w * 0.7F - (mx / (float)Math.max(1, w) - 0.5F) * 10.0F;
      float cy = h * 0.56F + (1.0F - intro) * 40.0F - (my / (float)Math.max(1, h) - 0.5F) * 6.0F;
      float r = Math.min(h * 0.26F, w * 0.16F);
      this.planetX = Math.round(cx);
      this.planetY = Math.round(cy);
      this.planetR = Math.round(r);

      for (int i = 5; i >= 1; i--) {
         MeowlSpace.disc(ctx, cx, cy, r + i * 7.0F, a(0x8B5CF6, 0.035F + 0.01F * Math.min(3.0F, this.spinBoost)));
      }

      this.ring(ctx, cx, cy, r, false);
      MeowlSpace.disc(ctx, cx, cy, r, 0xFF2A1F5E);
      MeowlSpace.disc(ctx, cx - r * 0.1F, cy - r * 0.1F, r * 0.9F, 0xFF3A2A7E);
      MeowlSpace.disc(ctx, cx - r * 0.22F, cy - r * 0.24F, r * 0.66F, 0xFF4B37A0);
      MeowlSpace.disc(ctx, cx - r * 0.34F, cy - r * 0.36F, r * 0.36F, 0xFF6048C0);

      for (int i = 0; i < 4; i++) {
         float by = cy - r * 0.5F + i * r * 0.32F + (float)Math.sin(t * 0.4 + i) * 2.0F;
         float half = (float)Math.sqrt(Math.max(0.0F, r * r - (by - cy) * (by - cy))) * 0.92F;
         ctx.fill(Math.round(cx - half), Math.round(by), Math.round(cx + half), Math.round(by + r * 0.06F), a(0xC9A8FF, 0.1F));
      }

      this.ring(ctx, cx, cy, r, true);
      float ma = t * 0.35F;
      float mxp = cx + (float)Math.cos(ma) * r * 1.85F;
      float myp = cy + (float)Math.sin(ma) * r * 0.55F;
      MeowlSpace.disc(ctx, mxp, myp, r * 0.13F, 0xFFB9B2D8);
      MeowlSpace.disc(ctx, mxp - r * 0.03F, myp - r * 0.03F, r * 0.08F, 0xFFDAD4F2);
   }

   private void ring(GuiGraphics ctx, float cx, float cy, float r, boolean front) {
      int n = 150;
      for (int band = 0; band < 3; band++) {
         float rr = r * (1.45F + band * 0.13F);
         int col = band == 1 ? 0xE3B5FF : 0x9D7BFF;
         for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2.0 / n + this.spin * (3.0F + band);
            float s = (float)Math.sin(ang);
            if (s > 0.0F != front) {
               continue;
            }

            float x = cx + (float)Math.cos(ang) * rr;
            float y = cy + s * rr * 0.26F - (float)Math.cos(ang) * rr * 0.12F;
            ctx.fill(Math.round(x), Math.round(y), Math.round(x) + 2, Math.round(y) + 1, a(col, front ? 0.8F : 0.5F));
         }
      }
   }

   /**
    * Dein Skin zwischen Menü und Planet. Greifbar: hängt beim Ziehen an der Maus, Arme und Beine schlenkern; losgelassen
    * treibt er schwerelos weiter, dreht sich, prallt an den Rändern ab und schwebt nach ein paar Sekunden zurück.
    */
   private void skin(GuiGraphics ctx, int w, int h, float t, int mx, int my) {
      if (this.skin == null) {
         return;
      }

      int left = 228;
      int right = Math.round(this.planetX - this.planetR * 1.45F);
      int sh = Math.round(Math.min(h * 0.46F, 190.0F));
      if (right - left < 60) {
         // kein Platz zwischen Menü und Planet: Skin ausblenden, damit er keine Klicks abfängt
         this.skinBoxW = 0;
         this.skinBoxH = 0;
         return;
      }

      float homeX = (left + right) / 2.0F;
      float homeY = h * 0.8F - sh / 2.0F;
      long nowNs = System.nanoTime();
      float dt = Math.min(0.05F, (nowNs - this.lastPhys) / 1.0E9F);
      this.lastPhys = nowNs;
      if (!this.ragInit) {
         this.ragInit = true;
         this.rx = homeX;
         this.ry = homeY;
      }

      if (this.grabbed) {
         float tx = mx - this.grabDx;
         float ty = my - this.grabDy;
         if (dt > 0.0F) {
            // Geschwindigkeit aus der Mausbewegung (geglättet) für den Schwung beim Loslassen
            this.vx += ((tx - this.rx) / dt - this.vx) * 0.35F;
            this.vy += ((ty - this.ry) / dt - this.vy) * 0.35F;
         }

         this.rx = tx;
         this.ry = ty;
         this.rag.roll += (Math.max(-0.7F, Math.min(0.7F, this.vx * 0.0012F)) - this.rag.roll) * 0.15F;
      } else {
         boolean drifting = System.currentTimeMillis() - this.releasedAt < 4500L;
         if (!drifting) {
            // sanft zurück an den Platz schweben, Drehung auslaufen lassen
            this.vx += (homeX - this.rx) * 2.2F * dt;
            this.vy += (homeY - this.ry) * 2.2F * dt;
            float damp = (float)Math.exp(-2.6F * dt);
            this.vx *= damp;
            this.vy *= damp;
            float target = (float)(Math.round(this.rag.roll / (Math.PI * 2.0)) * Math.PI * 2.0);
            this.rag.roll += (target - this.rag.roll) * Math.min(1.0F, 2.0F * dt);
            this.rollVel *= damp;
            this.spinVel *= damp;
            this.skinYaw += (25.0F - (mx - this.rx) / (float)Math.max(1, w) * 80.0F - this.skinYaw) * 0.04F;
         } else {
            // Weltraum: kaum Reibung
            float damp = (float)Math.exp(-0.25F * dt);
            this.vx *= damp;
            this.vy *= damp;
            this.rag.roll += this.rollVel * dt;
            this.rollVel *= (float)Math.exp(-0.2F * dt);
            this.skinYaw += this.spinVel * dt;
            this.spinVel *= (float)Math.exp(-0.3F * dt);
         }

         this.rx += this.vx * dt;
         this.ry += this.vy * dt;
         // an den Bildschirmrändern abprallen
         float hw = sh * 0.3F;
         float hh = sh * 0.5F;
         if (this.rx < hw && this.vx < 0.0F || this.rx > w - hw && this.vx > 0.0F) {
            this.vx = -this.vx * 0.7F;
            this.rollVel = -this.rollVel * 0.8F + this.vy * 0.004F;
         }

         if (this.ry < hh && this.vy < 0.0F || this.ry > h - hh && this.vy > 0.0F) {
            this.vy = -this.vy * 0.7F;
            this.rollVel = -this.rollVel * 0.8F - this.vx * 0.004F;
         }
      }

      // Gliedmaßen: je schneller, desto wilder; ziehen gegen die Bewegung nach
      float speed = (float)Math.hypot(this.vx, this.vy);
      float targetFlail = Math.min(1.0F, speed / 700.0F);
      this.rag.flail += (Math.max(targetFlail, this.grabbed ? 0.35F : 0.08F) - this.rag.flail) * 0.12F;
      this.rag.lagX += (Math.max(-1.0F, Math.min(1.0F, -this.vx / 600.0F)) - this.rag.lagX) * 0.15F;
      this.rag.lagY += (Math.max(-1.0F, Math.min(1.0F, -this.vy / 600.0F)) - this.rag.lagY) * 0.15F;
      float in = ease((t - 0.45F) / 0.6F);
      float bob = this.grabbed ? 0.0F : (float)Math.sin(t * 1.3) * 3.0F;
      int box = Math.round(sh * 1.25F);
      this.skinBoxW = Math.round(sh * 0.6F);
      this.skinBoxH = sh;
      int cx = Math.round(this.rx);
      int cy = Math.round(this.ry + bob + (1.0F - in) * 20.0F);
      float pitch = -5.0F + this.rag.lagY * 12.0F;
      Render2D.glow(ctx, cx - this.skinBoxW / 4, cy - sh / 3, this.skinBoxW / 2, sh * 2 / 3, 20, 0x8B5CF6, (0.18F + 0.25F * this.rag.flail) * in);
      try {
         // das Skin-Bild selbst in echten Pixeln angeben (gilt unabhängig davon, ob Minecraft die Verkleinerung anwendet)
         float s = this.ls;
         ctx.pose().pushMatrix();
         ctx.pose().scale(1.0F / s, 1.0F / s);
         this.skinDraw.drawRag(
            ctx, this.skin.get(), pitch, this.skinYaw, Math.round((cx - box / 2) * s), Math.round((cy - box / 2) * s), Math.round((cx + box / 2) * s), Math.round((cy + box / 2) * s), sh * s, t, this.rag
         );
         ctx.pose().popMatrix();
      } catch (RuntimeException var20) {
      }

      if (!this.grabbed && System.currentTimeMillis() - this.releasedAt > 4500L && Render2D.inside(mx, my, cx - this.skinBoxW / 2, cy - sh / 2, this.skinBoxW, sh)) {
         String hint = "Ziehen & werfen ✦";
         ctx.drawString(this.font, hint, cx - this.font.width(hint) / 2, cy + sh / 2 + 4, a(0x8E86B0, in), false);
      }
   }

   private boolean overSkin(double mx, double my) {
      return this.skin != null && Math.abs(mx - this.rx) < this.skinBoxW / 2.0 && Math.abs(my - this.ry) < this.skinBoxH / 2.0;
   }
   /** Logo mit dem Meowl-Avatar daneben, der zur Maus schaut, und einer Sprechblase. */
   private void logo(GuiGraphics ctx, float t, int mx, int my) {
      float in = ease((t - 0.1F) / 0.7F);
      int x = 28;
      int y = Math.round(24 - (1.0F - in) * 14.0F);
      // Avatar
      int px = 3;
      int ay = y + Math.round((float)Math.sin(t * 2.0) * 2.0F);
      float lookX = (mx - (x + 24)) / 120.0F;
      float lookY = (my - (ay + 24)) / 120.0F;
      Render2D.glow(ctx, x, ay, 16 * px, 16 * px, 12, 0x8B5CF6, 0.35F * in);
      MeowlAvatar.draw(ctx, x, ay, px, lookX, lookY, t);
      // Schriftzug
      int lx = x + 16 * px + 12;
      String word = "MEOWL";
      ctx.pose().pushMatrix();
      ctx.pose().translate(lx, y + 2);
      ctx.pose().scale(3.2F, 3.2F);
      int cx = 0;
      for (int i = 0; i < word.length(); i++) {
         Component c = Component.literal(String.valueOf(word.charAt(i))).withStyle(ChatFormatting.BOLD);
         float k = (float)(0.5 + 0.5 * Math.sin(t * 1.6 - i * 0.6));
         ctx.drawString(this.font, c, cx, 0, a(mix(0xFFB57BFF, 0xFFFF7EB6, k) & 16777215, in), true);
         cx += this.font.width(c) + 1;
      }

      ctx.pose().popMatrix();
      ctx.drawString(this.font, "C L I E N T", lx + 2, y + 33, a(0xCFC4FF, in), false);
      String version = FabricLoader.getInstance().getModContainer("nebula").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
      ctx.drawString(this.font, "v" + version + "  ·  Minecraft " + SharedConstants.getCurrentVersion().name(), lx + 2, y + 45, a(0x7E76A6, in), false);
      // Sprechblase: wechselt alle 6 Sekunden
      float bubbleIn = ease((t - 1.2F) / 0.4F);
      if (bubbleIn > 0.01F) {
         int idx = (int)((t - 1.2F) / 6.0F);
         String tip = idx <= 0 ? this.hello() + ", " + this.user() + "!" : TIPS[(idx - 1) % TIPS.length];
         float local = (t - 1.2F) % 6.0F;
         float fade = Math.min(1.0F, Math.min(local / 0.3F, (6.0F - local) / 0.3F)) * bubbleIn;
         int tw = this.font.width(tip);
         int bx = x + 4;
         int by = ay + 16 * px + 8;
         Render2D.roundRect(ctx, bx, by, tw + 12, 16, 7, a(0x1E1838, 0.92F * fade));
         ctx.fill(bx + 10, by - 3, bx + 14, by, a(0x1E1838, 0.92F * fade));
         ctx.drawString(this.font, tip, bx + 6, by + 4, a(0xF0E8FF, fade), false);
      }
   }

   private String user() {
      return this.minecraft.getUser() != null ? this.minecraft.getUser().getName() : "du";
   }

   private String hello() {
      int hour = LocalTime.now().getHour();
      return hour < 5 ? "Gute Nacht" : (hour < 11 ? "Guten Morgen" : (hour < 17 ? "Hallo" : (hour < 22 ? "Guten Abend" : "Gute Nacht")));
   }

   /** Oben rechts: Uhrzeit. */
   private void greeting(GuiGraphics ctx, int w, float t) {
      float in = ease((t - 0.5F) / 0.6F);
      LocalTime now = LocalTime.now();
      String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
      ctx.pose().pushMatrix();
      ctx.pose().translate(w - 24 - this.font.width(time) * 2.0F, 22);
      ctx.pose().scale(2.0F, 2.0F);
      ctx.drawString(this.font, time, 0, 0, a(0xF3E9FF, in * 0.9F), false);
      ctx.pose().popMatrix();
      String day = this.hello();
      ctx.drawString(this.font, day, w - 24 - this.font.width(day), 44, a(0x8E86B0, in), false);
   }

   private List<MeowlTitleScreen.Entry> entries() {
      List<MeowlTitleScreen.Entry> out = new ArrayList<>();
      out.add(new MeowlTitleScreen.Entry("☄", "Einzelspieler", () -> this.minecraft.setScreen(new SelectWorldScreen(this))));
      out.add(new MeowlTitleScreen.Entry("✦", "Mehrspieler", () -> this.minecraft.setScreen(new MeowlMultiplayerScreen())));
      out.add(new MeowlTitleScreen.Entry("✿", "Meowl-Menü", () -> {
         NebulaClient c = NebulaClient.getInstance();
         this.minecraft.setScreen(new MeowlScreen(c.getModuleManager(), c.getConfigManager()));
      }));
      out.add(new MeowlTitleScreen.Entry("⚙", "Einstellungen", () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
      out.add(new MeowlTitleScreen.Entry("⏻", "Beenden", () -> this.minecraft.stop()));
      return out;
   }

   /** Menüpunkte links; gibt die Unterkante zurück. */
   private int menu(GuiGraphics ctx, int h, float t, int mx, int my) {
      List<MeowlTitleScreen.Entry> list = this.entries();
      int x = 28;
      int bw = 190;
      int bh = 26;
      int gap = 7;
      int y0 = Math.max(150, h / 2 - (list.size() * (bh + gap)) / 2 + 30);

      for (int i = 0; i < list.size(); i++) {
         MeowlTitleScreen.Entry e = list.get(i);
         float in = ease((t - 0.25F - i * 0.08F) / 0.45F);
         int y = y0 + i * (bh + gap);
         int bx = Math.round(x - (1.0F - in) * 60.0F);
         boolean hov = in > 0.9F && Render2D.inside(mx, my, x, y, bw, bh);
         this.hover[i] += ((hov ? 1.0F : 0.0F) - this.hover[i]) * 0.25F;
         this.button(ctx, bx, y, bw, bh, e.icon(), e.label(), this.hover[i], in);
      }

      return y0 + list.size() * (bh + gap);
   }

   private void button(GuiGraphics ctx, int bx, int y, int bw, int bh, String icon, String label, float hv, float in) {
      int off = Math.round(hv * 8.0F);
      if (hv > 0.02F) {
         Render2D.glow(ctx, bx + off, y, bw, bh, 8, 0x8B5CF6, 0.45F * hv);
      }

      Render2D.roundRect(ctx, bx + off, y, bw, bh, 8, a(mix(0xFF141829, 0xFF2A1F52, hv) & 16777215, 0.82F * in));
      Render2D.roundRect(ctx, bx + off, y + 5, 3 + Math.round(hv * 2.0F), bh - 10, 2, a(ACCENT & 16777215, in * (0.5F + 0.5F * hv)));
      ctx.drawString(this.font, icon, bx + off + 14, y + (bh - 8) / 2, a(mix(0xFF8E86B0, ACCENT, hv) & 16777215, in), false);
      ctx.drawString(
         this.font,
         Component.literal(label).withStyle(hv > 0.5F ? ChatFormatting.BOLD : ChatFormatting.RESET),
         bx + off + 30,
         y + (bh - 8) / 2,
         a(mix(0xFFCFC8E8, 0xFFFFFFFF, hv) & 16777215, in),
         false
      );
      if (hv > 0.05F) {
         ctx.drawString(this.font, "›", bx + off + bw - 16, y + (bh - 8) / 2, a(ACCENT & 16777215, hv * in), false);
      }
   }

   private void footer(GuiGraphics ctx, int w, int h, float t) {
      float in = ease((t - 0.6F) / 0.5F);
      MeowlSpace.disc(ctx, 34, h - 17, 4.0F, a(0x5BE38A, in * (0.6F + 0.4F * (float)Math.sin(t * 3.0))));
      ctx.drawString(this.font, "Angemeldet als " + this.user(), 44, h - 21, a(0xCFC8E8, in), false);
      String note = "Nicht von Mojang oder Microsoft";
      int noteX = w - 22 - this.font.width(note);
      ctx.drawString(this.font, note, noteX, h - 21, a(0x5C567A, in), false);
      // Umschalter Meowl ⇄ Vanilla
      int sx = noteX - 16 - 26;
      int sy = h - 24;
      Render2D.toggleSwitch(ctx, sx, sy, 1.0F);
      String label = "Meowl-Menü";
      ctx.drawString(this.font, label, sx - 6 - this.font.width(label), h - 21, a(0xCFC8E8, in), false);

   }

   // ================================================================== Eingabe

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.clickFallback.onEvent();
      if (this.pending != null) {
         return true;
      }

      if (this.hits.isEmpty()) {
         this.layoutHits(Math.round(this.width / this.ls), Math.round(this.height / this.ls));
      }

      if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
         double x = click.x() / this.ls;
         double y = click.y() / this.ls;
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            MeowlTitleScreen.Hit hit = this.hits.get(i);
            if (Render2D.inside(x, y, hit.x(), hit.y(), hit.w(), hit.h())) {
               this.transition(hit.action(), hit.x() + hit.w() / 2.0F, hit.y() + hit.h() / 2.0F);
               return true;
            }
         }

         if (this.overSkin(x, y)) {
            long now = System.currentTimeMillis();
            if (now - this.lastSkinClick < 300L) {
               // Doppelklick: sofort zurück an den Platz
               this.releasedAt = 0L;
            }

            this.lastSkinClick = now;
            this.grabbed = true;
            this.grabDx = (float)(x - this.rx);
            this.grabDy = (float)(y - this.ry);
            return true;
         }

         // Planet anklicken: Ring dreht kurz schneller; sonst Himmel: Welle + Sternschnuppe
         if (Math.hypot(x - this.planetX, y - this.planetY) < this.planetR) {
            this.spinBoost = Math.min(12.0F, this.spinBoost + 5.0F);
         } else {
            MeowlSpace.click((float)click.x(), (float)click.y());
         }

         return true;
      }
      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.grabbed) {
         this.grabbed = false;
         this.releasedAt = System.currentTimeMillis();
         // Schwung mitnehmen: Drehen und Rollen aus der Wurfrichtung
         this.vx = Math.max(-1800.0F, Math.min(1800.0F, this.vx));
         this.vy = Math.max(-1800.0F, Math.min(1800.0F, this.vy));
         this.rollVel = this.vx * 0.004F + this.vy * 0.002F;
         this.spinVel = this.vx * 0.25F;
         return true;
      }

      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      return this.grabbed || super.mouseDragged(click, dx, dy);
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
      // Tasten 1-5 öffnen die Menüpunkte (falls eine andere Mod Mausklicks abfängt)
      int k = input.key();
      if (this.pending == null && k >= 49 && k <= 53) {
         List<MeowlTitleScreen.Entry> list = this.entries();
         int idx = k - 49;
         if (idx < list.size()) {
            this.transition(list.get(idx).action(), 120.0F, this.height / this.ls / 2.0F);
            return true;
         }
      }

      return super.keyPressed(input);
   }
}
