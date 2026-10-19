package com.lennart.nebula.module.mods.pet;

import com.lennart.nebula.module.mods.MeowlPet;
import com.lennart.nebula.util.MeowlNetwork;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

public final class PetPresence {
   private final Minecraft mc = Minecraft.getInstance();
   private final MeowlNetwork network = new MeowlNetwork();
   private final Map<UUID, MeowlPetEntity> remotePets = new HashMap<>();
   private final MeowlPet settings;
   private UUID lastSelf;
   private String lastName;
   private boolean online;

   public PetPresence(MeowlPet settings) {
      this.settings = settings;
   }

   public void tick() {
      LocalPlayer self = this.mc.player;
      if (self != null && this.mc.level != null) {
         if (!this.settings.presenceEnabled()) {
            if (this.online && this.lastSelf != null) {
               this.network.goOffline(this.lastSelf, this.lastName);
            }

            this.online = false;
            this.remotePets.clear();
         } else {
            this.online = true;
            this.lastSelf = self.getUUID();
            this.lastName = self.getGameProfile().name();
            boolean petOn = this.settings.isEnabled() && this.settings.sharePet();
            this.network.tick(this.lastSelf, this.lastName, petOn, this.settings.config());
            Set<UUID> present = new HashSet<>();
            if (this.settings.showOthersPets()) {
               for (Player player : this.mc.level.players()) {
                  if (player != self && !player.isSpectator()) {
                     MeowlNetwork.Peer peer = this.network.get(player.getUUID(), player.getGameProfile().name());
                     if (peer != null && peer.petEnabled() && peer.pet() != null) {
                        present.add(player.getUUID());
                        MeowlPetEntity remote = this.remotePets.computeIfAbsent(player.getUUID(), u -> new MeowlPetEntity());
                        remote.tick(this.mc.level, player.position(), player.getEyePosition(), peer.pet());
                     }
                  }
               }
            }

            this.remotePets.keySet().retainAll(present);
         }
      }
   }

   public void shutdown() {
      if (this.online && this.lastSelf != null) {
         this.network.goOffline(this.lastSelf, this.lastName);
      }
   }

   public void render(WorldCtx context, float tickDelta) {
      if (this.mc.level != null && this.mc.player != null && this.online) {
         for (Entry<UUID, MeowlPetEntity> entry : this.remotePets.entrySet()) {
            Player owner = this.mc.level.getPlayerByUUID(entry.getKey());
            MeowlNetwork.Peer peer = this.network.get(entry.getKey(), owner != null ? owner.getGameProfile().name() : null);
            if (peer != null && peer.pet() != null) {
               entry.getValue().render(context, tickDelta, peer.pet());
            }
         }
      }
   }

   public boolean isMeowlUser(UUID uuid, String name) {
      return this.peer(uuid, name) != null;
   }

   public MeowlNetwork.Peer peer(UUID uuid, String name) {
      return this.online ? this.network.get(uuid, name) : null;
   }
}
