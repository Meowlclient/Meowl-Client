package com.lennart.nebula.update;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface UpdateSource {
   String name();

   CompletableFuture<Optional<UpdateInfo>> fetchLatest();
}
