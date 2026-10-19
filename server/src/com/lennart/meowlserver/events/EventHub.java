package com.lennart.meowlserver.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventHub {
   private static final int MAX_QUEUE = 256;
   private final Map<UUID, EventHub.Queue> queues = new ConcurrentHashMap<>();

   private EventHub.Queue queue(UUID uuid) {
      return this.queues.computeIfAbsent(uuid, u -> new EventHub.Queue());
   }

   public void send(UUID to, String type, Object data) {
      EventHub.Queue q = this.queue(to);
      synchronized (q) {
         q.events.addLast(new EventHub.Event(q.nextSeq++, type, data));

         while (q.events.size() > 256) {
            q.events.removeFirst();
         }

         q.notifyAll();
      }
   }

   public long latest(UUID uuid) {
      EventHub.Queue q = this.queue(uuid);
      synchronized (q) {
         return q.nextSeq - 1L;
      }
   }

   public List<EventHub.Event> poll(UUID uuid, long since, long waitMs) throws InterruptedException {
      EventHub.Queue q = this.queue(uuid);
      long deadline = System.currentTimeMillis() + waitMs;
      synchronized (q) {
         while (true) {
            List<EventHub.Event> out = new ArrayList<>();

            for (EventHub.Event e : q.events) {
               if (e.seq() > since) {
                  out.add(e);
               }
            }

            long left = deadline - System.currentTimeMillis();
            if (!out.isEmpty() || left <= 0L) {
               return out;
            }

            q.wait(left);
         }
      }
   }

   public void forget(UUID uuid) {
      this.queues.remove(uuid);
   }

   public record Event(long seq, String type, Object data) {
   }

   private static final class Queue {
      final Deque<EventHub.Event> events = new ArrayDeque<>();
      long nextSeq = 1L;
   }
}
