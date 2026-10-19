package com.lennart.nebula.util;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Färbt alle Vertex-Farben eines anderen Consumers um (z.B. eigene Farbe für Uhren). */
public final class RecolorConsumer implements VertexConsumer {
   private final VertexConsumer inner;
   private final int target;

   public RecolorConsumer(VertexConsumer inner, int target) {
      this.inner = inner;
      this.target = target;
   }

   public static VertexConsumer wrap(VertexConsumer vc, int target) {
      return target == 0 ? vc : new RecolorConsumer(vc, target);
   }

   @Override
   public VertexConsumer addVertex(float x, float y, float z) {
      this.inner.addVertex(x, y, z);
      return this;
   }

   @Override
   public VertexConsumer setColor(int r, int g, int b, int a) {
      int c = com.lennart.nebula.cosmetic.CosmeticValue.recolor(a << 24 | r << 16 | g << 8 | b, this.target);
      this.inner.setColor(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, a);
      return this;
   }

   @Override
   public VertexConsumer setColor(int argb) {
      this.inner.setColor(com.lennart.nebula.cosmetic.CosmeticValue.recolor(argb, this.target));
      return this;
   }

   @Override
   public VertexConsumer setUv(float u, float v) {
      this.inner.setUv(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv1(int u, int v) {
      this.inner.setUv1(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv2(int u, int v) {
      this.inner.setUv2(u, v);
      return this;
   }

   @Override
   public VertexConsumer setNormal(float x, float y, float z) {
      this.inner.setNormal(x, y, z);
      return this;
   }

   @Override
   public VertexConsumer setLineWidth(float w) {
      this.inner.setLineWidth(w);
      return this;
   }
}