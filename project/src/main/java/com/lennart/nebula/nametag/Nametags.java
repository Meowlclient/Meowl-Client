package com.lennart.nebula.nametag;

import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.NametagEditorScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.hud.TotemCounter;
import com.lennart.nebula.module.mods.Friends;
import com.lennart.nebula.module.mods.MeowlPet;
import com.lennart.nebula.module.mods.Tiers;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.Team.Visibility;

public class Nametags extends Module {
   private static final String LEFT = "Links";
   private static final String RIGHT = "Rechts";
   private static final String ABOVE = "Über dem Namen";
   private final BooleanSetting meowl = this.add(new BooleanSetting("Meowl-Symbol", "Kleine Meowl neben dem Namen von Spielern mit Meowl Client", true));
   private final ModeSetting meowlSide = this.add(
      new ModeSetting("Meowl-Position", "Alt – jetzt im Nametag-Editor", "Links", "Links", "Rechts", "Über dem Namen")
   );
   private final TextSetting layout = this.add(new TextSetting("Layout", "Aufbau der Namenszeile (Nametag-Editor)", "", 200));
   private final NumberSetting scale = this.add(new NumberSetting("Größe", "Größe der ganzen Namenszeile (inkl. Name)", 1.0, 0.5, 2.0, 0.05, "x"));
   private final NumberSetting yOffset = this.add(
      new NumberSetting("Höhe", "Namenszeile nach oben (minus) / unten (plus) verschieben", 0.0, -30.0, 30.0, 1.0, "px")
   );
   private NametagConfig cachedConfig;
   private String cachedConfigText;
   private final NumberSetting meowlSize = this.add(new NumberSetting("Meowl-Größe", "Größe des Symbols", 9.0, 6.0, 16.0, 1.0, "px"));
   private final BooleanSetting meowlSelf = this.add(new BooleanSetting("Meowl bei mir", "Symbol auch an deinem eigenen Namen (Modul „Eigener Nametag“)", true));
   private final ModeSetting align = this.add(
      new ModeSetting(
         "Ausrichtung",
         "Ganze Zeile symmetrisch mittig über dem Kopf, oder der Name mittig wie bei Vanilla",
         "Ganze Zeile mittig",
         "Ganze Zeile mittig",
         "Name mittig"
      )
   );
   private final NumberSetting gap = this.add(new NumberSetting("Abstand zum Namen", "Abstand der Symbole/Tiers zum Namen", 3.0, 1.0, 12.0, 1.0, "px"));
   private final BooleanSetting showWhenHidden = this.add(
      new BooleanSetting(
         "Auch bei verstecktem Namen",
         "Viele Server blenden den Vanilla-Namen aus (eigene Nametags). Meowl-Zeichen und Tiers trotzdem neben dem Namen zeigen",
         true
      )
   );
   private final BooleanSetting drawHiddenName = this.add(
      new BooleanSetting(
         "Versteckten Namen selbst zeigen", "Blendet der Server den Namen aus, zeigt Meowl ihn selbst (sonst nur Symbole links/rechts vom Namensplatz)", true
      )
   );

