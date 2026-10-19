package com.lennart.meowlserver.auth;

import java.util.Map;
import java.util.UUID;

public final class Session {
   private final String token;
   private final UUID uuid;
   private final String name;
   private final long expiresAt;
   private volatile long lastSeen = System.currentTimeMillis();
   private volatile String mcServer = "";
   private volatile Map<String, Object> presence;
   private volatile long presenceAt;
   private volatile String clientVersion = "";
   /** Spielernamen aus der Tab-Liste (kleingeschrieben), zuletzt per /presence gemeldet. */
   private volatile java.util.Set<String> near = java.util.Set.of();

   Session(String token, UUID uuid, String name, long expiresAt) {
      this.token = token;
      this.uuid = uuid;
      this.name = name;
      this.expiresAt = expiresAt;
   }

   public String token() {
      return this.token;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public String name() {
      return this.name;
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   public long lastSeen() {
      return this.lastSeen;
   }

   public void touch() {
      this.lastSeen = System.currentTimeMillis();
   }

   public String mcServer() {
      return this.mcServer;
   }

   public void setMcServer(String server) {
      if (server != null && server.length() <= 128) {
         this.mcServer = server;
      }
   }

   public Map<String, Object> presence() {
      return System.currentTimeMillis() - this.presenceAt < 180000L ? this.presence : null;
   }

   public void setPresence(Map<String, Object> presence) {
      this.presence = presence;
      this.presenceAt = System.currentTimeMillis();
   }

   public java.util.Set<String> near() {
      return System.currentTimeMillis() - this.presenceAt < 180000L ? this.near : java.util.Set.of();
   }

   public void setNear(java.util.Set<String> near) {
      this.near = java.util.Set.copyOf(near);
   }

   public String clientVersion() {
      return this.clientVersion;
   }

   public void setClientVersion(String version) {
      if (version != null && version.length() <= 32 && version.matches("[0-9A-Za-z.+-]*")) {
         this.clientVersion = version;
      }
   }

   public boolean clientAtLeast(int major, int minor, int patch) {
      String[] p = this.clientVersion.split("[^0-9]+");
      int[] v = new int[3];
      int n = 0;

      for (String part : p) {
         if (!part.isEmpty()) {
            if (n >= 3) {
               break;
            }

            try {
               v[n++] = Integer.parseInt(part);
            } catch (NumberFormatException var12) {
               return false;
            }
         }
      }

      if (n == 0) {
         return false;
      } else if (v[0] != major) {
         return v[0] > major;
      } else {
         return v[1] != minor ? v[1] > minor : v[2] >= patch;
      }
   }

   public boolean isOnline() {
      return System.currentTimeMillis() - this.lastSeen < 60000L;
   }
}
