package com.lennart.nebula.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.images.ImageCache;
import com.lennart.nebula.util.ChatUtil;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Private Nachrichten mit Meowl-Freunden (Text, Block Images, Screenshots) über den Meowl Server. */
public final class FriendChat {
   private static final FriendChat INSTANCE = new FriendChat();
   private final Map<String, List<FriendChat.Msg>> chats = new ConcurrentHashMap<>();
   private final Map<String, Integer> unread = new ConcurrentHashMap<>();
   private final ImageCache images = new ImageCache(FabricLoader.getInstance().getConfigDir().resolve("meowl-chat-cache"));
   private volatile String openWith;
   private Runnable listener = () -> {};
   private boolean hooked;

   private FriendChat() {
   }

   public static FriendChat get() {
      INSTANCE.hook();
      return INSTANCE;
   }

   private synchronized void hook() {
      if (!this.hooked) {
         this.hooked = true;
         MeowlService s = MeowlService.get();
         s.on("chat_msg", this::onMessage);
         s.onConnected(this::refreshUnread);
         ClientTickEvents.END_CLIENT_TICK.register(c -> this.images.tick());
      }
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   public ImageCache images() {
      return this.images;
   }

   public void setListener(Runnable r) {
      this.listener = r == null ? () -> {} : r;
   }

   /** Welcher Chat gerade offen ist (dort zählt nichts als ungelesen). */
   public void setOpen(String name) {
      this.openWith = name == null ? null : key(name);
      if (name != null) {
         this.unread.remove(key(name));
      }
   }

   public int unread(String name) {
      return this.unread.getOrDefault(key(name), 0);
   }

   public int unreadTotal() {
      int n = 0;

      for (int v : this.unread.values()) {
         n += v;
      }

      return n;
   }

   public List<FriendChat.Msg> messages(String name) {
      return this.chats.getOrDefault(key(name), List.of());
   }

   public boolean loaded(String name) {
      return this.chats.containsKey(key(name));
   }

   private static FriendChat.Msg parse(JsonObject o) {
      return new FriendChat.Msg(
         o.has("from") ? o.get("from").getAsString() : "?",
         o.has("text") ? o.get("text").getAsString() : null,
         o.has("image") ? o.get("image").getAsString() : null,
         o.has("time") ? o.get("time").getAsLong() : System.currentTimeMillis()
      );
   }

   private void onMessage(JsonObject o) {
      FriendChat.Msg m = parse(o);
      String k = key(m.from());
      List<FriendChat.Msg> list = this.chats.get(k);
      if (list != null) {
         list.add(m);
      }

      if (!k.equals(this.openWith)) {
         this.unread.merge(k, 1, Integer::sum);
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            String preview = m.text() != null ? (m.text().length() > 40 ? m.text().substring(0, 40) + "…" : m.text()) : "§o[Bild]";
            ChatUtil.chat(Component.literal("§d✉ §f" + m.from() + "§7: " + preview + " ").append(ChatUtil.button("[Öffnen]", 16744140, "/meowl msg " + m.from(), "Chat öffnen")));
            mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.5F);
         }
      }

      this.listener.run();
   }

   public void refreshUnread() {
      if (MeowlService.get().isConnected()) {
         MeowlService.get().get("/chat/unread").whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
            if (e == null && r.ok() && r.json() != null && r.json().has("unread")) {
               this.unread.clear();

               for (Map.Entry<String, JsonElement> en : r.json().getAsJsonObject("unread").entrySet()) {
                  if (!en.getKey().equals(this.openWith)) {
                     this.unread.put(en.getKey(), en.getValue().getAsInt());
                  }
               }

               this.listener.run();
            }
         }));
      }
   }

   public void loadHistory(String name, Consumer<String> onError) {
      if (!MeowlService.get().isConnected()) {
         onError.accept("Nicht mit dem Meowl Server verbunden");
         return;
      }

      MeowlService.get().get("/chat/history?with=" + name).whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null || !r.ok() || r.json() == null) {
            onError.accept(e != null ? e.getMessage() : (r.status() == 404 && r.error().startsWith("Unbekannter") ? "Meowl Server zu alt – bitte Server-Update einspielen" : r.error()));
         } else {
            List<FriendChat.Msg> list = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (JsonElement el : r.json().getAsJsonArray("messages")) {
               list.add(parse(el.getAsJsonObject()));
            }

            this.chats.put(key(name), list);
            this.unread.remove(key(name));
            this.listener.run();
         }
      }));
   }

   public void send(String to, String text, String image, Consumer<String> onError) {
      JsonObject o = new JsonObject();
      o.addProperty("to", to);
      if (text != null && !text.isBlank()) {
         o.addProperty("text", text.strip());
      }

      if (image != null) {
         o.addProperty("image", image);
      }

      MeowlService.get().post("/chat/send", o).whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null || !r.ok() || r.json() == null) {
            onError.accept(e != null ? e.getMessage() : r.error());
         } else {
            this.chats.computeIfAbsent(key(to), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(parse(r.json()));
            this.listener.run();
         }
      }));
   }

   /** Screenshot verkleinern (max. 1280 px), als JPEG hochladen und als Bild-Nachricht senden. */
   public void sendScreenshot(String to, Path file, Consumer<String> status) {
      MeowlService svc = MeowlService.get();
      if (!svc.isConnected() || svc.baseUrl() == null || !svc.baseUrl().startsWith("https://")) {
         status.accept("§cBilder senden braucht einen Meowl Server mit HTTPS-Adresse");
         return;
      }

      status.accept("Lade hoch …");
      CompletableFuture.supplyAsync(() -> {
         try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) {
               throw new IllegalStateException("Bild nicht lesbar");
            }

            double f = Math.min(1.0, 1280.0 / Math.max(src.getWidth(), src.getHeight()));
            int w = Math.max(1, (int)Math.round(src.getWidth() * f));
            int h = Math.max(1, (int)Math.round(src.getHeight() * f));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return jpeg(out, 0.85F);
         } catch (Exception var8) {
            throw new IllegalStateException(var8.getMessage(), var8);
         }
      }).thenCompose(bytes -> svc.request("POST", "/chat/image", null, bytes)).whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null || !r.ok() || r.json() == null || !r.json().has("file")) {
            String err = e != null ? (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()) : (r.status() == 404 ? "Meowl Server zu alt – bitte Server-Update einspielen" : r.error());
            status.accept("§cHochladen fehlgeschlagen: " + err);
         } else {
            status.accept("");
            this.send(to, null, svc.baseUrl() + "/chatimg/" + r.json().get("file").getAsString(), err -> status.accept("§c" + err));
         }
      }));
   }

   private static byte[] jpeg(BufferedImage img, float quality) throws Exception {
      Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
      ImageWriter writer = it.next();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
         writer.setOutput(out);
         ImageWriteParam p = writer.getDefaultWriteParam();
         p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         p.setCompressionQuality(quality);
         writer.write(null, new IIOImage(img, null, null), p);
      } finally {
         writer.dispose();
      }

      return bytes.toByteArray();
   }

   /** Screenshots aus dem Minecraft-Ordner, neueste zuerst. */
   public static List<Path> screenshots() {
      Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
      List<Path> out = new ArrayList<>();

      try (java.util.stream.Stream<Path> s = java.nio.file.Files.list(dir)) {
         s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".png"))
            .sorted(java.util.Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
            .limit(24L)
            .forEach(out::add);
      } catch (Exception var6) {
         NebulaClient.LOGGER.debug("Keine Screenshots: {}", var6.toString());
      }

      return out;
   }

   public record Msg(String from, String text, String image, long time) {
   }
}
