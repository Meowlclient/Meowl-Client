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

/** Ich-Ansicht des Butterfly-Messers in mehreren Animationsphasen (gleiche Matrizen wie ButterflyKnife). */
public class ButterflyFpPreview {
   static Quaternionf armSpace(Vector3f dir) {
      Vector3f y = new Vector3f(dir).normalize();
      Vector3f x = new Vector3f(0, 0, 1);
      x.sub(new Vector3f(y).mul(x.dot(y))).normalize();
      Vector3f z = new Vector3f(x).cross(y).normalize();
      Quaternionf view = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
      view = new Quaternionf().rotateY((float)Math.toRadians(-20.0)).mul(view);
      return com.lennart.nebula.fx.KarambitHand.armRest().conjugate().mul(view);
   }

   static float smooth(float x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }
   static float easeOut(float x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x) * (1 - x); }

   public static void main(String[] a) throws Exception {
      Vector3f CLOSED = new Vector3f(1.0F, -0.22F, 0.35F).normalize();
      Vector3f OPEN = new Vector3f(0.42F, 1.0F, -0.22F).normalize();
      float[] times = {-1, 0.15F, 0.35F, 0.55F, 0.8F, 1.05F, 9};
      WingsPreview.W = 534;
      int W = WingsPreview.W, H = WingsPreview.H;
      BufferedImage sheet = new BufferedImage(W * 4, H * 2, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = sheet.createGraphics();
      Method bm = PlasmaSword.class.getDeclaredMethod("butterfly", Matrix4f.class, VertexConsumer.class, PlasmaSword.Look.class, float.class, float.class, float.class, boolean.class);
      bm.setAccessible(true);
      PlasmaSword.Look look = new PlasmaSword.Look(0xB040FF, 1.0F, 0.95F, 1.0F, 0.0F, false, true, PlasmaSword.BUTTERFLY, PlasmaSword.LAVENDER);
      for (int i = 0; i < times.length; i++) {
         float t = times[i];
         Vector3f dir; float spin, ha, hb;
         if (t < 0) { dir = OPEN; spin = 0; ha = 180; hb = 152; }
         else if (t > 5) { dir = OPEN; spin = 4; ha = 180; hb = 160; }
         else { dir = new Vector3f(OPEN).add(new Vector3f(-0.35F, -0.25F, 0.0F).mul((float)Math.sin(Math.PI * Math.min(1.0F, t / 0.9F)))).normalize(); ha = 180; hb = 152 + 720 * easeOut(t / 1.25F); spin = 360 * smooth(t / 0.85F) + (float)Math.sin(t * 18) * 12 * (1 - smooth(t / 1.25F)); }
         WingsPreview.Rec body = new WingsPreview.Rec(), solid = new WingsPreview.Rec(), glow = new WingsPreview.Rec();
         PoseStack m = new PoseStack();
         m.translate(0.64F, -0.6F, -0.72F);
         m.mulPose(Axis.YP.rotationDegrees(45));
         m.translate(-1, 3.6F, 3.5F);
         m.mulPose(Axis.ZP.rotationDegrees(120));
         m.mulPose(Axis.XP.rotationDegrees(200));
         m.mulPose(Axis.YP.rotationDegrees(-135));
         m.translate(5.6F, 0, 0);
         m.translate(-5 / 16F, 2 / 16F, 0);
         m.mulPose(Axis.ZP.rotation(0.1F));
         m.pushPose(); m.scale(0.0625F, 0.0625F, 0.0625F);
         WorldRender.box(m, body, -3, -2, -2, 1, 10, 2, 0xFF5A5A62, 0.9F);
         m.popPose();
         m.translate(-1 / 16F, 9 / 16F, -0.6F / 16F);
         m.mulPose(armSpace(dir));
         m.mulPose(Axis.XP.rotationDegrees(spin));
         m.scale(1.35F, 1.35F, 1.35F);
         m.translate(0, -(0.12F + 0.24F * (float)Math.cos(Math.toRadians(ha))), 0);
         Matrix4f mat = new Matrix4f(m.last().pose());
         bm.invoke(null, mat, solid, look, 1.0F, ha, hb, false);
         bm.invoke(null, mat, glow, look, 1.0F, ha, hb, true);
         for (WingsPreview.Rec r : new WingsPreview.Rec[]{body, solid, glow}) for (float[] p : r.v) { p[1] = -p[1] + 0.2F; p[2] = -p[2] - 6.0F; }
         WingsPreview.ZOOM = 0.41F;
         float[] zb = new float[W * H]; java.util.Arrays.fill(zb, Float.MAX_VALUE);
         float[] rgb = new float[W * H * 3];
         for (int k = 0; k < W * H; k++) { rgb[k * 3] = 0.35F; rgb[k * 3 + 1] = 0.5F; rgb[k * 3 + 2] = 0.3F; }
         WingsPreview.raster(body, zb, rgb, 0, 0, false);
         WingsPreview.raster(solid, zb, rgb, 0, 0, false);
         WingsPreview.raster(glow, zb, rgb, 0, 0, true);
         BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
         for (int k = 0; k < W * H; k++) {
            int r = Math.min(255, Math.round(rgb[k * 3] * 255)), gg = Math.min(255, Math.round(rgb[k * 3 + 1] * 255)), b = Math.min(255, Math.round(rgb[k * 3 + 2] * 255));
            img.setRGB(k % W, k / W, r << 16 | gg << 8 | b);
         }
         g.drawImage(img, (i % 4) * W, (i / 4) * H, null);
         g.setColor(Color.WHITE);
         g.drawString(t < 0 ? "zu" : (t > 5 ? "offen" : "t=" + t), (i % 4) * W + 4, (i / 4) * H + 14);
      }
      ImageIO.write(sheet, "png", new File(a[0]));
   }
}