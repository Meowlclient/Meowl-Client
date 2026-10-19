package com.lennart.nebula.module.mods.pet;

import com.lennart.nebula.module.mods.MeowlPet;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MeowlPetEntity {
   public static final String DANCE_ALL = "Alles";
   public static final String DANCE_HOP = "Hüpfen";
   public static final String DANCE_SWAY = "Wackeln";
   public static final String DANCE_SPIN = "Drehen";
   public static final String DANCE_OFF = "Aus";
   private static final MeowlPetEntity.Skin SKIN_PURPLE = new MeowlPetEntity.Skin(
      -7703880, -9742698, -1845262, -877640, -1, -15396833, -673470, -1537748, 0, false
   );
   private static final MeowlPetEntity.Skin SKIN_CAT = new MeowlPetEntity.Skin(
      -6518158, -9544116, -724500, -1530202, -2502720, -15593972, -2519924, -724500, -11912402, true
   );
   public static final String SKIN_MODE_PURPLE = "Meowl (lila)";
   public static final String SKIN_MODE_CAT = "Katze";
   private static final float MODEL_HEIGHT = 19.0F;
   private final Random random = new Random();
   private ClientLevel world;
   private Vec3 pos;
   private Vec3 prevPos;
   private Vec3 target;
   private float yaw;
   private float prevYaw;
   private float headYaw;
   private float prevHeadYaw;
   private double velY;
   private boolean onGround;
   private int idleTicks;
   private boolean dancing;
   private float walkPhase;
   private float prevWalkPhase;
   private float walkAmount;
   private float prevWalkAmount;
   private int blinkTicks;
   private int age;

   public void reset() {
      this.pos = null;
      this.target = null;
   }

   public void tick(ClientLevel world, Vec3 center, Vec3 lookAt, PetConfig cfg) {
      if (world != this.world) {
         this.world = world;
         this.pos = null;
      }

      this.age++;
      if (this.pos == null || this.pos.distanceToSqr(center) > 576.0 || this.pos.y < world.getMinY() - 10) {
         this.spawnNear(center);
      }

      this.prevPos = this.pos;
      this.prevYaw = this.yaw;
      this.prevHeadYaw = this.headYaw;
      this.prevWalkPhase = this.walkPhase;
      this.prevWalkAmount = this.walkAmount;
      double tempo = cfg.speed();
      double dx0 = center.x - this.pos.x;
      double dz0 = center.z - this.pos.z;
      double distToCenter = Math.sqrt(dx0 * dx0 + dz0 * dz0);
      boolean tooFar = distToCenter > cfg.radius() + 2.5;
      if (!tooFar || this.target != null && !(horizontalDist(this.target, center) > cfg.radius())) {
         if (this.target == null) {
            if (this.idleTicks > 0) {
               this.idleTicks--;
            } else {
               this.target = this.pickTarget(center, cfg.radius());
               this.dancing = false;
            }
         }
      } else {
         this.target = this.pickTarget(center, Math.min(2.0, cfg.radius()));
         this.idleTicks = 0;
         this.dancing = false;
      }

      double x = this.pos.x;
      double y = this.pos.y;
      double z = this.pos.z;
      boolean moving = false;
      if (this.target != null) {
         double dx = this.target.x - x;
         double dz = this.target.z - z;
         double dist = Math.sqrt(dx * dx + dz * dz);
         if (dist < 0.2) {
            this.arrive(cfg);
         } else {
            double stepLen = Math.min(dist, (tooFar ? 0.28 : 0.11) * tempo);
            double nx = x + dx / dist * stepLen;
            double nz = z + dz / dist * stepLen;
            double ground = groundAt(world, nx, nz, y, 1.4);
            double rise = ground - y;
            if (rise > 1.3) {
               this.arrive(cfg);
            } else if (rise > 0.6) {
               if (this.onGround) {
                  this.velY = 0.42;
                  this.onGround = false;
               }

               moving = true;
            } else {
               x = nx;
               z = nz;
               moving = true;
            }

            this.yaw = approachAngle(this.yaw, (float)Math.toDegrees(Math.atan2(-dx, dz)), 25.0F);
         }
      }

      double groundHere = groundAt(world, x, z, y, 0.6);
      if (this.onGround && groundHere > y && groundHere - y <= 0.6) {
         y = groundHere;
      }

      this.velY -= 0.08;
      this.velY = Math.max(this.velY, -1.5);
      y += this.velY;
      if (y <= groundHere) {
         y = groundHere;
         this.velY = 0.0;
         this.onGround = true;
      } else {
         this.onGround = false;
      }

      this.pos = new Vec3(x, y, z);
      this.walkAmount = approach(this.walkAmount, moving ? 1.0F : 0.0F, 0.2F);
      if (moving) {
         this.walkPhase += (float)(0.55 * tempo * (tooFar ? 1.6 : 1.0));
      }

      if (this.target == null) {
         float toPlayer = (float)Math.toDegrees(Math.atan2(-(lookAt.x - x), lookAt.z - z));
         if (!this.dancing) {
            this.yaw = approachAngle(this.yaw, toPlayer, 6.0F);
         }

         float rel = Mth.clamp(Mth.wrapDegrees(toPlayer - this.yaw), -70.0F, 70.0F);
         this.headYaw = approach(this.headYaw, this.dancing ? 0.0F : rel, 12.0F);
      } else {
         this.headYaw = approach(this.headYaw, 0.0F, 12.0F);
      }

      if (this.blinkTicks > 0) {
         this.blinkTicks--;
      } else if (this.random.nextInt(80) == 0) {
         this.blinkTicks = 3;
      }
   }

   private void arrive(PetConfig cfg) {
      this.target = null;
      this.idleTicks = 40 + this.random.nextInt(120);
      this.dancing = !"Aus".equals(cfg.dance()) && this.random.nextFloat() < 0.45F;
   }

   private void spawnNear(Vec3 center) {
      double angle = this.random.nextDouble() * Math.PI * 2.0;
      double x = center.x + Math.cos(angle) * 1.5;
      double z = center.z + Math.sin(angle) * 1.5;
      double y = groundAt(this.world, x, z, center.y + 1.0, 3.0);
      this.pos = this.prevPos = new Vec3(x, y, z);
      this.velY = 0.0;
      this.onGround = true;
      this.target = null;
      this.idleTicks = 20;
   }

   private Vec3 pickTarget(Vec3 center, double r) {
      for (int i = 0; i < 8; i++) {
         double angle = this.random.nextDouble() * Math.PI * 2.0;
         double dist = 1.0 + this.random.nextDouble() * Math.max(0.5, r - 1.0);
         double x = center.x + Math.cos(angle) * dist;
         double z = center.z + Math.sin(angle) * dist;
         double y = groundAt(this.world, x, z, center.y + 1.0, 3.0);
         if (Math.abs(y - center.y) <= 3.0) {
            return new Vec3(x, y, z);
         }
      }

      return new Vec3(center.x, center.y, center.z);
   }

   private static double groundAt(ClientLevel world, double x, double z, double fromY, double maxUp) {
      int bx = Mth.floor(x);
      int bz = Mth.floor(z);
      int top = Mth.floor(fromY + maxUp);
      MutableBlockPos p = new MutableBlockPos();

      for (int by = top; by >= top - 11; by--) {
         p.set(bx, by, bz);
         BlockState state = world.getBlockState(p);
         VoxelShape shape = state.getCollisionShape(world, p);
         if (!shape.isEmpty()) {
            double surface = by + shape.max(Axis.Y);
            if (surface <= fromY + maxUp + 1.0E-4) {
               return surface;
            }
         }
      }

      return fromY - 10.0;
   }

   public void render(WorldCtx context, float tickDelta, PetConfig cfg) {
      if (this.pos != null && this.prevPos != null && this.world != null) {
         Vec3 cam = context.cameraPos();
         double x = Mth.lerp((double)tickDelta, this.prevPos.x, this.pos.x);
         double y = Mth.lerp((double)tickDelta, this.prevPos.y, this.pos.y);
         double z = Mth.lerp((double)tickDelta, this.prevPos.z, this.pos.z);
         float bodyYaw = Mth.rotLerp(tickDelta, this.prevYaw, this.yaw);
         float head = Mth.lerp(tickDelta, this.prevHeadYaw, this.headYaw);
         float phase = Mth.lerp(tickDelta, this.prevWalkPhase, this.walkPhase);
         float walk = Mth.lerp(tickDelta, this.prevWalkAmount, this.walkAmount);
         float t = (this.age + tickDelta) / 20.0F * (float)cfg.speed();
         float brightness = this.brightnessAt(x, y + 0.3, z);
         float scale = (float)(cfg.size() / 19.0 * 1.0);
         PoseStack m = context.matrices();
         m.pushPose();
         m.translate(x - cam.x, y - cam.y, z - cam.z);
         if (cfg.shadow()) {
            VertexConsumer shadowVc = context.buffer(WorldRender.translucentColor());
            float r = (float)(cfg.size() * 0.3);
            WorldRender.flatQuad(m, shadowVc, -r, 0.015F, -r, r, r, 1426063360);
         }

         float hop = 0.0F;
         float sway = 0.0F;
         float spin = 0.0F;
         float flap = 0.0F;
         if (this.dancing && this.target == null) {
            boolean all = "Alles".equals(cfg.dance());
            if (all || "Hüpfen".equals(cfg.dance())) {
               hop = Math.abs(Mth.sin(t * (float) Math.PI * 2.0F)) * 3.5F;
               flap = Math.abs(Mth.sin(t * (float) Math.PI * 4.0F)) * 50.0F;
            }

            if (all || "Wackeln".equals(cfg.dance())) {
               sway = Mth.sin(t * (float) Math.PI * 2.0F) * 15.0F;
               flap = Math.max(flap, 25.0F + Mth.sin(t * (float) Math.PI * 4.0F) * 20.0F);
            }

            if (all || "Drehen".equals(cfg.dance())) {
               spin = t * (all ? 120.0F : 240.0F) % 360.0F;
            }
         }

         float waddle = Mth.sin(phase) * 8.0F * walk;
         float bob = Math.abs(Mth.sin(phase)) * 0.8F * walk;
         if (!this.onGround) {
            flap = Math.max(flap, 60.0F);
         }

         m.scale(scale, scale, scale);
         m.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw - spin));
         m.translate(0.0F, hop + bob + PetModels.hover(cfg.skin(), t) + liftPixels(cfg), 0.0F);
         m.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway + waddle));
         float legSwing = Mth.sin(phase) * 35.0F * walk;
         if (SkinPet.is(cfg.skin())) {
            // Skin Pet: Original-Skin eines Spielers, gezeichnet mit der echten Skin-Textur
            int packed = com.lennart.nebula.compat.Compat.lightAt(this.world, BlockPos.containing(x, y + 0.3, z));
            float tt = this.age / 20.0F * (float)cfg.speed();
            SkinPet.draw(m, context::buffer, SkinPet.name(cfg.skin()), packed, new PetModels.Anim(tt, head, legSwing, walk, flap, this.blinkTicks > 0, this.onGround, phase));
         } else {
            VertexConsumer vc = context.buffer(WorldRender.solidColor());
            this.drawModel(m, vc, cfg, brightness, head, legSwing, flap);
         }
         m.popPose();
      }
   }

   private static float liftPixels(PetConfig cfg) {
      return (float)(cfg.lift() / Math.max(0.05, cfg.size()) * 19.0);
   }

   /** Für die Menü-Vorschau: Modell im Stand, leichter Kopfschwenk. */
   public void drawPreview(PoseStack m, VertexConsumer vc, PetConfig cfg) {
      float t = System.currentTimeMillis() / 1000.0F;
      m.translate(0.0F, PetModels.hover(cfg.skin(), t), 0.0F);
      this.drawModel(m, vc, cfg, 1.0F, Mth.sin(t * 0.8F) * 25.0F, 0.0F, 0.0F);
   }

   private void drawModel(PoseStack m, VertexConsumer vc, PetConfig cfg, float light, float headYaw, float legSwing, float flap) {
      if (PetModels.has(cfg.skin())) {
         float t = this.age / 20.0F * (float)cfg.speed();
         PetModels.draw(cfg.skin(), m, vc, light, new PetModels.Anim(t, headYaw, legSwing, this.walkAmount, flap, this.blinkTicks > 0, this.onGround, this.walkPhase));
      } else {
         MeowlPetEntity.Skin k = "Katze".equals(cfg.skin()) ? SKIN_CAT : SKIN_PURPLE;

         for (int side = -1; side <= 1; side += 2) {
            m.pushPose();
            m.translate(side * 1.75F, 2.2F, 0.25F);
            m.mulPose(com.mojang.math.Axis.XP.rotationDegrees(side * legSwing));
            WorldRender.box(m, vc, -0.6F, -2.2F, -0.6F, 0.6F, 0.0F, 0.6F, k.feet(), light);
            WorldRender.box(m, vc, -1.1F, -2.2F, -0.6F, 1.1F, -1.6F, 2.0F, k.feet(), light);
            m.popPose();
         }

         WorldRender.box(m, vc, -4.0F, 2.0F, -3.5F, 4.0F, 10.0F, 3.5F, k.fur(), light);
         if (k.cat()) {
            WorldRender.box(m, vc, -3.4F, 2.2F, 3.5F, 3.4F, 10.0F, 4.0F, k.belly(), light);

            for (int i = 0; i < 3; i++) {
               float y = 4.0F + i * 2.0F;
               WorldRender.box(m, vc, -4.05F, y, -3.55F, 4.05F, y + 0.7F, -1.0F, k.stripe(), light);
            }

            WorldRender.box(m, vc, -0.7F, 2.5F, -6.5F, 0.7F, 3.9F, -3.5F, k.furDark(), light);
            WorldRender.box(m, vc, -0.7F, 3.9F, -6.5F, 0.7F, 7.5F, -5.1F, k.furDark(), light);
         } else {
            WorldRender.box(m, vc, -3.0F, 2.5F, 3.5F, 3.0F, 8.5F, 4.0F, k.belly(), light);
            WorldRender.box(m, vc, -2.0F, 2.2F, -5.0F, 2.0F, 3.6F, -3.5F, k.furDark(), light);
         }

         for (int side = -1; side <= 1; side += 2) {
            m.pushPose();
            m.translate(side * 4.0F, 9.5F, 0.0F);
            m.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * flap));
            if (side < 0) {
               WorldRender.box(m, vc, -1.0F, -6.5F, -2.5F, 0.0F, 0.0F, 2.5F, k.furDark(), light);
            } else {
               WorldRender.box(m, vc, 0.0F, -6.5F, -2.5F, 1.0F, 0.0F, 2.5F, k.furDark(), light);
            }

            m.popPose();
         }

         m.pushPose();
         m.translate(0.0F, 9.5F, 0.0F);
         m.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-headYaw));
         WorldRender.box(m, vc, -4.5F, 0.0F, -4.0F, 4.5F, 7.0F, 4.0F, k.fur(), light);
         boolean blink = this.blinkTicks > 0;
         if (k.cat()) {
            this.drawCatFace(m, vc, k, light, blink);
         } else {
            this.drawOwlFace(m, vc, k, light, blink);
         }

         for (int side = -1; side <= 1; side += 2) {
            float ox = side * 3.3F;
            WorldRender.box(m, vc, ox - 1.2F, 7.0F, -1.0F, ox + 1.2F, 8.6F, 1.0F, k.fur(), light);
            WorldRender.box(m, vc, ox - 0.7F, 8.6F, -0.6F, ox + 0.7F, 9.5F, 0.6F, k.cat() ? k.furDark() : k.fur(), light);
            WorldRender.box(m, vc, ox - 0.6F, 7.0F, 1.0F, ox + 0.6F, 8.4F, 1.25F, k.earInner(), light);
         }

         m.popPose();
      }
   }

   private void drawOwlFace(PoseStack m, VertexConsumer vc, MeowlPetEntity.Skin k, float light, boolean blink) {
      WorldRender.box(m, vc, -4.0F, 0.5F, 4.0F, 4.0F, 6.5F, 4.3F, k.belly(), light);

      for (int side = -1; side <= 1; side += 2) {
         float cx = side * 2.1F;
         if (blink) {
            WorldRender.box(m, vc, cx - 1.6F, 3.4F, 4.3F, cx + 1.6F, 3.9F, 4.6F, k.pupil(), light);
         } else {
            WorldRender.box(m, vc, cx - 1.6F, 2.0F, 4.3F, cx + 1.6F, 5.4F, 4.6F, k.eye(), light);
            WorldRender.box(m, vc, cx - 0.8F, 2.8F, 4.6F, cx + 0.8F, 4.4F, 4.9F, k.pupil(), 1.0F);
         }
      }

      WorldRender.box(m, vc, -0.75F, 0.9F, 4.3F, 0.75F, 2.8F, 5.8F, k.nose(), light);
   }

   private void drawCatFace(PoseStack m, VertexConsumer vc, MeowlPetEntity.Skin k, float light, boolean blink) {
      WorldRender.box(m, vc, -0.4F, 5.2F, 4.0F, 0.4F, 7.0F, 4.15F, k.stripe(), light);
      WorldRender.box(m, vc, -2.2F, 5.6F, 4.0F, -1.4F, 7.0F, 4.15F, k.stripe(), light);
      WorldRender.box(m, vc, 1.4F, 5.6F, 4.0F, 2.2F, 7.0F, 4.15F, k.stripe(), light);

      for (int i = 0; i < 3; i++) {
         float z = -2.5F + i * 2.0F;
         WorldRender.box(m, vc, -3.0F, 7.0F, z, 3.0F, 7.12F, z + 0.8F, k.stripe(), light);
      }

      for (int side = -1; side <= 1; side += 2) {
         float x1 = side < 0 ? -4.55F : 4.5F;
         WorldRender.box(m, vc, x1, 2.4F, 0.0F, x1 + 0.05F, 3.0F, 3.5F, k.stripe(), light);
         WorldRender.box(m, vc, x1, 3.6F, 0.5F, x1 + 0.05F, 4.1F, 3.5F, k.stripe(), light);
      }

      WorldRender.box(m, vc, -2.6F, 0.0F, 4.0F, 2.6F, 2.6F, 4.4F, k.belly(), light);
      WorldRender.box(m, vc, -1.4F, 2.6F, 4.0F, 1.4F, 2.9F, 4.4F, k.belly(), light);

      for (int side = -1; side <= 1; side += 2) {
         float cx = side * 2.2F;
         if (blink) {
            WorldRender.box(m, vc, cx - 1.5F, 4.1F, 4.0F, cx + 1.5F, 4.6F, 4.35F, k.pupil(), light);
         } else {
            WorldRender.box(m, vc, cx - 1.6F, 2.9F, 4.0F, cx + 1.6F, 6.0F, 4.25F, k.eye(), light);
            WorldRender.box(m, vc, cx - 1.3F, 3.15F, 4.25F, cx + 1.3F, 5.75F, 4.45F, k.pupil(), 1.0F);
            WorldRender.box(m, vc, cx - 0.9F + side * 0.2F, 4.8F, 4.45F, cx - 0.3F + side * 0.2F, 5.4F, 4.55F, -1, 1.0F);
         }
      }

      WorldRender.box(m, vc, -0.55F, 2.1F, 4.4F, 0.55F, 2.7F, 4.7F, k.nose(), light);
      WorldRender.box(m, vc, -0.12F, 1.4F, 4.4F, 0.12F, 2.1F, 4.5F, k.furDark(), light);
   }

   private float brightnessAt(double x, double y, double z) {
      ClientLevel world = this.world;
      BlockPos p = BlockPos.containing(x, y, z);
      int block = world.getBrightness(LightLayer.BLOCK, p);
      int sky = world.getBrightness(LightLayer.SKY, p) - world.getSkyDarken();
      int level = Math.max(block, Math.max(0, sky));
      return 0.3F + 0.7F * (level / 15.0F);
   }

   private static double horizontalDist(Vec3 a, Vec3 b) {
      double dx = a.x - b.x;
      double dz = a.z - b.z;
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static float approach(float value, float goal, float step) {
      return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
   }

   private static float approachAngle(float value, float goal, float step) {
      float diff = Mth.wrapDegrees(goal - value);
      return value + Mth.clamp(diff, -step, step);
   }

   private record Skin(int fur, int furDark, int belly, int earInner, int eye, int pupil, int nose, int feet, int stripe, boolean cat) {
   }
}
