package com.lennart.nebula.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Zeichnet alle Kosmetik-Module am Spieler (eigene und die anderer Meowl-Spieler). */
public class CosmeticFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
   public CosmeticFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
      super(context);
   }

   @Override
   public void submit(PoseStack matrices, SubmitNodeCollector queue, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !state.isInvisible && com.lennart.nebula.module.mods.OtherClients.showMeowlCosmetics()) {
         // Pose dieses Spielers sicher setzen: andere Ebenen (z.B. anderer Mods) können das geteilte Modell verstellen,
         // dann säße Kopfschmuck beim Hoch-/Runterschauen nicht mehr auf dem Kopf
         this.getParentModel().setupAnim(state);
         com.lennart.nebula.emote.EmoteState.Active emote = state.isPassenger ? null : com.lennart.nebula.emote.EmoteState.forState(state, state.id);
         if (emote != null && emote.type.vehicle()) {
            float lit = 0.32F + 0.68F * Math.max(LightCoordsUtil.block(light), LightCoordsUtil.sky(light)) / 15.0F;
            float now = com.lennart.nebula.emote.EmoteState.now();
            com.lennart.nebula.emote.VehicleModels.submit(
               matrices,
               queue,
               emote.type,
               com.lennart.nebula.emote.EmotePoses.blend(emote, now),
               state.walkAnimationPos * 1.3F,
               this.getParentModel().head.yRot,
               now - emote.start,
               lit
            );
         }

         if (com.lennart.nebula.emote.EmoteBody.hideHead(state.id)) {
            // eigener Körper in der Ich-Ansicht: Kosmetik säße direkt vor der Kamera
            return;
         }

         Entity entity = mc.level.getEntity(state.id);
         // Leistung: Kosmetik anderer nur bis zur eingestellten Sichtweite
         if (entity instanceof Player far && far != mc.player && mc.player != null
            && far.distanceToSqr(mc.player) > com.lennart.nebula.module.mods.Performance.cosmeticRangeSqr()) {
            return;
         }

         if (entity instanceof Player player) {
            int block = LightCoordsUtil.block(light);
            int sky = LightCoordsUtil.sky(light);
            float bright = 0.32F + 0.68F * Math.max(block, sky) / 15.0F;
            CosmeticContext ctx = null;

            // Vorschaubild im Menü: nur diese eine Kosmetik in diesem Stil
            PreviewOverrides.Entry preview = PreviewOverrides.get(state);
            if (preview != null && preview.target() == PreviewOverrides.PET) {
               com.lennart.nebula.module.mods.MeowlPet pets = com.lennart.nebula.module.mods.MeowlPet.instance();
               if (pets != null) {
                  pets.drawPreviewBeside(matrices, queue, preview.style());
               }

               return;
            }

            for (Cosmetic c : Cosmetics.all()) {
               CosmeticValue v = preview == null ? Cosmetics.valueFor(player, c) : (preview.target() == c ? c.valueWithStyle(preview.style()) : null);
               if (v != null) {
                  if (ctx == null) {
                     ctx = new CosmeticContext(matrices, queue, state, this.getParentModel(), player, bright, Cosmetics.time() + (player.getId() % 97) * 0.37F);
                  }

                  long t0 = com.lennart.nebula.util.Perf.start();
                  Object key = player == mc.player ? c : c.othersKey();
                  try {
                     matrices.pushPose();
                     ctx.perfKey = key;
                     ctx.outer = v.outer();
                     if (v.shifted()) {
                        matrices.translate(v.ox() / 16.0F, -v.oy() / 16.0F, -v.oz() / 16.0F);
                     }

                     c.render(ctx, v);
                  } catch (RuntimeException var14) {
                  } finally {
                     matrices.popPose();
                     com.lennart.nebula.util.Perf.add(key, t0);
                  }
               }
            }
         }
      }
   }
}
