package com.lennart.nebula.duel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lennart.nebula.net.MeowlService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DuelNetworkManager {
   CompletableFuture<MeowlService.Response> invite(UUID target, String dim, double x, double y, double z, double radius, int hp, int rounds) {
      JsonObject o = new JsonObject();
      o.addProperty("target", target.toString());
      o.addProperty("dim", dim);
      o.addProperty("x", x);
      o.addProperty("y", y);
      o.addProperty("z", z);
      o.addProperty("radius", radius);
      o.addProperty("hp", hp);
      o.addProperty("rounds", rounds);
      return MeowlService.get().post("/duel/invite", o);
   }

   CompletableFuture<MeowlService.Response> respond(String inviteId, boolean accept, String dim, double x, double y, double z) {
      JsonObject o = new JsonObject();
      o.addProperty("inviteId", inviteId);
      o.addProperty("accept", accept);
      o.addProperty("dim", dim);
      o.addProperty("x", x);
      o.addProperty("y", y);
      o.addProperty("z", z);
      return MeowlService.get().post("/duel/respond", o);
   }

   void pose(double x, double y, double z, float yaw, float pitch, boolean sneaking) {
      JsonObject o = new JsonObject();
      o.addProperty("x", x);
      o.addProperty("y", y);
      o.addProperty("z", z);
      o.addProperty("yaw", yaw);
      o.addProperty("pitch", Math.max(-90.0F, Math.min(90.0F, pitch)));
      o.addProperty("sneaking", sneaking);
      MeowlService.get().post("/duel/pose", o);
   }

   CompletableFuture<MeowlService.Response> shot(double ox, double oy, double oz, double dx, double dy, double dz, double blockDistance) {
      JsonObject o = new JsonObject();
      o.addProperty("bt", blockDistance);
      o.addProperty("ox", ox);
      o.addProperty("oy", oy);
      o.addProperty("oz", oz);
      o.addProperty("dx", dx);
      o.addProperty("dy", dy);
      o.addProperty("dz", dz);
      return MeowlService.get().post("/duel/shot", o);
   }

   void ground(List<double[]> grounds) {
      JsonObject o = new JsonObject();
      JsonArray arr = new JsonArray();

      for (double[] g : grounds) {
         JsonArray pair = new JsonArray();
         pair.add(g[0]);
         pair.add(g[1]);
         arr.add(pair);
      }

      o.add("grounds", arr);
      MeowlService.get().post("/duel/ground", o);
   }

   void reload() {
      MeowlService.get().post("/duel/reload", new JsonObject());
   }

   void leave() {
      if (MeowlService.get().isConnected()) {
         MeowlService.get().post("/duel/leave", new JsonObject());
      }
   }
}
