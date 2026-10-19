package com.lennart.meowlserver.duel;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import com.lennart.meowlserver.auth.AuthService;
import com.lennart.meowlserver.auth.Session;
import com.lennart.meowlserver.events.EventHub;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class DuelService {
   private static final Logger LOG = Logger.getLogger("Duel");
   private static final long INVITE_TTL_MS = 30000L;
   private static final long POSE_TIMEOUT_MS = 15000L;
   private static final long MAX_DURATION_MS = 1800000L;
   private static final double MAX_RANGE = 100.0;
   private static final double MAX_START_DISTANCE = 64.0;
   private static final double EYE = 1.62;
   private final AuthService auth;
   private final EventHub events;
   private final SecureRandom random = new SecureRandom();
   private final Map<String, DuelService.Invite> invites = new ConcurrentHashMap<>();
   private final Map<UUID, DuelSession> byPlayer = new ConcurrentHashMap<>();

   public DuelService(AuthService auth, EventHub events) {
      this.auth = auth;
      this.events = events;
   }

   private static Vec3 pos(Map<String, Object> body) {
      return new Vec3(Json.num(body, "x", -3.0E7, 3.0E7), Json.num(body, "y", -4096.0, 4096.0), Json.num(body, "z", -3.0E7, 3.0E7));
   }

   public Map<String, Object> invite(Session from, Map<String, Object> body) {
      UUID target = UUID.fromString(Json.str(body, "target", 40));
      if (target.equals(from.uuid())) {
         throw new ApiException(400, "Du kannst dich nicht selbst herausfordern");
      } else if (this.byPlayer.containsKey(from.uuid())) {
         throw new ApiException(409, "Du bist schon in einem Duell");
      } else {
         Session to = this.auth.byUuid(target);
         if (to != null && to.isOnline()) {
            if (from.mcServer().isEmpty() || !from.mcServer().equals(to.mcServer())) {
               throw new ApiException(409, "Ihr seid nicht auf demselben Minecraft-Server");
            } else if (this.byPlayer.containsKey(target)) {
               throw new ApiException(409, "Der Spieler ist schon in einem Duell");
            } else {
               String dim = Json.str(body, "dim", 64);
               double radius = Json.num(body, "radius", 8.0, 32.0);
               int hp = (int)Json.num(body, "hp", 50.0, 1000.0, 200.0);
               int rounds = (int)Json.num(body, "rounds", 1.0, 15.0, 1.0);
               String id = UUID.randomUUID().toString();
               this.invites
                  .put(
                     id,
                     new DuelService.Invite(
                        id, from.uuid(), from.name(), target, from.mcServer(), dim, pos(body), radius, hp, rounds, System.currentTimeMillis() + 30000L
                     )
                  );
               Map<String, Object> ev = new LinkedHashMap<>();
               ev.put("inviteId", id);
               ev.put("from", from.uuid().toString());
               ev.put("fromName", from.name());
               ev.put("radius", radius);
               ev.put("hp", hp);
               ev.put("rounds", rounds);
               this.events.send(target, "duel_invite", ev);
               return Map.of("inviteId", id);
            }
         } else {
            throw new ApiException(404, "Spieler hat keinen Meowl Client (oder ist nicht verbunden)");
         }
      }
   }

   public Map<String, Object> respond(Session me, Map<String, Object> body) {
      String inviteId = Json.str(body, "inviteId", 40);
      DuelService.Invite inv = this.invites.remove(inviteId);
      if (inv != null && inv.to().equals(me.uuid()) && inv.expires() >= System.currentTimeMillis()) {
         boolean accept = Json.bool(body, "accept");
         Map<String, Object> result = new LinkedHashMap<>();
         result.put("inviteId", inviteId);
         result.put("accepted", accept);
         result.put("by", me.name());
         this.events.send(inv.from(), "duel_invite_result", result);
         if (!accept) {
            return result;
         } else {
            Session from = this.auth.byUuid(inv.from());
            if (from == null || !from.isOnline() || !inv.server().equals(me.mcServer())) {
               throw new ApiException(409, "Herausforderer ist nicht mehr verfügbar");
            } else if (!this.byPlayer.containsKey(me.uuid()) && !this.byPlayer.containsKey(inv.from())) {
               String dim = Json.str(body, "dim", 64);
               Vec3 myPos = pos(body);
               if (dim.equals(inv.dim()) && !(myPos.distance(inv.fromPos()) > 64.0)) {
                  DuelArena arena = DuelArena.generate(inv.fromPos(), myPos, inv.radius(), this.random.nextLong());
                  DuelSession session = new DuelSession(
                     UUID.randomUUID().toString(), arena, inv.from(), inv.fromName(), me.uuid(), me.name(), inv.hp(), inv.rounds()
                  );
                  this.byPlayer.put(inv.from(), session);
                  this.byPlayer.put(me.uuid(), session);

                  for (DuelSession.PlayerState p : session.players.values()) {
                     this.events.send(p.uuid, "duel_start", this.startEvent(session, p.uuid));
                  }

                  LOG.info("Duell gestartet: " + inv.fromName() + " vs " + me.name());
                  return result;
               } else {
                  throw new ApiException(409, "Ihr müsst in derselben Dimension und höchstens 64 Blöcke voneinander entfernt sein");
               }
            } else {
               throw new ApiException(409, "Einer von euch ist schon in einem Duell");
            }
         }
      } else {
         throw new ApiException(404, "Einladung nicht gefunden oder abgelaufen");
      }
   }

   private Map<String, Object> startEvent(DuelSession s, UUID viewer) {
      DuelSession.PlayerState opp = s.opponent(viewer);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", s.id);
      m.put("opponent", opp.uuid.toString());
      m.put("opponentName", opp.name);
      m.put("center", List.of(s.arena.center().x(), s.arena.center().y(), s.arena.center().z()));
      m.put("floorY", s.arena.floorY());
      m.put("radius", s.arena.radius());
      List<Object> obstacles = new ArrayList<>();

      for (Aabb box : s.arena.obstacles()) {
         obstacles.add(box.toList());
      }

      m.put("obstacles", obstacles);
      m.put("challenger", s.challenger.toString());
      m.put("startIn", Math.max(0L, s.startAt - System.currentTimeMillis()));
      m.put("maxHp", s.maxHp);
      m.put("rounds", s.rounds);
      m.put("winsNeeded", s.winsNeeded);
      m.put("round", s.round);
      m.put("mag", 6);
      m.put("cooldown", 400L);
      m.put("reload", 2250L);
      return m;
   }

   private DuelSession require(Session me) {
      DuelSession s = this.byPlayer.get(me.uuid());
      if (s != null && s.state != DuelSession.State.ENDED) {
         s.update();
         return s;
      } else {
         throw new ApiException(404, "Kein laufendes Duell");
      }
   }

   public synchronized void pose(Session me, Map<String, Object> body) {
      DuelSession s = this.require(me);
      DuelSession.PlayerState p = s.player(me.uuid());
      long now = System.currentTimeMillis();
      DuelSession.Pose pose = new DuelSession.Pose(
         now, pos(body), (float)Json.num(body, "yaw", -100000.0, 100000.0), (float)Json.num(body, "pitch", -90.0, 90.0), Json.bool(body, "sneaking")
      );
      DuelSession.Pose last = p.latest();
      if (last != null && last.feet().distance(pose.feet()) > 12.0 && now - last.time() < 1000L) {
         throw new ApiException(422, "Unplausible Position");
      } else {
         p.history.addLast(pose);

         while (!p.history.isEmpty() && now - p.history.peekFirst().time() > 2000L) {
            p.history.removeFirst();
         }
      }
   }

   public synchronized Map<String, Object> shot(Session me, Map<String, Object> body) {
      DuelSession s = this.require(me);
      if (s.state != DuelSession.State.ACTIVE) {
         throw new ApiException(409, "Das Duell hat noch nicht begonnen");
      } else {
         DuelSession.PlayerState shooter = s.player(me.uuid());
         DuelSession.PlayerState target = s.opponent(me.uuid());
         long now = System.currentTimeMillis();
         if (shooter.hp <= 0) {
            throw new ApiException(409, "Du bist besiegt");
         } else if (now < shooter.reloadUntil) {
            throw new ApiException(409, "Lädt nach");
         } else if (shooter.ammo <= 0) {
            this.startReload(s, shooter, now);
            throw new ApiException(409, "Magazin leer");
         } else if (now - shooter.lastShot < 370L) {
            throw new ApiException(429, "Cooldown");
         } else {
            Vec3 origin = new Vec3(Json.num(body, "ox", -3.0E7, 3.0E7), Json.num(body, "oy", -4096.0, 4096.0), Json.num(body, "oz", -3.0E7, 3.0E7));
            Vec3 dir = new Vec3(Json.num(body, "dx", -1.01, 1.01), Json.num(body, "dy", -1.01, 1.01), Json.num(body, "dz", -1.01, 1.01)).normalize();
            double blockT = Json.num(body, "bt", 0.0, 100.0);
            if (dir.length() < 0.5) {
               throw new ApiException(400, "Ungültige Richtung");
            } else {
               DuelSession.Pose shooterPose = shooter.latest();
               if (shooterPose != null && now - shooterPose.time() <= 1000L && !(shooterPose.feet().add(new Vec3(0.0, 1.62, 0.0)).distance(origin) > 1.5)) {
                  shooter.lastShot = now;
                  shooter.ammo--;
                  double wallT = s.arena.rayObstacles(origin, dir, 100.0);
                  double maxT = Math.min(wallT >= 0.0 ? wallT : 100.0, blockT);
                  String hit = maxT < 100.0 ? "wall" : "miss";
                  double hitT = maxT;
                  int damage = 0;

                  for (DuelSession.Pose pose : target.history) {
                     if (now - pose.time() <= 500L || pose == target.latest()) {
                        double[] r = testHitbox(pose, origin, dir, maxT);
                        if (r != null && r[0] < hitT + 1.0E-9 && (damage == 0 || r[1] > damage)) {
                           hitT = r[0];
                           damage = (int)r[1];
                           hit = r[1] >= 150.0 ? "head" : (r[1] >= 55.0 ? "body" : "legs");
                        }
                     }
                  }

                  if (damage > 0) {
                     target.hp = Math.max(0, target.hp - damage);
                  }

                  Vec3 end = origin.add(dir.mul(hitT));
                  Map<String, Object> ev = new LinkedHashMap<>();
                  ev.put("shooter", me.uuid().toString());
                  ev.put("origin", List.of(origin.x(), origin.y(), origin.z()));
                  ev.put("end", List.of(end.x(), end.y(), end.z()));
                  ev.put("hit", hit);
                  ev.put("damage", damage);
                  ev.put("hp", s.hpMap());
                  ev.put("ammo", shooter.ammo);
                  this.broadcast(s, "duel_shot", ev);
                  if (shooter.ammo <= 0) {
                     this.startReload(s, shooter, now);
                  }

                  if (target.hp <= 0) {
                     shooter.wins++;
                     if (shooter.wins >= s.winsNeeded) {
                        this.finish(s, shooter.uuid, target.name + " wurde besiegt" + (s.rounds > 1 ? " (" + shooter.wins + ":" + target.wins + ")" : ""));
                     } else {
                        s.nextRound();
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("round", s.round);
                        r.put("rounds", s.rounds);
                        r.put("winner", shooter.uuid.toString());
                        r.put("winnerName", shooter.name);
                        r.put("wins", s.winsMap());
                        r.put("hp", s.hpMap());
                        r.put("mag", 6);
                        r.put("startIn", Math.max(0L, s.startAt - System.currentTimeMillis()));
                        this.broadcast(s, "duel_round", r);
                     }
                  }

                  return ev;
               } else {
                  throw new ApiException(422, "Schussposition passt nicht zu deiner gemeldeten Position");
               }
            }
         }
      }
   }

   private static double[] testHitbox(DuelSession.Pose pose, Vec3 origin, Vec3 dir, double maxT) {
      Vec3 f = pose.feet();
      double h = pose.sneaking() ? 1.5 : 1.8;
      double hw = 0.35;
      Aabb head = new Aabb(f.x() - 0.32, f.y() + h - 0.5, f.z() - 0.32, f.x() + 0.32, f.y() + h + 0.08, f.z() + 0.32);
      Aabb body = new Aabb(f.x() - hw, f.y() + 0.75, f.z() - hw, f.x() + hw, f.y() + h - 0.5, f.z() + hw);
      Aabb legs = new Aabb(f.x() - hw, f.y(), f.z() - hw, f.x() + hw, f.y() + 0.75, f.z() + hw);
      double tHead = head.ray(origin, dir, maxT);
      if (tHead >= 0.0) {
         return new double[]{tHead, 150.0};
      } else {
         double tBody = body.ray(origin, dir, maxT);
         if (tBody >= 0.0) {
            return new double[]{tBody, 55.0};
         } else {
            double tLegs = legs.ray(origin, dir, maxT);
            return tLegs >= 0.0 ? new double[]{tLegs, 46.0} : null;
         }
      }
   }

   public synchronized void ground(Session me, Map<String, Object> body) {
      DuelSession s = this.require(me);
      if (s.challenger.equals(me.uuid()) && !s.grounded) {
         if (body.get("grounds") instanceof List<?> list && list.size() <= 64) {
            List<double[]> grounds = new ArrayList<>();
            Iterator obstacles = list.iterator();

            while (obstacles.hasNext()) {
               if (!(obstacles.next() instanceof List<?> pair)
                  || pair.size() != 2
                  || !(pair.get(0) instanceof Double lo)
                  || !(pair.get(1) instanceof Double hi)) {
                  throw new ApiException(400, "Ungültiger Eintrag");
               }

               grounds.add(new double[]{lo, hi});
            }

            try {
               s.arena = s.arena.grounded(grounds);
            } catch (IllegalArgumentException var11) {
               throw new ApiException(400, var11.getMessage());
            }

            s.grounded = true;
            List<Object> obstaclesx = new ArrayList<>();

            for (Aabb box : s.arena.obstacles()) {
               obstaclesx.add(box.toList());
            }

            this.broadcast(s, "duel_arena", Map.of("id", s.id, "obstacles", obstaclesx));
         } else {
            throw new ApiException(400, "grounds fehlt");
         }
      } else {
         throw new ApiException(409, "Boden schon gesetzt");
      }
   }

   public synchronized void reload(Session me) {
      DuelSession s = this.require(me);
      DuelSession.PlayerState p = s.player(me.uuid());
      if (p.ammo < 6 && System.currentTimeMillis() >= p.reloadUntil) {
         this.startReload(s, p, System.currentTimeMillis());
      }
   }

   private void startReload(DuelSession s, DuelSession.PlayerState p, long now) {
      p.reloadUntil = now + 2250L;
      p.ammo = 6;
      this.broadcast(s, "duel_reload", Map.of("player", p.uuid.toString(), "duration", 2250L));
   }

   public synchronized void leave(Session me) {
      DuelSession s = this.byPlayer.get(me.uuid());
      if (s != null && s.state != DuelSession.State.ENDED) {
         this.finish(s, s.opponent(me.uuid()).uuid, me.name() + " hat aufgegeben");
      }
   }

   private void finish(DuelSession s, UUID winner, String reason) {
      s.end(winner);
      Map<String, Object> ev = new LinkedHashMap<>();
      ev.put("id", s.id);
      ev.put("winner", winner == null ? null : winner.toString());
      ev.put("winnerName", winner == null ? null : s.player(winner).name);
      ev.put("reason", reason);
      ev.put("wins", s.winsMap());
      this.broadcast(s, "duel_end", ev);
      LOG.info("Duell beendet: " + reason);
   }

   private void broadcast(DuelSession s, String type, Object data) {
      for (DuelSession.PlayerState p : s.players.values()) {
         this.events.send(p.uuid, type, data);
      }
   }

   public synchronized void tick() {
      long now = System.currentTimeMillis();
      this.invites.values().removeIf(i -> i.expires() < now);
      Iterator<Entry<UUID, DuelSession>> it = this.byPlayer.entrySet().iterator();

      while (it.hasNext()) {
         DuelSession s = it.next().getValue();
         s.update();
         if (s.state == DuelSession.State.ENDED) {
            if (now - s.endedAt > 5000L) {
               it.remove();
            }
         } else if (now - s.created > 1800000L) {
            this.finish(s, null, "Zeit abgelaufen");
         } else if (s.state == DuelSession.State.ACTIVE) {
            for (DuelSession.PlayerState p : s.players.values()) {
               DuelSession.Pose last = p.latest();
               long lastTime = last == null ? s.startAt : last.time();
               Session session = this.auth.byUuid(p.uuid);
               if (now - lastTime > 15000L || session == null || !session.isOnline()) {
                  this.finish(s, s.opponent(p.uuid).uuid, p.name + " hat die Verbindung verloren");
                  break;
               }
            }
         }
      }
   }

   private record Invite(
      String id, UUID from, String fromName, UUID to, String server, String dim, Vec3 fromPos, double radius, int hp, int rounds, long expires
   ) {
   }
}
