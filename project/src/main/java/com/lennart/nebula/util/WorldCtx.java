package com.lennart.nebula.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 26.x-Fassung: Grafik wird nicht mehr direkt in Puffer geschrieben, sondern beim Sammeln der Zeichenaufträge
 * eingereicht. Die Ecken werden deshalb zuerst aufgezeichnet und am Ende als eigene Geometrie abgeschickt.
 */
public final class WorldCtx {
   private final LevelRenderContext ctx;
   private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

   private WorldCtx(LevelRenderContext ctx) {
      this.ctx = ctx;
   }

   public static void afterEntities(Consumer<WorldCtx> callback) {
      LevelRenderEvents.COLLECT_SUBMITS.register(c -> {
         WorldCtx w = new WorldCtx(c);
         callback.accept(w);
         w.flush();
      });
   }

   public PoseStack matrices() {
      return this.ctx.poseStack();
   }

   public CameraRenderState camera() {
      return this.ctx.levelState().cameraRenderState;
   }

   public Vec3 cameraPos() {
      return this.camera().pos;
   }

   public Quaternionf cameraOrientation() {
      return this.camera().orientation;
   }

   public VertexConsumer buffer(RenderType type) {
      return this.recorders.computeIfAbsent(type, t -> new Recorder());
   }

   public void text(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f matrix, boolean seeThrough, int background, int light) {
      PoseStack ps = new PoseStack();
      ps.last().pose().set(matrix);
      this.ctx.submitNodeCollector()
         .submitText(ps, x, y, Component.literal(text).getVisualOrderText(), shadow, seeThrough ? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL, light, color, background, 0);
   }

   public void flush() {
      for (Map.Entry<RenderType, Recorder> e : this.recorders.entrySet()) {
         Recorder r = e.getValue();
         if (!r.vertices.isEmpty()) {
            this.ctx.submitNodeCollector().submitCustomGeometry(new PoseStack(), e.getKey(), (pose, vc) -> r.replay(vc));
         }
      }

      this.recorders.clear();
   }

   /** Eine aufgezeichnete Ecke. */
   private static final class Vert {
      float x;
      float y;
      float z;
      int color;
      boolean hasColor;
      float u;
      float v;
      boolean hasUv;
      int overlay;
      boolean hasOverlay;
      int light;
      boolean hasLight;
      float nx;
      float ny;
      float nz;
      boolean hasNormal;
      float width;
      boolean hasWidth;
   }

   /** Zeichnet Ecken auf (bereits mit der Matrix des Aufrufers transformiert). */
   private static final class Recorder implements VertexConsumer {
      final List<Vert> vertices = new ArrayList<>();
      private Vert cur = new Vert();

      @Override
      public VertexConsumer addVertex(float x, float y, float z) {
         this.cur = new Vert();
         this.cur.x = x;
         this.cur.y = y;
         this.cur.z = z;
         this.vertices.add(this.cur);
         return this;
      }

      @Override
      public VertexConsumer setColor(int r, int g, int b, int a) {
         return this.setColor(a << 24 | r << 16 | g << 8 | b);
      }

      @Override
      public VertexConsumer setColor(int argb) {
         this.cur.color = argb;
         this.cur.hasColor = true;
         return this;
      }

      @Override
      public VertexConsumer setUv(float u, float v) {
         this.cur.u = u;
         this.cur.v = v;
         this.cur.hasUv = true;
         return this;
      }

      @Override
      public VertexConsumer setUv1(int u, int v) {
         this.cur.overlay = u << 16 | v & 0xFFFF;
         this.cur.hasOverlay = true;
         return this;
      }

      @Override
      public VertexConsumer setUv2(int u, int v) {
         this.cur.light = u << 16 | v & 0xFFFF;
         this.cur.hasLight = true;
         return this;
      }

      @Override
      public VertexConsumer setUv3(float u, float v) {
         return this;
      }

      @Override
      public VertexConsumer setNormal(float x, float y, float z) {
         this.cur.nx = x;
         this.cur.ny = y;
         this.cur.nz = z;
         this.cur.hasNormal = true;
         return this;
      }

      @Override
      public VertexConsumer setLineWidth(float w) {
         this.cur.width = w;
         this.cur.hasWidth = true;
         return this;
      }

      void replay(VertexConsumer vc) {
         for (Vert v : this.vertices) {
            vc.addVertex(v.x, v.y, v.z);
            if (v.hasColor) {
               vc.setColor(v.color);
            }

            if (v.hasUv) {
               vc.setUv(v.u, v.v);
            }

            if (v.hasOverlay) {
               vc.setUv1(v.overlay >>> 16, v.overlay & 0xFFFF);
            }

            if (v.hasLight) {
               vc.setUv2(v.light >>> 16, v.light & 0xFFFF);
            }

            if (v.hasNormal) {
               vc.setNormal(v.nx, v.ny, v.nz);
            }

            if (v.hasWidth) {
               vc.setLineWidth(v.width);
            }
         }
      }
   }
}