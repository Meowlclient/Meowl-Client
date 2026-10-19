package com.lennart.nebula.facecam;

import com.github.sarxos.webcam.Webcam;
import com.lennart.nebula.NebulaClient;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CameraCapture {
   private volatile CameraCapture.Request desired;
   private volatile Thread thread;
   private volatile String error;
   private volatile String activeName;

   public static List<String> listCameras() {
      List<String> names = new ArrayList<>();

      try {
         for (Webcam cam : Webcam.getWebcams(4L, TimeUnit.SECONDS)) {
            names.add(cam.getName());
         }
      } catch (Throwable var3) {
         NebulaClient.LOGGER.warn("Kameras konnten nicht gesucht werden: {}", var3.toString());
      }

      return names;
   }

   public boolean isRunning() {
      return this.thread != null;
   }

   public String error() {
      return this.error;
   }

   public void clearError() {
      this.error = null;
   }

   public String activeName() {
      return this.activeName;
   }

   public synchronized void start(String name, int width, int height, int fps, Consumer<BufferedImage> onFrame) {
      int f = Math.max(1, Math.min(30, fps));
      CameraCapture.Request current = this.desired;
      if (current == null || !current.key().equals(name + "|" + width + "x" + height) || current.fps() != f || this.thread == null) {
         this.desired = new CameraCapture.Request(name, width, height, f, onFrame);
         if (this.thread == null) {
            Thread t = new Thread(this::run, "Meowl-Facecam");
            t.setDaemon(true);
            this.thread = t;
            t.start();
         }
      }
   }

   public synchronized void stop() {
      this.desired = null;
      Thread t = this.thread;
      if (t != null) {
         t.interrupt();
      }
   }

   private void run() {
      Webcam webcam = null;
      String openKey = null;

      try {
         while (true) {
            CameraCapture.Request req = this.desired;
            if (req == null) {
               break;
            }

            if (!req.key().equals(openKey)) {
               if (webcam != null) {
                  webcam.close();
                  webcam = null;
               }

               webcam = open(req);
               openKey = req.key();
               this.activeName = webcam.getName();
            }

            long start = System.currentTimeMillis();
            BufferedImage image = webcam.getImage();
            if (image != null && this.desired == req) {
               req.onFrame().accept(image);
            }

            long sleep = 1000L / req.fps() - (System.currentTimeMillis() - start);
            if (sleep > 0L) {
               Thread.sleep(sleep);
            }
         }
      } catch (InterruptedException var29) {
      } catch (Throwable var301) {
         boolean interrupted = Thread.interrupted() || var301.getCause() instanceof InterruptedException;
         if (this.desired != null && !interrupted) {
            this.error = var301.getMessage() == null ? var301.getClass().getSimpleName() : var301.getMessage();
            NebulaClient.LOGGER.warn("Facecam: {}", var301.toString());
         }
      } finally {
         if (webcam != null) {
            try {
               webcam.close();
            } catch (Throwable var28) {
            }
         }

         this.activeName = null;
         synchronized (this) {
            if (this.thread == Thread.currentThread()) {
               this.thread = null;
            }

            if (this.desired != null && this.error == null && this.thread == null) {
               Thread t = new Thread(this::run, "Meowl-Facecam");
               t.setDaemon(true);
               this.thread = t;
               t.start();
            }
         }
      }
   }

   private static Webcam open(CameraCapture.Request req) throws Exception {
      List<Webcam> cams = Webcam.getWebcams(4L, TimeUnit.SECONDS);
      Webcam webcam = null;

      for (Webcam c : cams) {
         if (req.name() == null || req.name().isBlank() || c.getName().equals(req.name())) {
            webcam = c;
            break;
         }
      }

      if (webcam == null) {
         throw new IllegalStateException(cams.isEmpty() ? "Keine Kamera gefunden" : "Kamera \"" + req.name() + "\" nicht gefunden");
      } else {
         if (webcam.isOpen()) {
            webcam.close();
         }

         webcam.setViewSize(bestSize(webcam, req.width(), req.height()));
         if (!webcam.open()) {
            throw new IllegalStateException("Kamera konnte nicht geöffnet werden (von anderem Programm belegt?)");
         } else {
            return webcam;
         }
      }
   }

   private static Dimension bestSize(Webcam webcam, int w, int h) {
      Dimension best = null;

      for (Dimension d : webcam.getViewSizes()) {
         if (best == null || Math.abs(d.width - w) + Math.abs(d.height - h) < Math.abs(best.width - w) + Math.abs(best.height - h)) {
            best = d;
         }
      }

      return best != null ? best : new Dimension(320, 240);
   }

   private record Request(String name, int width, int height, int fps, Consumer<BufferedImage> onFrame) {
      String key() {
         return this.name + "|" + this.width + "x" + this.height;
      }
   }
}
