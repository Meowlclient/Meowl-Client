package com.lennart.nebula.module.mods.pet;

public record PetConfig(String skin, String dance, double speed, double size, double radius, boolean shadow, double lift) {
   public PetConfig(String skin, String dance, double speed, double size, double radius, boolean shadow) {
      this(skin, dance, speed, size, radius, shadow, 0.0);
   }

   public PetConfig sanitized() {
      boolean skinPet = SkinPet.is(this.skin) && SkinPet.validName(SkinPet.name(this.skin));
      String s = !"Katze".equals(this.skin) && !PetModels.has(this.skin) && !skinPet ? "Meowl (lila)" : this.skin;
      String var3 = this.dance == null ? "" : this.dance;

      String d = switch (var3) {
         case "Hüpfen", "Wackeln", "Drehen", "Aus" -> this.dance;
         default -> "Alles";
      };
      return new PetConfig(s, d, clamp(this.speed, 0.3, 3.0, 1.0), clamp(this.size, 0.3, 3.0, 0.8), clamp(this.radius, 2.0, 12.0, 4.0), this.shadow, clamp(this.lift, 0.0, 2.0, 0.0));
   }

   private static double clamp(double v, double min, double max, double def) {
      return Double.isNaN(v) ? def : Math.max(min, Math.min(max, v));
   }
}
