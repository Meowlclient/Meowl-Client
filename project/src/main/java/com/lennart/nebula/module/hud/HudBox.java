package com.lennart.nebula.module.hud;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Ein verschiebbares HUD-Element. Die Position hängt an der nächsten Bildschirmkante (links/Mitte/rechts und
 * oben/Mitte/unten) mit einem Abstand in GUI-Pixeln – so bleibt ein Element im Vollbild und im Fenster an derselben
 * Stelle (z.B. direkt neben der Hotbar). Breite und Höhe lassen sich zusätzlich unabhängig strecken.
 */
public final class HudBox {
   private static final int LEGACY = -1;
   public final NumberSetting x;
   public final NumberSetting y;
   public final NumberSetting scale;
   public final NumberSetting stretchX;
   public final NumberSetting stretchY;
   public final NumberSetting opacity;
   public final BooleanSetting background;
   public final ColorSetting backgroundColor;
   public final NumberSetting rounding;
   /** Nur die Anzeige im HUD an/aus (HUD-Editor) – das Modul selbst läuft weiter. */
   public final BooleanSetting shown;
   private final NumberSetting anchorX;
   private final NumberSetting anchorY;
   private final NumberSetting offX;
   private final NumberSetting offY;
   private final String label;
   private float lastX;
   private float lastY;
   private float lastW;
   private float lastH;
   private int lastBaseW;
   private int lastBaseH;
   private long lastDrawn;
   private boolean lastAnchored;
   private Runnable detachAction = () -> {};
   /** Breite ändert den Inhalt (z.B. Textbreite) statt ihn zu strecken. */
   private boolean reflow;
   private int naturalW = 1;

   public HudBox(HudBox.Adder adder, String label, double defX, double defY) {
      this(adder, label, defX, defY, UnaryOperator.identity());
   }

   public HudBox(HudBox.Adder adder, String label, double defX, double defY, UnaryOperator<String> name) {
      this.label = label;
      this.x = adder.add(
         new NumberSetting(name.apply("Position X"), "Horizontale Position (0 = links, 100 = rechts) – oder im HUD-Editor ziehen", defX, 0.0, 100.0, 0.1, "%")
      );
      this.y = adder.add(
         new NumberSetting(name.apply("Position Y"), "Vertikale Position (0 = oben, 100 = unten) – oder im HUD-Editor ziehen", defY, 0.0, 100.0, 0.1, "%")
      );
      this.scale = adder.add(new NumberSetting(name.apply("Größe"), "Skalierung (im HUD-Editor: Mausrad)", 1.0, 0.5, 3.0, 0.05, "x"));
      this.stretchX = adder.add(
         new NumberSetting(name.apply("HUD-Breite"), "Breite strecken oder stauchen (im HUD-Editor: rechten Rand ziehen)", 1.0, 0.3, 4.0, 0.01, "x")
      );
      this.stretchY = adder.add(
         new NumberSetting(name.apply("HUD-Höhe"), "Höhe strecken oder stauchen (im HUD-Editor: unteren Rand ziehen)", 1.0, 0.3, 4.0, 0.01, "x")
      );
      this.opacity = adder.add(new NumberSetting(name.apply("Transparenz"), "Deckkraft (0 % = unsichtbar)", 100.0, 0.0, 100.0, 5.0, "%"));
      this.background = adder.add(new BooleanSetting(name.apply("Hintergrund"), "Dunkler Kasten hinter der Anzeige", true));
      this.backgroundColor = adder.add(new ColorSetting(name.apply("Hintergrund-Farbe"), "Farbe des Kastens", -1877863392));
      this.backgroundColor.setVisibility(this.background::get);
      this.rounding = adder.add(new NumberSetting(name.apply("Ecken-Rundung"), "0 = eckig", 3.0, 0.0, 8.0, 1.0, "px"));
      this.rounding.setVisibility(this.background::get);
      this.shown = adder.add(new BooleanSetting(name.apply("HUD sichtbar"), "Nur die Anzeige ein-/ausblenden – das Modul bleibt an", true));
      this.shown.setVisibility(() -> false);
      this.anchorX = adder.add(new NumberSetting(name.apply("HUD-Anker X"), "", LEGACY, LEGACY, 2.0, 1.0, ""));
      this.anchorY = adder.add(new NumberSetting(name.apply("HUD-Anker Y"), "", LEGACY, LEGACY, 2.0, 1.0, ""));
      this.offX = adder.add(new NumberSetting(name.apply("HUD-Abstand X"), "", 0.0, -8000.0, 8000.0, 0.5, "px"));
      this.offY = adder.add(new NumberSetting(name.apply("HUD-Abstand Y"), "", 0.0, -8000.0, 8000.0, 0.5, "px"));
      this.x.setVisibility(() -> false);
      this.y.setVisibility(() -> false);
      this.stretchX.setVisibility(() -> false);
      this.stretchY.setVisibility(() -> false);
      this.anchorX.setVisibility(() -> false);
      this.anchorY.setVisibility(() -> false);
      this.offX.setVisibility(() -> false);
      this.offY.setVisibility(() -> false);
   }

