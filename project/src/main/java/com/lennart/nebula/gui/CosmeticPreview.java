package com.lennart.nebula.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Vorschau für Kosmetik in den Einstellungen: dein Charakter dreht sich langsam (Maus darüber = selbst drehen) und
 * trägt die Kosmetik – auch wenn das Modul gerade aus ist.
 */
public final class CosmeticPreview implements ModulePreview {
   private final Runnable mark;
   private final Object target;
   private float yaw = 180.0F;
   private long last = System.nanoTime();
   /** Angehalten gilt für alle Vorschauen, bis man wieder auf Abspielen drückt. */
   private static boolean paused = true;
   private int bx;
   private int by;

   public static boolean isPaused() {
      return paused;
   }

   /** @param mark wird jedes Bild aufgerufen und schaltet die Vorschau der Kosmetik kurz frei */
   public CosmeticPreview(Runnable mark) {
      this(mark, null);
   }

   /** @param target Vorschau-Ziel (z.B. {@link com.lennart.nebula.cosmetic.PreviewOverrides#PET}), null = normale Kosmetik */
   public CosmeticPreview(Runnable mark, Object target) {
      this.mark = mark;
      this.target = target;
   }

   @Override
   public int preferredWidth() {
      return 170;
   }

   @Override
   public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
      this.mark.run();
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - this.last) / 1.0E9F);
      this.last = now;
      boolean hover = Render2D.inside(mouseX, mouseY, x, y, w, h);
      // Knopf oben rechts: Drehen anhalten / weiter
      this.bx = x + w - 22;
      this.by = y + 6;
      boolean overButton = Render2D.inside(mouseX, mouseY, this.bx, this.by, 16, 16);
      if (hover && !overButton) {
         // Maus nach rechts = Figur dreht sich nach rechts; Mitte = von vorne
         float target = 180.0F - 360.0F * ((mouseX - x) / (float)Math.max(1, w) - 0.5F);
         this.yaw += (target - this.yaw) * Math.min(1.0F, dt * 8.0F);
      } else if (!paused) {
         // „Automatisch drehen“ an
         this.yaw = (this.yaw + dt * 35.0F) % 360.0F;
      }

      Render2D.roundRect(ctx, x, y, w, h, 8, 0xC0101420);
      ctx.fillGradient(x + 4, y + 4, x + w - 4, y + h - 4, 0x40306090, 0x10101420);
      int floorY = y + h - 26;
      Render2D.roundRect(ctx, x + w / 2 - 34, floorY - 2, 68, 4, 2, 0x50000000);
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         ctx.drawCenteredString(Minecraft.getInstance().font, "Vorschau in einer Welt", x + w / 2, y + h / 2, -7564628);
      } else {
         int size = Math.max(16, Math.min(w / 3, (int)((h - 50) / 2.6F)));
         this.drawPlayer(ctx, player, x + w / 2, floorY, size, x, y, w, h);
      }

      Render2D.roundRect(ctx, this.bx, this.by, 16, 16, 4, overButton ? 0xE08B5CF6 : 0xC0262E46);
      if (paused) {
         // Abspielen-Dreieck
         for (int i = 0; i < 7; i++) {
            ctx.fill(this.bx + 6 + i / 2, this.by + 4 + i / 2, this.bx + 7 + i / 2, this.by + 12 - i / 2, -1);
         }
      } else {
         ctx.fill(this.bx + 5, this.by + 4, this.bx + 7, this.by + 12, -1);
         ctx.fill(this.bx + 9, this.by + 4, this.bx + 11, this.by + 12, -1);
      }

      String hint = overButton ? (paused ? "Automatisch drehen" : "Automatisch drehen aus") : (hover ? "Maus bewegen = drehen" : "Maus drüber = drehen");
      ctx.drawCenteredString(Minecraft.getInstance().font, hint, x + w / 2, y + h - 14, -10656900);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mouseX, mouseY, this.bx, this.by, 16, 16)) {
         paused = !paused;
         return true;
      } else {
         return false;
      }
   }

   private void drawPlayer(GuiGraphics ctx, LocalPlayer player, int px, int feet, int size, int bx, int by, int bw, int bh) {
      try {
         EntityRenderDispatcher manager = Minecraft.getInstance().getEntityRenderDispatcher();
         EntityRenderer<? super LocalPlayer, ?> renderer = manager.getRenderer(player);
         EntityRenderState state = renderer.createRenderState(player, 1.0F);
         state.lightCoords = 15728880;
         state.shadowPieces.clear();
         state.outlineColor = 0;
         state.nameTag = null;
         if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = this.yaw;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.walkAnimationSpeed = 0.0F;
            living.walkAnimationPos = 0.0F;
            living.hasRedOverlay = false;
            living.deathTime = 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
         }

         if (this.target != null) {
            com.lennart.nebula.cosmetic.PreviewOverrides.put(state, this.target, null);
         }

         int x1 = bx + 2;
         int x2 = bx + bw - 2;
         int y1 = by + 2;
         int y2 = by + bh - 2;
         float cy = (y1 + y2) / 2.0F;
         Quaternionf camera = new Quaternionf().rotateX(-0.18F);
         Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).mul(camera);
         ctx.enableScissor(x1, y1, x2, y2);
         ctx.submitEntityRenderState(state, size, new Vector3f(0.0F, (feet - cy) / size, 0.0F), rotation, camera, x1, y1, x2, y2);
         ctx.disableScissor();
      } catch (RuntimeException var17) {
      }
   }
}
