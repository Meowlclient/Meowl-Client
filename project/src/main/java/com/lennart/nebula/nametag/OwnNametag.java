package com.lennart.nebula.nametag;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.ModulePreview;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.mods.Tiers;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class OwnNametag extends Module {
   private final BooleanSetting showName = this.add(new BooleanSetting("Name anzeigen", "Deinen Namen über dir zeigen", true));
   private final NumberSetting height = this.add(new NumberSetting("Abstand", "Höhe über dem Kopf (zusätzlich)", 0.0, -0.5, 1.5, 0.05, "Blöcke"));
   private final NumberSetting size = this.add(new NumberSetting("Größe", "Größe des Namensschilds", 1.0, 0.5, 2.5, 0.05, "x"));
   private final NumberSetting offsetX = this.add(
      new NumberSetting("Position X", "Nach links (minus) / rechts (plus) verschieben", 0.0, -40.0, 40.0, 1.0, "px")
   );
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe des Namens", -1, false));
   private final NumberSetting opacity = this.add(new NumberSetting("Transparenz", "Deckkraft des Namens", 100.0, 10.0, 100.0, 5.0, "%"));
   private final BooleanSetting background = this.add(new BooleanSetting("Hintergrund", "Dunkler Kasten hinter dem Namen (wie Vanilla)", true));
   private final ColorSetting backgroundColor = this.add(new ColorSetting("Hintergrund-Farbe", "Farbe/Deckkraft des Kastens", 1073741824));
   private final BooleanSetting outline = this.add(new BooleanSetting("Outline", "Schrift mit farbigem Rand (statt Schatten)", false));
   private final ColorSetting outlineColor = this.add(new ColorSetting("Outline-Farbe", "Farbe des Schriftrands", -16777216, false));
   private final BooleanSetting shadow = this.add(new BooleanSetting("Schatten", "Schrift mit Schatten", false));
   private final BooleanSetting meowlIcon = this.add(
      new BooleanSetting("Meowl-Icon", "Meowl-Symbol an deinem Namen (Position/Größe im Modul „Nametags“)", true)
   );
   private final ModeSetting align = this.add(
      new ModeSetting("Ausrichtung", "Ganze Zeile symmetrisch mittig über dem Kopf, oder der Name mittig wie bei Vanilla", "Ganze Zeile mittig", "Ganze Zeile mittig", "Name mittig")
   );
   private final BooleanSetting tiers = this.add(new BooleanSetting("Tiers", "Deine Tiers am Namen (Auswahl links/rechts im Modul „Tiers“)", true));

   public OwnNametag() {
      super("Eigener Nametag", "Dein eigener Name über dir in der 3. Person (F5) – mit Meowl-Symbol und Tiers, voll einstellbar.", Category.RENDER);
      this.icon(Items.NAME_TAG);
      // wird im Modul „Nametags“ mit angezeigt (zusammengelegt)
      this.hidden();
      this.group("Eigener Nametag", new Setting[]{this.showName, this.align, this.height, this.size, this.offsetX});
      this.group("Eigener Nametag · Stil", new Setting[]{this.color, this.opacity, this.background, this.backgroundColor, this.outline, this.outlineColor, this.shadow});
      this.group("Eigener Nametag · Symbole", new Setting[]{this.meowlIcon, this.tiers});
      this.backgroundColor.setVisibility(this.background::get);
      this.outlineColor.setVisibility(this.outline::get);
      this.shadow.setVisibility(() -> !this.outline.get());
   }

   private int textColor(float alphaFactor) {
      int a = Math.round(255.0F * (float)(this.opacity.get() / 100.0) * alphaFactor);
      return a << 24 | this.color.get() & 16777215;
   }

   public void renderLabel(Player player, AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
      Minecraft mc = this.mc;
      if (!mc.options.getCameraType().isFirstPerson() && com.lennart.nebula.compat.Compat.renderNames() && !player.isInvisible() && mc.getCameraEntity() == player) {
         Font tr = mc.font;
         Nametags tags = Nametags.get();
         NametagLayout.Row row = Nametags.collect(player, this.meowlIcon.get(), this.tiers.get());
         if (this.showName.get() || !row.isEmpty()) {
            float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 pos = NametagLayout.labelPos(player, tickDelta);
            boolean sneaking = player.isDiscrete();
            matrices.pushPose();
            matrices.translate(pos.x, pos.y + 0.5 + this.height.get(), pos.z);
            matrices.rotate(camera.orientation);
            float s = 0.025F * (float)this.size.get().doubleValue();
            matrices.scale(s, -s, s);
            matrices.translate((float)this.offsetX.getInt(), state.showExtraEars ? -10.0F : 0.0F, 0.0F);
            FormattedCharSequence name = this.showName.get() ? Component.literal(player.getGameProfile().name()).getVisualOrderText() : null;
            Tiers t = NebulaClient.getInstance().getModuleManager().get(Tiers.class);
            boolean fullBg = this.background.get() && (t == null || t.tierBackground());
            boolean center = !this.align.is("Name mittig");
            NametagStyle.draw(
               tr,
               matrices,
               queue,
               state.lightCoords,
               sneaking,
               name,
               row,
               0.0F,
               center,
               new NametagStyle.Look(
                  this.textColor(1.0F), this.background.get(), this.backgroundColor.get(), this.outline.get(), this.outlineColor.get(), this.shadow.get(), fullBg
               )
            );

            matrices.popPose();
         }
      }
   }

   private static float nameX(Font tr, NametagLayout.Row row, float nameW, boolean center) {
      float leftW = NametagLayout.width(tr, row.left());
      float rightW = NametagLayout.width(tr, row.right());
      float leftBlock = leftW > 0.0F ? leftW + row.gap() : 0.0F;
      float rightBlock = rightW > 0.0F ? rightW + row.gap() : 0.0F;
      float total = leftBlock + nameW + rightBlock;
      float start = center ? -total / 2.0F : -nameW / 2.0F - leftBlock;
      return start + leftBlock;
   }

   @Override
   public ModulePreview preview() {
      return new ModulePreview() {
         @Override
         public int preferredWidth() {
            return 180;
         }

         @Override
         public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
            OwnNametag.this.renderPreview(ctx, x, y, w, h);
         }
      };
   }

   private void renderPreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
      Minecraft mc = this.mc;
      Font tr = mc.font;
      ctx.fillGradient(x, y, x + w, y + h * 3 / 5, -9459492, -4728848);
      ctx.fillGradient(x, y + h * 3 / 5, x + w, y + h, -10646982, -12688345);
      int headX = x + w / 2;
      int headY = y + h / 2 + 4;
      Render2D.roundRect(ctx, headX - 10, headY, 20, 20, 3, -7508406);
      Render2D.roundRect(ctx, headX - 12, headY + 22, 24, 30, 3, -13668432);
      String name = mc.getUser() != null ? mc.getUser().getName() : "Spieler";
      float scale = 1.6F * (float)this.size.get().doubleValue();
      ctx.pose().pushMatrix();
      ctx.pose().translate(headX + this.offsetX.getInt() * scale, headY - 8 - (float)(this.height.get() * 30.0));
      ctx.pose().scale(scale, scale);
      Nametags tags = Nametags.get();
      if (tags != null) {
         NametagLayout.Row row = tags.previewRow(tags.config(), (e, p) -> p);
         GuiCanvas canvas = new GuiCanvas(ctx, this.background.get() ? this.backgroundColor.get() : 0, null);
         FormattedCharSequence ot = this.showName.get() ? Component.literal(name).getVisualOrderText() : null;
         NametagLayout.drawRow(tr, canvas, ot, this.textColor(1.0F), NametagLayout.NameMode.NORMAL, row, 0.0F, !this.align.is("Name mittig"), this.background.get());
      }

      ctx.pose().popMatrix();
      ctx.text(tr, "Sichtbar in F5", x + 6, y + h - 12, -1578509, true);
   }
}
