import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.*;
import com.lennart.nebula.module.mods.*;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.setting.Setting;
import java.util.*;

public class Smoke {
   public static void main(String[] a) throws Exception {
      net.minecraft.SharedConstants.createGameVersion();
      net.minecraft.Bootstrap.initialize();
      List<Module> mods = new ArrayList<>();
      for (String cls : a) {
         try { mods.add((Module) Class.forName(cls).getDeclaredConstructor().newInstance()); System.out.println("OK  " + cls); }
         catch (Throwable t) { System.out.println("ERR " + cls + ": " + t); Throwable c = t.getCause(); if (c != null) { System.out.println("    " + c); for (StackTraceElement e : c.getStackTrace()) if (e.getClassName().startsWith("com.lennart")) System.out.println("      at " + e); } }
      }
      for (Module m : mods) {
         Set<String> names = new HashSet<>();
         for (Setting<?> s : m.getSettings()) if (!names.add(s.getName())) System.out.println("DOPPELT in " + m.getName() + ": " + s.getName());
      }
      CommandDispatcher<FabricClientCommandSource> d = new CommandDispatcher<>();
      ClientCommandRegistrationCallback.EVENT.invoker().register(d, null);
      var root = d.getRoot().getChild("meowl");
      System.out.println("Root-Befehle: " + d.getRoot().getChildren().stream().map(n -> n.getName()).toList());
      System.out.println("/meowl: " + (root == null ? "FEHLT" : root.getChildren().stream().map(n -> n.getName()).toList()));
   }
}
