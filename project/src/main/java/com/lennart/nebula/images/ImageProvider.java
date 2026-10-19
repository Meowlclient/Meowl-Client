package com.lennart.nebula.images;

import java.net.URI;

public interface ImageProvider {
   String name();

   boolean matches(URI var1);

   URI directUrl(URI var1);
}
