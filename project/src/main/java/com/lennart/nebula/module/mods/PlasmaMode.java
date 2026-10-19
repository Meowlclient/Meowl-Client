package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.plasma.PlasmaSword;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.MeowlNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PlasmaMode extends Module {
   private static PlasmaMode instance;
   private final ModeSetting model = this.add(new ModeSetting("Modell", "Lichtschwert, Karambit, Katana oder Butterfly", "Lichtschwert", "Lichtschwert", "Karambit", "Katana", "Butterfly"));
   private final ColorSetting color = this.add(new ColorSetting("Klingenfarbe", "Farbe der Energieklinge", -54742, false));
   private final NumberSetting glow = this.add(new NumberSetting("Glow", "Wie stark die Klinge leuchtet", 100.0, 20.0, 160.0, 5.0, "%"));
   private final NumberSetting length = this.add(new NumberSetting("Klingenlänge", "Länge der Klinge", 100.0, 60.0, 140.0, 5.0, "%"));
   private final BooleanSetting ignite = this.add(new BooleanSetting("Zünd-Animation", "Klinge fährt beim Ziehen aus", true));
   private final BooleanSetting trail = this.add(new BooleanSetting("Schlag-Spur", "Nachleuchten beim Zuschlagen", true));
   private final BooleanSetting others = this.add(
      new BooleanSetting("Alle Schwerter anderer", "Auch normale Schwerter anderer Spieler als Lichtschwert in deiner Farbe zeigen (nur bei dir)", false)
   );
   private final BooleanSetting share = this.add(new BooleanSetting("Für andere sichtbar", "Andere Meowl-Spieler sehen dein Schwert als Lichtschwert", true));
   private final BooleanSetting showShared = this.add(
      new BooleanSetting("Lichtschwerter anderer", "Lichtschwerter anderer Meowl-Spieler in deren Farbe anzeigen", true)
   );
   private final BooleanSetting sound = this.add(new BooleanSetting("Sounds", "Zünden, Summen, Schwung, Treffer und Einfahren", true));
   private final ModeSetting soundSet = this.add(
      new ModeSetting("Klang", "Eigene Energieschwert-Sounds oder Vanilla-Sounds (Leuchtfeuer/Dreizack)", "Plasma", "Plasma", "Vanilla")
   );
   private final NumberSetting volume = this.add(new NumberSetting("Lautstärke", "Lautstärke der Sounds", 50.0, 0.0, 100.0, 5.0, "%"));
   private final BooleanSetting hum = this.add(new BooleanSetting("Summen", "Leises Summen, solange du das Schwert hältst", true));
   private final ModeSetting finish = this.add(new ModeSetting("Finish", "Muster der Karambit-Klinge (wie in CS)", "Doppler", PlasmaSword.FINISHES));
   private boolean inspectDown;
   private final boolean[] slotDown = new boolean[9];
   private int lastSlot = -1;
   private boolean holding;
   private long igniteStart;
   private boolean wasSwinging;
   private PlasmaMode.Hum humSound;
   private static final SoundEvent IGNITE = own("ignite");
   private static final SoundEvent RETRACT = own("retract");
   private static final SoundEvent SWING = own("swing");
   private static final SoundEvent HIT = own("hit");
   static final SoundEvent HUM = own("hum");

   public PlasmaMode() {
      super(
         "Schwerter",
         "Kosmetik für dein Schwert: Lichtschwert, Karambit oder Katana – Farbe, Glow, Finishes, Animationen und Inspect. Andere Meowl-Spieler sehen es auch. Nur visuell.",
         Category.PLAYER
      );
      this.section(Section.COSMETICS);
      this.icon(Items.END_ROD);
      instance = this;
      this.legacy("Plasma Mode", "Lichtschwert");
      this.finish.setVisibility(() -> this.model.is("Karambit") || this.model.is("Butterfly"));
      // Modell wird im Cosmetic-Shop gewählt (Schwerter-Karten) – kein eigener Button im Modul-Menü
      this.model.setVisibility(() -> false);
      this.group("Klinge", new Setting[]{this.model, this.finish, this.color, this.glow, this.length, this.ignite, this.trail});
      this.group("Andere Spieler", new Setting[]{this.share, this.showShared, this.others});
      this.group("Sound", new Setting[]{this.sound, this.soundSet, this.volume, this.hum});
      this.soundSet.setVisibility(this.sound::get);
      this.volume.setVisibility(this.sound::get);
      this.hum.setVisibility(this.sound::get);
   }

   /** Ingame-Vorschau beim Ziehen an den Reglern: Schwert (bzw. Karambit/Katana) in der Hand. */
   @Override
   public ItemStack ingamePreviewItem() {
      return new ItemStack(Items.DIAMOND_SWORD);
   }

   public static PlasmaMode active() {
      return instance != null && instance.isEnabled() ? instance : null;
   }

   public static boolean isSword(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.is(ItemTags.SWORDS);
   }

   public boolean showFor(boolean localPlayer) {
      return localPlayer || this.others.get();
   }

   /** Für die Anwesenheit (Kosmetik-Liste „cos“): Farbe|Glow|Länge. */
   public static void presence(com.google.gson.JsonObject cos) {
      PlasmaMode p = instance;
      if (p != null && p.isEnabled() && p.share.get()) {
         cos.addProperty("saber", String.format(
               "%06x|%d|%d|%d|%d", p.color.get() & 16777215, Math.round(p.glow.get()), Math.round(p.length.get()), p.modelId(), PlasmaSword.finishId(p.finish.get())
            )
         );
      }
   }

   /** Teilt man sein Schwert gerade mit anderen (dafür muss die Anwesenheit laufen)? */
   public static boolean sharing() {
      return instance != null && instance.isEnabled() && instance.share.get();
   }

   /** Will man die Lichtschwerter anderer sehen (Anwesenheit muss dafür laufen)? */
   public static boolean wantsOthers() {
      return instance != null && instance.showShared.get();
   }

   /** Vorschau im Kosmetik-Shop: eigenes Lichtschwert, auch wenn das Modul gerade aus ist. */
   /** Für Hotbar/Inventar-Symbole: {Modell, Farbe, Finish} oder null, wenn das Modul aus ist. */
   public static int[] iconInfo() {
      PlasmaMode p = instance;
      return p == null || !p.isEnabled() ? null : new int[]{p.modelId(), p.color.get() & 16777215, PlasmaSword.finishId(p.finish.get())};
   }

   /** Eigenes Karambit in der Ich-Ansicht (Hand wie ohne Item, Messer im Rückwärtsgriff)? */
   public static boolean karambitFirstPerson(ItemStack stack) {
      PlasmaMode p = instance;
      return p != null && p.isEnabled() && (p.modelId() == PlasmaSword.KARAMBIT || p.modelId() == PlasmaSword.BUTTERFLY) && isSword(stack);
   }

   /** Aussehen des eigenen Messers mit einem bestimmten Modell (Farbe, Glow, Länge, Finish), oder null wenn aus. */
   public static PlasmaSword.Look currentLook(int model) {
      PlasmaMode p = instance;
      return p == null
         ? null
         : new PlasmaSword.Look(
            p.color.get() & 16777215, (float)(p.glow.get() / 100.0), 0.95F * (float)(p.length.get() / 100.0), 1.0F, 0.0F, false, true, model, PlasmaSword.finishId(p.finish.get())
         );
   }

   /** Ist das eigene Messer gerade ein Butterfly? */
   public static boolean butterfly() {
      PlasmaMode p = instance;
      return p != null && p.modelId() == PlasmaSword.BUTTERFLY;
   }

   public static void submitKarambitHand(PoseStack matrices, SubmitNodeCollector queue, float spin) {
      PlasmaMode p = instance;
      if (p != null) {
         PlasmaSword.submitKarambit(
            matrices,
            queue,
            new PlasmaSword.Look(
               p.color.get() & 16777215,
               (float)(p.glow.get() / 100.0),
               0.95F * (float)(p.length.get() / 100.0),
               1.0F,
               0.0F,
               false,
               true,
               PlasmaSword.KARAMBIT,
               PlasmaSword.finishId(p.finish.get())
            ),
            spin
         );
      }
   }

   private int modelId() {
      return modelId(this.model.get());
   }

   private static int modelId(String name) {
      return switch (name == null ? "" : name) {
         case "Karambit" -> PlasmaSword.KARAMBIT;
         case "Katana" -> PlasmaSword.KATANA;
         case "Butterfly" -> PlasmaSword.BUTTERFLY;
         default -> PlasmaSword.SABER;
      };
   }

   /** Vorschau mit einem bestimmten Modell (Shop-Karte), sonst dem eingestellten. */
   public static boolean renderPreview(PoseStack matrices, SubmitNodeCollector queue, boolean leftHanded, String style) {
      PlasmaMode p = instance;
      if (p == null) {
         return false;
      }

      int id = style != null && !style.equals("saber") ? modelId(style) : p.modelId();
      p.render(matrices, queue, leftHanded, false, true, 0.0F, id);
      return true;
   }

   public static boolean renderPreview(PoseStack matrices, SubmitNodeCollector queue, boolean leftHanded) {
      PlasmaMode p = instance;
      if (p == null) {
         return false;
      }

      p.render(matrices, queue, leftHanded, false, true, 0.0F);
      return true;
   }

   /**
    * Zeichnet ein Schwert als Lichtschwert, falls passend: eigenes (Modul an), geteiltes eines anderen Meowl-Spielers
    * oder – wenn eingestellt – jedes Schwert anderer in der eigenen Farbe. false = normales Schwert zeichnen.
    */
   public static boolean renderSaber(
      PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, Entity holder, boolean leftHanded, boolean firstPerson, float swing
   ) {
      PlasmaMode p = instance;
      if (p == null || !isSword(stack)) {
         return false;
      }

      Minecraft mc = Minecraft.getInstance();
      if (holder != null && holder == mc.player) {
         if (!p.isEnabled()) {
            return false;
         }

         p.render(matrices, queue, leftHanded, firstPerson, true, swing);
         return true;
      }

      if (p.showShared.get() && holder instanceof Player other) {
         MeowlNetwork.Peer peer = MeowlPet.peer(other);
         String v = peer == null ? null : peer.cosmetic("saber");
         if (v != null) {
            try {
               String[] parts = v.split("\\|");
               int rgb = Integer.parseInt(parts[0], 16) & 16777215;
               float glow = parts.length > 1 ? Math.max(20, Math.min(160, Integer.parseInt(parts[1]))) / 100.0F : 1.0F;
               float len = parts.length > 2 ? Math.max(60, Math.min(140, Integer.parseInt(parts[2]))) / 100.0F : 1.0F;
               int model = parts.length > 3 ? Math.max(0, Math.min(3, Integer.parseInt(parts[3]))) : PlasmaSword.SABER;
               int fin = parts.length > 4 ? Math.max(0, Math.min(PlasmaSword.FINISHES.length - 1, Integer.parseInt(parts[4]))) : PlasmaSword.DOPPLER;
               PlasmaSword.render(matrices, queue, leftHanded, new PlasmaSword.Look(rgb, glow, 0.95F * len, 1.0F, swing, p.trail.get(), firstPerson, model, fin));
               return true;
            } catch (RuntimeException var14) {
               return false;
            }
         }
      }

      if (p.isEnabled() && p.others.get()) {
         p.render(matrices, queue, leftHanded, firstPerson, false, swing);
         return true;
      }

      return false;
   }

   private float vol() {
      // Messer summen und zünden nicht
      return this.sound.get() && this.modelId() == PlasmaSword.SABER ? (float)(this.volume.get() / 100.0) : 0.0F;
   }

   private static SoundEvent own(String name) {
      return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("nebula", "plasma." + name));
   }

   private boolean plasmaSounds() {
      return this.soundSet.is("Plasma");
   }

   private void play(SoundEvent event, float pitch, float volume) {
      this.mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
   }

   @Override
   public void onTick() {
      boolean now = this.mc.player != null && isSword(this.mc.player.getMainHandItem());
      float v = this.vol();
      boolean own = this.plasmaSounds();
      if (now && !this.holding) {
         this.igniteStart = System.currentTimeMillis();
         if (v > 0.0F) {
            if (own) {
               this.play(IGNITE, 1.0F, 0.8F * v);
            } else {
               this.play(SoundEvents.BEACON_ACTIVATE, 1.9F, 0.6F * v);
            }
         }
      } else if (!now && this.holding && v > 0.0F) {
         if (own) {
            this.play(RETRACT, 1.0F, 0.7F * v);
         } else {
            this.play(SoundEvents.BEACON_DEACTIVATE, 1.9F, 0.5F * v);
         }
      }

      this.holding = now;
      boolean swinging = this.mc.player != null && com.lennart.nebula.compat.Compat.isSwinging(this.mc.player);
      if (now && swinging && !this.wasSwinging && v > 0.0F) {
         float pitch = 0.94F + this.mc.player.getRandom().nextFloat() * 0.12F;
         if (own) {
            this.play(SWING, pitch, 0.55F * v);
            if (this.mc.crosshairPickEntity != null) {
               this.play(HIT, pitch, 0.5F * v);
            }
         } else {
            this.play(SoundEvents.TRIDENT_THROW.value(), 1.5F + this.mc.player.getRandom().nextFloat() * 0.3F, 0.45F * v);
         }
      }

      this.wasSwinging = swinging;
      // Inspect (Taste in den Minecraft-Steuerungen): frei für jeden, solange ein Messer/Schwert in der Hand ist; Zuschlagen oder Wegstecken bricht ab
      boolean inspectPressed = com.lennart.nebula.NebulaClient.down(com.lennart.nebula.NebulaClient.inspectKey);
      // oder wie in CS: die Slot-Taste des schon gezogenen Schwerts nochmal drücken
      int sel = this.mc.player != null ? this.mc.player.getInventory().getSelectedSlot() : -1;
      boolean slotAgain = false;
      for (int i = 0; i < 9; i++) {
         boolean d = this.mc.options.keyHotbarSlots[i].isDown();
         if (d && !this.slotDown[i] && i == sel && i == this.lastSlot) {
            slotAgain = true;
         }

         this.slotDown[i] = d;
      }

      this.lastSlot = sel;
      if ((inspectPressed && !this.inspectDown || slotAgain) && now && this.mc.gui.screen() == null) {
         if (this.modelId() == PlasmaSword.BUTTERFLY) {
            com.lennart.nebula.fx.ButterflyKnife.toggle();
         } else {
            com.lennart.nebula.fx.KarambitHand.inspect();
         }
      }

      this.inspectDown = inspectPressed;
      if (!now || swinging) {
         com.lennart.nebula.fx.KarambitHand.cancelInspect();
      }

      if (!now) {
         // weggesteckt: Butterfly ist beim nächsten Ziehen wieder zugeklappt
         com.lennart.nebula.fx.ButterflyKnife.reset();
      }
      boolean wantHum = now && this.hum.get() && v > 0.0F;
      if (this.humSound != null && this.humSound.own != own) {
         this.humSound.halt();
         this.humSound = null;
      }

      if (wantHum && (this.humSound == null || this.humSound.isStopped())) {
         this.humSound = new PlasmaMode.Hum(own);
         this.mc.getSoundManager().play(this.humSound);
      }

      if (this.humSound != null) {
         this.humSound.target = wantHum ? (own ? 0.22F : 0.18F) * v : 0.0F;
         if (!wantHum && this.humSound.level() <= 0.002F) {
            this.humSound.halt();
            this.humSound = null;
         }
      }
   }

   @Override
   protected void onDisable() {
      if (this.humSound != null) {
         this.humSound.halt();
         this.humSound = null;
      }

      this.holding = false;
   }

   public void render(PoseStack matrices, SubmitNodeCollector queue, boolean leftHanded, boolean firstPerson, boolean localPlayer, float swing) {
      this.render(matrices, queue, leftHanded, firstPerson, localPlayer, swing, this.modelId());
   }

   private void render(PoseStack matrices, SubmitNodeCollector queue, boolean leftHanded, boolean firstPerson, boolean localPlayer, float swing, int modelId) {
      float extend = 1.0F;
      if (localPlayer && this.ignite.get()) {
         float t = (float)(System.currentTimeMillis() - this.igniteStart) / 260.0F;
         extend = Math.max(0.0F, Math.min(1.0F, t));
         extend = 1.0F - (1.0F - extend) * (1.0F - extend);
      }

      PlasmaSword.render(
         matrices,
         queue,
         leftHanded,
         new PlasmaSword.Look(
            this.color.get() & 16777215,
            (float)(this.glow.get() / 100.0),
            0.95F * (float)(this.length.get() / 100.0),
            extend,
            swing,
            this.trail.get(),
            firstPerson,
            modelId,
            PlasmaSword.finishId(this.finish.get())
         )
      );
   }

   private static final class Hum extends AbstractTickableSoundInstance {
      float target;
      final boolean own;

      Hum(boolean own) {
         super(own ? PlasmaMode.HUM : SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, RandomSource.create());
         this.own = own;
         this.looping = true;
         this.delay = 0;
         this.attenuation = Attenuation.NONE;
         this.relative = true;
         this.pitch = own ? 1.0F : 1.6F;
         this.volume = 0.001F;
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         this.volume = Math.max(0.0F, this.volume + (this.target - this.volume) * 0.15F);
      }

      float level() {
         return this.volume;
      }

      void halt() {
         this.stop();
      }
   }
}
