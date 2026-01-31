package net.borisshoes.borislib.datastorage;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;


public record DataKey<T extends StorableData>(Identifier id, StorageScope scope, Supplier<T> globalDef,
                                              Function<ResourceKey<Level>, T> worldDef, Function<UUID, T> playerDef) {
   public DataKey{
      Objects.requireNonNull(id);
      Objects.requireNonNull(scope);
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
   
   public static <T extends StorableData> DataKey<T> ofGlobal(Identifier id, Supplier<T> def){
      return new DataKey<>(id, StorageScope.GLOBAL, def, null, null);
   }
   
   public static <T extends StorableData> DataKey<T> ofWorld(Identifier id, Function<ResourceKey<Level>, T> def){
      return new DataKey<>(id, StorageScope.WORLD, null, def, null);
   }
   
   public static <T extends StorableData> DataKey<T> ofPlayer(Identifier id, Function<java.util.UUID, T> def){
      return new DataKey<>(id, StorageScope.PLAYER, null, null, def);
   }
   
   public T makeDefaultGlobal(){
      return globalDef.get();
   }
   
   public T makeDefaultWorld(ResourceKey<Level> worldKey){
      return worldDef.apply(worldKey);
   }
   
   public T makeDefaultPlayer(java.util.UUID uuid){
      return playerDef.apply(uuid);
   }
   
   public enum StorageScope {
      GLOBAL, WORLD, PLAYER
   }
}