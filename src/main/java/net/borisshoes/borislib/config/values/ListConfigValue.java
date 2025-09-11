package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ListConfigValue<G,T extends ConfigValue<G>> extends ConfigValue<List<G>> {
   protected final List<G> defaultValue;
   private final T configType;
   
   public ListConfigValue(@NotNull String name, List<G> defaultValue, T configType){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.configType = configType;
   }
   
   @Override
   public List<G> getFromString(String value){
      try {
         value = value.trim();
         if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1).trim();
         }
         ArrayList<G> list = new ArrayList<>();
         if (value.isEmpty()) {
            return list;
         }
         
         String[] tokens = value.split("[,\\s]+");
         for(String token : tokens){
            G parsedValue = configType.getFromString(token);
            if(parsedValue != null){
               list.add(parsedValue);
            }
         }
         
         return list;
      } catch (Exception e) {
         return defaultValue;
      }
   }
   
   @Override
   public ArgumentType<?> getArgumentType(){
      return StringArgumentType.greedyString();
   }
   
   @Override
   public List<G> parseArgumentValue(CommandContext<ServerCommandSource> ctx){
      String str = StringArgumentType.getString(ctx, name);
      return getFromString(str);
   }
   
   @Override
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder){
      return builder.buildFuture();
   }
   
   @Override
   public String getValueString(){
      return this.value.stream().map(Object::toString).collect(Collectors.joining(", "));
   }
}