package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.gui.NetherCoordsScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.Setting;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class NetherCoords extends Module {
   private final BooleanSetting hud = this.add(new BooleanSetting("HUD-Anzeige", "Deine Position in der jeweils anderen Dimension im HUD", true));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Textfarbe der HUD-Zeile", -30107, false));
   private final KeybindSetting openKey = this.add(new KeybindSetting("Rechner-Taste", "Öffnet den Umrechner", Bind.NONE));
   private final HudBox box = this.hudBox("Nether-Koordinaten", 1.0, 30.0);
   private boolean keyDown;

   public NetherCoords() {
      super("Nether-Koordinaten", "Rechnet Overworld ↔ Nether um (X/Z ÷ 8 bzw. × 8) – als Rechner und HUD-Zeile.", Category.WORLD);
      this.section(Section.TOOLS);
      this.icon(Items.OBSIDIAN);
      this.add(new ActionSetting("Rechner", "Umrechner öffnen", "Öffnen", () -> this.open("", "", "")));
      this.group(
         "Anzeige", new Setting[]{this.hud, this.color, this.box.scale, this.box.opacity, this.box.background, this.box.backgroundColor, this.box.rounding}
      );
      this.group("Rechner", new Setting[]{this.openKey});
      MeowlCommands.add(
         "nethercoords",
         "nethercoords [x y z] §7– Nether/Oberwelt umrechnen (kurz: nc)",
         root -> ClientCommands.literal(root)
            .executes(ctx -> {
               this.open("", "", "");
               return 1;
            })
            .then(
               ClientCommands.argument("x", StringArgumentType.word())
                  .then(
                     ClientCommands.argument("y", StringArgumentType.word())
                        .then(ClientCommands.argument("z", StringArgumentType.word()).executes(ctx -> {
                           this.open(StringArgumentType.getString(ctx, "x"), StringArgumentType.getString(ctx, "y"), StringArgumentType.getString(ctx, "z"));
                           return 1;
                        }))
                  )
            ),
         "nc"
      );
   }

   public void open(String x, String y, String z) {
      this.mc.schedule(() -> this.mc.gui.setScreen(new NetherCoordsScreen(this.mc.gui.screen(), x, y, z)));
   }

   public static boolean inNether(Level world) {
      return world != null && world.dimension() == Level.NETHER;
   }

   public static double[] convert(double x, double z, boolean toNether) {
      double f = toNether ? 0.125 : 8.0;
      return new double[]{x * f, z * f};
   }

   public static String fmt(double v) {
      return Math.abs(v - Math.rint(v)) < 1.0E-9 ? String.valueOf((long)Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
   }

   @Override
   public void onTick() {
      boolean d = this.openKey.get().isPressed();
      if (d && !this.keyDown && this.mc.gui.screen() == null) {
         this.open("", "", "");
      }

      this.keyDown = d;
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = HudLayout.isEditing();
      if (this.mc.player != null && (this.hud.get() || editing) && (!this.mc.gui.hud.isHidden() || editing)) {
         Level w = this.mc.level;
         boolean nether = inNether(w);
         if (!nether && w.dimension() != Level.OVERWORLD && !editing) {
            return;
         }

         double[] r = convert(this.mc.player.getX(), this.mc.player.getZ(), !nether);
         String text = (nether ? "Overworld: " : "Nether: ") + (long)Math.floor(r[0]) + " " + this.mc.player.getBlockY() + " " + (long)Math.floor(r[1]);
         int tw = this.mc.font.width(text);
         this.box.begin(ctx, tw + 8, 13);
         ctx.text(this.mc.font, text, 4, 3, this.box.fade(this.color.get()), true);
         this.box.end(ctx);
      }
   }
}
