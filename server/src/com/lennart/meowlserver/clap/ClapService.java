package com.lennart.meowlserver.clap;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import com.lennart.meowlserver.auth.AuthService;
import com.lennart.meowlserver.auth.Session;
import com.lennart.meowlserver.events.EventHub;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class ClapService {
   public static final long INVITE_TTL_MS = 15000L;
   public static final long POSITION_TIMEOUT_MS = 45000L;
   public static final long LEAD_MS = 2600L;
   public static final long HIT_GRACE_MS = 1200L;
   public static final long PLAY_DELAY_MS = 350L;
   private final AuthService auth;
   private final EventHub events;
   private final Map<String, ClapService.Invite> invites = new ConcurrentHashMap<>();
   private final Map<UUID, ClapService.ClapSession> byPlayer = new ConcurrentHashMap<>();

   public ClapService(AuthService auth, EventHub events) {
      this.auth = auth;
      this.events = events;
   }

   private static boolean supports(Session s) {
      return s.clientAtLeast(1, 4, 0);
   }

   private static Map<String, Object> map() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("serverTime", System.currentTimeMillis());
      return m;
   }

   private ClapService.ClapSession active(UUID u) {
      ClapService.ClapSession s = this.byPlayer.get(u);
      return s != null && s.state != ClapService.State.DONE ? s : null;
   }

   public synchronized Map<String, Object> invite(Session from, Map<String, Object> body) {
      UUID target = UUID.fromString(Json.str(body, "target", 40));
      if (target.equals(from.uuid())) {
         throw new ApiException(400, "Du kannst dich nicht selbst abklatschen");
      } else if (!supports(from)) {
         throw new ApiException(426, "Bitte aktualisiere deinen Meowl Client (Clap braucht 1.4.0)");
      } else if (this.active(from.uuid()) != null) {
         throw new ApiException(409, "Du bist schon in einem Clap");
      } else {
         Session to = this.auth.byUuid(target);
         if (to == null || !to.isOnline()) {
            throw new ApiException(404, "Spieler hat keinen Meowl Client (oder ist nicht verbunden)");
         } else if (!supports(to)) {
            throw new ApiException(409, to.name() + " hat eine ältere Meowl-Version ohne Clap");
         } else if (from.mcServer().isEmpty() || !from.mcServer().equals(to.mcServer())) {
            throw new ApiException(409, "Ihr seid nicht auf demselben Minecraft-Server");
         } else if (this.active(target) != null) {
            throw new ApiException(409, to.name() + " ist gerade in einem Clap");
         } else {
            this.invites.values().removeIf(i -> i.from().equals(from.uuid()) && i.to().equals(target));
            String id = UUID.randomUUID().toString();
            this.invites.put(id, new ClapService.Invite(id, from.uuid(), from.name(), target, from.mcServer(), System.currentTimeMillis() + 15000L));
            Map<String, Object> ev = map();
            ev.put("inviteId", id);
            ev.put("from", from.uuid().toString());
            ev.put("fromName", from.name());
            ev.put("expiresIn", 15000L);
            this.events.send(target, "clap_invite", ev);
            Map<String, Object> r = map();
            r.put("inviteId", id);
            r.put("expiresIn", 15000L);
            return r;
         }
      }
   }

   public synchronized Map<String, Object> respond(Session me, Map<String, Object> body) {
      String inviteId = Json.str(body, "inviteId", 40);
      ClapService.Invite inv = this.invites.remove(inviteId);
      if (inv != null && inv.to().equals(me.uuid()) && inv.expires() >= System.currentTimeMillis()) {
         boolean accept = Json.bool(body, "accept");
         String reason = body.get("reason") instanceof String rs && rs.matches("[a-z_]{1,24}") ? rs : "";
         Map<String, Object> result = map();
         result.put("inviteId", inviteId);
         result.put("accepted", accept);
         result.put("by", me.name());
         result.put("reason", reason);
         if (!accept) {
            this.events.send(inv.from(), "clap_invite_result", result);
            return result;
         } else {
            Session from = this.auth.byUuid(inv.from());
            if (from != null && from.isOnline() && inv.server().equals(me.mcServer()) && from.mcServer().equals(me.mcServer())) {
               if (this.active(me.uuid()) == null && this.active(inv.from()) == null) {
                  this.events.send(inv.from(), "clap_invite_result", result);
                  ClapService.ClapSession s = new ClapService.ClapSession(
                     new ClapService.Player(inv.from(), inv.fromName()), new ClapService.Player(me.uuid(), me.name())
                  );
                  this.byPlayer.put(s.a.uuid, s);
                  this.byPlayer.put(s.b.uuid, s);

                  for (ClapService.Player p : new ClapService.Player[]{s.a, s.b}) {
                     ClapService.Player o = s.other(p.uuid);
                     Map<String, Object> ev = map();
                     ev.put("id", s.id);
                     ev.put("partner", o.uuid.toString());
                     ev.put("partnerName", o.name);
                     ev.put("starter", p == s.a);
                     ev.put("timeout", 45000L);
                     this.events.send(p.uuid, "clap_session", ev);
                  }

                  return result;
               } else {
                  throw new ApiException(409, "Einer von euch ist schon in einem Clap");
               }
            } else {
               throw new ApiException(409, inv.fromName() + " ist nicht mehr verfügbar");
            }
         }
      } else {
         throw new ApiException(404, "Einladung nicht gefunden oder abgelaufen");
      }
   }

   private ClapService.ClapSession require(Session me, String id) {
      ClapService.ClapSession s = this.active(me.uuid());
      if (s == null || id != null && !s.id.equals(id)) {
         throw new ApiException(404, "Kein laufender Clap");
      } else {
         return s;
      }
   }

   public synchronized Map<String, Object> ready(Session me, Map<String, Object> body) {
      ClapService.ClapSession s = this.require(me, Json.str(body, "id", 40));
      boolean ready = Json.bool(body, "ready");
      if (s.state == ClapService.State.POSITION) {
         ClapService.Player p = s.get(me.uuid());
         if (p.ready != ready) {
            p.ready = ready;
            Map<String, Object> ev = map();
            ev.put("id", s.id);
            ev.put("ready", ready);
            this.events.send(s.other(me.uuid()).uuid, "clap_ready", ev);
         }

         if (s.a.ready && s.b.ready) {
            s.state = ClapService.State.CHARGE;
            s.beatAt = System.currentTimeMillis() + 2600L;
            Map<String, Object> ev = map();
            ev.put("id", s.id);
            ev.put("beatAt", s.beatAt);
            ev.put("lead", 2600L);
            ev.put("grace", 1200L);
            this.broadcast(s, "clap_go", ev);
         }
      }

      Map<String, Object> r = map();
      r.put("state", s.state.name());
      return r;
   }

   public synchronized Map<String, Object> hit(Session me, Map<String, Object> body) {
      ClapService.ClapSession s = this.require(me, Json.str(body, "id", 40));
      if (s.state != ClapService.State.CHARGE) {
         throw new ApiException(409, "Noch nicht im Takt");
      } else {
         ClapService.Player p = s.get(me.uuid());
         if (p.hit) {
            throw new ApiException(409, "Schon geklatscht");
         } else {
            long now = System.currentTimeMillis();
            long at = (long)Json.num(body, "at", 0.0, 9.0E15);
            at = Math.min(at, now + 50L);
            at = Math.max(at, s.beatAt - 2600L);
            at = Math.min(at, s.beatAt + 1200L);
            p.hit = true;
            p.at = at;
            p.charge = Json.num(body, "charge", 0.0, 1.0, 0.0);
            Map<String, Object> ev = map();
            ev.put("id", s.id);
            this.events.send(s.other(me.uuid()).uuid, "clap_partner_hit", ev);
            if (s.a.hit && s.b.hit) {
               this.resolve(s);
            }

            Map<String, Object> r = map();
            r.put("ok", true);
            return r;
         }
      }
   }

   static ClapService.Grade grade(boolean bothHit, long dtSync, long dtBeat, double avgCharge) {
      if (!bothHit) {
         return ClapService.Grade.BAD;
      } else {
         double e = Math.max((double)dtSync, 0.7 * dtBeat);
         if (e <= 45.0 && avgCharge >= 0.9) {
            return ClapService.Grade.MAX;
         } else if (e <= 90.0 && avgCharge >= 0.5) {
            return ClapService.Grade.PERFECT;
         } else {
            return e <= 200.0 ? ClapService.Grade.GOOD : ClapService.Grade.BAD;
         }
      }
   }

   private void resolve(ClapService.ClapSession s) {
      boolean both = s.a.hit && s.b.hit;
      long dtSync = both ? Math.abs(s.a.at - s.b.at) : -1L;
      long dtBeat = both ? (Math.abs(s.a.at - s.beatAt) + Math.abs(s.b.at - s.beatAt)) / 2L : -1L;
      double charge = ((s.a.hit ? s.a.charge : 0.0) + (s.b.hit ? s.b.charge : 0.0)) / 2.0;
      ClapService.Grade g = grade(both, dtSync, dtBeat, charge);
      s.state = ClapService.State.DONE;
      s.doneAt = System.currentTimeMillis();
      Map<String, Object> ev = map();
      ev.put("id", s.id);
      ev.put("grade", g.name());
      ev.put("dtSync", dtSync);
      ev.put("dtBeat", dtBeat);
      ev.put("charge", Math.round(charge * 100.0) / 100.0);
      ev.put("missed", !both);
      ev.put("playAt", s.doneAt + 350L);
      this.broadcast(s, "clap_result", ev);
   }

   public synchronized void cancel(Session me) {
      ClapService.ClapSession s = this.active(me.uuid());
      if (s != null) {
         this.end(s, me.name() + " hat abgebrochen");
      }

      this.invites.values().removeIf(i -> i.from().equals(me.uuid()));
   }

   private void end(ClapService.ClapSession s, String reason) {
      s.state = ClapService.State.DONE;
      s.doneAt = System.currentTimeMillis();
      Map<String, Object> ev = map();
      ev.put("id", s.id);
      ev.put("reason", reason);
      this.broadcast(s, "clap_end", ev);
   }

   private void broadcast(ClapService.ClapSession s, String type, Object data) {
      this.events.send(s.a.uuid, type, data);
      this.events.send(s.b.uuid, type, data);
   }

   public synchronized void tick() {
      long now = System.currentTimeMillis();
      this.invites.values().removeIf(i -> i.expires() < now);
      Iterator<Entry<UUID, ClapService.ClapSession>> it = this.byPlayer.entrySet().iterator();

      while (it.hasNext()) {
         ClapService.ClapSession s = it.next().getValue();
         if (s.state != ClapService.State.DONE) {
            for (ClapService.Player p : new ClapService.Player[]{s.a, s.b}) {
               Session session = this.auth.byUuid(p.uuid);
               if (session == null || !session.isOnline()) {
                  this.end(s, p.name + " ist nicht mehr verbunden");
                  break;
               }
            }

            if (s.state == ClapService.State.POSITION && now - s.created > 45000L) {
               this.end(s, "Zeit abgelaufen – ihr habt euch nicht rechtzeitig aufgestellt");
            } else if (s.state == ClapService.State.CHARGE && now > s.beatAt + 1200L + 300L) {
               this.resolve(s);
            }
         } else if (now - s.doneAt > 5000L) {
            it.remove();
         }
      }
   }

   private static final class ClapSession {
      final String id = UUID.randomUUID().toString();
      final ClapService.Player a;
      final ClapService.Player b;
      final long created = System.currentTimeMillis();
      ClapService.State state = ClapService.State.POSITION;
      long beatAt;
      long doneAt;

      ClapSession(ClapService.Player a, ClapService.Player b) {
         this.a = a;
         this.b = b;
      }

      ClapService.Player get(UUID u) {
         return this.a.uuid.equals(u) ? this.a : this.b;
      }

      ClapService.Player other(UUID u) {
         return this.a.uuid.equals(u) ? this.b : this.a;
      }
   }

   public static enum Grade {
      BAD,
      GOOD,
      PERFECT,
      MAX;
   }

   private record Invite(String id, UUID from, String fromName, UUID to, String server, long expires) {
   }

   private static final class Player {
      final UUID uuid;
      final String name;
      boolean ready;
      boolean hit;
      long at;
      double charge;

      Player(UUID uuid, String name) {
         this.uuid = uuid;
         this.name = name;
      }
   }

   private static enum State {
      POSITION,
      CHARGE,
      DONE;
   }
}
