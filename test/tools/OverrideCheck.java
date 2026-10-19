import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Findet Methoden der Mod, die eine Methode aus Minecraft/Fabric/Bibliotheken überschreiben bzw. implementieren.
 * Ausgabe: Klasse, Methodenname, Parametertypen (für @Override und die Mappings-Umstellung).
 */
public class OverrideCheck {
   public static void main(String[] a) throws Exception {
      Path classes = Path.of(a[0]);
      List<URL> urls = new ArrayList<>();
      urls.add(classes.toUri().toURL());

      for (String p : Files.readString(Path.of(a[1])).split(File.pathSeparator)) {
         if (!p.isBlank()) {
            urls.add(new File(p.trim()).toURI().toURL());
         }
      }

      URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), null);
      List<String> names = new ArrayList<>();

      try (Stream<Path> s = Files.walk(classes)) {
         s.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
            String rel = classes.relativize(p).toString().replace(File.separatorChar, '.');
            names.add(rel.substring(0, rel.length() - 6));
         });
      }

      for (String n : names) {
         if (n.contains(".mixin.")) {
            continue;
         }

         Class<?> c;
         try {
            c = Class.forName(n, false, cl);
         } catch (Throwable t) {
            System.out.println("# nicht ladbar: " + n + " " + t);
            continue;
         }

         Method[] declared;
         try {
            declared = c.getDeclaredMethods();
         } catch (Throwable t) {
            System.out.println("# Methoden nicht lesbar: " + n + " " + t);
            continue;
         }

         for (Method m : declared) {
            if (m.isSynthetic() || m.isBridge() || Modifier.isStatic(m.getModifiers()) || Modifier.isPrivate(m.getModifiers())) {
               continue;
            }

            Class<?> owner = findOverridden(c, m);
            if (owner != null && !owner.getName().startsWith("com.lennart.") && !owner.getName().startsWith("java.")) {
               System.out.println(c.getName() + "\t" + m.getName() + "\t" + Arrays.toString(m.getParameterTypes()) + "\t" + owner.getName());
            }
         }
      }
   }

   private static Class<?> findOverridden(Class<?> c, Method m) {
      List<Class<?>> todo = new ArrayList<>();
      if (c.getSuperclass() != null) {
         todo.add(c.getSuperclass());
      }

      todo.addAll(Arrays.asList(c.getInterfaces()));

      while (!todo.isEmpty()) {
         Class<?> k = todo.remove(0);

         try {
            for (Method sm : k.getDeclaredMethods()) {
               if (sm.getName().equals(m.getName()) && sm.getParameterCount() == m.getParameterCount() && !Modifier.isPrivate(sm.getModifiers()) && !Modifier.isStatic(sm.getModifiers())) {
                  Class<?>[] a = sm.getParameterTypes();
                  Class<?>[] b = m.getParameterTypes();
                  boolean same = true;

                  for (int i = 0; i < a.length; i++) {
                     if (!a[i].isAssignableFrom(b[i]) && a[i] != Object.class) {
                        same = false;
                     }
                  }

                  if (same) {
                     return k;
                  }
               }
            }

            if (k.getSuperclass() != null) {
               todo.add(k.getSuperclass());
            }

            todo.addAll(Arrays.asList(k.getInterfaces()));
         } catch (Throwable var9) {
         }
      }

      return null;
   }
}
