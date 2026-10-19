package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.mods.ProfilePicture;
import com.lennart.nebula.nametag.NametagIcons;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Profilbild wählen: Datei, Link oder eigener Skin-Kopf – wird auf 16 x 16 verkleinert (wie das Meowl-Zeichen). */
public class ProfilePictureScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private final Screen parent;
   private final ProfilePicture module;
   private final MeowlTextField input = new MeowlTextField(512).placeholder("Dateipfad oder https-Link zu einem Bild …");
   private final List<int[]> rects = new ArrayList<>();
   private final List<Runnable> clicks = new ArrayList<>();
   private String note = "";
   private String pending;

   public ProfilePictureScreen(Screen parent, ProfilePicture module) {
      super(Component.literal("Profilbild"));
      this.parent = parent;
      this.module = module;
      this.input.onSubmit(s -> this.load(s));
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, String text, int color, int mouseX, int mouseY, Runnable action) {
      boolean hot = Render2D.inside(mouseX, mouseY, x, y, w, 16);
      Render2D.roundRect(ctx, x, y, w, 16, 4, hot ? 0xE08B5CF6 : color);
      ctx.centeredText(this.font, text, x + w / 2, y + 4, -1);
      this.rects.add(new int[]{x, y, w, 16});
      this.clicks.add(action);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.rects.clear();
      this.clicks.clear();
      if (this.pending != null) {
         this.module.setEncoded(this.pending);
         this.module.setEnabled(true);
         this.pending = null;
      }

      ctx.fill(0, 0, this.width, this.height, 0xB005060C);
      int pw = Math.min(360, this.width - 20);
      int ph = Math.min(250, this.height - 20);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.roundRect(ctx, px, py, pw, ph, 8, 0xF0101420);
      ctx.text(this.font, Component.literal("Profilbild").withStyle(ChatFormatting.BOLD), px + 10, py + 8, -1, true);
      ctx.text(this.font, "Ersetzt das Meowl-Zeichen neben deinem Namen (16 x 16 Pixel)", px + 10, py + 20, -7564628, false);
      // Vorschau groß und in Originalgröße am Namen
      String b64 = this.module.encoded();
      Identifier tex = b64.isEmpty() ? null : ProfilePicture.texture(b64);
      int big = 80;
      int bx = px + 14;
      int by = py + 38;
      Render2D.roundRect(ctx, bx - 4, by - 4, big + 8, big + 8, 6, 0xC01C2234);
      if (tex != null) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, tex, bx, by, 0.0F, 0.0F, big, big, 16, 16, 16, 16);
      } else {
         NametagIcons.gui(ctx, NametagIcons.meowl(), bx, by, big);
      }

      int nx = bx + big + 20;
      int ny = by + 10;
      ctx.text(this.font, "So sieht es am Namen aus:", nx, ny - 2, -3885571, false);
      String name = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "Spieler";
      int tw = this.font.width(name) + 14;
      ctx.fill(nx, ny + 10, nx + tw + 4, ny + 22, 0x60000000);
      if (tex != null) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, tex, nx + 2, ny + 12, 0.0F, 0.0F, 8, 8, 16, 16, 16, 16);
      } else {
         NametagIcons.gui(ctx, NametagIcons.meowl(), nx + 2, ny + 12, 8);
      }

      ctx.text(this.font, name, nx + 13, ny + 12, -1, false);
      ctx.text(this.font, b64.isEmpty() ? "Kein eigenes Bild – Meowl-Zeichen" : (this.module.isEnabled() ? "Aktiv" : "Gespeichert (Modul aus)"), nx, ny + 30, b64.isEmpty() ? -7564628 : 0xFF5BE38A, false);
      int ry = py + 38 + big + 16;
      this.input.setBounds(px + 10, ry, pw - 70, 16);
      this.input.render(ctx, this.font, false);
      this.button(ctx, px + pw - 56, ry, 46, "Laden", 0xE0262E46, mouseX, mouseY, () -> this.load(this.input.getText()));
      ry += 22;
      int bw = (pw - 30) / 3;
      this.button(ctx, px + 10, ry, bw, "Datei wählen …", 0xE0262E46, mouseX, mouseY, this::pickFile);
      this.button(ctx, px + 15 + bw, ry, bw, "Mein Skin-Kopf", 0xE0262E46, mouseX, mouseY, this::skinHead);
      this.button(ctx, px + 20 + 2 * bw, ry, bw, "Entfernen", 0xE06A3030, mouseX, mouseY, () -> {
         this.module.setEncoded("");
         this.note = "Profilbild entfernt – wieder Meowl-Zeichen";
      });
      ry += 24;
      ctx.text(this.font, this.note, px + 10, ry, this.note.startsWith("§c") ? 0xFFFF6060 : -7564628, false);
      this.button(ctx, px + pw - 70, py + ph - 24, 60, "Fertig", 0xE0306A40, mouseX, mouseY, this::onClose);
   }

   private void accept(BufferedImage img) {
      try {
         BufferedImage s = ProfilePicture.scale(img);
         this.pending = ProfilePicture.encode(s);
         this.note = "Bild übernommen (16 x 16)";
      } catch (Exception var3) {
         this.note = "§cBild konnte nicht verarbeitet werden";
      }
   }

   private void load(String source) {
      String s = source == null ? "" : source.trim().replace("\"", "");
      if (s.isEmpty()) {
         this.note = "§cErst einen Pfad oder Link eingeben";
         return;
      }

      this.note = "Lade …";
      CompletableFuture.runAsync(() -> {
         try {
            BufferedImage img;
            if (s.startsWith("https://")) {
               HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(HttpClient.Redirect.NORMAL).build();
               HttpResponse<byte[]> r = http.send(
                  HttpRequest.newBuilder(URI.create(s)).timeout(Duration.ofSeconds(12L)).header("User-Agent", "Meowl-Client").build(),
                  HttpResponse.BodyHandlers.ofByteArray()
               );
               if (r.body().length > 8000000) {
                  throw new IllegalStateException("zu groß");
               }

               img = ImageIO.read(new ByteArrayInputStream(r.body()));
            } else {
               Path p = Path.of(s);
               if (Files.size(p) > 16000000L) {
                  throw new IllegalStateException("zu groß");
               }

               img = ImageIO.read(p.toFile());
            }

            if (img == null) {
               this.note = "§cKein lesbares Bild (PNG, JPG, GIF, BMP)";
            } else {
               this.accept(img);
            }
         } catch (Exception var5) {
            this.note = "§cKonnte nicht geladen werden: " + var5.getMessage();
         }
      });
   }

   private void pickFile() {
      CompletableFuture.runAsync(() -> {
         try {
            String path = openDialog();
            if (path != null) {
               this.minecraft.execute(() -> {
                  this.input.setText(path);
                  this.load(path);
               });
            }
         } catch (Throwable var2) {
            this.note = "§cDateiauswahl nicht verfügbar – Pfad oben eintragen";
         }
      });
   }

   /** Datei-Dialog: LWJGL-TinyFD (falls vorhanden), sonst unter Windows der normale Öffnen-Dialog. */
   private static String openDialog() throws Exception {
      try {
         Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
         java.lang.reflect.Method m = c.getMethod(
            "tinyfd_openFileDialog", CharSequence.class, CharSequence.class, org.lwjgl.PointerBuffer.class, CharSequence.class, boolean.class
         );
         return (String)m.invoke(null, "Profilbild wählen", "", null, "Bilder", false);
      } catch (ClassNotFoundException | NoSuchMethodException var4) {
         if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            throw var4;
         }

         Process p = new ProcessBuilder(
               "powershell",
               "-NoProfile",
               "-STA",
               "-Command",
               "Add-Type -AssemblyName System.Windows.Forms; $d=New-Object System.Windows.Forms.OpenFileDialog; $d.Filter='Bilder|*.png;*.jpg;*.jpeg;*.gif;*.bmp'; if($d.ShowDialog() -eq 'OK'){ $d.FileName }"
            )
            .redirectErrorStream(true)
            .start();
         String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
         p.waitFor();
         return out.isEmpty() ? null : out;
      }
   }

   /** Gesicht (mit Hut-Ebene) aus deinem Skin. */
   private void skinHead() {
      if (this.minecraft.player == null) {
         this.note = "§cNur in einer Welt möglich";
         return;
      }

      java.util.UUID uuid = this.minecraft.player.getUUID();
      this.note = "Lade Skin …";
      CompletableFuture.runAsync(() -> {
         try {
            var full = this.minecraft.services().sessionService().fetchProfile(uuid, false);
            if (full == null) {
               throw new IllegalStateException("kein Profil");
            }

            String url = null;

            for (var prop : full.profile().properties().get("textures")) {
               String json = new String(Base64.getDecoder().decode(prop.value()), java.nio.charset.StandardCharsets.UTF_8);
               var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
               url = root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            }

            if (url == null) {
               throw new IllegalStateException("kein Skin");
            }

            HttpResponse<byte[]> r = HttpClient.newHttpClient()
               .send(HttpRequest.newBuilder(URI.create(url.replace("http://", "https://"))).build(), HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(r.body()));
            BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = face.createGraphics();
            g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
            g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
            g.dispose();
            this.accept(face);
         } catch (Exception var9) {
            this.note = "§cSkin nicht ladbar: " + var9.getMessage();
         }
      });
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.input.mouseClicked(click.x(), click.y(), this.font)) {
         return true;
      } else {
         if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = this.rects.size() - 1; i >= 0; i--) {
               int[] r = this.rects.get(i);
               if (Render2D.inside(click.x(), click.y(), r[0], r[1], r[2], r[3])) {
                  this.clicks.get(i).run();
                  return true;
               }
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      if (!this.input.isFocused() && input.codepoint() > 32) {
         this.input.setFocused(true);
      }

      return this.input.charTyped(input.codepoint()) || super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.input.isFocused() && input.key() != InputConstants.KEY_ESCAPE && this.input.keyPressed(input.key(), input.modifiers())) {
         return true;
      } else {
         return super.keyPressed(input);
      }
   }
}
