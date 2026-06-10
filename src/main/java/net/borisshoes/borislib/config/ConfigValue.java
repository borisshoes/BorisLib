package net.borisshoes.borislib.config;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.utils.TextUtils;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Abstract base class for all configuration values.
 *
 * <p>Each config value type (int, string, boolean, etc.) extends this class and implements
 * the abstract methods for parsing, command integration, and serialization.
 *
 * <p>Config values support:
 * <ul>
 *   <li><b>Default values</b> — Used when the config file is missing or corrupted</li>
 *   <li><b>Validation</b> — Optional {@link Validator} to enforce constraints</li>
 *   <li><b>Callbacks</b> — OnSet callback executed when value changes</li>
 *   <li><b>Command integration</b> — Automatic command argument parsing and suggestions</li>
 * </ul>
 *
 * <h3>Example Subclass:</h3>
 * <pre>{@code
 * public class IntConfigValue extends ConfigValue<Integer> {
 *     // Implementation...
 * }
 * }</pre>
 *
 * @param <T> the type of value this config holds
 * @see IntConfigValue For integer settings
 * @see BooleanConfigValue For boolean settings
 * @see StringConfigValue For string settings
 * @see Validator For value validation
 */
public abstract class ConfigValue<T> {
   protected final T defaultValue;
   protected final String name;
   protected T value;
   protected Consumer<T> onSet;
   protected Validator<T> validator;
   
   /**
    * Full constructor with all options.
    *
    * @param name the setting name (used in config file and commands)
    * @param defaultValue the default value (used when file is missing or value is invalid)
    * @param onSet optional callback executed when the value changes (can be null)
    * @param validator optional validator to enforce constraints (can be null)
    */
   public ConfigValue(@NotNull String name, T defaultValue, Consumer<T> onSet, Validator<T> validator){
      this.name = name;
      this.defaultValue = defaultValue;
      this.onSet = onSet;
      this.validator = validator;
   }
   
   /**
    * Simple constructor with just name and default value.
    *
    * @param name the setting name
    * @param defaultValue the default value
    */
   public ConfigValue(@NotNull String name, T defaultValue){
      this(name, defaultValue, null, null);
   }
   
   /**
    * Gets the setting name.
    *
    * @return the name
    */
   public String getName(){
      return name;
   }
   
   /**
    * Parses a string value from the config file into the appropriate type.
    *
    * <p>Implementations should handle parsing errors gracefully and return {@code null}
    * on failure so the default value can be used.
    *
    * @param value the string value from the config file
    * @return the parsed value, or {@code null} if parsing failed
    */
   public abstract T getFromString(String value);
   
   /**
    * Returns the Brigadier {@link ArgumentType} for command integration.
    *
    * @return the argument type for this value
    */
   public abstract ArgumentType<?> getArgumentType();
   
   /**
    * Parses the value from a command context after it's been parsed by Brigadier.
    *
    * @param ctx the command context
    * @return the parsed value
    */
   public abstract T parseArgumentValue(CommandContext<CommandSourceStack> ctx);
   
   /**
    * Provides command suggestions for this value type.
    *
    * <p>For example, enum values might suggest all enum constants,
    * while boolean values suggest "true" and "false".
    *
    * @param ctx the command context
    * @param builder the suggestions builder
    * @return a future containing the suggestions
    */
   public abstract CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder);
   
   /**
    * Converts the current value to a string for writing to the config file.
    *
    * @return the string representation
    */
   public abstract String getValueString();
   
   /**
    * Sets the value, triggering the onSet callback if one is registered.
    *
    * @param value the new value
    */
   public void setValue(T value){
      this.value = value;
      if(this.onSet != null){
         this.onSet.accept(value);
      }
   }
   
   /**
    * Validates a value against this config's validator, if one is set.
    *
    * <p>If validation fails, the validator's failure callback is invoked
    * (typically sending an error message to the command source).
    *
    * @param value the value to validate
    * @param ctx the command context (can be null)
    * @return {@code true} if validation passed or no validator is set, {@code false} if validation failed
    */
   public boolean validate(T value, @Nullable CommandContext<CommandSourceStack> ctx){
      if(this.validator != null){
         boolean pass = this.validator.predicate().test(value);
         if(!pass){
            this.validator.onFail().accept(value, ctx);
         }
         return pass;
      }
      return true;
   }
   
   /**
    * Sets the validator for this config value.
    *
    * @param validator the validator
    * @return this instance for method chaining
    */
   public ConfigValue<T> setValidator(Validator<T> validator){
      this.validator = validator;
      return this;
   }
   
   /**
    * Sets the onSet callback for this config value.
    *
    * <p>The callback is executed whenever {@link #setValue(Object)} is called,
    * including when values are loaded from the config file.
    *
    * @param onSet the callback consumer
    * @return this instance for method chaining
    */
   public ConfigValue<T> setOnSet(Consumer<T> onSet){
      this.onSet = onSet;
      return this;
   }
   
   /**
    * Gets the translation key for this setting's comment (description).
    *
    * @param modId the mod identifier
    * @return the translation key
    */
   public String getComment(String modId){
      return getTranslation(this.name, modId, "comment");
   }
   
   /**
    * Builds a translation key for a setting.
    *
    * <p>Format: {@code command.<modId>.<snake_case_name>.<suffix>}
    *
    * @param name the setting name (camelCase)
    * @param modId the mod identifier
    * @param suffix the translation suffix ("comment", "getter_setter", etc.)
    * @return the translation key
    */
   public static String getTranslation(String name, String modId, String suffix){
      return "command." + modId + "." + TextUtils.camelToSnake(name) + "." + suffix;
   }
   
   public static String getErrorTranslation(String modId){
      return "command." + modId + ".error";
   }
}
