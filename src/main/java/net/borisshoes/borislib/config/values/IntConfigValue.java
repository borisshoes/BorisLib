package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class IntConfigValue extends ConfigValue<Integer> {
   protected final int defaultValue;
   private final IntLimits limits;
   
   public IntConfigValue(@NotNull String name, Integer defaultValue, IntLimits limits){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.limits = limits;
   }
   
   public IntConfigValue(@NotNull String name, Integer defaultValue){
      this(name, defaultValue, new IntLimits());
   }
   
   @Override
   public Integer getFromString(String value){
      return Integer.parseInt(value);
   }
   
   @Override
   public ArgumentType<Integer> getArgumentType(){
      return IntegerArgumentType.integer(limits.min, limits.max);
   }
   
   @Override
   public Integer parseArgumentValue(CommandContext<CommandSourceStack> ctx){
      return IntegerArgumentType.getInteger(ctx, name);
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      if(limits.min > 0 && limits.max - limits.min < 10000){
         String start = builder.getRemaining().toLowerCase(Locale.ROOT);
         Set<String> nums = new HashSet<>();
         for(int i = limits.min; i <= limits.max; i++){
            nums.add(String.valueOf(i));
         }
         nums.stream().filter(s -> s.startsWith(start)).forEach(builder::suggest);
      }
      return builder.buildFuture();
   }
   
   @Override
   public String getValueString(){
      return this.value.toString();
   }
   
   public static class IntLimits {
      int min = Integer.MIN_VALUE, max = Integer.MAX_VALUE;
      
      public IntLimits(){}
      
      public IntLimits(int min){
         this.min = min;
      }
      
      public IntLimits(int min, int max){
         this.min = min;
         this.max = max;
      }
   }
}