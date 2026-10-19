import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

/**
 * Offline-Vorschau der Fahrzeug-Emotes (VehicleModels, private Zeichenmethoden per Reflexion) mit einer groben
 * Spielerfigur in der passenden Haltung. Aufruf: VehiclePreview out.png [yaw...]
 */
public class VehiclePreview {
   static Method m(String name, Class<?>... types) throws Exception {
      Method m = Class.forName("com.lennart.nebula.emote.VehicleModels").getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m;
   }

   public static void main(String[] a) throws Exception {
      String[] kinds = {"scooter", "car", "tank", "heli"};
      float[] yaws = a.length > 1 ? new float[a.length - 1] : new float[]{150.0F, 60.0F};
      for (int i = 1; i < a.length; i++) {
         yaws[i - 1] = Float.parseFloat(a[i]);
      }

      WingsPreview.ZOOM = 0.55F;
      int W = WingsPreview.W;
      int H = WingsPreview.H;
      BufferedImage sheet = new BufferedImage(W * kinds.length, H * yaws.length, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = sheet.createGraphics();

      for (int r = 0; r < yaws.length; r++) {
         for (int c = 0; c < kinds.length; c++) {
            String k = kinds[c];
            WingsPreview.Rec body = new WingsPreview.Rec();
            WingsPreview.Rec solid = new WingsPreview.Rec();
            WingsPreview.Rec glow = new WingsPreview.Rec();
            PoseStack bm = WingsPreview.base();
            boolean sit = k.equals("car") || k.equals("heli");
            WorldRender.box(bm, body, -4, 0, -2, 4, 12, 2, 0xFF6A7A9A, 0.9F);
            WorldRender.box(bm, body, -4, -8, -4, 4, 0, 4, 0xFFD8B090, 0.9F);
            if (sit) {
               WorldRender.box(bm, body, -4, 12, -12, 0, 16, 0, 0xFF3A4A6A, 0.9F);
               WorldRender.box(bm, body, 0, 12, -12, 4, 16, 0, 0xFF3A4A6A, 0.9F);
            } else {
               WorldRender.box(bm, body, -4, 12, -2, 0, 24, 2, 0xFF3A4A6A, 0.9F);
               WorldRender.box(bm, body, 0, 12, -2, 4, 24, 2, 0xFF3A4A6A, 0.9F);
            }

            // Arme nach vorn (Lenker/Lenkrad)
            WorldRender.box(bm, body, -8, 2, -10, -4, 6, -2, 0xFF6A7A9A, 0.9F);
            WorldRender.box(bm, body, 4, 2, -10, 8, 6, -2, 0xFF6A7A9A, 0.9F);
            WorldRender.box(bm, body, -3, -4, -4.05F, -1, -3, -4, 0xFF203060, 0.9F);
            WorldRender.box(bm, body, 1, -4, -4.05F, 3, -3, -4, 0xFF203060, 0.9F);
            PoseStack ms = WingsPreview.base();
            PoseStack gs = WingsPreview.base();
            switch (k) {
               case "scooter" -> {
                  m("scooter", PoseStack.class, VertexConsumer.class, float.class, float.class).invoke(null, ms, solid, 0.6F, 0.95F);
                  m("scooterGlow", PoseStack.class, VertexConsumer.class, float.class).invoke(null, gs, glow, 1.0F);
               }
               case "car" -> {
                  m("car", PoseStack.class, VertexConsumer.class, float.class, float.class).invoke(null, ms, solid, 0.6F, 0.95F);
                  m("carGlow", PoseStack.class, VertexConsumer.class).invoke(null, gs, glow);
               }
               case "tank" -> m("tank", PoseStack.class, VertexConsumer.class, float.class, float.class, float.class).invoke(null, ms, solid, 0.6F, 0.3F, 0.95F);
               default -> {
                  m("heli", PoseStack.class, VertexConsumer.class, float.class, float.class).invoke(null, ms, solid, 0.3F, 0.95F);
                  m("heliGlow", PoseStack.class, VertexConsumer.class, float.class).invoke(null, gs, glow, 0.3F);
               }
            }

            BufferedImage img = WingsPreview.renderRecs(body, solid, glow, yaws[r]);
            g.drawImage(img, c * W, r * H, null);
            g.setColor(Color.WHITE);
            g.drawString(k + " " + yaws[r], c * W + 4, r * H + 14);
         }
      }

      ImageIO.write(sheet, "png", new File(a[0]));
   }
}
