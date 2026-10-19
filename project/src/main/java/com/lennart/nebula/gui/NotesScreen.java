package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.notes.Note;
import com.lennart.nebula.notes.NotesManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class NotesScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private int bookW = 470;
   private int listW = 168;
   private static final int ROW_H = 26;
   private static final int LINE = 11;
   private static final int COVER = -14016707;
   private static final int PAGE = -15065810;
   private static final int RULE = 808070981;
   private final Screen parent;
   private final NotesManager notes;
   private final MeowlTextField search = new MeowlTextField(64).placeholder("Suchen …");
   private final MeowlTextField title = new MeowlTextField(64).placeholder("Titel");
   private final MeowlTextField category = new MeowlTextField(32).placeholder("Kategorie");
   private MultiLineEditBox editor;
   private Note selected;
   private boolean favoritesOnly;
   private double listScroll;
   private long deleteArmedUntil;
   private int bookX;
   private int bookY;
   private int bookH;
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM. HH:mm");

   public NotesScreen(Screen parent, NotesManager notes) {
      super(Component.literal("Meowl Notes"));
      this.parent = parent;
      this.notes = notes;
      this.title.onChange(s -> {
         if (this.selected != null) {
            notes.rename(this.selected, s);
         }
      });
      this.category.onChange(s -> {
         if (this.selected != null) {
            notes.setCategory(this.selected, s);
         }
      });
   }

   @Override
   protected void init() {
      this.bookW = Math.min(470, this.width - 16);
      this.listW = Math.min(168, (int)(this.bookW * 0.36));
      this.bookH = Math.max(230, Math.min(this.height - 16, 300));
      this.bookX = (this.width - this.bookW) / 2;
      this.bookY = (this.height - this.bookH) / 2;
      int ex = this.pageX();
      int ey = this.bookY + 84;
      int ew = this.pageW() - 12;
      int eh = this.bookH - 84 - 14;
      this.editor = MultiLineEditBox.builder()
         .setX(ex)
         .setY(ey)
         .setPlaceholder(Component.literal("Hier schreiben …"))
         .setTextColor(-1578509)
         .setCursorColor(-3885571)
         .setTextShadow(false)
         .setShowBackground(false)
         .setShowDecorations(false)
         .build(this.font, ew, eh, Component.literal("Notiz"));
      this.editor.setCharacterLimit(20000);
      this.editor.setValueListener(s -> {
         if (this.selected != null) {
            this.notes.setContent(this.selected, s);
         }
      });
      this.addRenderableWidget(this.editor);
      if (this.selected == null) {
         List<Note> list = this.notes.list("", false);
         this.select(list.isEmpty() ? null : list.get(0));
      } else {
         this.select(this.selected);
      }
   }

   private void select(Note note) {
      this.selected = note;
      this.deleteArmedUntil = 0L;
      this.title.setText(note == null ? "" : note.title());
      this.category.setText(note == null ? "" : note.category());
      this.editor.setValue(note == null ? "" : note.content());
      this.editor.visible = note != null;
   }

   @Override
   public void tick() {
      this.notes.tick();
   }

   @Override
   public void removed() {
      this.notes.saveNow();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.notes.saveNow();
      this.minecraft.gui.setScreen(this.parent);
   }

   private int listX() {
      return this.bookX + 14;
   }

   private int pageX() {
      return this.bookX + 14 + this.listW + 22;
   }

   private int pageW() {
      return this.bookW - (this.pageX() - this.bookX) - 14;
   }

   private int listTop() {
      return this.bookY + 56;
   }

   private int listBottom() {
      return this.bookY + this.bookH - 38;
   }

   private int catW() {
      return Math.max(50, Math.min(90, this.pageW() - 170));
   }

   private List<Note> visibleNotes() {
      return this.notes.list(this.search.getText(), this.favoritesOnly);
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -1728053248);
      Render2D.roundRect(context, this.bookX - 4, this.bookY - 4, this.bookW + 8, this.bookH + 8, 8, -14016707);
      Render2D.roundRect(context, this.bookX - 4, this.bookY - 4, this.bookW + 8, 3, 2, -7643914);
      Render2D.roundRect(context, this.bookX + 6, this.bookY + 6, this.listW + 16, this.bookH - 12, 4, -15065810);
      Render2D.roundRect(context, this.pageX() - 8, this.bookY + 6, this.pageW() + 8, this.bookH - 12, 4, -15065810);
      int spine = this.bookX + 6 + this.listW + 16;
      context.fill(spine, this.bookY + 6, this.pageX() - 8, this.bookY + this.bookH - 6, -15593956);

      for (int y = this.bookY + 12; y < this.bookY + this.bookH - 12; y += 8) {
         context.fill(spine + 2, y, spine + 3, y + 4, 1623504381);
      }

      for (int y = this.bookY + 84 + 11 + 1; y < this.bookY + this.bookH - 12; y += 11) {
         context.fill(this.pageX() - 2, y, this.pageX() + this.pageW() - 6, y + 1, 808070981);
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      Component head = Component.literal("MEOWL NOTES").withStyle(ChatFormatting.BOLD);
      context.text(this.font, head, this.listX() + 2, this.bookY + 14, -3885571, true);
      this.search.setBounds(this.listX(), this.bookY + 30, this.listW - 22, 16);
      this.search.render(context, this.font, false);
      boolean favHover = Render2D.inside(mouseX, mouseY, this.listX() + this.listW - 18, this.bookY + 30, 18, 16);
      Render2D.roundRect(
         context, this.listX() + this.listW - 18, this.bookY + 30, 18, 16, 3, this.favoritesOnly ? 1082875126 : (favHover ? -14407358 : -14933965)
      );
      context.text(this.font, "★", this.listX() + this.listW - 13, this.bookY + 34, this.favoritesOnly ? -278748 : -7564628, false);
      List<Note> list = this.visibleNotes();
      this.clampListScroll(list.size());
      context.enableScissor(this.listX(), this.listTop(), this.listX() + this.listW, this.listBottom());
      int y = this.listTop() - (int)this.listScroll;

      for (Note note : list) {
         if (y + 26 >= this.listTop() && y <= this.listBottom()) {
            boolean sel = note == this.selected;
            boolean hover = Render2D.inside(mouseX, mouseY, this.listX(), y, this.listW, 24) && mouseY < this.listBottom();
            Render2D.roundRect(context, this.listX(), y, this.listW, 24, 3, sel ? 1082875126 : (hover ? -14407358 : 0));
            context.fill(this.listX(), y + 3, this.listX() + 2, y + 26 - 5, note.color());
            String marks = (note.pinned() ? "⚑ " : "") + (note.favorite() ? "★ " : "");
            int mw = this.font.width(marks);
            context.text(this.font, marks, this.listX() + 6, y + 3, -278748, false);
            context.text(
               this.font,
               this.font.plainSubstrByWidth(note.title(), this.listW - 12 - mw),
               this.listX() + 6 + mw,
               y + 3,
               sel ? -1578509 : -1578509,
               false
            );
            String sub = (note.category().isEmpty() ? "" : note.category() + " · ") + this.dateFormat.format(new Date(note.modified()));
            context.text(this.font, this.font.plainSubstrByWidth(sub, this.listW - 12), this.listX() + 6, y + 13, -10656900, false);
         }

         y += 26;
      }

      context.disableScissor();
      if (list.isEmpty()) {
         context.text(this.font, "Keine Notizen gefunden", this.listX() + 2, this.listTop() + 4, -7564628, false);
      }

      int ny = this.bookY + this.bookH - 32;
      boolean newHover = Render2D.inside(mouseX, mouseY, this.listX(), ny, this.listW, 18);
      Render2D.roundRect(context, this.listX(), ny, this.listW, 18, 4, newHover ? -7643914 : 1082875126);
      String add = "+ Neue Notiz";
      context.text(this.font, add, this.listX() + (this.listW - this.font.width(add)) / 2, ny + 5, -1578509, false);
      if (this.selected == null) {
         context.text(this.font, "Wähle links eine Notiz oder lege eine neue an.", this.pageX(), this.bookY + 40, -7564628, false);
      } else {
         this.title.setBounds(this.pageX() - 2, this.bookY + 14, this.pageW() - 8, 16);
         this.title.render(context, this.font, false);
         this.category.setBounds(this.pageX() - 2, this.bookY + 36, this.catW(), 15);
         this.category.render(context, this.font, false);
         int bx = this.pageX() + this.catW() + 4;
         bx = this.toolButton(context, bx, this.bookY + 36, this.selected.pinned() ? "⚑ Angepinnt" : "⚑ Pin", this.selected.pinned(), mouseX, mouseY);
         bx = this.toolButton(context, bx, this.bookY + 36, "★", this.selected.favorite(), mouseX, mouseY);
         int var22 = this.toolButton(context, bx, this.bookY + 36, "  ", false, mouseX, mouseY);
         context.fill(bx + 4, this.bookY + 40, bx + 14, this.bookY + 47, this.selected.color());
         boolean armed = System.currentTimeMillis() < this.deleteArmedUntil;
         this.toolButton(context, var22, this.bookY + 36, armed ? "Sicher?" : "Löschen", armed, mouseX, mouseY);
         context.fill(this.pageX() - 2, this.bookY + 58, this.pageX() + this.pageW() - 6, this.bookY + 59, -14012603);
         String info = this.editor.getValue().length() + " Zeichen · gespeichert automatisch";
         context.text(this.font, info, this.pageX(), this.bookY + 66, -10656900, false);
      }
   }

   private int toolButton(GuiGraphicsExtractor ctx, int x, int y, String label, boolean active, int mx, int my) {
      int w = this.font.width(label) + 10;
      boolean hover = Render2D.inside(mx, my, x, y, w, 15);
      Render2D.roundRect(ctx, x, y, w, 15, 3, active ? 1082875126 : (hover ? -14407358 : -14933965));
      ctx.text(this.font, label, x + 5, y + 4, active ? -3885571 : -7564628, false);
      return x + w + 4;
   }

   private void clampListScroll(int count) {
      int max = Math.max(0, count * 26 - (this.listBottom() - this.listTop()));
      this.listScroll = Math.max(0.0, Math.min((double)max, this.listScroll));
   }

   private void unfocusFields() {
      this.search.setFocused(false);
      this.title.setFocused(false);
      this.category.setFocused(false);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      this.unfocusFields();
      if (!this.search.mouseClicked(mx, my, this.font)
         && (this.selected == null || !this.title.mouseClicked(mx, my, this.font) && !this.category.mouseClicked(mx, my, this.font))) {
         if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (Render2D.inside(mx, my, this.listX() + this.listW - 18, this.bookY + 30, 18, 16)) {
               this.favoritesOnly = !this.favoritesOnly;
               return true;
            }

            if (Render2D.inside(mx, my, this.listX(), this.bookY + this.bookH - 32, this.listW, 18)) {
               Note note = this.notes.create();
               this.search.setText("");
               this.favoritesOnly = false;
               this.select(note);
               this.title.setFocused(true);
               return true;
            }

            if (Render2D.inside(mx, my, this.listX(), this.listTop(), this.listW, this.listBottom() - this.listTop())) {
               int i = (int)((my - this.listTop() + this.listScroll) / 26.0);
               List<Note> list = this.visibleNotes();
               if (i >= 0 && i < list.size()) {
                  this.select(list.get(i));
               }

               return true;
            }

            if (this.selected != null && my >= this.bookY + 36 && my < this.bookY + 51) {
               int bx = this.pageX() + this.catW() + 4;
               int[] widths = new int[]{
                  this.font.width(this.selected.pinned() ? "⚑ Angepinnt" : "⚑ Pin") + 10,
                  this.font.width("★") + 10,
                  this.font.width("  ") + 10,
                  this.font.width(System.currentTimeMillis() < this.deleteArmedUntil ? "Sicher?" : "Löschen") + 10
               };

               for (int b = 0; b < widths.length; b++) {
                  if (mx >= bx && mx < bx + widths[b]) {
                     this.toolAction(b);
                     return true;
                  }

                  bx += widths[b] + 4;
               }
            }
         }

         return super.mouseClicked(click, doubled);
      } else {
         this.setFocused(null);
         this.editor.setFocused(false);
         return true;
      }
   }

   private void toolAction(int button) {
      switch (button) {
         case 0:
            this.notes.togglePinned(this.selected);
            break;
         case 1:
            this.notes.toggleFavorite(this.selected);
            break;
         case 2:
            this.notes.cycleColor(this.selected);
            break;
         default:
            if (System.currentTimeMillis() < this.deleteArmedUntil) {
               this.notes.delete(this.selected);
               List<Note> list = this.visibleNotes();
               this.select(list.isEmpty() ? null : list.get(0));
            } else {
               this.deleteArmedUntil = System.currentTimeMillis() + 3000L;
            }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (mouseX < this.pageX() - 8) {
         this.listScroll -= verticalAmount * 26.0;
         this.clampListScroll(this.visibleNotes().size());
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      int cp = input.codepoint();
      return !this.search.charTyped(cp) && !this.title.charTyped(cp) && !this.category.charTyped(cp) ? super.charTyped(input) : true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      for (MeowlTextField field : new MeowlTextField[]{this.search, this.title, this.category}) {
         if (field.isFocused()) {
            field.keyPressed(input.key(), input.modifiers());
            return true;
         }
      }

      if (input.key() == InputConstants.KEY_ESCAPE && this.editor.isFocused()) {
         this.editor.setFocused(false);
         this.setFocused(null);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }
}
