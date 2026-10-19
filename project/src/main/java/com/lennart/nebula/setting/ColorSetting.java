package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ColorSetting extends Setting<Integer> {
   private final boolean alpha;

   public ColorSetting(String name, String description, int defaultArgb, boolean alpha) {
      super(name, description, defaultArgb);
      this.alpha = alpha;
   }

   public ColorSetting(String name, String description, int defaultArgb) {
      this(name, description, defaultArgb, true);
   }

   public boolean hasAlpha() {
      return this.alpha;
   }

   public int argb() {
      return this.get();
   }

   public int withOpacity(float opacity) {
      int a = Math.round((this.get() >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, opacity)));
      return a << 24 | this.get() & 16777215;
   }

   protected Integer sanitize(Integer newValue) {
      return newValue == null ? this.getDefault() : (this.alpha ? newValue : newValue | 0xFF000000);
   }

   public static String toHex(int argb) {
      return String.format("#%08X", argb);
   }

   public static Integer parseHex(String text) {
      if (text == null) {
         return null;
      } else {
         String s = text.trim();
         if (s.startsWith("#")) {
            s = s.substring(1);
         }

         try {
            if (s.length() == 6) {
               return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            }

            if (s.length() == 8) {
               return (int)Long.parseLong(s, 16);
            }
         } catch (NumberFormatException var3) {
         }

         return null;
      }
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(toHex(this.get()));
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive()) {
         Integer parsed = parseHex(json.getAsString());
         if (parsed != null) {
            this.set(parsed);
         }
      }
   }
}
