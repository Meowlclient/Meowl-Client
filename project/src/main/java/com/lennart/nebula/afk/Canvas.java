package com.lennart.nebula.afk;

public interface Canvas {
   void fill(int var1, int var2, int var3, int var4, int var5);

   void gradient(int var1, int var2, int var3, int var4, int var5, int var6);

   void texture(String var1, int var2, int var3, int var4, int var5, int var6);

   void scissor(int var1, int var2, int var3, int var4);

   void noScissor();
}
