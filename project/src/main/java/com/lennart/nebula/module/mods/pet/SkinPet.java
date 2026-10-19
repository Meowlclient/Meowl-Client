package com.lennart.nebula.module.mods.pet;

import com.lennart.nebula.NebulaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * „Skin Pet“: eine kleine Spielerfigur mit exakt dem Original-Skin eines Spielers (keine Farbänderung, keine Spiegelung).
 * Der Skin kommt live aus der Tabliste, wenn der Spieler online ist, sonst über Mojang (alle 10 Minuten aktualisiert).
 */
public final class SkinPet {
   public static final String PREFIX = "Skin:";
   private static final long REFRESH_MS = 600000L;
   private static final Map<String, SkinPet.Cached> CACHE = new ConcurrentHashMap<>();

   private SkinPet() {
   }

   public static boolean is(String skin) {
      return skin != null && skin.startsWith(PREFIX);
   }

   public static String name(String skin) {
      return skin.substring(PREFIX.length());
   }

   public static boolean validName(String name) {
      return name != null && name.matches("[A-Za-z0-9_]{1,16}");
   }

   private static final class Cached {
      volatile PlayerSkin textures;
      volatile long fetched;
      volatile boolean loading;
      volatile boolean missing;
   }

   /** Aktuelle Skin-Texturen des Spielers oder null, solange sie noch laden. */
   public static PlayerSkin textures(String name) {
      Minecraft mc = Minecraft.getInstance();
      // 1) Spieler ist auf dem gleichen Server: Skin direkt aus der Tabliste (immer aktuell)
      if (mc.getConnection() != null) {
         PlayerInfo e = mc.getConnection().getPlayerInfo(name);
         if (e != null) {
            return e.getSkin();
         }
      }

      // 2) sonst über Mojang laden und regelmäßig erneuern (Skin-Änderungen werden übernommen)
      String key = name.toLowerCase(Locale.ROOT);
      SkinPet.Cached c = CACHE.computeIfAbsent(key, k -> new SkinPet.Cached());
      long now = System.currentTimeMillis();
      if (!c.loading && (c.fetched == 0L || now - c.fetched > REFRESH_MS)) {
         c.loading = true;
         c.fetched = now;
         CompletableFuture.supplyAsync(() -> {
            var services = mc.services();
            var byName = services.profileResolver().fetchByName(name);
            if (byName.isEmpty()) {
               return Optional.<PlayerSkin>empty();
            } else {
               var full = services.sessionService().fetchProfile(byName.get().id(), false);
               var profile = full != null ? full.profile() : byName.get();
               return mc.getSkinManager().get(profile).join();
            }
         }).whenComplete((result, error) -> {
            c.loading = false;
            if (error != null) {
               NebulaClient.LOGGER.debug("Skin Pet: Skin von {} nicht ladbar: {}", name, error.toString());
            } else if (result.isPresent()) {
               c.textures = result.get();
               c.missing = false;
            } else {
               c.missing = true;
            }
         });
      }

      return c.textures;
   }

   // ------------------------------------------------------------------ Modell

   /**
    * Zeichnet die Figur im Pet-Raum (Füße bei y=0, Blick nach +z, Einheit = Pixel des Pet-Modells).
    * Die Figur ist 32 Skin-Pixel hoch und wird auf die Pet-Höhe (19) verkleinert.
    */
   public static void draw(PoseStack m, java.util.function.Function<net.minecraft.client.renderer.rendertype.RenderType, VertexConsumer> consumers, String name, int light, PetModels.Anim a) {
      PlayerSkin tex = textures(name);
      if (tex == null) {
         return;
      }

      Identifier id = tex.body().texturePath();
      boolean slim = tex.model() == PlayerModelType.SLIM;
      VertexConsumer vc = consumers.apply(RenderTypes.entityCutout(id));
      m.pushPose();
      float k = 19.0F / 32.0F;
      m.scale(k, k, k);
      float swing = a.legSwing();
      float armSwing = swing * 0.9F + Mth.sin(a.time() * 1.6F) * 3.0F * (1.0F - a.walk());
      // Beine
      limb(m, vc, light, -2.0F, 12.0F, 0, 16, 0, 32, 4, swing);
      limb(m, vc, light, 2.0F, 12.0F, 16, 48, 0, 48, 4, -swing);
      // Körper + Jacke
      m.pushPose();
      m.translate(0.0F, 12.0F, 0.0F);
      cuboid(m, vc, light, -4.0F, 0.0F, -2.0F, 8, 12, 4, 16, 16, 0.0F);
      cuboid(m, vc, light, -4.0F, 0.0F, -2.0F, 8, 12, 4, 16, 32, 0.25F);
      m.popPose();
      // Arme (schmal beim Alex-Modell)
      int aw = slim ? 3 : 4;
      arm(m, vc, light, true, aw, -armSwing, a.flap());
      arm(m, vc, light, false, aw, armSwing, a.flap());
      // Kopf + Hut
      m.pushPose();
      m.translate(0.0F, 24.0F, 0.0F);
      m.rotate(Axis.YP.rotationDegrees(-a.headYaw()));
      m.rotate(Axis.XP.rotationDegrees(Mth.sin(a.time() * 0.9F) * 4.0F * (1.0F - a.walk())));
      cuboid(m, vc, light, -4.0F, 0.0F, -4.0F, 8, 8, 8, 0, 0, 0.0F);
      cuboid(m, vc, light, -4.0F, 0.0F, -4.0F, 8, 8, 8, 32, 0, 0.5F);
      m.popPose();
      m.popPose();
   }