   private final BooleanSetting ownOn = this.add(new BooleanSetting("Eigener Nametag", "Dein eigener Nametag in F5 – genau so, wie ihn andere über dir sehen (Server-Farben, Ränge, Teams), mit Meowl-Symbol und Tiers", false));
   private final BooleanSetting ownMigrated = this.add(new BooleanSetting("Eigener Nametag übernommen", "", false));
   private final BooleanSetting thirdMigrated = this.add(new BooleanSetting("3rd Person übernommen", "", false));
   private final BooleanSetting layoutV2 = this.add(new BooleanSetting("Layout 2", "", false));
   private final BooleanSetting layoutV3 = this.add(new BooleanSetting("Layout 3", "", false));
   // Stil für die Namen anderer Spieler (wie beim eigenen Nametag)
   private final BooleanSetting othersStyled = this.add(
      new BooleanSetting("Eigener Stil", "Namen anderer Spieler mit den Einstellungen hier zeichnen (aus = wie Vanilla)", true)
   );
   private final ColorSetting nameColor = this.add(new ColorSetting("Namensfarbe", "Farbe der Namen (Teamfarben vom Server bleiben erhalten)", -1, false));
   private final NumberSetting nameOpacity = this.add(new NumberSetting("Namens-Deckkraft", "Wie deckend die Namen sind", 100.0, 10.0, 100.0, 5.0, "%"));
   private final BooleanSetting nameBackground = this.add(new BooleanSetting("Namens-Hintergrund", "Dunkler Kasten hinter dem Namen", true));
   private final ColorSetting nameBackgroundColor = this.add(new ColorSetting("Hintergrundfarbe", "Farbe/Deckkraft des Kastens", 1073741824));
   private final BooleanSetting nameOutline = this.add(new BooleanSetting("Schriftrand", "Schrift mit farbigem Rand (statt Schatten)", false));
   private final ColorSetting nameOutlineColor = this.add(new ColorSetting("Schriftrand-Farbe", "Farbe des Schriftrands", -16777216, false));
   private final BooleanSetting nameShadow = this.add(new BooleanSetting("Schriftschatten", "Schrift mit Schatten", false));
   private final BooleanSetting replaceServer = this.add(
      new BooleanSetting(
         "Server-Nametags ersetzen",
         "Zeigt der Server Namen über eigene Text-Objekte an, blendet Meowl sie aus und zeichnet den Nametag mit deinen Einstellungen",
         true
      )
   );
   private final BooleanSetting tabIcon = this.add(
      new BooleanSetting("Meowl-Symbol in Tab", "Meowl-Symbol vor Spielern mit Meowl Client in der Tab-Liste", true)
   );
   private final BooleanSetting stackOnGraphics = this.add(
      new BooleanSetting(
         "Bei Server-Grafiken nach oben",
         "An: Hat der Server-Name eigene Bilder (Ränge), wandern Meowl-Symbol und Tiers in eine Zeile darüber. Aus: Sie bleiben dort, wo du sie im Editor eingestellt hast",
         false
      )
   );
   private final NumberSetting serverGap = this.add(
      new NumberSetting(
         "Abstand zu Server-Grafiken",
         "Haben Server-Namen eigene Bilder/Texturen, kommen Meowl-Symbole und Tiers als Zeile darüber – so viel Abstand",
         10.0,
         0.0,
         40.0,
         1.0,
         "px"
      )
   );

   public Nametags() {
      super("Nametags", "Nametags für alle Spieler und deinen eigenen Namen (F5): Meowl-Symbol, Tiers, Pops und Freunde – sauber und symmetrisch am Namen.", Category.RENDER);
      this.icon(Items.NAME_TAG);
      this.ownMigrated.setVisibility(() -> false);
      this.thirdMigrated.setVisibility(() -> false);
      this.layoutV2.setVisibility(() -> false);
      this.layoutV3.setVisibility(() -> false);
      ClientTickEvents.END_CLIENT_TICK.register(c -> this.syncOwn());

      for (Setting<?> st : new Setting[]{
         this.meowl,
         this.meowlSide,
         this.meowlSize,
         this.meowlSelf,
         this.gap,
         this.showWhenHidden,
         this.layout
      }) {
         st.setVisibility(() -> false);
      }

      this.group(
         "Andere Spieler",
         new Setting[]{
            this.align,
            this.othersStyled,
            this.nameColor,
            this.nameOpacity,
            this.nameBackground,
            this.nameBackgroundColor,
            this.nameOutline,
            this.nameOutlineColor,
            this.nameShadow,
            this.scale,
            this.yOffset,
            this.drawHiddenName,
            this.replaceServer,
            this.stackOnGraphics,
            this.serverGap,
            this.tabIcon
         }
      );
      this.replaceServer.setVisibility(this.othersStyled::get);

      for (Setting<?> st : new Setting[]{this.nameColor, this.nameOpacity, this.nameBackground, this.nameOutline}) {
         st.setVisibility(this.othersStyled::get);
      }

      this.nameBackgroundColor.setVisibility(() -> this.othersStyled.get() && this.nameBackground.get());
      this.nameOutlineColor.setVisibility(() -> this.othersStyled.get() && this.nameOutline.get());
      this.nameShadow.setVisibility(() -> this.othersStyled.get() && !this.nameOutline.get());

      this.add(
         new ActionSetting(
            "Editor",
            "Nametags per Drag & Drop mit Vorschau einstellen",
            "Öffnen",
            () -> this.mc.schedule(() -> this.mc.gui.setScreen(new NametagEditorScreen(this.mc.gui.screen())))
         )
      );
      MeowlCommands.add(
         "info",
         "info <spieler> §7– Meowl-Status und Tiers eines Spielers",
         name -> ClientCommands.literal(name).then(ClientCommands.argument("spieler", StringArgumentType.word()).executes(ctx -> {
            String n = StringArgumentType.getString(ctx, "spieler");
            this.mc.schedule(() -> this.printInfo(n));
            return 1;
         }))
      );
      this.setEnabled(true);
   }

