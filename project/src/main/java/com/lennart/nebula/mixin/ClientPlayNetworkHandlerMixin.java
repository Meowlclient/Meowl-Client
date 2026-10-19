package com.lennart.nebula.mixin;

import com.lennart.nebula.totem.TotemPopTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class ClientPlayNetworkHandlerMixin {
   @Inject(
      method = {"handleEntityEvent"},
      at = {@At("TAIL")}
   )
   private void meowl$totemPops(ClientboundEntityEventPacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         Entity entity = packet.getEntity(mc.level);
         if (entity != null) {
            TotemPopTracker.onEntityStatus(entity, packet.getEventId());
         }
      }
   }
}
