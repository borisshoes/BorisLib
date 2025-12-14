package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Caches live objects per player. File format on disk is the same flat namespaced map ({modId -> {key -> NBT}}).
 * Two maps per player:
 * - objects: decoded live objects (modId -> key -> Object)
 * - raw: raw NBT for keys not yet accessed (modId -> key -> NbtCompound)
 * On save: encode objects via DataRegistry, and write any remaining raw entries as-is.
 */
public final class PlayerObjectStore {
   private final Path dir;
   private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
   
   private static final class Entry {
      final Map<String, Map<String, Object>> objects = new ConcurrentHashMap<>();
      final Map<String, Map<String, CompoundTag>> raw = new ConcurrentHashMap<>();
   }
   
   public PlayerObjectStore(Path worldRoot){
      this.dir = worldRoot.resolve("data").resolve(MOD_ID).resolve("players");
      try{
         Files.createDirectories(dir);
      }catch(Exception ignored){
      
      }
   }
   
   private Path file(UUID u){
      return dir.resolve(u.toString() + ".dat");
   }
   
   private Entry getEntry(UUID u){
      return cache.computeIfAbsent(u, id -> {
         Entry e = new Entry();
         Path f = file(id);
         if(!Files.exists(f)) return e;
         try(DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(f)))){
            CompoundTag root = net.minecraft.nbt.NbtIo.read(in);
            var dyn = new Dynamic<>(NbtOps.INSTANCE, root);
            Map<String, Map<String, CompoundTag>> decoded = FlatNamespacedMap.CODEC.parse(dyn).result().orElseGet(HashMap::new);
            // Deep-copy into mutable maps
            decoded.forEach((modId, inner) -> {
               Map<String, CompoundTag> mutableInner = new ConcurrentHashMap<>(inner);
               e.raw.put(modId, mutableInner);
            });
         }catch(Exception ignored){
         }
         return e;
      });
   }
   
   @SuppressWarnings("unchecked")
   public <T> T getLive(UUID u, DataKey<T> key){
      Entry e = getEntry(u);
      Map<String, Object> modObjs = e.objects.computeIfAbsent(key.modId(), k -> new ConcurrentHashMap<>());
      Object got = modObjs.get(key.key());
      if(got != null) return (T) got;
      
      // Try lazy-decode from raw NBT
      Map<String, CompoundTag> modRaw = e.raw.get(key.modId());
      if(modRaw != null){
         CompoundTag n = modRaw.remove(key.key());
         if(n != null){
            T decoded = decode(key.codec(), n);
            modObjs.put(key.key(), decoded);
            if(modRaw.isEmpty()) e.raw.remove(key.modId());
            return decoded;
         }
      }
      
      // Create default
      T created = key.makeDefaultPlayer(u);
      if(created == null){
         throw new IllegalStateException("DataKey<" + key.id() + "> default factory returned null for player " + u);
      }
      modObjs.put(key.key(), created);
      return created;
   }
   
   public <T> void setLive(UUID u, DataKey<T> key, T value){
      Entry e = getEntry(u);
      e.objects.computeIfAbsent(key.modId(), k -> new ConcurrentHashMap<>()).put(key.key(), value);
      Map<String, CompoundTag> modRaw = e.raw.get(key.modId());
      if(modRaw != null){
         modRaw.remove(key.key());
         if(modRaw.isEmpty()) e.raw.remove(key.modId());
      }
   }
   
   public void preload(UUID u){
      /* touch the entry so raw NBT (if any) is parsed once */
      getEntry(u);
   }
   
   public void save(UUID u){
      Entry e = cache.get(u);
      if(e == null) return;
      
      // Build a namespaced NBT map by encoding live objects + copying remaining raw
      Map<String, Map<String, CompoundTag>> out = new HashMap<>();
      // 1) encode objects using registered codecs
      for(var modEntry : e.objects.entrySet()){
         String modId = modEntry.getKey();
         Map<String, CompoundTag> tgt = out.computeIfAbsent(modId, k -> new HashMap<>());
         for(var kv : modEntry.getValue().entrySet()){
            String key = kv.getKey();
            DataKey<Object> dk = DataRegistry.get(modId, key, DataKey.StorageScope.PLAYER);
            if(dk != null){
               tgt.put(key, encode(dk.codec(), kv.getValue()));
            }else{
               // No registered codec? Skip to avoid corrupting data.
            }
         }
      }
      
      // 2) copy any raw entries we never decoded
      for(var modEntry : e.raw.entrySet()){
         String modId = modEntry.getKey();
         Map<String, CompoundTag> tgt = out.computeIfAbsent(modId, k -> new HashMap<>());
         for(var kv : modEntry.getValue().entrySet()){
            tgt.putIfAbsent(kv.getKey(), kv.getValue());
         }
      }
      
      // Write file
      var dyn = FlatNamespacedMap.CODEC.encodeStart(NbtOps.INSTANCE, out).result().orElseGet(CompoundTag::new);
      try(OutputStream os = Files.newOutputStream(file(u)); GZIPOutputStream gz = new GZIPOutputStream(os); DataOutputStream outStr = new DataOutputStream(gz)){
         net.minecraft.nbt.NbtIo.writeUnnamedTagWithFallback(dyn, outStr);
      }catch(Exception ignored){
      }
   }
   
   public Map<UUID, Map<String, Map<String, Object>>> snapshotAllObjects(){
      Map<UUID, Map<String, Map<String, Object>>> copy = new HashMap<>();
      cache.forEach((u, e) -> {
         Map<String, Map<String, Object>> m = new HashMap<>();
         e.objects.forEach((mod, inner) -> m.put(mod, Map.copyOf(inner)));
         copy.put(u, Map.copyOf(m));
      });
      return copy;
   }
   
   private static <T> CompoundTag encode(Codec<T> codec, T v){
      return (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, v).result().orElse(new CompoundTag());
   }
   
   private static <T> T decode(Codec<T> codec, CompoundTag tag){
      return codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElseThrow();
   }
}