   private OwnNametag own() {
      NebulaClient c = NebulaClient.getInstance();
      return c == null ? null : c.getModuleManager().get(OwnNametag.class);
   }

   private com.lennart.nebula.module.mods.ThirdPersonNametag third() {
      NebulaClient c = NebulaClient.getInstance();
      return c == null ? null : c.getModuleManager().get(com.lennart.nebula.module.mods.ThirdPersonNametag.class);
   }

   /**
    * Eigener Nametag (zusammengelegt): der Schalter hier zeigt deinen echten Nametag in F5 wie „3rd Person Nametag“; der
    * alte, selbst gezeichnete „Eigene Nametag“ bleibt aus, damit nichts doppelt ist.
    */
   private void syncOwn() {
      OwnNametag own = this.own();
      if (own != null && own.isEnabled()) {
         own.setEnabled(false);
      }

      com.lennart.nebula.module.mods.ThirdPersonNametag third = this.third();
      if (third != null && third.isEnabled() != this.ownOn.get()) {
         third.setEnabled(this.ownOn.get());
      }
   }
   /** Namen anderer Spieler im eigenen Stil zeichnen? */
   public boolean othersStyled() {
      return this.isEnabled() && this.othersStyled.get();
   }

   // Zugriff für den Nametag-Editor (Bereich „Andere Spieler“)
   public BooleanSetting othersStyledSetting() {
      return this.othersStyled;
   }

   public ColorSetting nameColorSetting() {
      return this.nameColor;
   }

   public NumberSetting nameOpacitySetting() {
      return this.nameOpacity;
   }

   public BooleanSetting nameBackgroundSetting() {
      return this.nameBackground;
   }

   public BooleanSetting nameOutlineSetting() {
      return this.nameOutline;
   }

   public ColorSetting nameOutlineColorSetting() {
      return this.nameOutlineColor;
   }

   public BooleanSetting nameShadowSetting() {
      return this.nameShadow;
   }

   public BooleanSetting replaceServerSetting() {
      return this.replaceServer;
   }

   /** Server-Nametags (Text-Objekte) durch den eigenen Meowl-Nametag ersetzen? */
   public boolean replaceServerTags() {
      return this.othersStyled() && this.replaceServer.get();
   }

   public NametagStyle.Look othersLook(boolean fullBackground) {
      int a = Math.round(255.0F * (float)(this.nameOpacity.get() / 100.0));
      return new NametagStyle.Look(
         a << 24 | this.nameColor.get() & 16777215,
         this.nameBackground.get(),
         this.nameBackgroundColor.get(),
         this.nameOutline.get(),
         this.nameOutlineColor.get(),
         this.nameShadow.get(),
         fullBackground && this.nameBackground.get()
      );
   }

   public boolean ownEnabled() {
      return this.ownOn.get();
   }

