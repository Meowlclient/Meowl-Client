package com.lennart.nebula.facecam;

import java.awt.image.BufferedImage;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class FacecamManager {
   private final CameraCapture capture = new CameraCapture();
   private final FacecamRenderer renderer = new FacecamRenderer();
   private final FacecamSyncManager sync = new FacecamSyncManager(this.renderer);
   private volatile FacecamManager.Config config = new FacecamManager.Config(false, false, 160, 120, 6);
   private volatile UUID self;

   public FacecamRenderer renderer() {
      return this.renderer;
   }

   public FacecamSyncManager sync() {
      return this.sync;
   }

   public CameraCapture capture() {
      return this.capture;
   }

   public void setConfig(FacecamManager.Config config) {
      this.config = config;
      this.sync.setSending(config.stream());
   }

   public void ensureRunning(UUID self, String camera, int width, int height, int fps) {
      this.self = self;
      if (this.capture.error() == null) {
         this.capture.start(camera, width, height, fps, this::onFrame);
      }
   }

   private void onFrame(BufferedImage image) {
      FacecamManager.Config c = this.config;
      UUID me = this.self;
      if (me != null) {
         BufferedImage local = VideoEncoder.scale(image, 320, 240, c.mirror());
         this.renderer.submit(me, VideoEncoder.pixels(local), local.getWidth(), local.getHeight());
         if (c.stream()) {
            BufferedImage small = VideoEncoder.scale(image, c.streamW(), c.streamH(), c.mirror());
            this.sync.offerFrame(VideoEncoder.jpeg(small), c.streamFps());
         }
      }
   }

   public void stopCamera() {
      FacecamManager.Config c = this.config;
      this.config = new FacecamManager.Config(c.mirror(), false, c.streamW(), c.streamH(), c.streamFps());
      this.capture.stop();
      this.sync.stopStream();
      UUID me = this.self;
      if (me != null) {
         Minecraft.getInstance().execute(() -> this.renderer.remove(me));
      }
   }

   public void clearError() {
      this.capture.clearError();
   }

   public boolean isCameraRunning() {
      return this.capture.isRunning();
   }

   public record Config(boolean mirror, boolean stream, int streamW, int streamH, int streamFps) {
   }
}
