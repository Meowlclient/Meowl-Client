package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.mods.NetherCoords;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NetherCoordsScreen extends Screen {
   private final Screen parent;
   private final MeowlTextField[] fields = new MeowlTextField[]{
      new MeowlTextField(12).placeholder("X"), new MeowlTextField(12).placeholder("Y"), new MeowlTextField(12).placeholder("Z")
   };
   private boolean toNether = true;
   private int px;
   private int py;
   private int pw;
   private int ph;
   private String copied = "";

   public NetherCoordsScreen(Screen parent, String x, String y, String z) {
      super(Component.literal("Nether-Koordinaten"));
      this.parent = parent;
      this.fields[0].setText(x);
      this.fields[1].setText(y);
      this.fields[2].setText(z);
      // ohne Vorgabe: die Werte vom letzten Mal (auch nach Neustart)
      if ((x == null || x.isEmpty()) && (z == null || z.isEmpty())) {
         com.google.gson.JsonObject st = ToolState.get("nether_coords");
         if (st.has("x")) {
            this.fields[0].setText(ToolState.getString(st, "x", ""));
            this.fields[1].setText(ToolState.getString(st, "y", ""));
            this.fields[2].setText(ToolState.getString(st, "z", ""));
            this.toNether = ToolState.getBool(st, "toNether", true);
            this.restored = true;
         }
      }
   }

   private boolean restored;

   @Override
   public void removed() {
      com.google.gson.JsonObject st = new com.google.gson.JsonObject();
      st.addProperty("x", this.fields[0].getText());
      st.addProperty("y", this.fields[1].getText());
      st.addProperty("z", this.fields[2].getText());
      st.addProperty("toNether", this.toNether);
      ToolState.put("nether_coords", st);
      super.removed();
   }

   @Override
   protected void init() {
      if (this.minecraft.level != null && !this.restored) {
         this.toNether = !NetherCoords.inNether(this.minecraft.level);
      }

      if (this.fields[0].getText().isEmpty() && this.minecraft.player != null) {
         this.fillHere();
      }
   }

   private void fillHere() {
      if (this.minecraft.player != null) {
         this.fields[0].setText(String.valueOf(this.minecraft.player.getBlockX()));
         this.fields[1].setText(String.valueOf(this.minecraft.player.getBlockY()));
         this.fields[2].setText(String.valueOf(this.minecraft.player.getBlockZ()));
         this.toNether = !NetherCoords.inNether(this.minecraft.level);
      }
   }

   private Double parse(int i) {
      String t = this.fields[i].getText().trim().replace(',', '.');
      if (!t.isEmpty() && !t.equals("~")) {
         try {
            return Double.parseDouble(t);
         } catch (NumberFormatException var4) {
            return Double.NaN;
         }
      } else {
         return null;
      }
   }

   private String output() {
      Double x = this.parse(0);
      Double y = this.parse(1);
      Double z = this.parse(2);
      if (x != null && z != null && !x.isNaN() && !z.isNaN() && (y == null || !y.isNaN())) {
         double[] r = NetherCoords.convert(x, z, this.toNether);
         return NetherCoords.fmt(r[0]) + " " + (y == null ? "~" : NetherCoords.fmt(y)) + " " + NetherCoords.fmt(r[1]);
      } else {
         return null;
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -1610612736);
      this.pw = Math.min(300, this.width - 16);
      this.ph = 176;
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
      Render2D.glow(context, this.px, this.py, this.pw, this.ph, 8, -2075077, 0.35F);
      Render2D.roundRect(context, this.px, this.py, this.pw, this.ph, 8, -15855591);
      Render2D.roundRect(context, this.px, this.py, this.pw, 26, 8, -15065296);
      context.drawString(this.font, Component.literal("Nether-Koordinaten").withStyle(ChatFormatting.BOLD), this.px + 10, this.py + 9, -1578509, false);
      int y = this.py + 34;
      boolean hov = Render2D.inside(mouseX, mouseY, this.px + 10, y, this.pw - 20, 22);
      Render2D.roundRect(context, this.px + 10, y, this.pw - 20, 22, 6, hov ? -14407358 : -15394521);
      ItemStack from = com.lennart.nebula.util.SafeStack.of(this.toNether ? Items.GRASS_BLOCK : Items.NETHERRACK);
      ItemStack to = com.lennart.nebula.util.SafeStack.of(this.toNether ? Items.NETHERRACK : Items.GRASS_BLOCK);
      context.renderItem(from, this.px + 16, y + 3);
      String dir = this.toNether ? "Overworld  →  Nether   (X/Z ÷ 8)" : "Nether  →  Overworld   (X/Z × 8)";
      context.drawString(this.font, dir, this.px + 38, y + 7, -1578509, false);
      context.renderItem(to, this.px + this.pw - 32, y + 3);
      y += 30;
      context.drawString(this.font, "Eingabe", this.px + 10, y, -3885571, false);
      y += 11;
      int fw = (this.pw - 20 - 8) / 3;

      for (int i = 0; i < 3; i++) {
         MeowlTextField f = this.fields[i];
         f.setBounds(this.px + 10 + i * (fw + 4), y, fw, 18);
         Double v = this.parse(i);
         f.render(context, this.font, v != null && v.isNaN());
      }

      y += 24;
      boolean hereHov = Render2D.inside(mouseX, mouseY, this.px + 10, y, 110, 16);
      Render2D.roundRect(context, this.px + 10, y, 110, 16, 5, hereHov ? -7575312 : -7643914);
      context.drawString(this.font, "Meine Position", this.px + 65 - this.font.width("Meine Position") / 2, y + 4, -1, false);
      y += 24;
      context.drawString(this.font, this.toNether ? "Im Nether" : "In der Overworld", this.px + 10, y, -3885571, false);
      y += 11;
      String out = this.output();
      Render2D.roundRect(context, this.px + 10, y, this.pw - 20, 22, 6, -15394521);
      if (out == null) {
         context.drawString(this.font, "X und Z eingeben (Zahlen)", this.px + 16, y + 7, -10656900, false);
      } else {
         context.drawString(this.font, Component.literal(out).withStyle(ChatFormatting.BOLD), this.px + 16, y + 7, -11870592, false);
         boolean cHov = Render2D.inside(mouseX, mouseY, this.px + this.pw - 74, y + 3, 60, 16);
         Render2D.roundRect(context, this.px + this.pw - 74, y + 3, 60, 16, 5, cHov ? -14407358 : -14012603);
         String label = out.equals(this.copied) ? "✔ kopiert" : "Kopieren";
         context.drawString(this.font, label, this.px + this.pw - 44 - this.font.width(label) / 2, y + 7, -1578509, false);
      }

      context.drawString(this.font, "Y bleibt gleich · Klick oben: Richtung tauschen", this.px + 10, this.py + this.ph - 12, -10656900, false);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();

      for (MeowlTextField f : this.fields) {
         if (f.mouseClicked(mx, my, this.font)) {
            for (MeowlTextField o : this.fields) {
               if (o != f) {
                  o.setFocused(false);
               }
            }

            return true;
         }
      }

      int y = this.py + 34;
      if (Render2D.inside(mx, my, this.px + 10, y, this.pw - 20, 22)) {
         this.toNether = !this.toNether;
         return true;
      } else {
         y += 65;
         if (Render2D.inside(mx, my, this.px + 10, y, 110, 16)) {
            this.fillHere();
            return true;
         } else {
            y += 35;
            String out = this.output();
            if (out != null && Render2D.inside(mx, my, this.px + this.pw - 74, y + 3, 60, 16)) {
               this.minecraft.keyboardHandler.setClipboard(out);
               this.copied = out;
               return true;
            } else {
               return super.mouseClicked(click, doubled);
            }
         }
      }
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      for (MeowlTextField f : this.fields) {
         if (f.isFocused()) {
            return f.charTyped(input.codepoint());
         }
      }

      return super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      for (int i = 0; i < this.fields.length; i++) {
         MeowlTextField f = this.fields[i];
         if (f.isFocused() && input.key() != InputConstants.KEY_ESCAPE) {
            if (input.key() == InputConstants.KEY_TAB) {
               f.setFocused(false);
               this.fields[(i + 1) % 3].setFocused(true);
               return true;
            }

            return f.keyPressed(input.key(), input.modifiers());
         }
      }

      return super.keyPressed(input);
   }
}
