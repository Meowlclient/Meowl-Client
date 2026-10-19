package com.lennart.nebula.waypoints;

public final class Waypoint {
   public static final String[] SYMBOLS = new String[]{"★", "♦", "⚑", "⌂", "☠", "♥", "⚔", "●", "▲", "✚", "☀", "♣"};
   public static final int[] COLORS = new int[]{11561983, 5227519, 4906624, 16436245, 16281969, 16486972, 16020150, 16777215, 2282478, 10741301};
   public String name;
   public double x;
   public double y;
   public double z;
   public String dim;
   public int color;
   public String symbol;
   public boolean visible = true;

   public Waypoint(String name, double x, double y, double z, String dim, int color, String symbol) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
      this.dim = dim;
      this.color = color;
      this.symbol = symbol;
   }

   public void nextColor() {
      int i = 0;

      for (int k = 0; k < COLORS.length; k++) {
         if (COLORS[k] == this.color) {
            i = k + 1;
         }
      }

      this.color = COLORS[i % COLORS.length];
   }

   public void nextSymbol() {
      int i = 0;

      for (int k = 0; k < SYMBOLS.length; k++) {
         if (SYMBOLS[k].equals(this.symbol)) {
            i = k + 1;
         }
      }

      this.symbol = SYMBOLS[i % SYMBOLS.length];
   }
}
