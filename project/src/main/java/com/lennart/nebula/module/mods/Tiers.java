package com.lennart.nebula.module.mods;

import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.gui.NametagEditorScreen;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.gui.TiersTrackerScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import com.lennart.nebula.nametag.NametagIcons;
import com.lennart.nebula.nametag.NametagPiece;
import com.lennart.nebula.nametag.TierPiece;
import com.lennart.nebula.nametag.TierSidePiece;
import com.lennart.nebula.nametag.TierTextPiece;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.tiers.KitIcons;
import com.lennart.nebula.tiers.NameLookup;
import com.lennart.nebula.tiers.TierCache;
import com.lennart.nebula.tiers.TierEntry;
import com.lennart.nebula.tiers.TierManager;
import com.lennart.nebula.tiers.TierProfile;
import com.lennart.nebula.tiers.TierProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class Tiers extends Module {
   private static final String BEST = "Bester Tier";
   private static final String MULTI = "Mehrere Kits";
   private static final String PER_LIST = "Je Tierlist";
   private static final String KIT = "Bestimmtes Kit";
   private static final String ABOVE = "Über dem Namen";
   private static final String BESIDE = "Neben dem Namen";
   private static final String SIDE_OFF = "Aus";
   private static final String SIDE_BEST = "Bester (alle)";
   private final TierManager manager = new TierManager();
   private final NameLookup names = new NameLookup();
   private final List<BooleanSetting> listToggles = new ArrayList<>();
   private final BooleanSetting nametag = this.add(
      new BooleanSetting("Tiers am Namen", "Tiers direkt am Namensschild anderer Spieler", true).alias(new String[]{"Am Namen"})
   );
   private final ModeSetting placement = this.add(
      new ModeSetting("Position", "Links/rechts neben dem Namen oder als eigene Zeile darüber", "Neben dem Namen", "Neben dem Namen", "Über dem Namen")
   );
   private final ModeSetting leftSystem;
   private final TextSetting leftKit = this.add(
      new TextSetting("Links: Kit", "Leer = bester Tier. Sonst Kit-Name, z.B. sword, crystal, mace, pot, smp", "", 24)
   );
   private final ModeSetting rightSystem;
   private final TextSetting rightKit = this.add(
      new TextSetting("Rechts: Kit", "Leer = bester Tier. Sonst Kit-Name, z.B. sword, crystal, mace, pot, smp", "", 24)
   );
   private final ModeSetting kitMode = this.add(
      new ModeSetting(
         "Anzeige", "Welche Tiers in der Zeile über dem Namen stehen", "Mehrere Kits", "Bester Tier", "Mehrere Kits", "Je Tierlist", "Bestimmtes Kit"
      )
   );
   private final NumberSetting count = this.add(new NumberSetting("Anzahl", "Wie viele Tiers höchstens nebeneinander", 3.0, 1.0, 8.0, 1.0, ""));
   private final TextSetting kit = this.add(new TextSetting("Kit", "Kit-Name, z.B. sword, axe, pot, smp, uhc, mace, crystal", "sword", 24));
   private final ModeSetting nameStyle = this.add(
      new ModeSetting("Stil am Namen", "Wie Tiers am Namen aussehen", "Icon außen", "Icon außen", "Wie Server-Nametag", "Mit Icons")
   );
   private final BooleanSetting listIcons = this.add(
      new BooleanSetting("Tierlist-Icons", "Logo der Tierlist vor dem Tier (MCPVP-Pokal, PvPTiers, Subtiers)", true)
   );
   private final BooleanSetting kitIcons = this.add(new BooleanSetting("Kit-Icons", "Symbol des Kits vor dem Tier (Schwert, Crystal …)", true));
   private final NumberSetting iconSize = this.add(new NumberSetting("Icon-Größe", "Größe der Icons am Namen", 8.0, 6.0, 12.0, 1.0, "px"));
   private final BooleanSetting brackets = this.add(new BooleanSetting("Klammern", "Tier in eckigen Klammern: [HT1]", false));
   private final BooleanSetting background = this.add(new BooleanSetting("Hintergrund", "Dunkler Hintergrund hinter den Tiers (wie beim Namen)", true));
   private final NumberSetting othersY = this.add(
      new NumberSetting("Höhe (Zeile darüber)", "Zeile über dem Namen nach oben (minus) / unten (plus) verschieben", 0.0, -40.0, 20.0, 1.0, "px")
         .alias(new String[]{"Höhe (andere)"})
   );
   private final BooleanSetting card = this.add(new BooleanSetting("Info-Karte", "Alle Tierlists anzeigen, wenn du einen Spieler anschaust", true));
   private final HudBox cardBox = this.hudBox("Tiers-Karte", 56.0, 50.0);
   private final BooleanSetting ownHud = this.add(new BooleanSetting("Eigene Tiers im HUD", "Deine Tiers dauerhaft im HUD (Position im HUD-Editor)", true));
   private final HudBox ownBox = this.hudBox("Eigene Tiers", 99.0, 12.0);
   private final KeybindSetting trackerKey = this.add(new KeybindSetting("Tracker-Taste", "Öffnet den Tiers Tracker", Bind.NONE));
   private boolean trackerKeyDown;

   public Tiers() {
      super(
         "Tiers",
         "Tiers von MCPVP (Donut, aktuell), MCTiers (Legacy, alter Stand), PvPTiers und Subtiers – links/rechts am Namen, im HUD und im Tiers Tracker.",
         Category.RENDER
      );
      this.icon(Items.DIAMOND_SWORD);
      List<String> sides = new ArrayList<>(List.of("Aus", "Bester (alle)"));

      for (TierProvider p : this.manager.providers()) {
         String label = TierManager.label(p);
         sides.add(p.displayName());
         BooleanSetting toggle = new BooleanSetting("Tierlist: " + label, "Tiers von " + label + " benutzen", true);
         if (p.id().equals("mcpvp")) {
            toggle.alias(new String[]{"Tierlist: MCPVP (MCTiers/VTL)", "Tierlist: MCPVP (Dream/Donut)", "Tierlist: MCTiers"});
         }

         this.listToggles.add(this.add(toggle));
      }

      String[] sideModes = sides.toArray(new String[0]);
      this.leftSystem = this.add(
         new ModeSetting("Links vom Namen", "Welche Tierlist links neben dem Namen steht (z.B. alter MCTiers-Stand)", "MCTiers", sideModes)
      );
      this.rightSystem = this.add(
         new ModeSetting("Rechts vom Namen", "Welche Tierlist rechts neben dem Namen steht (z.B. aktuelle MCPVP-Liste)", "MCPVP", sideModes)
      );
      this.cardBox.background.set(false);
      this.cardBox.background.setVisibility(() -> false);
      this.ownBox.background.set(true);
      this.group("Tierlists", this.listToggles.toArray(new Setting[0]));
      this.group(
         "Am Namen",
         new Setting[]{
            this.nametag, this.placement, this.leftSystem, this.leftKit, this.rightSystem, this.rightKit, this.kitMode, this.count, this.kit, this.othersY
         }
      );
      this.group("Icons & Stil", new Setting[]{this.nameStyle, this.listIcons, this.kitIcons, this.iconSize, this.brackets, this.background});
      this.group(
         "HUD",
         new Setting[]{
            this.card,
            this.cardBox.scale,
            this.cardBox.opacity,
            this.ownHud,
            this.ownBox.scale,
            this.ownBox.opacity,
            this.ownBox.background,
            this.ownBox.backgroundColor,
            this.ownBox.rounding,
            this.trackerKey
         }
      );

      for (Setting<?> st : new Setting[]{
         this.nametag,
         this.placement,
         this.leftSystem,
         this.leftKit,
         this.rightSystem,
         this.rightKit,
         this.kitMode,
         this.count,
         this.kit,
         this.othersY,
         this.nameStyle,
         this.kitIcons,
         this.iconSize,
         this.brackets,
         this.background
      }) {
         st.setVisibility(() -> false);
      }

      this.add(
         new ActionSetting(
            "Nametag-Editor",
            "Tiers am Namen: Position, Stil und Tierlists am Namensschild – im Nametag-Editor mit Vorschau",
            "Editor öffnen",
            () -> this.mc.schedule(() -> this.mc.gui.setScreen(new NametagEditorScreen(this.mc.gui.screen())))
         )
      );
      this.add(new ActionSetting("Tiers Tracker", "Spieler suchen: Name, alter Name oder UUID – alle Tiers aller Listen", "Öffnen", () -> this.openTracker("")));
      this.add(new ActionSetting("Cache leeren", "Alle gespeicherten Tier-Daten verwerfen und neu laden", "Leeren", this.manager::clearCache));
      MeowlCommands.add("tiers", "tiers [spieler] §7– Tiers Tracker öffnen", root -> ClientCommands.literal(root).executes(ctx -> {
         this.openTracker("");
         return 1;
      }).then(ClientCommands.argument("spieler", StringArgumentType.word()).executes(ctx -> {
         this.openTracker(StringArgumentType.getString(ctx, "spieler"));
         return 1;
      })));
   }

   public TierManager manager() {
      return this.manager;
   }

   public NameLookup names() {
      return this.names;
   }

   public void openTracker(String query) {
      this.mc.schedule(() -> this.mc.gui.setScreen(new TiersTrackerScreen(this.mc.gui.screen(), this, query)));
   }

   @Override
   public void onTick() {
      boolean down = this.trackerKey.get().isPressed();
      if (down && !this.trackerKeyDown && this.mc.gui.screen() == null) {
         this.openTracker("");
      }

      this.trackerKeyDown = down;
   }

   private List<TierProvider> enabledProviders() {
      List<TierProvider> out = new ArrayList<>();
      List<TierProvider> all = this.manager.providers();

      for (int i = 0; i < all.size(); i++) {
         if (this.listToggles.get(i).get()) {
            out.add(all.get(i));
         }
      }

      return out;
   }

   public Tiers.Lookup lookup(UUID uuid, String name, String mode, int limit) {
      boolean loading = false;
      boolean anyData = false;
      List<Tiers.Item> all = new ArrayList<>();
      List<Tiers.Item> perList = new ArrayList<>();
      List<Tiers.Item> kitItems = new ArrayList<>();

      for (TierProvider p : this.enabledProviders()) {
         TierCache.Result r = this.manager.get(p, uuid, name);
         if (r == null || r.state() == TierCache.State.LOADING) {
            loading = true;
         } else if (r.state() == TierCache.State.FOUND && r.profile() != null) {
            TierProfile prof = r.profile();
            anyData |= !prof.rankings().isEmpty();
            prof.rankings().forEach(e -> all.add(new Tiers.Item(e, p)));
            prof.best().ifPresent(e -> perList.add(new Tiers.Item(e, p)));
            prof.forMode(this.kit.get()).ifPresent(e -> kitItems.add(new Tiers.Item(e, p)));
         }
      }

      Comparator<Tiers.Item> byTier = Comparator.comparingInt(i -> i.entry().sortKey());
      all.sort(byTier);

      List<Tiers.Item> chosen = (List<Tiers.Item>)(switch (mode) {
         case "Bester Tier" -> all.isEmpty() ? List.of() : List.of(all.get(0));
         case "Je Tierlist" -> perList;
         case "Bestimmtes Kit" -> kitItems;
         default -> all;
      });
      return new Tiers.Lookup(chosen.subList(0, Math.min(limit, chosen.size())), loading, anyData);
   }

   private Tiers.Lookup nametagLookup(Player player) {
      return this.lookup(player.getUUID(), player.getGameProfile().name(), this.kitMode.get(), this.kitMode.is("Bester Tier") ? 1 : this.count.getInt());
   }

   public boolean hasNametagLine(Player player) {
      if (this.isEnabled() && this.nametag.get() && this.placement.is("Über dem Namen")) {
         Tiers.Lookup l = this.nametagLookup(player);
         return !l.items().isEmpty();
      } else {
         return false;
      }
   }

   public boolean showsAtName() {
      return this.isEnabled() && this.nametag.get();
   }

   public boolean placedAbove() {
      return this.placement.is("Über dem Namen");
   }

   public float aboveOffset() {
      return this.othersY.getInt();
   }

   public boolean tierBackground() {
      return this.background.get();
   }

   public Tiers.Item sideItem(Player player, boolean left) {
      ModeSetting system = left ? this.leftSystem : this.rightSystem;
      String kitName = (left ? this.leftKit : this.rightKit).get().trim();
      if (system.is("Aus")) {
         return null;
      } else {
         Tiers.Item best = null;

         for (TierProvider p : this.enabledProviders()) {
            if (system.is("Bester (alle)") || system.is(p.displayName())) {
               TierCache.Result r = this.manager.get(p, player.getUUID(), player.getGameProfile().name());
               if (r != null && r.state() == TierCache.State.FOUND && r.profile() != null) {
                  Optional<TierEntry> e = kitName.isEmpty() ? r.profile().best() : r.profile().forMode(kitName);
                  if (e.isPresent() && (best == null || e.get().sortKey() < best.entry().sortKey())) {
                     best = new Tiers.Item(e.get(), p);
                  }
               }
            }
         }

         return best;
      }
   }

   public NametagPiece tierPiece(Player player, boolean first, boolean leftOfName) {
      Tiers.Item item = this.sideItem(player, first);
      return item == null ? null : this.piece(item, leftOfName);
   }

   public NametagPiece samplePiece(boolean first, boolean leftOfName) {
      ModeSetting system = first ? this.leftSystem : this.rightSystem;
      if (system.is("Aus")) {
         return null;
      } else {
         TierProvider provider = this.manager.providers().get(0);

         for (TierProvider p : this.manager.providers()) {
            if (system.is(p.displayName())) {
               provider = p;
            }
         }

         String kitName = (first ? this.leftKit : this.rightKit).get().trim();
         TierEntry e = TierEntry.parse(
            kitName.isEmpty() ? (first ? "sword" : "mace") : kitName,
            kitName.isEmpty() ? (first ? "Sword" : "Mace") : kitName,
            first ? "HT3" : "LT2",
            false,
            null
         );
         return e == null ? null : this.piece(new Tiers.Item(e, provider), leftOfName);
      }
   }

   public List<NametagPiece> sampleKitPieces() {
      List<NametagPiece> out = new ArrayList<>();
      String[][] demo = new String[][]{{"sword", "Sword", "HT2"}, {"mace", "Mace", "LT3"}, {"crystal", "Crystal", "HT4"}, {"pot", "Pot", "LT1"}};
      int n = this.kitMode.is("Bester Tier") ? 1 : Math.min(this.count.getInt(), demo.length);

      for (int i = 0; i < n; i++) {
         TierEntry e = TierEntry.parse(demo[i][0], demo[i][1], demo[i][2], false, null);
         if (e != null) {
            out.add(this.piece(new Tiers.Item(e, this.manager.providers().get(0))));
         }
      }

      return out;
   }

   public BooleanSetting nametagSetting() {
      return this.nametag;
   }

   public ModeSetting system(boolean first) {
      return first ? this.leftSystem : this.rightSystem;
   }

   public TextSetting systemKit(boolean first) {
      return first ? this.leftKit : this.rightKit;
   }

   public ModeSetting kitModeSetting() {
      return this.kitMode;
   }

   public NumberSetting countSetting() {
      return this.count;
   }

   public TextSetting kitSetting() {
      return this.kit;
   }

   public NumberSetting aboveOffsetSetting() {
      return this.othersY;
   }

   public ModeSetting nameStyleSetting() {
      return this.nameStyle;
   }

   public BooleanSetting listIconsSetting() {
      return this.listIcons;
   }

   public BooleanSetting kitIconsSetting() {
      return this.kitIcons;
   }

   public NumberSetting iconSizeSetting() {
      return this.iconSize;
   }

   public BooleanSetting backgroundSetting() {
      return this.background;
   }

   public BooleanSetting bracketsSetting() {
      return this.brackets;
   }

   public boolean legacyPlacedAbove() {
      return this.placement.is("Über dem Namen");
   }

   public NametagPiece sidePiece(Player player, boolean left) {
      Tiers.Item item = this.sideItem(player, left);
      return item == null ? null : this.piece(item, left);
   }

   public List<NametagPiece> abovePieces(Player player) {
      List<NametagPiece> out = new ArrayList<>();

      for (Tiers.Item item : this.nametagLookup(player).items()) {
         out.add(this.piece(item));
      }

      return out;
   }

   public void useOutsideIcons() {
      this.nameStyle.set("Icon außen");
   }

   public boolean serverStyle() {
      return this.nameStyle.is("Wie Server-Nametag");
   }

   private NametagPiece piece(Tiers.Item item) {
      return this.piece(item, false);
   }

   private NametagPiece piece(Tiers.Item item, boolean leftOfName) {
      TierEntry e = item.entry();
      if (this.nameStyle.is("Icon außen")) {
         KitIcons.Icon kitIcon = this.kitIcons.get() ? KitIcons.forMode(e.mode()) : null;
         return new TierSidePiece(kitIcon, e.label(), e.color(), this.iconSize.getInt(), leftOfName);
      } else if (this.serverStyle()) {
         return new TierTextPiece(e.label(), e.color(), leftOfName);
      } else {
         int size = this.iconSize.getInt();
         Identifier list = this.listIcons.get() ? NametagIcons.tierlist(item.provider().id(), 32) : null;
         KitIcons.Icon kitIcon = this.kitIcons.get() ? KitIcons.forMode(e.mode()) : null;
         String label = this.brackets.get() ? "[" + e.label() + "]" : e.label();
         return new TierPiece(list, kitIcon, label, e.color(), size);
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = HudLayout.isEditing();
      if (this.mc.player != null && (!this.mc.gui.hud.isHidden() || editing)) {
         if (this.ownHud.get() || editing) {
            this.renderOwn(ctx, editing);
         }

         if (editing || this.card.get() && this.mc.gui.screen() == null) {
            Player target = (Player)(editing ? this.mc.player : this.lookedAtPlayer(tickDelta));
            if (target != null) {
               this.renderCard(ctx, target);
            }
         }
      }
   }

   private void renderOwn(GuiGraphicsExtractor ctx, boolean editing) {
      Font tr = this.mc.font;
      UUID self = this.mc.player.getUUID();
      String name = this.mc.player.getGameProfile().name();
      List<String[]> rows = new ArrayList<>();
      List<List<Tiers.Item>> rowItems = new ArrayList<>();

      for (TierProvider p : this.enabledProviders()) {
         TierCache.Result r = this.manager.get(p, self, name);
         if (r != null && r.state() == TierCache.State.FOUND && r.profile() != null && !r.profile().rankings().isEmpty()) {
            List<Tiers.Item> items = new ArrayList<>();
            r.profile().rankings().stream().sorted(Comparator.comparingInt(TierEntry::sortKey)).limit(4L).forEach(e -> items.add(new Tiers.Item(e, p)));
            rows.add(new String[]{TierManager.tag(p)});
            rowItems.add(items);
         }
      }

      if (rows.isEmpty()) {
         if (editing) {
            String t = "Eigene Tiers (keine Daten)";
            this.ownBox.begin(ctx, tr.width(t) + 8, 13);
            ctx.text(tr, t, 4, 3, this.ownBox.fade(-7564628), true);
            this.ownBox.end(ctx);
         }
      } else {
         int w = 0;

         for (List<Tiers.Item> items : rowItems) {
            int rw = 18;

            for (Tiers.Item i : items) {
               rw += 10 + tr.width(i.entry().label()) + 4;
            }

            w = Math.max(w, rw);
         }

         int h = rows.size() * 11 + 4;
         this.ownBox.begin(ctx, w + 4, h);
         int y = 3;

         for (int r = 0; r < rows.size(); r++) {
            Tiers.Item first = rowItems.get(r).get(0);
            if (this.listIcons.get()) {
               int px = Math.round(9.0F * (float)this.ownBox.scale.get().doubleValue()) * NametagIcons.guiScale();
               NametagIcons.gui(ctx, NametagIcons.tierlist(first.provider().id(), px), 4, y, 9);
            } else {
               ctx.text(tr, rows.get(r)[0], 3, y + 1, this.ownBox.fade(TierManager.color(first.provider())), true);
            }

            int x = 18;

            for (Tiers.Item i : rowItems.get(r)) {
               KitIcons.Icon icon = KitIcons.forMode(i.entry().mode());
               if (icon != null) {
                  KitIcons.draw(ctx, icon, x, y, 9);
               }

               x += 10;
               ctx.text(tr, i.entry().label(), x, y + 1, this.ownBox.fade(i.entry().color()), true);
               x += tr.width(i.entry().label()) + 4;
            }

            y += 11;
         }

         this.ownBox.end(ctx);
      }
   }

   private void renderCard(GuiGraphicsExtractor ctx, Player target) {
      Font tr = this.mc.font;
      List<String[]> rows = new ArrayList<>();
      List<Integer> colors = new ArrayList<>();
      List<KitIcons.Icon> icons = new ArrayList<>();
      List<TierProvider> providers = new ArrayList<>();

      for (TierProvider provider : this.enabledProviders()) {
         TierCache.Result r = this.manager.get(provider, target.getUUID(), target.getGameProfile().name());
         int color = -7564628;
         KitIcons.Icon icon = null;
         String text;
         if (r == null || r.state() == TierCache.State.LOADING) {
            text = "lädt …";
         } else if (r.state() == TierCache.State.ERROR) {
            text = this.manager.isOffline(provider) ? "offline (Pause)" : "nicht erreichbar";
            color = -495247;
         } else {
            if (r.state() == TierCache.State.NONE || r.profile() == null) {
               continue;
            }

            Optional<TierEntry> best = this.kitMode.is("Bestimmtes Kit") ? r.profile().forMode(this.kit.get()) : r.profile().best();
            if (best.isEmpty()) {
               continue;
            }

            TierEntry e = best.get();
            icon = KitIcons.forMode(e.mode());
            int more = r.profile().rankings().size() - 1;
            text = e.label() + " " + e.modeTitle() + (more > 0 ? "  +" + more + " Kits" : "");
            color = e.color();
         }

         rows.add(new String[]{provider.displayName(), text});
         colors.add(color);
         icons.add(icon);
         providers.add(provider);
      }

      if (!rows.isEmpty()) {
         int w = tr.width(target.getName().getString()) + 16;

         for (String[] row : rows) {
            w = Math.max(w, 85 + tr.width(row[1]) + 10);
         }

         int h = 18 + rows.size() * 11 + 4;
         this.cardBox.begin(ctx, w, h, false);
         Render2D.roundRect(ctx, 0, 0, w, h, 4, this.cardBox.fade(-804121568));
         Render2D.roundRect(ctx, 0, 4, 2, h - 8, 1, -7643914);
         ctx.text(tr, target.getName().getString(), 7, 5, -1578509, true);
         int ry = 18;

         for (int i = 0; i < rows.size(); i++) {
            if (this.listIcons.get()) {
               int px = Math.round(9.0F * (float)this.cardBox.scale.get().doubleValue()) * NametagIcons.guiScale();
               NametagIcons.gui(ctx, NametagIcons.tierlist(providers.get(i).id(), px), 6, ry - 1, 9);
               ctx.text(tr, rows.get(i)[0], 17, ry, TierManager.color(providers.get(i)), false);
            } else {
               ctx.text(tr, rows.get(i)[0], 7, ry, TierManager.color(providers.get(i)), false);
            }

            KitIcons.Icon icon = icons.get(i);
            if (icon != null) {
               KitIcons.draw(ctx, icon, 73, ry - 1, 10);
            }

            ctx.text(tr, rows.get(i)[1], 85, ry, colors.get(i), false);
            ry += 11;
         }

         this.cardBox.end(ctx);
      }
   }

   private Player lookedAtPlayer(float tickDelta) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.crosshairPickEntity instanceof Player p) {
            return p;
         } else {
            Vec3 eye = this.mc.player.getEyePosition(tickDelta);
            Vec3 look = this.mc.player.getViewVector(tickDelta);
            Player best = null;
            double bestAngle = Math.toRadians(4.0);

            for (Player p : this.mc.level.players()) {
               if (p != this.mc.player && !p.isInvisible()) {
                  Vec3 to = p.getPosition(tickDelta).add(0.0, p.getBbHeight() * 0.6, 0.0).subtract(eye);
                  double dist = to.length();
                  if (!(dist > 64.0) && !(dist < 0.5)) {
                     double angle = Math.acos(Math.max(-1.0, Math.min(1.0, to.normalize().dot(look))));
                     if (angle < bestAngle && this.mc.player.hasLineOfSight(p)) {
                        bestAngle = angle;
                        best = p;
                     }
                  }
               }
            }

            return best;
         }
      } else {
         return null;
      }
   }

   public record Item(TierEntry entry, TierProvider provider) {
   }

   public record Lookup(List<Tiers.Item> items, boolean loading, boolean anyData) {
   }
}
