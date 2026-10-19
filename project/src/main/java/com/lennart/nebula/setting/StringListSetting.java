package com.lennart.nebula.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class StringListSetting extends Setting<List<String>> {
   private UnaryOperator<String> normalizer = t -> t.isBlank() ? null : t.trim();
   private String placeholder = "Eintrag hinzufügen …";
   private boolean editable;

   public StringListSetting(String name, String description, List<String> defaultValue) {
      super(name, description, List.copyOf(defaultValue));
   }

   protected List<String> sanitize(List<String> newValue) {
      if (newValue == null) {
         return List.of();
      } else {
         Set<String> unique = new LinkedHashSet<>(newValue);
         return Collections.unmodifiableList(new ArrayList<>(unique));
      }
   }

   public StringListSetting normalizer(UnaryOperator<String> normalizer, String placeholder) {
      this.normalizer = normalizer;
      this.placeholder = placeholder;
      this.editable = true;
      return this;
   }

   public boolean isEditable() {
      return this.editable;
   }

   public String normalize(String input) {
      return input == null ? null : this.normalizer.apply(input.trim());
   }

   public String placeholder() {
      return this.placeholder;
   }

   public void addEntry(String entry) {
      List<String> list = new ArrayList<>(this.get());
      list.add(entry);
      this.set(list);
   }

   public void removeEntry(String entry) {
      List<String> list = new ArrayList<>(this.get());
      list.remove(entry);
      this.set(list);
   }

   public static String itemId(String text) {
      String t = text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      Identifier id = Identifier.tryParse(t.contains(":") ? t : "minecraft:" + t);
      return id != null && BuiltInRegistries.ITEM.containsKey(id) ? id.toString() : null;
   }

   public boolean contains(String entry) {
      return this.get().contains(entry);
   }

   public boolean toggle(String entry) {
      List<String> list = new ArrayList<>(this.get());
      boolean added;
      if (list.remove(entry)) {
         added = false;
      } else {
         list.add(entry);
         added = true;
      }

      this.set(list);
      return added;
   }

   public void clear() {
      this.set(List.of());
   }

   @Override
   public JsonElement toJson() {
      JsonArray array = new JsonArray();
      this.get().forEach(array::add);
      return array;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonArray()) {
         List<String> list = new ArrayList<>();

         for (JsonElement element : json.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
               list.add(element.getAsString());
            }
         }

         this.set(list);
      }
   }
}
