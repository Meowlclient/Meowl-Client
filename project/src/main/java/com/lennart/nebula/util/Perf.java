package com.lennart.nebula.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leichter Messer für das Leistungs-Menü: summiert, wie viel Rechenzeit (CPU, Render-Thread) jedes Modul und jede
 * Kosmetik pro Bild kostet, und wie viel CPU die Hintergrund-Threads und Hilfsprozesse des Clients ziehen. Ausgewertet
 * wird jede Sekunde; eine Messung kostet nur zwei {@code nanoTime}-Aufrufe.
 */
public final class Perf {
   /** Schlüssel → gesammelte Nanosekunden im laufenden Messfenster (nur Render-/Client-Thread). */
   private static final Map<Object, long[]> ACC = new HashMap<>();
   private static volatile Map<Object, Double> msPerFrame = Map.of();
   private static volatile Map<String, Double> background = Map.of();
   private static volatile double frameMs;
   private static volatile int fps;
   private static long windowStart = System.nanoTime();
   private static int frames;
   private static final Map<Long, Long> THREAD_CPU = new HashMap<>();
   private static final Map<Long, Long> PROC_CPU = new HashMap<>();
   private static final ThreadMXBean BEAN = ManagementFactory.getThreadMXBean();

   private Perf() {
   }

   public static long start() {
      return System.nanoTime();
   }

   /** Zeit seit {@code t0} dem Schlüssel gutschreiben (nur vom Client-Thread aufrufen). */
   public static void add(Object key, long t0) {
      long d = System.nanoTime() - t0;
      long[] a = ACC.get(key);
      if (a == null) {
         a = new long[1];
         ACC.put(key, a);
      }

      a[0] += d;
   }

   private static final Map<Class<?>, Object> MODULES = new HashMap<>();

   /** Modul-Instanz als Messschlüssel (gecacht). */
   public static Object module(Class<? extends com.lennart.nebula.module.Module> type) {
      Object m = MODULES.get(type);
      if (m == null) {
         m = com.lennart.nebula.NebulaClient.getInstance().getModuleManager().get(type);
         if (m == null) {
            return type;
         }

         MODULES.put(type, m);
      }

      return m;
   }

   /** Verzögert gezeichnete Geometrie messen: die Zeit beim eigentlichen Zeichnen wird {@code owner} gutgeschrieben. */
   public static net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer timed(
      Class<? extends com.lennart.nebula.module.Module> owner, net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer r
   ) {
      return (pose, vc) -> {
         long t0 = System.nanoTime();
         r.render(pose, vc);
         add(module(owner), t0);
      };
   }

   /** Einmal pro gezeichnetem Bild (in der Welt). */
   public static void frame() {
      frames++;
      long now = System.nanoTime();
      long win = now - windowStart;
      if (win < 1000000000L) {
         return;
      }

      Map<Object, Double> out = new HashMap<>();
      for (Map.Entry<Object, long[]> e : ACC.entrySet()) {
         if (e.getValue()[0] > 0L) {
            out.put(e.getKey(), e.getValue()[0] / 1.0E6 / Math.max(1, frames));
         }

         e.getValue()[0] = 0L;
      }

      msPerFrame = out;
      fps = Math.round(frames * 1.0E9F / win);
      frameMs = win / 1.0E6 / Math.max(1, frames);
      // Hintergrund-Last nur, wenn jemand hinschaut (Overlay/Menü), und nie auf dem Render-Thread:
      // die Prozessliste abzufragen dauert unter Windows 15+ ms und würde sonst jede Sekunde ruckeln
      if (System.currentTimeMillis() - wanted < 3000L && !sampling) {
         sampling = true;
         long w = win;
         Thread t = new Thread(() -> {
            try {
               background = sampleBackground(w);
            } finally {
               sampling = false;
            }
         }, "Meowl-Leistung");
         t.setDaemon(true);
         t.setPriority(Thread.MIN_PRIORITY);
         t.start();
      } else if (System.currentTimeMillis() - wanted >= 3000L) {
         background = Map.of();
      }
      frames = 0;
      windowStart = now;
   }

