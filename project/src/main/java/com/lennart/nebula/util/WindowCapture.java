package com.lennart.nebula.util;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinUser;
import java.util.Locale;

/**
 * Bild eines anderen Fensters abgreifen (nur Windows): sucht ein sichtbares Fenster, dessen Titel einen Text enthält
 * (z.B. „TikTok“ im Browser), und kopiert seinen Inhalt – auch wenn es von Minecraft verdeckt ist (nicht minimiert).
 */
public final class WindowCapture {
   public static final int PW_RENDERFULLCONTENT = 2;
   private static final int WM_MOUSEWHEEL = 0x020A;

   private WindowCapture() {
   }

   public static boolean supported() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
   }

   /** Ergebnis: Pixel (ARGB) und Größe. */
   public record Frame(int[] argb, int width, int height, String title) {
   }

   /** Erstes sichtbares Fenster, dessen Titel {@code part} enthält (ohne Groß-/Kleinschreibung). */
   public static WinDef.HWND find(String part) {
      String needle = part.toLowerCase(Locale.ROOT);
      WinDef.HWND[] found = new WinDef.HWND[1];
      User32.INSTANCE.EnumWindows((WinUser.WNDENUMPROC)(hwnd, data) -> {
         if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
            return true;
         }

         char[] buf = new char[512];
         int n = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
         String title = new String(buf, 0, Math.max(0, n)).toLowerCase(Locale.ROOT);
         if (title.contains(needle) && !title.contains("minecraft")) {
            found[0] = hwnd;
            return false;
         }

         return true;
      }, null);
      return found[0];
   }

   /** Größtes sichtbares Fenster mit Titel, das zu einem der Prozesse gehört (z.B. dem selbst gestarteten Browser). */
   public static WinDef.HWND findByPid(java.util.Set<Long> pids) {
      WinDef.HWND[] found = new WinDef.HWND[1];
      int[] bestArea = {0};
      User32.INSTANCE.EnumWindows((WinUser.WNDENUMPROC)(hwnd, data) -> {
         if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
            return true;
         }

         com.sun.jna.ptr.IntByReference pid = new com.sun.jna.ptr.IntByReference();
         User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
         if (pids.contains((long)pid.getValue()) && !title(hwnd).isEmpty()) {
            WinDef.RECT r = new WinDef.RECT();
            if (User32.INSTANCE.GetWindowRect(hwnd, r)) {
               int area = (r.right - r.left) * (r.bottom - r.top);
               if (area > bestArea[0]) {
                  bestArea[0] = area;
                  found[0] = hwnd;
               }
            }
         }

         return true;
      }, null);
      return found[0];
   }
   public static String title(WinDef.HWND hwnd) {
      char[] buf = new char[512];
      int n = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
      return new String(buf, 0, Math.max(0, n));
   }

   /** Inhalt des Fensters (Client-Bereich) kopieren, oder null. */
   public static Frame grab(WinDef.HWND hwnd) {
      WinDef.RECT r = new WinDef.RECT();
      if (!User32.INSTANCE.GetWindowRect(hwnd, r)) {
         return null;
      }

      int w = r.right - r.left;
      int h = r.bottom - r.top;
      if (w < 50 || h < 50 || w > 8000 || h > 8000) {
         return null;
      }

      WinDef.HDC screen = User32.INSTANCE.GetDC(null);
      WinDef.HDC mem = GDI32.INSTANCE.CreateCompatibleDC(screen);
      WinDef.HBITMAP bmp = GDI32.INSTANCE.CreateCompatibleBitmap(screen, w, h);
      com.sun.jna.platform.win32.WinNT.HANDLE old = GDI32.INSTANCE.SelectObject(mem, bmp);

      try {
         if (!User32.INSTANCE.PrintWindow(hwnd, mem, PW_RENDERFULLCONTENT)) {
            return null;
         }

         WinGDI.BITMAPINFO info = new WinGDI.BITMAPINFO();
         info.bmiHeader.biWidth = w;
         // negative Höhe = Zeilen von oben nach unten
         info.bmiHeader.biHeight = -h;
         info.bmiHeader.biPlanes = 1;
         info.bmiHeader.biBitCount = 32;
         info.bmiHeader.biCompression = WinGDI.BI_RGB;
         Memory buf = new Memory((long)w * h * 4L);
         GDI32.INSTANCE.SelectObject(mem, old);
         if (GDI32.INSTANCE.GetDIBits(mem, bmp, 0, h, buf, info, WinGDI.DIB_RGB_COLORS) == 0) {
            return null;
         }

         int[] px = buf.getIntArray(0L, w * h);

         for (int i = 0; i < px.length; i++) {
            px[i] |= 0xFF000000;
         }

         return new Frame(px, w, h, title(hwnd));
      } finally {
         GDI32.INSTANCE.DeleteObject(bmp);
         GDI32.INSTANCE.DeleteDC(mem);
         User32.INSTANCE.ReleaseDC(null, screen);
      }
   }

   /** Mausrad an das Fenster schicken (z.B. nächstes Video). Klappt nicht bei jedem Browser. */
   public static void scroll(WinDef.HWND hwnd, int x, int y, boolean down) {
      WinDef.RECT r = new WinDef.RECT();
      User32.INSTANCE.GetWindowRect(hwnd, r);
      int sx = r.left + x;
      int sy = r.top + y;
      int delta = down ? -120 : 120;
      WinDef.WPARAM wp = new WinDef.WPARAM((long)(delta & 0xFFFF) << 16);
      WinDef.LPARAM lp = new WinDef.LPARAM((long)(sy & 0xFFFF) << 16 | (long)(sx & 0xFFFF));
      // an alle Kindfenster (der Browser-Inhalt ist ein eigenes Kindfenster)
      User32.INSTANCE.EnumChildWindows(hwnd, (child, data) -> {
         User32.INSTANCE.PostMessage(child, WM_MOUSEWHEEL, wp, lp);
         return true;
      }, null);
      User32.INSTANCE.PostMessage(hwnd, WM_MOUSEWHEEL, wp, lp);
   }
}
