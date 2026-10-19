package com.lennart.nebula.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

/** Server anpingen wie die Minecraft-Serverliste – mit Zeitlimit, damit nichts ewig „lädt“. */
public final class ServerPings {
   private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
      Thread t = new Thread(r, "Meowl-Server-Ping");
      t.setDaemon(true);
      return t;
   });
   private final ServerStatusPinger pinger = new ServerStatusPinger();
   private final Map<ServerData, Long> started = new ConcurrentHashMap<>();

   public void ping(ServerData d) {
      d.setState(ServerData.State.PINGING);
      d.motd = Component.literal("Wird angepingt …");
      d.status = Component.empty();
      d.ping = -1L;
      d.players = null;
      this.started.put(d, System.currentTimeMillis());
      boolean nativeTransport = Minecraft.getInstance().options.useNativeTransport();
      POOL.submit(() -> {
         try {
            this.pinger.pingServer(d, () -> {}, () -> {
               this.started.remove(d);
               d.setState(d.protocol == SharedConstants.getProtocolVersion() ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE);
            }, EventLoopGroupHolder.remote(nativeTransport));
         } catch (java.net.UnknownHostException var4) {
            this.fail(d, "Adresse nicht gefunden");
         } catch (Exception var5) {
            this.fail(d, "Nicht erreichbar");
         }
      });
   }

   private void fail(ServerData d, String why) {
      this.started.remove(d);
      d.setState(ServerData.State.UNREACHABLE);
      d.motd = Component.literal(why);
   }

   /** Jeden Tick: Verbindungen weiterlaufen lassen, hängende Pings nach 8 Sekunden als offline markieren. */
   public void tick() {
      this.pinger.tick();
      long now = System.currentTimeMillis();
      for (Map.Entry<ServerData, Long> e : this.started.entrySet()) {
         ServerData d = e.getKey();
         if (d.state() != ServerData.State.PINGING) {
            this.started.remove(d);
         } else if (now - e.getValue() > 8000L) {
            this.fail(d, "Keine Antwort");
         }
      }
   }

   public void stop() {
      this.pinger.removeAll();
      this.started.clear();
   }
}
