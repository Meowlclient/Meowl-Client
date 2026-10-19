package com.lennart.nebula.afk;

public final class AfkInput {
   private static AfkInput.Listener listener;
   private static double lastX = Double.NaN;
   private static double lastY;
   private static double moved;
   private static long windowStart;

   private AfkInput() {
   }

   public static void setListener(AfkInput.Listener l) {
      listener = l;
   }

   private static void fire(AfkInput.Kind kind, int code) {
      AfkInput.Listener l = listener;
      if (l != null) {
         l.onInput(kind, code);
      }
   }

   public static void key(int key, int action) {
      if (action == 1) {
         fire(AfkInput.Kind.KEY, key);
      }
   }

   public static void click(int button, int action) {
      if (action == 1) {
         fire(AfkInput.Kind.CLICK, button);
      }
   }

   public static void scroll() {
      fire(AfkInput.Kind.SCROLL, 0);
   }

   public static void cursor(double x, double y) {
      if (Double.isNaN(lastX)) {
         lastX = x;
         lastY = y;
      } else {
         double dist = Math.hypot(x - lastX, y - lastY);
         lastX = x;
         lastY = y;
         long now = System.currentTimeMillis();
         if (now - windowStart > 500L) {
            windowStart = now;
            moved = 0.0;
         }

         moved += dist;
         if (moved > 14.0) {
            moved = 0.0;
            fire(AfkInput.Kind.MOVE, 0);
         }
      }
   }

   public static enum Kind {
      KEY,
      CLICK,
      SCROLL,
      MOVE;
   }

   public interface Listener {
      void onInput(AfkInput.Kind var1, int var2);
   }
}
