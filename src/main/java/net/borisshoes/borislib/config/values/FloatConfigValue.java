package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class FloatConfigValue extends ConfigValue<Float> {
   protected final float defaultValue;
   private final FloatLimits limits;
   
   public FloatConfigValue(@NotNull String name, Float defaultValue, FloatLimits limits){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.limits = limits;
   }
   
   public FloatConfigValue(@NotNull String name, Float defaultValue){
      this(name, defaultValue, new FloatLimits());
   }
   
   @Override
   public Float getFromString(String value){
      return Mth.clamp(Float.parseFloat(value), limits.min, limits.max);
   }
   
   @Override
   public ArgumentType<Float> getArgumentType(){
      return FloatArgumentType.floatArg(limits.min, limits.max);
   }
   
   @Override
   public Float parseArgumentValue(CommandContext<CommandSourceStack> ctx){
      return FloatArgumentType.getFloat(ctx, name);
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      return builder.buildFuture();
   }
   
   @Override
   public String getValueString(){
      return String.valueOf(this.value != null ? this.value : this.defaultValue);
   }
   
   public static class FloatLimits {
      float min = -Float.MAX_VALUE, max = Float.MAX_VALUE;
      
      public FloatLimits(){}
      
      public FloatLimits(float min){
         this.min = min;
      }
      
      public FloatLimits(float min, float max){
         this.min = min;
         this.max = max;
      }
   }
}

