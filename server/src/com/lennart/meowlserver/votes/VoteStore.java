package com.lennart.meowlserver.votes;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Kosmetik-Abstimmung: jeder Meowl-Spieler stimmt pro Kosmetik ab, ob sie bleiben soll (✓) oder raus kann (✗), und kann
 * sich neue Kosmetik wünschen. Gespeichert in data/votes.json (lesbar auf dem Server), gezählt für alle.
 */
public final class VoteStore {
   private static final Logger LOG = Logger.getLogger("Votes");
   private static final int MAX_ITEMS = 600;
   private static final int MAX_WISHES_PER_USER = 25;
   private static final int MAX_WISHES = 3000;
   private final Path file;
   /** Spieler → (Kosmetik → bleibt?) */
   private final Map<String, Map<String, Boolean>> votes = new HashMap<>();
   private final List<Map<String, Object>> wishes = new ArrayList<>();

   public VoteStore(Path dataDir) {
      this.file = dataDir.resolve("votes.json");
      this.load();
   }

   private synchronized void load() {
      try {
         if (Files.exists(this.file)) {
            Map<String, Object> root = Json.parseObject(Files.readString(this.file, StandardCharsets.UTF_8));
            if (root.get("votes") instanceof Map<?, ?> v) {
               for (Entry<?, ?> e : v.entrySet()) {
                  Map<String, Boolean> mine = new LinkedHashMap<>();
                  if (e.getValue() instanceof Map<?, ?> m) {
                     m.forEach((k, b) -> mine.put(k.toString(), Boolean.TRUE.equals(b)));
                  }

                  this.votes.put(e.getKey().toString(), mine);
               }
            }

            if (root.get("wishes") instanceof List<?> w) {
               for (Object o : w) {
                  if (o instanceof Map<?, ?> m) {
                     Map<String, Object> wish = new LinkedHashMap<>();
                     m.forEach((k, x) -> wish.put(k.toString(), x));
                     this.wishes.add(wish);
                  }
               }
            }
         }
      } catch (IOException | RuntimeException var5) {
         LOG.warning("votes.json konnte nicht gelesen werden: " + var5);
      }
   }

   private synchronized void save() {
      Map<String, Object> v = new LinkedHashMap<>();
      this.votes.forEach((k, m) -> v.put(k, new LinkedHashMap<>(m)));
      try {
         Path tmp = this.file.resolveSibling("votes.json.tmp");
         Files.writeString(tmp, Json.write(Map.of("votes", v, "wishes", this.wishes)), StandardCharsets.UTF_8);
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var3) {
         LOG.warning("votes.json konnte nicht gespeichert werden: " + var3);
      }
   }

   private static String item(Map<String, Object> body) {
      String item = Json.str(body, "item", 80).trim();
      if (item.isEmpty() || !item.matches("[\\p{L}\\p{N} .:_+&'()!?-]+")) {
         throw new ApiException(400, "Ungültige Kosmetik");
      }

      return item;
   }

   /** Stimme abgeben bzw. ändern. */
   public synchronized void vote(UUID user, Map<String, Object> body) {
      String item = item(body);
      Map<String, Boolean> mine = this.votes.computeIfAbsent(user.toString(), k -> new LinkedHashMap<>());
      if (!mine.containsKey(item) && mine.size() >= MAX_ITEMS) {
         throw new ApiException(429, "Zu viele Stimmen");
      }

      mine.put(item, Json.bool(body, "keep"));
      this.save();
   }

   /** Neue Kosmetik wünschen. */
   public synchronized void wish(UUID user, String name, Map<String, Object> body) {
      String text = Json.str(body, "text", 200).replaceAll("[\\p{Cntrl}]", " ").trim();
      if (text.length() < 2) {
         throw new ApiException(400, "Wunsch ist zu kurz");
      }

      long mine = this.wishes.stream().filter(w -> user.toString().equals(w.get("uuid"))).count();
      if (mine >= MAX_WISHES_PER_USER) {
         throw new ApiException(429, "Du hast schon " + MAX_WISHES_PER_USER + " Wünsche abgegeben");
      }

      if (this.wishes.size() >= MAX_WISHES) {
         this.wishes.remove(0);
      }

      Map<String, Object> w = new LinkedHashMap<>();
      w.put("uuid", user.toString());
      w.put("name", name);
      w.put("text", text);
      w.put("time", System.currentTimeMillis());
      this.wishes.add(w);
      this.save();
   }

   /** Ergebnis: pro Kosmetik [bleibt, raus], eigene Stimmen und die neuesten Wünsche (ohne Spieler-IDs). */
   public synchronized Map<String, Object> results(UUID user) {
      Map<String, int[]> count = new LinkedHashMap<>();
      for (Map<String, Boolean> m : this.votes.values()) {
         m.forEach((item, keep) -> count.computeIfAbsent(item, k -> new int[2])[keep ? 0 : 1]++);
      }

      Map<String, Object> tally = new LinkedHashMap<>();
      count.forEach((k, c) -> tally.put(k, List.of(c[0], c[1])));
      List<Object> recent = new ArrayList<>();
      for (int i = this.wishes.size() - 1; i >= 0 && recent.size() < 40; i--) {
         Map<String, Object> w = this.wishes.get(i);
         recent.add(Map.of("name", String.valueOf(w.getOrDefault("name", "?")), "text", String.valueOf(w.getOrDefault("text", ""))));
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("tally", tally);
      out.put("mine", new LinkedHashMap<>(this.votes.getOrDefault(user.toString(), Map.of())));
      out.put("voters", this.votes.size());
      out.put("wishes", recent);
      return out;
   }
}