   public String label() {
      return this.label;
   }

   public float alpha() {
      return (float)(this.opacity.get() / 100.0);
   }

   public int fade(int argb) {
      int a = Math.round((argb >>> 24 & 0xFF) * this.alpha());
      return a << 24 | argb & 16777215;
   }

   private float sx() {
      return (float)(this.reflow ? this.scale.get() : this.scale.get() * this.stretchX.get());
   }

   private float sy() {
      return (float)(this.reflow ? this.scale.get() : this.scale.get() * this.stretchY.get());
   }

   /** Für Text-Elemente: die eingestellte Breite verändert den Platz für den Inhalt, Text bleibt unverzerrt. */
   public HudBox reflow() {
      this.reflow = true;
      return this;
   }

   public boolean isReflow() {
      return this.reflow;
   }

   /** Breite für den Inhalt: natürliche Breite mal eingestellter Breiten-Faktor (nur bei {@link #reflow()}). */
   public int layoutWidth(int natural) {
      this.naturalW = Math.max(1, natural);
      return this.reflow ? Math.max(24, Math.round(natural * (float)this.stretchX.get().doubleValue())) : natural;
   }

   public void begin(GuiGraphics ctx, int w, int h) {
      this.begin(ctx, w, h, this.background.get());
   }

   public void begin(GuiGraphics ctx, int w, int h, boolean drawBackground) {
      float sx = this.sx();
      float sy = this.sy();
      int sw = ctx.guiWidth();
      int sh = ctx.guiHeight();
      float W = w * sx;
      float H = h * sy;
      float px;
      float py;
      if (this.anchorX.getInt() == LEGACY || this.anchorY.getInt() == LEGACY) {
         // alte Prozent-Angabe einmalig in Kante + Abstand umrechnen
         px = (float)(this.x.get() / 100.0) * Math.max(0.0F, sw - W);
         py = (float)(this.y.get() / 100.0) * Math.max(0.0F, sh - H);
         this.lastBaseW = w;
         this.lastBaseH = h;
         this.store(px, py, W, H, sw, sh);
      }

      px = this.place(this.anchorX.getInt(), (float)this.offX.get().doubleValue(), W, sw);
      py = this.place(this.anchorY.getInt(), (float)this.offY.get().doubleValue(), H, sh);
      this.remember(px, py, w, h, sx, sy, false);
      ctx.pose().pushMatrix();
      ctx.pose().translate(px, py);
      ctx.pose().scale(sx, sy);
      if (this.hiddenNow()) {
         // im HUD ausgeblendet: nichts sichtbar zeichnen (Modul läuft weiter)
         ctx.pose().scale(0.0F, 0.0F);
         return;
      }

      if (drawBackground) {
         this.drawBackground(ctx, w, h);
      }
   }

   /** Linke/obere Kante aus Anker und Abstand, immer ganz auf dem Bildschirm. */
   private float place(int anchor, float off, float size, int screen) {
      float p = switch (anchor) {
         case 1 -> screen / 2.0F + off - size / 2.0F;
         case 2 -> screen - size - off;
         default -> off;
      };
      return Math.max(0.0F, Math.min(Math.max(0.0F, screen - size), p));
   }

   /** Position als Kante + Abstand speichern (das nächstgelegene Drittel des Bildschirms bestimmt die Kante). */
   private void store(float px, float py, float W, float H, int sw, int sh) {
      float cx = px + W / 2.0F;
      float cy = py + H / 2.0F;
      int ax = cx < sw / 3.0F ? 0 : (cx > sw * 2.0F / 3.0F ? 2 : 1);
      int ay = cy < sh / 3.0F ? 0 : (cy > sh * 2.0F / 3.0F ? 2 : 1);
      this.anchorX.set((double)ax);
      this.anchorY.set((double)ay);
      this.offX.set((double)(ax == 0 ? px : (ax == 2 ? sw - W - px : cx - sw / 2.0F)));
      this.offY.set((double)(ay == 0 ? py : (ay == 2 ? sh - H - py : cy - sh / 2.0F)));
      // Prozent-Werte weiter pflegen (Rückfall, falls die Kante fehlt)
      this.x.set(Math.max(0.0, Math.min(100.0, px / Math.max(1.0F, sw - W) * 100.0)));
      this.y.set(Math.max(0.0, Math.min(100.0, py / Math.max(1.0F, sh - H) * 100.0)));
   }

