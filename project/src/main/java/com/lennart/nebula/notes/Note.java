package com.lennart.nebula.notes;

import java.util.UUID;

public class Note {
   public static final int[] COLORS = new int[]{-7643914, -1096636, -680437, -14498466, -12877066, -1292135, -7035976};
   final String id;
   String title;
   String content;
   String category;
   int color;
   boolean pinned;
   boolean favorite;
   long created;
   long modified;

   Note(String id, String title, String content) {
      this.id = id;
      this.title = title;
      this.content = content;
      this.category = "";
      this.color = COLORS[0];
      this.created = System.currentTimeMillis();
      this.modified = this.created;
   }

   static Note create(String title) {
      return new Note(UUID.randomUUID().toString(), title, "");
   }

   public String id() {
      return this.id;
   }

   public String title() {
      return this.title;
   }

   public String content() {
      return this.content;
   }

   public String category() {
      return this.category;
   }

   public int color() {
      return this.color;
   }

   public boolean pinned() {
      return this.pinned;
   }

   public boolean favorite() {
      return this.favorite;
   }

   public long modified() {
      return this.modified;
   }
}
