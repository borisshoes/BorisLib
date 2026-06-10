package net.borisshoes.borislib.config;

/**
 * Interface for configuration settings that can be registered and managed by {@link ConfigManager}.
 *
 * <p>Implementations typically wrap a {@link ConfigValue} instance and provide methods for
 * ID generation and name access. The most common implementation is {@link ConfigSetting}.
 *
 * <h3>Example:</h3>
 * <pre>{@code
 * public static final IConfigSetting<?> MAX_POWER = register("maxPower",
 *     new IntConfigValue("maxPower", 100)
 * );
 *
 * private static IConfigSetting<?> register(String id, ConfigValue<?> value) {
 *     ConfigSetting<?> setting = new ConfigSetting<>(value);
 *     Registry.register(SETTINGS, Identifier.of("mymod", id), setting);
 *     return setting;
 * }
 * }</pre>
 *
 * @param <T> the type of value this setting holds
 * @see ConfigSetting The standard implementation
 * @see ConfigValue The underlying value container
 * @see ConfigManager The manager that uses these settings
 */
public interface IConfigSetting<T> {
   /**
    * Creates or returns the {@link ConfigValue} instance for this setting.
    *
    * @return the config value
    */
   ConfigValue<T> makeConfigValue();
   
   /**
    * Gets the registry ID for this setting (typically snake_case).
    *
    * @return the ID
    */
   String getId();
   
   /**
    * Gets the name of this setting (typically camelCase, matches ConfigValue name).
    *
    * @return the name
    */
   String getName();
}