package com.lennart.nebula.module.hud;

import com.google.gson.JsonObject;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

public class ToggleSprint extends Module {
   private static final String STYLE_TEXT = "Text";
   private static final String STYLE_PILL = "Pille";
   private static final String STYLE_COMPACT = "Kompakt";
   private final KeybindSetting toggleKey = this.add(
      new KeybindSetting("Umschalt-Taste", "Taste zum Umschalten. \"Keine\" = die normale Sprint-Taste aus den Optionen", Bind.NONE)
   );
   private final BooleanSetting sneak = this.add(new BooleanSetting("Toggle Sneak", "Schleichen ebenfalls per Tastendruck an/aus", false));
   private final KeybindSetting sneakKey = this.add(
      new KeybindSetting("Sneak-Taste", "Taste zum Umschalten. \"Keine\" = die normale Schleich-Taste", Bind.NONE)
   );
   private final BooleanSetting display = this.add(new BooleanSetting("Anzeige", "Status im HUD anzeigen (Position im HUD-Editor)", true));
   private final ModeSetting style = this.add(new ModeSetting("Anzeige-Stil", "Aussehen der Anzeige", "Pille", "Pille", "Text", "Kompakt"));
   private final TextSetting text = this.add(new TextSetting("Text", "Anzeigetext im Stil „Text“ (danach folgt AN/AUS)", "SPRINT: ", 32));
   private final ColorSetting onColor = this.add(new ColorSetting("Farbe AN", "Farbe, wenn Sprint an ist", -7934036));
   private final ColorSetting offColor = this.add(new ColorSetting("Farbe AUS", "Farbe, wenn Sprint aus ist", -495247));
   private final BooleanSetting onlyActive = this.add(new BooleanSetting("Nur wenn aktiv", "An: nur anzeigen, wenn Sprint/Sneak an ist", false));
   private final HudBox box = this.hudBox("Sprint-Anzeige", 1.0, 1.0);
   private boolean sprintOn;
   private boolean sneakOn;
   private boolean lastPressed;
   private boolean lastSneakPressed;

   public ToggleSprint() {
      super("Toggle Sprint", "Sprint (und optional Schleichen) per Tastendruck an/aus – mit eigener HUD-Anzeige.", Category.MOVEMENT);
      this.icon(Items.SUGAR);
      this.legacy(new String[]{"Toggle Sprint Display"});
      this.group("Sprint", new Setting[]{this.toggleKey});
      this.group("Sneak", new Setting[]{this.sneak, this.sneakKey});
      List<Setting<?>> displaySettings = new ArrayList<>(List.of(this.display, this.style, this.text, this.onColor, this.offColor, this.onlyActive));
      displaySettings.addAll(List.of(this.box.scale, this.box.opacity, this.box.background, this.box.backgroundColor, this.box.rounding));
      this.group("Anzeige", displaySettings.toArray(new Setting[0]));
      this.sneakKey.setVisibility(this.sneak::get);

      for (Setting<?> s : displaySettings) {
         if (s != this.display) {
            s.setVisibility(this.display::get);
         }
      }

      this.text.setVisibility(() -> this.display.get() && this.style.is("Text"));
      this.box.backgroundColor.setVisibility(() -> this.display.get() && this.box.background.get());
      this.box.rounding.setVisibility(() -> this.display.get() && this.box.background.get());
   }

   public boolean isSprintToggled() {
      return this.vanillaToggle() ? this.mc.options.keySprint.isDown() : this.isEnabled() && this.sprintOn;
   }

   public boolean isSneakToggled() {
      return this.vanillaSneakToggle() ? this.mc.options.keyShift.isDown() : this.isEnabled() && this.sneak.get() && this.sneakOn;
   }

   private boolean vanillaToggle() {
      return this.mc.options != null && this.mc.options.toggleSprint().get();
   }

   private boolean vanillaSneakToggle() {
      return this.mc.options != null && this.mc.options.toggleCrouch().get();
   }

   @Override
   protected void onDisable() {
      this.sprintOn = false;
      this.sneakOn = false;
      if (this.mc.options != null) {
         if (!this.vanillaToggle()) {
            this.mc.options.keySprint.setDown(physicallyPressed(this.mc.options.keySprint));
         }

         if (!this.vanillaSneakToggle()) {
            this.mc.options.keyShift.setDown(physicallyPressed(this.mc.options.keyShift));
         }
      }
   }

   private static boolean physicallyPressed(KeyMapping key) {
      Key bound = KeyMappingHelper.getBoundKeyOf(key);
      if (bound != null && bound.getValue() >= 0) {
         Bind bind = bound.getType() == Type.MOUSE ? Bind.mouse(bound.getValue()) : Bind.keyboard(bound.getValue());
         return bind.isPressed();
      } else {
         return false;
      }
   }

