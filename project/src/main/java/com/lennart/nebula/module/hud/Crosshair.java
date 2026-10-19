package com.lennart.nebula.module.hud;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.gui.Animation;
import com.lennart.nebula.gui.ModulePreview;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class Crosshair extends Module {
   public static final String CROSS = "Kreuz";
   public static final String DOT = "Punkt";
   public static final String CROSS_DOT = "Kreuz + Punkt";
   public static final String CIRCLE = "Kreis";
   public static final String CIRCLE_DOT = "Kreis + Punkt";
   public static final String T_SHAPE = "T-Form";
   public static final String ARROW = "Pfeil";
   public static final String SQUARE = "Quadrat";
   public static final String BRACKETS = "Klammern";
   private final ModeSetting preset = this.add(
      new ModeSetting(
            "Form", "Form des Fadenkreuzes", "Kreuz", "Kreuz", "Punkt", "Kreuz + Punkt", "Kreis", "Kreis + Punkt", "T-Form", "Pfeil", "Quadrat", "Klammern"
         )
         .alias(new String[]{"Preset"})
   );
   private final NumberSetting size = this.add(new NumberSetting("Größe", "Länge der Linien", 5.0, 1.0, 20.0, 1.0, "px"));
   private final NumberSetting thickness = this.add(new NumberSetting("Dicke", "Linienstärke", 1.0, 1.0, 6.0, 1.0, "px"));
   private final NumberSetting gap = this.add(
      new NumberSetting("Abstand", "Abstand der Linien zur Mitte", 2.0, 0.0, 15.0, 1.0, "px").alias(new String[]{"Gap"})
   );
   private final NumberSetting rotation = this.add(new NumberSetting("Drehung", "Dreht das Fadenkreuz (45° = X-Form)", 0.0, 0.0, 90.0, 5.0, "°"));
   private final BooleanSetting centerDot = this.add(new BooleanSetting("Mittelpunkt", "Zusätzlicher Punkt in der Mitte", false));
   private final NumberSetting dotSize = this.add(new NumberSetting("Punkt-Größe", "Größe des Mittelpunkts", 1.0, 1.0, 6.0, 1.0, "px"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe des Fadenkreuzes", -1));
   private final NumberSetting opacity = this.add(new NumberSetting("Transparenz", "Deckkraft des Fadenkreuzes", 100.0, 10.0, 100.0, 5.0, "%"));
   private final BooleanSetting outline = this.add(new BooleanSetting("Outline", "Rand um das Fadenkreuz – gut sichtbar auf hellem Hintergrund", true));
   private final NumberSetting outlineWidth = this.add(new NumberSetting("Outline-Stärke", "Breite des Randes", 1.0, 1.0, 3.0, 1.0, "px"));
   private final ColorSetting outlineColor = this.add(new ColorSetting("Outline-Farbe", "Farbe des Randes", -16777216));
   private final BooleanSetting dynamic = this.add(new BooleanSetting("Dynamisch", "Abstand wächst beim Laufen, Sprinten, Springen und nach Schlägen", false));
   private final NumberSetting dynamicStrength = this.add(
      new NumberSetting("Dynamik-Stärke", "Wie stark sich das Fadenkreuz öffnet", 1.0, 0.25, 3.0, 0.25, "x")
   );
   private final BooleanSetting targetColorOn = this.add(new BooleanSetting("Ziel-Farbe", "Andere Farbe, solange ein Spieler/Mob anvisiert ist", false));
   private final ColorSetting targetColor = this.add(new ColorSetting("Farbe bei Ziel", "Farbe, wenn ein Wesen anvisiert ist", -1096636));
   private final BooleanSetting cooldownDim = this.add(
      new BooleanSetting("Cooldown abdunkeln", "Fadenkreuz ist blasser, bis der nächste Schlag voll geladen ist", false)
   );
   private final BooleanSetting thirdPerson = this.add(new BooleanSetting("In 3. Person", "Fadenkreuz auch in der F5-Ansicht zeigen", false));
   private final BooleanSetting showWithDebug = this.add(
      new BooleanSetting("Mit F3 zeigen", "Auch bei offenem Debug-Bildschirm (F3) das eigene Fadenkreuz statt der Achsen zeigen", true)
   );
   private final Animation spread = new Animation(12.0F, 0.0F);

   public Crosshair() {
      super("Crosshair", "Eigenes Fadenkreuz mit Live-Vorschau: Form, Größe, Abstand, Farbe, Outline und dynamische Zustände.", Category.HUD);
      this.section(Section.PVP);
      this.icon(Items.TARGET);
      this.group("Form", new Setting[]{this.preset, this.size, this.thickness, this.gap, this.rotation, this.centerDot, this.dotSize});
      this.group("Farbe & Outline", new Setting[]{this.color, this.opacity, this.outline, this.outlineWidth, this.outlineColor});
      this.group("Dynamik & Zustände", new Setting[]{this.dynamic, this.dynamicStrength, this.targetColorOn, this.targetColor, this.cooldownDim});
      this.group("Anzeige", new Setting[]{this.thirdPerson, this.showWithDebug});
      this.outlineColor.setVisibility(this.outline::get);
      this.outlineWidth.setVisibility(this.outline::get);
      this.dotSize.setVisibility(() -> this.centerDot.get() || this.preset.is("Punkt") || this.preset.is("Kreuz + Punkt") || this.preset.is("Kreis + Punkt"));
      this.dynamicStrength.setVisibility(this.dynamic::get);
      this.targetColor.setVisibility(this.targetColorOn::get);
      this.size.setVisibility(() -> !this.preset.is("Punkt"));
      this.gap.setVisibility(() -> !this.preset.is("Punkt"));
   }

   public boolean render(GuiGraphics ctx) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR)) {
         if (!mc.options.getCameraType().isFirstPerson() && !this.thirdPerson.get()) {
            return true;
         } else if (mc.getDebugOverlay().showDebugScreen() && !this.showWithDebug.get()) {
            return false;
         } else {
            float s = this.spread.update(this.dynamic.get() ? this.dynamicSpread(player) : 0.0F);
            boolean target = mc.crosshairPickEntity instanceof LivingEntity;
            this.draw(
               ctx, ctx.guiWidth() / 2, ctx.guiHeight() / 2, new Crosshair.State(s, target, player.getAttackStrengthScale(0.0F))
            );
            return true;
         }
      } else {
         return true;
      }
   }

   private float dynamicSpread(LocalPlayer player) {
      float s = 0.0F;
      double vx = player.getX() - player.xo;
      double vz = player.getZ() - player.zo;
      if (vx * vx + vz * vz > 4.0E-4) {
         s += 2.0F;
      }

      if (player.isSprinting()) {
         s += 2.0F;
      }

      if (!player.onGround()) {
         s += 3.0F;
      }

      s += (1.0F - player.getAttackStrengthScale(0.0F)) * 4.0F;
      return s * (float)this.dynamicStrength.get().doubleValue();
   }

   public void draw(GuiGraphics ctx, int cx, int cy, Crosshair.State state) {
      int t = this.thickness.getInt();
      int len = this.size.getInt();
      int g = this.gap.getInt() + Math.round(state.spread());
      float alpha = (float)(this.opacity.get() / 100.0);
      if (this.cooldownDim.get() && state.cooldown() < 1.0F) {
         alpha *= 0.35F + 0.65F * state.cooldown();
      }

      int base = this.targetColorOn.get() && state.target() ? this.targetColor.get() : this.color.get();
      int main = withAlpha(base, alpha);
      int border = withAlpha(this.outlineColor.get(), alpha);
      int ol = this.outline.get() ? this.outlineWidth.getInt() : 0;
      float rot = (float)this.rotation.get().doubleValue();
      boolean rotated = rot % 360.0F != 0.0F;
      if (rotated) {
         ctx.pose().pushMatrix();
         ctx.pose().translate(cx, cy);
         ctx.pose().rotate((float)Math.toRadians(rot));
         ctx.pose().translate(-cx, -cy);
      }

      int dot = this.dotSize.getInt();
      String p = this.preset.get();
      switch (p) {
         case "Punkt":
            dot(ctx, cx, cy, dot + 1, main, border, ol);
            break;
         case "Kreuz + Punkt":
            cross(ctx, cx, cy, t, len, g, true, main, border, ol);
            dot(ctx, cx, cy, dot, main, border, ol);
            break;
         case "Kreis":
            circle(ctx, cx, cy, g + len / 2 + 1, t, main, border, ol);
            break;
         case "Kreis + Punkt":
            circle(ctx, cx, cy, g + len / 2 + 1, t, main, border, ol);
            dot(ctx, cx, cy, dot, main, border, ol);
            break;
         case "T-Form":
            cross(ctx, cx, cy, t, len, g, false, main, border, ol);
            break;
         case "Pfeil":
            arrow(ctx, cx, cy, t, len, g, main, border, ol);
            break;
         case "Quadrat":
            square(ctx, cx, cy, g + len / 2 + 1, t, main, border, ol);
            break;
         case "Klammern":
            brackets(ctx, cx, cy, g + 2, len, t, main, border, ol);
            break;
         default:
            cross(ctx, cx, cy, t, len, g, true, main, border, ol);
      }

      if (this.centerDot.get() && !p.equals("Punkt") && !p.equals("Kreuz + Punkt") && !p.equals("Kreis + Punkt")) {
         dot(ctx, cx, cy, dot, main, border, ol);
      }

      if (rotated) {
         ctx.pose().popMatrix();
      }
   }

   private static int withAlpha(int argb, float alpha) {
      int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
      return a << 24 | argb & 16777215;
   }

   private static void box(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color, int border, int ol) {
      if (ol > 0) {
         ctx.fill(x1 - ol, y1 - ol, x2 + ol, y1, border);
         ctx.fill(x1 - ol, y2, x2 + ol, y2 + ol, border);
         ctx.fill(x1 - ol, y1, x1, y2, border);
         ctx.fill(x2, y1, x2 + ol, y2, border);
      }

      ctx.fill(x1, y1, x2, y2, color);
   }

   private static void cross(GuiGraphics ctx, int cx, int cy, int t, int len, int g, boolean top, int color, int border, int ol) {
      int a = t / 2;
      int b = t - a;
      box(ctx, cx + b + g, cy - a, cx + b + g + len, cy + b, color, border, ol);
      box(ctx, cx - a - g - len, cy - a, cx - a - g, cy + b, color, border, ol);
      box(ctx, cx - a, cy + b + g, cx + b, cy + b + g + len, color, border, ol);
      if (top) {
         box(ctx, cx - a, cy - a - g - len, cx + b, cy - a - g, color, border, ol);
      }
   }

   private static void dot(GuiGraphics ctx, int cx, int cy, int t, int color, int border, int ol) {
      int a = t / 2;
      int b = t - a;
      box(ctx, cx - a, cy - a, cx + b, cy + b, color, border, ol);
   }

   private static void square(GuiGraphics ctx, int cx, int cy, int r, int t, int color, int border, int ol) {
      box(ctx, cx - r, cy - r, cx + r + 1, cy - r + t, color, border, ol);
      box(ctx, cx - r, cy + r + 1 - t, cx + r + 1, cy + r + 1, color, border, ol);
      box(ctx, cx - r, cy - r + t, cx - r + t, cy + r + 1 - t, color, border, ol);
      box(ctx, cx + r + 1 - t, cy - r + t, cx + r + 1, cy + r + 1 - t, color, border, ol);
   }

   private static void brackets(GuiGraphics ctx, int cx, int cy, int r, int len, int t, int color, int border, int ol) {
      int arm = Math.max(2, len / 2 + 1);
      int x1 = cx - r - t;
      int x2 = cx + r + 1;
      box(ctx, x1, cy - arm, x1 + t, cy + arm + 1, color, border, ol);
      box(ctx, x1 + t, cy - arm, x1 + t + 2, cy - arm + t, color, border, ol);
      box(ctx, x1 + t, cy + arm + 1 - t, x1 + t + 2, cy + arm + 1, color, border, ol);
      box(ctx, x2, cy - arm, x2 + t, cy + arm + 1, color, border, ol);
      box(ctx, x2 - 2, cy - arm, x2, cy - arm + t, color, border, ol);
      box(ctx, x2 - 2, cy + arm + 1 - t, x2, cy + arm + 1, color, border, ol);
   }

   private static void circle(GuiGraphics ctx, int cx, int cy, int r, int t, int color, int border, int ol) {
      if (ol > 0) {
         ring(ctx, cx, cy, r + ol, t + 2 * ol, border);
      }

      ring(ctx, cx, cy, r, t, color);
   }

   private static void ring(GuiGraphics ctx, int cx, int cy, int r, int t, int color) {
      double outer = r + 0.5;
      double inner = Math.max(0.0, r + 0.5 - t);

      for (int dy = -r; dy <= r; dy++) {
         double yy = dy;
         int xo = (int)Math.floor(Math.sqrt(Math.max(0.0, outer * outer - yy * yy)));
         int xi = Math.abs(dy) < inner ? (int)Math.ceil(Math.sqrt(Math.max(0.0, inner * inner - yy * yy))) : 0;
         if (xi == 0) {
            ctx.fill(cx - xo, cy + dy, cx + xo + 1, cy + dy + 1, color);
         } else {
            ctx.fill(cx - xo, cy + dy, cx - xi + 1, cy + dy + 1, color);
            ctx.fill(cx + xi, cy + dy, cx + xo + 1, cy + dy + 1, color);
         }
      }
   }

   private static void arrow(GuiGraphics ctx, int cx, int cy, int t, int len, int g, int color, int border, int ol) {
      if (ol > 0) {
         for (int i = 0; i < len; i++) {
            int y = cy + g + i;
            ctx.fill(cx - i - ol, y - ol, cx - i + t + ol, y + t + ol, border);
            ctx.fill(cx + i - t + 1 - ol, y - ol, cx + i + 1 + ol, y + t + ol, border);
         }
      }

      for (int i = 0; i < len; i++) {
         int y = cy + g + i;
         ctx.fill(cx - i, y, cx - i + t, y + t, color);
         ctx.fill(cx + i - t + 1, y, cx + i + 1, y + t, color);
      }
   }

   /** Beim Ziehen an den Reglern: Menü ausblenden, das echte Crosshair ingame zeigen. */
   @Override
   public boolean hasIngamePreview() {
      return true;
   }

   @Override
   public ModulePreview preview() {
      return new Crosshair.CrosshairPreview(this, null);
   }

   public static final class CrosshairPreview implements ModulePreview {
      private final Crosshair crosshair;
      private final CrosshairAddons addons;

      public CrosshairPreview(Crosshair crosshair, CrosshairAddons addons) {
         this.crosshair = crosshair;
         this.addons = addons;
      }

      @Override
      public int preferredWidth() {
         return 190;
      }

      @Override
      public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
         Minecraft mc = Minecraft.getInstance();
         long now = System.currentTimeMillis();
         int phase = (int)(now / 2200L % 3L);
         float pulse = (float)((Math.sin(now / 300.0) + 1.0) / 2.0);
         boolean dyn = this.crosshair.dynamic.get();

         Crosshair.State state = switch (phase) {
            case 1 -> new Crosshair.State(0.0F, true, 1.0F);
            case 2 -> new Crosshair.State(
               dyn ? (4.0F + 3.0F * pulse) * (float)this.crosshair.dynamicStrength.get().doubleValue() : 0.0F, false, 0.35F + 0.65F * pulse
            );
            default -> Crosshair.State.IDLE;
         };

         String label = switch (phase) {
            case 1 -> "Ziel anvisiert";
            case 2 -> dyn ? "Laufen + Cooldown" : "Cooldown";
            default -> "Normal";
         };
         int sceneH = h - 34;
         ctx.fillGradient(x, y, x + w, y + sceneH * 2 / 3, -9459492, -4728848);
         ctx.fillGradient(x, y + sceneH * 2 / 3, x + w, y + sceneH, -10646982, -12688345);

         for (int i = 0; i < 6; i++) {
            int bx = x + 8 + i * (w / 6);
            int bh = 10 + i * 7 % 18;
            ctx.fill(bx, y + sceneH * 2 / 3 - bh, bx + w / 9, y + sceneH * 2 / 3, -9736328);
         }

         int cx = x + w / 2;
         int cy = y + sceneH / 2;
         float scale = 2.5F;
         ctx.pose().pushMatrix();
         ctx.pose().translate(cx, cy);
         ctx.pose().scale(scale, scale);
         this.crosshair.draw(ctx, 0, 0, state);
         if (this.addons != null) {
            this.addons.drawPreviewOverlay(ctx, 0, 0, state, now);
         }

         ctx.pose().popMatrix();
         int by = y + sceneH;
         ctx.fill(x, by, x + w, y + h, -16118765);
         Render2D.roundRect(ctx, x + 6, by + 6, 22, 22, 4, -10584152);
         this.crosshair.draw(ctx, x + 17, by + 17, state);
         if (this.addons != null) {
            ctx.enableScissor(x + 6, by + 6, x + 28, by + 28);
            this.addons.drawPreviewOverlay(ctx, x + 17, by + 17, state, now);
            ctx.disableScissor();
         }

         ctx.drawString(mc.font, label, x + 34, by + 7, -1578509, false);
         ctx.drawString(mc.font, "Echte Größe · Ziehen = ingame", x + 34, by + 18, -10656900, false);
      }
   }

   public record State(float spread, boolean target, float cooldown) {
      public static final Crosshair.State IDLE = new Crosshair.State(0.0F, false, 1.0F);
   }
}
