package com.lennart.meowlserver.duel;

public record Vec3(double x, double y, double z) {
   public Vec3 add(Vec3 o) {
      return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z);
   }

   public Vec3 sub(Vec3 o) {
      return new Vec3(this.x - o.x, this.y - o.y, this.z - o.z);
   }

   public Vec3 mul(double f) {
      return new Vec3(this.x * f, this.y * f, this.z * f);
   }

   public double length() {
      return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
   }

   public double distance(Vec3 o) {
      return this.sub(o).length();
   }

   public Vec3 normalize() {
      double l = this.length();
      return l < 1.0E-9 ? new Vec3(0.0, 0.0, 0.0) : this.mul(1.0 / l);
   }

   public double[] toArray() {
      return new double[]{this.x, this.y, this.z};
   }
}
