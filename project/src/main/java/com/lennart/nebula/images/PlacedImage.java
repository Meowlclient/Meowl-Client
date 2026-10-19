package com.lennart.nebula.images;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record PlacedImage(String id, String url, String server, String dim, BlockPos pos, Direction facing, int w, int h, String creator, boolean local) {
   public JsonObject toJson() {
      JsonObject o = new JsonObject();
      o.addProperty("id", this.id);
      o.addProperty("url", this.url);
      o.addProperty("server", this.server);
      o.addProperty("dim", this.dim);
      o.addProperty("x", this.pos.getX());
      o.addProperty("y", this.pos.getY());
      o.addProperty("z", this.pos.getZ());
      o.addProperty("facing", this.facing.getSerializedName());
      o.addProperty("w", this.w);
      o.addProperty("h", this.h);
      o.addProperty("creator", this.creator);
      return o;
   }

   public static PlacedImage fromJson(JsonObject o, boolean local) {
      try {
         Direction facing = Direction.byName(o.get("facing").getAsString());
         if (facing != null && !facing.getAxis().isVertical()) {
            int w = o.get("w").getAsInt();
            int h = o.get("h").getAsInt();
            return w >= 1 && h >= 1 && w <= 32 && h <= 32
               ? new PlacedImage(
                  o.get("id").getAsString(),
                  o.get("url").getAsString(),
                  o.has("server") ? o.get("server").getAsString() : "",
                  o.get("dim").getAsString(),
                  new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()),
                  facing,
                  w,
                  h,
                  o.has("creator") ? o.get("creator").getAsString() : "",
                  local
               )
               : null;
         } else {
            return null;
         }
      } catch (RuntimeException var51) {
         return null;
      }
   }
}
