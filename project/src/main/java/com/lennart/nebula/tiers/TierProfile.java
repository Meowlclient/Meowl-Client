package com.lennart.nebula.tiers;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record TierProfile(String system, UUID uuid, String name, List<TierEntry> rankings, String region, int points) {
   public Optional<TierEntry> best() {
      return this.rankings.stream().min(Comparator.comparingInt(TierEntry::sortKey));
   }

   public Optional<TierEntry> forMode(String mode) {
      String m = mode.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      return this.rankings.stream().filter(e -> e.mode().equalsIgnoreCase(m) || e.modeTitle().toLowerCase(Locale.ROOT).replace(' ', '_').equals(m)).findFirst();
   }
}
