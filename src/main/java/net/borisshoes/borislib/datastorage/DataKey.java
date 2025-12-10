package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;


public record DataKey<T>(Identifier id, StorageScope scope, Codec<T> codec, Supplier<T> globalDef, Function<RegistryKey<World>, T> worldDef, Function<UUID, T> playerDef) {
   public DataKey{
      Objects.requireNonNull(id);
      Objects.requireNonNull(scope);
      Objects.requireNonNull(codec);
      switch(scope){
         case GLOBAL -> {
            Objects.requireNonNull(globalDef, "globalDef required for GLOBAL DataKey");
            if(worldDef != null || playerDef != null)
               throw new IllegalArgumentException("Only globalDef must be set for GLOBAL");
         }
         case WORLD -> {
            Objects.requireNonNull(worldDef, "worldDef required for WORLD DataKey");
            if(globalDef != null || playerDef != null)
               throw new IllegalArgumentException("Only worldDef must be set for WORLD");
         }
         case PLAYER -> {
            Objects.requireNonNull(playerDef, "playerDef required for PLAYER DataKey");
            if(globalDef != null || worldDef != null)
               throw new IllegalArgumentException("Only playerDef must be set for PLAYER");
         }
      }
   }
   
   public String modId(){
      return id.getNamespace();
   }
   
   public String key(){
      return id.getPath();
   }
   
   public static <T> DataKey<T> ofGlobal(Identifier id, Codec<T> codec, Supplier<T> def){
      return new DataKey<>(id, StorageScope.GLOBAL, codec, def, null, null);
   }
   
   public static <T> DataKey<T> ofWorld(Identifier id, Codec<T> codec, Function<RegistryKey<World>, T> def){
      return new DataKey<>(id, StorageScope.WORLD, codec, null, def, null);
   }
   
   public static <T> DataKey<T> ofPlayer(Identifier id, Codec<T> codec, Function<java.util.UUID, T> def){
      return new DataKey<>(id, StorageScope.PLAYER, codec, null, null, def);
   }
   
   public T makeDefaultGlobal(){
      return globalDef.get();
   }
   
   public T makeDefaultWorld(RegistryKey<World> worldKey){
      return worldDef.apply(worldKey);
   }
   
   public T makeDefaultPlayer(java.util.UUID uuid){
      return playerDef.apply(uuid);
   }
   
   public enum StorageScope {
      GLOBAL, WORLD, PLAYER
   }
}