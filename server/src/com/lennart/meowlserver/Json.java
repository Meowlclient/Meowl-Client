package com.lennart.meowlserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class Json {
   private static final int MAX_DEPTH = 32;
   private final String s;
   private int i;

   private Json(String s) {
      this.s = s;
   }

   public static Object parse(String text) {
      Json p = new Json(text);
      p.ws();
      Object v = p.value(0);
      p.ws();
      if (p.i != p.s.length()) {
         throw new IllegalArgumentException("Zeichen nach JSON-Ende");
      } else {
         return v;
      }
   }

   public static Map<String, Object> parseObject(String text) {
      Object v = parse(text);
      if (!(v instanceof Map)) {
         throw new IllegalArgumentException("JSON-Objekt erwartet");
      } else {
         return (Map<String, Object>)v;
      }
   }

   private Object value(int depth) {
      if (depth > 32) {
         throw new IllegalArgumentException("JSON zu tief verschachtelt");
      } else if (this.i >= this.s.length()) {
         throw new IllegalArgumentException("Unerwartetes Ende");
      } else {
         char c = this.s.charAt(this.i);

         return switch (c) {
            case '"' -> this.string();
            case '[' -> this.array(depth);
            case 'f' -> this.literal("false", Boolean.FALSE);
            case 'n' -> this.literal("null", null);
            case 't' -> this.literal("true", Boolean.TRUE);
            case '{' -> this.object(depth);
            default -> this.number();
         };
      }
   }

   private Map<String, Object> object(int depth) {
      Map<String, Object> map = new LinkedHashMap<>();
      this.i++;
      this.ws();
      if (this.peek('}')) {
         this.i++;
         return map;
      } else {
         while (true) {
            this.ws();
            String key = this.string();
            this.ws();
            this.expect(':');
            this.ws();
            map.put(key, this.value(depth + 1));
            this.ws();
            if (!this.peek(',')) {
               this.expect('}');
               return map;
            }

            this.i++;
         }
      }
   }

   private List<Object> array(int depth) {
      List<Object> list = new ArrayList<>();
      this.i++;
      this.ws();
      if (this.peek(']')) {
         this.i++;
         return list;
      } else {
         while (true) {
            this.ws();
            list.add(this.value(depth + 1));
            this.ws();
            if (!this.peek(',')) {
               this.expect(']');
               return list;
            }

            this.i++;
         }
      }
   }

   private String string() {
      this.expect('"');
      StringBuilder b = new StringBuilder();

      while (this.i < this.s.length()) {
         char c = this.s.charAt(this.i++);
         if (c == '"') {
            return b.toString();
         }

         if (c == '\\') {
            char e = this.s.charAt(this.i++);
            switch (e) {
               case '"':
               case '/':
               case '\\':
                  b.append(e);
                  break;
               case 'b':
                  b.append('\b');
                  break;
               case 'f':
                  b.append('\f');
                  break;
               case 'n':
                  b.append('\n');
                  break;
               case 'r':
                  b.append('\r');
                  break;
               case 't':
                  b.append('\t');
                  break;
               case 'u':
                  b.append((char)Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
                  this.i += 4;
                  break;
               default:
                  throw new IllegalArgumentException("Ungültiges Escape");
            }
         } else {
            b.append(c);
         }
      }

      throw new IllegalArgumentException("Unbeendeter String");
   }

   private Double number() {
      int start = this.i;

      while (this.i < this.s.length() && "+-0123456789.eE".indexOf(this.s.charAt(this.i)) >= 0) {
         this.i++;
      }

      if (start == this.i) {
         throw new IllegalArgumentException("Ungültiger Wert an Position " + this.i);
      } else {
         return Double.parseDouble(this.s.substring(start, this.i));
      }
   }

   private Object literal(String word, Object value) {
      if (!this.s.startsWith(word, this.i)) {
         throw new IllegalArgumentException("Ungültiger Wert");
      } else {
         this.i = this.i + word.length();
         return value;
      }
   }

   private boolean peek(char c) {
      return this.i < this.s.length() && this.s.charAt(this.i) == c;
   }

   private void expect(char c) {
      if (!this.peek(c)) {
         throw new IllegalArgumentException("'" + c + "' erwartet an Position " + this.i);
      } else {
         this.i++;
      }
   }

   private void ws() {
      while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
         this.i++;
      }
   }

   public static String write(Object value) {
      StringBuilder b = new StringBuilder();
      write(b, value);
      return b.toString();
   }

   private static void write(StringBuilder b, Object v) {
      if (v == null) {
         b.append("null");
      } else if (v instanceof String str) {
         b.append('"');

         for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
               case '\t':
                  b.append("\\t");
                  break;
               case '\n':
                  b.append("\\n");
                  break;
               case '\r':
                  b.append("\\r");
                  break;
               case '"':
                  b.append("\\\"");
                  break;
               case '\\':
                  b.append("\\\\");
                  break;
               default:
                  if (c < ' ') {
                     b.append(String.format("\\u%04x", Integer.valueOf(c)));
                  } else {
                     b.append(c);
                  }
            }
         }

         b.append('"');
      } else if (v instanceof Number n) {
         double d = n.doubleValue();
         if (!Double.isNaN(d) && !Double.isInfinite(d)) {
            if (d == Math.rint(d) && Math.abs(d) < 1.0E15) {
               b.append((long)d);
            } else {
               b.append(d);
            }
         } else {
            b.append("0");
         }
      } else if (v instanceof Boolean bool) {
         b.append(bool);
      } else if (v instanceof Map<?, ?> map) {
         b.append('{');
         boolean first = true;

         for (Entry<?, ?> e : map.entrySet()) {
            if (!first) {
               b.append(',');
            }

            first = false;
            write(b, String.valueOf(e.getKey()));
            b.append(':');
            write(b, e.getValue());
         }

         b.append('}');
      } else if (v instanceof Iterable<?> list) {
         b.append('[');
         boolean first = true;

         for (Object o : list) {
            if (!first) {
               b.append(',');
            }

            first = false;
            write(b, o);
         }

         b.append(']');
      } else {
         write(b, v.toString());
      }
   }

   public static String str(Map<String, Object> o, String key, int maxLen) {
      if (o.get(key) instanceof String s && !s.isEmpty() && s.length() <= maxLen) {
         return s;
      } else {
         throw new IllegalArgumentException("Feld '" + key + "' fehlt oder ist ungültig");
      }
   }

   public static double num(Map<String, Object> o, String key, double min, double max) {
      if (o.get(key) instanceof Double d && !d.isNaN() && !(d < min) && !(d > max)) {
         return d;
      } else {
         throw new IllegalArgumentException("Feld '" + key + "' fehlt oder liegt außerhalb von " + min + "…" + max);
      }
   }

   public static double num(Map<String, Object> o, String key, double min, double max, double def) {
      return o.get(key) == null ? def : num(o, key, min, max);
   }

   public static boolean bool(Map<String, Object> o, String key) {
      return Boolean.TRUE.equals(o.get(key));
   }
}