   public void toggleOwn() {
      this.ownOn.set(!this.ownOn.get());
      this.syncOwn();
      NebulaClient.getInstance().getModuleManager().notifyChanged();
   }

   private static final net.minecraft.network.chat.Component TAB_ICON = net.minecraft.network.chat.Component.empty()
      .append(
         net.minecraft.network.chat.Component.literal("")
            .withStyle(s -> s.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("nebula", "icons"))))
      )
      // Leerzeichen in normaler Schrift (die Icon-Schrift hat kein Leerzeichen -> weißer Kasten)
      .append(" ");

   /** Meowl-Symbol für die Tab-Liste (null = keins). */
   public static net.minecraft.network.chat.Component tabIcon(java.util.UUID uuid, String name) {
      Nametags tags = get();
      if (tags == null || !tags.isEnabled() || !tags.tabIcon.get() || uuid == null) {
         return null;
      }

      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      boolean self = mc.player != null && uuid.equals(mc.player.getUUID());
      com.lennart.nebula.module.mods.MeowlPet pet = NebulaClient.getInstance().getModuleManager().get(com.lennart.nebula.module.mods.MeowlPet.class);
      boolean meowl = self || pet != null && pet.isMeowlUser(uuid, name);
      return meowl ? TAB_ICON : null;
   }

   /** Sollen Symbole bei Server-Grafiken über den Namen ausweichen? */
   public boolean stackOnGraphics() {
      return this.stackOnGraphics.get();
   }

   /** Abstand (px), wenn ein Server-Name eigene Grafiken hat. */
   public float serverGraphicsGap() {
      return (float)this.serverGap.get().doubleValue();
   }

   public static Nametags get() {
      NebulaClient c = NebulaClient.getInstance();
      return c == null ? null : c.getModuleManager().get(Nametags.class);
   }

   public boolean meowlActive() {
      return this.isEnabled() && this.meowl.get();
   }

   public boolean meowlForSelf() {
      return this.meowlActive() && this.meowlSelf.get();
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new NametagEditorScreen(parent);
   }

   public NametagConfig config() {
      String text = this.layout.get();
      if (this.cachedConfig == null || !text.equals(this.cachedConfigText)) {
         this.cachedConfig = NametagConfig.parse(text.isBlank() ? NametagConfig.DEFAULT : text);
         this.cachedConfigText = text;
      }

      return this.cachedConfig;
   }

   public void setConfig(NametagConfig config) {
      this.layout.set(config.serialize());
   }

   @Override
   public void onConfigLoaded() {
      // Einmalig: bisherigen An/Aus-Zustand von „Eigener Nametag“ übernehmen
      if (!this.ownMigrated.get()) {
         OwnNametag own = this.own();
         if (own != null) {
            this.ownOn.set(own.isEnabled());
         }

         this.ownMigrated.set(true);
      }

      // Einmalig: war „3rd Person Nametag“ an, bleibt der eigene Nametag an
      if (!this.thirdMigrated.get()) {
         com.lennart.nebula.module.mods.ThirdPersonNametag third = this.third();
         if (third != null && third.isEnabled()) {
            this.ownOn.set(true);
         }

         this.thirdMigrated.set(true);
      }

      this.syncOwn();
      // Einmalig auf den neuen Standard umstellen: [Meowl] [Icon] LT4 | Name | LT5 [Icon], ohne Freunde-Stern
      if (!this.layoutV2.get()) {
         this.setConfig(NametagConfig.parse(NametagConfig.DEFAULT));
         Tiers tiers = NebulaClient.getInstance().getModuleManager().get(Tiers.class);
         if (tiers != null) {
            tiers.useOutsideIcons();
         }

         this.layoutV2.set(true);
      }

      // Einmalig: versteckte Server-Namen selbst zeigen, damit Nametags anderer Spieler wie der eigene aussehen
      if (!this.layoutV3.get()) {
         this.drawHiddenName.set(true);
         this.layoutV3.set(true);
      }

      if (this.layout.get().isBlank()) {
         NametagConfig c = NametagConfig.parse(NametagConfig.DEFAULT);
         if (this.meowlSide.is("Rechts")) {
            c.move(NametagConfig.Element.MEOWL, NametagConfig.Zone.RIGHT, 99);
         } else if (this.meowlSide.is("Über dem Namen")) {
            c.move(NametagConfig.Element.MEOWL, NametagConfig.Zone.ABOVE, 0);
         }

         Tiers tiers = NebulaClient.getInstance().getModuleManager().get(Tiers.class);
         if (tiers != null && tiers.legacyPlacedAbove()) {
            c.move(NametagConfig.Element.TIER_1, NametagConfig.Zone.HIDDEN, 0);
            c.move(NametagConfig.Element.TIER_2, NametagConfig.Zone.HIDDEN, 0);
            c.move(NametagConfig.Element.TIER_KITS, NametagConfig.Zone.ABOVE, 99);
         }

         this.setConfig(c);
      }
   }

   public float rowScale() {
      return (float)this.scale.get().doubleValue();
   }

   public float rowYOffset() {
      return this.yOffset.getInt();
   }

   public BooleanSetting meowlSetting() {
      return this.meowl;
   }

   public NumberSetting meowlSizeSetting() {
      return this.meowlSize;
   }

   public BooleanSetting meowlSelfSetting() {
      return this.meowlSelf;
   }

   public ModeSetting alignSetting() {
      return this.align;
   }

   public NumberSetting gapSetting() {
      return this.gap;
   }

   public BooleanSetting showWhenHiddenSetting() {
      return this.showWhenHidden;
   }

   public BooleanSetting drawHiddenNameSetting() {
      return this.drawHiddenName;
   }

   public NumberSetting scaleSetting() {
      return this.scale;
   }

   public NumberSetting yOffsetSetting() {
      return this.yOffset;
   }

   public float meowlSize() {
      return this.meowlSize.getInt();
   }

   public float gap() {
      return this.gap.getInt();
   }

   public boolean centerGroup() {
      return !this.align.is("Name mittig");
   }

   public boolean showWhenHidden() {
      return this.showWhenHidden.get();
   }

   public boolean drawHiddenName() {
      return this.drawHiddenName.get();
   }

   private void printInfo(String name) {
      if (this.mc.level != null) {
         Player p = null;

         for (Player pl : this.mc.level.players()) {
            if (pl.getGameProfile().name().equalsIgnoreCase(name)) {
               p = pl;
            }
         }

         if (p == null) {
            ChatUtil.chat("§c" + name + " ist nicht in deiner Nähe (muss geladen sein)");
         } else {
            ModuleManager mm = NebulaClient.getInstance().getModuleManager();
            ChatUtil.chat("§dNametag-Info für §f" + p.getGameProfile().name() + " §8(" + p.getUUID() + ")");
            ChatUtil.chat("§7Meowl Client erkannt: " + (isMeowlUser(mm, p) ? "§aja" : "§cnein §8(nicht im Meowl-Netzwerk gefunden)"));
            Team team = p.getTeam();
            String rule = team == null ? "kein Team" : team.getNameTagVisibility().name().toLowerCase(Locale.ROOT);
            ChatUtil.chat(
               "§7Server-Namensregel: §f"
                  + rule
                  + (
                     team != null && team.getNameTagVisibility() == Visibility.NEVER
                        ? " §8(Vanilla-Name versteckt → Meowl zeigt Symbole trotzdem)"
                        : ""
                  )
            );
            ChatUtil.chat(
               "§7Server-Nametag (eigenes Entity): " + (ServerNametags.hasServerNametag(p) ? "§agefunden – Symbole stehen auf seiner Höhe" : "§8keins")
            );
            Tiers tiers = mm.get(Tiers.class);
            if (tiers != null) {
               ChatUtil.chat("§7Tiers am Namen: " + (tiers.showsAtName() ? "§aan" : "§caus"));
               NametagPiece l = tiers.tierPiece(p, true, false);
               NametagPiece r = tiers.tierPiece(p, false, false);
               ChatUtil.chat("§7Liste 1: §f" + label(l) + "  §7Liste 2: §f" + label(r) + " §8(Tracker: /meowl tiers " + p.getGameProfile().name() + ")");
            }

            NametagLayout.Row row = collect(p, true, true);
            ChatUtil.chat("§7Elemente: links " + row.left().size() + ", rechts " + row.right().size() + ", darüber " + row.above().size());
         }
      }
   }

   public static NametagLayout.Row collect(Player player, boolean allowMeowl, boolean allowTiers) {
      return collect(player, allowMeowl, allowTiers, null);
   }

   public static NametagLayout.Row collect(Player player, boolean allowMeowl, boolean allowTiers, String visibleName) {
      Nametags self = get();
      List<NametagPiece> left = new ArrayList<>();
      List<NametagPiece> right = new ArrayList<>();
      List<NametagPiece> above = new ArrayList<>();
      if (self == null) {
         return new NametagLayout.Row(left, right, above, 3.0F, 0.0F);
      } else {
         ModuleManager mm = NebulaClient.getInstance().getModuleManager();
         Tiers tiers = mm.get(Tiers.class);
         NametagConfig cfg = self.config();

         for (NametagConfig.Zone zone : new NametagConfig.Zone[]{NametagConfig.Zone.LEFT, NametagConfig.Zone.RIGHT, NametagConfig.Zone.ABOVE}) {
            List<NametagPiece> out = zone == NametagConfig.Zone.LEFT ? left : (zone == NametagConfig.Zone.RIGHT ? right : above);

            for (NametagConfig.Element e : cfg.get(zone)) {
               self.pieces(e, zone, player, allowMeowl, allowTiers, visibleName, mm, tiers, out);
            }
         }

         // anderer Meowl-Spieler ist gerade im AFK-Bildschirm
         if (player != Minecraft.getInstance().player && com.lennart.nebula.module.mods.DesertAfk.showOthersAfk()) {
            com.lennart.nebula.util.MeowlNetwork.Peer peer = MeowlPet.peer(player);
            if (peer != null && peer.afk()) {
               above.add(0, TextPiece.of("meowl afk", 0xFFC9A6FF));
            }
         }

         float aboveOffset = tiers != null ? tiers.aboveOffset() : 0.0F;
         return new NametagLayout.Row(left, right, above, self.gap(), aboveOffset);
      }
   }

   private void pieces(
      NametagConfig.Element e,
      NametagConfig.Zone zone,
      Player player,
      boolean allowMeowl,
      boolean allowTiers,
      String visibleName,
      ModuleManager mm,
      Tiers tiers,
      List<NametagPiece> out
   ) {
      boolean active = this.isEnabled();
      boolean isSelf = player == Minecraft.getInstance().player;
      boolean tiersOn = active && allowTiers && tiers != null && tiers.showsAtName();
      NametagPiece p = null;
      switch (e) {
         case MEOWL:
            if (active && allowMeowl && this.meowl.get() && (isSelf ? this.meowlSelf.get() : isMeowlUser(mm, player))) {
               p = new MeowlPiece(this.meowlSize(), com.lennart.nebula.module.mods.ProfilePicture.iconFor(player));
            }
            break;
         case FRIEND:
            p = isSelf ? null : Friends.namePiece(player);
            break;
         case POPS:
            p = active ? TotemCounter.namePiece(player) : null;
            break;
         case VOICE:
            p = VoicePiece.predicts(player.getUUID()) ? new VoicePiece(player.getId()) : null;
            break;
         case TIER_1:
         case TIER_2:
            if (tiersOn) {
               p = withoutDuplicate(tiers.tierPiece(player, e == NametagConfig.Element.TIER_1, zone == NametagConfig.Zone.LEFT), visibleName);
            }
            break;
         case TIER_KITS:
            if (tiersOn) {
               for (NametagPiece k : tiers.abovePieces(player)) {
                  NametagPiece kept = withoutDuplicate(k, visibleName);
                  if (kept != null) {
                     out.add(kept);
                  }
               }
            }
      }

      if (p != null) {
         out.add(p);
      }
   }

   public NametagLayout.Row previewRow(NametagConfig cfg, BiFunction<NametagConfig.Element, NametagPiece, NametagPiece> tag) {
      ModuleManager mm = NebulaClient.getInstance().getModuleManager();
      Tiers tiers = mm.get(Tiers.class);
      Friends friends = Friends.get();
      TotemCounter totem = mm.get(TotemCounter.class);
      List<List<NametagPiece>> lists = new ArrayList<>();

      for (NametagConfig.Zone zone : new NametagConfig.Zone[]{NametagConfig.Zone.LEFT, NametagConfig.Zone.RIGHT, NametagConfig.Zone.ABOVE}) {
         List<NametagPiece> out = new ArrayList<>();

         for (NametagConfig.Element e : cfg.get(zone)) {
            Object var10000 = switch (e) {
               case MEOWL -> this.meowl.get() ? new MeowlPiece(this.meowlSize()) : null;
               case FRIEND -> friends != null && friends.marking() ? TextPiece.of("★", friends.markColorSetting().get()) : null;
               case POPS -> totem != null && totem.isEnabled() && totem.popsInName() ? TextPiece.of("[5 Pops]", totem.popsColorSetting().get()) : null;
               case VOICE -> com.lennart.nebula.compat.VoiceChatCompat.loaded() ? TextPiece.of("♪", 0xFF5BE38A) : null;
               case TIER_1, TIER_2 -> tiers != null && tiers.nametagSetting().get()
                  ? tiers.samplePiece(e == NametagConfig.Element.TIER_1, zone == NametagConfig.Zone.LEFT)
                  : null;
               case TIER_KITS -> {
                  if (tiers != null && tiers.nametagSetting().get()) {
                     List<NametagPiece> kits = tiers.sampleKitPieces();
                     NametagPiece group = new Nametags.GroupPiece(kits);
                     yield kits.isEmpty() ? null : group;
                  } else {
                     yield null;
                  }
               }
            };

            NametagPiece p = (NametagPiece)var10000;
            if (p == null) {
               p = TextPiece.of("(" + e.label + " aus)", -2139062144);
            }

            out.add(tag.apply(e, p));
         }

         lists.add(out);
      }

      return new NametagLayout.Row(lists.get(0), lists.get(1), lists.get(2), this.gap(), tiers != null ? tiers.aboveOffset() : 0.0F);
   }

   private static String label(NametagPiece p) {
      return p instanceof TierTextPiece tx ? tx.label() : (p instanceof TierPiece t ? t.label() : "–");
   }

   private static NametagPiece withoutDuplicate(NametagPiece piece, String visibleName) {
      if (piece != null && visibleName != null) {
         String label = piece instanceof TierTextPiece tx ? tx.label() : (piece instanceof TierPiece t ? t.label().replace("[", "").replace("]", "") : null);
         return label != null && ServerNametags.containsName(visibleName, label) ? null : piece;
      } else {
         return piece;
      }
   }

   public static boolean hasAboveLine(Player player) {
      return !collect(player, true, true).above().isEmpty();
   }

   private static boolean isMeowlUser(ModuleManager mm, Player player) {
      MeowlPet pet = mm.get(MeowlPet.class);
      return pet != null && pet.isMeowlUser(player.getUUID(), player.getGameProfile().name());
   }

   record GroupPiece(List<NametagPiece> pieces) implements NametagPiece {
      @Override
      public float width(Font tr) {
         return NametagLayout.width(tr, this.pieces);
      }

      @Override
      public void draw(Font tr, NameCanvas canvas, float x, float y) {
         for (NametagPiece p : this.pieces) {
            p.draw(tr, canvas, x, y);
            x += p.width(tr) + 3.0F;
         }
      }
   }
}
