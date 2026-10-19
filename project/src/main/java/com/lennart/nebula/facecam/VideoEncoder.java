package com.lennart.nebula.facecam;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public final class VideoEncoder {
   public static final int MAX_STREAM_BYTES = 45056;

   private VideoEncoder() {
   }

   public static BufferedImage scale(BufferedImage src, int maxW, int maxH, boolean mirror) {
      double f = Math.min(1.0, Math.min((double)maxW / src.getWidth(), (double)maxH / src.getHeight()));
      int w = Math.max(8, (int)Math.round(src.getWidth() * f));
      int h = Math.max(8, (int)Math.round(src.getHeight() * f));
      BufferedImage out = new BufferedImage(w, h, 1);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      if (mirror) {
         g.drawImage(src, w, 0, -w, h, null);
      } else {
         g.drawImage(src, 0, 0, w, h, null);
      }

      g.dispose();
      return out;
   }

   public static byte[] jpeg(BufferedImage image) {
      for (float quality : new float[]{0.7F, 0.5F, 0.35F, 0.2F}) {
         byte[] data = encode(image, quality);
         if (data != null && data.length <= 45056) {
            return data;
         }
      }

      return null;
   }

   private static byte[] encode(BufferedImage image, float quality) {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();

      Object out;
      try (
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream outx = ImageIO.createImageOutputStream(bytes);
      ) {
         writer.setOutput(outx);
         ImageWriteParam param = writer.getDefaultWriteParam();
         param.setCompressionMode(2);
         param.setCompressionQuality(quality);
         writer.write(null, new IIOImage(image, null, null), param);
         outx.flush();
         return bytes.toByteArray();
      } catch (IOException var19) {
         out = null;
      } finally {
         writer.dispose();
      }

      return (byte[])out;
   }

   public static int[] pixels(BufferedImage image) {
      return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
   }
}
