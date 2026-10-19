package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.Predicate;

public class TextSetting extends Setting<String> {
   private final int maxLength;
   private Predicate<String> validator = s -> true;

   public TextSetting(String name, String description, String defaultValue, int maxLength) {
      super(name, description, defaultValue);
      this.maxLength = maxLength;
   }

   public int getMaxLength() {
      return this.maxLength;
   }

   public TextSetting validator(Predicate<String> validator) {
      this.validator = validator;
      return this;
   }

   public boolean isValid(String value) {
      return this.validator.test(value);
   }

   protected String sanitize(String newValue) {
      if (newValue == null) {
         return this.getDefault();
      } else {
         String clean = newValue.replaceAll("\\p{Cntrl}", "");
         return clean.length() > this.maxLength ? clean.substring(0, this.maxLength) : clean;
      }
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.get());
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive()) {
         this.set(json.getAsString());
      }
   }
}
