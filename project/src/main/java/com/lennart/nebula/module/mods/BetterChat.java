package com.lennart.nebula.module.mods;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;

/** Längerer Chat-Verlauf, Filter, Hervorhebungen und ein zweiter Chat für gefilterte Nachrichten. */
public class BetterChat extends Module {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
   private static final String TO_SECOND = "In 2. Chat verschieben";
   private static final String HIDE = "Ganz ausblenden";
   private final NumberSetting history = this.add(
      new NumberSetting("Chat-Verlauf", "Wie viele Nachrichten der Chat behält (Vanilla: 100)", 1000.0, 100.0, 5000.0, 100.0, "")
   );
   private final NumberSetting sentHistory = this.add(
      new NumberSetting("Gesendete merken", "Wie viele eigene Nachrichten/Befehle du mit Pfeil-hoch wiederholen kannst (Vanilla: 100)", 500.0, 100.0, 2000.0, 50.0, "")
   );
   private final BooleanSetting timestamps = this.add(new BooleanSetting("Uhrzeit", "Zeigt vor jeder Nachricht die Uhrzeit [12:34]", false));
   private final StringListSetting words = this.add(
      new StringListSetting("Gefilterte Wörter", "Nachrichten mit diesen Wörtern werden gefiltert (Groß-/Kleinschreibung egal)", List.of())
         .normalizer(t -> t.isBlank() ? null : t.trim(), "Wort hinzufügen …")
   );
   private final StringListSetting players = this.add(
      new StringListSetting("Gefilterte Spieler", "Nachrichten dieser Spieler werden gefiltert", List.of())
         .normalizer(t -> t.isBlank() ? null : t.trim(), "Spielername …")
   );
   private final BooleanSetting joinLeave = this.add(new BooleanSetting("Join/Leave filtern", "„X hat das Spiel betreten/verlassen“ filtern", false));
   private final BooleanSetting deaths = this.add(new BooleanSetting("Todesmeldungen filtern", "„X wurde von Y getötet“ usw. filtern", false));
   private final ModeSetting filterMode = this.add(
      new ModeSetting("Gefiltert wird", "Wohin gefilterte Nachrichten kommen", TO_SECOND, TO_SECOND, HIDE)
   );
   private final StringListSetting highlights = this.add(
      new StringListSetting("Hervorheben", "Nachrichten mit diesen Wörtern bekommen eine Markierung (z.B. dein Name)", List.of())
         .normalizer(t -> t.isBlank() ? null : t.trim(), "Wort hinzufügen …")
   );
   private final BooleanSetting highlightOwnName = this.add(new BooleanSetting("Eigenen Namen hervorheben", "Markiert Nachrichten, in denen dein Name vorkommt", true));
   private final ColorSetting highlightColor = this.add(new ColorSetting("Markierungs-Farbe", "Farbe der Markierung", -10929));
   private final BooleanSetting highlightSound = this.add(new BooleanSetting("Ping-Sound", "Kurzer Ton bei einer hervorgehobenen Nachricht", true));
   private final NumberSetting secondLines = this.add(new NumberSetting("Zeilen", "Wie viele Zeilen der 2. Chat zeigt", 6.0, 2.0, 20.0, 1.0, ""));
   private final NumberSetting secondLinesOpen = this.add(
      new NumberSetting("Zeilen bei offenem Chat", "Mehr Zeilen, solange du den Chat geöffnet hast", 14.0, 2.0, 40.0, 1.0, "")
   );
   private final NumberSetting secondWidth = this.add(new NumberSetting("Breite", "Breite des 2. Chats", 220.0, 120.0, 400.0, 10.0, "px"));
   private final ModeSetting secondShow = this.add(
      new ModeSetting("Anzeigen", "Wann der 2. Chat zu sehen ist", "Wie Chat", "Wie Chat", "Immer", "Nur bei offenem Chat")
   );
   private final ColorSetting secondAccent = this.add(new ColorSetting("Akzent", "Farbe der Titelleiste", Section.CLIENT.color()));
   private final HudBox box = this.hudBox("2. Chat (gefiltert)", 98.0, 70.0).reflow();
   private final Deque<BetterChat.Entry> filtered = new ArrayDeque<>();
   private int unseen;

