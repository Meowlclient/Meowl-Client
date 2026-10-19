package com.lennart.nebula.replay;

import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Aufnahme und Wiedergabe: zeichnet ständig die letzten Minuten aller Spieler in der Nähe auf (Position, Blick, Haltung,
 * Ausrüstung, Schläge) und spielt sie als „Geister“-Spieler in der Welt ab – die Kamera kann jedem folgen.
 */
public final class ReplayEngine {
   private static final EquipmentSlot[] SLOTS = {
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final ReplayEngine INSTANCE = new ReplayEngine();
   private final ArrayDeque<ReplayData.Frame> buffer = new ArrayDeque<>();
   private final Map<UUID, Boolean> lastSwing = new HashMap<>();
   private String bufferDim = "";
   private long bufferStart;
   // Wiedergabe
   private ReplayData.Clip clip;
   private float pos;
   private float speed = 1.0F;
   private boolean paused;
   private final Map<UUID, Ghost> ghosts = new HashMap<>();
   private UUID follow;
   private int nextId = -700000;
   private boolean jumped;

   public static ReplayEngine get() {
      return INSTANCE;
   }

   // ================================================================== Aufnahme

   public void record(Minecraft mc, int maxSeconds, double range) {
      if (mc.level == null || mc.player == null || this.clip != null) {
         return;
      }

      String dim = mc.level.dimension().identifier().toString();
      if (!dim.equals(this.bufferDim)) {
         this.clearBuffer();
         this.bufferDim = dim;
      }

      if (this.buffer.isEmpty()) {
         this.bufferStart = System.currentTimeMillis();
      }

      List<ReplayData.State> list = new ArrayList<>();

      for (Player p : mc.level.players()) {
         if (p instanceof Ghost || p.isSpectator() || p.distanceToSqr(mc.player) > range * range) {
            continue;
         }

         boolean sw = SwingCompat.isSwinging(p);
         Boolean before = this.lastSwing.put(p.getUUID(), sw);
         int flags = 0;
         if (p.isShiftKeyDown()) {
            flags |= ReplayData.CROUCH;
         }

         if (p.isSprinting()) {
            flags |= ReplayData.SPRINT;
         }

         if (p.isSwimming() || p.isVisuallySwimming()) {
            flags |= ReplayData.SWIM;
         }

         if (p.isFallFlying()) {
            flags |= ReplayData.FLY;
         }

         if (sw && !Boolean.TRUE.equals(before)) {
            flags |= ReplayData.SWING;
         }

         String[] items = new String[ReplayData.SLOTS];

         for (int i = 0; i < SLOTS.length; i++) {
            ItemStack st = p.getItemBySlot(SLOTS[i]);
            items[i] = st.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(st.getItem()).toString().intern();
         }

         list.add(
            new ReplayData.State(
               p.getUUID(), p.getGameProfile().name().intern(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), p.getYHeadRot(), p.yBodyRot, flags, items
            )
         );
      }

      this.buffer.addLast(new ReplayData.Frame(list));
      int max = Math.max(20, maxSeconds * 20);

      while (this.buffer.size() > max) {
         this.buffer.removeFirst();
         this.bufferStart += 50L;
      }
   }

   public void clearBuffer() {
      this.buffer.clear();
      this.lastSwing.clear();
   }

   public float bufferedSeconds() {
      return this.buffer.size() / 20.0F;
   }

   /** Die letzten Sekunden als Clip (Kopie). */
   public ReplayData.Clip snapshot() {
      return new ReplayData.Clip(this.bufferDim, this.bufferStart, new ArrayList<>(this.buffer));
   }

   // ================================================================== Wiedergabe

   public boolean playing() {
      return this.clip != null;
   }

   public ReplayData.Clip clip() {
      return this.clip;
   }

   public void start(ReplayData.Clip c) {
      this.stop();
      if (c != null && !c.frames().isEmpty()) {
         this.clip = c;
         this.pos = 0.0F;
         this.paused = false;
         this.jumped = true;
         this.follow = null;
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            this.follow = mc.player.getUUID();
         }

         this.apply(mc);
      }
   }

