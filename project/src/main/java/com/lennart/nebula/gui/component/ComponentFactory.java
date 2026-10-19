package com.lennart.nebula.gui.component;

import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ImageLibrarySetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.setting.TextSetting;

public final class ComponentFactory {
   private ComponentFactory() {
   }

   public static SettingComponent<?> create(Setting<?> setting, Runnable onChange) {
      return (SettingComponent<?>)(switch (setting) {
         case BooleanSetting s -> new ToggleComponent(s, onChange);
         case com.lennart.nebula.setting.HueSetting hs -> new HueComponent(hs, onChange);
         case NumberSetting sx -> new SliderComponent(sx, onChange);
         case com.lennart.nebula.setting.StyleSetting st -> new StyleGridComponent(st, onChange);
         case KeybindSetting sxx -> new KeybindComponent(sxx, onChange);
         case ModeSetting sxxx -> new ModeComponent(sxxx, onChange);
         case StringListSetting sxxxx -> new StringListComponent(sxxxx, onChange);
         case ColorSetting sxxxxx -> new ColorComponent(sxxxxx, onChange);
         case TextSetting sxxxxxx -> new TextComponent(sxxxxxx, onChange);
         case ActionSetting sxxxxxxx -> new ActionComponent(sxxxxxxx, onChange);
         case ImageLibrarySetting sxxxxxxxx -> new ImageLibraryComponent(sxxxxxxxx, onChange);
         default -> throw new IllegalArgumentException("Keine GUI-Komponente für " + setting.getClass().getSimpleName());
      });
   }
}
