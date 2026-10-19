package com.lennart.nebula.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 26.x: Bildschirme zeichnen über extractRenderState/extractBackground. Diese Basisklasse leitet auf die gewohnten
 * Namen render/renderBackground um, damit der Code der Bildschirme in beiden Versionen gleich bleibt.
 */
public abstract class MeowlBaseScreen extends Screen {
   protected MeowlBaseScreen(Component title) {
      super(title);
   }

   @Override
   public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      // 26.x (SDL3): Buchstaben kommen nur an, wenn die Texteingabe angemeldet ist – Meowl-Fenster tun das immer
      // (mehrfacher Aufruf ist unschädlich; beim Fensterwechsel meldet Minecraft sie selbst wieder ab)
      if (this.textInput()) {
         net.minecraft.client.Minecraft.getInstance().textInputManager().startTextInput(this);
      }

      this.render(g, mouseX, mouseY, delta);
   }

   /**
    * Braucht dieser Bildschirm Texteingabe? Hauptmenü, Auswahl und Serverliste nicht – dort würde die ständig gestartete
    * Windows-Texteingabe (SDL3) Minecraft Fokus und Mausklicks wegnehmen.
    */
   protected boolean textInput() {
      return true;
   }

   public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      super.extractRenderState(g, mouseX, mouseY, delta);
   }

   @Override
   public final void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      this.renderBackground(g, mouseX, mouseY, delta);
   }

   public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      super.extractBackground(g, mouseX, mouseY, delta);
   }
}
