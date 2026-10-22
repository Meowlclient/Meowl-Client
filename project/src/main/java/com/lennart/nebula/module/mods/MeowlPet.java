package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.mods.pet.MeowlPetEntity;
import com.lennart.nebula.module.mods.pet.PetConfig;
import com.lennart.nebula.module.mods.pet.PetPresence;
import com.lennart.nebula.module.mods.pet.SkinPet;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.nametag.Nametags;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.util.MeowlNetwork;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class MeowlPet extends Module {
   private final com.lennart.nebula.setting.StyleSetting skin = this.add(
      new com.lennart.nebula.setting.StyleSetting(
         "Skin",
         "Welches Pet um dich herumläuft",
         "Meowl (lila)",
         com.lennart.nebula.cosmetic.PreviewOverrides.PET,
         false,
         "Meowl (lila)",
         "Katze",
         "Nasse Eule",
         "Mini-Rex",
         "Mini-Raptor",
         "Mini-Triceratops",
         "Baby-Drache",
         "Geisterchen",
         "Duolingo-Eule",
         "BB-8",
         "R2-D2",
         "Skin Pet"
      )
   );
   private final TextSetting skinPlayer = this.add(
      new TextSetting("Skin-Pet Spieler", "Minecraft-Name des Spielers, dessen Original-Skin das Skin Pet trägt", "", 16)
         .validator(s -> s.isEmpty() || SkinPet.validName(s))
   );
   private final ModeSetting dance = this.add(
      new ModeSetting("Tanz", "Wie die Meowl tanzt, wenn sie stehen bleibt", "Alles", "Alles", "Hüpfen", "Wackeln", "Drehen", "Aus")
   );
   private final NumberSetting speed = this.add(new NumberSetting("Tempo", "Lauf- und Tanztempo", 1.0, 0.3, 3.0, 0.1, "x"));
   private final NumberSetting size = this.add(new NumberSetting("Größe", "Höhe der Meowl in Blöcken", 0.8, 0.5, 1.6, 0.1, "Blöcke"));
   private final NumberSetting radius = this.add(new NumberSetting("Laufradius", "Wie weit sie um dich herumläuft", 4.0, 2.0, 12.0, 0.5, "Blöcke"));
   private final BooleanSetting follow = this.add(
      new BooleanSetting("Folgt dir", "An: läuft immer um dich herum. Aus: bleibt dort, wo du sie eingeschaltet hast", true)
   );
   private final NumberSetting height = this.add(new NumberSetting("Höhe", "Pet etwas höher schweben lassen (andere Meowl-Spieler sehen es auch)", 0.0, 0.0, 1.0, 0.1, "Blöcke"));
   private static MeowlPet instance;
   private final BooleanSetting shadow = this.add(new BooleanSetting("Schatten", "Kleiner Schatten unter der Meowl", true));
   private final BooleanSetting sharePet = this.add(new BooleanSetting("Mein Pet teilen", "Andere Meowl-Spieler sehen dein Pet um dich herumlaufen", true));
   private final BooleanSetting othersPets = this.add(
      new BooleanSetting("Pets anderer zeigen", "Pets anderer Meowl-Spieler bei dir anzeigen (auch wenn dein eigenes Pet aus ist)", true)
   );
   private final MeowlPetEntity pet = new MeowlPetEntity();
   private final PetPresence presence = new PetPresence(this);
   private Vec3 home;
   private ClientLevel lastWorld;

   public MeowlPet() {
      super(
         "Pets",
         "Ein 3D-Pet läuft um dich herum: Meowl, Katze, nasse Eule, Mini-Dinos, Baby-Drache oder Geist (Meowl-Spieler sehen es auch).",
         Category.MEOWL
      );
      this.section(Section.COSMETICS);
      this.legacy("Meowl Pet");
      instance = this;
      this.skinPlayer.setVisibility(() -> this.skin.is("Skin Pet"));
      this.group("Aussehen", new com.lennart.nebula.setting.Setting[]{this.skin, this.skinPlayer, this.size, this.shadow});
      this.group("Bewegung", new com.lennart.nebula.setting.Setting[]{this.dance, this.speed, this.radius, this.height, this.follow});
      this.group("Andere Spieler", new com.lennart.nebula.setting.Setting[]{this.sharePet, this.othersPets});
      ClientTickEvents.END_CLIENT_TICK.register(c -> this.presence.tick());
      WorldCtx.afterEntities(ctx -> this.presence.render(ctx, this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)));
      ClientLifecycleEvents.CLIENT_STOPPING.register(c -> this.presence.shutdown());
   }

   @Override
   public com.lennart.nebula.gui.ModulePreview preview() {
      return new com.lennart.nebula.gui.CosmeticPreview(() -> {}, com.lennart.nebula.cosmetic.PreviewOverrides.PET);
   }

   public static MeowlPet instance() {
      return instance;
   }

   /** Vorschau im Menü: das Pet steht neben deinem Charakter (Aufruf aus der Kosmetik-Ebene, Modellraum des Spielers). */
   public void drawPreviewBeside(com.mojang.blaze3d.vertex.PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector queue, String style) {
      PetConfig own = this.config();
      // Raster-Vorschau: dieses Pet in fester Größe vor der Figur
      com.lennart.nebula.module.mods.pet.PetConfig cfg = style == null
         ? own
         : new PetConfig(style, own.dance(), own.speed(), Math.min(1.3, Math.max(0.5, own.size())), own.radius(), own.shadow(), 0.0);
      if ("Skin Pet".equals(cfg.skin()) || com.lennart.nebula.module.mods.pet.SkinPet.is(cfg.skin())) {
         return;
      }

      matrices.pushPose();
      // zurück vom Modellraum (y nach unten) in den Raum der Figur (y nach oben, Füße bei 0)
      matrices.translate(0.0F, 1.501F, 0.0F);
      matrices.scale(-1.0F, -1.0F, 1.0F);
      if (style == null) {
         matrices.translate(0.85F, 0.0F, 0.15F);
      } else {
         matrices.translate(0.42F, 0.0F, -0.6F);
         // Vorschau-Raster: Pet schaut zur Kamera
         matrices.rotate(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
      }

      float scale = (float)(cfg.size() / 19.0);
      matrices.scale(scale, scale, scale);
      queue.submitCustomGeometry(matrices, com.lennart.nebula.util.WorldRender.solidColor(), com.lennart.nebula.util.Perf.timed(com.lennart.nebula.module.mods.MeowlPet.class, (entry, vc) -> {
         com.mojang.blaze3d.vertex.PoseStack m = new com.mojang.blaze3d.vertex.PoseStack();
         m.last().pose().set(entry.pose());
         m.last().normal().set(entry.normal());
         this.pet.drawPreview(m, vc, cfg);
      }));
      matrices.popPose();
   }

   public static double lift() {
      return instance == null ? 0.0 : instance.height.get();
   }

   public boolean sharePet() {
      return this.sharePet.get();
   }

   public boolean showOthersPets() {
      return this.othersPets.get();
   }

   public boolean presenceEnabled() {
      Nametags tags = Nametags.get();
      return this.sharePet.get() || this.othersPets.get() || tags != null && tags.meowlActive() || Watches.active() != null || Wings.active() != null || com.lennart.nebula.cosmetic.Cosmetics.anyActive() || com.lennart.nebula.cosmetic.Cosmetics.anyShowOthers() || PlasmaMode.sharing();
   }

   public boolean isMeowlUser(UUID uuid, String name) {
      return this.presence.isMeowlUser(uuid, name);
   }

   public static MeowlNetwork.Peer peer(Player player) {
      return instance == null ? null : instance.presence.peer(player.getUUID(), player.getGameProfile().name());
   }

   public PetConfig config() {
      String s = this.skin.get();
      if ("Skin Pet".equals(s)) {
         // Skin Pet: als "Skin:<Name>" übertragen, damit andere Meowl-Spieler denselben Skin sehen
         String name = this.skinPlayer.get().trim();
         if (name.isEmpty() && this.mc.getUser() != null) {
            name = this.mc.getUser().getName();
         }

         s = SkinPet.validName(name) ? SkinPet.PREFIX + name : "Meowl (lila)";
      }

      return new PetConfig(s, this.dance.get(), this.speed.get(), this.size.get(), this.radius.get(), this.shadow.get(), this.height.get());
   }

   @Override
   protected void onEnable() {
      this.pet.reset();
      this.home = null;
   }

   @Override
   public void onTick() {
      LocalPlayer player = this.mc.player;
      ClientLevel world = this.mc.level;
      if (player != null && world != null) {
         if (world != this.lastWorld) {
            this.lastWorld = world;
            this.home = null;
         }

         if (this.home == null) {
            this.home = player.position();
         }

         Vec3 center = this.follow.get() ? player.position() : this.home;
         this.pet.tick(world, center, player.getEyePosition(), this.config());
      }
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      this.pet.render(context, tickDelta, this.config());
   }
}
