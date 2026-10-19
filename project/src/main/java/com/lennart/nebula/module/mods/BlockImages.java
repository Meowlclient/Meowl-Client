package com.lennart.nebula.module.mods;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.images.ImageCache;
import com.lennart.nebula.images.ImageGeometry;
import com.lennart.nebula.images.ImageManager;
import com.lennart.nebula.images.ImageProviders;
import com.lennart.nebula.images.ImageRenderer;
import com.lennart.nebula.images.PlacedImage;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.hud.HudBox;
import com.lennart.nebula.module.hud.HudLayout;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ImageLibrarySetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.InputBlocker;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.lennart.nebula.util.WorldCtx;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class BlockImages extends Module {
   private final ImageLibrarySetting library = this.add(
      new ImageLibrarySetting("Bilder", "Deine Bilder: Link, Größe in Blöcken, sichtbar/ausgeblendet. Der Kreis wählt das Bild zum Platzieren.")
   );
   private final TextSetting legacyUrl = this.add(new TextSetting("Bild-URL", "", "", 512));
   private final NumberSetting legacyWidth = this.add(new NumberSetting("Breite", "", 2.0, 1.0, 32.0, 1.0, ""));
   private final NumberSetting legacyHeight = this.add(new NumberSetting("Höhe", "", 2.0, 1.0, 32.0, 1.0, ""));
   private final BooleanSetting keepAspect = this.add(new BooleanSetting("Seitenverhältnis erhalten", "Bild nicht verzerren (Rand bleibt frei)", true));
   private final NumberSetting renderDistance = this.add(
      new NumberSetting("Render-Distanz", "Bilder nur bis zu dieser Entfernung zeichnen", 64.0, 16.0, 256.0, 8.0, "Blöcke")
   );
   private final KeybindSetting placeKey = this.add(new KeybindSetting("Platzier-Modus-Taste", "Schaltet den Platzier-Modus an/aus", Bind.NONE));
   private final KeybindSetting nextKey = this.add(new KeybindSetting("Nächstes Bild", "Wechselt das ausgewählte Bild (auch im Platzier-Modus)", Bind.NONE));
   private final BooleanSetting hudShow = this.add(new BooleanSetting("Bild im HUD", "Zeigt ein Bild aus deiner Bibliothek dauerhaft im HUD", false));
   private final NumberSetting hudSlot = this.add(
      new NumberSetting("HUD-Bild Nr.", "Welches Bild aus der Liste (0 = das gerade ausgewählte)", 0.0, 0.0, 30.0, 1.0, "")
   );
   private final NumberSetting hudWidth = this.add(new NumberSetting("HUD-Bild Breite", "Breite im HUD (Höhe passt sich an)", 96.0, 24.0, 320.0, 4.0, "px"));
   private final HudBox hudBox = this.hudBox("Block Image (HUD)", 98.0, 4.0);
   private final ImageCache cache = new ImageCache(FabricLoader.getInstance().getConfigDir().resolve("meowl-image-cache"));
   private final ImageManager manager = new ImageManager(FabricLoader.getInstance().getConfigDir().resolve("meowl-images-local.json"));
   private final ImageRenderer renderer = new ImageRenderer(this.cache);
   private boolean placing;
   private boolean lastKey;
   private boolean lastNext;
   private Set<String> hiddenUrls = Set.of();
   private ImageLibrarySetting.Library hiddenFor;

   public BlockImages() {
      super("Block Images", "Bilder (z.B. Imgur) an Blockwände hängen – für alle Meowl-Spieler sichtbar. Ein Bild pro Spieler (ein neues ersetzt das alte), verschwindet beim Verlassen oder außer Sichtweite.", Category.WORLD);
      this.setEnabled(true);
      this.section(Section.MEOWL);
      this.icon(Items.PAINTING);
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.removeAllOwn());
      this.add(new ActionSetting("Platzier-Modus", "An: Rechtsklick auf eine Wand platziert das Bild", "An/Aus", this::togglePlacing));
      this.legacyUrl.setVisibility(() -> false);
      this.legacyWidth.setVisibility(() -> false);
      this.legacyHeight.setVisibility(() -> false);
      this.hudBox.background.set(false);
      this.group(
         "HUD",
         new Setting[]{this.hudShow, this.hudSlot, this.hudWidth, this.hudBox.scale, this.hudBox.opacity, this.hudBox.background, this.hudBox.backgroundColor, this.hudBox.rounding}
      );

      for (Setting<?> s : new Setting[]{this.hudSlot, this.hudWidth, this.hudBox.scale, this.hudBox.opacity, this.hudBox.background}) {
         s.setVisibility(this.hudShow::get);
      }

      this.hudBox.backgroundColor.setVisibility(() -> this.hudShow.get() && this.hudBox.background.get());
      this.hudBox.rounding.setVisibility(() -> this.hudShow.get() && this.hudBox.background.get());
      this.library.setPreview((link, status, size) -> {
         String direct;
         try {
            direct = ImageProviders.resolve(link).toString();
         } catch (IllegalArgumentException var6) {
            status.append(var6.getMessage());
            return null;
         }

         ImageCache.Entry entry = this.cache.get(direct);
         switch (entry.state()) {
            case LOADING:
               status.append("lädt …");
               break;
            case FAILED:
               status.append("Fehler: ").append(entry.error());
               break;
            case READY:
               status.append("OK · ").append(entry.width()).append("×").append(entry.height());
               size[0] = entry.width();
               size[1] = entry.height();
         }

         return entry.texture();
      });
      InputBlocker.blockUseWhen(() -> this.isEnabled() && this.placing);
      InputBlocker.blockAttackWhen(() -> this.isEnabled() && this.placing);
   }

   private void togglePlacing() {
      this.placing = !this.placing;
      ChatUtil.actionBar("Block Images: Platzier-Modus " + (this.placing ? "§aAN §7(Rechtsklick = platzieren, Linksklick = eigenes Bild entfernen)" : "§cAUS"));
      if (this.mc.screen != null && this.placing) {
         this.mc.setScreen(null);
      }
   }

   @Override
   protected void onDisable() {
      this.placing = false;
      this.cache.clear();
   }

   @Override
   public void onConfigLoaded() {
      this.migrate();
   }

   private void migrate() {
      if (!this.legacyUrl.get().isBlank()) {
         if (this.library.get().slots().stream().noneMatch(s -> s.url().equals(this.legacyUrl.get().trim()))) {
            this.library.add(new ImageLibrarySetting.Slot(this.legacyUrl.get(), this.legacyWidth.getInt(), this.legacyHeight.getInt(), true));
         }

         this.legacyUrl.set("");
      }
   }

   /** Direkte Links aller gültigen Bilder in der Liste (z.B. zum Verschicken im Freunde-Chat). */
   public List<String> libraryUrls() {
      List<String> out = new java.util.ArrayList<>();

      for (ImageLibrarySetting.Slot s : this.library.get().slots()) {
         if (ImageProviders.isValid(s.url())) {
            out.add(ImageProviders.resolve(s.url()).toString());
         }
      }

      return out;
   }

   /** Bild aus Meowl Paint in die Liste aufnehmen und auswählen. */
   public boolean addPainted(String url, int w, int h) {
      if (!this.library.add(new ImageLibrarySetting.Slot(url, w, h, true))) {
         return false;
      } else {
         this.library.select(this.library.get().slots().size() - 1);
         NebulaClient.getInstance().getModuleManager().notifyChanged();
         return true;
      }
   }

   private ImageLibrarySetting.Slot current() {
      return this.library.get().current();
   }

   private void nextImage() {
      int n = this.library.get().slots().size();
      if (n == 0) {
         ChatUtil.actionBar("§cNoch keine Bilder – im Menü unter Block Images hinzufügen");
      } else {
         this.library.select((this.library.get().selected() + 1) % n);
         ImageLibrarySetting.Slot s = this.current();
         ChatUtil.actionBar("Bild " + (this.library.get().selected() + 1) + "/" + n + " · " + s.w() + "×" + s.h());
      }
   }

   private void updateHidden() {
      if (this.hiddenFor != this.library.get()) {
         this.hiddenFor = this.library.get();
         Set<String> set = new HashSet<>();

         for (ImageLibrarySetting.Slot s : this.hiddenFor.slots()) {
            if (!s.visible() && ImageProviders.isValid(s.url())) {
               set.add(ImageProviders.resolve(s.url()).toString());
            }
         }

         this.hiddenUrls = set;
      }
   }

   @Override
   public void onPreTick() {
      boolean next = this.nextKey.get().isPressed();
      if (next && !this.lastNext && this.mc.screen == null && this.mc.player != null) {
         this.nextImage();
      }

      this.lastNext = next;
      boolean key = this.placeKey.get().isPressed();
      if (key && !this.lastKey && this.mc.screen == null && this.mc.player != null) {
         this.togglePlacing();
      }

      this.lastKey = key;
      if (this.placing && this.mc.player != null && this.mc.screen == null) {
         boolean use = false;
         boolean attack = false;

         while (this.mc.options.keyUse.consumeClick()) {
            use = true;
         }

         while (this.mc.options.keyAttack.consumeClick()) {
            attack = true;
         }

         if (use) {
            this.place();
         }

         if (attack) {
            this.removeLookedAt();
         }
      }
   }

   @Override
   public void onTick() {
      this.manager.tick();
      this.cache.tick();
      if (this.mc.player != null && this.mc.level != null && this.mc.player.tickCount % 20 == 0) {
         this.cleanupOwn();
      }
   }

   /** Eigene Bilder verschwinden, sobald du außer Sichtweite (Render-Distanz) bist oder die Dimension wechselst. */
   private void cleanupOwn() {
      String me = MeowlService.get().myUuid() != null ? MeowlService.get().myUuid() : this.mc.player.getStringUUID();
      String dim = this.mc.level.dimension().identifier().toString();
      double max = this.mc.options.getEffectiveRenderDistance() * 16.0 + 16.0;

      for (PlacedImage image : this.manager.snapshot()) {
         if (image.creator().equals(me) || image.local()) {
            boolean gone = !image.dim().equals(dim)
               || this.mc.player.position().distanceToSqr(image.pos().getX() + 0.5, image.pos().getY() + 0.5, image.pos().getZ() + 0.5) > max * max;
            if (gone) {
               this.manager.remove(image, msg -> {});
            }
         }
      }
   }

   /** Beim Verlassen des Servers: alle eigenen Bilder dort löschen. */
   public void removeAllOwn() {
      if (this.mc.player != null) {
         String me = MeowlService.get().myUuid() != null ? MeowlService.get().myUuid() : this.mc.player.getStringUUID();

         for (PlacedImage image : this.manager.snapshot()) {
            if (image.creator().equals(me) || image.local()) {
               this.manager.remove(image, msg -> {});
            }
         }
      }
   }

   private BlockHitResult target() {
      HitResult hit = this.mc.hitResult;
      return hit instanceof BlockHitResult bhr && hit.getType() == Type.BLOCK ? bhr : null;
   }

   private void place() {
      BlockHitResult hit = this.target();
      if (hit == null) {
         ChatUtil.actionBar("§cZiele auf eine Blockseite");
      } else if (hit.getDirection().getAxis().isVertical()) {
         ChatUtil.actionBar("§cBilder gehen nur an senkrechte Wände");
      } else {
         ImageLibrarySetting.Slot slot = this.current();
         if (slot != null && !slot.url().isBlank()) {
            String direct;
            try {
               direct = ImageProviders.resolve(slot.url()).toString();
            } catch (IllegalArgumentException var6) {
               ChatUtil.actionBar("§c" + var6.getMessage());
               return;
            }

            String me = MeowlService.get().myUuid() != null ? MeowlService.get().myUuid() : this.mc.player.getStringUUID();
            PlacedImage draft = new PlacedImage(
               "",
               direct,
               this.manager.server(),
               this.mc.level.dimension().identifier().toString(),
               hit.getBlockPos(),
               hit.getDirection(),
               slot.w(),
               slot.h(),
               me,
               false
            );
            this.manager.place(draft, ChatUtil::actionBar);
         } else {
            ChatUtil.actionBar("§cKein Bild ausgewählt – im Menü unter Block Images ein Bild hinzufügen");
         }
      }
   }

   private void removeLookedAt() {
      Vec3 eye = this.mc.player.getEyePosition(1.0F);
      Vec3 dir = this.mc.player.getViewVector(1.0F);
      String dim = this.mc.level.dimension().identifier().toString();
      PlacedImage best = null;
      double bestT = Double.MAX_VALUE;

      for (PlacedImage image : this.manager.all()) {
         if (image.dim().equals(dim)) {
            double t = ImageGeometry.of(image.pos(), image.facing(), image.w(), image.h()).ray(eye, dir, 12.0);
            if (t >= 0.0 && t < bestT) {
               bestT = t;
               best = image;
            }
         }
      }

      if (best != null) {
         String me = MeowlService.get().myUuid() != null ? MeowlService.get().myUuid() : this.mc.player.getStringUUID();
         if (!best.local() && !best.creator().equals(me)) {
            ChatUtil.actionBar("§cNur der Ersteller kann dieses Bild entfernen");
         } else {
            this.manager.remove(best, ChatUtil::actionBar);
         }
      }
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      if (this.mc.level != null) {
         String dim = this.mc.level.dimension().identifier().toString();
         this.updateHidden();
         Collection<PlacedImage> all = this.manager.all();
         Iterable<PlacedImage> visible = (Iterable<PlacedImage>)(this.hiddenUrls.isEmpty()
            ? all
            : all.stream().filter(i -> !this.hiddenUrls.contains(i.url())).toList());
         this.renderer.render(context, visible, dim, this.renderDistance.get(), this.keepAspect.get());
         if (this.placing) {
            BlockHitResult hit = this.target();
            ImageLibrarySetting.Slot slot = this.current();
            if (hit != null && !hit.getDirection().getAxis().isVertical() && slot != null) {
               ImageGeometry g = ImageGeometry.of(hit.getBlockPos(), hit.getDirection(), slot.w(), slot.h());
               this.renderer.renderPreview(context, g, ImageProviders.isValid(slot.url()));
            }
         }
      }
   }

   private ImageLibrarySetting.Slot hudImage() {
      List<ImageLibrarySetting.Slot> slots = this.library.get().slots();
      int n = this.hudSlot.getInt();
      if (n <= 0) {
         return this.current();
      } else {
         return n <= slots.size() ? slots.get(n - 1) : null;
      }
   }

   private void renderHudImage(GuiGraphics ctx) {
      boolean editing = HudLayout.isEditing();
      ImageLibrarySetting.Slot slot = this.hudImage();
      ImageCache.Entry entry = null;
      if (slot != null && ImageProviders.isValid(slot.url())) {
         entry = this.cache.get(ImageProviders.resolve(slot.url()).toString());
      }

      boolean ready = entry != null && entry.state() == ImageCache.State.READY && entry.texture() != null && entry.width() > 0;
      if (ready || editing) {
         int w = this.hudWidth.getInt();
         int h = ready ? Math.max(1, Math.round((float)w * entry.height() / entry.width())) : w * 3 / 4;
         this.hudBox.begin(ctx, w, h);
         if (ready) {
            int color = this.hudBox.fade(-1);
            ctx.blit(RenderPipelines.GUI_TEXTURED, entry.texture(), 0, 0, 0.0F, 0.0F, w, h, entry.width(), entry.height(), entry.width(), entry.height(), color);
         } else {
            ctx.fill(0, 0, w, h, 1347440720);
            String t = slot == null ? "kein Bild" : (entry != null && entry.state() == ImageCache.State.FAILED ? "Fehler" : "lädt …");
            ctx.drawString(this.mc.font, t, (w - this.mc.font.width(t)) / 2, h / 2 - 4, -7564628, false);
         }

         this.hudBox.end(ctx);
      }
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      if (this.hudShow.get() && (!this.mc.options.hideGui || HudLayout.isEditing())) {
         this.renderHudImage(ctx);
      }

      if (this.placing && this.mc.screen == null) {
         ImageLibrarySetting.Slot slot = this.current();
         String text = "Platzier-Modus · "
            + (
               slot == null
                  ? "kein Bild"
                  : "Bild " + (this.library.get().selected() + 1) + "/" + this.library.get().slots().size() + " · " + slot.w() + "×" + slot.h()
            )
            + (MeowlService.get().isConnected() ? " · geteilt" : " · nur lokal");
         ctx.drawString(
            this.mc.font,
            text,
            (ctx.guiWidth() - this.mc.font.width(text)) / 2,
            ctx.guiHeight() / 2 + 14,
            -3885571,
            true
         );
      }
   }
}
