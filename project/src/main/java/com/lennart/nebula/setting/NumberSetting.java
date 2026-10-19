package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public class NumberSetting extends Setting<Double> {
   private final double min;
   private final double max;
   private final double step;
   private final String unit;

   public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String unit) {
      super(name, description, defaultValue);
      this.min = min;
      this.max = max;
      this.step = step;
      this.unit = unit == null ? "" : unit;
      this.set(defaultValue);
   }

   protected Double sanitize(Double newValue) {
      if (newValue != null && !newValue.isNaN()) {
         double clamped = Math.max(this.min, Math.min(this.max, newValue));
         if (this.step > 0.0) {
            clamped = this.min + Math.round((clamped - this.min) / this.step) * this.step;
         }

         return Math.round(clamped * 1000.0) / 1000.0;
      } else {
         return this.getDefault();
      }
   }

   public int getInt() {
      return (int)Math.round(this.get());
   }

   public double getMin() {
      return this.min;
   }

   public double getMax() {
      return this.max;
   }

   public double getStep() {
      return this.step;
   }

   public String format() {
      boolean whole = this.step >= 1.0 && this.step == Math.floor(this.step);
      String number = whole ? String.valueOf(this.getInt()) : String.format(Locale.ROOT, "%.1f", this.get());
      return this.unit.isEmpty() ? number : number + " " + this.unit;
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.get());
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
         this.set(json.getAsDouble());
      }
   }
}
