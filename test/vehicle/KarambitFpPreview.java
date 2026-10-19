import com.lennart.nebula.plasma.PlasmaSword;
import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Ich-Ansicht des Karambits (gleiche Matrizen wie Vanilla renderPlayerArm + KarambitHand). Aufruf: out.png */
public class KarambitFpPreview {
   public static void main(String[] a) throws Exception {
      float[] spins = new float[a.length - 2]; for (int s = 2; s < a.length; s++) spins[s - 2] = Float.parseFloat(a[s]); float yflip = Float.parseFloat(a[1]);
      WingsPreview.W = 534;
      int W = WingsPreview.W;
      int H = WingsPreview.H;
      BufferedImage sheet = new BufferedImage(W * spins.length, H, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = sheet.createGraphics();
      Method km = PlasmaSword.class.getDeclaredMethod("karambit", Matrix4f.class, VertexConsumer.class, PlasmaSword.Look.class, float.class, boolean.class);
      km.setAccessible(true);
      

      for (int i = 0; i < spins.length; i++) {
         WingsPreview.Rec body = new WingsPreview.Rec();
         WingsPreview.Rec solid = new WingsPreview.Rec();
         WingsPreview.Rec glow = new WingsPreview.Rec();
         PoseStack m = new PoseStack();
         m.translate(0.64F, -0.6F, -0.72F);
         m.mulPose(Axis.YP.rotationDegrees(45.0F));
         m.translate(-1.0F, 3.6F, 3.5F);
         m.mulPose(Axis.ZP.rotationDegrees(120.0F));
         m.mulPose(Axis.XP.rotationDegrees(200.0F));
         m.mulPose(Axis.YP.rotationDegrees(-135.0F));
         m.translate(5.6F, 0.0F, 0.0F);
         m.translate(-5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
         m.mulPose(Axis.ZP.rotation(0.1F));
         m.pushPose();
         m.scale(0.0625F, 0.0625F, 0.0625F);
         WorldRender.box(m, body, -3, -2, -2, 1, 10, 2, 0xFF5A5A62, 0.9F);
         m.popPose();
         m.translate(-1.0F / 16.0F, 9.0F / 16.0F, -0.6F / 16.0F);
         m.mulPose(com.lennart.nebula.fx.KarambitHand.grip(0.0F, yflip, spins[i]));
         m.scale(0.85F, 0.85F, 0.85F);
         m.translate(0.0F, 0.07F, 0.0F);
         PlasmaSword.Look look = new PlasmaSword.Look(0xB040FF, 1.0F, 0.95F, 1.0F, 0.0F, false, true, PlasmaSword.KARAMBIT, 0);
         Matrix4f mat = new Matrix4f(m.last().pose());
         km.invoke(null, mat, solid, look, 1.0F, false);
         km.invoke(null, mat, glow, look, 1.0F, true);
         for (WingsPreview.Rec r : new WingsPreview.Rec[]{body, solid, glow}) {
            for (float[] p : r.v) {
               float x = p[0];
               float y = p[1];
               float z = p[2];
               p[0] = x;
               p[1] = -y + 0.2F;
               p[2] = -z - 6.0F;
            }
         }

         WingsPreview.ZOOM = 0.41F;
         float[] zb = new float[W * H];
         java.util.Arrays.fill(zb, Float.MAX_VALUE);
         float[] rgb = new float[W * H * 3];
         for (int k = 0; k < W * H; k++) {
            rgb[k * 3] = 0.55F;
            rgb[k * 3 + 1] = 0.5F;
            rgb[k * 3 + 2] = 0.42F;
         }

         WingsPreview.raster(body, zb, rgb, 0.0, 0.0, false);
         WingsPreview.raster(solid, zb, rgb, 0.0, 0.0, false);
         WingsPreview.raster(glow, zb, rgb, 0.0, 0.0, true);
         BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
         for (int k = 0; k < W * H; k++) {
            int r = Math.min(255, Math.round(rgb[k * 3] * 255.0F));
            int gg = Math.min(255, Math.round(rgb[k * 3 + 1] * 255.0F));
            int b = Math.min(255, Math.round(rgb[k * 3 + 2] * 255.0F));
            img.setRGB(k % W, k / W, r << 16 | gg << 8 | b);
         }

         g.drawImage(img, i * W, 0, null);
         g.setColor(Color.WHITE);
         g.drawString("flach " + spins[i] + " turn " + yflip, i * W + 4, 14);
      }

      ImageIO.write(sheet, "png", new File(a[0]));
   }
}

