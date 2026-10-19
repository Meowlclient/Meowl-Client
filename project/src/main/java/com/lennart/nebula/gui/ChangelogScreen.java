package com.lennart.nebula.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.mods.MeowlServerModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Changelogs: was in den letzten Versionen neu ist (vom Meowl Server), die installierte Version ist markiert. */
public class ChangelogScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
   private final Screen parent;
   private final String installed;
   private volatile List<String[]> notes;
   private volatile String status = "Lade Changelogs …";
   private float scroll;
   private int contentH;

   public ChangelogScreen(Screen parent) {
      super(Component.literal("Changelog"));
      this.parent = parent;
      this.installed = FabricLoader.getInstance().getModContainer("nebula").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
      this.load();
   }

   private void load() {
      MeowlServerModule server = NebulaClient.getInstance().getModuleManager().get(MeowlServerModule.class);
      String base = server == null ? null : server.targetUrl();
      if (base == null || base.isEmpty()) {
         this.status = "Kein Meowl Server eingestellt";
         return;
      }

      String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
      String mc = FabricLoader.getInstance().getModContainer("minecraft").map(m -> "?mc=" + m.getMetadata().getVersion().getFriendlyString().replaceAll("[^0-9.]", "")).orElse("");
      HttpRequest req = HttpRequest.newBuilder(URI.create(root + "/update/notes" + mc)).timeout(Duration.ofSeconds(10L)).header("User-Agent", "MeowlClient").GET().build();
      HTTP.sendAsync(req, BodyHandlers.ofString()).whenComplete((r, e) -> {
         if (e != null || r.statusCode() != 200) {
            this.status = r != null && r.statusCode() == 404 ? "Der Server kennt noch keine Changelogs (Server-Update nötig)" : "Server nicht erreichbar";
            return;
         }

         try {
            JsonArray arr = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("notes");
            List<String[]> out = new ArrayList<>();

            for (JsonElement el : arr) {
               JsonObject o = el.getAsJsonObject();
               out.add(new String[]{o.get("version").getAsString(), o.get("changelog").getAsString()});
            }

            this.notes = out;
            this.status = out.isEmpty() ? "Noch keine Changelogs veröffentlicht" : "";
         } catch (RuntimeException var5) {
            this.status = "Antwort nicht lesbar";
         }
      });
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(0, 0, this.width, this.height, 0xC005060C);
      int pw = Math.min(360, this.width - 30);
      int px = (this.width - pw) / 2;
      int top = 34;
      int bottom = this.height - 30;
      Render2D.roundRect(ctx, px - 8, top - 26, pw + 16, bottom - top + 50, 8, 0xE0101420);
      ctx.text(this.font, Component.literal("Changelog").withStyle(ChatFormatting.BOLD), px, top - 18, -1, true);
      String inst = "Installiert: " + this.installed;
      ctx.text(this.font, inst, px + pw - this.font.width(inst), top - 18, -7564628, false);
      List<String[]> list = this.notes;
      if (list == null || list.isEmpty()) {
         ctx.centeredText(this.font, this.status, this.width / 2, top + 20, -7564628);
      } else {
         ctx.enableScissor(px - 4, top, px + pw + 4, bottom);
         int y = top + 4 - Math.round(this.scroll);

         for (String[] n : list) {
            boolean current = n[0].equals(this.installed);
            boolean newer = !current && com.lennart.nebula.update.Version.parse(n[0]) != null
               && com.lennart.nebula.update.Version.parse(this.installed) != null
               && com.lennart.nebula.update.Version.parse(n[0]).isNewerThan(com.lennart.nebula.update.Version.parse(this.installed));
            int headH = 14;
            Render2D.roundRect(ctx, px, y, pw, headH, 4, current ? 0xC08B5CF6 : (newer ? 0xC0306A40 : 0xC01C2234));
            String head = "Version " + n[0] + (current ? "  · installiert" : (newer ? "  · neu" : ""));
            ctx.text(this.font, Component.literal(head).withStyle(ChatFormatting.BOLD), px + 6, y + 3, -1, false);
            y += headH + 4;

            for (String line : n[1].split("\n")) {
               if (line.isBlank() || line.startsWith("Neu in ")) {
                  continue;
               }

               String text = line.startsWith("- ") ? "• " + line.substring(2) : line;
               for (FormattedCharSequence part : this.font.split(Component.literal(text), pw - 12)) {
                  ctx.text(this.font, part, px + 6, y, -1578509, false);
                  y += 10;
               }
            }

            y += 8;
         }

         ctx.disableScissor();
         this.contentH = y + Math.round(this.scroll) - top;
      }

      boolean hot = Render2D.inside(mouseX, mouseY, this.width / 2 - 40, this.height - 24, 80, 16);
      Render2D.roundRect(ctx, this.width / 2 - 40, this.height - 24, 80, 16, 5, hot ? 0xE08B5CF6 : 0xE0262E46);
      ctx.centeredText(this.font, "Zurück", this.width / 2, this.height - 20, -1);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      float max = Math.max(0, this.contentH - (this.height - 64));
      this.scroll = Math.max(0.0F, Math.min(max, this.scroll - (float)v * 20.0F));
      return true;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (Render2D.inside(click.x(), click.y(), this.width / 2 - 40, this.height - 24, 80, 16)) {
         this.onClose();
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
