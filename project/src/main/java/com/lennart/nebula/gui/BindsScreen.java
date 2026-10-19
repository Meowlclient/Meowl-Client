package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.mods.BindCommands;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.util.KeybindConflicts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class BindsScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int PANEL_W = 420;
   private static final int HEADER_H = 30;
   private static final int TAB_H = 22;
   private static final int ROW_H = 26;
   private static final int PAD = 12;
   private static final int KEY_W = 92;
   private final Screen parent;
   private int panelX;
   private int panelY;
   private int panelH;
   private boolean commandsTab;
   private double scroll;
   private Module listeningModule;
   private int listeningCommand = -1;
   private final List<Bind> cmdKeys = new ArrayList<>();
   private final List<MeowlTextField> cmdFields = new ArrayList<>();

   public BindsScreen(Screen parent) {
      super(Component.literal("Binds"));
      this.parent = parent;
   }

   private BindCommands bindCommands() {
      return NebulaClient.getInstance().getModuleManager().get(BindCommands.class);
   }

   @Override
   protected void init() {
      this.panelH = Math.max(220, Math.min(this.height - 20, 320));
      this.panelX = (this.width - 420) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      if (this.cmdFields.isEmpty()) {
         for (BindCommands.CommandBind cb : this.bindCommands().getBinds()) {
            this.addCommandRow(cb.bind(), cb.command());
         }
      }
   }

   private void addCommandRow(Bind bind, String command) {
      this.cmdKeys.add(bind);
      MeowlTextField field = new MeowlTextField(256).placeholder("/befehl oder Chat-Text").onChange(s -> this.saveCommands());
      field.setText(command);
      this.cmdFields.add(field);
   }

   private void saveCommands() {
      List<BindCommands.CommandBind> list = new ArrayList<>();

      for (int i = 0; i < this.cmdKeys.size(); i++) {
         list.add(new BindCommands.CommandBind(this.cmdKeys.get(i), this.cmdFields.get(i).getText()));
      }

      this.bindCommands().setBinds(list);
      NebulaClient.getInstance().getConfigManager().save();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.saveCommands();
      this.minecraft.gui.setScreen(this.parent);
   }

   private int listTop() {
      return this.panelY + 30 + 22 + 8;
   }

   private int listBottom() {
      return this.panelY + this.panelH - 20;
   }

   private int listX() {
      return this.panelX + 12;
   }

   private int listW() {
      return 396;
   }

   private List<Module> modules() {
      return NebulaClient.getInstance().getModuleManager().getModules();
   }

   private int rowCount() {
      return this.commandsTab ? this.cmdKeys.size() + 1 : this.modules().size();
   }

   private int rowY(int i) {
      return this.listTop() + i * 30 - (int)this.scroll;
   }

   private int keyButtonX() {
      return this.listX() + this.listW() - 92 - 8;
   }

   private void clampScroll() {
      int content = this.rowCount() * 30;
      this.scroll = Math.max(0.0, Math.min((double)Math.max(0, content - (this.listBottom() - this.listTop())), this.scroll));
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -1728053248);
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      Render2D.roundRect(context, this.panelX, this.panelY, 420, this.panelH, 6, -267250656);
      Render2D.roundRect(context, this.panelX, this.panelY, 420, 30, 6, -15197398);
      context.fill(this.panelX, this.panelY + 30 - 6, this.panelX + 420, this.panelY + 30, -15197398);
      context.fill(this.panelX, this.panelY + 30, this.panelX + 420, this.panelY + 30 + 1, -14012603);
      Component title = Component.literal("Binds").withStyle(ChatFormatting.BOLD);
      int tw = this.font.width(title);
      Render2D.roundRect(context, this.panelX + (420 - tw) / 2 - 10, this.panelY + 10, 4, 10, 2, -7643914);
      context.text(this.font, title, this.panelX + (420 - tw) / 2, this.panelY + 11, -1578509, true);
      this.drawTab(context, "Module", this.listX(), !this.commandsTab, mouseX, mouseY);
      this.drawTab(context, "Befehle", this.listX() + 88, this.commandsTab, mouseX, mouseY);
      this.clampScroll();
      context.enableScissor(this.panelX, this.listTop(), this.panelX + 420, this.listBottom());
      if (this.commandsTab) {
         this.renderCommands(context, mouseX, mouseY);
      } else {
         this.renderModules(context, mouseX, mouseY);
      }

      context.disableScissor();
      String hint = this.listeningModule == null && this.listeningCommand < 0
         ? "Klick auf eine Taste zum Ändern  ·  Gelb = Konflikt  ·  ESC: zurück"
         : "Taste drücken  ·  ESC: abbrechen  ·  Entf: keine Taste";
      context.text(this.font, hint, this.panelX + 10, this.panelY + this.panelH - 14, -10656900, false);
   }

   private void drawTab(GuiGraphicsExtractor ctx, String label, int x, boolean active, int mx, int my) {
      boolean hover = Render2D.inside(mx, my, x, this.panelY + 30 + 6, 80, 18);
      Render2D.roundRect(ctx, x, this.panelY + 30 + 6, 80, 18, 4, active ? 1082875126 : (hover ? -14407358 : -14933965));
      ctx.text(this.font, label, x + (80 - this.font.width(label)) / 2, this.panelY + 30 + 11, active ? -3885571 : -7564628, false);
   }

   private void renderModules(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      List<Module> list = this.modules();

      for (int i = 0; i < list.size(); i++) {
         Module module = list.get(i);
         int y = this.rowY(i);
         if (y + 26 >= this.listTop() && y <= this.listBottom()) {
            Render2D.roundRect(ctx, this.listX(), y, this.listW(), 26, 4, -14933965);
            ctx.text(this.font, module.getName(), this.listX() + 8, y + 4, -1578509, false);
            Bind bind = module.getKeybind().get();
            List<String> conflicts = KeybindConflicts.find(bind, module.getKeybind());
            String sub = conflicts.isEmpty() ? module.getCategory().getDisplayName() : "⚠ auch: " + String.join(", ", conflicts);
            ctx.text(
               this.font,
               this.font.plainSubstrByWidth(sub, this.listW() - 92 - 30),
               this.listX() + 8,
               y + 15,
               conflicts.isEmpty() ? -10656900 : -278748,
               false
            );
            this.drawKeyButton(ctx, y, bind, this.listeningModule == module, !conflicts.isEmpty(), mouseX, mouseY);
         }
      }
   }

   private void renderCommands(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      for (int i = 0; i < this.cmdKeys.size(); i++) {
         int y = this.rowY(i);
         Render2D.roundRect(ctx, this.listX(), y, this.listW(), 26, 4, -14933965);
         MeowlTextField field = this.cmdFields.get(i);
         field.setBounds(this.listX() + 26, y + 5, this.listW() - 92 - 44, 16);
         field.render(ctx, this.font, false);
         boolean delHover = Render2D.inside(mouseX, mouseY, this.listX() + 6, y + 6, 14, 14);
         Render2D.roundRect(ctx, this.listX() + 6, y + 6, 14, 14, 3, delHover ? -1096636 : -14012603);
         ctx.text(this.font, "✕", this.listX() + 9, y + 9, -1578509, false);
         Bind bind = this.cmdKeys.get(i);
         boolean conflict = !KeybindConflicts.find(bind, this.matchingBind(i)).isEmpty();
         this.drawKeyButton(ctx, y, bind, this.listeningCommand == i, conflict, mouseX, mouseY);
      }

      int y = this.rowY(this.cmdKeys.size());
      boolean hover = Render2D.inside(mouseX, mouseY, this.listX(), y, this.listW(), 26);
      Render2D.roundRect(ctx, this.listX(), y, this.listW(), 26, 4, hover ? 1082875126 : -14933965);
      String add = "+ Neuer Befehls-Bind";
      ctx.text(this.font, add, this.listX() + (this.listW() - this.font.width(add)) / 2, y + 9, -3885571, false);
   }

   private Object matchingBind(int index) {
      List<BindCommands.CommandBind> saved = this.bindCommands().getBinds();
      return index < saved.size() ? saved.get(index) : null;
   }

   private void drawKeyButton(GuiGraphicsExtractor ctx, int y, Bind bind, boolean listening, boolean conflict, int mx, int my) {
      int bx = this.keyButtonX();
      int by = y + 5;
      boolean hovered = Render2D.inside(mx, my, bx, by, 92, 16);
      Render2D.roundRect(ctx, bx, by, 92, 16, 3, listening ? 1082875126 : (hovered ? -13288878 : -14012603));
      String label = listening ? "Taste drücken…" : bind.getDisplayName();
      label = this.font.plainSubstrByWidth(label, 84);
      int color = listening ? -278748 : (conflict ? -278748 : (bind.isNone() ? -7564628 : -3885571));
      ctx.text(this.font, label, bx + (92 - this.font.width(label)) / 2, by + 4, color, false);
   }

   private boolean listening() {
      return this.listeningModule != null || this.listeningCommand >= 0;
   }

   private void assign(Bind bind) {
      if (this.listeningModule != null) {
         this.listeningModule.getKeybind().set(bind);
         NebulaClient.getInstance().getConfigManager().save();
      } else if (this.listeningCommand >= 0 && this.listeningCommand < this.cmdKeys.size()) {
         this.cmdKeys.set(this.listeningCommand, bind);
         this.saveCommands();
      }

      this.stopListening();
   }

   private void stopListening() {
      this.listeningModule = null;
      this.listeningCommand = -1;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      int button = click.button();
      if (this.listening()) {
         if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            this.assign(Bind.mouse(button));
         } else {
            this.stopListening();
         }

         return true;
      } else if (Render2D.inside(mx, my, this.listX(), this.panelY + 30 + 6, 80, 18)) {
         this.commandsTab = false;
         this.scroll = 0.0;
         return true;
      } else if (Render2D.inside(mx, my, this.listX() + 88, this.panelY + 30 + 6, 80, 18)) {
         this.commandsTab = true;
         this.scroll = 0.0;
         return true;
      } else if (!(my < this.listTop()) && !(my > this.listBottom()) && button == InputConstants.MOUSE_BUTTON_LEFT) {
         if (!this.commandsTab) {
            List<Module> list = this.modules();

            for (int i = 0; i < list.size(); i++) {
               if (Render2D.inside(mx, my, this.keyButtonX(), this.rowY(i) + 5, 92, 16)) {
                  this.listeningModule = list.get(i);
                  return true;
               }
            }

            return true;
         } else {
            boolean handled = false;

            for (int ix = 0; ix < this.cmdKeys.size(); ix++) {
               int y = this.rowY(ix);
               if (Render2D.inside(mx, my, this.listX() + 6, y + 6, 14, 14)) {
                  this.cmdKeys.remove(ix);
                  this.cmdFields.remove(ix);
                  this.saveCommands();
                  return true;
               }

               if (Render2D.inside(mx, my, this.keyButtonX(), y + 5, 92, 16)) {
                  this.listeningCommand = ix;
                  return true;
               }

               handled |= this.cmdFields.get(ix).mouseClicked(mx, my, this.font);
            }

            if (!handled && Render2D.inside(mx, my, this.listX(), this.rowY(this.cmdKeys.size()), this.listW(), 26)) {
               this.addCommandRow(Bind.NONE, "/");
               this.saveCommands();
               this.cmdFields.get(this.cmdFields.size() - 1).setFocused(true);
               return true;
            } else {
               return true;
            }
         }
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scroll -= verticalAmount * 18.0;
      this.clampScroll();
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      for (MeowlTextField field : this.cmdFields) {
         if (field.charTyped(input.codepoint())) {
            return true;
         }
      }

      return super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.listening()) {
         switch (input.key()) {
            case InputConstants.KEY_ESCAPE:
               this.stopListening();
               break;
            case InputConstants.KEY_BACKSPACE:
            case InputConstants.KEY_DELETE:
               this.assign(Bind.NONE);
               break;
            default:
               this.assign(Bind.keyboard(input.key()));
         }

         return true;
      } else {
         for (MeowlTextField field : this.cmdFields) {
            if (field.isFocused()) {
               field.keyPressed(input.key(), input.modifiers());
               return true;
            }
         }

         return super.keyPressed(input);
      }
   }
}
