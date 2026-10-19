import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.util.WorldRender;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

/** Offline-Vorschau der Kosmetik (ruft die privaten Zeichenmethoden per Reflexion auf). */
public class CosmeticsPreview {
   static Object make(String cls) throws Exception {
      Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      sun.misc.Unsafe u = (sun.misc.Unsafe)f.get(null);
      return u.allocateInstance(Class.forName("com.lennart.nebula.cosmetic.mods." + cls));
   }

   static Method m(Object o, String name, Class<?>... types) throws Exception {
      Method m = o.getClass().getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m;
   }

   public static void main(String[] a) throws Exception {
      String cls = a[0];
      String[] styles = (String[])Class.forName("com.lennart.nebula.cosmetic.mods." + cls).getField("STYLES").get(null);
      Object o = make(cls);
      int cols = 4;
      int rows = (styles.length + cols - 1) / cols;
      int W = WingsPreview.W;
      int H = WingsPreview.H;
      BufferedImage sheet = new BufferedImage(W * cols, H * rows, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = sheet.createGraphics();
      float yaw = Float.parseFloat(a[2]);
      if (a.length > 5) {
         WingsPreview.ZOOM = Float.parseFloat(a[3]);
         WingsPreview.OX = Float.parseFloat(a[4]);
         WingsPreview.OY = Float.parseFloat(a[5]);
      }

      for (int i = 0; i < styles.length; i++) {
         CosmeticValue v = CosmeticValue.of(styles[i], 0.0F, 1.0F, "3,10");
         WingsPreview.Rec solid = new WingsPreview.Rec();
         WingsPreview.Rec glow = new WingsPreview.Rec();
         WingsPreview.Rec body = new WingsPreview.Rec();
         PoseStack bm = WingsPreview.base();
         WorldRender.box(bm, body, -4, 0, -2, 4, 12, 2, 0xFF6A7A9A, 0.9F);
         WorldRender.box(bm, body, -4, -8, -4, 4, 0, 4, 0xFFD8B090, 0.9F);
         WorldRender.box(bm, body, -4, 12, -2, 0, 24, 2, 0xFF3A4A6A, 0.9F);
         WorldRender.box(bm, body, 0, 12, -2, 4, 24, 2, 0xFF3A4A6A, 0.9F);
         WorldRender.box(bm, body, -8, 0, -2, -4, 12, 2, 0xFF6A7A9A, 0.9F);
         WorldRender.box(bm, body, 4, 0, -2, 8, 12, 2, 0xFF6A7A9A, 0.9F);
         // Augen
         WorldRender.box(bm, body, -3, -4, -4.05F, -1, -3, -4, 0xFF203060, 0.9F);
         WorldRender.box(bm, body, 1, -4, -4.05F, 3, -3, -4, 0xFF203060, 0.9F);
         float t = 1.7F + i * 0.31F;
         draw(cls, o, solid, false, v, t);
         draw(cls, o, glow, true, v, t);
         BufferedImage img = WingsPreview.renderRecs(body, solid, glow, yaw);
         g.drawImage(img, i % cols * W, i / cols * H, null);
         g.setColor(Color.WHITE);
         g.drawString(styles[i], i % cols * W + 4, i / cols * H + 14);
      }

      ImageIO.write(sheet, "png", new File(a[1]));
   }

   static void draw(String cls, Object o, VertexConsumer vc, boolean glow, CosmeticValue v, float t) throws Exception {
      PoseStack ms = WingsPreview.base();
      switch (cls) {
         case "Hats":
         case "FaceGear":
            if (glow) {
               m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class).invoke(o, ms, vc, v.style(), v, t);
            } else {
               m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class).invoke(o, ms, vc, v.style(), v, t, 0.95F);
            }
            break;
         case "Halo":
            ms.translate(0.0F, -11.5F, 0.0F);
            if (glow) {
               m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class).invoke(o, ms, vc, v.style(), v, t);
            } else {
               m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class).invoke(o, ms, vc, v.style(), v, t, 0.95F);
            }
            break;
         case "Aura":
            ms.translate(0.0F, 24.0F, 0.0F);
            if (glow) {
               m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class).invoke(o, ms, vc, v.style(), v, t);
            } else {
               m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class).invoke(o, ms, vc, v.style(), v, t, 0.95F);
            }
            break;
         case "Orbit":
            for (int i = 0; i < 3; i++) {
               m(o, "place", PoseStack.class, float.class, float.class, int.class, int.class, float.class).invoke(o, ms, t, t, i, 3, 1.0F);
               if (glow) {
                  m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, int.class).invoke(o, ms, vc, v.style(), v, t, i);
               } else {
                  m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, int.class, float.class).invoke(o, ms, vc, v.style(), v, t, i, 0.95F);
               }

               ms.popPose();
            }
            break;
         case "Capes":
            m(o, "cloth", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class, boolean.class, float.class, boolean.class)
               .invoke(o, ms, vc, v.style(), v, t, 0.5F, false, 0.95F, glow);
            break;
         case "BackItems":
            ms.translate(0.0F, 0.0F, 2.0F);
            if (glow) {
               m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, boolean.class).invoke(o, ms, vc, v.style(), v, t, true);
            } else {
               m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class).invoke(o, ms, vc, v.style(), v, t, 0.95F);
            }
            break;
         case "ShoulderBuddy":
            ms.translate(6.0F, 0.0F, 0.0F);
            m(o, "creature", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class, boolean.class)
               .invoke(o, ms, vc, v.style(), v, t, 0.95F, glow);
            break;
         case "MoodIcon":
            if (!glow) {
               m(o, "place", PoseStack.class, float.class, CosmeticValue.class, boolean.class).invoke(o, ms, t, v, false);
               m(o, "pixels", PoseStack.class, VertexConsumer.class, String[].class, CosmeticValue.class, float.class)
                  .invoke(o, ms, vc, m(o, "art", String.class).invoke(null, v.style()), v, 0.95F);
            }
            break;
         case "Shoes":
            for (int side = 0; side < 2; side++) {
               ms.pushPose();
               ms.translate(side == 0 ? 2.0F : -2.0F, 12.0F, 0.0F);
               if (glow) {
                  m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, boolean.class, int.class).invoke(o, ms, vc, v.style(), v, t, true, side);
               } else {
                  m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class, int.class).invoke(o, ms, vc, v.style(), v, t, 0.95F, side);
               }

               ms.popPose();
            }
            break;
         case "Hands":
            for (int side = 0; side < 2; side++) {
               ms.pushPose();
               ms.translate(side == 0 ? -6.0F : 6.0F, 9.0F + 2.0F, 0.0F);
               if (glow) {
                  m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, int.class).invoke(o, ms, vc, v.style(), v, t, side);
               } else {
                  m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class, int.class).invoke(o, ms, vc, v.style(), v, t, 0.95F, side);
               }

               ms.popPose();
            }
            break;
         case "Accessories":
            if (glow) {
               m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class).invoke(o, ms, vc, v.style(), v, t);
            } else {
               m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class, float.class).invoke(o, ms, vc, v.style(), v, t, 0.3F, 0.95F);
            }
            break;
         case "WeatherCloud":
            m(o, "place", PoseStack.class, float.class, CosmeticValue.class, boolean.class).invoke(o, ms, t, v, false);
            if (glow) {
               m(o, "glow", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class).invoke(o, ms, vc, v.style(), v, t);
            } else {
               m(o, "solid", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class).invoke(o, ms, vc, v.style(), v, t, 0.95F);
            }
            break;
         case "Tails":
            ms.translate(0.0F, 10.5F, 1.8F);
            m(o, "draw", PoseStack.class, VertexConsumer.class, String.class, CosmeticValue.class, float.class, float.class, float.class, boolean.class)
               .invoke(o, ms, vc, v.style(), v, t, 0.4F, 0.95F, glow);
      }
   }
}