   private static void limb(PoseStack m, VertexConsumer vc, int light, float cx, float pivotY, int u, int v, int ou, int ov, int w, float swingDeg) {
      m.pushPose();
      m.translate(cx, pivotY, 0.0F);
      m.rotate(Axis.XP.rotationDegrees(swingDeg));
      cuboid(m, vc, light, -w / 2.0F, -12.0F, -2.0F, w, 12, 4, u, v, 0.0F);
      cuboid(m, vc, light, -w / 2.0F, -12.0F, -2.0F, w, 12, 4, ou, ov, 0.25F);
      m.popPose();
   }

   private static void arm(PoseStack m, VertexConsumer vc, int light, boolean right, int w, float swingDeg, float flap) {
      // rechter Arm liegt bei -x (Blickrichtung +z)
      float side = right ? -1.0F : 1.0F;
      m.pushPose();
      m.translate(side * (4.0F + w / 2.0F), 22.0F, 0.0F);
      m.rotate(Axis.XP.rotationDegrees(swingDeg));
      m.rotate(Axis.ZP.rotationDegrees(side * (3.0F + flap * 0.15F)));
      int u = right ? 40 : 32;
      int v = right ? 16 : 48;
      int ou = right ? 40 : 48;
      int ov = right ? 32 : 48;
      cuboid(m, vc, light, -w / 2.0F, -10.0F, -2.0F, w, 12, 4, u, v, 0.0F);
      cuboid(m, vc, light, -w / 2.0F, -10.0F, -2.0F, w, 12, 4, ou, ov, 0.25F);
      m.popPose();
   }

   /**
    * Quader mit Minecraft-Skin-UVs. (x,y,z) = Ecke mit kleinstem x/y/z, Größe w×h×d in Pixeln, (u,v) = Texturursprung.
    * Seitenbelegung wie im Skin-Layout: rechts (-x), vorne (+z), links (+x), hinten (-z), oben, unten.
    */
   private static void cuboid(PoseStack m, VertexConsumer vc, int light, float x, float y, float z, int w, int h, int d, int u, int v, float grow) {
      float x1 = x - grow;
      float y1 = y - grow;
      float z1 = z - grow;
      float x2 = x + w + grow;
      float y2 = y + h + grow;
      float z2 = z + d + grow;
      float uR = u;
      float uF = u + d;
      float uL = u + d + w;
      float uB = u + d + w + d;
      float uE = u + d + w + d + w;
      float vTop = v;
      float vSide = v + d;
      float vEnd = v + d + h;
      PoseStack.Pose e = m.last();
      // vorne (+z): Texturbereich vorne, links im Bild = rechte Körperseite (-x)
      quad(vc, e, light, 0, 0, 1, x1, y2, z2, uF, vSide, x2, y2, z2, uL, vSide, x2, y1, z2, uL, vEnd, x1, y1, z2, uF, vEnd);
      // hinten (-z): von hinten gesehen ist links = +x
      quad(vc, e, light, 0, 0, -1, x2, y2, z1, uB, vSide, x1, y2, z1, uE, vSide, x1, y1, z1, uE, vEnd, x2, y1, z1, uB, vEnd);
      // rechte Seite (-x): von außen gesehen ist links = hinten (-z)
      quad(vc, e, light, -1, 0, 0, x1, y2, z1, uR, vSide, x1, y2, z2, uF, vSide, x1, y1, z2, uF, vEnd, x1, y1, z1, uR, vEnd);
      // linke Seite (+x): von außen gesehen ist links = vorne (+z)
      quad(vc, e, light, 1, 0, 0, x2, y2, z2, uL, vSide, x2, y2, z1, uB, vSide, x2, y1, z1, uB, vEnd, x2, y1, z2, uL, vEnd);
      // oben: vordere Kante an der Unterkante des Bereichs
      quad(vc, e, light, 0, 1, 0, x1, y2, z1, uF, vTop, x2, y2, z1, uL, vTop, x2, y2, z2, uL, vSide, x1, y2, z2, uF, vSide);
      // unten
      quad(vc, e, light, 0, -1, 0, x1, y1, z2, uL, vTop, x2, y1, z2, uL + w, vTop, x2, y1, z1, uL + w, vSide, x1, y1, z1, uL, vSide);
   }

   private static void quad(
      VertexConsumer vc,
      PoseStack.Pose e,
      int light,
      float nx,
      float ny,
      float nz,
      float ax,
      float ay,
      float az,
      float au,
      float av,
      float bx,
      float by,
      float bz,
      float bu,
      float bv,
      float cx,
      float cy,
      float cz,
      float cu,
      float cv,
      float dx,
      float dy,
      float dz,
      float du,
      float dv
   ) {
      vertex(vc, e, light, nx, ny, nz, ax, ay, az, au, av);
      vertex(vc, e, light, nx, ny, nz, dx, dy, dz, du, dv);
      vertex(vc, e, light, nx, ny, nz, cx, cy, cz, cu, cv);
      vertex(vc, e, light, nx, ny, nz, bx, by, bz, bu, bv);
   }

   private static void vertex(VertexConsumer vc, PoseStack.Pose e, int light, float nx, float ny, float nz, float x, float y, float z, float u, float v) {
      vc.addVertex(e, x, y, z).setColor(-1).setUv(u / 64.0F, v / 64.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(e, nx, ny, nz);
   }
}
