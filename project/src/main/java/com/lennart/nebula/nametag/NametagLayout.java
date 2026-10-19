package com.lennart.nebula.nametag;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.mods.Tiers;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class NametagLayout {
   public static final float CENTER_Y = 3.5F;
   public static final float SPACING = 3.0F;
   public static final Object NAME_TAG = "NAME";
   private static final float ABOVE_LINE = 11.0F;

   private NametagLayout() {
   }

   public static boolean render(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         if (mc.level.getEntity(state.id) instanceof Player player) {
            try {
               if (player == mc.player && state.nameTag == null) {
                  OwnNametag own = NebulaClient.getInstance().getModuleManager().get(OwnNametag.class);
                  if (own != null && own.isEnabled()) {
                     own.renderLabel(player, state, matrices, queue, camera);
                  }

                  return false;
               } else {
                  Nametags tags = Nametags.get();
                  boolean labelShown = state.nameTag != null && state.nameTagAttachment != null;
                  if (labelShown || showWithoutVanillaLabel(mc, player, state, tags) && !ServerNametags.hasServerNametag(player)) {
                     NametagLayout.Row row = Nametags.collect(player, true, true, labelShown ? state.nameTag.getString() : null);
                     boolean graphics = labelShown && hasCustomGraphics(state.nameTag.getVisualOrderText());
                     if (graphics && tags != null && tags.stackOnGraphics()) {
                        row = stacked(row);
                     }
                     // versteckter Server-Name: Meowl zeichnet den Namen selbst – auch ohne Symbole/Tiers
                     boolean ownName = !labelShown && tags != null && (tags.drawHiddenName() || ServerNametags.replaced(player));
                     boolean styled = tags != null && tags.othersStyled() && !graphics;
                     if (row.isEmpty() && !ownName && !(styled && labelShown)) {
                        return false;
                     } else {
                        VoicePiece.begin(matrices.last().pose());
                        matrices.pushPose();
                        int yBase = state.showExtraEars ? -10 : 0;
                        if (state.scoreText != null) {
                           queue.submitNameTag(
                              matrices, state.nameTagAttachment, yBase, state.scoreText, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera
                           );
                           matrices.translate(0.0F, 0.25875F, 0.0F);
                        }

                        Vec3 pos = labelShown ? state.nameTagAttachment : labelPos(player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
                        matrices.translate(pos.x, pos.y + 0.5, pos.z);
                        matrices.mulPose(camera.orientation);
                        matrices.scale(0.025F, -0.025F, 0.025F);
                        applyUserTransform(matrices, tags);
                        NameCanvas canvas = new LabelCanvas(queue, matrices, !state.isDiscrete, state.lightCoords, vanillaBackground(mc));
                        boolean center = tags == null || tags.centerGroup();
                        FormattedCharSequence styledName = labelShown ? state.nameTag.getVisualOrderText() : (ownName ? player.getDisplayName().getVisualOrderText() : null);
                        if (styled && styledName != null) {
                           // gleicher Stil wie beim eigenen Nametag (Farbe, Hintergrund, Schriftrand, Schatten)
                           NametagStyle.draw(mc.font, matrices, queue, state.lightCoords, state.isDiscrete, styledName, row, yBase, center, tags.othersLook(rowBackground()));
                        } else if (labelShown) {
                           drawRow(
                              mc.font,
                              canvas,
                              state.nameTag.getVisualOrderText(),
                              -1,
                              NametagLayout.NameMode.VANILLA,
                              row,
                              yBase,
                              center,
                              rowBackground()
                           );
                        } else if (tags != null && tags.drawHiddenName()) {
                           drawRow(
                              mc.font,
                              canvas,
                              player.getDisplayName().getVisualOrderText(),
                              -1,
                              NametagLayout.NameMode.NORMAL,
                              row,
                              yBase,
                              center,
                              rowBackground()
                           );
                        } else {
                           drawRow(
                              mc.font,
                              canvas,
                              player.getDisplayName().getVisualOrderText(),
                              0,
                              NametagLayout.NameMode.RESERVE,
                              row,
                              yBase,
                              center,
                              rowBackground()
                           );
                        }

                        matrices.popPose();
                        return true;
                     }
                  } else {
                     return false;
                  }
               }
            } catch (RuntimeException var14) {
               NebulaClient.LOGGER.warn("Nametag von {} konnte nicht gezeichnet werden: {}", player.getGameProfile().name(), var14.toString());
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean showWithoutVanillaLabel(Minecraft mc, Player player, AvatarRenderState state, Nametags tags) {
      if (tags == null || !tags.isEnabled() || !tags.showWhenHidden() || !com.lennart.nebula.compat.Compat.renderNames()) {
         return false;
      } else if (player != mc.getCameraEntity() && !player.isSpectator() && !player.isInvisibleTo(mc.player)) {
         double max = player.isDiscrete() ? 32.0 : 64.0;
         return state.distanceToCameraSq < max * max;
      } else {
         return false;
      }
   }

   public static void applyUserTransform(PoseStack matrices, Nametags tags) {
      if (tags != null) {
         float s = tags.rowScale();
         matrices.translate(0.0F, 8.0F + tags.rowYOffset(), 0.0F);
         matrices.scale(s, s, s);
         matrices.translate(0.0F, -8.0F, 0.0F);
      }
   }

   public static int vanillaBackground(Minecraft mc) {
      return (int)(mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
   }

   public static boolean rowBackgroundEnabled() {
      return rowBackground();
   }

   public static void drawBeside(Font tr, NameCanvas canvas, NametagLayout.Row row, float nameX, float nameW, float y, boolean background) {
      float leftW = width(tr, row.left());
      float rightW = width(tr, row.right());
      float lx = nameX - row.gap() - leftW;
      float rx = nameX + nameW + row.gap();
      int bg = canvas.backgroundColor();
      if (background) {
         if (leftW > 0.0F) {
            canvas.background(lx - 1.0F, y - 1.0F, lx + leftW + 1.0F, y + 9.0F, bg);
         }

         if (rightW > 0.0F) {
            canvas.background(rx - 1.0F, y - 1.0F, rx + rightW + 1.0F, y + 9.0F, bg);
         }
      }

      drawPieces(tr, canvas, row.left(), lx, y);
      drawPieces(tr, canvas, row.right(), rx, y);
      if (!row.above().isEmpty()) {
         float aw = width(tr, row.above());
         float ax = nameX + nameW / 2.0F - aw / 2.0F;
         float ay = y - 11.0F + row.aboveOffset();
         if (background) {
            canvas.background(ax - 1.0F, ay - 1.0F, ax + aw + 1.0F, ay + 9.0F, bg);
         }

         drawPieces(tr, canvas, row.above(), ax, ay);
      }
   }

   private static boolean rowBackground() {
      Tiers t = NebulaClient.getInstance().getModuleManager().get(Tiers.class);
      return t == null || t.tierBackground();
   }

   public static Vec3 labelPos(Player player, float tickDelta) {
      Vec3 p = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getYRot(tickDelta));
      return p != null ? p : new Vec3(0.0, player.getBbHeight(), 0.0);
   }

   public static void drawRow(
      Font tr,
      NameCanvas canvas,
      FormattedCharSequence name,
      int nameColor,
      NametagLayout.NameMode mode,
      NametagLayout.Row row,
      float y,
      boolean centerGroup,
      boolean fullBackground
   ) {
      float gap = row.gap();
      float nameW = name == null ? 0.0F : tr.width(name);
      float leftW = width(tr, row.left());
      float rightW = width(tr, row.right());
      float leftBlock;
      float rightBlock;
      if (name != null) {
         leftBlock = leftW > 0.0F ? leftW + gap : 0.0F;
         rightBlock = rightW > 0.0F ? rightW + gap : 0.0F;
      } else {
         leftBlock = leftW + (leftW > 0.0F && rightW > 0.0F ? 3.0F : 0.0F);
         rightBlock = rightW;
      }

      float total = leftBlock + nameW + rightBlock;
      float start = centerGroup ? -total / 2.0F : -nameW / 2.0F - leftBlock;
      float nameX = start + leftBlock;
      int bg = canvas.backgroundColor();
      boolean drawsName = name != null && (mode == NametagLayout.NameMode.VANILLA || mode == NametagLayout.NameMode.NORMAL);
      if (fullBackground && total > 0.0F) {
         if (mode != NametagLayout.NameMode.RESERVE && name != null) {
            if (mode == NametagLayout.NameMode.VANILLA) {
               canvas.background(start - 1.0F, y - 1.0F, nameX - 1.0F, y + 9.0F, bg);
               canvas.background(nameX + nameW, y - 1.0F, start + total + 1.0F, y + 9.0F, bg);
            } else {
               canvas.background(start - 1.0F, y - 1.0F, start + total + 1.0F, y + 9.0F, bg);
            }
         } else {
            if (leftW > 0.0F) {
               canvas.background(start - 1.0F, y - 1.0F, start + leftW + 1.0F, y + 9.0F, bg);
            }

            if (rightW > 0.0F) {
               canvas.background(start + total - rightW - 1.0F, y - 1.0F, start + total + 1.0F, y + 9.0F, bg);
            }
         }
      } else if (name != null && mode != NametagLayout.NameMode.RESERVE && mode != NametagLayout.NameMode.VANILLA) {
         canvas.background(nameX - 1.0F, y - 1.0F, nameX + nameW, y + 9.0F, bg);
      }

      if (name != null) {
         canvas.element(NAME_TAG, nameX, y, nameW, 8.0F);
      }

      drawPieces(tr, canvas, row.left(), start, y);
      if (drawsName) {
         if (mode == NametagLayout.NameMode.VANILLA) {
            canvas.vanillaName(name, nameX, y, nameColor, true);
         } else {
            canvas.text(name, nameX, y, nameColor);
         }
      }

      drawPieces(tr, canvas, row.right(), start + total - rightW, y);
      if (!row.above().isEmpty()) {
         float aw = width(tr, row.above());
         float ay = y - 11.0F + row.aboveOffset();
         if (fullBackground) {
            canvas.background(-aw / 2.0F - 1.0F, ay - 1.0F, aw / 2.0F + 1.0F, ay + 9.0F, bg);
         }

         drawPieces(tr, canvas, row.above(), -aw / 2.0F, ay);
      }
   }

   /**
    * Hat der Text eigene Server-Grafiken (Resource-Pack-Schrift oder Zeichen aus dem privaten Bereich, z.B. Rang-Bilder)?
    * Solche Bilder sind oft größer als eine Zeile oder werden mit Leerraum-Zeichen verschoben – daneben würde es
    * überlappen.
    */
   public static boolean hasCustomGraphics(FormattedCharSequence text) {
      if (text == null) {
         return false;
      } else {
         boolean[] found = new boolean[1];
         text.accept((index, style, cp) -> {
            if (cp >= 0xE000 && cp <= 0xF8FF || cp >= 0xF0000 || !net.minecraft.network.chat.FontDescription.DEFAULT.equals(style.getFont())) {
               found[0] = true;
               return false;
            } else {
               return true;
            }
         });
         return found[0];
      }
   }

   /** Bei Server-Grafiken nach oben ausweichen (Einstellung in Nametags, Standard aus)? */
   public static boolean stackGraphics() {
      Nametags tags = Nametags.get();
      return tags != null && tags.stackOnGraphics();
   }

   /** Alle Symbole/Tiers als eigene Zeile über dem Namen (mit Abstand), damit nichts mit Server-Grafiken überlappt. */
   public static NametagLayout.Row stacked(NametagLayout.Row row) {
      if (row.left().isEmpty() && row.right().isEmpty()) {
         return row;
      } else {
         Nametags tags = Nametags.get();
         float gap = tags == null ? 10.0F : tags.serverGraphicsGap();
         java.util.List<NametagPiece> all = new java.util.ArrayList<>(row.left());
         all.addAll(row.above());
         all.addAll(row.right());
         return new NametagLayout.Row(List.of(), List.of(), all, row.gap(), row.aboveOffset() - gap);
      }
   }

   public static float width(Font tr, List<NametagPiece> pieces) {
      float w = 0.0F;

      for (int i = 0; i < pieces.size(); i++) {
         w += pieces.get(i).width(tr) + (i > 0 ? 3.0F : 0.0F);
      }

      return w;
   }

   private static void drawPieces(Font tr, NameCanvas canvas, List<NametagPiece> pieces, float x, float y) {
      for (NametagPiece p : pieces) {
         p.draw(tr, canvas, x, y);
         x += p.width(tr) + 3.0F;
      }
   }

   public static enum NameMode {
      VANILLA,
      NORMAL,
      RESERVE_WITH_BACKGROUND,
      RESERVE;
   }

   public record Row(List<NametagPiece> left, List<NametagPiece> right, List<NametagPiece> above, float gap, float aboveOffset) {
      public boolean isEmpty() {
         return this.left.isEmpty() && this.right.isEmpty() && this.above.isEmpty();
      }
   }

   /**
    * Symbol von Simple Voice Chat zeichnen lassen – an der Stelle, die im Nametag-Editor für „Voice-Chat-Symbol“
    * eingestellt ist (Links/Rechts/Darüber), oder gar nicht (Ausgeblendet).
    */
   public static void drawVoiceIcon(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
      Nametags tags = Nametags.get();
      if (tags != null && tags.config().zoneOf(NametagConfig.Element.VOICE) == NametagConfig.Zone.HIDDEN) {
         VoicePiece.take(state.id);
         return;
      }

      // Voice Chat zeichnet nur, wenn ein Vanilla-Nametag da ist – sonst kurz einen setzen
      net.minecraft.network.chat.Component oldTag = state.nameTag;
      Vec3 oldAt = state.nameTagAttachment;
      if (state.nameTag == null) {
         state.nameTag = net.minecraft.network.chat.Component.literal(" ");
      }

      if (state.nameTagAttachment == null) {
         state.nameTagAttachment = new Vec3(0.0, state.boundingBoxHeight + 0.5, 0.0);
      }

      org.joml.Vector3f want = VoicePiece.take(state.id);
      matrices.pushPose();

      try {
         if (want != null) {
            // wo Voice Chat das Symbol von sich aus hinsetzt: rechts neben dem Namen, eine Zeile hoch
            float iconX = Minecraft.getInstance().font.width(state.nameTag) / 2 + 2;
            org.joml.Vector3f own = new org.joml.Vector3f(0.025F * iconX, 0.025F, 0.0F).rotate(camera.orientation);
            own.add((float)state.nameTagAttachment.x, (float)state.nameTagAttachment.y + 0.5F, (float)state.nameTagAttachment.z);
            matrices.translate(want.x - own.x, want.y - own.y, want.z - own.z);
         }

         com.lennart.nebula.compat.VoiceChatCompat.renderNameplateIcons(state, camera, matrices, queue);
      } finally {
         matrices.popPose();
         state.nameTag = oldTag;
         state.nameTagAttachment = oldAt;
      }
   }
}