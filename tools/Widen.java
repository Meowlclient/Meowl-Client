import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.accesswidener.*;
import org.objectweb.asm.*;

/** Widen in.jar out.jar file.accesswidener – wendet die (transitiven) Access Widener der Fabric API für den Compile-Classpath an. */
public class Widen {
   public static void main(String[] a) throws Exception {
      AccessWidener aw = new AccessWidener();
      new AccessWidenerReader(aw).read(Files.readAllBytes(Paths.get(a[2])), "named");
      Set<String> targets = aw.getTargets();
      try (ZipInputStream in = new ZipInputStream(Files.newInputStream(Paths.get(a[0])));
           ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Paths.get(a[1])))) {
         ZipEntry e;
         while ((e = in.getNextEntry()) != null) {
            byte[] b = in.readAllBytes();
            String n = e.getName();
            if (n.endsWith(".class") && targets.contains(n.substring(0, n.length() - 6).replace('/', '.'))) {
               ClassReader cr = new ClassReader(b);
               ClassWriter cw = new ClassWriter(0);
               cr.accept(AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, cw, aw), 0);
               b = cw.toByteArray();
            }
            out.putNextEntry(new ZipEntry(n));
            out.write(b);
            out.closeEntry();
         }
      }
   }
}