   public BetterChat() {
      super("Better Chat", "Längerer Chat-Verlauf, Filter für Wörter/Spieler, Hervorhebungen und ein 2. Chat, in dem gefilterte Nachrichten landen.", Category.MISC);
      this.section(Section.CLIENT);
      this.icon(Items.WRITABLE_BOOK);
      this.group("Verlauf", new Setting[]{this.history, this.sentHistory, this.timestamps});
      this.group("Filter", new Setting[]{this.words, this.players, this.joinLeave, this.deaths, this.filterMode});
      this.group("Hervorheben", new Setting[]{this.highlights, this.highlightOwnName, this.highlightColor, this.highlightSound});
      this.group(
         "2. Chat",
         new Setting[]{
            this.secondShow,
            this.secondLines,
            this.secondLinesOpen,
            this.secondWidth,
            this.secondAccent,
            this.box.scale,
            this.box.opacity,
            this.box.background,
            this.box.backgroundColor,
            this.box.rounding
         }
      );
   }

   public static BetterChat get() {
      NebulaClient c = NebulaClient.getInstance();
      BetterChat b = c == null ? null : c.getModuleManager().get(BetterChat.class);
      return b != null && b.isEnabled() ? b : null;
   }

   /** Maximale Anzahl Chat-Nachrichten (für den Mixin). */
   public static int maxMessages(int vanilla) {
      BetterChat b = get();
      return b == null ? vanilla : Math.max(vanilla, b.history.getInt());
   }

   public static int maxSent(int vanilla) {
      BetterChat b = get();
      return b == null ? vanilla : Math.max(vanilla, b.sentHistory.getInt());
   }

   /** Ergebnis für eine eingehende Nachricht: null = wegfiltern, sonst die (evtl. veränderte) Nachricht. */
   public Component process(Component message) {
      String plain = message.getString();
      String lower = plain.toLowerCase(Locale.ROOT);
      if (this.isFiltered(message, lower)) {
         if (this.filterMode.is(TO_SECOND)) {
            // dieselben Einstellungen wie im normalen Chat: Hervorhebung, Ping und Uhrzeit
            this.filtered.addFirst(new BetterChat.Entry(this.stamp(this.highlight(message, lower), true), System.currentTimeMillis()));

            while (this.filtered.size() > 300) {
               this.filtered.removeLast();
            }

            if (!(this.mc.gui.screen() instanceof ChatScreen)) {
               this.unseen++;
            }
         }

         return null;
      } else {
         Component out = this.highlight(message, lower);
         return this.timestamps.get() ? this.stamp(out, false) : out;
      }
   }

