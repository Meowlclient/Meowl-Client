package com.lennart.nebula.images;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

public final class UrlSafety {
   private UrlSafety() {
   }

   public static void checkPublic(URI uri) throws IOException {
      if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)) {
         for (InetAddress addr : InetAddress.getAllByName(uri.getHost())) {
            if (!isPublic(addr)) {
               throw new IOException("Adresse im lokalen Netz – blockiert");
            }
         }
      } else {
         throw new IOException("Nur öffentliche https-Links (Port 443)");
      }
   }

   static boolean isPublic(InetAddress a) {
      if (!a.isAnyLocalAddress() && !a.isLoopbackAddress() && !a.isLinkLocalAddress() && !a.isSiteLocalAddress() && !a.isMulticastAddress()) {
         byte[] b = a.getAddress();
         if (b.length == 4) {
            int first = b[0] & 255;
            int second = b[1] & 255;
            if (first == 100 && second >= 64 && second <= 127) {
               return false;
            }

            if (first == 0 || first >= 224) {
               return false;
            }
         }

         return !(a instanceof Inet6Address) || b.length != 16 || (b[0] & 254) != 252;
      } else {
         return false;
      }
   }
}
