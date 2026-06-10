package net.borisshoes.borislib.datastorage;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Strongly-typed handle that identifies one piece of persistent {@link StorableData} stored by BorisLib.
 *
 * <p>A {@code DataKey} bundles four things:</p>
 * <ul>
 *    <li>An {@link Identifier} that names the data, scoped by the owning mod's namespace.</li>
 *    <li>A {@link StorageScope} ({@link StorageScope#GLOBAL GLOBAL} / {@link StorageScope#WORLD WORLD} /
 *        {@link StorageScope#PLAYER PLAYER}) that controls where the data lives and how it is keyed.</li>
 *    <li>A scope-appropriate default-factory used the first time a piece of data is requested for a
 *        given context (server, world, or player UUID).</li>
 *    <li>Only one of the three factory fields ({@code globalDef}, {@code worldDef}, {@code playerDef}) is
 *        permitted to be non-null; the record's compact constructor enforces this.</li>
 * </ul>
 *
 * <p>Instances are normally created via the static helpers {@link #ofGlobal}, {@link #ofWorld} and
 * {@link #ofPlayer}, then registered with {@link DataRegistry#register(DataKey)} during mod init.
 * The registered key is then passed to the appropriate {@link DataAccess} getter/setter to read or
 * write the underlying object.</p>
 *
 * @param id        unique namespaced identifier for the data (e.g. {@code "mymod:economy"})
 * @param scope     the storage scope
 * @param globalDef factory invoked when the key is GLOBAL and no value exists yet
 * @param worldDef  factory invoked when the key is WORLD; receives the dimension key
 * @param playerDef factory invoked when the key is PLAYER; receives the player's UUID
 * @param <T>       the {@link StorableData} type being stored
 */
public record DataKey<T extends StorableData>(Identifier id, StorageScope scope, Supplier<T> globalDef,
                                              Function<ResourceKey<Level>, T> worldDef, Function<UUID, T> playerDef) {
   /**
    * Validates that exactly one factory matches the chosen {@link #scope()}.
    *
    * @throws NullPointerException     if {@code id}, {@code scope} or the scope-appropriate factory is null
    * @throws IllegalArgumentException if a factory from the wrong scope was also provided
    */
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
   
   /** @return the owning mod id ({@code id.getNamespace()}) — used as the outer key in the on-disk layout. */
   public String modId(){
      return id.getNamespace();
   }
   
   /** @return the local key ({@code id.getPath()}) — used as the inner key in the on-disk layout. */
   public String key(){
      return id.getPath();
   }
   
   /**
    * Creates a {@link StorageScope#GLOBAL} key.
    *
    * @param id  the namespaced identifier
    * @param def factory invoked on first access to produce a fresh default value
    * @param <T> the data type
    * @return a new (unregistered) {@code DataKey}
    */
   public static <T extends StorableData> DataKey<T> ofGlobal(Identifier id, Supplier<T> def){
      return new DataKey<>(id, StorageScope.GLOBAL, def, null, null);
   }
   
   /**
    * Creates a {@link StorageScope#WORLD} key. The factory receives the dimension's resource key so the
    * default value can be tailored per-dimension.
    *
    * @param id  the namespaced identifier
    * @param def factory invoked on first access for a given world
    * @param <T> the data type
    * @return a new (unregistered) {@code DataKey}
    */
   public static <T extends StorableData> DataKey<T> ofWorld(Identifier id, Function<ResourceKey<Level>, T> def){
      return new DataKey<>(id, StorageScope.WORLD, null, def, null);
   }
   
   /**
    * Creates a {@link StorageScope#PLAYER} key. The factory receives the player's UUID so the default
    * value can be tailored per-player.
    *
    * @param id  the namespaced identifier
    * @param def factory invoked on first access for a given player
    * @param <T> the data type
    * @return a new (unregistered) {@code DataKey}
    */
   public static <T extends StorableData> DataKey<T> ofPlayer(Identifier id, Function<java.util.UUID, T> def){
      return new DataKey<>(id, StorageScope.PLAYER, null, null, def);
   }
   
   /** @return a fresh default value for a GLOBAL key. Only valid when {@link #scope()} is GLOBAL. */
   public T makeDefaultGlobal(){
      return globalDef.get();
   }
   
   /**
    * @param worldKey the dimension being defaulted for
    * @return a fresh default value for a WORLD key. Only valid when {@link #scope()} is WORLD.
    */
   public T makeDefaultWorld(ResourceKey<Level> worldKey){
      return worldDef.apply(worldKey);
   }
   
   /**
    * @param uuid the player being defaulted for
    * @return a fresh default value for a PLAYER key. Only valid when {@link #scope()} is PLAYER.
    */
   public T makeDefaultPlayer(java.util.UUID uuid){
      return playerDef.apply(uuid);
   }
   
   /**
    * The kind of context the data is keyed by.
    *
    * <ul>
    *    <li>{@link #GLOBAL}: one instance per server, stored alongside the overworld save data.</li>
    *    <li>{@link #WORLD}: one instance per dimension, stored alongside that dimension's save data.</li>
    *    <li>{@link #PLAYER}: one instance per player UUID, stored under {@code players/<modid>/<uuid>.dat}.</li>
    * </ul>
    */
   public enum StorageScope {
      /** One value per server. */
      GLOBAL,
      /** One value per dimension. */
      WORLD,
      /** One value per player UUID. */
      PLAYER
   }
}