   private Component highlight(Component message, String lower) {
      if (!this.isHighlighted(lower)) {
         return message;
      } else {
         if (this.highlightSound.get() && this.mc.player != null) {
            this.mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.6F);
         }

         return Component.literal("▌ ").withStyle(s -> s.withColor(this.highlightColor.get() & 16777215)).append(message);
      }
   }

   private Component stamp(Component message, boolean force) {
      if (!force && !this.timestamps.get()) {
         return message;
      } else {
         MutableComponent t = Component.literal("[" + LocalTime.now().format(TIME) + "] ").withStyle(ChatFormatting.DARK_GRAY);
         return t.append(message);
      }
   }

   private boolean isFiltered(Component message, String lower) {
      if (message.getContents() instanceof TranslatableContents tr) {
         String key = tr.getKey();
         if (this.joinLeave.get() && (key.equals("multiplayer.player.joined") || key.equals("multiplayer.player.joined.renamed") || key.equals("multiplayer.player.left"))) {
            return true;
         }

         if (this.deaths.get() && key.startsWith("death.")) {
            return true;
         }
      }

      for (String w : this.words.get()) {
         if (!w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) {
            return true;
         }
      }

      for (String p : this.players.get()) {
         if (p.isBlank()) {
            continue;
         }

         String name = p.toLowerCase(Locale.ROOT);
         // übliche Formate: "<Name> text", "Name: text", "[Rang] Name » text", Privatnachrichten "Name flüstert dir zu"
         if (lower.startsWith("<" + name + ">") || lower.matches("^(\\[[^\\]]*\\]\\s*)*" + java.util.regex.Pattern.quote(name) + "\\s*(:|»|>|\\||-|flüstert|whispers).*")) {
            return true;
         }
      }

      return false;
   }

   private boolean isHighlighted(String lower) {
      if (this.highlightOwnName.get() && this.mc.player != null) {
         String me = this.mc.player.getGameProfile().name().toLowerCase(Locale.ROOT);
         // die eigene Nachricht ("<Ich> ...") nicht markieren
         if (lower.contains(me) && !lower.startsWith("<" + me + ">")) {
            return true;
         }
      }

      for (String w : this.highlights.get()) {
         if (!w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = HudLayout.isEditing();
      if (!this.filterMode.is(TO_SECOND) || this.mc.gui.hud.isHidden() && !editing) {
         return;
      }

      boolean chatOpen = this.mc.gui.screen() instanceof ChatScreen;
      if (chatOpen) {
         this.unseen = 0;
      }

      if (this.secondShow.is("Nur bei offenem Chat") && !chatOpen && !editing) {
         return;
      }

      long now = System.currentTimeMillis();
      boolean always = this.secondShow.is("Immer") || chatOpen || editing;
      Font tr = this.mc.font;
      int w = this.box.layoutWidth(this.secondWidth.getInt());
      // Chat-Einstellungen von Minecraft (Deckkraft der Schrift, Zeilenabstand) wie im normalen Chat
      float textOpacity = (float)(this.mc.options.chatOpacity().get() * 0.9 + 0.1);
      int lineH = Math.max(8, Math.round(9.0F * (float)(1.0 + this.mc.options.chatLineSpacing().get())));
      int maxLines = chatOpen ? this.secondLinesOpen.getInt() : this.secondLines.getInt();
      List<FormattedCharSequence> lines = new ArrayList<>();
      List<Float> alphas = new ArrayList<>();

      for (BetterChat.Entry e : this.filtered) {
         float a = always ? 1.0F : fade(now - e.time());
         if (a <= 0.0F) {
            break;
         }

         List<FormattedCharSequence> wrapped = tr.split(e.text(), w - 8);

         for (int i = wrapped.size() - 1; i >= 0 && lines.size() < maxLines; i--) {
            lines.add(0, wrapped.get(i));
            alphas.add(0, a);
         }

         if (lines.size() >= maxLines) {
            break;
         }
      }

      if (lines.isEmpty() && !always) {
         return;
      }

      float headA = lines.isEmpty() ? 1.0F : alphas.get(alphas.size() - 1);
      for (float a : alphas) {
         headA = Math.max(headA, a);
      }

      int h = 13 + Math.max(1, lines.size()) * lineH + 3;
      this.box.begin(ctx, w, h, false);
      if (this.box.background.get()) {
         int bg = this.box.backgroundColor.get();
         int bgA = Math.round((bg >>> 24 & 0xFF) * this.box.alpha() * headA);
         com.lennart.nebula.gui.Render2D.roundRect(ctx, 0, 0, w, h, Math.min(this.box.rounding.getInt(), h / 2), bgA << 24 | bg & 16777215);
      }

      int accent = this.secondAccent.get();
      ctx.fill(0, 0, w, 11, alpha(accent & 16777215 | 0x55000000, this.box.alpha() * headA));
      ctx.fill(0, 11, w, 12, alpha(accent, this.box.alpha() * headA));
      String title = "Gefiltert" + (this.filtered.isEmpty() ? "" : " · " + this.filtered.size()) + (this.unseen > 0 ? "  (+" + this.unseen + " neu)" : "");
      ctx.text(tr, title, 4, 2, alpha(-1, this.box.alpha() * headA), true);
      if (lines.isEmpty()) {
         ctx.text(tr, "Noch nichts gefiltert", 4, 14, alpha(-7564628, this.box.alpha()), false);
      }

      for (int i = 0; i < lines.size(); i++) {
         ctx.text(tr, lines.get(i), 4, 14 + i * lineH, alpha(-1, this.box.alpha() * alphas.get(i) * textOpacity), true);
      }

      this.box.end(ctx);
   }

   /** Wie Vanilla: 10 s sichtbar, dann 1 s ausblenden. */
   private static float fade(long age) {
      if (age < 10000L) {
         return 1.0F;
      } else {
         return age > 11000L ? 0.0F : 1.0F - (age - 10000L) / 1000.0F;
      }
   }

   private static int alpha(int argb, float a) {
      int al = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, a)));
      return al << 24 | argb & 16777215;
   }

   public void clearFiltered() {
      this.filtered.clear();
      this.unseen = 0;
   }

   private record Entry(Component text, long time) {
   }
}