   public void stop() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.getCameraEntity() instanceof Ghost) {
         mc.setCameraEntity(mc.player);
      }

      ClientLevel level = mc.level;

      for (Ghost g : this.ghosts.values()) {
         if (level != null) {
            level.removeEntity(g.getId(), Entity.RemovalReason.DISCARDED);
         }
      }

      this.ghosts.clear();
      this.clip = null;
   }

   public void tick(Minecraft mc) {
      if (this.clip == null) {
         return;
      }

      if (mc.level == null || mc.player == null) {
         this.stop();
         return;
      }

      if (!this.paused) {
         int before = (int)this.pos;
         this.pos += this.speed;
         if (this.pos >= this.clip.frames().size() - 1) {
            this.pos = this.clip.frames().size() - 1;
            this.paused = true;
         }

         if ((int)this.pos != before) {
            this.apply(mc);
         }
      } else if (this.jumped) {
         this.apply(mc);
      }
   }

   private void apply(Minecraft mc) {
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      ReplayData.Frame f = this.clip.frames().get(Math.max(0, Math.min(this.clip.frames().size() - 1, (int)this.pos)));
      boolean snap = this.jumped;
      this.jumped = false;
      java.util.Set<UUID> seen = new java.util.HashSet<>();

      for (ReplayData.State s : f.players()) {
         seen.add(s.id());
         Ghost g = this.ghosts.get(s.id());
         boolean fresh = false;
         if (g == null) {
            g = new Ghost(level, new GameProfile(UUID.randomUUID(), s.name()), s.id());
            g.setId(this.nextId--);
            g.snapTo(s.x(), s.y(), s.z(), s.yRot(), s.xRot());
            level.addEntity(g);
            this.ghosts.put(s.id(), g);
            fresh = true;
         }

         if (snap || fresh) {
            g.snapTo(s.x(), s.y(), s.z(), s.yRot(), s.xRot());
            g.setOldPosAndRot();
            g.yHeadRot = s.head();
            g.yBodyRot = s.body();
         } else {
            g.moveOrInterpolateTo(new Vec3(s.x(), s.y(), s.z()), s.yRot(), s.xRot());
            g.lerpHeadTo(s.head(), 3);
            g.yBodyRot = s.body();
         }

         g.setInvisible(false);
         g.setShiftKeyDown((s.flags() & ReplayData.CROUCH) != 0);
         g.setSprinting((s.flags() & ReplayData.SPRINT) != 0);
         Pose pose = (s.flags() & ReplayData.FLY) != 0
            ? Pose.FALL_FLYING
            : ((s.flags() & ReplayData.SWIM) != 0 ? Pose.SWIMMING : ((s.flags() & ReplayData.CROUCH) != 0 ? Pose.CROUCHING : Pose.STANDING));
         g.setSwimming((s.flags() & ReplayData.SWIM) != 0);
         g.setPose(pose);
         if ((s.flags() & ReplayData.SWING) != 0 && !this.paused) {
            SwingCompat.swing(g);
         }

         for (int i = 0; i < SLOTS.length; i++) {
            String id = s.items()[i];
            if (!java.util.Objects.equals(id, g.items[i])) {
               g.items[i] = id;
               Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
               g.setItemSlot(SLOTS[i], item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
            }
         }
      }

      // nicht mehr aufgezeichnete Spieler ausblenden
      for (Map.Entry<UUID, Ghost> e : this.ghosts.entrySet()) {
         if (!seen.contains(e.getKey())) {
            e.getValue().setInvisible(true);
         }
      }

      // Kamera folgt dem gewählten Spieler
      Ghost target = this.follow == null ? null : this.ghosts.get(this.follow);
      if (target != null && !target.isInvisible()) {
         if (mc.getCameraEntity() != target) {
            mc.setCameraEntity(target);
         }
      } else if (mc.getCameraEntity() instanceof Ghost) {
         mc.setCameraEntity(mc.player);
      }
   }

   public float position() {
      return this.pos;
   }

   public int length() {
      return this.clip == null ? 0 : this.clip.frames().size();
   }

   public void seek(float frame) {
      if (this.clip != null) {
         this.pos = Math.max(0.0F, Math.min(this.clip.frames().size() - 1, frame));
         this.jumped = true;
      }
   }

   public boolean paused() {
      return this.paused;
   }

   public void setPaused(boolean p) {
      if (!p && this.clip != null && this.pos >= this.clip.frames().size() - 1) {
         this.seek(0.0F);
      }

      this.paused = p;
   }

   public float speed() {
      return this.speed;
   }

   public void setSpeed(float s) {
      this.speed = s;
   }

   public UUID follow() {
      return this.follow;
   }

   /** null = keiner (deine eigene Kamera). */
   public void setFollow(UUID id) {
      this.follow = id;
      this.jumped = true;
   }

   /** Spieler, die im aktuellen Bild vorkommen (UUID → Name). */
   public Map<UUID, String> playersNow() {
      Map<UUID, String> out = new java.util.LinkedHashMap<>();
      if (this.clip != null) {
         for (ReplayData.State s : this.clip.frames().get(Math.max(0, Math.min(this.clip.frames().size() - 1, (int)this.pos))).players()) {
            out.put(s.id(), s.name());
         }
      }

      return out;
   }

   /** Geist-Spieler der Wiedergabe: trägt den echten Skin des aufgezeichneten Spielers. */
   public static final class Ghost extends RemotePlayer {
      final UUID origin;
      final String[] items = new String[ReplayData.SLOTS];

      Ghost(ClientLevel level, GameProfile profile, UUID origin) {
         super(level, profile);
         this.origin = origin;
      }

      @Override
      public PlayerSkin getSkin() {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(this.origin);
            if (info != null) {
               return info.getSkin();
            }
         }

         String name = this.getGameProfile().name();
         if (com.lennart.nebula.module.mods.pet.SkinPet.validName(name)) {
            PlayerSkin s = com.lennart.nebula.module.mods.pet.SkinPet.textures(name);
            if (s != null) {
               return s;
            }
         }

         return DefaultPlayerSkin.get(this.origin);
      }
   }
}
