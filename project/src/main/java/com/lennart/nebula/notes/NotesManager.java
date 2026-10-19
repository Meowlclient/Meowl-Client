package com.lennart.nebula.notes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NotesManager {
   private static final long SAVE_DELAY_MS = 1500L;
   private final NotesStorage storage;
   private final List<Note> notes;
   private boolean dirty;
   private long dirtySince;

   public NotesManager(NotesStorage storage) {
      this.storage = storage;
      this.notes = new ArrayList<>(storage.load());
      if (this.notes.isEmpty()) {
         Note welcome = Note.create("Meine Notiz");
         welcome.content = "• Server IP\n• Commands\n• Ideen";
         this.notes.add(welcome);
         this.markDirty();
      }
   }

   public List<Note> list(String query, boolean favoritesOnly) {
      String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      List<Note> out = new ArrayList<>();

      for (Note n : this.notes) {
         if ((!favoritesOnly || n.favorite)
            && (
               q.isEmpty()
                  || n.title.toLowerCase(Locale.ROOT).contains(q)
                  || n.content.toLowerCase(Locale.ROOT).contains(q)
                  || n.category.toLowerCase(Locale.ROOT).contains(q)
            )) {
            out.add(n);
         }
      }

      out.sort(Comparator.<Note, Boolean>comparing(nx -> !nx.pinned).thenComparing(nx -> -nx.modified));
      return out;
   }

   public Note create() {
      Note note = Note.create("Neue Notiz");
      this.notes.add(note);
      this.markDirty();
      return note;
   }

   public void delete(Note note) {
      if (this.notes.remove(note)) {
         this.markDirty();
      }
   }

   public void rename(Note note, String title) {
      String t = title != null && !title.isBlank() ? title.trim() : "Ohne Titel";
      if (!t.equals(note.title)) {
         note.title = t;
         this.touch(note);
      }
   }

   public void setContent(Note note, String content) {
      if (!content.equals(note.content)) {
         note.content = content;
         this.touch(note);
      }
   }

   public void setCategory(Note note, String category) {
      String c = category == null ? "" : category.trim();
      if (!c.equals(note.category)) {
         note.category = c;
         this.touch(note);
      }
   }

   public void togglePinned(Note note) {
      note.pinned = !note.pinned;
      this.markDirty();
   }

   public void toggleFavorite(Note note) {
      note.favorite = !note.favorite;
      this.markDirty();
   }

   public void cycleColor(Note note) {
      int i = 0;

      for (int k = 0; k < Note.COLORS.length; k++) {
         if (Note.COLORS[k] == note.color) {
            i = k;
         }
      }

      note.color = Note.COLORS[(i + 1) % Note.COLORS.length];
      this.markDirty();
   }

   private void touch(Note note) {
      note.modified = System.currentTimeMillis();
      this.markDirty();
   }

   private void markDirty() {
      if (!this.dirty) {
         this.dirtySince = System.currentTimeMillis();
      }

      this.dirty = true;
   }

   public void tick() {
      if (this.dirty && System.currentTimeMillis() - this.dirtySince >= 1500L) {
         this.saveNow();
      }
   }

   public void saveNow() {
      if (this.dirty) {
         this.storage.save(this.notes);
         this.dirty = false;
      }
   }
}
