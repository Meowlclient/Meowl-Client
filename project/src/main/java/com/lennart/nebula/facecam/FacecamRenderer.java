package com.lennart.nebula.facecam;

import com.lennart.nebula.util.DynamicTextures;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class FacecamRenderer {
   public static final String FACE = "Vor dem Gesicht";
   public static final String ABOVE = "Über dem Kopf";
   public static final String SIDE = "Neben dem Kopf";
   private final Map<UUID, FacecamRenderer.Frame> pending = new ConcurrentHashMap<>();
   private final Map<UUID, FacecamRenderer.Slot> slots = new HashMap<>();

   public void submit(UUID player, int[] pixels, int w, int h) {
      this.pending.put(player, new FacecamRenderer.Frame(pixels, w, h));
   }

   public void tick(Predicate<UUID> live) {
      Iterator<Entry<UUID, FacecamRenderer.Frame>> it = this.pending.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, FacecamRenderer.Frame> e = it.next();
         it.remove();
         FacecamRenderer.Slot slot = this.slots.computeIfAbsent(e.getKey(), FacecamRenderer.Slot::new);
         FacecamRenderer.Frame f = e.getValue();
         slot.texture = DynamicTextures.upload(slot.id, slot.texture, f.pixels(), f.width(), f.height());
         slot.width = f.width();
         slot.height = f.height();
         slot.lastFrame = System.currentTimeMillis();
      }

      long now = System.currentTimeMillis();
      this.slots.entrySet().removeIf(ex -> {
         if (now - ex.getValue().lastFrame > (live.test(ex.getKey()) ? 15000 : 3000)) {
            DynamicTextures.destroy(ex.getValue().id);
            return true;
         } else {
            return false;
         }
      });
   }

   public boolean has(UUID player) {
      FacecamRenderer.Slot s = this.slots.get(player);
      return s != null && s.texture != null;
   }

   public Identifier textureOf(UUID player) {
      FacecamRenderer.Slot s = this.slots.get(player);
      return s == null ? null : s.id;
   }

   public int[] sizeOf(UUID player) {
      FacecamRenderer.Slot s = this.slots.get(player);
      return s == null ? null : new int[]{s.width, s.height};
   }

   public void remove(UUID player) {
      this.pending.remove(player);
      FacecamRenderer.Slot s = this.slots.remove(player);
      if (s != null) {
         DynamicTextures.destroy(s.id);
      }
   }

   public void clear() {
      this.pending.clear();

      for (FacecamRenderer.Slot s : this.slots.values()) {
         DynamicTextures.destroy(s.id);
      }

      this.slots.clear();
   }

   public void render(
      WorldCtx ctx,
      Player player,
      float tickDelta,
      String position,
      double size,
      boolean frame,
      int frameColor,
      double distance,
      double maxPitch
   ) {
      FacecamRenderer.Slot slot = this.slots.get(player.getUUID());
      if (slot != null && slot.texture != null && slot.width != 0) {
         CameraRenderState camera = ctx.camera();
         Vec3 cam = camera.pos;
         Vec3 eye = player.getEyePosition(tickDelta);
         float yaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
         float pitch = player.getViewXRot(tickDelta);
         float w = (float)size;
         float h = w * slot.height / slot.width;
         PoseStack m = ctx.matrices();
         m.pushPose();
         switch (position) {
            case "Über dem Kopf":
               m.translate(eye.x - cam.x, eye.y + 0.75 + h / 2.0F + (player.getBbHeight() - 1.62) - cam.y, eye.z - cam.z);
               m.rotate(camera.orientation);
               break;
            case "Neben dem Kopf":
               m.translate(eye.x - cam.x, eye.y - cam.y, eye.z - cam.z);
               m.rotate(Axis.YP.rotationDegrees(-yaw));
               m.translate(-0.45F - w / 2.0F, 0.1F, 0.05F);
               break;
            default:
               m.translate(eye.x - cam.x, eye.y - cam.y, eye.z - cam.z);
               m.rotate(Axis.YP.rotationDegrees(-yaw));
               float p = Mth.clamp(pitch, (float)(-maxPitch), (float)maxPitch);
               m.rotate(Axis.XP.rotationDegrees(p));
               m.translate(0.0F, 0.0F, (float)distance);
         }

         if (frame) {
            float b = 0.03F;
            VertexConsumer vc = ctx.buffer(WorldRender.translucentColor());
            Matrix4f mat = m.last().pose();
            quadColor(vc, mat, -w / 2.0F - b, -h / 2.0F - b, w / 2.0F + b, h / 2.0F + b, -0.002F, frameColor);
         }

         VertexConsumer vc = ctx.buffer(RenderTypes.text(slot.id));
         Matrix4f mat = m.last().pose();
         int light = 15728880;
         vc.addVertex(mat, -w / 2.0F, -h / 2.0F, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(light);
         vc.addVertex(mat, w / 2.0F, -h / 2.0F, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(light);
         vc.addVertex(mat, w / 2.0F, h / 2.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(light);
         vc.addVertex(mat, -w / 2.0F, h / 2.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(light);
         vc.addVertex(mat, -w / 2.0F, h / 2.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(light);
         vc.addVertex(mat, w / 2.0F, h / 2.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(light);
         vc.addVertex(mat, w / 2.0F, -h / 2.0F, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(light);
         vc.addVertex(mat, -w / 2.0F, -h / 2.0F, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(light);
         m.popPose();
      }
   }

   private static void quadColor(VertexConsumer vc, Matrix4f mat, float x1, float y1, float x2, float y2, float z, int color) {
      vc.addVertex(mat, x1, y1, z).setColor(color);
      vc.addVertex(mat, x2, y1, z).setColor(color);
      vc.addVertex(mat, x2, y2, z).setColor(color);
      vc.addVertex(mat, x1, y2, z).setColor(color);
   }

   record Frame(int[] pixels, int width, int height) {
   }

   private static final class Slot {
      final Identifier id;
      DynamicTexture texture;
      int width;
      int height;
      long lastFrame;

      Slot(UUID uuid) {
         this.id = Identifier.fromNamespaceAndPath("nebula", "facecam/" + uuid.toString().replace("-", ""));
      }
   }
}
