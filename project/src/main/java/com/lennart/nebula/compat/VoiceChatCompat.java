package com.lennart.nebula.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Simple Voice Chat zeichnet seine Symbole (Lautsprecher, Mikro aus …) innerhalb des Vanilla-Nametags. Zeichnet Meowl
 * den Nametag selbst, rufen wir das Ereignis von Voice Chat hier auf – über Reflection, damit Meowl auch ohne Voice
 * Chat läuft.
 */
public final class VoiceChatCompat {
   private static boolean resolved;
   private static Object event;
   private static Method invoker;
   private static Method render;

   private VoiceChatCompat() {
   }

   private static synchronized void resolve() {
      if (resolved) {
         return;
      }

      resolved = true;
      if (!FabricLoader.getInstance().isModLoaded("voicechat")) {
         return;
      }

      try {
         Class<?> events = Class.forName("de.maxhenkel.voicechat.events.RenderEvents");
         Field f = events.getField("RENDER_NAMEPLATE");
         event = f.get(null);
         invoker = event.getClass().getMethod("invoker");
         Object inv = invoker.invoke(event);

         for (Class<?> c = inv.getClass(); c != null && render == null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
               for (Method m : i.getMethods()) {
                  if (m.getName().equals("render") && m.getParameterCount() == 4) {
                     // die Schnittstelle von Voice Chat ist nicht öffentlich – Zugriff erlauben
                     m.setAccessible(true);
                     render = m;
                  }
               }
            }
         }
      } catch (ReflectiveOperationException | RuntimeException | LinkageError var6) {
         com.lennart.nebula.NebulaClient.LOGGER.warn("Voice-Chat-Symbole: Anbindung fehlgeschlagen: {}", var6.toString());
         event = null;
         render = null;
      }

      if (event != null && render == null) {
         com.lennart.nebula.NebulaClient.LOGGER.warn("Voice-Chat-Symbole: render-Methode nicht gefunden");
      }
   }

   /** Parameter in der Reihenfolge von Voice Chat: (Render-State, Kamera, PoseStack, Collector). */
   public static void renderNameplateIcons(Object state, Object camera, Object matrices, Object queue) {
      resolve();
      if (event != null && render != null) {
         try {
            render.invoke(invoker.invoke(event), state, camera, matrices, queue);
         } catch (ReflectiveOperationException | RuntimeException var5) {
            com.lennart.nebula.NebulaClient.LOGGER.warn("Voice-Chat-Symbole: Aufruf fehlgeschlagen: {}", var5.toString());
            render = null;
         }
      }
   }

   public static boolean loaded() {
      return FabricLoader.getInstance().isModLoaded("voicechat");
   }

   private static Method getClient;
   private static Method getStates;
   private static boolean stateResolved;

   /** Zeigt Voice Chat bei diesem Spieler gerade ein Symbol? (Sprechen, Flüstern, getrennt, stumm, Gruppe) */
   public static boolean hasIcon(java.util.UUID uuid) {
      if (!loaded()) {
         return false;
      }

      try {
         if (!stateResolved) {
            stateResolved = true;
            Class<?> cm = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            getClient = cm.getMethod("getClient");
            getStates = cm.getMethod("getPlayerStateManager");
         }

         if (getClient == null || getStates == null) {
            return false;
         }

         Object client = getClient.invoke(null);
         if (client != null) {
            Object talk = client.getClass().getMethod("getTalkCache").invoke(client);
            if (talk != null
               && ((Boolean)talk.getClass().getMethod("isTalking", java.util.UUID.class).invoke(talk, uuid)
                  || (Boolean)talk.getClass().getMethod("isWhispering", java.util.UUID.class).invoke(talk, uuid))) {
               return true;
            }
         }

         Object states = getStates.invoke(null);
         if (states == null) {
            return false;
         }

         Class<?> sc = states.getClass();
         return (Boolean)sc.getMethod("isPlayerDisconnected", java.util.UUID.class).invoke(states, uuid)
            || (Boolean)sc.getMethod("isPlayerDisabled", java.util.UUID.class).invoke(states, uuid)
            || sc.getMethod("getGroup", java.util.UUID.class).invoke(states, uuid) != null;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError var5) {
         getClient = null;
         return false;
      }
   }
}