   public void beginAt(GuiGraphics ctx, int px, int py, int w, int h, boolean drawBackground) {
      this.remember(px, py, w, h, 1.0F, 1.0F, true);
      ctx.pose().pushMatrix();
      ctx.pose().translate(px, py);
      if (this.hiddenNow()) {
         ctx.pose().scale(0.0F, 0.0F);
         return;
      }

      if (drawBackground) {
         this.drawBackground(ctx, w, h);
      }
   }

   public void end(GuiGraphics ctx) {
      ctx.pose().popMatrix();
   }

   private void remember(float px, float py, int w, int h, float sx, float sy, boolean anchored) {
      this.lastX = px;
      this.lastY = py;
      this.lastW = w * sx;
      this.lastH = h * sy;
      this.lastBaseW = w;
      this.lastBaseH = h;
      this.lastAnchored = anchored;
      this.lastDrawn = System.currentTimeMillis();
   }

   private void drawBackground(GuiGraphics ctx, int w, int h) {
      int r = this.rounding.getInt();
      if (r <= 0) {
         ctx.fill(0, 0, w, h, this.fade(this.backgroundColor.get()));
      } else {
         Render2D.roundRect(ctx, 0, 0, w, h, Math.min(r, Math.min(w, h) / 2), this.fade(this.backgroundColor.get()));
      }
   }

   /** Ausgeblendet und nicht im HUD-Editor (dort sieht man alles, um es wieder einzuschalten). */
   public boolean hiddenNow() {
      return !this.shown.get() && !HudLayout.isEditing();
   }

   public boolean isVisibleNow() {
      return System.currentTimeMillis() - this.lastDrawn < 250L;
   }

   public void setDetachAction(Runnable action) {
      this.detachAction = action == null ? () -> {} : action;
   }

   public void detach() {
      if (this.lastAnchored) {
         this.detachAction.run();
         this.lastAnchored = false;
      }
   }

   public boolean isAnchored() {
      return this.lastAnchored;
   }

   public float screenX() {
      return this.lastX;
   }

   public float screenY() {
      return this.lastY;
   }

   public float screenW() {
      return this.lastW;
   }

   public float screenH() {
      return this.lastH;
   }

   public void moveTo(float px, float py, int screenW, int screenH) {
      float W = this.lastBaseW * this.sx();
      float H = this.lastBaseH * this.sy();
      px = Math.max(0.0F, Math.min(Math.max(0.0F, screenW - W), px));
      py = Math.max(0.0F, Math.min(Math.max(0.0F, screenH - H), py));
      this.store(px, py, W, H, screenW, screenH);
      this.lastX = px;
      this.lastY = py;
      this.lastW = W;
      this.lastH = H;
   }

   public void scaleAroundCenter(double newScale, int screenW, int screenH) {
      float cx = this.lastX + this.lastW / 2.0F;
      float cy = this.lastY + this.lastH / 2.0F;
      this.scale.set(newScale);
      this.lastW = this.lastBaseW * this.sx();
      this.lastH = this.lastBaseH * this.sy();
      this.moveTo(cx - this.lastW / 2.0F, cy - this.lastH / 2.0F, screenW, screenH);
   }

   /** Breite/Höhe auf dem Bildschirm einstellen (linke obere Ecke bleibt stehen); ≤ 0 = unverändert. */
   public void resizeTo(float width, float height, int screenW, int screenH) {
      float s = (float)this.scale.get().doubleValue();
      if (this.reflow) {
         if (width > 0.0F) {
            this.stretchX.set((double)(width / (this.naturalW * s)));
         }

         this.moveTo(this.lastX, this.lastY, screenW, screenH);
         return;
      }

      if (width > 0.0F && this.lastBaseW > 0) {
         this.stretchX.set((double)(width / (this.lastBaseW * s)));
      }

      if (height > 0.0F && this.lastBaseH > 0) {
         this.stretchY.set((double)(height / (this.lastBaseH * s)));
      }

      this.moveTo(this.lastX, this.lastY, screenW, screenH);
   }

   public boolean stretched() {
      return this.stretchX.get() != 1.0 || this.stretchY.get() != 1.0;
   }

   public void resetStretch(int screenW, int screenH) {
      this.stretchX.reset();
      this.stretchY.reset();
      this.moveTo(this.lastX, this.lastY, screenW, screenH);
   }

   public void resetLayout() {
      this.x.reset();
      this.y.reset();
      this.anchorX.reset();
      this.anchorY.reset();
      this.offX.reset();
      this.offY.reset();
      this.scale.reset();
      this.stretchX.reset();
      this.stretchY.reset();
      this.opacity.reset();
   }

   @FunctionalInterface
   public interface Adder {
      <S extends Setting<?>> S add(S var1);
   }
}
