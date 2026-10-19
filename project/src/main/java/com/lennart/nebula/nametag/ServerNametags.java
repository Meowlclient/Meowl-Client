package com.lennart.nebula.nametag;

import com.lennart.nebula.NebulaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.Display.TextDisplay.CachedLine;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ServerNametags {
   private static final Map<UUID, Long> MATCHED = new ConcurrentHashMap<>();
   private static final long KEEP_NS = 250000000L;

   private ServerNametags() {
   }

   public static boolean hasServerNametag(Player player) {
      Long t = MATCHED.get(player.getUUID());
      return t != null && System.nanoTime() - t < 250000000L;
   }

   private static boolean active() {
      Nametags tags = Nametags.get();
      return tags != null && tags.isEnabled() && tags.showWhenHidden() && com.lennart.nebula.compat.Compat.renderNames();
   }

   public static String plain(FormattedCharSequence text) {
      StringBuilder sb = new StringBuilder();
      text.accept((index, style, codePoint) -> {
         sb.appendCodePoint(codePoint);
         return true;
      });
      return sb.toString();
   }

   public static boolean containsName(String text, String name) {
      String t = text.toLowerCase(Locale.ROOT);
      String n = name.toLowerCase(Locale.ROOT);

      for (int i = t.indexOf(n); i >= 0; i = t.indexOf(n, i + 1)) {
         boolean before = i == 0 || !isNameChar(t.charAt(i - 1));
         int end = i + n.length();
         boolean after = end >= t.length() || !isNameChar(t.charAt(end));
         if (before && after) {
            return true;
         }
      }

      return false;
   }

   private static boolean isNameChar(char c) {
      return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
   }

   static Player owner(double x, double y, double z, String text) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null && !text.isEmpty()) {
         Player best = null;
         double bestD = Double.MAX_VALUE;

         for (Player p : mc.level.players()) {
            if (p != mc.getCameraEntity()) {
               Vec3 pos = p.position();
               double dx = x - pos.x;
               double dz = z - pos.z;
               double dy = y - pos.y;
               double h = dx * dx + dz * dz;
               if (h <= 2.25 && dy >= 0.5 && dy <= 4.5 && containsName(text, p.getGameProfile().name()) && h < bestD) {
                  bestD = h;
                  best = p;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static boolean visible(Minecraft mc, Player p) {
      return !p.isSpectator() && !p.isInvisibleTo(mc.player);
   }

   /** Server-Nametags, die Meowl durch einen eigenen (gestylten) Nametag ersetzt. */
   private static final Map<UUID, Long> REPLACED = new ConcurrentHashMap<>();

   /** Wird der Server-Nametag dieses Spielers gerade durch Meowl ersetzt? */
   public static boolean replaced(Player player) {
      Long t = REPLACED.get(player.getUUID());
      return t != null && System.nanoTime() - t < 250000000L;
   }

   private static boolean replacing() {
      Nametags tags = Nametags.get();
      return active() && tags.replaceServerTags();
   }

   /** Text-Display mit Spielernamen ausblenden, wenn Meowl ihn ersetzt (vor dem Zeichnen aufgerufen). */
   public static boolean hideTextDisplay(TextDisplayEntityRenderState state) {
      if (state.cachedInfo != null && replacing()) {
         Minecraft mc = Minecraft.getInstance();

         try {
            for (CachedLine line : state.cachedInfo.lines()) {
               Player owner = owner(state.x, state.y, state.z, plain(line.contents()));
               if (owner != null && visible(mc, owner)) {
                  REPLACED.put(owner.getUUID(), System.nanoTime());
                  return true;
               }
            }
         } catch (RuntimeException var5) {
            NebulaClient.LOGGER.debug("Text-Display nicht ersetzt: {}", var5.toString());
         }
      }

      return false;
   }

   public static boolean renderEntityLabel(EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
      if (state.nameTag != null && state.nameTagAttachment != null && active()) {
         Minecraft mc = Minecraft.getInstance();

         try {
            FormattedCharSequence text = state.nameTag.getVisualOrderText();
            Player owner = owner(state.x, state.y + state.nameTagAttachment.y, state.z, plain(text));
            if (owner != null && visible(mc, owner) && replacing()) {
               // Server-Name ausblenden – Meowl zeichnet den Nametag mit deinen Einstellungen am Spieler
               REPLACED.put(owner.getUUID(), System.nanoTime());
               return true;
            }

            if (owner != null && visible(mc, owner)) {
               NametagLayout.Row row = Nametags.collect(owner, true, true, plain(text));
               if (NametagLayout.hasCustomGraphics(text) && NametagLayout.stackGraphics()) {
                  row = NametagLayout.stacked(row);
               }

               if (row.isEmpty()) {
                  return false;
               } else {
                  MATCHED.put(owner.getUUID(), System.nanoTime());
                  Nametags tags = Nametags.get();
                  Vec3 pos = state.nameTagAttachment;
                  matrices.pushPose();
                  matrices.translate(pos.x, pos.y + 0.5, pos.z);
                  matrices.rotate(camera.orientation);
                  matrices.scale(0.025F, -0.025F, 0.025F);
                  NametagLayout.applyUserTransform(matrices, tags);
                  LabelCanvas canvas = new LabelCanvas(queue, matrices, !state.isDiscrete, state.lightCoords, NametagLayout.vanillaBackground(mc));
                  NametagLayout.drawRow(
                     mc.font,
                     canvas,
                     text,
                     -1,
                     NametagLayout.NameMode.VANILLA,
                     row,
                     0.0F,
                     tags == null || tags.centerGroup(),
                     NametagLayout.rowBackgroundEnabled()
                  );
                  matrices.popPose();
                  return true;
               }
            } else {
               return false;
            }
         } catch (RuntimeException var11) {
            NebulaClient.LOGGER.debug("Server-Nametag nicht erweitert: {}", var11.toString());
            return false;
         }
      } else {
         return false;
      }
   }

   public static void renderTextDisplay(TextDisplayEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, int light) {
      if (state.cachedInfo != null && state.textRenderState != null && active()) {
         Minecraft mc = Minecraft.getInstance();

         try {
            List<CachedLine> lines = state.cachedInfo.lines();

            for (int j = 0; j < lines.size(); j++) {
               CachedLine line = lines.get(j);
               Player owner = owner(state.x, state.y, state.z, plain(line.contents()));
               if (owner != null) {
                  if (!visible(mc, owner)) {
                     return;
                  }

                  StringBuilder all = new StringBuilder();

                  for (CachedLine l : lines) {
                     all.append(plain(l.contents())).append('\n');
                  }

                  NametagLayout.Row row = Nametags.collect(owner, true, true, all.toString());
                  if (row.isEmpty()) {
                     return;
                  }

                  boolean graphics = false;

                  for (CachedLine l : lines) {
                     graphics |= NametagLayout.hasCustomGraphics(l.contents());
                  }

                  if (graphics && NametagLayout.stackGraphics()) {
                     // über der obersten Zeile der Server-Anzeige statt daneben
                     row = NametagLayout.stacked(row);
                     line = lines.get(0);
                     j = 0;
                  }

                  MATCHED.put(owner.getUUID(), System.nanoTime());
                  int m = state.cachedInfo.width();

                  float h = switch (TextDisplay.getAlign(state.textRenderState.flags())) {
                     case LEFT -> 0.0F;
                     case RIGHT -> m - line.width();
                     case CENTER -> m / 2.0F - line.width() / 2.0F;
                  };
                  LabelCanvas canvas = new LabelCanvas(queue, matrices, true, light, NametagLayout.vanillaBackground(mc)).polygonOffset();
                  NametagLayout.drawBeside(mc.font, canvas, row, h, line.width(), j * 10.0F, false);
                  return;
               }
            }
         } catch (RuntimeException var14) {
            NebulaClient.LOGGER.debug("Text-Display-Nametag nicht erweitert: {}", var14.toString());
         }
      }
   }
}
