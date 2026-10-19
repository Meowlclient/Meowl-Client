package com.lennart.nebula.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Meowl-Mehrspieler: deine Server als Karten (Ping, Spieler, MOTD; Doppelklick verbindet) und links dein Skin auf einer
 * kleinen schwebenden Insel. Hinzufügen, Bearbeiten, Löschen und Direkt verbinden laufen über die Minecraft-Dialoge.
 */
public final class MeowlMultiplayerScreen extends Screen {
   /** Klicks notfalls selbst auslösen, falls eine andere Mod sie im Menü abfängt. */
   private final ClickFallback clickFallback = new ClickFallback();

   /** 26.x: keine Texteingabe nötig – sonst nimmt die Windows-Texteingabe Minecraft Fokus und Klicks weg. */
   protected boolean textInput() {
      return false;
   }
   private static final int ACCENT = 0xFFB57BFF;
   private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();
   private final long opened = System.currentTimeMillis();
   private final List<MeowlMultiplayerScreen.Hit> hits = new ArrayList<>();
   private final ServerPings pings = new ServerPings();
   private final List<ServerData> servers = new ArrayList<>();
   private final float[] cardHover = new float[256];
   private final float[] buttonHover = new float[8];
   private ServerList list;
   private ServerData selected;
   private long lastClick;
   // Umsortieren per Drag & Drop
   private static final int CARD_H = 36;
   private static final int CARD_GAP = 4;
   private ServerData pressed;
   private double pressY;
   private ServerData dragging;
   private double dragGrab;
   private int dragTarget;
   private final java.util.Map<ServerData, Float> slot = new java.util.HashMap<>();
   // Server-Icons wie in der Minecraft-Liste
   private final java.util.Map<ServerData, net.minecraft.client.gui.screens.FaviconTexture> icons = new java.util.HashMap<>();
   private final java.util.Map<ServerData, byte[]> iconBytes = new java.util.HashMap<>();
   private float scroll;
   private float scrollTarget;
   private int maxScroll;
   private int listX;
   private int listY;
   private int listW;
   private int listH;

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }

   public MeowlMultiplayerScreen() {
      super(Component.literal("Mehrspieler"));
   }

   @Override
   protected void init() {
      if (this.list == null) {
         this.reload();
      }

   }

   private void reload() {
      this.pings.stop();
      this.servers.clear();
      this.list = new ServerList(this.minecraft);
      this.list.load();
      for (int i = 0; i < this.list.size(); i++) {
         this.servers.add(this.list.get(i));
      }

      if (this.selected != null && !this.servers.contains(this.selected)) {
         this.selected = null;
      }

      for (ServerData d : this.servers) {
         this.pings.ping(d);
      }
   }

   @Override
   public void tick() {
      this.pings.tick();
   }

   @Override
   public void removed() {
      this.pings.stop();
      this.icons.values().forEach(net.minecraft.client.gui.screens.FaviconTexture::close);
      this.icons.clear();
      this.iconBytes.clear();
   }

   /** Icon des Servers (null = keins); neu hochladen, sobald der Server ein anderes schickt. */
   private net.minecraft.resources.Identifier icon(ServerData d) {
      byte[] b = d.getIconBytes();
      if (b == null) {
         return null;
      }

      net.minecraft.client.gui.screens.FaviconTexture tex = this.icons.get(d);
      if (tex == null || tex.isClosed()) {
         tex = net.minecraft.client.gui.screens.FaviconTexture.forServer(this.minecraft.getTextureManager(), d.ip);
         this.icons.put(d, tex);
         this.iconBytes.remove(d);
      }

      if (this.iconBytes.get(d) != b) {
         this.iconBytes.put(d, b);
         try {
            tex.upload(com.mojang.blaze3d.platform.NativeImage.read(b));
         } catch (Exception var5) {
            tex.clear();
            return null;
         }
      }

      return tex.textureLocation();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(new MeowlTitleScreen());
   }

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
   }

   // ================================================================== Aktionen

   private void join(ServerData d) {
      if (d != null) {
         MeowlTitleScreen.connect(this, d);
      }
   }

   private void add() {
      ServerData d = new ServerData(Component.translatable("selectServer.defaultName").getString(), "", ServerData.Type.OTHER);
      this.minecraft.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), ok -> {
         if (ok) {
            this.list.add(d, false);
            this.list.save();
            this.selected = d;
         }

         this.minecraft.setScreen(this);
         this.reload();
      }, d));
   }

   private void edit() {
      ServerData d = this.selected;
      if (d == null) {
         return;
      }

      ServerData copy = new ServerData(d.name, d.ip, ServerData.Type.OTHER);
      copy.copyFrom(d);
      this.minecraft.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), ok -> {
         if (ok) {
            d.copyNameIconFrom(copy);
            d.ip = copy.ip;
            d.setResourcePackStatus(copy.getResourcePackStatus());
            this.list.save();
         }

         this.minecraft.setScreen(this);
         this.reload();
      }, copy));
   }

   private void delete() {
      ServerData d = this.selected;
      if (d == null) {
         return;
      }

      this.minecraft.setScreen(new ConfirmScreen(ok -> {
         if (ok) {
            this.list.remove(d);
            this.list.save();
            this.selected = null;
         }

         this.minecraft.setScreen(this);
         this.reload();
      }, Component.literal("Server löschen?"), Component.literal("„" + d.name + "“ wird aus deiner Liste entfernt."), Component.literal("Löschen"), Component.literal("Abbrechen")));
   }

   private void direct() {
      ServerData d = new ServerData(Component.translatable("selectServer.defaultName").getString(), "", ServerData.Type.OTHER);
      this.minecraft.setScreen(new DirectJoinServerScreen(this, ok -> {
         if (ok) {
            this.join(d);
         } else {
            this.minecraft.setScreen(this);
         }
      }, d));
   }

   // ================================================================== Zeichnen

   private static int a(int rgb, float alpha) {
      return MeowlSpace.withAlpha(rgb, alpha);
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.clickFallback.pump(this, this.minecraft);
      this.hits.clear();
      int w = this.width;
      int h = this.height;
      float t = (System.currentTimeMillis() - this.opened) / 1000.0F;
      float in = MeowlTitleScreen.ease(t / 0.5F);
      this.scroll += (this.scrollTarget - this.scroll) * 0.3F;
      MeowlSpace.draw(ctx, w, h, mouseX, mouseY);
      // ruhiger Schleier, damit die Liste klar lesbar ist
      ctx.fill(0, 0, w, h, a(0x05060F, 0.45F));
      int cw = Math.min(520, w - 60);
      int cx = (w - cw) / 2;
      // Kopfzeile: Zurück links, Titel mittig
      this.pill(ctx, cx, 16, "‹ Zurück", mouseX, mouseY, in, this::onClose);
      Component title = Component.literal("MEHRSPIELER").withStyle(ChatFormatting.BOLD);
      ctx.drawString(this.font, title, w / 2 - this.font.width(title) / 2, 18, a(0xF3E9FF, in), false);
      String count = this.servers.size() + (this.servers.size() == 1 ? " Server" : " Server");
      ctx.drawString(this.font, count, cx + cw - this.font.width(count), 21, a(0x8E86B0, in), false);
      ctx.fill(cx, 40, cx + cw, 41, a(0x8B5CF6, 0.35F * in));
      int listTop = 48;
      int buttonsY = h - 34;
      // Knöpfe zuerst, damit sie auch dann klickbar bleiben, wenn beim Zeichnen der Liste etwas schiefgeht
      try {
         this.buttons(ctx, cx, cw, buttonsY, t, mouseX, mouseY);
      } catch (RuntimeException | LinkageError var14) {
         if (REPORTED.add("Knöpfe")) {
            com.lennart.nebula.NebulaClient.LOGGER.warn("Meowl-Mehrspieler: Knöpfe", var14);
         }
      }

      try {
         this.serverList(ctx, cx, listTop, cw, buttonsY - 10 - listTop, t, in, mouseX, mouseY);
      } catch (RuntimeException | LinkageError var13) {
         try {
            ctx.disableScissor();
         } catch (RuntimeException var12) {
         }

         if (REPORTED.add("Liste")) {
            com.lennart.nebula.NebulaClient.LOGGER.warn("Meowl-Mehrspieler: Liste", var13);
         }
      }
   }
   private void serverList(GuiGraphics ctx, int x, int y, int w, int h, float t, float in, int mx, int my) {
      Render2D.roundRect(ctx, x, y, w, h, 10, a(0x0B0E1C, 0.72F * in));
      this.listX = x + 6;
      this.listY = y + 6;
      this.listW = w - 12;
      this.listH = h - 12;
      int cardH = 36;
      int gap = 4;
      this.maxScroll = Math.max(0, this.servers.size() * (cardH + gap) - gap - this.listH);
      this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll, this.scrollTarget));
      if (this.servers.isEmpty()) {
         ctx.drawCenteredString(this.font, "Noch keine Server", x + w / 2, y + h / 2 - 10, a(0xCFC8E8, in));
         ctx.drawCenteredString(this.font, "unten mit „Hinzufügen“ eintragen", x + w / 2, y + h / 2 + 2, a(0x7E76A6, in));
         return;
      }

      ctx.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
      // Reihenfolge beim Ziehen: gezogener Server an der Zielstelle, die anderen rücken weich nach
      List<ServerData> order = new ArrayList<>(this.servers);
      if (this.dragging != null) {
         this.dragTarget = Math.max(0, Math.min(this.servers.size() - 1, (int)Math.floor((my - this.dragGrab + CARD_H / 2.0 - this.listY + this.scroll) / (CARD_H + CARD_GAP))));
         order.remove(this.dragging);
         order.add(this.dragTarget, this.dragging);
      }

      for (int i = 0; i < order.size(); i++) {
         ServerData d = order.get(i);
         float s = this.slot.getOrDefault(d, (float)i);
         s += (i - s) * 0.3F;
         this.slot.put(d, s);
         if (d == this.dragging) {
            continue;
         }

         int idx = this.servers.indexOf(d);
         float cin = MeowlTitleScreen.ease((t - 0.1F - idx * 0.04F) / 0.35F) * in;
         int cy = Math.round(this.listY + s * (cardH + gap) - this.scroll + (1.0F - cin) * 8.0F);
         if (cy + cardH < this.listY || cy > this.listY + this.listH) {
            continue;
         }

         boolean inside = my >= this.listY && my < this.listY + this.listH;
         boolean hov = this.dragging == null && inside && Render2D.inside(mx, my, this.listX, cy, this.listW, cardH);
         int hi = Math.min(Math.max(0, idx), this.cardHover.length - 1);
         this.cardHover[hi] += ((hov ? 1.0F : 0.0F) - this.cardHover[hi]) * 0.3F;
         this.card(ctx, d, this.listX, cy, this.listW, cardH, this.cardHover[hi], d == this.selected, cin, t);
         int top = Math.max(this.listY, cy);
         int bottom = Math.min(this.listY + this.listH, cy + cardH);
         if (bottom > top && this.dragging == null) {
            this.hits.add(new MeowlMultiplayerScreen.Hit(this.listX, top, this.listW, bottom - top, () -> this.clickServer(d)));
         }
      }

      if (this.dragging != null) {
         // gezogene Karte folgt der Maus, leicht angehoben
         int dy = (int)Math.round(Math.max(this.listY - 6, Math.min(this.listY + this.listH - cardH + 6, my - this.dragGrab)));
         Render2D.glow(ctx, this.listX, dy, this.listW, cardH, 8, 0xB57BFF, 0.7F);
         this.card(ctx, this.dragging, this.listX + 4, dy, this.listW - 8, cardH, 1.0F, true, 1.0F, t);
      }
      ctx.disableScissor();
      if (this.maxScroll > 0) {
         int barH = Math.max(18, this.listH * this.listH / (this.listH + this.maxScroll));
         int barY = this.listY + Math.round((this.listH - barH) * (this.scroll / this.maxScroll));
         Render2D.roundRect(ctx, x + w - 4, barY, 2, barH, 1, 0x60B8A8FF);
      }
   }

   private void clickServer(ServerData d) {
      long now = System.currentTimeMillis();
      if (d == this.selected && now - this.lastClick < 350L) {
         this.join(d);
      }

      this.selected = d;
      this.lastClick = now;
   }

   /** Schlichte Server-Karte: Symbol, Name, MOTD, rechts Ping und Spieler. */
   private void card(GuiGraphics ctx, ServerData d, int x, int y, int w, int h, float hv, boolean sel, float in, float t) {
      if (sel) {
         Render2D.roundRect(ctx, x, y, w, h, 7, a(ACCENT & 16777215, 0.85F * in));
         Render2D.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 6, a(0x221B42, in));
      } else {
         Render2D.roundRect(ctx, x, y, w, h, 7, a(MeowlTitleScreen.mix(0xFF131726, 0xFF1D2036, hv) & 16777215, in));
      }

      int seed = d.name == null ? 0 : d.name.hashCode();
      int col = java.awt.Color.HSBtoRGB((seed & 0xFFFF) / 65535.0F, 0.5F, 0.9F);
      float acx = x + 19;
      float acy = y + h / 2.0F;
      net.minecraft.resources.Identifier icon = this.icon(d);
      if (icon != null) {
         int s = 24;
         int ix = Math.round(acx) - s / 2;
         int iy = Math.round(acy) - s / 2;
         Render2D.roundRect(ctx, ix - 1, iy - 1, s + 2, s + 2, 4, a(0x000000, 0.35F * in));
         ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0.0F, 0.0F, s, s, 64, 64, 64, 64);
      } else {
         MeowlSpace.disc(ctx, acx, acy, 11.0F, a(col & 16777215, in));
         String letter = d.name == null || d.name.isEmpty() ? "?" : d.name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
         ctx.drawCenteredString(this.font, Component.literal(letter).withStyle(ChatFormatting.BOLD), Math.round(acx), Math.round(acy) - 4, a(0xFFFFFF, in));
      }
      int tx = x + 38;
      int tw = w - 38 - 84;
      ctx.drawString(this.font, this.font.plainSubstrByWidth(d.name == null ? d.ip : d.name, tw), tx, y + 7, a(0xF2EEFF, in), false);
      if (d.motd != null) {
         List<FormattedCharSequence> lines = this.font.split(d.motd, Math.max(20, tw));
         if (!lines.isEmpty()) {
            ctx.drawString(this.font, lines.get(0), tx, y + 20, a(0x8E88AE, in), false);
         }
      }

      ServerData.State st = d.state();
      int bars;
      int barCol;
      String info;
      if (st == ServerData.State.SUCCESSFUL) {
         bars = d.ping < 100L ? 5 : (d.ping < 200L ? 4 : (d.ping < 400L ? 3 : 2));
         barCol = bars >= 4 ? 0xFF5BE38A : (bars == 3 ? 0xFFFFC857 : 0xFFFF7A5A);
         info = d.players != null ? d.players.online() + " / " + d.players.max() : d.ping + " ms";
      } else if (st == ServerData.State.PINGING || st == ServerData.State.INITIAL) {
         bars = 1 + (int)(t * 6.0F) % 5;
         barCol = 0xFF6E6890;
         info = "lädt …";
      } else if (st == ServerData.State.INCOMPATIBLE) {
         bars = 0;
         barCol = 0xFFFFC857;
         info = "andere Version";
      } else {
         bars = 0;
         barCol = 0xFFFF5F6D;
         info = "offline";
      }

      int bx = x + w - 30;
      int by = y + 7;
      for (int i = 0; i < 5; i++) {
         int bh = 2 + i * 2;
         ctx.fill(bx + i * 4, by + 10 - bh, bx + i * 4 + 3, by + 10, a((i < bars ? barCol : 0xFF2E2A44) & 16777215, in));
      }

      if (st == ServerData.State.SUCCESSFUL && d.ping >= 0L) {
         String ms = d.ping + " ms";
         ctx.drawString(this.font, ms, bx - 6 - this.font.width(ms), by + 2, a(0x6E6890, in), false);
      }

      ctx.drawString(this.font, info, x + w - 10 - this.font.width(info), y + 21, a(st == ServerData.State.SUCCESSFUL ? 0xB8B2D6 : barCol & 16777215, in), false);
   }

   /** Gleich breite Knöpfe unter der Liste; „Verbinden“ hervorgehoben. */
   private void buttons(GuiGraphics ctx, int x0, int width, int y, float t, int mx, int my) {
      float in = MeowlTitleScreen.ease((t - 0.2F) / 0.4F);
      boolean has = this.selected != null;
      String[] labels = {"Verbinden", "Direkt", "Hinzufügen", "Bearbeiten", "Löschen", "Aktualisieren"};
      Runnable[] actions = {() -> this.join(this.selected), this::direct, this::add, this::edit, this::delete, this::reload};
      boolean[] enabled = {has, true, true, has, has, true};
      int gap = 5;
      int bw = (width - gap * (labels.length - 1)) / labels.length;
      for (int i = 0; i < labels.length; i++) {
         int x = x0 + i * (bw + gap);
         boolean hov = enabled[i] && Render2D.inside(mx, my, x, y, bw, 22);
         this.buttonHover[i] += ((hov ? 1.0F : 0.0F) - this.buttonHover[i]) * 0.3F;
         float hv = this.buttonHover[i];
         float alpha = in * (enabled[i] ? 1.0F : 0.4F);
         int bg = i == 0 ? MeowlTitleScreen.mix(0xFF6D45D8, 0xFF8B5CF6, hv) : MeowlTitleScreen.mix(0xFF161A2C, 0xFF262B46, hv);
         Render2D.roundRect(ctx, x, y, bw, 22, 7, a(bg & 16777215, 0.95F * alpha));
         String label = this.font.plainSubstrByWidth(labels[i], bw - 6);
         ctx.drawString(this.font, label, x + (bw - this.font.width(label)) / 2, y + 7, a(0xF3E9FF, alpha), false);
         if (enabled[i]) {
            this.hits.add(new MeowlMultiplayerScreen.Hit(x, y, bw, 22, actions[i]));
         }
      }
   }
   private void pill(GuiGraphics ctx, int x, int y, String text, int mx, int my, float in, Runnable action) {
      int pw = this.font.width(text) + 14;
      boolean hov = Render2D.inside(mx, my, x, y, pw, 18);
      Render2D.roundRect(ctx, x, y, pw, 18, 9, a(hov ? 0x8B5CF6 : 0x262E46, 0.85F * in));
      ctx.drawString(this.font, text, x + 7, y + 5, a(0xFFFFFF, in), false);
      this.hits.add(new MeowlMultiplayerScreen.Hit(x, y, pw, 18, action));
   }

   // ================================================================== Eingabe

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.clickFallback.onEvent();
      if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(click.x(), click.y(), this.listX, this.listY, this.listW, this.listH)) {
         int idx = (int)Math.floor((click.y() - this.listY + this.scroll) / (CARD_H + CARD_GAP));
         if (idx >= 0 && idx < this.servers.size()) {
            this.pressed = this.servers.get(idx);
            this.pressY = click.y();
            this.dragGrab = click.y() - (this.listY + idx * (CARD_H + CARD_GAP) - this.scroll);
         }
      }
      if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            MeowlMultiplayerScreen.Hit hit = this.hits.get(i);
            if (Render2D.inside(click.x(), click.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
               hit.action().run();
               return true;
            }
         }

         MeowlSpace.click((float)click.x(), (float)click.y());
         return true;
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      if (this.pressed != null && this.dragging == null && Math.abs(click.y() - this.pressY) > 4.0) {
         this.dragging = this.pressed;
         this.selected = this.pressed;
      }

      if (this.dragging != null) {
         // am Rand automatisch scrollen
         if (click.y() < this.listY + 10) {
            this.scrollTarget = Math.max(0.0F, this.scrollTarget - 6.0F);
         } else if (click.y() > this.listY + this.listH - 10) {
            this.scrollTarget = Math.min(this.maxScroll, this.scrollTarget + 6.0F);
         }

         return true;
      }

      return super.mouseDragged(click, dx, dy);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      this.pressed = null;
      if (this.dragging != null) {
         ServerData d = this.dragging;
         this.dragging = null;
         int from = this.servers.indexOf(d);
         int to = Math.max(0, Math.min(this.servers.size() - 1, this.dragTarget));
         if (from >= 0 && from != to) {
            // in der gespeicherten Liste Schritt für Schritt verschieben, dann speichern (Pings bleiben erhalten)
            int i = from;
            while (i != to) {
               int next = i + (to > i ? 1 : -1);
               this.list.swap(i, next);
               i = next;
            }

            this.list.save();
            this.servers.clear();
            for (int k = 0; k < this.list.size(); k++) {
               this.servers.add(this.list.get(k));
            }
         }

         return true;
      }

      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      if (Render2D.inside(mouseX, mouseY, this.listX, this.listY, this.listW, this.listH)) {
         this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll, this.scrollTarget - (float)vertical * 30.0F));
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      // Enter verbindet mit dem ausgewählten Server
      if ((input.key() == 257 || input.key() == 335) && this.selected != null) {
         this.join(this.selected);
         return true;
      }

      return super.keyPressed(input);
   }
}
