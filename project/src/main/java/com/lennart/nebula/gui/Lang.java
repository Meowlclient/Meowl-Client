package com.lennart.nebula.gui;

import com.lennart.nebula.NebulaClient;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprache des Meowl Clients (Deutsch/Englisch). Die Texte im Code sind deutsch; im englischen Modus werden sie
 * beim Anzeigen über die Tabellen in assets/nebula/i18n/*.tsv (deutsch TAB englisch) übersetzt.
 */
public final class Lang {
   private static final Pattern COLOR = Pattern.compile("§.");
   private static final String TRIM = " :()–-·….,!?[]/|»→‹›+\"„“";
   private static volatile boolean english;
   private static Map<String, String> exact;
   private static Pattern phrases;
   private static Map<String, String> phraseMap;
   private static final Map<String, String> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(512, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
         return this.size() > 4096;
      }
   });

   private Lang() {
   }

   public static boolean english() {
      return english;
   }

   public static void setEnglish(boolean on) {
      if (on != english) {
         english = on;
         CACHE.clear();
      }
   }

   /** Exakte Übersetzung eines festen Textes (z.B. Modulname). */
   public static String t(String de) {
      if (!english) {
         return german(de);
      } else if (de == null || de.isEmpty()) {
         return de;
      } else {
         load();
         String en = exact.get(de);
         return en != null ? en : tr(de);
      }
   }

   /** Übersetzt beliebige Texte: Farbcodes bleiben erhalten, bekannte Teile und Wörter werden ersetzt. */
   public static String tr(String de) {
      if (!english) {
         return german(de);
      } else if (de == null || de.length() < 2 || !hasLetters(de)) {
         return de;
      }

      String cached = CACHE.get(de);
      if (cached != null) {
         return cached;
      }

      load();
      String out = translate(de);
      CACHE.put(de, out);
      return out;
   }

   /** Wie {@link #tr}, aber ohne Wort-für-Wort-Ersatz (für fremde Texte, z.B. vom Server). */
   public static String exactOnly(String de) {
      if (!english || de == null || de.length() < 2) {
         return de;
      }

      load();
      String en = exact.get(de);
      if (en != null) {
         return en;
      } else {
         String[] c = core(de);
         String t = c == null ? null : exact.get(c[1]);
         return t != null ? c[0] + t + c[2] : de;
      }
   }

   private static Map<String, String> germanNames;

   /** Deutscher Modus: englische Modulnamen (z.B. „Watches“) auf Deutsch – nur ganze, bekannte Namen. */
   private static String german(String s) {
      if (s == null || s.length() < 3) {
         return s;
      }

      Map<String, String> g = germanNames;
      if (g == null) {
         g = loadGerman();
      }

      String d = g.get(s);
      return d != null ? d : s;
   }

   private static synchronized Map<String, String> loadGerman() {
      if (germanNames == null) {
         Map<String, String> map = new HashMap<>();
         InputStream in = Lang.class.getResourceAsStream("/assets/nebula/i18n/de_names.tsv");
         if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
               String line;

               while ((line = r.readLine()) != null) {
                  int tab = line.indexOf('\t');
                  if (tab > 0 && tab < line.length() - 1) {
                     map.put(line.substring(0, tab), line.substring(tab + 1));
                  }
               }
            } catch (Exception var6) {
               NebulaClient.LOGGER.warn("Deutsche Namen konnten nicht geladen werden: {}", var6.toString());
            }
         }

         germanNames = map;
      }

      return germanNames;
   }

   private static boolean hasLetters(String s) {
      for (int i = 0; i < s.length(); i++) {
         if (Character.isLetter(s.charAt(i))) {
            return true;
         }
      }

      return false;
   }

   private static String translate(String s) {
      String whole = exact.get(s);
      if (whole != null) {
         return whole;
      }

      StringBuilder out = new StringBuilder(s.length() + 16);
      Matcher m = COLOR.matcher(s);
      int last = 0;

      while (m.find()) {
         out.append(segment(s.substring(last, m.start())));
         out.append(m.group());
         last = m.end();
      }

      out.append(segment(s.substring(last)));
      return out.toString();
   }

   /** {vorne, Kern, hinten} – Satzzeichen/Leerzeichen am Rand bleiben stehen. */
   private static String[] core(String seg) {
      int a = 0;
      int b = seg.length();

      while (a < b && TRIM.indexOf(seg.charAt(a)) >= 0) {
         a++;
      }

      while (b > a && TRIM.indexOf(seg.charAt(b - 1)) >= 0) {
         b--;
      }

      return a >= b ? null : new String[]{seg.substring(0, a), seg.substring(a, b), seg.substring(b)};
   }

   private static String segment(String seg) {
      if (seg.isEmpty() || !hasLetters(seg)) {
         return seg;
      }

      String direct = exact.get(seg);
      if (direct != null) {
         return direct;
      }

      String[] c = core(seg);
      if (c == null) {
         return seg;
      }

      String en = exact.get(c[1]);
      if (en != null) {
         return c[0] + en + c[2];
      }

      // bekannte Wörter/Phrasen im Text ersetzen (längste zuerst, nur ganze Wörter)
      Matcher m = phrases.matcher(seg);
      StringBuilder sb = new StringBuilder();

      while (m.find()) {
         m.appendReplacement(sb, Matcher.quoteReplacement(phraseMap.get(m.group())));
      }

      m.appendTail(sb);
      return sb.toString();
   }

   private static synchronized void load() {
      if (exact == null) {
         Map<String, String> map = new HashMap<>();

         for (int i = 1; i <= 20; i++) {
            InputStream in = Lang.class.getResourceAsStream("/assets/nebula/i18n/part" + i + ".tsv");
            if (in == null) {
               break;
            }

            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
               String line;

               while ((line = r.readLine()) != null) {
                  int tab = line.indexOf('\t');
                  if (tab > 0 && tab < line.length() - 1) {
                     map.putIfAbsent(line.substring(0, tab), line.substring(tab + 1));
                  }
               }
            } catch (Exception var8) {
               NebulaClient.LOGGER.warn("Übersetzung part{} konnte nicht geladen werden: {}", i, var8.toString());
            }
         }

         List<String> keys = new ArrayList<>();
         Map<String, String> pm = new HashMap<>();

         for (Map.Entry<String, String> e : map.entrySet()) {
            String k = e.getKey();
            if (k.length() >= 4 && Character.isLetter(k.charAt(0)) && Character.isLetter(k.charAt(k.length() - 1))) {
               keys.add(k);
               pm.put(k, e.getValue());
            }
         }

         keys.sort((x, y) -> y.length() - x.length());
         StringBuilder re = new StringBuilder("(?<![\\p{L}\\d])(?:");

         for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
               re.append('|');
            }

            re.append(Pattern.quote(keys.get(i)));
         }

         re.append(")(?![\\p{L}\\d])");
         phraseMap = pm;
         phrases = Pattern.compile(re.toString());
         exact = map;
         NebulaClient.LOGGER.info("Meowl: {} englische Texte geladen", map.size());
      }
   }
}