   @Override
   public void onPreTick() {
      if (this.mc.player != null) {
         if (!this.vanillaToggle()) {
            KeyMapping sprint = this.mc.options.keySprint;
            boolean useOwnKey = !this.toggleKey.get().isNone();
            boolean pressed = useOwnKey ? this.toggleKey.get().isPressed() : physicallyPressed(sprint);
            boolean wasOn = this.sprintOn;
            if (this.mc.gui.screen() == null && pressed && !this.lastPressed) {
               this.sprintOn = !this.sprintOn;
            }

            this.lastPressed = pressed;
            if (this.sprintOn) {
               sprint.setDown(true);
            } else if (wasOn || !useOwnKey) {
               sprint.setDown(physicallyPressed(sprint));
            }
         }

         if (!this.vanillaSneakToggle()) {
            KeyMapping sneakBind = this.mc.options.keyShift;
            if (!this.sneak.get()) {
               if (this.sneakOn) {
                  this.sneakOn = false;
                  sneakBind.setDown(physicallyPressed(sneakBind));
               }
            } else {
               boolean useOwn = !this.sneakKey.get().isNone();
               boolean pressedx = useOwn ? this.sneakKey.get().isPressed() : physicallyPressed(sneakBind);
               boolean wasOnx = this.sneakOn;
               if (this.mc.gui.screen() == null && pressedx && !this.lastSneakPressed) {
                  this.sneakOn = !this.sneakOn;
               }

               this.lastSneakPressed = pressedx;
               if (this.sneakOn) {
                  sneakBind.setDown(true);
               } else if (wasOnx || !useOwn) {
                  sneakBind.setDown(physicallyPressed(sneakBind));
               }
            }
         }
      }
   }

   @Override
   public void onRenderHud(GuiGraphicsExtractor ctx, float tickDelta) {
      if (this.display.get() && (!this.mc.gui.hud.isHidden() || HudLayout.isEditing())) {
         boolean sprint = this.isSprintToggled();
         boolean sneaking = this.isSneakToggled();
         boolean editing = HudLayout.isEditing();
         if (!this.onlyActive.get() || sprint || sneaking || editing) {
            this.drawDisplay(ctx, sprint, sneaking);
         }
      }
   }

   private void drawDisplay(GuiGraphicsExtractor ctx, boolean sprint, boolean sneaking) {
      Font tr = this.mc.font;
      String style = this.style.get();
      int on = this.onColor.get();
      int off = this.offColor.get();
      switch (style) {
         case "Text":
            String label = this.text.get();
            String state = sprint ? "AN" : "AUS";
            String extra = this.sneak.get() ? (sneaking ? "  SNEAK" : "") : "";
            int w = tr.width(label + state + extra) + 8;
            this.box.begin(ctx, w, 13);
            ctx.text(tr, label, 4, 3, this.box.fade(-1578509), true);
            ctx.text(tr, state, 4 + tr.width(label), 3, this.box.fade(sprint ? on : off), true);
            if (!extra.isEmpty()) {
               ctx.text(tr, extra, 4 + tr.width(label + state), 3, this.box.fade(on), true);
            }

            this.box.end(ctx);
            break;
         case "Kompakt":
            String s = (sprint ? "S" : "s") + (this.sneak.get() ? (sneaking ? " N" : " n") : "");
            int cw = tr.width(s) + 8;
            this.box.begin(ctx, cw, 13);
            ctx.text(tr, s, 4, 3, this.box.fade(!sprint && !sneaking ? off : on), true);
            this.box.end(ctx);
            break;
         default:
            String a = "Sprint";
            String b = sneaking ? "Sneak" : null;
            int dw = 14 + tr.width(a) + 6 + (b != null ? 14 + tr.width(b) + 6 : 0);
            int h = 14;
            this.box.begin(ctx, dw, h, false);
            Render2D.roundRect(ctx, 0, 0, dw, h, 7, this.box.fade(this.box.backgroundColor.get()));
            Render2D.circle(ctx, 7, 7, 3, this.box.fade(sprint ? on : off));
            ctx.text(tr, a, 13, 3, this.box.fade(sprint ? -1 : -6643528), false);
            if (b != null) {
               int x = 13 + tr.width(a) + 6;
               Render2D.circle(ctx, x + 1, 7, 3, this.box.fade(on));
               ctx.text(tr, b, x + 7, 3, this.box.fade(-1), false);
            }

            this.box.end(ctx);
      }
   }

   @Override
   public void applyLegacy(String legacyName, boolean legacyEnabled, JsonObject ownSettings) {
      if (legacyName.equals("Toggle Sprint Display") && (ownSettings == null || !ownSettings.has(this.display.getName()))) {
         this.display.set(legacyEnabled);
      }
   }
}
