import java.nio.file.*;
import java.util.*;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

/** Remap in out mappings from to classpath... (tiny-remapper mit Mixin-Erweiterung, wie Loom "static"). */
public class Remap {
   public static void main(String[] a) throws Exception {
      Path in = Paths.get(a[0]), out = Paths.get(a[1]), map = Paths.get(a[2]);
      TinyRemapper r = TinyRemapper.newRemapper()
         .withMappings(TinyUtils.createTinyMappingProvider(map, a[3], a[4]))
         .extension(new MixinExtension())
         .renameInvalidLocals(false)
         .rebuildSourceFilenames(true)
         .build();
      Files.deleteIfExists(out);
      try (OutputConsumerPath o = new OutputConsumerPath.Builder(out).build()) {
         o.addNonClassFiles(in, NonClassCopyMode.FIX_META_INF, r);
         for (int i = 5; i < a.length; i++) r.readClassPath(Paths.get(a[i]));
         r.readInputs(in);
         r.apply(o);
      } finally {
         r.finish();
      }
   }
}
