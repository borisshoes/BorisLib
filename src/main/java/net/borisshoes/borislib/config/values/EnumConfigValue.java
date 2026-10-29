package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EnumConfigValue<K extends Enum<K> & StringRepresentable> extends ConfigValue<K> {
   protected final K defaultValue;
   private final Class<K> typeClass;
   
   public EnumConfigValue(@NotNull String name, K defaultValue, Class<K> typeClass){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.typeClass = typeClass;
   }
   
   @Override
   public K getFromString(String value){
      K parsed = parseEnum(value, typeClass);
      return parsed != null ? parsed : this.defaultValue;
   }
   
   @Override
   public ArgumentType<String> getArgumentType(){
      return StringArgumentType.string();
   }
   
   @Override
   public K parseArgumentValue(CommandContext<CommandSourceStack> ctx){
      // Matches the suggestions (serialized names, any case); unknown input reports itself and returns null, so the
      // command changes nothing
      String parsedString = StringArgumentType.getString(ctx, name);
      K parsed = parseEnum(parsedString, typeClass);
      if(parsed == null) ctx.getSource().sendFailure(Component.translatable("argument.enum.invalid", parsedString));
      return parsed;
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      return getEnumSuggestions(context, builder, typeClass);
   }
   
   @Override
   public String getValueString(){
      // Written as the serialized name, which is what getFromString and the suggestions match against
      return (this.value != null ? this.value : this.defaultValue).getSerializedName();
   }
   
   public static <K extends Enum<K> & StringRepresentable> CompletableFuture<Suggestions> getEnumSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, Class<K> enumClass){
      Set<String> options = new HashSet<>();
      for(K k : EnumSet.allOf(enumClass)){
         options.add(k.getSerializedName());
      }
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   /**
    * Finds the constant whose serialized name matches, ignoring case. Falls back to the Java name and
    * {@code toString()}, which is what config files written before 4.0.1 contain.
    *
    * @param string    the text to match
    * @param enumClass the enum type
    * @param <K>       the enum type
    * @return the matching constant, or {@code null} if none matches
    */
   public static <K extends Enum<K> & StringRepresentable> K parseEnum(String string, Class<K> enumClass){
      Optional<K> opt = EnumSet.allOf(enumClass).stream().filter(en -> en.getSerializedName().equalsIgnoreCase(string)).findFirst();
      if(opt.isEmpty()){
         opt = EnumSet.allOf(enumClass).stream().filter(en -> en.name().equalsIgnoreCase(string) || en.toString().equalsIgnoreCase(string)).findFirst();
      }
      return opt.orElse(null);
   }
}
