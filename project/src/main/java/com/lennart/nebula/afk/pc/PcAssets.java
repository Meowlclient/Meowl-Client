package com.lennart.nebula.afk.pc;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.afk.space.Tex;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Lädt Minecraft-Texturen (aus dem aktiven Ressourcenpaket) als {@link Tex} – auf dem Render-Thread aufrufen. */
public final class PcAssets {
   private PcAssets() {
   }

   /** Pfade relativ zu {@code assets/minecraft/textures/}, ohne „.png“ (z.B. „block/oak_planks“). */
   public static Map<String, Tex> load(Iterable<String> paths) {
      Map<String, Tex> out = new HashMap<>();
      Minecraft mc = Minecraft.getInstance();

      for (String path : paths) {
         try {
            Optional<Resource> res = mc.getResourceManager().getResource(Identifier.withDefaultNamespace("textures/" + path + ".png"));
            if (res.isPresent()) {
               try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                  Tex t = new Tex(img.getWidth(), img.getHeight());

                  for (int y = 0; y < t.h; y++) {
                     for (int x = 0; x < t.w; x++) {
                        t.px[y * t.w + x] = img.getPixel(x, y);
                     }
                  }

                  out.put(path, t);
               }
            }
         } catch (Exception var11) {
            NebulaClient.LOGGER.debug("Textur {} fehlt: {}", path, var11.toString());
         }
      }

      return out;
   }
}
