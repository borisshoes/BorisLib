package net.borisshoes.borislib.config;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.utils.TextUtils;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class ConfigValue<T>{
   protected final T defaultValue;
   protected final String name;
   protected T value;
   
   public ConfigValue(@NotNull String name, T defaultValue){
      this.name = name;
      this.defaultValue = defaultValue;
   }
   
   public String getName(){
      return name;
   }
   
   public abstract T getFromString(String value);
   
   public abstract ArgumentType<?> getArgumentType();
   
   public abstract T parseArgumentValue(CommandContext<ServerCommandSource> ctx);
   
   public abstract CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder);
   
   public abstract String getValueString();
   
   public void setValue(T value){
      this.value = value;
   }
   
   public String getComment(String modId){
      return getTranslation(this.name,modId,"comment");
   }
   
   public static String getTranslation(String name, String modId, String suffix){
      return "command."+modId+"."+TextUtils.camelToSnake(name)+"."+suffix;
   }
   
   public static String getErrorTranslation(String modId){
      return "command."+modId+".error";
   }
}
