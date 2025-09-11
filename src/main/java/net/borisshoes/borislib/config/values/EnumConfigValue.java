package net.borisshoes.borislib.config.values;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.config.ConfigValue;
import net.minecraft.network.packet.s2c.play.WaypointS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EnumConfigValue<K extends Enum<K> & StringIdentifiable> extends ConfigValue<K> {
   protected final K defaultValue;
   private final Class<K> typeClass;
   
   public EnumConfigValue(@NotNull String name, K defaultValue, Class<K> typeClass){
      super(name, defaultValue);
      this.defaultValue = defaultValue;
      this.typeClass = typeClass;
   }
   
   @Override
   public K getFromString(String value){
      return parseEnum(value,typeClass);
   }
   
   @Override
   public ArgumentType<String> getArgumentType(){
      return StringArgumentType.string();
   }
   
   @Override
   public K parseArgumentValue(CommandContext<ServerCommandSource> ctx){
      String parsedString = StringArgumentType.getString(ctx, name);
      return K.valueOf(this.typeClass,parsedString);
   }
   
   public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder){
      return getEnumSuggestions(context,builder,typeClass);
   }
   
   @Override
   public String getValueString(){
      return this.value.toString();
   }
   
   public static <K extends Enum<K> & StringIdentifiable> CompletableFuture<Suggestions> getEnumSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder, Class<K> enumClass){
      Set<String> options = new HashSet<>();
      for(K k : EnumSet.allOf(enumClass)){
         options.add(k.asString());
      }
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   public static <K extends Enum<K> & StringIdentifiable> K parseEnum(String string, Class<K> enumClass){
      Optional<K> opt = EnumSet.allOf(enumClass).stream().filter(en -> en.asString().equalsIgnoreCase(string)).findFirst();
      return opt.orElse(null);
   }
}
