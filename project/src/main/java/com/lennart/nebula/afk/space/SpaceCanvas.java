package com.lennart.nebula.afk.space;

/** Zeichenfläche der Sternenschlacht (Bildschirmkoordinaten). Im Spiel DrawContext, im Test Java2D. */
public interface SpaceCanvas {
   void fill(float x1, float y1, float x2, float y2, int argb);

   void gradient(float x1, float y1, float x2, float y2, int top, int bottom);

   /** Dünner Balken von (x1,y1) nach (x2,y2). */
   void line(float x1, float y1, float x2, float y2, float width, int argb);

   /** Bild hochladen (einmalig). */
   void upload(String id, Tex tex);

   boolean has(String id);

   /**
    * Ausschnitt (u0..u1, v0..v1 in Bildpixeln) eines Bildes in das Rechteck mit Mittelpunkt (cx, cy) und Größe
    * w x h zeichnen, um {@code angle} gedreht; {@code flip} spiegelt waagerecht. {@code tint} multipliziert (ARGB).
    */
   void image(String id, float cx, float cy, float w, float h, float u0, float v0, float u1, float v1, float angle, boolean flip, int tint);
}
