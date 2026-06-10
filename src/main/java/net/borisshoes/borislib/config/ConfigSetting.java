package net.borisshoes.borislib.config;

import net.borisshoes.borislib.utils.TextUtils;

import java.util.Objects;

/**
 * Standard implementation of {@link IConfigSetting} that wraps a {@link ConfigValue}.
 *
 * <p>This record-based implementation provides automatic ID generation by converting
 * the setting name from camelCase to snake_case.
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Create a config setting
 * IntConfigValue maxPowerValue = new IntConfigValue("maxPower", 100);
 * ConfigSetting<Integer> maxPowerSetting = new ConfigSetting<>(maxPowerValue);
 *
 * // Register it
 * Registry.register(SETTINGS, Identifier.of("mymod", "max_power"), maxPowerSetting);
 * }</pre>
 *
 * @param <T> the type of value this setting holds
 * @param setting the underlying config value
 * @see IConfigSetting The interface this implements
 * @see ConfigValue The value container
 */
public record ConfigSetting<T>(ConfigValue<T> setting) implements IConfigSetting<T> {
   /**
    * Creates a new config setting.
    *
    * @param setting the config value (must not be null)
    * @throws NullPointerException if setting is null
    */
   public ConfigSetting(ConfigValue<T> setting){
      this.setting = Objects.requireNonNull(setting);
   }
   
   public ConfigValue<T> makeConfigValue(){
      return setting;
   }
   
   public String getId(){
      return TextUtils.camelToSnake(setting.getName());
   }
   
   public String getName(){
      return setting.getName();
   }
}