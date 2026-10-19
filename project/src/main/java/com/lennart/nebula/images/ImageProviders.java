package com.lennart.nebula.images;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class ImageProviders {
   private static final List<ImageProvider> PROVIDERS = List.of(new ImgurProvider(), new UrlProvider());

   private ImageProviders() {
   }

   public static URI resolve(String text) {
      if (text != null && !text.isBlank()) {
         if (text.length() > 512) {
            throw new IllegalArgumentException("URL zu lang (max. 512 Zeichen)");
         } else {
            URI uri;
            try {
               uri = new URI(text.trim());
            } catch (Exception var5) {
               throw new IllegalArgumentException("Ungültige URL");
            }

            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
               throw new IllegalArgumentException("Nur https-Links sind erlaubt");
            } else if (uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)) {
               String host = uri.getHost().toLowerCase(Locale.ROOT);
               if (!host.equals("localhost") && host.contains(".") && !host.matches("[0-9.]+") && !host.contains(":")) {
                  for (ImageProvider p : PROVIDERS) {
                     if (p.matches(uri)) {
                        return p.directUrl(uri);
                     }
                  }

                  return uri;
               } else {
                  throw new IllegalArgumentException("IP-Adressen und lokale Hosts sind nicht erlaubt");
               }
            } else {
               throw new IllegalArgumentException("Links mit Benutzerdaten oder eigenem Port sind nicht erlaubt");
            }
         }
      } else {
         throw new IllegalArgumentException("Keine URL angegeben");
      }
   }

   public static boolean isValid(String text) {
      try {
         resolve(text);
         return true;
      } catch (IllegalArgumentException var2) {
         return false;
      }
   }
}
