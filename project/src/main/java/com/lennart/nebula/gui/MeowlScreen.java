package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.config.ConfigManager;
import com.lennart.nebula.gui.component.ComponentFactory;
import com.lennart.nebula.gui.component.SettingComponent;
import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.mods.ClientSettings;
import com.lennart.nebula.module.mods.MeowlServerModule;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.update.UpdateManager;
import com.lennart.nebula.util.BaulooStylesBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map.Entry;

public class MeowlScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int SPACE_TOP = -16513780;
   private static final int SPACE_BOTTOM = -16054504;
   private static final MeowlScreen.Star[] STARS = makeStars();
   private static Section focused;
   private final ModuleManager modules;
   private final ConfigManager config;
   private final ClientSettings clientSettings;
   private final MeowlTextField search = new MeowlTextField(32).placeholder("Modul suchen …");
   private final Animation openAnim = new Animation(12.0F, 0.0F);
   private boolean closing;
   private final MeowlScreen.Drawer drawer = new MeowlScreen.Drawer();
   private final int[] serverChip = new int[4];
   private final int[] gearRect = new int[4];
   private QuickSettingsPanel quick;
   private final int[] updateChip = new int[4];
   private final List<MeowlScreen.Button> buttons = new ArrayList<>();
   private long lastFrame = System.nanoTime();
   private static final Map<String, Boolean> COLLAPSED = new HashMap<>();
   private static final Map<String, String> LAST_TAB = new HashMap<>();
   private final Animation zoom;
   private Section zoomSection;
   private double spin;
   private double spinTarget;
   private final Map<Module, Animation> hover;
   private final Map<Module, Animation> onAnim;
   private final Map<Section, Animation> planetHover;
   private final List<MeowlScreen.Burst> bursts;
   private final List<MeowlScreen.Orb> orbs;
   private final Map<Section, float[]> planets;
   private final Random random;
   private long nextShootingStar;
   private float[] shootingStar;
   private long shootingStart;
   /** Zuletzt gezogener Regler (bleibt in der Ingame-Vorschau sichtbar). */
   private MeowlScreen.Row previewRow;
   private Module hoveredModule;
   private Section hoveredPlanet;
   private Section dragPlanet;
   private double dragStartX;
   private double dragStartY;
   private boolean dragMoved;
   private final long openedAt;
   // Suche: Enter führt per Sternschnuppe zum nächsten Treffer
   private int searchIndex = -1;
   private String searchQuery = "";
   private Module guideTarget;
   private long guideStart;
   private boolean guideArrived;
   private Module highlightModule;
   private long highlightStart;

   public MeowlScreen(ModuleManager modules, ConfigManager config) {
      super(Component.literal("Meowl"));
      this.zoom = new Animation(6.5F, focused != null ? 1.0F : 0.0F);
      this.zoomSection = focused;
      this.hover = new HashMap<>();
      this.onAnim = new HashMap<>();
      this.planetHover = new HashMap<>();
      this.bursts = new ArrayList<>();
      this.orbs = new ArrayList<>();
      this.planets = new HashMap<>();
      this.random = new Random();
      this.nextShootingStar = System.currentTimeMillis() + 2500L;
      this.openedAt = System.currentTimeMillis();
      this.modules = modules;
      this.config = config;
      this.clientSettings = modules.get(ClientSettings.class);
      this.search.onSubmit(s -> this.searchNext());
      this.quick = new QuickSettingsPanel(modules, config::save);
   }

   private boolean animations() {
      return this.clientSettings == null || this.clientSettings.animations();
   }

   @Override
   protected void init() {
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      this.config.save();
   }

   private List<Module> sectionModules(Section s) {
      List<Module> list = new ArrayList<>();

      for (Module m : this.modules.getModules()) {
         if (!m.isHidden() && m.getSection() == s) {
            list.add(m);
         }
      }

      return list;
   }

   private boolean searching() {
      return !this.search.getText().trim().isEmpty();
   }

   private boolean matches(Module m) {
      String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
      return q.isEmpty()
         || m.getName().toLowerCase(Locale.ROOT).contains(q)
         || Lang.t(m.getName()).toLowerCase(Locale.ROOT).contains(q)
         || m.getDescription().toLowerCase(Locale.ROOT).contains(q);
   }

   private List<Module> matches() {
      List<Module> out = new ArrayList<>();
      if (this.searching()) {
         for (Module m : this.modules.getModules()) {
            if (!m.isHidden() && this.matches(m)) {
               out.add(m);
            }
         }
      }

      return out;
   }

   /** Nächster Treffer der Suche: Planet heranzoomen, Sternschnuppe fliegt hin, dann öffnen sich die Einstellungen. */
   private void searchNext() {
      List<Module> hits = this.matches();
      String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
      if (!q.equals(this.searchQuery)) {
         this.searchQuery = q;
         this.searchIndex = -1;
      }

      if (hits.isEmpty()) {
         return;
      }

      this.searchIndex = (this.searchIndex + 1) % hits.size();
      Module m = hits.get(this.searchIndex);
      if (this.drawer.isOpen()) {
         this.drawer.close();
      }

      if (m.getSection() != null && !isDirect(m.getSection())) {
         this.focus(m.getSection());
      }

      this.guideTarget = m;
      this.guideStart = System.currentTimeMillis();
      this.guideArrived = false;
      this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F));
   }

   /** Wo der Mond eines Moduls gerade ist (sonst sein Planet, sonst null). */
   private float[] targetOf(Module m) {
      for (MeowlScreen.Orb o : this.orbs) {
         if (o.module() == m) {
            return new float[]{o.x(), o.y(), o.r()};
         }
      }

      float[] p = m.getSection() == null ? null : this.planets.get(m.getSection());
      return p == null ? null : new float[]{p[0], p[1], p[2]};
   }

   private void renderGuide(GuiGraphicsExtractor ctx, long now) {
      Module m = this.guideTarget;
      if (m != null) {
         float[] to = this.targetOf(m);
         float flight = 0.85F;
         float t = (now - this.guideStart) / 1000.0F / flight;
         if (to == null || t >= 1.0F) {
            if (!this.guideArrived) {
               this.guideArrived = true;
               this.highlightModule = m;
               this.highlightStart = now;
               if (to != null) {
                  this.bursts.add(new MeowlScreen.Burst(to[0], to[1], to[2], m.getSection() == null ? -1 : m.getSection().color(), now));
               }

               this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.7F));
            }

            // kurz zeigen, dann die Einstellungen öffnen
            if (now - this.highlightStart > 280L) {
               this.guideTarget = null;
               if (!m.isTool()) {
                  this.drawer.openModule(m);
               }
            }

            return;
         }

         // Flug von der Suchleiste in einem Bogen zum Ziel
         float sx = this.width / 2.0F;
         float sy = 18.0F;
         float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
         float cx = (sx + to[0]) / 2.0F + (to[0] < sx ? -60.0F : 60.0F);
         float cy = Math.min(sy, to[1]) - 30.0F;

         for (int i = 0; i < 26; i++) {
            float back = Math.max(0.0F, e - i * 0.018F);
            float u = 1.0F - back;
            float x = u * u * sx + 2.0F * u * back * cx + back * back * to[0];
            float y = u * u * sy + 2.0F * u * back * cy + back * back * to[1];
            int a = Math.round(235.0F * (1.0F - i / 26.0F));
            int size = i < 2 ? 3 : (i < 8 ? 2 : 1);
            int col = i < 4 ? 0xFFFFFF : (m.getSection() == null ? 0xC0D8FF : m.getSection().color() & 16777215);
            ctx.fill(Math.round(x) - size / 2, Math.round(y) - size / 2, Math.round(x) - size / 2 + size, Math.round(y) - size / 2 + size, a << 24 | col);
         }
      }
   }

   private void focus(Section s) {
      if (s != null) {
         this.zoomSection = s;
      }

      focused = s;
   }

   private static float ease(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private void layout() {
      this.orbs.clear();
      this.planets.clear();
      float z = ease(this.zoom.get());
      int w = this.width;
      int h = this.height;
      float top = 40.0F;
      float bottom = h - 30.0F;
      float midY = (top + bottom) / 2.0F + 6.0F;
      float small = Math.min(w, h);
      // Übersicht: zwei Reihen, kleine Planeten außen
      float rowRy = Math.max(18.0F, Math.min(60.0F, (bottom - top) * 0.12F));
      int dockIndex = 0;
      int dockCount = Section.values().length - 1;
      float dockStep = Math.min(34.0F, (bottom - top - 28.0F) / Math.max(1, dockCount - 1));

      for (Section s : Section.values()) {
         boolean direct = isDirect(s);
         // je mehr Module, desto größer der Planet (Freunde mit 1 Modul am kleinsten, Client am größten)
         float k = planetScale(this.sectionModules(s).size());
         float[] pos = PlanetPositions.get(s);
         float ox = w * pos[0];
         float oy = top + (bottom - top) * pos[1];
         float ory = direct ? 0.0F : rowRy * k;
         float opr = Math.max(7.0F, rowRy * 0.55F * k);
         float orx = direct ? 0.0F : Math.min(w * 0.12F * k, ory * 1.9F);
         boolean isFocus = this.zoomSection == s;
         float x;
         float y;
         float pr;
         float ry;
         float rx;
         float orbAlpha;
         if (this.zoomSection == null) {
            x = ox;
            y = oy;
            pr = opr;
            ry = ory;
            rx = orx;
            orbAlpha = 1.0F;
         } else if (isFocus) {
            float fpr = Math.max(20.0F, Math.min(44.0F, small * 0.095F)) * (0.8F + 0.2F * Math.min(1.0F, k));
            float fry = Math.min((bottom - top) * 0.4F, 150.0F);
            float frx = Math.min(w * 0.36F, fry * 2.15F);
            x = lerp(ox, w * 0.46F, z);
            y = lerp(oy, midY, z);
            pr = lerp(opr, fpr, z);
            ry = lerp(ory, fry, z);
            rx = lerp(orx, frx, z);
            orbAlpha = 1.0F;
         } else {
            // andere Planeten wandern in die Leiste rechts
            x = lerp(ox, w - 22.0F, z);
            y = lerp(oy, top + 14.0F + dockIndex * dockStep, z);
            pr = lerp(opr, 7.0F + 4.0F * Math.min(1.0F, k), z);
            ry = lerp(ory, 0.0F, z);
            rx = lerp(orx, 0.0F, z);
            orbAlpha = 1.0F - z;
            dockIndex++;
         }

         this.planets.put(s, new float[]{x, y, pr, rx, ry});
         if (!(orbAlpha <= 0.02F) && !direct) {
            List<Module> list = this.sectionModules(s);
            int n = list.size();
            int inner = n <= 7 ? 0 : Math.round(n * 0.4F);
            boolean detail = isFocus && z > 0.5F;

            for (int i = 0; i < n; i++) {
               boolean onInner = i < inner;
               int idx = onInner ? i : i - inner;
               int count = onInner ? inner : n - inner;
               float ringScale = onInner ? 0.6F : 1.0F;
               double dir = onInner ? -1.0 : 1.0;
               double ang = Math.toRadians(this.spin * dir + (onInner ? 20.0 : 0.0) + idx * 360.0 / Math.max(1, count) + s.ordinal() * 30.0);
               float px = x + (float)Math.cos(ang) * rx * ringScale;
               float py = y + (float)Math.sin(ang) * ry * ringScale;
               float depth = (float)((Math.sin(ang) + 1.0) / 2.0);
               float base = detail ? lerp(5.0F, Math.max(10.0F, Math.min(14.0F, small * 0.03F)), (z - 0.5F) * 2.0F) : (s == Section.CLIENT ? 5.0F : 4.0F) + depth * 2.0F;
               float r = base * (0.8F + 0.35F * depth);
               float spacing = (float)((Math.PI * 2) * Math.max(rx, ry) * ringScale / Math.max(1, count));
               this.orbs.add(new MeowlScreen.Orb(list.get(i), s, px, py, r, depth, orbAlpha, detail, spacing));
            }
         }
      }
   }

   /** Größe eines Planeten nach Anzahl seiner Module: 1 Modul ≈ 0.6, 7 Module = 1.0, ab 14 Modulen ≈ 1.4. */
   private static float planetScale(int modules) {
      return Math.max(0.6F, Math.min(1.4F, 0.6F + modules * 0.058F));
   }

   /** Planeten ohne Monde: ein Klick öffnet direkt das Modul (z.B. die Freundesliste). */
   private static boolean isDirect(Section s) {
      return s == Section.FRIENDS || s == Section.COSMETICS;
   }

   private void openDirect(Section s) {
      if (s == Section.COSMETICS) {
         // der Kosmetik-Planet ist ein eigener Shop
         this.minecraft.gui.setScreen(new CosmeticShopScreen(this));
         return;
      }

      for (Module m : this.sectionModules(s)) {
         if (m.isTool()) {
            m.openTool(this);
         } else {
            this.drawer.openModule(m);
         }

         return;
      }
   }

   private MeowlScreen.Orb orbAt(double mx, double my) {
      MeowlScreen.Orb best = null;

      for (MeowlScreen.Orb o : this.orbs) {
         if (o.alpha() > 0.4F && Math.hypot(mx - o.x(), my - o.y()) <= o.r() + 3.0F && (best == null || o.depth() > best.depth())) {
            best = o;
         }
      }

      return best;
   }

   private Section planetAt(double mx, double my) {
      for (Entry<Section, float[]> e : this.planets.entrySet()) {
         float[] p = e.getValue();
         if (Math.hypot(mx - p[0], my - p[1]) <= p[2] + 5.0F) {
            return e.getKey();
         }
      }

      return null;
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      // im Hauptmenü (ohne Welt) liegt das Meowl-Menü über dem Meowl-Weltraum statt über dem Minecraft-Panorama
      if (this.minecraft != null && this.minecraft.level == null) {
         MeowlSpace.draw(context, this.width, this.height, mouseX, mouseY);
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      if (IngamePreview.active()) {
         // Ingame-Vorschau: nur der gezogene Regler bleibt stehen, sonst freie Sicht ins Spiel
         if (this.previewRow instanceof MeowlScreen.SettingRow sr && sr.lw > 0) {
            // unten über der Hotbar, damit die Mitte (Fadenkreuz, Totem-Animation) frei bleibt; der Wert hängt nur an der Mausposition links/rechts
            int py = this.height - sr.height() - 34;
            Render2D.roundRect(context, sr.lx - 6, py - 4, sr.lw + 12, sr.height() + 8, 8, 0xD8101420);
            sr.render(context, sr.lx, py, sr.lw, mouseX, mouseY, deltaTicks);
         }

         String hint = "Vorschau ingame – loslassen, um zurückzukehren";
         int tw = this.font.width(hint);
         Render2D.roundRect(context, this.width / 2 - tw / 2 - 8, 8, tw + 16, 16, 6, 0xA0101420);
         context.text(this.font, hint, this.width / 2 - tw / 2, 12, -1, true);
         return;
      }

      float open = this.animations() ? this.openAnim.update(this.closing ? 0.0F : 1.0F) : (this.closing ? 0.0F : 1.0F);
      if (this.closing && open <= 0.02F) {
         this.minecraft.gui.setScreen(null);
      } else {
         float ease = 1.0F - (1.0F - open) * (1.0F - open);
         long now = System.currentTimeMillis();
         float dt = Math.min(0.1F, (float)(System.nanoTime() - this.lastFrame) / 1.0E9F);
         if (this.animations()) {
            this.zoom.update(focused != null ? 1.0F : 0.0F);
         } else {
            this.zoom.update(focused != null ? 1.0F : 0.0F);
            this.zoom.update(focused != null ? 1.0F : 0.0F);
         }

         if (focused == null && this.zoom.get() < 0.01F) {
            this.zoomSection = null;
         }

         boolean drawerBlocks = this.drawer.isOpen();
         int mx = drawerBlocks ? -1000 : mouseX;
         int my = drawerBlocks ? -1000 : mouseY;
         this.layout();
         MeowlScreen.Orb hov = this.orbAt(mx, my);
         this.hoveredModule = hov == null ? null : hov.module();
         this.hoveredPlanet = hov == null ? this.planetAt(mx, my) : null;
         if (this.animations()) {
            if (this.hoveredModule == null) {
               this.spinTarget += dt * 3.0;
            }

            this.spin = this.smooth(this.spin, this.spinTarget);
         } else {
            this.spin = this.spinTarget;
         }

         context.fillGradient(0, 0, this.width, this.height, Render2D.alpha(-16513780, ease), Render2D.alpha(-16054504, ease));
         context.pose().pushMatrix();
         float sc = 0.96F + 0.04F * ease;
         context.pose().translate(this.width / 2.0F * (1.0F - sc), this.height / 2.0F * (1.0F - sc));
         context.pose().scale(sc, sc);
         this.renderNebulae(context, now, mouseX, mouseY, ease);
         this.renderStars(context, now, mouseX, mouseY, ease);
         this.renderShootingStar(context, now);

         for (Section s : Section.values()) {
            this.renderSystem(context, s, now, mx, my);
         }

         this.renderBursts(context, now);
         this.renderGuide(context, now);
         if (hov != null && hov.detail()) {
            this.renderTooltip(context, hov);
         }

         this.renderTopBar(context, mx, my);
         this.renderBottom(context, mx, my);
         this.drawer.render(context, mouseX, mouseY, deltaTicks);
         double[] qp = this.unscale(mouseX, mouseY);
         this.quick.render(context, this.font, this.width - 8, 27, this.width, this.height, (int)qp[0], (int)qp[1], deltaTicks);
         context.pose().popMatrix();
         this.lastFrame = System.nanoTime();
      }
   }

   private void renderNebulae(GuiGraphicsExtractor ctx, long now, int mouseX, int mouseY, float ease) {
      float t = (float)now / 1000.0F;
      float px = (mouseX - this.width / 2.0F) * -0.01F;
      float py = (mouseY - this.height / 2.0F) * -0.01F;
      this.blob(ctx, this.width * 0.22F + px + (float)Math.sin(t * 0.05) * 12.0F, this.height * 0.35F + py, this.height * 0.55F, 11561983, 0.1F * ease);
      this.blob(ctx, this.width * 0.8F + px, this.height * 0.62F + py + (float)Math.cos(t * 0.04) * 10.0F, this.height * 0.6F, 4172287, 0.09F * ease);
      this.blob(ctx, this.width * 0.55F + px * 1.5F, this.height * 0.15F + py, this.height * 0.35F, 16736162, 0.05F * ease);
   }

   private void blob(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int rgb, float strength) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      float k = radius / 60.0F;
      ctx.pose().scale(k, k);

      for (int i = 0; i < 5; i++) {
         float f = 1.0F - i * 0.18F;
         int a = Math.round(255.0F * strength * (0.35F + i * 0.18F));
         Render2D.circle(ctx, 0, 0, Math.max(4, Math.round(60.0F * f)), Math.min(255, a) << 24 | rgb);
      }

      ctx.pose().popMatrix();
   }

   private void renderStars(GuiGraphicsExtractor ctx, long now, int mouseX, int mouseY, float ease) {
      float t = (float)now / 1000.0F;
      float ox = (mouseX - this.width / 2.0F) * -0.03F;
      float oy = (mouseY - this.height / 2.0F) * -0.03F;

      for (MeowlScreen.Star st : STARS) {
         int x = Math.round(st.x() * this.width + ox * st.depth());
         int y = Math.round(st.y() * this.height + oy * st.depth());
         float tw = 0.55F + 0.45F * (float)Math.sin(t * st.speed() + st.phase());
         int a = Math.round(255.0F * ease * tw * (0.35F + 0.65F * st.depth()));
         int color = a << 24 | st.rgb();
         ctx.fill(x, y, x + st.size(), y + st.size(), color);
         if (st.size() > 1 && tw > 0.9F) {
            int ga = a / 4 << 24 | st.rgb();
            ctx.fill(x - 1, y, x, y + st.size(), ga);
            ctx.fill(x + st.size(), y, x + st.size() + 1, y + st.size(), ga);
            ctx.fill(x, y - 1, x + st.size(), y, ga);
            ctx.fill(x, y + st.size(), x + st.size(), y + st.size() + 1, ga);
         }
      }
   }

   private void renderShootingStar(GuiGraphicsExtractor ctx, long now) {
      if (this.animations()) {
         if (this.shootingStar == null && now >= this.nextShootingStar) {
            this.shootingStar = new float[]{this.random.nextFloat() * this.width * 0.7F, this.random.nextFloat() * this.height * 0.35F};
            this.shootingStart = now;
         }

         if (this.shootingStar != null) {
            float t = (float)(now - this.shootingStart) / 900.0F;
            if (t >= 1.0F) {
               this.shootingStar = null;
               this.nextShootingStar = now + 4000L + this.random.nextInt(6000);
            } else {
               float len = 70.0F;
               float hx = this.shootingStar[0] + t * 260.0F;
               float hy = this.shootingStar[1] + t * 120.0F;
               float fade = t < 0.2F ? t / 0.2F : (1.0F - t) / 0.8F;

               for (int i = 0; i < 18; i++) {
                  float f = i / 18.0F;
                  int x = Math.round(hx - f * len);
                  int y = Math.round(hy - f * len * 0.46F);
                  int a = Math.round(230.0F * fade * (1.0F - f));
                  ctx.fill(x, y, x + (i < 3 ? 2 : 1), y + 1, a << 24 | 16777215);
               }
            }
         }
      }
   }

   private void renderSystem(GuiGraphicsExtractor ctx, Section s, long now, int mx, int my) {
      float[] p = this.planets.get(s);
      float cx = p[0];
      float cy = p[1];
      float pr = p[2];
      float z = ease(this.zoom.get());
      boolean isFocus = this.zoomSection == s;
      boolean mini = this.zoomSection != null && !isFocus;
      if (p[3] > 4.0F) {
         List<Module> list = this.sectionModules(s);
         int inner = list.size() <= 7 ? 0 : Math.round(list.size() * 0.4F);
         float alpha = mini ? 1.0F - z : 1.0F;
         this.orbitPath(ctx, cx, cy, p[3], p[4], s.color(), 0.22F * alpha);
         if (inner > 0) {
            this.orbitPath(ctx, cx, cy, p[3] * 0.6F, p[4] * 0.6F, s.color(), 0.16F * alpha);
         }
      }

      List<MeowlScreen.Orb> mine = new ArrayList<>();

      for (MeowlScreen.Orb o : this.orbs) {
         if (o.section() == s) {
            mine.add(o);
         }
      }

      mine.sort(Comparator.comparingDouble(MeowlScreen.Orb::depth));

      for (MeowlScreen.Orb ox : mine) {
         if (ox.depth() < 0.5F) {
            this.renderOrb(ctx, ox, now);
         }
      }

      this.renderPlanet(ctx, s, cx, cy, pr, now, mini);

      for (MeowlScreen.Orb oxx : mine) {
         if (oxx.depth() >= 0.5F) {
            this.renderOrb(ctx, oxx, now);
         }
      }

      int active = 0;
      List<Module> list = this.sectionModules(s);

      for (Module m : list) {
         if (m.isEnabled()) {
            active++;
         }
      }

      if (mini) {
         String label = s.displayName();
         int tw = Math.round(this.font.width(label) * 0.75F);
         this.drawSmall(ctx, label, Math.round(cx - pr - 5.0F - tw), Math.round(cy - 3.0F), Render2D.alpha(s.color(), z), 0.75F);
      } else if (isFocus && !(z < 0.95F)) {
         Component name = Component.literal(Lang.t(s.displayName()).toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
         int nw = this.font.width(name);
         ctx.text(this.font, name, Math.round(cx - nw / 2.0F), Math.round(cy + pr + 6.0F), Render2D.alpha(s.color(), z), true);
      } else {
         float a = isFocus ? 1.0F - z : 1.0F;
         int ly = Math.round(cy + Math.max(p[4], p[2]) + 10.0F);
         Component name = Component.literal(Lang.t(s.displayName()).toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
         int nw = this.font.width(name);
         ctx.text(this.font, name, this.clampX(Math.round(cx - nw / 2.0F), nw), ly, Render2D.alpha(s.color(), a), true);
      }
   }

   private int clampX(int x, int textW) {
      return Math.max(4, Math.min(this.width - 4 - textW, x));
   }

   private void orbitPath(GuiGraphicsExtractor ctx, float cx, float cy, float rx, float ry, int color, float alpha) {
      int steps = Math.max(40, Math.round((rx + ry) * 0.7F));
      int c = Math.round(255.0F * alpha) << 24 | color & 16777215;

      for (int i = 0; i < steps; i += 2) {
         double a = i * Math.PI * 2.0 / steps;
         int x = Math.round(cx + (float)Math.cos(a) * rx);
         int y = Math.round(cy + (float)Math.sin(a) * ry);
         ctx.fill(x, y, x + 1, y + 1, c);
      }
   }

   private void renderPlanet(GuiGraphicsExtractor ctx, Section s, float cx, float cy, float pr, long now, boolean mini) {
      float h = this.planetHover.computeIfAbsent(s, k -> new Animation(12.0F, 0.0F)).update(this.hoveredPlanet == s ? 1.0F : 0.0F);
      if (!this.animations()) {
         h = this.hoveredPlanet == s ? 1.0F : 0.0F;
      }

      boolean hasMatch = this.searching() && this.sectionModules(s).stream().anyMatch(this::matches);
      float pulse = (float)(0.5 + 0.5 * Math.sin(now / 600.0));
      int ix = Math.round(cx);
      int iy = Math.round(cy);
      int r = Math.round(pr * (1.0F + 0.06F * h));
      float glow = 0.35F + 0.35F * h + (hasMatch ? 0.3F * pulse : 0.0F);

      for (int i = 4; i >= 1; i--) {
         int a = Math.round(40.0F * glow / i);
         Render2D.circle(ctx, ix, iy, Math.min(64, r + i * 4), a << 24 | s.color() & 16777215);
      }

      if (s == Section.CLIENT) {
         this.saturnRing(ctx, cx, cy, r, s.color(), true);
      }

      // Planet: dunkler Rand, mittige Fläche (das Symbol sitzt genau darauf), kleiner Glanzpunkt
      Render2D.circle(ctx, ix, iy, Math.min(64, r), s.dark());
      Render2D.circle(ctx, ix, iy, Math.min(64, Math.round(r * 0.86F)), Theme.lerp(s.dark(), s.color(), 0.45F));
      Render2D.circle(ctx, ix - Math.round(r * 0.42F), iy - Math.round(r * 0.42F), Math.min(64, Math.max(1, Math.round(r * 0.2F))), 0x22FFFFFF);
      if (s.icon() == null) {
         float px = r * 1.1F / 16.0F;
         float left = cx - 8.0F * px;
         float topY = cy - 8.0F * px;
         ctx.pose().pushMatrix();
         ctx.pose().translate(left, topY + (float)Math.sin(now / 500.0) * Math.min(2.0F, r * 0.05F));
         ctx.pose().scale(px, px);

         MeowlSprite.draw(ctx, 0, 0);

         ctx.pose().popMatrix();
      } else {
         this.drawIcon(ctx, com.lennart.nebula.util.SafeStack.of(s.icon()), cx, cy, Math.max(0.5F, r * 1.1F / 16.0F));
         if (s == Section.CLIENT) {
            this.saturnRing(ctx, cx, cy, r, s.color(), false);
         }
      }

      if (mini && h > 0.01F) {
         Render2D.ring(ctx, ix, iy, Math.min(64, r + 3), 1, Render2D.alpha(s.color(), h), 0);
      }
   }

   private void saturnRing(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color, boolean back) {
      float rx = r * 1.75F;
      float ry = r * 0.42F;
      int steps = Math.max(60, Math.round(rx * 2.0F));

      for (int i = 0; i < steps; i++) {
         double a = i * Math.PI * 2.0 / steps;
         boolean isBack = Math.sin(a) < 0.0;
         if (isBack == back) {
            float tilt = -0.25F;
            float x = (float)Math.cos(a) * rx;
            float y = (float)Math.sin(a) * ry;
            float rxp = x - y * tilt;
            float ryp = y + x * tilt * 0.35F;
            int px = Math.round(cx + rxp);
            int py = Math.round(cy + ryp);
            int alpha = back ? 90 : 200;
            ctx.fill(px, py, px + 1, py + 1, alpha << 24 | color & 16777215);
            ctx.fill(px, py + 1, px + 1, py + 2, alpha / 3 << 24 | color & 16777215);
         }
      }
   }

   private void renderOrb(GuiGraphicsExtractor ctx, MeowlScreen.Orb o, long now) {
      Module m = o.module();
      float on = this.anim(this.onAnim, m, m.isEnabled() ? 1.0F : 0.0F);
      float hv = this.anim(this.hover, m, this.hoveredModule == m ? 1.0F : 0.0F);
      boolean match = this.matches(m);
      float alpha = o.alpha() * (this.searching() && !match ? 0.22F : 1.0F) * (0.55F + 0.45F * o.depth());
      int color = o.section().color();
      int ix = Math.round(o.x());
      int iy = Math.round(o.y());
      int r = Math.max(2, Math.round(o.r() * (1.0F + 0.25F * hv)));
      if (on > 0.02F) {
         for (int i = 3; i >= 1; i--) {
            int a = Math.round(55.0F * on * alpha / i);
            Render2D.circle(ctx, ix, iy, Math.min(64, r + i * 3), a << 24 | color & 16777215);
         }
      }

      int body = Theme.lerp(-15329241, Theme.lerp(o.section().dark(), color, 0.35F), on);
      Render2D.circle(ctx, ix, iy, Math.min(64, r + 1), Render2D.alpha(Theme.lerp(-14012603, color, Math.max(on, hv * 0.6F)), alpha));
      Render2D.circle(ctx, ix, iy, Math.min(64, r), Render2D.alpha(body, alpha));
      if (o.detail()) {
         float iconScale = r / 9.5F;
         if (alpha > 0.3F) {
            this.drawIcon(ctx, m.getIcon(), ix, iy, iconScale * (on > 0.5F ? 1.0F : 0.85F));
         }

         if (on > 0.5F) {
            double a = now / 400.0 + m.getName().length();
            int sx = ix + (int)Math.round(Math.cos(a) * (r + 3));
            int sy = iy + (int)Math.round(Math.sin(a) * (r + 3) * 0.5);
            Render2D.circle(ctx, sx, sy, 1, Render2D.alpha(-1, alpha));
         }

         if (this.searching() && match) {
            float pulse = (float)(0.5 + 0.5 * Math.sin(now / 250.0));
            Render2D.ring(ctx, ix, iy, Math.min(64, r + 4 + Math.round(pulse * 2.0F)), 1, Render2D.alpha(-1, 0.8F), 0);
         }

         if (m == this.highlightModule && now - this.highlightStart < 1600L) {
            // gefundener Treffer: Ringe laufen nach außen
            float ht = (now - this.highlightStart) / 1600.0F;

            for (int k = 0; k < 2; k++) {
               float rt = (ht * 2.0F + k * 0.5F) % 1.0F;
               Render2D.ring(ctx, ix, iy, Math.min(64, r + 3 + Math.round(rt * 14.0F)), 1, Render2D.alpha(color, (1.0F - rt) * (1.0F - ht)), 0);
            }
         }

         String name = m.getName();
         int maxW = Math.max(40, Math.min(110, Math.round(o.spacing() / 0.75F) - 6));
         String shown = this.font.width(name) > maxW ? this.font.plainSubstrByWidth(name, maxW - this.font.width("…")) + "…" : name;
         int tw = Math.round(this.font.width(shown) * 0.75F);
         int textColor = Render2D.alpha(on > 0.5F ? -1578509 : -7564628, Math.max(0.35F, alpha));
         this.drawSmall(ctx, shown, ix - tw / 2, iy + r + 4, textColor, 0.75F);
      }
   }

   private void renderBursts(GuiGraphicsExtractor ctx, long now) {
      this.bursts.removeIf(bx -> now - bx.start() > 600L);

      for (MeowlScreen.Burst b : this.bursts) {
         float t = (float)(now - b.start()) / 600.0F;
         int rr = Math.round(b.r() + t * 26.0F);
         int a = Math.round(200.0F * (1.0F - t));
         Render2D.ring(ctx, Math.round(b.x()), Math.round(b.y()), Math.min(64, rr), 1, a << 24 | b.color() & 16777215, 0);
      }
   }

   private void renderTooltip(GuiGraphicsExtractor ctx, MeowlScreen.Orb o) {
      Module m = o.module();
      int w = 170;
      List<FormattedCharSequence> lines = this.font.split(Component.literal(m.getDescription()), Math.round((w - 16) / 0.75F));
      int shownLines = Math.min(3, lines.size());
      int h = 34 + shownLines * 8 + 12;
      int x = Math.round(o.x() + o.r() + 10.0F);
      if (x + w > this.width - 6) {
         x = Math.round(o.x() - o.r() - 10.0F - w);
      }

      int y = Math.max(40, Math.min(this.height - h - 30, Math.round(o.y() - h / 2.0F)));
      int color = o.section().color();
      Render2D.glow(ctx, x, y, w, h, 6, color, 0.5F);
      Render2D.roundRect(ctx, x, y, w, h, 6, -301265380);
      Render2D.roundRect(ctx, x, y, 2, h, 1, color);

      for (int sy = y + 2; sy < y + h - 2; sy += 3) {
         ctx.fill(x + 3, sy, x + w - 2, sy + 1, 150994943);
      }

      ctx.text(this.font, this.boldTrim(m.getName(), w - 60), x + 9, y + 7, -1578509, false);
      String state = m.isEnabled() ? "AKTIV" : "AUS";
      int stw = Math.round(this.font.width(state) * 0.75F) + 8;
      Render2D.roundRect(ctx, x + w - stw - 6, y + 6, stw, 10, 3, m.isEnabled() ? Render2D.alpha(color, 0.35F) : -14670534);
      this.drawSmall(ctx, state, x + w - stw - 2, y + 8, m.isEnabled() ? -1 : -10656900, 0.75F);

      for (int i = 0; i < shownLines; i++) {
         this.drawSmall(ctx, lines.get(i), x + 9, y + 20 + i * 8, -7564628);
      }

      this.drawSmall(ctx, "Linksklick: an/aus  ·  Rechtsklick: Einstellungen", x + 9, y + h - 11, -10656900, 0.75F);
   }

   private void renderTopBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int px = 2;
      int lx = 10;
      int ly = 8;

      MeowlSprite.draw(ctx, lx, ly);

      ctx.text(this.font, Component.literal("MEOWL").withStyle(ChatFormatting.BOLD), 32, 9, -1578509, true);
      UpdateManager u = this.clientSettings != null ? this.clientSettings.updates() : null;
      String version = u != null && u.currentVersion() != null ? "v" + u.currentVersion() : "dev";
      this.drawSmall(ctx, version, 32 + this.font.width(Component.literal("MEOWL").withStyle(ChatFormatting.BOLD)) + 4, 11, -10656900, 0.75F);
      String where = this.zoomSection != null && focused != null ? this.zoomSection.displayName() + "-System" : "Wähle einen Planeten";
      this.drawSmall(ctx, where, 32, 20, -7564628, 0.75F);
      int sw = Math.min(160, Math.max(90, this.width / 4));
      this.search.setBounds((this.width - sw) / 2, 8, sw, 16);
      this.search.render(ctx, this.font, false);
      if (this.searching()) {
         List<Module> hits = this.matches();
         boolean sameQuery = this.search.getText().trim().toLowerCase(Locale.ROOT).equals(this.searchQuery) && this.searchIndex >= 0;
         String info = hits.isEmpty()
            ? "Keine Treffer"
            : (hits.size() == 1
               ? "Enter: hinfliegen zu " + hits.get(0).getName()
               : (sameQuery ? "Treffer " + (this.searchIndex % hits.size() + 1) + "/" + hits.size() + " · Enter: nächster" : hits.size() + " Treffer · Enter: hinfliegen"));
         int iw = Math.round(this.font.width(info) * 0.75F);
         this.drawSmall(ctx, info, (this.width - iw) / 2, 27, -7564628, 0.75F);
      }

      int right = this.width - 8;
      // Zahnrad: kleines Einstellungsfenster
      boolean gh = Render2D.inside(mouseX, mouseY, right - 16, 9, 16, 14);
      setRect(this.gearRect, right - 16, 9, 16, 14);
      Render2D.roundRect(ctx, right - 16, 9, 16, 14, 7, this.quick.isOpen() ? -7643914 : (gh ? -534369472 : -1072424921));
      ctx.text(this.font, "⚙", right - 12, 12, this.quick.isOpen() || gh ? -1 : -7564628, false);
      right -= 22;
      if (u != null && u.updateAvailable()) {
         String label = u.state() == UpdateManager.State.READY
            ? "Neustart für Update"
            : (u.state() == UpdateManager.State.DOWNLOADING ? "Lädt " + Math.round(u.progress() * 100.0F) + "%" : "Update " + u.latest().version());
         int w = this.font.width(label) + 12;
         boolean hov = Render2D.inside(mouseX, mouseY, right - w, 9, w, 14);
         setRect(this.updateChip, right - w, 9, w, 14);
         Render2D.roundRect(ctx, right - w, 9, w, 14, 7, hov ? -8635667 : -7643914);
         ctx.text(this.font, label, right - w + 6, 12, -1, false);
         right -= w + 6;
      } else {
         setRect(this.updateChip, 0, 0, 0, 0);
      }

      MeowlService.Status st = MeowlService.get().status();

      int dot = switch (st) {
         case CONNECTED -> -11870592;
         case CONNECTING -> -278748;
         default -> -495247;
      };

      String label = switch (st) {
         case CONNECTED -> "Server online";
         case CONNECTING -> "Verbinde …";
         default -> "Server offline";
      };
      int w = this.font.width(label) + 18;
      boolean hov = Render2D.inside(mouseX, mouseY, right - w, 9, w, 14);
      setRect(this.serverChip, right - w, 9, w, 14);
      Render2D.roundRect(ctx, right - w, 9, w, 14, 7, hov ? -534369472 : -1072424921);
      Render2D.circle(ctx, right - w + 8, 16, 3, dot);
      ctx.text(this.font, label, right - w + 14, 12, -7564628, false);
   }

   private void renderBottom(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      this.buttons.clear();
      int x = 8;
      int y = this.height - 22;
      if (focused != null) {
         x += this.pillButton(ctx, x, y, "‹ Übersicht", mouseX, mouseY, () -> this.focus(null)) + 5;
      }

      x += this.pillButton(ctx, x, y, "✎ HUD bearbeiten", mouseX, mouseY, () -> this.minecraft.gui.setScreen(new HudEditorScreen(this, this.modules, this.config)))
         + 5;
      x += this.pillButton(ctx, x, y, "✎ Nametags bearbeiten", mouseX, mouseY, () -> this.minecraft.gui.setScreen(new NametagEditorScreen(this))) + 5;
      x += this.pillButton(ctx, x, y, "⚙ Client & Updates", mouseX, mouseY, this.drawer::openClient) + 5;
      x += this.pillButton(ctx, x, y, "☰ Changelog", mouseX, mouseY, () -> this.minecraft.gui.setScreen(new ChangelogScreen(this))) + 5;
      if (this.clientSettings != null) {
         x += this.pillButton(ctx, x, y, Lang.english() ? "EN → DE" : "DE → EN", mouseX, mouseY, this.clientSettings::toggleLanguage) + 5;
      }
      if (focused == null && PlanetPositions.hasCustom()) {
         x += this.pillButton(ctx, x, y, "↺ Planeten", mouseX, mouseY, PlanetPositions::reset) + 5;
      }

      if (BaulooStylesBridge.isAvailable()) {
         x += this.pillButton(ctx, x, y, "✦ Bauloo Styles", mouseX, mouseY, () -> BaulooStylesBridge.open(this)) + 5;
      }

      String hint = focused == null
         ? "Klick auf einen Planeten zum Hineinzoomen  ·  Mausrad dreht die Umlaufbahnen  ·  ⚙ oben rechts: Einstellungen"
         : "Linksklick Mond: an/aus  ·  Rechtsklick: Einstellungen  ·  Mausrad: drehen  ·  ESC: zurück";
      int maxW = this.width - x - (focused != null ? 80 : 10);
      if (maxW > 40) {
         this.drawSmall(ctx, this.font.plainSubstrByWidth(hint, Math.round(maxW / 0.75F)), x + 4, y + 5, -10656900, 0.75F);
      }
   }

   private int pillButton(GuiGraphicsExtractor ctx, int x, int y, String label, int mx, int my, Runnable action) {
      int w = this.font.width(label) + 12;
      boolean hov = Render2D.inside(mx, my, x, y, w, 15);
      Render2D.roundRect(ctx, x, y, w, 15, 7, hov ? -534106040 : -1072424921);
      ctx.text(this.font, label, x + 6, y + 4, hov ? -1578509 : -7564628, false);
      this.buttons.add(new MeowlScreen.Button(x, y, w, 15, action));
      return w;
   }

   private double smooth(double current, double target) {
      if (!this.animations()) {
         return target;
      } else {
         long now = System.nanoTime();
         float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
         double next = current + (target - current) * Math.min(1.0F, dt * 18.0F);
         return Math.abs(target - next) < 0.3 ? target : next;
      }
   }

   private float anim(Map<Module, Animation> map, Module m, float target) {
      Animation a = map.computeIfAbsent(m, k -> new Animation(14.0F, target));
      return this.animations() ? a.update(target) : target;
   }

   private void drawIcon(GuiGraphicsExtractor ctx, ItemStack stack, float cx, float cy, float scale) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx - 8.0F * scale, cy - 8.0F * scale);
      ctx.pose().scale(scale, scale);
      ctx.item(stack, 0, 0);
      ctx.pose().popMatrix();
   }

   private void drawSmall(GuiGraphicsExtractor ctx, String text, int x, int y, int color, float scale) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(x, y);
      ctx.pose().scale(scale, scale);
      ctx.text(this.font, text, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   private void drawSmall(GuiGraphicsExtractor ctx, FormattedCharSequence text, int x, int y, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(x, y);
      ctx.pose().scale(0.75F, 0.75F);
      ctx.text(this.font, text, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   private Component boldTrim(String name, int maxW) {
      Component full = Component.literal(name).withStyle(ChatFormatting.BOLD);
      if (this.font.width(full) <= maxW) {
         return full;
      } else {
         String cut = name;

         while (cut.length() > 1 && this.font.width(Component.literal(cut + "…").withStyle(ChatFormatting.BOLD)) > maxW) {
            cut = cut.substring(0, cut.length() - 1);
         }

         return Component.literal(cut.stripTrailing() + "…").withStyle(ChatFormatting.BOLD);
      }
   }

   private static void setRect(int[] r, int x, int y, int w, int h) {
      r[0] = x;
      r[1] = y;
      r[2] = w;
      r[3] = h;
   }

   private static boolean inRect(int[] r, double mx, double my) {
      return r[2] > 0 && Render2D.inside(mx, my, r[0], r[1], r[2], r[3]);
   }

   private static int darken(int argb) {
      int r = (argb >> 16 & 0xFF) / 3;
      int g = (argb >> 8 & 0xFF) / 3;
      int b = (argb & 0xFF) / 3;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private double[] unscale(double mx, double my) {
      float open = this.animations() ? this.openAnim.get() : 1.0F;
      float ease = 1.0F - (1.0F - open) * (1.0F - open);
      float s = 0.96F + 0.04F * ease;
      return new double[]{(mx - this.width / 2.0F * (1.0F - s)) / s, (my - this.height / 2.0F * (1.0F - s)) / s};
   }

   private static MeowlScreen.Star[] makeStars() {
      Random r = new Random(1707L);
      MeowlScreen.Star[] stars = new MeowlScreen.Star[190];
      int[] tints = new int[]{16777215, 16777215, 16777215, 13624319, 16771529, 15127807};

      for (int i = 0; i < stars.length; i++) {
         stars[i] = new MeowlScreen.Star(
            r.nextFloat(),
            r.nextFloat(),
            r.nextFloat() < 0.12F ? 2 : 1,
            0.2F + r.nextFloat() * 0.8F,
            r.nextFloat() * 6.28F,
            0.6F + r.nextFloat() * 2.2F,
            tints[r.nextInt(tints.length)]
         );
      }

      return stars;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double[] p = this.unscale(click.x(), click.y());
      double mx = p[0];
      double my = p[1];
      int button = click.button();
      if (inRect(this.gearRect, mx, my) && button == InputConstants.MOUSE_BUTTON_LEFT) {
         this.quick.toggle();
         return true;
      }

      if (this.quick.isOpen()) {
         if (this.quick.mouseClicked(mx, my, button)) {
            return true;
         }

         this.quick.close();
      }

      if (this.drawer.isOpen()) {
         if (!this.drawer.mouseClicked(mx, my, button) && !this.drawer.contains(mx, my)) {
            this.drawer.close();
         }

         return true;
      } else if (this.search.mouseClicked(mx, my, this.font)) {
         return true;
      } else if (!inRect(this.serverChip, mx, my) && !inRect(this.updateChip, mx, my)) {
         for (MeowlScreen.Button b : this.buttons) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
               b.action().run();
               return true;
            }
         }

         this.layout();
         MeowlScreen.Orb o = this.orbAt(mx, my);
         if (o == null) {
            Section planet = this.planetAt(mx, my);
            if (planet != null && button == InputConstants.MOUSE_BUTTON_LEFT && focused == null && this.zoomSection == null && com.lennart.nebula.module.mods.ClientSettings.planetDrag()) {
               // Übersicht: gedrückt halten + ziehen verschiebt den Planeten, kurzer Klick öffnet ihn
               this.dragPlanet = planet;
               this.dragStartX = mx;
               this.dragStartY = my;
               this.dragMoved = false;
               return true;
            } else if (planet != null) {
               this.clickPlanet(planet);
               return true;
            } else {
               return super.mouseClicked(click, doubled);
            }
         } else {
            if (!o.detail()) {
               this.focus(o.section());
            } else if (!o.module().isTool() || button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) {
               if (button == InputConstants.MOUSE_BUTTON_RIGHT && o.module().createScreen(this) != null) {
                  this.minecraft.gui.setScreen(o.module().createScreen(this));
               } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                  this.drawer.openModule(o.module());
               } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                  o.module().toggle();
                  this.config.save();
                  this.bursts.add(new MeowlScreen.Burst(o.x(), o.y(), o.r(), o.section().color(), System.currentTimeMillis()));
               }
            } else {
               o.module().openTool(this);
            }

            return true;
         }
      } else {
         this.drawer.openClient();
         return true;
      }
   }

   private void clickPlanet(Section planet) {
      if (isDirect(planet)) {
         this.openDirect(planet);
      } else {
         this.focus(planet);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      double[] p = this.unscale(click.x(), click.y());
      if (this.dragPlanet != null) {
         Section planet = this.dragPlanet;
         this.dragPlanet = null;
         if (this.dragMoved) {
            PlanetPositions.save();
         } else {
            this.clickPlanet(planet);
         }

         return true;
      } else {
         if (this.quick.mouseReleased(p[0], p[1], click.button())) {
            return true;
         }

         if (IngamePreview.active()) {
            // nach dem Loslassen noch kurz ingame zeigen, dann kommt das Menü zurück
            IngamePreview.show(this.drawer.module, 900L);
         }

         return this.drawer.isOpen() && this.drawer.mouseReleased(p[0], p[1], click.button()) ? true : super.mouseReleased(click);
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      double[] p = this.unscale(click.x(), click.y());
      if (this.dragPlanet != null) {
         if (!this.dragMoved && Math.hypot(p[0] - this.dragStartX, p[1] - this.dragStartY) > 4.0) {
            this.dragMoved = true;
         }

         if (this.dragMoved) {
            float top = 40.0F;
            float bottom = this.height - 30.0F;
            PlanetPositions.set(this.dragPlanet, (float)(p[0] / this.width), (float)((p[1] - top) / (bottom - top)));
         }

         return true;
      }

      if (this.quick.mouseDragged(p[0], p[1], click.button())) {
         return true;
      }

      if (this.drawer.isOpen() && this.drawer.mouseDragged(p[0], p[1], click.button())) {
         // Regler wird gezogen: Menü ausblenden und das Item ingame in der Hand zeigen (wenn das Modul eins hat)
         IngamePreview.show(this.drawer.module, 300L);
         return true;
      }

      return super.mouseDragged(click, offsetX, offsetY);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      double[] p = this.unscale(mouseX, mouseY);
      if (this.quick.scroll(p[0], p[1], verticalAmount)) {
         return true;
      }

      if (this.drawer.isOpen()) {
         this.drawer.scroll(p[0], verticalAmount);
      } else {
         this.spinTarget += verticalAmount * 20.0;
      }

      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      if (this.quick.charTyped(input.codepoint())) {
         return true;
      }

      if (!this.drawer.isOpen()) {
         if (!this.search.isFocused() && input.codepoint() > 32) {
            this.search.setFocused(true);
         }

         return this.search.charTyped(input.codepoint()) ? true : super.charTyped(input);
      } else {
         return this.drawer.charTyped(input.codepoint()) || super.charTyped(input);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.quick.keyPressed(input.key(), input.keycode(), input.modifiers())) {
         return true;
      }

      if (this.quick.isOpen() && input.key() == InputConstants.KEY_ESCAPE) {
         this.quick.close();
         return true;
      }

      if (this.search.isFocused() && this.searching() && (!this.drawer.isOpen() || this.drawer.capturingRow() == null) && (input.key() == InputConstants.KEY_RETURN || input.key() == InputConstants.KEY_NUMPADENTER)) {
         this.searchNext();
         return true;
      }

      if (this.drawer.isOpen() && this.drawer.keyPressed(input.key(), input.keycode(), input.modifiers())) {
         return true;
      } else {
         if (this.search.isFocused()) {
            if (input.key() == InputConstants.KEY_ESCAPE) {
               this.search.setFocused(false);
               this.search.setText("");
               return true;
            }

            if (this.search.keyPressed(input.key(), input.modifiers())) {
               return true;
            }
         }

         if (!this.search.isFocused() && NebulaClient.getInstance().getOpenGuiKey().matches(input)) {
            this.closeAll();
            return true;
         } else {
            return super.keyPressed(input);
         }
      }
   }

   @Override
   public void onClose() {
      if (this.drawer.isOpen()) {
         this.drawer.close();
      } else if (focused != null) {
         this.focus(null);
      } else {
         this.closeAll();
      }
   }

   private void closeAll() {
      if (this.drawer.isOpen()) {
         this.drawer.close();
      }

      if (!this.animations()) {
         this.minecraft.gui.setScreen(null);
      } else {
         this.closing = true;
      }
   }

   public void openClientPage() {
      this.drawer.openClient();
   }

   public void openSettings(Module module) {
      if (module.getSection() != null && !module.isHidden()) {
         this.focus(module.getSection());
      }

      this.drawer.openModule(module);
   }

   private record Burst(float x, float y, float r, int color, long start) {
   }

   private record Button(int x, int y, int w, int h, Runnable action) {
   }

   private final class ButtonRow implements MeowlScreen.Row {
      final String label;
      final Runnable action;
      String tab = "Mehr";

      @Override
      public boolean visible() {
         return MeowlScreen.this.drawer.showsTab(this.tab);
      }
      int bx;
      int by;
      int bw;

      ButtonRow(String label, Runnable action) {
         this.label = label;
         this.action = action;
      }

      @Override
      public int height() {
         return 20;
      }

      @Override
      public void place(int x, int y, int w) {
         this.bx = x;
         this.by = y;
         this.bw = w;
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
         this.place(x, y, w);
         boolean hov = Render2D.inside(mx, my, x, y, w, 18);
         Render2D.roundRect(ctx, x, y, w, 18, 6, hov ? -14012344 : -15197141);
         ctx.text(
            MeowlScreen.this.font,
            this.label,
            x + (w - MeowlScreen.this.font.width(this.label)) / 2,
            y + 5,
            hov ? -1578509 : -7564628,
            false
         );
      }

      @Override
      public boolean click(double mx, double my, int b) {
         if (b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, this.bx, this.by, this.bw, 18)) {
            this.action.run();
            return true;
         } else {
            return false;
         }
      }
   }

   private final class Drawer {
      private static final int HEADER_H = 46;
      private static final int FOOTER_H = 20;
      private static final int CARD_GAP = 6;
      private final Animation slide = new Animation(16.0F, 0.0F);
      private Module module;
      private boolean clientPage;
      private boolean open;
      private final List<MeowlScreen.Row> rows = new ArrayList<>();
      private double scroll;
      private double scrollTarget;
      private int contentHeight;
      private long resetArmed;
      private ModulePreview preview;
      private final List<String> tabs = new ArrayList<>();
      private String tab;
      private int tallest;
      private final List<int[]> tabRects = new ArrayList<>();

      /** Gruppen als Reiter, sobald es mindestens eine echte Gruppe gibt (übersichtlicher als eine lange Liste). */
      static final String TAB_HUD = "HUD-Edit";
      static final String TAB_RESET = "Zurücksetzen";

      /** Angezeigter Name eines Bereichs (Zurücksetzen fragt nach). */
      String tabLabel(String t) {
         return t.equals(TAB_RESET) && System.currentTimeMillis() - this.resetArmed <= 3000L ? "Nochmal klicken" : t;
      }

      boolean tabbed() {
         return !this.clientPage && this.tabs.size() >= 3;
      }

      boolean showsTab(String t) {
         return !this.tabbed() || t.equals(this.tab);
      }

      private int tabBar() {
         return 0;
      }

      /** Breite der Seitenleiste mit den Bereichen (nur bei Modulen mit Gruppen). */
      int navW() {
         if (!this.tabbed()) {
            return 0;
         } else {
            int w = 0;

            for (String t : this.tabs) {
               w = Math.max(w, MeowlScreen.this.font.width(t));
            }

            return Math.max(84, Math.min(118, w + 26));
         }
      }

      int accent() {
         return this.module != null ? this.module.getSection().color() : -7643914;
      }

      boolean isOpen() {
         return this.open;
      }

      boolean isClient() {
         return this.open && this.clientPage;
      }

      private float progress() {
         return MeowlScreen.this.animations() ? this.slide.get() : (this.open ? 1.0F : 0.0F);
      }

      int previewW() {
         if (this.preview == null) {
            return 0;
         } else {
            int max = (MeowlScreen.this.width - 60) / 2 - 10;
            return Math.max(120, Math.min(this.preview.preferredWidth(), max));
         }
      }

      int width() {
         int settingsW = Math.max(240, Math.min(360, MeowlScreen.this.width - 60));
         int w = this.preview == null ? settingsW : settingsW + this.previewW() + 10;
         if (this.tabbed()) {
            w += this.navW() + 8;
         }

         return Math.min(w, MeowlScreen.this.width - 16);
      }

      int height() {
         int content = this.tabbed() ? Math.max(Math.max(this.tallest, this.contentHeight), this.tabs.size() * 20 + 22) : this.contentHeight;
         int wanted = 78 + this.tabBar() + Math.max(this.preview == null ? 120 : 220, content);
         return Math.max(160, Math.min(MeowlScreen.this.height - 24, wanted));
      }

      int x() {
         return (MeowlScreen.this.width - this.width()) / 2;
      }

      int y() {
         float p = this.progress();
         return (MeowlScreen.this.height - this.height()) / 2 + Math.round((1.0F - p) * 10.0F);
      }

      boolean contains(double mx, double my) {
         return Render2D.inside(mx, my, this.x(), this.y(), this.width(), this.height());
      }

      boolean contains(double mx) {
         return mx >= this.x() && mx < this.x() + this.width();
      }

      int navX() {
         return this.preview == null ? this.x() + 10 : this.x() + 10 + this.previewW() + 10;
      }

      int areaX() {
         return this.navX() + (this.tabbed() ? this.navW() + 8 : 0);
      }

      int areaW() {
         return this.x() + this.width() - 10 - this.areaX();
      }

      int areaY() {
         return this.y() + 46 + 6 + this.tabBar();
      }

      int areaH() {
         return this.height() - 46 - 20 - 12 - this.tabBar();
      }

      void openModule(Module m) {
         this.module = m;
         this.clientPage = false;
         this.preview = m.preview();
         this.rows.clear();
         int total = m.getSettings().size();
         boolean many = total > 16;
         MeowlScreen.GroupRow current = null;
         String currentName = null;
         boolean first = true;

         this.tabs.clear();

         for (Setting<?> s : m.getSettingsForMenu()) {
            String g = s.getGroup();
            if (g != null && !g.equals(currentName)) {
               int count = (int)m.getSettingsForMenu().stream().filter(x -> g.equals(x.getGroup())).count();
               current = MeowlScreen.this.new GroupRow(m.getName() + "/" + g, g, count, many && !first);
               this.rows.add(current);
               first = false;
            } else if (g == null) {
               current = null;
            }

            currentName = g;
            MeowlScreen.SettingRow row = MeowlScreen.this.new SettingRow(s);
            row.group = current;
            row.tab = g != null ? g : (s instanceof com.lennart.nebula.setting.KeybindSetting && s.getName().equals("Keybind") ? "Mehr" : "Allgemein");
            // nur Bereiche mit sichtbaren Einstellungen (sonst z.B. ein leeres "Allgemein" aus internen Werten)
            if (s.isVisible() && !this.tabs.contains(row.tab) && !row.tab.equals("Mehr")) {
               this.tabs.add(row.tab);
            }

            this.rows.add(row);
         }

         // „Allgemein“ zuerst
         if (this.tabs.remove("Allgemein")) {
            this.tabs.add(0, "Allgemein");
         }

         if (this.tabs.isEmpty()) {
            this.tabs.add("Allgemein");
         }

         // Übersichtlich: Bereiche mit höchstens einer Einstellung in den ersten vollen Bereich einsortieren,
         // die Tastenbelegung („Mehr“) kommt ans Ende des ersten Bereichs statt in einen eigenen Bereich
         java.util.Map<String, Integer> visibleCount = new java.util.HashMap<>();
         for (MeowlScreen.Row r : this.rows) {
            if (r instanceof MeowlScreen.SettingRow sr && sr.c.getSetting().isVisible()) {
               visibleCount.merge(sr.tab, 1, Integer::sum);
            }
         }

         String target = this.tabs.stream().filter(t -> visibleCount.getOrDefault(t, 0) > 1).findFirst().orElse(this.tabs.get(0));
         java.util.Set<String> merged = new java.util.HashSet<>();
         merged.add("Mehr");
         for (String t : this.tabs) {
            if (!t.equals(target) && visibleCount.getOrDefault(t, 0) <= 1) {
               merged.add(t);
            }
         }

         this.tabs.removeAll(merged);
         java.util.List<MeowlScreen.Row> moved = new java.util.ArrayList<>();
         for (MeowlScreen.Row r : this.rows) {
            if (r instanceof MeowlScreen.SettingRow sr && merged.contains(sr.tab)) {
               sr.tab = target;
               if (sr.c.getSetting() instanceof com.lennart.nebula.setting.KeybindSetting && sr.c.getSetting().getName().equals("Keybind")) {
                  moved.add(r);
               }
            }
         }

         // Tastenbelegung ganz ans Ende
         this.rows.removeAll(moved);
         this.rows.addAll(moved);
         // eigene Bereiche als Aktionen: HUD-Editor direkt öffnen, Einstellungen zurücksetzen
         if (!m.getHudBoxes().isEmpty()) {
            this.tabs.add(TAB_HUD);
         }

         this.tabs.add(TAB_RESET);
         String last = LAST_TAB.get(m.getName());
         this.tab = last != null && this.tabs.contains(last) && !last.equals(TAB_HUD) && !last.equals(TAB_RESET) ? last : this.tabs.get(0);
         this.scroll = 0.0;
         this.scrollTarget = 0.0;
         this.open = true;
      }

      void openClient() {
         this.module = null;
         this.clientPage = true;
         this.tabs.clear();
         this.preview = null;
         this.rows.clear();
         this.rows.add(MeowlScreen.this.new StatusRow());

         for (Module m : MeowlScreen.this.modules.getModules()) {
            if (m.isHidden()) {
               MeowlScreen.GroupRow group = MeowlScreen.this.new GroupRow(
                  "client/" + m.getName(), m instanceof MeowlServerModule ? "Meowl Server" : m.getName(), 0, false
               );
               this.rows.add(group);

               for (Setting<?> s : m.getSettings()) {
                  if (!s.getName().equals("Keybind")) {
                     MeowlScreen.SettingRow row = MeowlScreen.this.new SettingRow(s);
                     row.group = group;
                     this.rows.add(row);
                  }
               }
            }
         }

         this.scroll = 0.0;
         this.scrollTarget = 0.0;
         this.open = true;
      }

      private void resetSettings() {
         long now = System.currentTimeMillis();
         if (now - this.resetArmed > 3000L) {
            this.resetArmed = now;
         } else {
            for (Setting<?> s : this.module.getSettings()) {
               if (!s.getName().equals("Keybind")) {
                  s.reset();
               }
            }

            MeowlScreen.this.config.save();
            this.resetArmed = 0L;
            this.openModule(this.module);
         }
      }

      void close() {
         this.open = false;
      }

      private int layoutHeight() {
         int cy = 0;
         Object prev = null;
         boolean any = false;

         for (MeowlScreen.Row r : this.rows) {
            if (r.visible()) {
               Object card = r.card();
               if (any && (card == null || card != prev)) {
                  if (prev != null) {
                     cy += 4;
                  }

                  cy += 6;
               }

               if (card != null && card != prev && !(r instanceof MeowlScreen.GroupRow)) {
                  cy += 4;
               }

               cy += r.height();
               prev = card;
               any = true;
            }
         }

         if (prev != null) {
            cy += 4;
         }

         return cy + 4;
      }

      void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
         float p = MeowlScreen.this.animations() ? this.slide.update(this.open ? 1.0F : 0.0F) : (this.open ? 1.0F : 0.0F);
         if (!(p <= 0.01F)) {
            double[] um = MeowlScreen.this.unscale((double)mouseX, (double)mouseY);
            int mx = (int)um[0];
            int my = (int)um[1];
            if (this.tabbed()) {
               // Höhe am längsten Reiter ausrichten, damit das Fenster beim Wechseln nicht springt
               String keep = this.tab;
               int max = 0;

               for (String t : this.tabs) {
                  this.tab = t;
                  max = Math.max(max, this.layoutHeight());
               }

               this.tab = keep;
               this.tallest = max;
            }

            this.contentHeight = this.layoutHeight();
            ctx.fill(0, 0, MeowlScreen.this.width, MeowlScreen.this.height, Render2D.alpha(-1728053248, p));
            int w = this.width();
            int h = this.height();
            int x = this.x();
            int y = this.y();
            int accent = this.accent();
            int alphaBg = Render2D.alpha(-15855591, Math.min(1.0F, p * 1.4F));
            Render2D.glow(ctx, x, y, w, h, 10, accent, 0.4F * p);
            Render2D.roundRect(ctx, x - 1, y - 1, w + 2, h + 2, 10, Render2D.alpha(Theme.lerp(-14275777, accent, 0.35F), p));
            Render2D.roundRect(ctx, x, y, w, h, 9, alphaBg);
            this.renderStars(ctx, x, y, w, h, p);
            this.renderHeader(ctx, x, y, w, mx, my);
            if (this.tabbed()) {
               this.renderNav(ctx, this.navX(), this.areaY(), this.navW(), this.areaH(), mx, my);
            }
            if (this.preview != null) {
               int px = x + 10;
               int py = this.areaY();
               int pw = this.previewW();
               int ph = this.areaH();
               Render2D.roundRect(ctx, px, py, pw, ph, 7, -16118765);
               Render2D.roundRect(ctx, px, py, pw, 14, 7, -15460313);
               ctx.fill(px, py + 7, px + pw, py + 14, -15460313);
               MeowlScreen.this.drawSmall(ctx, "VORSCHAU", px + 7, py + 4, -10656900, 0.75F);
               ctx.enableScissor(px, py + 14, px + pw, py + ph);
               this.preview.render(ctx, px, py + 14, pw, ph - 14, mx, my, delta);
               ctx.disableScissor();
            }

            int areaX = this.areaX();
            int areaW = this.areaW();
            int areaY = this.areaY();
            int areaH = this.areaH();
            double maxScroll = Math.max(0, this.contentHeight - areaH);
            this.scrollTarget = Math.max(0.0, Math.min(maxScroll, this.scrollTarget));
            this.scroll = Math.max(0.0, Math.min(maxScroll, MeowlScreen.this.smooth(this.scroll, this.scrollTarget)));
            boolean scrollbar = this.contentHeight > areaH;
            int cw = areaW - (scrollbar ? 6 : 0);
            ctx.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
            String hint = this.renderRows(ctx, areaX, areaY - (int)this.scroll, cw, mx, my, delta, areaY, areaH);
            ctx.disableScissor();
            if (this.scroll > 1.0) {
               ctx.fillGradient(areaX, areaY, areaX + cw, areaY + 8, -1072820199, 921625);
            }

            if (this.scroll < maxScroll - 1.0) {
               ctx.fillGradient(areaX, areaY + areaH - 8, areaX + cw, areaY + areaH, 921625, -1072820199);
            }

            if (scrollbar) {
               int barH = Math.max(18, areaH * areaH / this.contentHeight);
               int barY = areaY + (int)((areaH - barH) * (this.scroll / (this.contentHeight - areaH)));
               Render2D.roundRect(ctx, areaX + areaW - 3, areaY, 3, areaH, 1, -15328728);
               Render2D.roundRect(ctx, areaX + areaW - 3, barY, 3, barH, 1, -12959648);
            }

            if (hint == null && this.module != null && System.currentTimeMillis() - this.resetArmed < 3000L) {
               hint = "Nochmal klicken zum Zurücksetzen";
            }

            int fy = y + h - 20;
            ctx.fill(x + 10, fy - 2, x + w - 10, fy - 1, -14802381);
            String footer = hint != null
               ? hint
               : (
                  this.module != null
                     ? "Änderungen werden sofort gespeichert  ·  Rechtsklick auf Regler: Standardwert  ·  ESC: schließen"
                     : "ESC oder Klick daneben: schließen"
               );
            MeowlScreen.this.drawSmall(
               ctx, MeowlScreen.this.font.plainSubstrByWidth(footer, Math.round((w - 20) / 0.75F)), x + 10, fy + 5, hint != null ? -7564628 : -10656900, 0.75F
            );
         }
      }

      /** Dezente Sterne im Fenster – wie im Weltraum-Menü dahinter. */
      private void renderStars(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float p) {
         float t = (float)System.currentTimeMillis() / 1000.0F;
         ctx.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);

         for (MeowlScreen.Star st : MeowlScreen.STARS) {
            int sx = x + Math.round(st.x() * w);
            int sy = y + Math.round(st.y() * h);
            float tw = 0.55F + 0.45F * (float)Math.sin(t * st.speed() + st.phase());
            int a = Math.round(60.0F * p * tw * st.depth());
            if (a > 3) {
               ctx.fill(sx, sy, sx + 1, sy + 1, a << 24 | st.rgb());
            }
         }

         ctx.disableScissor();
      }

      /** Seitenleiste: alle Bereiche des Moduls untereinander – läuft nie über, auch bei vielen Gruppen. */
      private void renderNav(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mx, int my) {
         this.tabRects.clear();
         int accent = this.accent();
         Render2D.roundRect(ctx, x, y, w, h, 7, -16118765);
         MeowlScreen.this.drawSmall(ctx, "BEREICHE", x + 8, y + 6, -10656900, 0.75F);
         int itemH = 18;
         int iy = y + 16;
         int maxItems = Math.max(1, (h - 20) / (itemH + 2));
         boolean compact = this.tabs.size() > maxItems;
         if (compact) {
            itemH = Math.max(11, (h - 20) / this.tabs.size() - 2);
         }

         for (int i = 0; i < this.tabs.size(); i++) {
            String t = this.tabs.get(i);
            boolean active = t.equals(this.tab);
            boolean hov = Render2D.inside(mx, my, x + 3, iy, w - 6, itemH);
            if (active) {
               Render2D.roundRect(ctx, x + 3, iy, w - 6, itemH, 5, Render2D.alpha(accent, 0.22F));
               Render2D.roundRect(ctx, x + 3, iy + 3, 2, itemH - 6, 1, accent);
            } else if (hov) {
               Render2D.roundRect(ctx, x + 3, iy, w - 6, itemH, 5, -14736071);
            }

            // Anzahl sichtbarer Einstellungen im Bereich
            int n = 0;

            for (MeowlScreen.Row r : this.rows) {
               if (r instanceof MeowlScreen.SettingRow sr && t.equals(sr.tab) && sr.c.getSetting().isVisible()) {
                  n++;
               }
            }

            int color = active ? -1 : (hov ? -1578509 : -7564628);
            float scale = compact || MeowlScreen.this.font.width(t) > w - 30 ? 0.75F : 1.0F;
            int textY = iy + (itemH - Math.round(8 * scale)) / 2;
            String shown = MeowlScreen.this.font.plainSubstrByWidth(this.tabLabel(t), Math.round((w - 30) / scale));
            MeowlScreen.this.drawSmall(ctx, shown, x + 10, textY, color, scale);
            if (n > 0) {
               String c = String.valueOf(n);
               MeowlScreen.this.drawSmall(ctx, c, x + w - 8 - Math.round(MeowlScreen.this.font.width(c) * 0.75F), iy + (itemH - 6) / 2, active ? accent : -10656900, 0.75F);
            }

            this.tabRects.add(new int[]{x + 3, iy, w - 6, itemH, i});
            iy += itemH + 2;
         }
      }

      /** Reiterleiste: eine Pille pro Gruppe, aktive in Planetenfarbe. */
      private void renderTabs(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
         this.tabRects.clear();
         int gap = 3;
         int total = 0;

         for (String t : this.tabs) {
            total += MeowlScreen.this.font.width(t) + 14 + gap;
         }

         float scale = total > w ? 0.75F : 1.0F;
         int cx = x;
         int accent = this.accent();

         for (int i = 0; i < this.tabs.size(); i++) {
            String t = this.tabs.get(i);
            int tw = Math.round(MeowlScreen.this.font.width(t) * scale) + (scale < 1.0F ? 10 : 14);
            boolean active = t.equals(this.tab);
            boolean hov = Render2D.inside(mx, my, cx, y, tw, 16);
            Render2D.roundRect(ctx, cx, y, tw, 16, 8, active ? Render2D.alpha(accent, 0.85F) : (hov ? -14012344 : -15197141));
            int color = active ? -15724528 : (hov ? -1578509 : -7564628);
            if (scale < 1.0F) {
               MeowlScreen.this.drawSmall(ctx, this.tabLabel(t), cx + 5, y + 5, color, scale);
            } else {
               ctx.text(MeowlScreen.this.font, this.tabLabel(t), cx + 7, y + 4, color, false);
            }

            this.tabRects.add(new int[]{cx, y, tw, 16, i});
            cx += tw + gap;
         }
      }

      private void renderHeader(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
         ItemStack icon = this.module != null ? this.module.getIcon() : com.lennart.nebula.util.SafeStack.of(Items.COMPARATOR);
         int color = this.module != null ? this.module.getSection().color() : -7035976;
         int dark = this.module != null ? this.module.getSection().dark() : -15197141;
         boolean enabled = this.module != null && this.module.isEnabled();
         // Kopf mit Verlauf in Planetenfarbe
         ctx.fillGradient(x + 1, y + 1, x + w - 1, y + 44, Render2D.alpha(color, 0.16F), 0);
         // Modul-Symbol als kleiner Planet
         int pr = 14;
         for (int i = 3; i >= 1; i--) {
            Render2D.circle(ctx, x + 24, y + 23, pr + i * 3, Math.round((enabled ? 50.0F : 22.0F) / i) << 24 | color & 16777215);
         }

         Render2D.circle(ctx, x + 24, y + 23, pr, dark);
         Render2D.circle(ctx, x + 24, y + 23, Math.round(pr * 0.86F), Theme.lerp(dark, color, enabled ? 0.55F : 0.3F));
         Render2D.circle(ctx, x + 18, y + 17, 3, 0x22FFFFFF);
         MeowlScreen.this.drawIcon(ctx, icon, x + 24, y + 23, 1.05F);
         int textX = x + 46;
         int right = x + w - 10;
         boolean closeHover = Render2D.inside(mx, my, right - 16, y + 8, 16, 16);
         Render2D.roundRect(ctx, right - 16, y + 8, 16, 16, 5, closeHover ? -12967888 : -15197141);
         ctx.text(MeowlScreen.this.font, "✕", right - 11, y + 12, closeHover ? -495247 : -7564628, false);
         right -= 24;
         if (this.module != null) {
            String state = enabled ? "Aktiv" : "Aus";
            Render2D.toggleSwitch(ctx, right - 26, y + 9, enabled ? 1.0F : 0.0F);
            int sw = MeowlScreen.this.font.width(state);
            ctx.text(MeowlScreen.this.font, state, right - 32 - sw, y + 12, enabled ? -3885571 : -10656900, false);
            right -= 40 + sw;
         }

         String title = this.module != null ? Lang.t(this.module.getName()) : "Client & Updates";
         Component bold = MeowlScreen.this.boldTrim(title, right - textX - 4);
         ctx.text(MeowlScreen.this.font, bold, textX, y + 9, -1578509, false);
         if (this.module != null && !this.module.isHidden()) {
            // Planet als kleiner Chip neben dem Titel
            String chip = "● " + Lang.t(this.module.getSection().displayName());
            int cx = textX + MeowlScreen.this.font.width(bold) + 6;
            int cw = Math.round(MeowlScreen.this.font.width(chip) * 0.75F) + 8;
            if (cx + cw < right) {
               Render2D.roundRect(ctx, cx, y + 8, cw, 10, 5, Render2D.alpha(color, 0.22F));
               MeowlScreen.this.drawSmall(ctx, chip, cx + 4, y + 10, color, 0.75F);
            }
         }

         String desc = this.module != null ? this.module.getDescription() : "Verbindung, Updates und Oberfläche";
         List<FormattedCharSequence> lines = MeowlScreen.this.font.split(Component.literal(Lang.t(desc)), Math.round((x + w - 10 - textX) / 0.75F));

         for (int i = 0; i < Math.min(2, lines.size()); i++) {
            MeowlScreen.this.drawSmall(ctx, lines.get(i), textX, y + 22 + i * 8, -7564628);
         }

         ctx.fill(x + 10, y + 46 - 1, x + w - 10, y + 46, Theme.lerp(-14802381, color, 0.35F));
      }

      private String renderRows(GuiGraphicsExtractor ctx, int x, int startY, int cw, int mx, int my, float delta, int areaY, int areaH) {
         List<int[]> spans = new ArrayList<>();
         List<Integer> ys = new ArrayList<>();
         int cy = startY;
         Object prevCard = null;
         int spanStart = -1;
         boolean any = false;

         for (MeowlScreen.Row r : this.rows) {
            if (!r.visible()) {
               ys.add(Integer.MIN_VALUE);
            } else {
               Object card = r.card();
               if (any && (card == null || card != prevCard)) {
                  if (prevCard != null) {
                     spans.add(new int[]{spanStart, cy + 4});
                     cy += 4;
                  }

                  cy += 6;
               }

               if (card != null && card != prevCard) {
                  spanStart = cy;
                  if (!(r instanceof MeowlScreen.GroupRow)) {
                     cy += 4;
                  }
               }

               ys.add(cy);
               cy += r.height();
               prevCard = card;
               any = true;
            }
         }

         if (prevCard != null) {
            spans.add(new int[]{spanStart, cy + 4});
         }

         for (int[] span : spans) {
            Render2D.roundRect(ctx, x, span[0], cw, span[1] - span[0], 7, -15394521);
         }

         String hint = null;

         for (int i = 0; i < this.rows.size(); i++) {
            MeowlScreen.Row rx = this.rows.get(i);
            int ry = ys.get(i);
            if (ry == Integer.MIN_VALUE) {
               if (rx instanceof MeowlScreen.SettingRow sr) {
                  sr.c.setBounds(-1000, -1000, 0);
               }
            } else {
               if (ry + rx.height() >= areaY && ry <= areaY + areaH) {
                  if (rx instanceof MeowlScreen.SettingRow && my >= areaY && my < areaY + areaH && Render2D.inside(mx, my, x, ry, cw, rx.height())) {
                     // Zeile unter der Maus: leichter Schimmer + Akzentstrich in Planetenfarbe
                     int accent = this.accent();
                     Render2D.roundRect(ctx, x + 2, ry + 1, cw - 4, rx.height() - 2, 4, Render2D.alpha(accent, 0.08F));
                     ctx.fill(x + 3, ry + 4, x + 5, ry + rx.height() - 4, Render2D.alpha(accent, 0.9F));
                  }

                  rx.render(ctx, x, ry, cw, mx, my, delta);
               } else {
                  rx.place(x, ry, cw);
               }

               if (rx instanceof MeowlScreen.SettingRow && i + 1 < this.rows.size()) {
                  MeowlScreen.Row next = this.rows.get(i + 1);
                  if (next instanceof MeowlScreen.SettingRow && next.visible() && next.card() == rx.card()) {
                     ctx.fill(x + 10, ry + rx.height(), x + cw - 10, ry + rx.height() + 1, -14802381);
                  }
               }

               if (my >= areaY && my < areaY + areaH && hint == null) {
                  hint = rx.hint(mx, my);
               }
            }
         }

         return hint;
      }

      private MeowlScreen.Row capturingRow() {
         for (MeowlScreen.Row r : this.rows) {
            if (r.capturing()) {
               return r;
            }
         }

         return null;
      }

      boolean mouseClicked(double mx, double my, int button) {
         if (!this.contains(mx, my)) {
            return false;
         } else {
            MeowlScreen.Row cap = this.capturingRow();
            if (cap != null) {
               return cap.click(mx, my, button);
            } else {
               int x = this.x();
               int y = this.y();
               int right = x + this.width() - 10;
               if (button == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, right - 16, y + 8, 16, 16)) {
                  this.close();
                  return true;
               } else if (this.module != null && button == InputConstants.MOUSE_BUTTON_LEFT && my < y + 46 && mx >= right - 24 - 80 && mx < right - 24) {
                  this.module.toggle();
                  MeowlScreen.this.config.save();
                  return true;
               } else {
                  if (button == InputConstants.MOUSE_BUTTON_LEFT && this.tabbed()) {
                     for (int[] r : this.tabRects) {
                        if (Render2D.inside(mx, my, r[0], r[1], r[2], r[3])) {
                           String clicked = this.tabs.get(r[4]);
                           if (clicked.equals(TAB_HUD)) {
                              MeowlScreen.this.minecraft.gui.setScreen(new HudEditorScreen(MeowlScreen.this, MeowlScreen.this.modules, MeowlScreen.this.config));
                              return true;
                           } else if (clicked.equals(TAB_RESET)) {
                              this.resetSettings();
                              return true;
                           }

                           this.tab = clicked;
                           LAST_TAB.put(this.module.getName(), this.tab);
                           this.scroll = 0.0;
                           this.scrollTarget = 0.0;
                           MeowlScreen.this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F));
                           return true;
                        }
                     }
                  }

                  if (this.preview != null && this.preview.mouseClicked(mx, my, button)) {
                     return true;
                  }

                  int areaY = this.areaY();
                  int areaH = this.areaH();
                  if (my >= areaY && my < areaY + areaH && mx >= this.areaX()) {
                     for (MeowlScreen.Row r : this.rows) {
                        if (r.visible() && r.click(mx, my, button)) {
                           return true;
                        }
                     }
                  }

                  return true;
               }
            }
         }
      }

      boolean mouseReleased(double mx, double my, int button) {
         for (MeowlScreen.Row r : this.rows) {
            if (r.release(mx, my, button)) {
               return true;
            }
         }

         return false;
      }

      boolean mouseDragged(double mx, double my, int button) {
         for (MeowlScreen.Row r : this.rows) {
            if (r.drag(mx, my, button)) {
               MeowlScreen.this.previewRow = r;
               return true;
            }
         }

         return false;
      }

      void scroll(double mx, double amount) {
         if (this.contains(mx)) {
            this.scrollTarget -= amount * 28.0;
         }
      }

      boolean keyPressed(int key, int scancode, int modifiers) {
         for (MeowlScreen.Row r : this.rows) {
            if (r.key(key, scancode, modifiers)) {
               return true;
            }
         }

         return false;
      }

      boolean charTyped(int codepoint) {
         for (MeowlScreen.Row r : this.rows) {
            if (r.chr(codepoint)) {
               return true;
            }
         }

         return false;
      }
   }

   private final class GroupRow implements MeowlScreen.Row {
      final String key;
      final String title;
      final int count;
      final Animation arrow;
      int bx;
      int by;
      int bw;

      GroupRow(String key, String title, int count, boolean collapsedByDefault) {
         this.key = key;
         this.title = title;
         this.count = count;
         MeowlScreen.COLLAPSED.putIfAbsent(key, collapsedByDefault);
         this.arrow = new Animation(16.0F, this.collapsed() ? 0.0F : 1.0F);
      }

      boolean collapsed() {
         return MeowlScreen.COLLAPSED.getOrDefault(this.key, false);
      }

      @Override
      public boolean visible() {
         return !MeowlScreen.this.drawer.tabbed();
      }

      @Override
      public Object card() {
         return this;
      }

      @Override
      public int height() {
         return 22;
      }

      @Override
      public void place(int x, int y, int w) {
         this.bx = x;
         this.by = y;
         this.bw = w;
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
         this.place(x, y, w);
         boolean hov = Render2D.inside(mx, my, x, y, w, 22);
         Render2D.roundRect(ctx, x, y, w, 22, 7, hov ? -14736071 : -15065296);
         if (!this.collapsed()) {
            ctx.fill(x, y + 15, x + w, y + 22, hov ? -14736071 : -15065296);
            ctx.fill(x, y + 21, x + w, y + 22, -14802381);
         }

         float a = MeowlScreen.this.animations() ? this.arrow.update(this.collapsed() ? 0.0F : 1.0F) : (this.collapsed() ? 0.0F : 1.0F);
         Render2D.roundRect(ctx, x + 8, y + 7, 3, 8, 1, -7643914);
         ctx.text(
            MeowlScreen.this.font, Component.literal(this.title.toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD), x + 16, y + 7, -1578509, false
         );
         String n = this.count > 0 ? String.valueOf(this.count) : "";
         ctx.text(MeowlScreen.this.font, a > 0.5F ? "▾" : "▸", x + w - 14, y + 7, -7564628, false);
         if (!n.isEmpty()) {
            MeowlScreen.this.drawSmall(ctx, n, x + w - 22 - Math.round(MeowlScreen.this.font.width(n) * 0.75F), y + 8, -10656900, 0.75F);
         }
      }

      @Override
      public boolean click(double mx, double my, int b) {
         if (b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, this.bx, this.by, this.bw, 22)) {
            MeowlScreen.COLLAPSED.put(this.key, !this.collapsed());
            return true;
         } else {
            return false;
         }
      }
   }

   private record Orb(Module module, Section section, float x, float y, float r, float depth, float alpha, boolean detail, float spacing) {
   }

   private interface Row {
      int height();

      default Object card() {
         return null;
      }

      default void place(int x, int y, int w) {
      }

      default boolean visible() {
         return true;
      }

      void render(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, int var6, float var7);

      default boolean click(double mx, double my, int button) {
         return false;
      }

      default boolean release(double mx, double my, int button) {
         return false;
      }

      default boolean drag(double mx, double my, int button) {
         return false;
      }

      default boolean key(int key, int scancode, int modifiers) {
         return false;
      }

      default boolean chr(int codepoint) {
         return false;
      }

      default boolean capturing() {
         return false;
      }

      default String hint(double mx, double my) {
         return null;
      }
   }

   private final class SectionRow implements MeowlScreen.Row {
      final String title;

      SectionRow(String title) {
         this.title = title;
      }

      @Override
      public int height() {
         return 16;
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
         ctx.text(MeowlScreen.this.font, Component.literal(this.title).withStyle(ChatFormatting.BOLD), x + 2, y + 6, -3885571, false);
      }
   }

   private final class SettingRow implements MeowlScreen.Row {
      final SettingComponent<?> c;
      MeowlScreen.GroupRow group;
      String tab = "Allgemein";

      SettingRow(Setting<?> setting) {
         this.c = ComponentFactory.create(setting, MeowlScreen.this.config::save);
      }

      @Override
      public int height() {
         return this.c.getHeight();
      }

      @Override
      public boolean visible() {
         if (!this.c.getSetting().isVisible()) {
            return false;
         } else {
            MeowlScreen.Drawer d = MeowlScreen.this.drawer;
            return d.tabbed() ? d.showsTab(this.tab) : this.group == null || !this.group.collapsed();
         }
      }

      @Override
      public Object card() {
         return this.group != null ? this.group : MeowlScreen.this.drawer;
      }

      int lx;
      int ly;
      int lw;

      @Override
      public void place(int x, int y, int w) {
         this.c.setBounds(x, y, w);
         this.lx = x;
         this.ly = y;
         this.lw = w;
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
         this.c.setBounds(x, y, w);
         this.lx = x;
         this.ly = y;
         this.lw = w;
         this.c.render(ctx, MeowlScreen.this.font, mx, my, delta);
      }

      @Override
      public boolean click(double mx, double my, int b) {
         return this.c.mouseClicked(mx, my, b);
      }

      @Override
      public boolean release(double mx, double my, int b) {
         return this.c.mouseReleased(mx, my, b);
      }

      @Override
      public boolean drag(double mx, double my, int b) {
         return this.c.mouseDragged(mx, my, b);
      }

      @Override
      public boolean key(int k, int s, int m) {
         return this.c.keyPressed(k, s, m);
      }

      @Override
      public boolean chr(int cp) {
         return this.c.charTyped(cp);
      }

      @Override
      public boolean capturing() {
         return this.c.isCapturingInput();
      }

      @Override
      public String hint(double mx, double my) {
         return this.c.isHovered(mx, my) ? this.c.getSetting().getDescription() : null;
      }
   }

   private record Star(float x, float y, int size, float depth, float phase, float speed, int rgb) {
   }

   private final class StatusRow implements MeowlScreen.Row {
      int bx;
      int by;
      int bw;

      @Override
      public int height() {
         return 58;
      }

      @Override
      public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
         Render2D.roundRect(ctx, x, y, w, 54, 5, -15328728);
         UpdateManager u = MeowlScreen.this.clientSettings != null ? MeowlScreen.this.clientSettings.updates() : null;
         String version = u != null && u.currentVersion() != null ? u.currentVersion().toString() : "Entwicklung";
         ctx.text(MeowlScreen.this.font, "Meowl Client " + version, x + 8, y + 6, -1578509, false);
         MeowlService service = MeowlService.get();
         int dot = service.isConnected() ? -11870592 : (service.status() == MeowlService.Status.CONNECTING ? -278748 : -495247);
         Render2D.circle(ctx, x + 11, y + 21, 3, dot);
         MeowlScreen.this.drawSmall(
            ctx,
            MeowlScreen.this.font.plainSubstrByWidth("Meowl Server: " + service.statusText(), Math.round((w - 24) / 0.75F)),
            x + 18,
            y + 18,
            -7564628,
            0.75F
         );
         String msg = u == null ? "" : u.message();
         MeowlScreen.this.drawSmall(ctx, MeowlScreen.this.font.plainSubstrByWidth(msg, Math.round((w - 16) / 0.75F)), x + 8, y + 28, -7564628, 0.75F);
         String label = null;
         if (u != null) {
            label = switch (u.state()) {
               case AVAILABLE, FAILED -> u.latest() != null ? "Update " + u.latest().version() + " herunterladen" : "Erneut prüfen";
               case DOWNLOADING -> null;
               case READY -> u.restartArmed() ? "Sicher? Nochmal klicken zum Beenden" : "Minecraft beenden & Update installieren";
               case DISABLED -> null;
               default -> "Nach Updates suchen";
            };
         }

         this.bx = x + 8;
         this.by = y + 38;
         this.bw = w - 16;
         if (u != null && u.state() == UpdateManager.State.DOWNLOADING) {
            Render2D.roundRect(ctx, this.bx, this.by, this.bw, 10, 4, -14275777);
            Render2D.roundRect(ctx, this.bx, this.by, Math.max(8, Math.round(this.bw * u.progress())), 10, 4, -7643914);
         } else if (label != null) {
            boolean hov = Render2D.inside(mx, my, this.bx, this.by, this.bw, 12);
            Render2D.roundRect(ctx, this.bx, this.by, this.bw, 12, 4, hov ? -8635667 : -11788907);
            MeowlScreen.this.drawSmall(
               ctx, label, this.bx + (this.bw - Math.round(MeowlScreen.this.font.width(label) * 0.75F)) / 2, this.by + 3, -1, 0.75F
            );
         }
      }

      @Override
      public boolean click(double mx, double my, int b) {
         UpdateManager u = MeowlScreen.this.clientSettings != null ? MeowlScreen.this.clientSettings.updates() : null;
         if (u != null && b == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, this.bx, this.by, this.bw, 12)) {
            if ((u.state() == UpdateManager.State.AVAILABLE || u.state() == UpdateManager.State.FAILED) && u.latest() != null) {
               u.download();
            } else if (u.state() == UpdateManager.State.READY) {
               u.restartToInstall();
            } else {
               u.check(true);
            }

            return true;
         } else {
            return false;
         }
      }
   }
}
