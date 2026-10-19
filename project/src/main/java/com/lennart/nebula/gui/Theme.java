package com.lennart.nebula.gui;

public final class Theme {
   public static final int OVERLAY = -1728053248;
   public static final int PANEL = -267250656;
   public static final int HEADER = -15197398;
   public static final int SIDEBAR = -15394778;
   public static final int CARD = -14933965;
   public static final int CARD_HOVER = -14407358;
   public static final int DIVIDER = -14012603;
   public static final int ACCENT = -7643914;
   public static final int ACCENT_SOFT = 1082875126;
   public static final int ACCENT_TEXT = -3885571;
   public static final int TEXT = -1578509;
   public static final int TEXT_DIM = -7564628;
   public static final int TEXT_MUTED = -10656900;
   public static final int SWITCH_OFF = -13288878;
   public static final int KNOB = -1;
   public static final int WARNING = -278748;
   public static final int PANEL_BG = -15855591;
   public static final int GROUP_CARD = -15394521;
   public static final int GROUP_HEADER = -15065296;
   public static final int ROW_HOVER = 251658239;
   public static final int ROW_DIVIDER = -14802381;
   public static final int BORDER = -14275777;
   public static final int TRACK = -14012603;
   public static final int SUCCESS = -11870592;
   public static final int DANGER = -495247;

   private Theme() {
   }

   public static int lerp(int from, int to, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int a = (int)((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * t);
      int r = (int)((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int g = (int)((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int b = (int)((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }
}
