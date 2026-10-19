package com.lennart.nebula.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AnvilCalc {
   public static final int TOO_EXPENSIVE = 40;

   private AnvilCalc() {
   }

   public static int penalty(int work) {
      return (1 << work) - 1;
   }

   public static int xpForLevel(int l) {
      if (l <= 16) {
         return l * l + 6 * l;
      } else {
         return l <= 31 ? (int)Math.round(2.5 * l * l - 40.5 * l + 360.0) : (int)Math.round(4.5 * l * l - 162.5 * l + 2220.0);
      }
   }

   public static AnvilCalc.Result solve(List<AnvilCalc.Book> books, int itemWork, String itemName, boolean creative) {
      int n = books.size();
      if (n == 0) {
         return new AnvilCalc.Result(List.of(), 0, 0, itemWork, true);
      } else {
         int full = (1 << n) - 1;
         int maxW = n + itemWork + 1;
         int inf = 536870911;
         int[] value = new int[1 << n];

         for (int m = 1; m <= full; m++) {
            int low = Integer.numberOfTrailingZeros(m);
            value[m] = value[m & m - 1] + books.get(low).bookValue();
         }

         int[][] cost = new int[1 << n][maxW + 1];
         int[][][] how = new int[1 << n][maxW + 1][];

         for (int[] row : cost) {
            Arrays.fill(row, inf);
         }

         for (int i = 0; i < n; i++) {
            cost[1 << i][0] = 0;
         }

         for (int m = 1; m <= full; m++) {
            if (Integer.bitCount(m) >= 2) {
               for (int a = m - 1 & m; a > 0; a = a - 1 & m) {
                  int b = m ^ a;

                  for (int wa = 0; wa <= maxW; wa++) {
                     if (cost[a][wa] < inf) {
                        for (int wb = 0; wb <= maxW; wb++) {
                           if (cost[b][wb] < inf) {
                              int step = penalty(wa) + penalty(wb) + value[b];
                              if (creative || step < 40) {
                                 int w = Math.max(wa, wb) + 1;
                                 if (w <= maxW) {
                                    int total = cost[a][wa] + cost[b][wb] + step;
                                    if (total < cost[m][w]) {
                                       cost[m][w] = total;
                                       how[m][w] = new int[]{a, wa, wb};
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         List<AnvilCalc.Step> steps = new ArrayList<>();
         if (itemName == null) {
            int bestW = -1;

            for (int w = 0; w <= maxW; w++) {
               if (cost[full][w] < inf && (bestW < 0 || cost[full][w] < cost[full][bestW])) {
                  bestW = w;
               }
            }

            if (bestW < 0) {
               return new AnvilCalc.Result(List.of(), 0, 0, 0, false);
            } else {
               buildBooks(books, how, full, bestW, steps);
               return finish(steps, bestW);
            }
         } else {
            int[][] item = new int[1 << n][maxW + 1];
            int[][][] itemHow = new int[1 << n][maxW + 1][];

            for (int[] row : item) {
               Arrays.fill(row, inf);
            }

            item[0][Math.min(itemWork, maxW)] = 0;

            for (int mx = 0; mx <= full; mx++) {
               for (int wx = 0; wx <= maxW; wx++) {
                  if (item[mx][wx] < inf) {
                     int rest = full ^ mx;

                     for (int t = rest; t > 0; t = t - 1 & rest) {
                        for (int wt = 0; wt <= maxW; wt++) {
                           if (cost[t][wt] < inf) {
                              int step = penalty(wx) + penalty(wt) + value[t];
                              if (creative || step < 40) {
                                 int nw = Math.max(wx, wt) + 1;
                                 if (nw <= maxW) {
                                    int total = item[mx][wx] + cost[t][wt] + step;
                                    int nm = mx | t;
                                    if (total < item[nm][nw]) {
                                       item[nm][nw] = total;
                                       itemHow[nm][nw] = new int[]{mx, wx, t, wt};
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            int bestW = -1;

            for (int wxx = 0; wxx <= maxW; wxx++) {
               if (item[full][wxx] < inf && (bestW < 0 || item[full][wxx] < item[full][bestW] || item[full][wxx] == item[full][bestW] && wxx < bestW)) {
                  bestW = wxx;
               }
            }

            if (bestW < 0) {
               return new AnvilCalc.Result(List.of(), 0, 0, 0, false);
            } else {
               buildItem(books, how, itemHow, full, bestW, itemName, steps);
               return finish(steps, bestW);
            }
         }
      }
   }

   private static AnvilCalc.Result finish(List<AnvilCalc.Step> steps, int finalWork) {
      int levels = 0;
      int xp = 0;

      for (AnvilCalc.Step s : steps) {
         levels += s.cost();
         xp += xpForLevel(s.cost());
      }

      return new AnvilCalc.Result(List.copyOf(steps), levels, xp, finalWork, true);
   }

   private static String label(List<AnvilCalc.Book> books, int m) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < books.size(); i++) {
         if ((m >> i & 1) != 0) {
            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(books.get(i).name());
         }
      }

      return sb.toString();
   }

   private static void buildBooks(List<AnvilCalc.Book> books, int[][][] how, int m, int w, List<AnvilCalc.Step> out) {
      int[] h = how[m][w];
      if (h != null) {
         int a = h[0];
         int b = m ^ a;
         buildBooks(books, how, a, h[1], out);
         buildBooks(books, how, b, h[2], out);
         int cost = penalty(h[1]) + penalty(h[2]);
         int value = 0;

         for (int i = 0; i < books.size(); i++) {
            if ((b >> i & 1) != 0) {
               value += books.get(i).bookValue();
            }
         }

         out.add(new AnvilCalc.Step("Buch [" + label(books, a) + "]", "Buch [" + label(books, b) + "]", cost + value, w));
      }
   }

   private static void buildItem(List<AnvilCalc.Book> books, int[][][] how, int[][][] itemHow, int m, int w, String itemName, List<AnvilCalc.Step> out) {
      int[] h = itemHow[m][w];
      if (h != null) {
         buildItem(books, how, itemHow, h[0], h[1], itemName, out);
         buildBooks(books, how, h[2], h[3], out);
         int value = 0;

         for (int i = 0; i < books.size(); i++) {
            if ((h[2] >> i & 1) != 0) {
               value += books.get(i).bookValue();
            }
         }

         String left = h[0] == 0 ? itemName : itemName + " [" + label(books, h[0]) + "]";
         out.add(new AnvilCalc.Step(left, "Buch [" + label(books, h[2]) + "]", penalty(h[1]) + penalty(h[3]) + value, w));
      }
   }

   public record Book(String name, int level, int anvilCost) {
      int bookValue() {
         return Math.max(1, this.anvilCost / 2) * this.level;
      }
   }

   public record Result(List<AnvilCalc.Step> steps, int totalLevels, int totalXp, int finalWork, boolean possible) {
   }

   public record Step(String left, String right, int cost, int workAfter) {
   }
}
