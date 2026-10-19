package com.lennart.nebula.gui.widget;

import com.lennart.nebula.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class NotesInventoryButton extends AbstractWidget {
   private static final int SIZE = 14;
   private final AbstractContainerScreen<?> screen;
   private final Runnable onClick;

   public NotesInventoryButton(AbstractContainerScreen<?> screen, Runnable onClick) {
      super(0, 0, 14, 14, Component.literal("Notizen"));
      this.screen = screen;
      this.onClick = onClick;
      this.setTooltip(Tooltip.create(Component.literal("Notizen")));
      this.updatePosition();
   }

   private void updatePosition() {
      HandledScreenAccessor a = (HandledScreenAccessor)this.screen;
      this.setX(a.meowl$getX() + a.meowl$getBackgroundWidth() + 2);
      this.setY(a.meowl$getY() + 4);
   }

   @Override
   protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      this.updatePosition();
      int x = this.getX();
      int y = this.getY();
      boolean hover = this.isHovered();
      context.fill(x, y, x + 14, y + 14, -16777216);
      context.fill(x + 1, y + 1, x + 14 - 1, y + 14 - 1, hover ? -2434342 : -3750202);
      context.fill(x + 1, y + 1, x + 14 - 2, y + 2, -1);
      context.fill(x + 1, y + 1, x + 2, y + 14 - 2, -1);
      context.fill(x + 2, y + 14 - 2, x + 14 - 1, y + 14 - 1, -11184811);
      context.fill(x + 14 - 2, y + 2, x + 14 - 1, y + 14 - 1, -11184811);
      context.fill(x + 4, y + 3, x + 10, y + 11, -659236);
      context.fill(x + 4, y + 3, x + 10, y + 4, -7643914);
      context.fill(x + 5, y + 6, x + 9, y + 7, -7566196);
      context.fill(x + 5, y + 8, x + 9, y + 9, -7566196);
   }

   @Override
   public void onClick(MouseButtonEvent click, boolean doubled) {
      Minecraft.getInstance().execute(this.onClick);
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput builder) {
      this.defaultButtonNarrationText(builder);
   }
}
