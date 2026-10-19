package com.lennart.nebula.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Zeichen-Umgebung für eigene Grafik in der Welt. Kapselt die je nach Minecraft-Version unterschiedliche
 * Fabric-Schnittstelle (1.21.11: WorldRenderContext, 26.x: LevelRenderContext) – der übrige Code ist dadurch gleich.
 */
public final class WorldCtx {
   private final WorldRenderContext ctx;

   private WorldCtx(WorldRenderContext ctx) {
      this.ctx = ctx;
   }

   /** Nach dem Zeichnen der Entities aufrufen lassen. */
   public static void afterEntities(Consumer<WorldCtx> callback) {
      WorldRenderEvents.AFTER_ENTITIES.register(c -> {
         WorldCtx w = new WorldCtx(c);
         callback.accept(w);
         w.flush();
      });
   }

   public PoseStack matrices() {
      return this.ctx.matrices();
   }

   public CameraRenderState camera() {
      return this.ctx.worldState().cameraRenderState;
   }

   public Vec3 cameraPos() {
      return this.camera().pos;
   }

   public Quaternionf cameraOrientation() {
      return this.camera().orientation;
   }

   /** Puffer für eine Render-Ebene (Ecken mit bereits transformierten Matrizen eintragen). */
   public VertexConsumer buffer(RenderType type) {
      return this.ctx.consumers().getBuffer(type);
   }

   /** Text in der Welt (Matrix bestimmt Lage und Größe). */
   public void text(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f matrix, boolean seeThrough, int background, int light) {
      font.drawInBatch(text, x, y, color, shadow, matrix, this.ctx.consumers(), seeThrough ? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL, background, light);
   }

   /** Gesammelte Grafik abschicken (in 1.21.11 nicht nötig). */
   public void flush() {
   }
}
