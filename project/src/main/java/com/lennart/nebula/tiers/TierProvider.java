package com.lennart.nebula.tiers;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TierProvider {
   String id();

   String displayName();

   CompletableFuture<Optional<TierProfile>> fetch(UUID var1, String var2);
}
