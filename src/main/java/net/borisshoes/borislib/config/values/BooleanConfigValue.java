package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BooleanConfigValue extends ConfigValue<Boolean> {
   protected final boolean defaultValue;
   
   public BooleanConfigValue(@NotNull String name, boolean defaultValue){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
   }
   
   @Override
   public Boolean getFromString(String value){
      return Boolean.parseBoolean(value);
   }
   
   @Override
   public ArgumentType<Boolean> getArgumentType(){
      return BoolArgumentType.bool();
   }
   
   @Override
   public Boolean parseArgumentValue(CommandContext<ServerCommandSource> ctx){
      return BoolArgumentType.getBool(ctx, name);
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder){
      Set<String> options = new HashSet<>();
      options.add("true");
      options.add("false");
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   @Override
   public String getValueString(){
      return this.value.toString();
   }
}
