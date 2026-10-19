package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class KeybindSetting extends Setting<Bind> {
   public KeybindSetting(String name, String description, Bind defaultValue) {
      super(name, description, defaultValue);
   }

   protected Bind sanitize(Bind newValue) {
      return newValue == null ? Bind.NONE : newValue;
   }

   @Override
   public JsonElement toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("type", this.get().type().name());
      obj.addProperty("code", this.get().code());
      // Tastenname (z.B. "key.keyboard.b") – gilt unabhängig von der Minecraft-Version
      Bind b = this.get();
      if (!b.isNone()) {
         com.mojang.blaze3d.platform.InputConstants.Type t = b.type() == Bind.Type.MOUSE ? com.mojang.blaze3d.platform.InputConstants.Type.MOUSE : com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD;
         obj.addProperty("name", t.getOrCreate(b.code()).getName());
      }
      return obj;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonObject()) {
         try {
            JsonObject obj = json.getAsJsonObject();
            Bind.Type type = Bind.Type.valueOf(obj.get("type").getAsString());
            int code = obj.get("code").getAsInt();
            if (type != Bind.Type.NONE && obj.has("name")) {
               com.mojang.blaze3d.platform.InputConstants.Key key = com.mojang.blaze3d.platform.InputConstants.getKey(obj.get("name").getAsString());
               if (key != null && key.getValue() >= 0) {
                  code = key.getValue();
               }
            }

            this.set(type == Bind.Type.NONE ? Bind.NONE : new Bind(type, code));
         } catch (RuntimeException var5) {
         }
      }
   }
}
