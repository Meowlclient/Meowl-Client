import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Prüft, ob alle Mixin-Ziele (Klassen, Methoden, @At-Ziele) im Minecraft-Jar (intermediary) existieren. */
public class MixinCheck {
   static Map<String, ClassNode> mc = new HashMap<>();

   static ClassNode load(ZipFile z, String name) throws IOException {
      ZipEntry e = z.getEntry(name + ".class");
      if (e == null) return null;
      ClassNode n = new ClassNode();
      new ClassReader(z.getInputStream(e)).accept(n, ClassReader.SKIP_CODE);
      return n;
   }

   public static void main(String[] a) throws Exception {
      ZipFile mod = new ZipFile(a[0]);
      ZipFile game = new ZipFile(a[1]);
      int targets = 0, errors = 0;
      for (Enumeration<? extends ZipEntry> en = mod.entries(); en.hasMoreElements();) {
         ZipEntry e = en.nextElement();
         if (!e.getName().contains("/mixin/") || !e.getName().endsWith(".class")) continue;
         ClassNode cn = new ClassNode();
         new ClassReader(mod.getInputStream(e)).accept(cn, 0);
         List<String> owners = new ArrayList<>();
         if (cn.invisibleAnnotations != null) for (AnnotationNode an : cn.invisibleAnnotations) collectTargets(an, owners);
         if (cn.visibleAnnotations != null) for (AnnotationNode an : cn.visibleAnnotations) collectTargets(an, owners);
         for (String o : owners) {
            targets++;
            if (load(game, o) == null) { System.out.println("FEHLT Klasse " + o + " (" + cn.name + ")"); errors++; }
         }
         for (MethodNode m : cn.methods) {
            List<AnnotationNode> anns = new ArrayList<>();
            if (m.visibleAnnotations != null) anns.addAll(m.visibleAnnotations);
            if (m.invisibleAnnotations != null) anns.addAll(m.invisibleAnnotations);
            for (AnnotationNode an : anns) {
               if (!an.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !an.desc.contains("Accessor") && !an.desc.contains("Invoker")) continue;
               List<String> methods = strings(an, "method");
               for (String o : owners) {
                  ClassNode target = load(game, o);
                  if (target == null) continue;
                  for (String sel : methods) {
                     targets++;
                     if (!hasMethod(game, target, sel)) { System.out.println("FEHLT Methode " + o + "." + sel + " (" + cn.name + "." + m.name + ")"); errors++; continue; }
                     if (an.desc.endsWith("/Inject;") && !injectFits(game, target, sel, m.desc)) {
                        System.out.println("PARAMETER passen nicht " + o + "." + sel + " <- " + cn.name + "." + m.name + m.desc + "  Ziel: " + descs(game, target, sel));
                        errors++;
                     }
                  }
               }
               if (an.desc.endsWith("/Invoker;") || an.desc.endsWith("/Accessor;")) {
                  // Ziel-Name aus value oder aus dem Methodennamen
                  List<String> v = strings(an, "value");
                  String name = v.isEmpty() ? m.name.replaceFirst("^(invoke|call|get|set|is)", "") : v.get(0);
                  if (an.desc.endsWith("/Invoker;")) {
                     for (String o : owners) {
                        ClassNode target = load(game, o);
                        if (target == null) continue;
                        targets++;
                        String want = m.desc.substring(0, m.desc.indexOf(')') + 1);
                        boolean ok = false;
                        for (String d : descs(game, target, name)) if (d.startsWith(want)) ok = true;
                        if (!ok) { System.out.println("INVOKER passt nicht " + o + "." + name + m.desc + "  vorhanden: " + descs(game, target, name)); errors++; }
                     }
                  }
               }
               for (Object at : list(an, "at")) {
                  AnnotationNode atn = (AnnotationNode) at;
                  for (String t : strings(atn, "target")) {
                     targets++;
                     if (!memberExists(game, t)) { System.out.println("FEHLT @At-Ziel " + t + " (" + cn.name + "." + m.name + ")"); errors++; }
                  }
               }
            }
         }
      }
      System.out.println(errors == 0 ? "ALLE " + targets + " MIXIN-ZIELE GEFUNDEN" : errors + " FEHLER bei " + targets + " Zielen");
      System.exit(errors == 0 ? 0 : 1);
   }

