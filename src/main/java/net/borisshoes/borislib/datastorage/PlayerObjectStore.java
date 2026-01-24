package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
      // Backup raw data loaded lazily for recovery purposes
      Map<String, Map<String, CompoundTag>> backupRaw = null;
      boolean backupLoaded = false;
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
   
   private Path backupFile(UUID u){
      return dir.resolve(u.toString() + ".dat_old");
   }
   
   /**
    * Attempts to read NBT from the main file, falling back to backup if corrupted.
    * Returns null if both files are missing or corrupted.
    */
   @Nullable
   private CompoundTag readWithBackup(UUID u){
      Path main = file(u);
      Path backup = backupFile(u);
      
      // Try main file first
      if(Files.exists(main)){
         try(DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(main)))){
            return NbtIo.read(in);
         }catch(Exception e){
            BorisLib.LOGGER.warn("Failed to read player data file {}, trying backup: {}", main, e.getMessage());
         }
      }
      
      // Fall back to backup
      if(Files.exists(backup)){
         try(DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(backup)))){
            BorisLib.LOGGER.info("Successfully loaded player data from backup file for {}", u);
            return NbtIo.read(in);
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to read backup player data file {} as well: {}", backup, e.getMessage());
         }
      }
      
      return null;
   }
   
   /**
    * Reads only the backup file NBT without affecting the main data.
    * Returns null if backup doesn't exist or is corrupted.
    */
   @Nullable
   private CompoundTag readBackupOnly(UUID u){
      Path backup = backupFile(u);
      if(!Files.exists(backup)) return null;
      try(DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(backup)))){
         return NbtIo.read(in);
      }catch(Exception e){
         BorisLib.LOGGER.warn("Failed to read backup file for recovery: {}", e.getMessage());
         return null;
      }
   }
   
   /**
    * Lazily loads and parses the backup file's raw data for recovery purposes.
    */
   private void ensureBackupLoaded(UUID u, Entry e){
      if(e.backupLoaded) return;
      e.backupLoaded = true;
      
      CompoundTag backupRoot = readBackupOnly(u);
      if(backupRoot == null) return;
      
      try{
         var dyn = new Dynamic<>(NbtOps.INSTANCE, backupRoot);
         Map<String, Map<String, CompoundTag>> decoded = FlatNamespacedMap.CODEC.parse(dyn).result().orElse(null);
         if(decoded != null){
            e.backupRaw = new HashMap<>();
            decoded.forEach((modId, inner) -> e.backupRaw.put(modId, new HashMap<>(inner)));
         }
      }catch(Exception ex){
         BorisLib.LOGGER.warn("Failed to parse backup data structure for recovery: {}", ex.getMessage());
      }
   }
   
   /**
    * Attempts to recover a key's data from the backup file.
    * Returns null if backup doesn't have valid data for this key.
    */
   @Nullable
   private <T> T tryRecoverFromBackup(UUID u, Entry e, DataKey<T> key){
      ensureBackupLoaded(u, e);
      if(e.backupRaw == null) return null;
      
      Map<String, CompoundTag> modRaw = e.backupRaw.get(key.modId());
      if(modRaw == null) return null;
      
      CompoundTag backupTag = modRaw.get(key.key());
      if(backupTag == null) return null;
      
      T recovered = decode(key.codec(), backupTag, key.id().toString() + " (from backup)");
      if(recovered != null){
         BorisLib.LOGGER.info("Successfully recovered data for key {} player {} from backup", key.id(), u);
      }
      return recovered;
   }
   
   private Entry getEntry(UUID u){
      return cache.computeIfAbsent(u, id -> {
         Entry e = new Entry();
         CompoundTag root = readWithBackup(id);
         if(root == null) return e;
         try{
            var dyn = new Dynamic<>(NbtOps.INSTANCE, root);
            Map<String, Map<String, CompoundTag>> decoded = FlatNamespacedMap.CODEC.parse(dyn).result().orElseGet(HashMap::new);
            // Deep-copy into mutable maps
            decoded.forEach((modId, inner) -> {
               Map<String, CompoundTag> mutableInner = new ConcurrentHashMap<>(inner);
               e.raw.put(modId, mutableInner);
            });
         }catch(Exception ex){
            BorisLib.LOGGER.error("Failed to parse player data structure for {}: {}", id, ex.getMessage());
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
            T decoded = decode(key.codec(), n, key.id().toString());
            if(decoded != null){
               modObjs.put(key.key(), decoded);
               if(modRaw.isEmpty()) e.raw.remove(key.modId());
               return decoded;
            }
            // decode returned null (corrupted data), try to recover from backup
            BorisLib.LOGGER.warn("Corrupted data for key {} player {}, attempting recovery from backup", key.id(), u);
            if(modRaw.isEmpty()) e.raw.remove(key.modId());
            
            T recovered = tryRecoverFromBackup(u, e, key);
            if(recovered != null){
               modObjs.put(key.key(), recovered);
               return recovered;
            }
            BorisLib.LOGGER.warn("Could not recover key {} for player {} from backup, using default value", key.id(), u);
         }
      }
      
      // Create default
      T created = key.makeDefaultPlayer(u);
      if(created == null){
         BorisLib.LOGGER.warn("DataKey<{}> default factory returned null for player {}. This may cause issues.", key.id(), u);
         throw new IllegalStateException("DataKey<" + key.id() + "> default factory returned null for player " + u);
      }
      modObjs.put(key.key(), created);
      return created;
   }
   
   public <T> void setLive(UUID u, DataKey<T> key, T value){
      Entry e = getEntry(u);
      T toStore = value != null ? value : key.makeDefaultPlayer(u);
      e.objects.computeIfAbsent(key.modId(), k -> new ConcurrentHashMap<>()).put(key.key(), toStore);
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
               CompoundTag encoded = encode(dk.codec(), kv.getValue(), dk.id().toString());
               // Validate: skip empty compounds to avoid saving uninitialized data
               if(encoded == null || encoded.isEmpty()){
                  BorisLib.LOGGER.warn("Skipping save for key {} player {} - encoded data is empty/invalid", dk.id(), u);
                  continue;
               }
               tgt.put(key, encoded);
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
      
      // Write file with backup rotation:
      // 1. Write to temp file
      // 2. Move existing main -> backup (if main exists)
      // 3. Move temp -> main
      Path main = file(u);
      Path backup = backupFile(u);
      Path temp = dir.resolve(u.toString() + ".dat_tmp");
      
      var dyn = FlatNamespacedMap.CODEC.encodeStart(NbtOps.INSTANCE, out).result().orElseGet(CompoundTag::new);
      try{
         // Write to temp file first
         try(OutputStream os = Files.newOutputStream(temp); GZIPOutputStream gz = new GZIPOutputStream(os); DataOutputStream outStr = new DataOutputStream(gz)){
            NbtIo.writeUnnamedTagWithFallback(dyn, outStr);
         }
         
         // Rotate: main -> backup (only if main exists and is valid)
         if(Files.exists(main)){
            Files.move(main, backup, StandardCopyOption.REPLACE_EXISTING);
         }
         
         // Move temp -> main
         Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING);
      }catch(Exception ex){
         BorisLib.LOGGER.error("Failed to save player data for {}: {}", u, ex.getMessage());
         // Clean up temp file if it exists
         try{
            Files.deleteIfExists(temp);
         }catch(Exception ignored){
         }
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
   
   /**
    * Safely encodes an object to NBT using the given codec.
    * Returns null if encoding fails, with a warning logged.
    */
   @Nullable
   private static <T> CompoundTag encode(Codec<T> codec, T v, String keyId){
      if(v == null){
         BorisLib.LOGGER.warn("Cannot encode null value for key {}", keyId);
         return null;
      }
      DataResult<net.minecraft.nbt.Tag> result = codec.encodeStart(NbtOps.INSTANCE, v);
      if(result.error().isPresent()){
         BorisLib.LOGGER.warn("Failed to encode data for key {}: {}", keyId, result.error().get().message());
         return null;
      }
      return (CompoundTag) result.result().orElse(null);
   }
   
   /**
    * Safely decodes NBT using the given codec.
    * Returns null if decoding fails (corrupted/incompatible data), with a warning logged.
    */
   @Nullable
   private static <T> T decode(Codec<T> codec, CompoundTag tag, String keyId){
      DataResult<T> result = codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag));
      if(result.error().isPresent()){
         BorisLib.LOGGER.warn("Failed to decode data for key {}: {}", keyId, result.error().get().message());
         return null;
      }
      return result.result().orElse(null);
   }
}