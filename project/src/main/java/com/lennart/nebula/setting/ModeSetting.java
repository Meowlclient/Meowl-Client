package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;

public class ModeSetting extends Setting<String> {
   private final List<String> modes;

   public ModeSetting(String name, String description, String defaultMode, String... modes) {
      super(name, description, defaultMode);
      this.modes = List.of(modes);
      if (!this.modes.contains(defaultMode)) {
         throw new IllegalArgumentException("Default-Modus '" + defaultMode + "' fehlt in " + this.modes);
      }
   }

   public List<String> getModes() {
      return this.modes;
   }

   public boolean is(String mode) {
      return this.get().equals(mode);
   }

   public int getIndex() {
      return Math.max(0, this.modes.indexOf(this.get()));
   }

   protected String sanitize(String newValue) {
      return newValue != null && this.modes.contains(newValue) ? newValue : this.getDefault();
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.get());
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
         String value = json.getAsString();
         if (this.modes.contains(value)) {
            this.set(value);
         }
      }
   }
}
