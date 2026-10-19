package com.lennart.nebula.images;

import java.net.URI;
import java.util.Locale;

public class ImgurProvider implements ImageProvider {
   @Override
   public String name() {
      return "Imgur";
   }

   @Override
   public boolean matches(URI uri) {
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
      return host.equals("imgur.com") || host.endsWith(".imgur.com");
   }

   @Override
   public URI directUrl(URI uri) {
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (!path.startsWith("/a/") && !path.startsWith("/gallery/") && !path.startsWith("/t/")) {
         String file = path.replaceFirst("^/", "");
         if (!file.matches("[A-Za-z0-9]{5,10}(\\.[A-Za-z0-9]{3,4})?")) {
            throw new IllegalArgumentException("Unbekanntes Imgur-Linkformat");
         } else {
            String id = file.contains(".") ? file.substring(0, file.indexOf(46)) : file;
            String ext = file.contains(".") ? file.substring(file.indexOf(46) + 1).toLowerCase(Locale.ROOT) : "png";
            if (ext.equals("gifv") || ext.equals("mp4") || ext.equals("webm")) {
               ext = "gif";
            }

            return URI.create("https://i.imgur.com/" + id + "." + ext);
         }
      } else {
         throw new IllegalArgumentException("Imgur-Alben werden nicht unterstützt – bitte den Link eines einzelnen Bildes nehmen");
      }
   }
}
