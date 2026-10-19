package com.lennart.nebula.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Steuerung: Suchfeld über der Tastenliste – findet nach Name der Aktion, Kategorie oder belegter Taste. */
@Mixin({KeyBindsScreen.class})
public abstract class KeyBindsScreenMixin extends OptionsSubScreen {
   @Shadow
   private KeyBindsList keyBindsList;
   @Unique
   private String meowl$query = "";
   @Unique
   private List<KeyBindsList.Entry> meowl$all;

   protected KeyBindsScreenMixin(Screen parent, Options options, Component title) {
      super(parent, options, title);
   }

   @Inject(method = {"addContents"}, at = {@At("TAIL")})
   private void meowl$addSearch(CallbackInfo ci) {
      this.meowl$all = new ArrayList<>(this.keyBindsList.children());
      EditBox box = new EditBox(this.font, 0, 0, 220, 18, Component.literal("Suche"));
      box.setHint(Component.literal("Suchen: Aktion oder Taste …").withStyle(ChatFormatting.DARK_GRAY));
      box.setMaxLength(40);
      box.setValue(this.meowl$query);
      box.setResponder(q -> {
         this.meowl$query = q;
         this.meowl$filter();
      });
      this.layout.setHeaderHeight(56);
      this.layout.addToHeader(box, s -> s.alignVerticallyBottom().paddingBottom(5));
      this.meowl$filter();
   }

   @Unique
   private void meowl$filter() {
      if (this.keyBindsList == null || this.meowl$all == null) {
         return;
      }

      String q = this.meowl$query.trim().toLowerCase(Locale.ROOT);
      if (q.isEmpty()) {
         this.keyBindsList.replaceEntries(this.meowl$all);
         this.keyBindsList.refreshEntries();
         return;
      }

      // "taste:x" / "key:x" sucht nur nach der Belegung
      boolean keyOnly = false;
      for (String p : new String[]{"taste:", "key:"}) {
         if (q.startsWith(p)) {
            q = q.substring(p.length()).trim();
            keyOnly = true;
         }
      }

      List<KeyBindsList.Entry> out = new ArrayList<>();
      KeyBindsList.Entry category = null;
      boolean categoryMatch = false;
      boolean categoryAdded = false;

      for (KeyBindsList.Entry e : this.meowl$all) {
         if (e instanceof CategoryEntryAccessor c) {
            category = e;
            categoryAdded = false;
            categoryMatch = !keyOnly && c.meowl$categoryName().getMessage().getString().toLowerCase(Locale.ROOT).contains(q);
            continue;
         }

         if (e instanceof KeyEntryAccessor k) {
            KeyMapping key = k.meowl$key();
            String name = k.meowl$name().getString().toLowerCase(Locale.ROOT);
            String bound = key.getTranslatedKeyMessage().getString().toLowerCase(Locale.ROOT);
            boolean match = categoryMatch
               || !keyOnly && name.contains(q)
               || bound.equals(q)
               || (q.length() >= 2 || keyOnly) && bound.contains(q);
            if (match) {
               if (category != null && !categoryAdded) {
                  out.add(category);
                  categoryAdded = true;
               }

               out.add(e);
            }
         }
      }

      this.keyBindsList.replaceEntries(out);
      this.keyBindsList.setScrollAmount(0.0);
      this.keyBindsList.refreshEntries();
   }
}