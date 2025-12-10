package net.borisshoes.borislib.datastorage;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DataRegistry {
   private static final Map<String, DataKey<?>> BY_SIG = new ConcurrentHashMap<>();
   
   private DataRegistry(){}
   
   public static <T> DataKey<T> register(DataKey<T> k){
      String sig = k.modId() + "/" + k.key() + "/" + k.scope();
      if(BY_SIG.putIfAbsent(sig, k) != null) throw new IllegalStateException("Duplicate data key: " + sig);
      return k;
   }
   
   @SuppressWarnings("unchecked")
   public static <T> DataKey<T> get(String modId, String key, DataKey.StorageScope s){
      return (DataKey<T>) BY_SIG.get(modId + "/" + key + "/" + s);
   }
   
   @SuppressWarnings("unchecked")
   public static <T> DataKey<T> get(Identifier id, DataKey.StorageScope s){
      return (DataKey<T>) BY_SIG.get(id.getNamespace() + "/" + id.getPath() + "/" + s);
   }
}