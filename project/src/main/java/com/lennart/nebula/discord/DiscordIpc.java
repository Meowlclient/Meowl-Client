package com.lennart.nebula.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class DiscordIpc implements Closeable {
   private static final int OP_HANDSHAKE = 0;
   private static final int OP_FRAME = 1;
   private static final int OP_CLOSE = 2;
   private RandomAccessFile pipe;
   private SocketChannel socket;

   static DiscordIpc open() throws IOException {
      DiscordIpc ipc = new DiscordIpc();
      boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
      IOException last = null;

      for (int i = 0; i < 10; i++) {
         try {
            if (windows) {
               ipc.pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
               return ipc;
            }

            for (String dir : candidates()) {
               Path p = Path.of(dir, "discord-ipc-" + i);
               if (Files.exists(p)) {
                  SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                  ch.connect(UnixDomainSocketAddress.of(p));
                  ipc.socket = ch;
                  return ipc;
               }
            }
         } catch (IOException var10) {
            last = var10;
         }
      }

      throw last != null ? last : new IOException("Discord läuft nicht");
   }

   private static String[] candidates() {
      String base = System.getenv("XDG_RUNTIME_DIR");
      if (base == null) {
         base = System.getenv("TMPDIR");
      }

      if (base == null) {
         base = "/tmp";
      }

      return new String[]{base, base + "/app/com.discordapp.Discord", base + "/snap.discord", "/tmp"};
   }

   void handshake(String clientId) throws IOException {
      JsonObject o = new JsonObject();
      o.addProperty("v", 1);
      o.addProperty("client_id", clientId);
      this.write(0, o.toString());
      JsonObject ready = this.read();
      if (ready == null || !"READY".equals(ready.has("evt") && !ready.get("evt").isJsonNull() ? ready.get("evt").getAsString() : "")) {
         throw new IOException(
            "Discord hat die Verbindung abgelehnt" + (ready != null && ready.has("message") ? ": " + ready.get("message").getAsString() : "")
         );
      }
   }

   void send(JsonObject payload) throws IOException {
      this.write(1, payload.toString());
      this.read();
   }

   private synchronized void write(int op, String json) throws IOException {
      byte[] data = json.getBytes(StandardCharsets.UTF_8);
      ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
      buf.putInt(op).putInt(data.length).put(data).flip();
      if (this.pipe != null) {
         this.pipe.write(buf.array(), 0, buf.limit());
      } else {
         while (buf.hasRemaining()) {
            this.socket.write(buf);
         }
      }
   }

   private void readFully(byte[] dst) throws IOException {
      if (this.pipe != null) {
         this.pipe.readFully(dst);
      } else {
         ByteBuffer b = ByteBuffer.wrap(dst);

         while (b.hasRemaining()) {
            if (this.socket.read(b) < 0) {
               throw new IOException("Verbindung zu Discord getrennt");
            }
         }
      }
   }

   private JsonObject read() throws IOException {
      byte[] header = new byte[8];
      this.readFully(header);
      ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
      int op = h.getInt();
      int len = h.getInt();
      if (len >= 0 && len <= 1048576) {
         byte[] body = new byte[len];
         this.readFully(body);
         if (op == 2) {
            throw new IOException("Discord hat die Verbindung geschlossen: " + new String(body, StandardCharsets.UTF_8));
         } else {
            try {
               return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException var7) {
               return null;
            }
         }
      } else {
         throw new IOException("Ungültige Antwort von Discord");
      }
   }

   @Override
   public void close() {
      try {
         if (this.pipe != null) {
            this.pipe.close();
         }

         if (this.socket != null) {
            this.socket.close();
         }
      } catch (IOException var2) {
      }
   }
}
