package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
   private final String name;
   private String description;
   private final T defaultValue;
   private T value;
   private BooleanSupplier visibility = () -> true;
   private String group;
   private final List<String> aliases = new ArrayList<>();

   protected Setting(String name, String description, T defaultValue) {
      this.name = name;
      this.description = description;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
   }

   public String getName() {
      return this.name;
   }

   /** Name in der Oberfläche (Standard: der Name; einige Einstellungen zeigen einen wechselnden Namen). */
   public String getDisplayName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public T get() {
      return this.value;
   }

   public T getDefault() {
      return this.defaultValue;
   }

   public void set(T newValue) {
      this.value = this.sanitize(newValue);
   }

   public void reset() {
      this.set(this.defaultValue);
   }

   protected T sanitize(T newValue) {
      return newValue;
   }

   public void setVisibility(BooleanSupplier visibility) {
      this.visibility = visibility;
   }

   public String getGroup() {
      return this.group;
   }

   public void setGroup(String group) {
      this.group = group;
   }

   public <S extends Setting<T>> S alias(String... oldNames) {
      this.aliases.addAll(List.of(oldNames));
      return (S)this;
   }

   public List<String> getAliases() {
      return this.aliases;
   }

   public boolean isVisible() {
      return this.visibility.getAsBoolean();
   }

   public abstract JsonElement toJson();

   public abstract void fromJson(JsonElement var1);
}