   static void collectTargets(AnnotationNode an, List<String> out) {
      if (!an.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return;
      for (Object v : list(an, "value")) out.add(((Type) v).getInternalName());
      for (String s : strings(an, "targets")) out.add(s.replace('.', '/'));
   }

   static List<Object> list(AnnotationNode an, String key) {
      if (an.values == null) return List.of();
      for (int i = 0; i < an.values.size(); i += 2) if (an.values.get(i).equals(key)) {
         Object v = an.values.get(i + 1);
         return v instanceof List<?> l ? new ArrayList<>(l) : List.of(v);
      }
      return List.of();
   }

   static List<String> strings(AnnotationNode an, String key) {
      List<String> out = new ArrayList<>();
      for (Object o : list(an, key)) if (o instanceof String s) out.add(s);
      return out;
   }

   static boolean hasMethod(ZipFile game, ClassNode c, String sel) throws IOException {
      String name = sel, desc = null;
      int p = sel.indexOf('(');
      if (p >= 0) { name = sel.substring(0, p); desc = sel.substring(p); }
      Deque<ClassNode> todo = new ArrayDeque<>(List.of(c));
      Set<String> seen = new HashSet<>();
      while (!todo.isEmpty()) {
         ClassNode cur = todo.pop();
         if (!seen.add(cur.name)) continue;
         for (MethodNode m : cur.methods) if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) return true;
         List<String> parents = new ArrayList<>(cur.interfaces);
         if (cur.superName != null) parents.add(cur.superName);
         for (String p2 : parents) { ClassNode n = load(game, p2); if (n != null) todo.push(n); }
      }
      return false;
   }

   static List<String> descs(ZipFile game, ClassNode c, String sel) throws IOException {
      String name = sel.contains("(") ? sel.substring(0, sel.indexOf('(')) : sel;
      String only = sel.contains("(") ? sel.substring(sel.indexOf('(')) : null;
      List<String> out = new ArrayList<>();
      Deque<ClassNode> todo = new ArrayDeque<>(List.of(c));
      Set<String> seen = new HashSet<>();
      while (!todo.isEmpty()) {
         ClassNode cur = todo.pop();
         if (!seen.add(cur.name)) continue;
         for (MethodNode m : cur.methods) if (m.name.equals(name) && (only == null || m.desc.equals(only))) out.add(m.desc);
         if (cur.superName != null) { ClassNode n = load(game, cur.superName); if (n != null) todo.push(n); }
      }
      return out;
   }

   /** @Inject-Handler: erst die Ziel-Parameter, dann CallbackInfo, danach evtl. eingefangene Locals. */
   static boolean injectFits(ZipFile game, ClassNode c, String sel, String handler) throws IOException {
      Type[] h = Type.getArgumentTypes(handler);
      for (String d : descs(game, c, sel)) {
         Type[] t = Type.getArgumentTypes(d);
         if (h.length == 0) return true;
         if (h.length < t.length + 1) {
            // Handler darf auch nur CallbackInfo haben
            if (h.length == 1 && h[0].getInternalName().startsWith("org/spongepowered/")) return true;
            continue;
         }
         boolean ok = true;
         for (int i = 0; i < t.length; i++) {
            if (t[i].getSort() != h[i].getSort()) { ok = false; break; }
            if (t[i].getSort() == Type.OBJECT && !t[i].equals(h[i]) && !isSuper(game, h[i].getInternalName(), t[i].getInternalName())) { ok = false; break; }
         }
         if (ok && h[t.length].getInternalName().startsWith("org/spongepowered/")) return true;
         if (h.length == 1 && h[0].getInternalName().startsWith("org/spongepowered/")) return true;
      }
      return false;
   }

   static boolean isSuper(ZipFile game, String sup, String sub) throws IOException {
      // generische Parameter werden zu ihrer Grenze gelöscht; Handler darf einen Obertyp nehmen
      ClassNode cur = load(game, sub);
      while (cur != null) {
         if (cur.name.equals(sup) || cur.interfaces.contains(sup)) return true;
         cur = cur.superName == null ? null : load(game, cur.superName);
      }
      return sup.equals("java/lang/Object");
   }

   static boolean memberExists(ZipFile game, String t) throws IOException {
      // Formen: Lowner;name(desc)ret  oder  Lowner;name:Ltype;
      if (!t.startsWith("L") || !t.contains(";")) return true;
      String owner = t.substring(1, t.indexOf(';'));
      String rest = t.substring(t.indexOf(';') + 1);
      if (owner.startsWith("java/") || owner.startsWith("com/mojang/") || owner.startsWith("org/")) return true;
      ClassNode c = load(game, owner);
      if (c == null) return false;
      if (rest.contains(":")) {
         String fname = rest.substring(0, rest.indexOf(':'));
         ClassNode cur = c;
         while (cur != null) {
            for (FieldNode f : cur.fields) if (f.name.equals(fname)) return true;
            cur = cur.superName == null ? null : load(game, cur.superName);
         }
         return false;
      }
      return hasMethod(game, c, rest);
   }
}
