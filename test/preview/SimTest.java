import com.lennart.nebula.afk.space.*;
public class SimTest {
   public static void main(String[] a) {
      ShipSprites ss = new ShipSprites(Preview.TEX); ss.bakeAll();
      for (int d = 0; d < 4; d++) {
         SpaceBattle b = new SpaceBattle(ss, d, d);
         int[] ev = new int[5];
         b.setEvents(new SpaceBattle.Events() {
            public void explosion(float dd, float s) { ev[0]++; } public void shot(float dd, byte f, boolean c) { ev[1]++; }
            public void flyby() { ev[2]++; } public void hullHit() { ev[3]++; } public void jump(float dd) { ev[4]++; } });
         int maxB = 0, maxBl = 0;
         for (int i = 0; i < 20 * 60 * 10; i++) { b.update(0.05f); maxB = Math.max(maxB, b.bolts.size()); maxBl = Math.max(maxBl, b.blasts.size()); }
         System.out.printf("Dichte %d: Schiffe %d Jäger %d maxBolts %d maxBlasts %d Wracks %d | Expl %d Schüsse %d Flybys %d Treffer %d Sprünge %d%n",
            d, b.ships.size(), b.fighters.size(), maxB, maxBl, b.wrecks.size(), ev[0], ev[1], ev[2], ev[3], ev[4]);
      }
   }
}
