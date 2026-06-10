package net.borisshoes.borislib.datastorage;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link DataKey}s.
 *
 * <p>Keys are addressed by the composite signature {@code "<modId>/<key>/<scope>"} so the same name can
 * legitimately exist under two different scopes (e.g. a GLOBAL "stats" and a PLAYER "stats"). Registering
 * the same signature twice throws.</p>
 *
 * <p>This registry is purely an in-memory lookup table; the actual persistence is handled by
 * {@link GlobalState}, {@link WorldState} and {@link PlayerObjectStore}.</p>
 */
public final class DataRegistry {
   private static final Map<String, DataKey<?>> BY_SIG = new ConcurrentHashMap<>();
   
   private DataRegistry(){
   }
   
   /**
    * Registers a {@link DataKey}.
    *
    * @param k   the key to register; must be unique under its {@code (modId, key, scope)} triple
    * @param <T> the storable type
    * @return the same key, for convenient chaining at declaration site
    * @throws IllegalStateException if a key with the same signature is already registered
    */
   public static <T extends StorableData> DataKey<T> register(DataKey<T> k){
      String sig = k.modId() + "/" + k.key() + "/" + k.scope();
      if(BY_SIG.putIfAbsent(sig, k) != null) throw new IllegalStateException("Duplicate data key: " + sig);
      return k;
   }
   
   /**
    * Looks up a previously registered key by its mod id, path and scope.
    *
    * @param modId the namespace component
    * @param key   the path component
    * @param s     the storage scope
    * @param <T>   the data type, inferred at the call site (unchecked cast)
    * @return the matching key, or {@code null} if not registered
    */
   @SuppressWarnings("unchecked")
   public static <T extends StorableData> DataKey<T> get(String modId, String key, DataKey.StorageScope s){
      return (DataKey<T>) BY_SIG.get(modId + "/" + key + "/" + s);
   }
   
   /**
    * Convenience overload of {@link #get(String, String, DataKey.StorageScope)} that accepts an
    * {@link Identifier} instead of separate strings.
    *
    * @param id  the full identifier
    * @param s   the storage scope
    * @param <T> the data type
    * @return the matching key, or {@code null} if not registered
    */
   @SuppressWarnings("unchecked")
   public static <T extends StorableData> DataKey<T> get(Identifier id, DataKey.StorageScope s){
      return (DataKey<T>) BY_SIG.get(id.getNamespace() + "/" + id.getPath() + "/" + s);
   }
}