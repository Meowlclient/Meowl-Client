package com.lennart.nebula.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;

public class ImageLibrarySetting extends Setting<ImageLibrarySetting.Library> {
   public static final int MAX = 64;
   private ImageLibrarySetting.Preview preview;

   public ImageLibrarySetting(String name, String description) {
      super(name, description, new ImageLibrarySetting.Library(List.of(), -1));
   }

   public void setPreview(ImageLibrarySetting.Preview preview) {
      this.preview = preview;
   }

   public ImageLibrarySetting.Preview preview() {
      return this.preview;
   }

   private void edit(Consumer<List<ImageLibrarySetting.Slot>> change, int selected) {
      List<ImageLibrarySetting.Slot> list = new ArrayList<>(this.get().slots());
      change.accept(list);
      this.set(new ImageLibrarySetting.Library(list, selected));
   }

   public boolean add(ImageLibrarySetting.Slot slot) {
      if (this.get().slots().size() >= 64) {
         return false;
      } else {
         this.edit(l -> l.add(slot), this.get().slots().size());
         return true;
      }
   }

   public void replace(int index, ImageLibrarySetting.Slot slot) {
      if (index >= 0 && index < this.get().slots().size()) {
         this.edit(l -> l.set(index, slot), this.get().selected());
      }
   }

   public void remove(int index) {
      if (index >= 0 && index < this.get().slots().size()) {
         int sel = this.get().selected();
         this.edit(l -> l.remove(index), sel > index ? sel - 1 : sel);
      }
   }

   public void move(int index, int delta) {
      int target = index + delta;
      if (index >= 0 && target >= 0 && index < this.get().slots().size() && target < this.get().slots().size()) {
         int sel = this.get().selected();
         int newSel = sel == index ? target : (sel == target ? index : sel);
         this.edit(l -> Collections.swap(l, index, target), newSel);
      }
   }

   public void select(int index) {
      this.set(new ImageLibrarySetting.Library(this.get().slots(), index));
   }

   @Override
   public JsonElement toJson() {
      JsonObject o = new JsonObject();
      o.addProperty("selected", this.get().selected());
      JsonArray arr = new JsonArray();

      for (ImageLibrarySetting.Slot s : this.get().slots()) {
         JsonObject e = new JsonObject();
         e.addProperty("url", s.url());
         e.addProperty("w", s.w());
         e.addProperty("h", s.h());
         e.addProperty("visible", s.visible());
         arr.add(e);
      }

      o.add("images", arr);
      return o;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonObject()) {
         try {
            JsonObject o = json.getAsJsonObject();
            List<ImageLibrarySetting.Slot> list = new ArrayList<>();

            for (JsonElement el : o.getAsJsonArray("images")) {
               JsonObject e = el.getAsJsonObject();
               String url = e.has("url") ? e.get("url").getAsString() : "";
               if (url.length() <= 512) {
                  list.add(
                     new ImageLibrarySetting.Slot(
                        url,
                        e.has("w") ? e.get("w").getAsInt() : 2,
                        e.has("h") ? e.get("h").getAsInt() : 2,
                        !e.has("visible") || e.get("visible").getAsBoolean()
                     )
                  );
               }
            }

            this.set(new ImageLibrarySetting.Library(list, o.has("selected") ? o.get("selected").getAsInt() : 0));
         } catch (RuntimeException var8) {
         }
      }
   }

   public record Library(List<ImageLibrarySetting.Slot> slots, int selected) {
      public Library(List<ImageLibrarySetting.Slot> slots, int selected) {
         slots = Collections.unmodifiableList(new ArrayList<>(slots.subList(0, Math.min(64, slots.size()))));
         selected = slots.isEmpty() ? -1 : Math.max(0, Math.min(slots.size() - 1, selected));
         this.slots = slots;
         this.selected = selected;
      }

      public ImageLibrarySetting.Slot current() {
         return this.selected >= 0 ? this.slots.get(this.selected) : null;
      }
   }

   public interface Preview {
      Identifier texture(String var1, StringBuilder var2, int[] var3);
   }

   public record Slot(String url, int w, int h, boolean visible) {
      public Slot(String url, int w, int h, boolean visible) {
         url = url == null ? "" : url.trim();
         w = Math.max(1, Math.min(32, w));
         h = Math.max(1, Math.min(32, h));
         this.url = url;
         this.w = w;
         this.h = h;
         this.visible = visible;
      }

      public ImageLibrarySetting.Slot withUrl(String u) {
         return new ImageLibrarySetting.Slot(u, this.w, this.h, this.visible);
      }

      public ImageLibrarySetting.Slot withSize(int nw, int nh) {
         return new ImageLibrarySetting.Slot(this.url, nw, nh, this.visible);
      }

      public ImageLibrarySetting.Slot withVisible(boolean v) {
         return new ImageLibrarySetting.Slot(this.url, this.w, this.h, v);
      }
   }
}
