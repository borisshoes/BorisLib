package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class StringConfigValue extends ConfigValue<String> {
   protected final String defaultValue;
   protected final String[] options;
   
   public StringConfigValue(@NotNull String name, String defaultValue, @Nullable String... options){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.options = options;
   }
   
   @Override
   public String getFromString(String value){
      return value;
   }
   
   @Override
   public ArgumentType<String> getArgumentType(){
      return StringArgumentType.greedyString();
   }
   
   @Override
   public String parseArgumentValue(CommandContext<CommandSourceStack> ctx){
      return StringArgumentType.getString(ctx, name);
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      Arrays.stream(options).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   @Override
   public String getValueString(){
      return this.value;
   }
}