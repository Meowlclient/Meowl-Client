package com.lennart.nebula;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.util.WorldCtx;

import com.lennart.nebula.afk.SitPose;
import com.lennart.nebula.api.NebulaAddon;
import com.lennart.nebula.config.ConfigManager;
import com.lennart.nebula.gui.MeowlScreen;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.hud.ArmorHud;
import com.lennart.nebula.module.hud.Crosshair;
import com.lennart.nebula.module.hud.CrosshairAddons;
import com.lennart.nebula.module.hud.Saturation;
import com.lennart.nebula.module.hud.SpotifyOverlay;
import com.lennart.nebula.module.hud.ToggleSprint;
import com.lennart.nebula.module.hud.TotemCounter;
import com.lennart.nebula.module.mods.ArmorTrimPreview;
import com.lennart.nebula.module.mods.BindCommands;
import com.lennart.nebula.module.mods.BetterChat;
import com.lennart.nebula.module.mods.Blackjack;
import com.lennart.nebula.module.mods.BlockImages;
import com.lennart.nebula.module.mods.Chess;
import com.lennart.nebula.module.mods.TicTacToe;
import com.lennart.nebula.module.mods.Clap;
import com.lennart.nebula.module.mods.ClientSettings;
import com.lennart.nebula.module.mods.DeathAnimation;
import com.lennart.nebula.module.mods.DesertAfk;
import com.lennart.nebula.module.mods.DiscordPresence;
import com.lennart.nebula.module.mods.Emotes;
import com.lennart.nebula.module.mods.EnchantCalculator;
import com.lennart.nebula.module.mods.Facecam;
import com.lennart.nebula.module.mods.FriendLocator;
import com.lennart.nebula.module.mods.Friends;
import com.lennart.nebula.module.mods.ItemBeam;
import com.lennart.nebula.module.mods.ItemHighlighter;
import com.lennart.nebula.module.mods.MeowlPet;
import com.lennart.nebula.module.mods.MeowlServerModule;
import com.lennart.nebula.module.mods.NetherCoords;
import com.lennart.nebula.module.mods.NoHotbarScroll;
import com.lennart.nebula.module.mods.Notes;
import com.lennart.nebula.module.mods.Paint;
import com.lennart.nebula.module.mods.PlasmaMode;
import com.lennart.nebula.module.mods.SheriffDuel;
import com.lennart.nebula.module.mods.ShulkerTooltips;
import com.lennart.nebula.module.mods.Tiers;
import com.lennart.nebula.module.mods.TotemInspect;
import com.lennart.nebula.module.mods.Watches;
import com.lennart.nebula.module.mods.Wings;
import com.lennart.nebula.module.mods.Waypoints;
import com.lennart.nebula.module.render.ColorSaturation;
import com.lennart.nebula.module.render.Fullbright;
import com.lennart.nebula.module.sound.SoundController;
import com.lennart.nebula.nametag.Nametags;
import com.lennart.nebula.nametag.OwnNametag;
import com.lennart.nebula.totem.TotemPopTracker;
import com.lennart.nebula.util.BaulooStylesBridge;
import com.lennart.nebula.watch.WatchFeature;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NebulaClient implements ClientModInitializer {
   public static final String MOD_ID = "nebula";
   public static final String NAME = "Meowl";
   public static final Logger LOGGER = LoggerFactory.getLogger("Meowl");
   private static NebulaClient instance;
   private ModuleManager moduleManager;
   private ConfigManager configManager;
   private KeyMapping openGuiKey;
   private KeyMapping baulooKey;

   @Override
   public void onInitializeClient() {
      instance = this;
      this.moduleManager = new ModuleManager();
      this.moduleManager.register(new Fullbright());
      this.moduleManager.register(new ColorSaturation());
      this.moduleManager.register(new ItemHighlighter());
      this.moduleManager.register(new MeowlPet());
      this.moduleManager.register(new BindCommands());
      this.moduleManager.register(new Crosshair());
      this.moduleManager.register(new CrosshairAddons());
      this.moduleManager.register(new ToggleSprint());
      this.moduleManager.register(new SoundController());
      this.moduleManager.register(new Tiers());
      this.moduleManager.register(new Nametags());
      this.moduleManager.register(new OwnNametag());
      this.moduleManager.register(new com.lennart.nebula.module.mods.ThirdPersonNametag());
      this.moduleManager.register(new ArmorHud());
      this.moduleManager.register(new TotemCounter());
      this.moduleManager.register(new Saturation());
      this.moduleManager.register(new Notes());
      this.moduleManager.register(new MeowlServerModule());
      this.moduleManager.register(new BlockImages());
      this.moduleManager.register(new Facecam());
      this.moduleManager.register(new SheriffDuel());
      this.moduleManager.register(new ItemBeam());
      this.moduleManager.register(new TotemInspect());
      this.moduleManager.register(new Clap());
      this.moduleManager.register(new Blackjack());
      this.moduleManager.register(new DesertAfk());
      this.moduleManager.register(new Emotes());
      this.moduleManager.register(new Waypoints());
      this.moduleManager.register(new Friends());
      this.moduleManager.register(new FriendLocator());
      this.moduleManager.register(new ArmorTrimPreview());
      this.moduleManager.register(new com.lennart.nebula.module.mods.ChatEmojisModule());
      this.moduleManager.register(new PlasmaMode());
      this.moduleManager.register(new Watches());
      this.moduleManager.register(new DiscordPresence());
      this.moduleManager.register(new NoHotbarScroll());
      this.moduleManager.register(new EnchantCalculator());
      this.moduleManager.register(new com.lennart.nebula.module.mods.BrewingHelper());
      this.moduleManager.register(new com.lennart.nebula.module.mods.ProfilePicture());
      this.moduleManager.register(new com.lennart.nebula.module.mods.BlockPaletteTool());
      this.moduleManager.register(new com.lennart.nebula.module.mods.SoundRadar());
      this.moduleManager.register(new com.lennart.nebula.module.hud.Streaming());
      this.moduleManager.register(new com.lennart.nebula.module.mods.Replay());
      this.moduleManager.register(new NetherCoords());
      this.moduleManager.register(new SpotifyOverlay());
      this.moduleManager.register(new DeathAnimation());
      this.moduleManager.register(new ShulkerTooltips());
      this.moduleManager.register(new BetterChat());
      this.moduleManager.register(new TicTacToe());
      this.moduleManager.register(new Chess());
      this.moduleManager.register(new Paint());
      this.moduleManager.register(new com.lennart.nebula.module.hud.InventoryHud());
      this.moduleManager.register(new com.lennart.nebula.module.mods.Hydration());
      this.moduleManager.register(new com.lennart.nebula.module.render.ShieldStyle());
      this.moduleManager.register(new com.lennart.nebula.module.render.ArmorView());
      this.moduleManager.register(new com.lennart.nebula.module.render.HitColor());
      this.moduleManager.register(new com.lennart.nebula.module.render.TotemSize());
      this.moduleManager.register(new com.lennart.nebula.module.render.Hitboxes());
      this.moduleManager.register(new com.lennart.nebula.module.render.Particles());
      this.moduleManager.register(new com.lennart.nebula.module.mods.OtherClients());
      this.moduleManager.register(new com.lennart.nebula.module.mods.Zoom());
      this.moduleManager.register(new com.lennart.nebula.module.mods.Freelook());
      this.moduleManager.register(new com.lennart.nebula.module.mods.Tpa());
      this.moduleManager.register(new com.lennart.nebula.module.mods.Performance());
      com.lennart.nebula.cosmetic.mods.CosmeticModules.registerAll(this.moduleManager);
      this.moduleManager.register(new ClientSettings());

      for (EntrypointContainer<NebulaAddon> addon : FabricLoader.getInstance().getEntrypointContainers("nebula", NebulaAddon.class)) {
         try {
            addon.getEntrypoint().registerModules(this.moduleManager);
            LOGGER.info("Addon geladen: {}", addon.getProvider().getMetadata().getName());
         } catch (RuntimeException | LinkageError var4) {
            LOGGER.error("Addon {} konnte nicht geladen werden (passt evtl. nicht zu dieser Version)", addon.getProvider().getMetadata().getId(), var4);
         }
      }

      LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
         if (renderer instanceof AvatarRenderer<?> player) {
            helper.register(new WatchFeature(player));
            helper.register(new com.lennart.nebula.cosmetic.CosmeticFeature(player));
         }
      });
      this.configManager = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("nebula.json"), this.moduleManager);
      this.configManager.load();
      com.lennart.nebula.module.mods.ClientSettings.enableViewersOnce(this.moduleManager);
      this.configManager.save();
      this.moduleManager.setChangeListener(this.configManager::save);
      Category category = Category.register(Identifier.fromNamespaceAndPath("nebula", "main"));
      this.openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.open_gui", Type.KEYBOARD, InputConstants.KEY_RSHIFT, category));
      this.baulooKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.bauloo_styles", Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
      // Emotes und Inspect: in den Minecraft-Steuerungen einstellbar
      emoteWheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.emote_wheel", Type.KEYBOARD, InputConstants.KEY_B, category));
      emoteStopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.emote_stop", Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
      inspectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.inspect", Type.KEYBOARD, InputConstants.KEY_G, category));
      com.lennart.nebula.gui.MeowlTitleScreen.registerHooks();
      ClientTickEvents.START_CLIENT_TICK.register(client -> this.moduleManager.onPreTick(client));
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         while (this.openGuiKey.consumeClick()) {
            if (client.gui.screen() == null) {
               client.gui.setScreen(new MeowlScreen(this.moduleManager, this.configManager));
            }
         }

         while (this.baulooKey.consumeClick()) {
            if (client.gui.screen() == null) {
               BaulooStylesBridge.open(null);
            }
         }

         TotemPopTracker.tick(client);
         com.lennart.nebula.wings.WingsParticles.tick(client);
         com.lennart.nebula.cosmetic.Cosmetics.tick(client);
         if (client.player == null) {
            SitPose.reset();
         }

         DesertAfk afk = DesertAfk.get();
         if (afk != null) {
            afk.tickAlways(client);
         }

         DiscordPresence discord = this.moduleManager.get(DiscordPresence.class);
         if (discord != null) {
            discord.tickAlways();
         }

         this.moduleManager.onTick(client);
      });
      HudElementRegistry.addLast(
         Identifier.fromNamespaceAndPath("nebula", "modules"),
         (context, tickCounter) -> this.moduleManager.onRenderHud(Minecraft.getInstance(), context, tickCounter.getGameTimeDeltaPartialTick(true))
      );
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nebula", "afk_scene"), (context, tickCounter) -> {
         DesertAfk afk = DesertAfk.get();
         if (afk != null) {
            afk.renderOverlay(context);
         }
      });
      HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (context, tickCounter) -> {
         Crosshair crosshair = this.moduleManager.get(Crosshair.class);
         if (crosshair == null || !crosshair.isEnabled() || !crosshair.render(context)) {
            vanilla.extractRenderState(context, tickCounter);
         }
      });
      HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath("nebula", "saturation"), (context, tickCounter) -> {
         Saturation saturation = this.moduleManager.get(Saturation.class);
         if (saturation != null) {
            saturation.renderFoodOverlay(context);
         }
      });
      WorldCtx.afterEntities(context -> {
         Minecraft client = Minecraft.getInstance();
         this.moduleManager.onRenderWorld(client, context, client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
      });
      ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
         if (screen instanceof AbstractContainerScreen<?> handled) {
            ScreenEvents.afterExtract(screen).register((s, context, mouseX, mouseY, tickDelta) -> ItemHighlighter.renderOverlay(handled, context));
         }

         com.lennart.nebula.gui.MenuScaleWidget.attach(screen);
         if (screen instanceof net.minecraft.client.gui.screens.ChatScreen chat && com.lennart.nebula.module.mods.ChatEmojisModule.button()) {
            com.lennart.nebula.chat.ChatEmojiPicker.attach(chat);
         }
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
         this.configManager.save();
         Notes notes = this.moduleManager.get(Notes.class);
         if (notes != null) {
            notes.saveNow();
         }
      });
      LOGGER.info("{} geladen ({} Module)", "Meowl", this.moduleManager.getModules().size());
   }

   public static NebulaClient getInstance() {
      return instance;
   }

   public ModuleManager getModuleManager() {
      return this.moduleManager;
   }

   public static KeyMapping emoteWheelKey;
   public static KeyMapping emoteStopKey;
   public static KeyMapping inspectKey;

   /** Gerade gedrückt (auch wenn die Taste nichts belegt hat: false)? */
   public static boolean down(KeyMapping key) {
      return key != null && key.isDown();
   }

   /** Tastaturcode einer Taste (für „Taste halten“), sonst -1. */
   public static int keyCode(KeyMapping key) {
      if (key == null) {
         return -1;
      }

      InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(key);
      return k.getType() == Type.KEYBOARD ? k.getValue() : -1;
   }

   public KeyMapping getOpenGuiKey() {
      return this.openGuiKey;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }
}
