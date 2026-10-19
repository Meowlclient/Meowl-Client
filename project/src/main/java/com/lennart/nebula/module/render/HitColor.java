package com.lennart.nebula.module.render;

import com.lennart.nebula.mixin.OverlayTextureAccessor;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.Items;

/**
 * Hitcolor: Farbe und Stärke des Aufblitzens, wenn ein Spieler oder Mob getroffen wird (statt dem roten Vanilla-
 * Aufblitzen). Minecraft nimmt die Farbe aus einer kleinen Textur – die wird hier neu eingefärbt.
 */
public class HitColor extends Module {
   /** Vanilla: Rot, 30 % Anteil (Alpha = 70 % Originalfarbe). */
   private static final int VANILLA = 0xB2FF0000;
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe beim Treffer", 0xFF8B5CF6, false));
   private final NumberSetting strength = this.add(new NumberSetting("Stärke", "Wie kräftig die Farbe über dem Getroffenen liegt (Vanilla ≈ 30 %)", 45.0, 5.0, 100.0, 5.0, "%"));
   private final BooleanSetting rainbow = this.add(new BooleanSetting("Regenbogen", "Farbe wechselt fließend durch alle Farben", false));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Tempo", "Wie schnell der Regenbogen wechselt", 1.0, 0.2, 4.0, 0.1, "x"));
   private int applied = Integer.MIN_VALUE;

   public HitColor() {
      super("Hitcolor", "Eigene Farbe für das Aufblitzen beim Treffen (statt Rot) – mit Stärke und Regenbogen.", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.REDSTONE);
      this.rainbowSpeed.setVisibility(this.rainbow::get);
      this.color.setVisibility(() -> !this.rainbow.get());
      this.group("Farbe", new Setting[]{this.color, this.strength, this.rainbow, this.rainbowSpeed});
   }

   private int wanted() {
      int rgb;
      if (this.rainbow.get()) {
         float hue = (float)(System.currentTimeMillis() / 1000.0 * 0.25 * this.rainbowSpeed.get() % 1.0);
         rgb = java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F) & 16777215;
      } else {
         rgb = this.color.get() & 16777215;
      }

      // im Shader ist Alpha der Anteil der ORIGINAL-Farbe: mehr Stärke = weniger Alpha
      int alpha = (int)Math.round((1.0 - this.strength.get() / 100.0) * 255.0);
      return alpha << 24 | rgb;
   }

   @Override
   public void onTick() {
      this.apply(this.wanted());
   }

   @Override
   public void onRenderHud(net.minecraft.client.gui.GuiGraphicsExtractor ctx, float tickDelta) {
      // Regenbogen flüssig auch zwischen den Ticks
      if (this.rainbow.get()) {
         this.apply(this.wanted());
      }
   }

   @Override
   protected void onDisable() {
      this.apply(VANILLA);
      this.applied = Integer.MIN_VALUE;
   }

   private void apply(int argb) {
      if (argb != this.applied && this.mc.gameRenderer != null) {
         DynamicTexture tex = ((OverlayTextureAccessor)this.mc.gameRenderer.overlayTexture()).meowl$texture();
         NativeImage img = tex.getPixels();
         if (img != null) {
            // obere Hälfte der 16x16-Textur = Treffer-Farbe
            for (int y = 0; y < 8; y++) {
               for (int x = 0; x < 16; x++) {
                  img.setPixel(x, y, argb);
               }
            }

            tex.upload();
            this.applied = argb;
         }
      }
   }
}
