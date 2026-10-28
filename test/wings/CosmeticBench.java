import com.lennart.nebula.cosmetic.CosmeticValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Misst, wie viel Rechenzeit jede Kosmetik-Variante pro Bild kostet (CPU, Erzeugen der Geometrie) und wie viel Speicher
 * sie dabei anlegt. Aufruf: java CosmeticBench [Klasse ...]
 */
public class CosmeticBench {
   /** Zählt nur Ecken, speichert nichts. */
   static final class Null implements VertexConsumer {
      long vertices;

      public VertexConsumer addVertex(float x, float y, float z) {
         this.vertices++;
         return this;
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         return this;
      }

      public VertexConsumer setColor(int argb) {
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer setLineWidth(float w) {
         return this;
      }
   }

   static long allocated() {
      return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId());
   }

   public static void main(String[] a) throws Exception {
      String[] classes = a.length > 0 ? a : new String[]{"Hats", "FaceGear", "Accessories", "Aura", "Hands", "MoodIcon", "ShoulderBuddy", "Tails"};
      List<String> rows = new ArrayList<>();
      for (String cls : classes) {
         String[] styles = (String[])Class.forName("com.lennart.nebula.cosmetic.mods." + cls).getField("STYLES").get(null);
         Object o = CosmeticsPreview.make(cls);
         for (String st : styles) {
            CosmeticValue v = CosmeticValue.of(st, 0.0F, 1.0F, "3,10");
            Null n = new Null();
            // aufwärmen (JIT)
            for (int i = 0; i < 400; i++) {
               CosmeticsPreview.draw(cls, o, n, false, v, i * 0.016F);
               CosmeticsPreview.draw(cls, o, n, true, v, i * 0.016F);
            }

            n.vertices = 0L;
            int runs = 2000;
            long mem0 = allocated();
            long t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) {
               CosmeticsPreview.draw(cls, o, n, false, v, i * 0.016F);
               CosmeticsPreview.draw(cls, o, n, true, v, i * 0.016F);
            }

            double us = (System.nanoTime() - t0) / 1000.0 / runs;
            double kb = (allocated() - mem0) / 1024.0 / runs;
            rows.add(String.format("%8.1f µs  %7.1f KB  %6d Ecken  %s: %s", us, kb, n.vertices / runs, cls, st));
         }
      }

      rows.sort((x, y) -> Double.compare(Double.parseDouble(y.trim().split("\\s+")[0].replace(',', '.')), Double.parseDouble(x.trim().split("\\s+")[0].replace(',', '.'))));
      rows.forEach(System.out::println);
   }
}