   private static volatile long wanted;
   private static volatile boolean sampling;

   /** Overlay bzw. Leistungs-Menü sichtbar: Hintergrund-Last messen (sonst wird gar nicht gemessen). */
   public static void wantBackground() {
      wanted = System.currentTimeMillis();
   }

   /** Alle Threads ohne Stapel-Abfrage (die hält sonst kurz die ganze Java-VM an). */
   private static Thread[] threads() {
      ThreadGroup g = Thread.currentThread().getThreadGroup();
      while (g.getParent() != null) {
         g = g.getParent();
      }

      Thread[] all = new Thread[g.activeCount() * 2 + 16];
      int n = g.enumerate(all, true);
      return java.util.Arrays.copyOf(all, n);
   }

   /** CPU der eigenen Threads („Meowl-…“) und Hilfsprozesse (z.B. PowerShell für Spotify) in % eines Kerns. */
   private static synchronized Map<String, Double> sampleBackground(long win) {
      Map<String, Double> out = new LinkedHashMap<>();
      try {
         if (BEAN.isThreadCpuTimeSupported()) {
            Map<Long, Long> seen = new HashMap<>();
            for (Thread t : threads()) {
               String name = t.getName();
               // eigene Threads und die HTTP-Arbeiter (Netzwerk des Clients: Meowl Server, Bilder, Streaming …)
               if (name.startsWith("HttpClient")) {
                  name = "Netzwerk (HTTP)";
               } else if (!name.startsWith("Meowl")) {
                  continue;
               }

               long cpu = BEAN.getThreadCpuTime(t.threadId());
               if (cpu < 0L) {
                  continue;
               }

               seen.put(t.threadId(), cpu);
               Long before = THREAD_CPU.get(t.threadId());
               if (before != null && cpu > before) {
                  out.merge(name, (cpu - before) * 100.0 / win, Double::sum);
               }
            }

            THREAD_CPU.clear();
            THREAD_CPU.putAll(seen);
         }

         Map<Long, Long> seenProc = new HashMap<>();
         ProcessHandle.current().descendants().forEach(p -> {
            var info = p.info();
            info.totalCpuDuration().ifPresent(d -> {
               long cpu = d.toNanos();
               seenProc.put(p.pid(), cpu);
               Long before = PROC_CPU.get(p.pid());
               if (before != null && cpu > before) {
                  String cmd = info.command().orElse("Prozess").replace('\\', '/');
                  String exe = cmd.substring(cmd.lastIndexOf('/') + 1);
                  out.merge("Prozess: " + exe, (cpu - before) * 100.0 / win, Double::sum);
               }
            });
         });
         PROC_CPU.clear();
         PROC_CPU.putAll(seenProc);
      } catch (RuntimeException var7) {
      }

      return out;
   }

   /** Millisekunden pro Bild je Schlüssel (letzte Sekunde). */
   public static Map<Object, Double> msPerFrame() {
      return msPerFrame;
   }

   /** CPU-Last der Hintergrund-Threads/-Prozesse in % eines Kerns (letzte Sekunde). */
   public static Map<String, Double> background() {
      return background;
   }

   public static double frameMs() {
      return frameMs;
   }

   public static int fps() {
      return fps;
   }

   /** Wie viele FPS etwas kostet, das {@code ms} pro Bild braucht (ohne es wäre die Bildzeit um ms kürzer). */
   public static double fpsCost(double ms) {
      double f = frameMs;
      if (f <= 0.0 || ms <= 0.0) {
         return 0.0;
      }

      return 1000.0 / Math.max(0.2, f - ms) - 1000.0 / f;
   }

   /** Für Listen: Einträge nach Kosten sortiert. */
   public static <K> List<Map.Entry<K, Double>> sorted(Map<K, Double> m) {
      List<Map.Entry<K, Double>> l = new ArrayList<>(m.entrySet());
      l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
      return l;
   }
}
