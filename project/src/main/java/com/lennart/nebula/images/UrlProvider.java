package com.lennart.nebula.images;

import java.net.URI;

public class UrlProvider implements ImageProvider {
   @Override
   public String name() {
      return "URL";
   }

   @Override
   public boolean matches(URI uri) {
      return true;
   }

   @Override
   public URI directUrl(URI uri) {
      return uri;
   }
}
