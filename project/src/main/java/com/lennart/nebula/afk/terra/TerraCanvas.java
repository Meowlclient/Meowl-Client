package com.lennart.nebula.afk.terra;

/** Zeichenfläche der Terraria-Szene (Minecraft-GUI im Spiel, Java2D in der Offline-Vorschau). */
public interface TerraCanvas {
   int ATLAS = 0;
   int SKIN = 1;
   int BG_FAR = 2;
   int BG_MID = 3;
   int BG_NEAR = 4;
   int MAP = 5;

   void fill(float x1, float y1, float x2, float y2, int argb);

   /**
    * Texturausschnitt (u, v, uw, vh in Texeln) als Parallelogramm: (u, v) landet auf p0, (u + uw, v) auf p1 und
    * (u, v + vh) auf p3. Farbe wird mit {@code tint} multipliziert.
    */
   void quad(int tex, float p0x, float p0y, float p1x, float p1y, float p3x, float p3y, int u, int v, int uw, int vh, int tint);

   void text(String s, float x, float y, float scale, int argb, boolean shadow);

   int textWidth(String s);
}
