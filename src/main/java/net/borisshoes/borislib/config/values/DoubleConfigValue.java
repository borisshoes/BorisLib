package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class DoubleConfigValue extends ConfigValue<Double> {
   protected final double defaultValue;
   private final DoubleLimits limits;
   
   public DoubleConfigValue(@NotNull String name, Double defaultValue, DoubleLimits limits){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.limits = limits;
   }
   
   public DoubleConfigValue(@NotNull String name, Double defaultValue){
      this(name, defaultValue, new DoubleLimits());
   }
   
   @Override
   public Double getFromString(String value){
      return Double.parseDouble(value);
   }
   
   @Override
   public ArgumentType<Double> getArgumentType(){
      return DoubleArgumentType.doubleArg(limits.min, limits.max);
   }
   
   @Override
   public Double parseArgumentValue(CommandContext<ServerCommandSource> ctx){
      return DoubleArgumentType.getDouble(ctx, name);
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder){
      return builder.buildFuture();
   }
   
   @Override
   public String getValueString(){
      return this.value.toString();
   }
   
   public static class DoubleLimits {
      double min = Double.MIN_VALUE, max = Double.MAX_VALUE;
      
      public DoubleLimits(){}
      
      public DoubleLimits(double min){
         this.min = min;
      }
      
      public DoubleLimits(double min, double max){
         this.min = min;
         this.max = max;
      }
   }
}