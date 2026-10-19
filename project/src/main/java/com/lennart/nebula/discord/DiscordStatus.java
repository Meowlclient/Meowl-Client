package com.lennart.nebula.discord;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiscordStatus {
   private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Meowl-Discord");
      t.setDaemon(true);
      return t;
   });
   private DiscordIpc ipc;
   private String connectedId;
   private String lastActivity;
   private long nextTry;
   private volatile String text = "Nicht verbunden";

   public String text() {
      return this.text;
   }

   public void setText(String text) {
      this.text = text;
   }

   public void update(String clientId, JsonObject activity) {
      this.worker.submit(() -> this.doUpdate(clientId, activity));
   }

   private void doUpdate(String clientId, JsonObject activity) {
      try {
         if (this.ipc == null || !clientId.equals(this.connectedId)) {
            this.close();
            if (System.currentTimeMillis() < this.nextTry) {
               return;
            }

            this.nextTry = System.currentTimeMillis() + 15000L;
            this.ipc = DiscordIpc.open();
            this.ipc.handshake(clientId);
            this.connectedId = clientId;
            this.lastActivity = null;
         }

         String key = activity.toString();
         if (!key.equals(this.lastActivity)) {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity);
            JsonObject cmd = new JsonObject();
            cmd.addProperty("cmd", "SET_ACTIVITY");
            cmd.add("args", args);
            cmd.addProperty("nonce", UUID.randomUUID().toString());
            this.ipc.send(cmd);
            this.lastActivity = key;
         }

         this.text = "Verbunden";
      } catch (Exception var6) {
         this.text = "Discord nicht erreichbar";
         this.close();
      }
   }

   private void close() {
      if (this.ipc != null) {
         this.ipc.close();
      }

      this.ipc = null;
      this.connectedId = null;
   }

   public void stop() {
      this.worker.submit(() -> {
         try {
            if (this.ipc != null) {
               JsonObject args = new JsonObject();
               args.addProperty("pid", ProcessHandle.current().pid());
               JsonObject cmd = new JsonObject();
               cmd.addProperty("cmd", "SET_ACTIVITY");
               cmd.add("args", args);
               cmd.addProperty("nonce", UUID.randomUUID().toString());
               this.ipc.send(cmd);
            }
         } catch (Exception var3) {
         }

         this.close();
         this.lastActivity = null;
         this.text = "Aus";
      });
   }
}
