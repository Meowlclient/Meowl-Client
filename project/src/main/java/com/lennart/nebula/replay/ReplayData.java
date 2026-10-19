package com.lennart.nebula.replay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Aufgezeichnete Spieler-Bewegungen: ein Bild je Tick (20 pro Sekunde). */
public final class ReplayData {
   public static final int CROUCH = 1;
   public static final int SPRINT = 2;
   public static final int SWIM = 4;
   public static final int FLY = 8;
   public static final int SWING = 16;
   /** Ausrüstung: Haupthand, Zweithand, Kopf, Brust, Beine, Füße. */
   public static final int SLOTS = 6;
   private static final int MAGIC = 0x4D525031;

   /** Zustand eines Spielers in einem Tick. */
   public record State(UUID id, String name, double x, double y, double z, float yRot, float xRot, float head, float body, int flags, String[] items) {
   }

   public record Frame(List<State> players) {
   }

   /** Ein gespeicherter Ausschnitt mit Dimension und Startzeit. */
   public record Clip(String dimension, long startedAt, List<Frame> frames) {
      public float seconds() {
         return this.frames.size() / 20.0F;
      }
   }

   private ReplayData() {
   }

   public static void save(Clip clip, Path file) throws IOException {
      Files.createDirectories(file.getParent());

      try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
         out.writeInt(MAGIC);
         out.writeUTF(clip.dimension());
         out.writeLong(clip.startedAt());
         out.writeInt(clip.frames().size());

         for (Frame f : clip.frames()) {
            out.writeShort(f.players().size());

            for (State s : f.players()) {
               out.writeLong(s.id().getMostSignificantBits());
               out.writeLong(s.id().getLeastSignificantBits());
               out.writeUTF(s.name());
               out.writeDouble(s.x());
               out.writeDouble(s.y());
               out.writeDouble(s.z());
               out.writeFloat(s.yRot());
               out.writeFloat(s.xRot());
               out.writeFloat(s.head());
               out.writeFloat(s.body());
               out.writeByte(s.flags());

               for (int i = 0; i < SLOTS; i++) {
                  out.writeUTF(s.items()[i] == null ? "" : s.items()[i]);
               }
            }
         }
      }
   }

   public static Clip load(Path file) throws IOException {
      try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
         if (in.readInt() != MAGIC) {
            throw new IOException("Keine Meowl-Replay-Datei");
         }

         String dim = in.readUTF();
         long started = in.readLong();
         int n = in.readInt();
         List<Frame> frames = new ArrayList<>(n);

         for (int i = 0; i < n; i++) {
            int count = in.readUnsignedShort();
            List<State> list = new ArrayList<>(count);

            for (int k = 0; k < count; k++) {
               UUID id = new UUID(in.readLong(), in.readLong());
               String name = in.readUTF();
               double x = in.readDouble();
               double y = in.readDouble();
               double z = in.readDouble();
               float yRot = in.readFloat();
               float xRot = in.readFloat();
               float head = in.readFloat();
               float body = in.readFloat();
               int flags = in.readByte();
               String[] items = new String[SLOTS];

               for (int s = 0; s < SLOTS; s++) {
                  String it = in.readUTF();
                  items[s] = it.isEmpty() ? null : it.intern();
               }

               list.add(new State(id, name.intern(), x, y, z, yRot, xRot, head, body, flags, items));
            }

            frames.add(new Frame(list));
         }

         return new Clip(dim, started, frames);
      }
   }
}
