package com.lennart.nebula.images;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.util.DynamicTextures;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class ImageCache {
   private static final int MAX_BYTES = 8388608;
   private static final int MAX_DIM = 4096;
   private static final int TEX_MAX = 512;
   private static final int MAX_TEXTURES = 48;
   private static final long UNUSED_MS = 60000L;
   private final Path cacheDir;
   private final Map<String, ImageCache.Entry> entries = new ConcurrentHashMap<>();
   private ExecutorService loader;
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NEVER).build();

   public ImageCache(Path cacheDir) {
      this.cacheDir = cacheDir;
   }

   public ImageCache.Entry get(String url) {
      ImageCache.Entry e = this.entries.computeIfAbsent(url, u -> {
         ImageCache.Entry n = new ImageCache.Entry();
         this.executor().submit(() -> this.load(u, n));
         return n;
      });
      e.lastUsed = System.currentTimeMillis();
      if (e.state == ImageCache.State.READY && e.texture == null && e.pixels != null) {
         this.upload(url, e);
      }

      return e;
   }

   private synchronized ExecutorService executor() {
      if (this.loader == null) {
         this.loader = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Meowl-Images");
            t.setDaemon(true);
            return t;
         });
      }

      return this.loader;
   }

   private void upload(String url, ImageCache.Entry e) {
      e.texture = Identifier.fromNamespaceAndPath("nebula", "image/" + hash(url).substring(0, 24));
      e.tex = DynamicTextures.upload(e.texture, null, e.pixels, e.width, e.height);
      e.pixels = null;
   }

   private void load(String url, ImageCache.Entry e) {
      try {
         Path cached = this.cacheDir.resolve(hash(url) + ".png");
         BufferedImage image = null;
         if (Files.exists(cached)) {
            try {
               image = decode(Files.readAllBytes(cached));
            } catch (IOException | RuntimeException var8) {
               Files.deleteIfExists(cached);
            }
         }

         if (image == null) {
            image = decode(this.download(URI.create(url)));
            Files.createDirectories(this.cacheDir);
            Path tmp = this.cacheDir.resolve(hash(url) + ".tmp");
            ImageIO.write(image, "png", tmp.toFile());
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         }

         int w = image.getWidth();
         int h = image.getHeight();
         int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
         e.width = w;
         e.height = h;
         e.pixels = argb;
         e.state = ImageCache.State.READY;
      } catch (Exception var91) {
         e.error = var91.getMessage() == null ? var91.getClass().getSimpleName() : var91.getMessage();
         e.state = ImageCache.State.FAILED;
         NebulaClient.LOGGER.warn("Bild {} konnte nicht geladen werden: {}", url, e.error);
      }
   }

   private byte[] download(URI uri) throws IOException, InterruptedException {
      URI current = uri;

      for (int hop = 0; hop < 4; hop++) {
         UrlSafety.checkPublic(current);
         HttpRequest req = HttpRequest.newBuilder(current)
            .timeout(Duration.ofSeconds(15L))
            .header("User-Agent", "MeowlClient")
            .header("Accept", "image/png,image/jpeg,image/gif,image/bmp;q=0.9,*/*;q=0.1")
            .GET()
            .build();
         HttpResponse<InputStream> r = this.http.send(req, BodyHandlers.ofInputStream());
         int code = r.statusCode();
         if (code < 300 || code >= 400) {
            byte[] var11;
            try (InputStream in = r.body()) {
               if (code != 200) {
                  throw new IOException("HTTP " + code);
               }

               long length = r.headers().firstValueAsLong("Content-Length").orElse(-1L);
               if (length > 8388608L) {
                  throw new IOException("Bild zu groß (max. 8 MB)");
               }

               byte[] data = in.readNBytes(8388609);
               if (data.length > 8388608) {
                  throw new IOException("Bild zu groß (max. 8 MB)");
               }

               var11 = data;
            }

            return var11;
         }

         r.body().close();
         String location = r.headers().firstValue("Location").orElseThrow(() -> new IOException("Weiterleitung ohne Ziel"));
         current = current.resolve(location);
      }

      throw new IOException("Zu viele Weiterleitungen");
   }

   private static BufferedImage decode(byte[] data) throws IOException {
      BufferedImage var9;
      try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
         Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
         if (!readers.hasNext()) {
            throw new IOException("Format nicht unterstützt (PNG, JPEG, GIF oder BMP)");
         }

         ImageReader reader = readers.next();

         try {
            reader.setInput(in, true, true);
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096) {
               throw new IOException("Auflösung zu groß (max. 4096×4096)");
            }

            ImageReadParam param = reader.getDefaultReadParam();
            int sub = Math.max(1, Math.max(w, h) / 1024);
            param.setSourceSubsampling(sub, sub, 0, 0);
            BufferedImage raw = reader.read(0, param);
            var9 = scale(raw);
         } finally {
            reader.dispose();
         }
      }

      return var9;
   }

   private static BufferedImage scale(BufferedImage src) {
      int w = src.getWidth();
      int h = src.getHeight();
      double f = Math.min(1.0, 512.0 / Math.max(w, h));
      int nw = Math.max(1, (int)Math.round(w * f));
      int nh = Math.max(1, (int)Math.round(h * f));
      BufferedImage out = new BufferedImage(nw, nh, 2);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(src, 0, 0, nw, nh, null);
      g.dispose();
      return out;
   }

   private static String hash(String s) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception var2) {
         return Integer.toHexString(s.hashCode());
      }
   }

   public void tick() {
      long now = System.currentTimeMillis();
      this.entries.entrySet().removeIf(en -> {
         ImageCache.Entry ex = en.getValue();
         if (ex.state != ImageCache.State.LOADING && now - ex.lastUsed > 60000L) {
            if (ex.texture != null) {
               DynamicTextures.destroy(ex.texture);
            }

            return true;
         } else {
            return false;
         }
      });
      List<Map.Entry<String, ImageCache.Entry>> loaded = new ArrayList<>();

      for (Map.Entry<String, ImageCache.Entry> en : this.entries.entrySet()) {
         if (en.getValue().texture != null) {
            loaded.add(en);
         }
      }

      if (loaded.size() > 48) {
         loaded.sort(Comparator.comparingLong(enx -> enx.getValue().lastUsed));

         for (int i = 0; i < loaded.size() - 48; i++) {
            ImageCache.Entry e = loaded.get(i).getValue();
            if (now - e.lastUsed < 1000L) {
               break;
            }

            DynamicTextures.destroy(e.texture);
            this.entries.remove(loaded.get(i).getKey());
         }
      }
   }

   public synchronized void clear() {
      if (this.loader != null) {
         this.loader.shutdownNow();
         this.loader = null;
      }

      for (ImageCache.Entry e : this.entries.values()) {
         if (e.texture != null) {
            Identifier id = e.texture;
            Minecraft.getInstance().execute(() -> DynamicTextures.destroy(id));
         }
      }

      this.entries.clear();
   }

   public static final class Entry {
      volatile ImageCache.State state = ImageCache.State.LOADING;
      volatile String error;
      volatile int[] pixels;
      volatile int width;
      volatile int height;
      Identifier texture;
      DynamicTexture tex;
      long lastUsed = System.currentTimeMillis();

      public ImageCache.State state() {
         return this.state;
      }

      public String error() {
         return this.error;
      }

      public int width() {
         return this.width;
      }

      public int height() {
         return this.height;
      }

      public Identifier texture() {
         return this.texture;
      }
   }

   public static enum State {
      LOADING,
      READY,
      FAILED;
   }
}
