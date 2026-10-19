package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.mods.Tiers;
import com.lennart.nebula.nametag.NametagIcons;
import com.lennart.nebula.tiers.KitIcons;
import com.lennart.nebula.tiers.NameLookup;
import com.lennart.nebula.tiers.TierCache;
import com.lennart.nebula.tiers.TierEntry;
import com.lennart.nebula.tiers.TierManager;
import com.lennart.nebula.tiers.TierProfile;
import com.lennart.nebula.tiers.TierProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.services.ProfileResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;

public class TiersTrackerScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int BG = -15855591;
   private static final int PANEL = -15526365;
   private static final int CARD = -15197141;
   private static final int LEFT_W = 132;
   private static final int TOP_H = 30;
   private static final List<NameLookup.Candidate> RECENT = new ArrayList<>();
   private static final Map<UUID, PlayerSkin> SKINS = new ConcurrentHashMap<>();
   private static final Map<UUID, Boolean> SKIN_REQUESTED = new ConcurrentHashMap<>();
   private final Screen parent;
   private final Tiers tiers;
   private final MeowlTextField search = new MeowlTextField(36).placeholder("Name, alter Name oder UUID …");
   private final Animation open = new Animation(14.0F, 0.0F);
   private List<NameLookup.Candidate> results = List.of();
   private String status = "";
   private boolean searching;
   private NameLookup.Candidate selected;
   private List<NameLookup.NameChange> history = List.of();
   private boolean historyLoading;
   private double leftScroll;
   private double leftTarget;
   private double rightScroll;
   private double rightTarget;
   private int leftContent;
   private int rightContent;
   private long lastFrame = System.nanoTime();
   private final List<Object[]> leftRows = new ArrayList<>();
   private int[] reloadButton = new int[4];

   public TiersTrackerScreen(Screen parent, Tiers tiers, String query) {
      super(Component.literal("Tiers Tracker"));
      this.parent = parent;
      this.tiers = tiers;
      this.search.onSubmit(q -> this.runSearch());
      if (query != null && !query.isBlank()) {
         this.search.setText(query);
      }
   }

   @Override
   protected void init() {
      this.search.setFocused(this.selected == null && this.results.isEmpty());
      if (!this.search.getText().isBlank() && this.results.isEmpty() && !this.searching) {
         this.runSearch();
      } else if (this.selected == null && this.minecraft.player != null) {
         this.select(new NameLookup.Candidate(this.minecraft.player.getUUID(), this.minecraft.player.getGameProfile().name(), false));
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private void runSearch() {
      String q = this.search.getText().trim();
      if (!q.isEmpty()) {
         this.searching = true;
         this.status = "Suche „" + q + "“ …";
         this.tiers.names().search(q).whenComplete((list, error) -> this.minecraft.execute(() -> {
            this.searching = false;
            if (error == null && list != null) {
               this.results = (List<NameLookup.Candidate>)list;
               this.status = list.isEmpty() ? "Kein Spieler mit „" + q + "“ gefunden" : list.size() + " Treffer";
               this.leftTarget = 0.0;
               if (!list.isEmpty()) {
                  this.select(list.get(0));
               }
            } else {
               this.status = "Suche fehlgeschlagen (Internet?)";
               this.results = List.of();
            }
         }));
      }
   }

   private void select(NameLookup.Candidate c) {
      this.selected = c;
      this.rightTarget = 0.0;
      this.history = List.of();
      this.historyLoading = true;
      RECENT.removeIf(r -> r.uuid().equals(c.uuid()));
      RECENT.add(0, new NameLookup.Candidate(c.uuid(), c.name(), false));

      while (RECENT.size() > 12) {
         RECENT.remove(RECENT.size() - 1);
      }

      UUID uuid = c.uuid();
      this.tiers.names().history(uuid).whenComplete((h, e) -> this.minecraft.execute(() -> {
         if (this.selected != null && this.selected.uuid().equals(uuid)) {
            this.history = h == null ? List.of() : h;
            this.historyLoading = false;
         }
      }));
      this.requestSkin(uuid, c.name());
   }

   private void requestSkin(UUID uuid, String name) {
      if (!SKINS.containsKey(uuid) && SKIN_REQUESTED.putIfAbsent(uuid, true) == null) {
         if (this.minecraft.getConnection() != null) {
            PlayerInfo entry = this.minecraft.getConnection().getPlayerInfo(uuid);
            if (entry != null) {
               SKINS.put(uuid, entry.getSkin());
               return;
            }
         }

         CompletableFuture.<GameProfile>supplyAsync(() -> {
               ProfileResult result = this.minecraft.services().sessionService().fetchProfile(uuid, false);
               return result == null ? new GameProfile(uuid, name) : result.profile();
            })
            .thenCompose(profile -> this.minecraft.getSkinManager().get(profile))
            .thenAccept(opt -> opt.ifPresent(s -> SKINS.put(uuid, s)))
            .exceptionally(e -> null);
      }
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
   }

   private double smooth(double cur, double target, float dt) {
      double next = cur + (target - cur) * Math.min(1.0F, dt * 18.0F);
      return Math.abs(target - next) < 0.3 ? target : next;
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      float a = this.open.update(1.0F);
      context.fill(0, 0, this.width, this.height, Render2D.alpha(-536344304, a));
      context.fill(0, 0, this.width, this.height, Render2D.alpha(-15855591, a * 0.6F));
      context.text(this.font, Component.literal("Tiers Tracker").withStyle(ChatFormatting.BOLD), 10, 8, -1578509, false);
      this.small(context, "MCPVP (MCTiers/VTL) · PvPTiers · Subtiers", 10, 19, -7564628);
      int sx = 146;
      int sw = Math.max(120, this.width - sx - 70);
      this.search.setBounds(sx, 7, sw, 16);
      this.search.render(context, this.font, false);
      boolean goHover = Render2D.inside(mouseX, mouseY, sx + sw + 4, 7, 52, 16);
      Render2D.roundRect(context, sx + sw + 4, 7, 52, 16, 5, goHover ? -8635667 : -7643914);
      context.text(this.font, "Suchen", sx + sw + 4 + (52 - this.font.width("Suchen")) / 2, 11, -1, false);
      this.leftScroll = this.smooth(this.leftScroll, this.leftTarget, dt);
      this.rightScroll = this.smooth(this.rightScroll, this.rightTarget, dt);
      this.renderLeft(context, mouseX, mouseY);
      this.renderProfile(context, mouseX, mouseY);
      super.render(context, mouseX, mouseY, deltaTicks);
   }

   private void renderLeft(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int x = 6;
      int y = 30;
      int w = 132;
      int h = this.height - 30 - 6;
      Render2D.roundRect(ctx, x, y, w, h, 6, -15526365);
      this.leftRows.clear();
      ctx.enableScissor(x, y + 2, x + w, y + h - 2);
      int cy = y + 6 - (int)this.leftScroll;
      if (!this.status.isEmpty()) {
         for (FormattedCharSequence line : this.font.split(Component.literal(this.status), Math.round((w - 12) / 0.75F))) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(x + 6, cy);
            ctx.pose().scale(0.75F, 0.75F);
            ctx.text(this.font, line, 0, 0, this.searching ? -3885571 : -7564628, false);
            ctx.pose().popMatrix();
            cy += 8;
         }

         cy += 4;
      }

      cy = this.section(ctx, mouseX, mouseY, "Treffer", this.results, x, cy, w);
      List<NameLookup.Candidate> online = new ArrayList<>();
      if (this.minecraft.getConnection() != null) {
         this.minecraft
            .getConnection()
            .getOnlinePlayers()
            .stream()
            .sorted(Comparator.comparing(e -> e.getProfile().name().toLowerCase(Locale.ROOT)))
            .limit(80L)
            .forEach(e -> online.add(new NameLookup.Candidate(e.getProfile().id(), e.getProfile().name(), false)));
      }

      cy = this.section(ctx, mouseX, mouseY, "Auf diesem Server", online, x, cy, w);
      cy = this.section(ctx, mouseX, mouseY, "Zuletzt angesehen", RECENT, x, cy, w);
      ctx.disableScissor();
      this.leftContent = cy + (int)this.leftScroll - y;
      this.leftTarget = Math.max(0.0, Math.min((double)Math.max(0, this.leftContent - h), this.leftTarget));
   }

   private int section(GuiGraphicsExtractor ctx, int mouseX, int mouseY, String title, List<NameLookup.Candidate> list, int x, int cy, int w) {
      if (list.isEmpty()) {
         return cy;
      } else {
         ctx.text(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 6, cy, -3885571, false);
         cy += 12;

         for (NameLookup.Candidate c : list) {
            boolean sel = this.selected != null && this.selected.uuid().equals(c.uuid());
            boolean hov = mouseY >= 30 && Render2D.inside(mouseX, mouseY, x + 3, cy - 1, w - 6, 14);
            if (sel || hov) {
               Render2D.roundRect(ctx, x + 3, cy - 1, w - 6, 14, 4, sel ? -14015664 : -14933965);
            }

            this.face(ctx, c.uuid(), c.name(), x + 6, cy + 1, 10);
            String label = this.font.plainSubstrByWidth(c.name(), w - 34);
            ctx.text(this.font, label, x + 20, cy + 2, -1578509, false);
            if (c.viaOldName()) {
               this.small(ctx, "alt", x + w - 18, cy + 3, -278748);
            }

            this.leftRows.add(new Object[]{x + 3, cy - 1, w - 6, 14, c});
            cy += 15;
         }

         return cy + 6;
      }
   }

   private void face(GuiGraphicsExtractor ctx, UUID uuid, String name, int x, int y, int size) {
      PlayerSkin skin = SKINS.get(uuid);
      if (skin == null) {
         this.requestSkin(uuid, name);
         Render2D.roundRect(ctx, x, y, size, size, 2, -13880760);
      } else {
         PlayerFaceExtractor.extractRenderState(ctx, skin, x, y, size);
      }
   }

   private void renderProfile(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int x = 144;
      int y = 30;
      int w = this.width - x - 6;
      int h = this.height - 30 - 6;
      Render2D.roundRect(ctx, x, y, w, h, 6, -15526365);
      if (this.selected == null) {
         String t = "Suche oben nach einem Spieler – oder wähle links einen aus.";
         ctx.text(this.font, t, x + (w - this.font.width(t)) / 2, y + h / 2 - 4, -7564628, false);
      } else {
         ctx.enableScissor(x, y + 2, x + w, y + h - 2);
         int cy = y + 8 - (int)this.rightScroll;
         this.face(ctx, this.selected.uuid(), this.selected.name(), x + 8, cy, 24);
         ctx.text(this.font, Component.literal(this.selected.name()).withStyle(ChatFormatting.BOLD), x + 38, cy + 2, -1578509, false);
         this.small(ctx, this.selected.uuid().toString(), x + 38, cy + 14, -10656900);
         int bw = 70;
         int bx = x + w - bw - 8;
         boolean hov = Render2D.inside(mouseX, mouseY, bx, cy + 4, bw, 14);
         Render2D.roundRect(ctx, bx, cy + 4, bw, 14, 5, hov ? -13748912 : -14538686);
         ctx.text(this.font, "↻ Neu laden", bx + (bw - this.font.width("↻ Neu laden")) / 2, cy + 7, -1578509, false);
         this.reloadButton = new int[]{bx, cy + 4, bw, 14};
         cy += 32;
         ctx.text(this.font, Component.literal("Namensverlauf").withStyle(ChatFormatting.BOLD), x + 8, cy, -3885571, false);
         cy += 12;
         if (this.historyLoading) {
            this.small(ctx, "lädt …", x + 10, cy, -7564628);
            cy += 10;
         } else if (this.history.isEmpty()) {
            this.small(ctx, "Kein Verlauf bekannt (nur aktueller Name)", x + 10, cy, -7564628);
            cy += 10;
         } else {
            int hx = x + 8;

            for (NameLookup.NameChange n : this.history) {
               String label = n.name() + (n.changedAt().isEmpty() ? "" : "  " + n.changedAt());
               int cw = Math.round(this.font.width(label) * 0.75F) + 10;
               if (hx + cw > x + w - 8) {
                  hx = x + 8;
                  cy += 13;
               }

               boolean current = n.name().equalsIgnoreCase(this.selected.name());
               Render2D.roundRect(ctx, hx, cy, cw, 11, 4, current ? -14015664 : -15197141);
               this.small(ctx, label, hx + 5, cy + 2, current ? -1578509 : -7564628);
               hx += cw + 4;
            }

            cy += 15;
         }

         cy += 4;
         TierManager manager = this.tiers.manager();

         for (TierProvider p : manager.providers()) {
            cy = this.providerCard(ctx, manager, p, x + 8, cy, w - 16) + 6;
         }

         ctx.disableScissor();
         this.rightContent = cy + (int)this.rightScroll - y;
         this.rightTarget = Math.max(0.0, Math.min((double)Math.max(0, this.rightContent - h), this.rightTarget));
      }
   }

   private int providerCard(GuiGraphicsExtractor ctx, TierManager manager, TierProvider p, int x, int y, int w) {
      TierCache.Result r = manager.get(p, this.selected.uuid(), this.selected.name());
      List<TierEntry> entries = r != null && r.profile() != null ? new ArrayList<>(r.profile().rankings()) : new ArrayList<>();
      entries.sort(Comparator.comparingInt(TierEntry::sortKey));
      int chipW = 104;
      int chipH = 22;
      int gap = 4;
      int perRow = Math.max(1, (w - 12 + gap) / (chipW + gap));
      int rows = entries.isEmpty() ? 0 : (entries.size() + perRow - 1) / perRow;
      int h = 22 + (rows == 0 ? 12 : rows * (chipH + gap));
      int color = TierManager.color(p);
      Render2D.roundRect(ctx, x, y, w, h, 5, -15197141);
      Render2D.roundRect(ctx, x, y + 4, 3, 12, 1, color);
      int px = 10 * NametagIcons.guiScale();
      NametagIcons.gui(ctx, NametagIcons.tierlist(p.id(), px), x + 8, y + 5, 10);
      ctx.text(this.font, Component.literal(TierManager.label(p)).withStyle(ChatFormatting.BOLD), x + 21, y + 6, color, false);
      int infoColor = -7564628;
      String info;
      if (r == null || r.state() == TierCache.State.LOADING) {
         info = "lädt …";
      } else if (r.state() == TierCache.State.ERROR) {
         info = "nicht erreichbar – versucht es gleich nochmal";
         infoColor = -495247;
      } else if (r.state() != TierCache.State.NONE && r.profile() != null && !entries.isEmpty()) {
         TierProfile prof = r.profile();
         info = entries.size() + " Kits" + (prof.points() > 0 ? " · " + prof.points() + " Punkte" : "") + (prof.region() != null ? " · " + prof.region() : "");
      } else {
         info = "nicht eingetragen";
      }

      int tx = x + 25 + this.font.width(Component.literal(TierManager.label(p)).withStyle(ChatFormatting.BOLD));
      this.small(ctx, this.font.plainSubstrByWidth(info, Math.round((x + w - tx - 6) / 0.75F)), tx, y + 7, infoColor);
      int cy = y + 20;
      if (entries.isEmpty()) {
         return y + h;
      } else {
         for (int i = 0; i < entries.size(); i++) {
            TierEntry e = entries.get(i);
            int cx = x + 6 + i % perRow * (chipW + gap);
            int ry = cy + i / perRow * (chipH + gap);
            Render2D.roundRect(ctx, cx, ry, chipW, chipH, 5, -15789540);
            Render2D.roundRect(ctx, cx, ry, 3, chipH, 1, e.color());
            KitIcons.Icon icon = KitIcons.forMode(e.mode());
            if (icon != null) {
               KitIcons.draw(ctx, icon, cx + 5, ry + 3, 16);
            }

            int lx = cx + (icon != null ? 24 : 8);
            ctx.text(this.font, Component.literal(e.label()).withStyle(ChatFormatting.BOLD), lx, ry + 3, e.color(), false);
            String sub = e.modeTitle();
            if (e.peak() != null && !e.peak().equals(e.subtier() + e.tier())) {
               sub = sub + " · Peak " + e.peak();
            }

            this.small(ctx, this.font.plainSubstrByWidth(sub, Math.round((chipW - (lx - cx) - 4) / 0.75F)), lx, ry + 13, -7564628);
         }

         return y + h;
      }
   }

   private void small(GuiGraphicsExtractor ctx, String text, int x, int y, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(x, y);
      ctx.pose().scale(0.75F, 0.75F);
      ctx.text(this.font, text, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      if (this.search.mouseClicked(mx, my, this.font)) {
         return true;
      } else {
         int sx = 146;
         int sw = Math.max(120, this.width - sx - 70);
         if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, sx + sw + 4, 7, 52, 16)) {
            this.runSearch();
            return true;
         } else if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
            && this.selected != null
            && my >= 30.0
            && Render2D.inside(mx, my, this.reloadButton[0], this.reloadButton[1], this.reloadButton[2], this.reloadButton[3])) {
            this.tiers.manager().invalidate(this.selected.uuid());
            return true;
         } else {
            if (my >= 30.0 && mx < 138.0) {
               for (Object[] row : this.leftRows) {
                  if (Render2D.inside(mx, my, (Integer)row[0], (Integer)row[1], (Integer)row[2], (Integer)row[3])) {
                     this.select((NameLookup.Candidate)row[4]);
                     return true;
                  }
               }
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (mouseX < 138.0) {
         this.leftTarget -= verticalAmount * 24.0;
      } else {
         this.rightTarget -= verticalAmount * 28.0;
      }

      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      return this.search.charTyped(input.codepoint()) || super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.search.isFocused()) {
         if (input.key() == InputConstants.KEY_ESCAPE) {
            this.search.setFocused(false);
            return true;
         }

         if (this.search.keyPressed(input.key(), input.modifiers())) {
            return true;
         }
      }

      return super.keyPressed(input);
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
