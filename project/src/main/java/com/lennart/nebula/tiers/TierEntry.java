package com.lennart.nebula.tiers;

import java.util.Locale;

public record TierEntry(String mode, String modeTitle, int tier, String subtier, boolean retired, String peak) {
   public String label() {
      return (this.retired ? "R" : "") + this.subtier + this.tier;
   }

   public String subtierName() {
      String var1 = this.subtier;

      return switch (var1) {
         case "HT" -> "High";
         case "MT" -> "Mid";
         case "ST" -> "Start";
         default -> "Low";
      };
   }

   public int sortKey() {
      String var2 = this.subtier;

      int sub = switch (var2) {
         case "HT" -> 0;
         case "MT" -> 1;
         case "LT" -> 2;
         default -> 3;
      };
      return this.tier * 10 + sub + (this.retired ? 5 : 0);
   }

   public int color() {
      boolean high = "HT".equals(this.subtier);

      return switch (this.tier) {
         case 1 -> high ? -12470 : -2772139;
         case 2 -> high ? -5983289 : -7828075;
         case 3 -> high ? -1531798 : -4889818;
         case 4 -> high ? -7372120 : -10134663;
         default -> high ? -7630442 : -9538696;
         case 6 -> high ? -7696487 : -9538950;
      };
   }

   public static TierEntry parse(String mode, String title, String rank, boolean retired, String peak) {
      if (rank == null) {
         return null;
      } else {
         String r = rank.trim().toUpperCase(Locale.ROOT);
         if (r.startsWith("R")) {
            retired = true;
            r = r.substring(1);
         }

         if (r.length() < 3) {
            return null;
         } else {
            String sub = r.substring(0, 2);
            if (!sub.equals("HT") && !sub.equals("LT") && !sub.equals("MT") && !sub.equals("ST")) {
               return null;
            } else {
               try {
                  int tier = Integer.parseInt(r.substring(2));
                  return tier >= 1 && tier <= 6 ? new TierEntry(mode, title, tier, sub, retired, peak) : null;
               } catch (NumberFormatException var81) {
                  return null;
               }
            }
         }
      }
   }
}
