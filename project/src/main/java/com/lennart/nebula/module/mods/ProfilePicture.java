package com.lennart.nebula.module.mods;

import com.google.gson.JsonObject;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.DynamicTextures;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Profilbild: ein eigenes kleines Bild (16 x 16, genau wie das Meowl-Zeichen) erscheint statt des Meowl-Zeichens neben
 * deinem Namen – auch bei allen anderen Meowl-Spielern.
 */
public class ProfilePicture extends Module {
   /** Gleiche Auflösung wie das Meowl-Zeichen im Nametag. */
   public static final int SIZE = 16;
   private static ProfilePicture instance;
   private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
   final TextSetting data = this.add(new TextSetting("Bilddaten", "", "", 4096));
   private final BooleanSetting share = this.add(new BooleanSetting("Für andere sichtbar", "Andere Meowl-Spieler sehen dein Profilbild", true));
   private final BooleanSetting showOthers = this.add(new BooleanSetting("Profilbilder anderer zeigen", "Profilbilder anderer Meowl-Spieler anzeigen", true));

   public ProfilePicture() {
      super("Profilbild", "Eigenes kleines Profilbild statt des Meowl-Zeichens neben deinem Namen – im gleichen Format (16 x 16).", Category.PLAYER);
      this.setEnabled(true);
      this.section(Section.COSMETICS);
      this.icon(Items.PLAYER_HEAD);
      instance = this;
      this.data.setVisibility(() -> false);
      ActionSetting open = this.add(new ActionSetting("Bild wählen", "Bild aus Datei, Link oder deinem Skin-Kopf", "Öffnen", this::openMenu));
      this.group("Profilbild", new Setting[]{open, this.share, this.showOthers});
   }

   public static ProfilePicture get() {
      return instance;
   }

   private void openMenu() {
      this.mc.schedule(() -> this.mc.gui.setScreen(new com.lennart.nebula.gui.ProfilePictureScreen(this.mc.gui.screen(), this)));
   }

   public String encoded() {
      return this.data.get();
   }

   public void setEncoded(String b64) {
      this.data.set(b64 == null ? "" : b64);
   }

   public static void presenceFields(JsonObject o) {
      ProfilePicture p = instance;
      if (p != null && p.isEnabled() && p.share.get() && !p.data.get().isEmpty()) {
         o.addProperty("pfp", p.data.get());
      }
   }

   /** Textur für das Zeichen neben dem Namen dieses Spielers (null = normales Meowl-Zeichen). */
   public static Identifier iconFor(Player player) {
      ProfilePicture p = instance;
      if (p == null) {
         return null;
      }

      String b64;
      if (player == p.mc.player) {
         b64 = p.isEnabled() ? p.data.get() : "";
      } else {
         if (!p.showOthers.get()) {
            return null;
         }

         com.lennart.nebula.util.MeowlNetwork.Peer peer = MeowlPet.peer(player);
         b64 = peer == null ? null : peer.pfp();
      }

      return b64 == null || b64.isEmpty() ? null : texture(b64);
   }

   /** Base64-PNG als Textur hochladen (je Bild nur einmal). */
   public static Identifier texture(String b64) {
      Identifier cached = TEXTURES.get(b64);
      if (cached != null) {
         return cached;
      }

      int[] px = decode(b64);
      if (px == null) {
         return null;
      }

      if (TEXTURES.size() > 128) {
         TEXTURES.values().forEach(DynamicTextures::destroy);
         TEXTURES.clear();
      }

      Identifier id = Identifier.fromNamespaceAndPath("nebula", "pfp/" + UUID.nameUUIDFromBytes(b64.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      DynamicTextures.upload(id, null, px, SIZE, SIZE);
      TEXTURES.put(b64, id);
      return id;
   }

   /** Pixel (ARGB, 16 x 16) aus Base64-PNG, oder null. */
   public static int[] decode(String b64) {
      try {
         BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
         if (img == null) {
            return null;
         }

         BufferedImage s = scale(img);
         int[] px = new int[SIZE * SIZE];
         s.getRGB(0, 0, SIZE, SIZE, px, 0, SIZE);
         return px;
      } catch (Exception var3) {
         return null;
      }
   }

   /** Mitte quadratisch zuschneiden und auf 16 x 16 verkleinern (weich gemittelt). */
   public static BufferedImage scale(BufferedImage src) {
      int side = Math.min(src.getWidth(), src.getHeight());
      int ox = (src.getWidth() - side) / 2;
      int oy = (src.getHeight() - side) / 2;
      BufferedImage cur = src.getSubimage(ox, oy, side, side);

      // schrittweise halbieren, damit nichts flimmert
      while (cur.getWidth() > SIZE * 2) {
         int n = Math.max(SIZE, cur.getWidth() / 2);
         BufferedImage half = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = half.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
         g.drawImage(cur, 0, 0, n, n, null);
         g.dispose();
         cur = half;
      }

      BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, cur.getWidth() <= SIZE ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(cur, 0, 0, SIZE, SIZE, null);
      g.dispose();
      return out;
   }

   /** 16 x 16 Pixel als Base64-PNG. */
   public static String encode(BufferedImage img16) throws java.io.IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img16, "png", out);
      return Base64.getEncoder().encodeToString(out.toByteArray());
   }
}
