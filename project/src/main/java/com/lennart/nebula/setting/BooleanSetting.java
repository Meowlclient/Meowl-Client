package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanSetting extends Setting<Boolean> {
   public BooleanSetting(String name, String description, boolean defaultValue) {
      super(name, description, defaultValue);
   }

   public void toggle() {
      this.set(!this.get());
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.get());
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
         this.set(json.getAsBoolean());
      }
   }
}
