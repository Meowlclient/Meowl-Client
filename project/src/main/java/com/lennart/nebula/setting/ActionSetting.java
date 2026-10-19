package com.lennart.nebula.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

public class ActionSetting extends Setting<Boolean> {
   private final String buttonLabel;
   private final Runnable action;

   public ActionSetting(String name, String description, String buttonLabel, Runnable action) {
      super(name, description, false);
      this.buttonLabel = buttonLabel;
      this.action = action;
   }

   public String getButtonLabel() {
      return this.buttonLabel;
   }

   public void run() {
      this.action.run();
   }

   @Override
   public JsonElement toJson() {
      return JsonNull.INSTANCE;
   }

   @Override
   public void fromJson(JsonElement json) {
   }
}
