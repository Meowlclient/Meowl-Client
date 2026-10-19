package com.lennart.nebula.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Meowl-Emojis im Chat: im Text steht z.B. „:fire:“ – Meowl-Spieler sehen dort das Bild (eigene Schrift
 * {@code nebula:emoji}, Zeichen U+E900 …), alle anderen einfach den Text. Dazu Symbole und Text-Emoticons, die mit der
 * Minecraft-Schrift jeder sieht.
 */
public final class ChatEmojis {
   /** Reihenfolge = Bilder in textures/font/emoji.png (erzeugt von test/emoji/MakeEmoji.java). */
   public static final String[] NAMES = {
      "smile", "lol", "wink", "cool", "cry", "angry", "love", "shock", "clown", "heart", "fire", "skull", "like", "star", "crown", "100", "gg",
      "eyes", "ghost", "creeper", "diamond", "sword", "party", "sus", "meowl"
   };
   public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("nebula", "emoji"));
   private static final Style ICON = Style.EMPTY.withFont(FONT).withColor(ChatFormatting.WHITE);
   private static final Pattern TOKEN = Pattern.compile(":([a-z0-9_]{2,12}):");
   private static final Map<String, Integer> INDEX = new HashMap<>();
   /** Symbole aus der Minecraft-Schrift – sieht jeder. */
   public static final String[] SYMBOLS = {
      "❤", "♥", "♡", "★", "☆", "✦", "✧", "✪", "☀", "☁", "☂", "☃", "❄", "⚡", "☄", "♪", "♫", "☕", "⚔", "☠", "✔", "✘", "✓", "✗", "➤", "➜",
      "⬆", "⬇", "⬅", "➡", "☯", "☮", "♻", "⚠", "✿", "❀", "☘", "♠", "♣", "♦", "∞", "☺", "☹", "☻", "✌", "☝", "✍", "☎", "✉", "✂", "✈", "⚓",
      "⚑", "♔", "♛", "☢", "☣", "⌛", "©", "™"
   };
   /** Text-Emoticons – sieht jeder. */
   public static final String[] EMOTICONS = {
      "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "( ͡° ͜ʖ ͡°)", "ʕ•ᴥ•ʔ", "(◕‿◕)", "ಠ_ಠ", "(ಥ﹏ಥ)", "(づ◕‿◕)づ", "(ノ◕ヮ◕)ノ*:･ﾟ✧",
      "(¬‿¬)", "(✿◠‿◠)", "(⌐■_■)", "( •_•)>⌐■-■", "(•_•)", "(◡‿◡✿)", "(^_^)/", "\\o/", "o7", "UwU", "OwO", ":3", "<3", "xD", "GG", "ez"
   };

   static {
      for (int i = 0; i < NAMES.length; i++) {
         INDEX.put(NAMES[i], i);
      }
   }

   private ChatEmojis() {
   }

   public static String token(int i) {
      return ":" + NAMES[i] + ":";
   }

   public static String glyph(int i) {
      return String.valueOf((char)(0xE900 + i));
   }

   /** Ein Emoji als Text-Baustein (für Chat und Auswahl). */
   public static MutableComponent icon(int i) {
      return Component.literal(glyph(i)).withStyle(ICON);
   }

   /** „:name:“ in einer Nachricht durch Bilder ersetzen; ohne Treffer kommt dieselbe Nachricht zurück. */
   public static Component decorate(Component message) {
      if (message == null || !message.getString().contains(":")) {
         return message;
      }

      boolean[] found = {false};
      MutableComponent out = Component.empty();
      message.visit((style, text) -> {
         Matcher m = TOKEN.matcher(text);
         int last = 0;

         while (m.find()) {
            Integer idx = INDEX.get(m.group(1));
            if (idx == null) {
               continue;
            }

            found[0] = true;
            if (m.start() > last) {
               out.append(Component.literal(text.substring(last, m.start())).withStyle(style));
            }

            // Klick/Hover der Nachricht bleiben erhalten, nur Schrift und Farbe ändern sich
            out.append(Component.literal(glyph(idx)).withStyle(style.withFont(FONT).withColor(ChatFormatting.WHITE)));
            last = m.end();
         }

         if (last < text.length()) {
            out.append(Component.literal(text.substring(last)).withStyle(style));
         }

         return Optional.empty();
      }, Style.EMPTY);
      return found[0] ? out : message;
   }
}
