package net.borisshoes.borislib.datastorage;

import com.mojang.logging.LogUtils;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
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
 * On save: encode objects via StorableData.write(), and write any remaining raw entries as-is.
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
         Map<String, Map<String, CompoundTag>> decoded = parseNamespacedMap(backupRoot);
         if(decoded != null){
            e.backupRaw = new HashMap<>();
            decoded.forEach((modId, inner) -> e.backupRaw.put(modId, new HashMap<>(inner)));
         }
      }catch(Exception ex){
         BorisLib.LOGGER.warn("Failed to parse backup data structure for recovery: {}", ex.getMessage());
      }
   }
   
   /**
    * Parses a CompoundTag as a nested map of {modId -> {key -> CompoundTag}}.
    */
   private Map<String, Map<String, CompoundTag>> parseNamespacedMap(CompoundTag root){
      Map<String, Map<String, CompoundTag>> result = new HashMap<>();
      for(String modId : root.keySet()){
         if(root.get(modId) instanceof CompoundTag modTag){
            Map<String, CompoundTag> inner = new HashMap<>();
            for(String key : modTag.keySet()){
               if(modTag.get(key) instanceof CompoundTag keyTag){
                  inner.put(key, keyTag);
               }
            }
            result.put(modId, inner);
         }
      }
      return result;
   }
   
   /**
    * Attempts to recover a key's data from the backup file.
    * Returns null if backup doesn't have valid data for this key.
    */
   @Nullable
   private <T extends StorableData> T tryRecoverFromBackup(UUID u, Entry e, DataKey<T> key){
      ensureBackupLoaded(u, e);
      if(e.backupRaw == null) return null;
      
      Map<String, CompoundTag> modRaw = e.backupRaw.get(key.modId());
      if(modRaw == null) return null;
      
      CompoundTag backupTag = modRaw.get(key.key());
      if(backupTag == null) return null;
      
      T recovered = decode(key, backupTag, u, key.id().toString() + " (from backup)");
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
            Map<String, Map<String, CompoundTag>> decoded = parseNamespacedMap(root);
            if(decoded != null){
               // Deep-copy into mutable maps
               decoded.forEach((modId, inner) -> {
                  Map<String, CompoundTag> mutableInner = new ConcurrentHashMap<>(inner);
                  e.raw.put(modId, mutableInner);
               });
            }
         }catch(Exception ex){
            BorisLib.LOGGER.error("Failed to parse player data structure for {}: {}", id, ex.getMessage());
         }
         return e;
      });
   }
   
   @SuppressWarnings("unchecked")
   public <T extends StorableData> T getLive(UUID u, DataKey<T> key){
      Entry e = getEntry(u);
      Map<String, Object> modObjs = e.objects.computeIfAbsent(key.modId(), k -> new ConcurrentHashMap<>());
      
      // computeIfAbsent is atomic on ConcurrentHashMap — only one thread runs the
      // mapping function for a given key, preventing the race where two threads both
      // see null, one grabs the raw NBT, and the other overwrites with a blank default.
      return (T) modObjs.computeIfAbsent(key.key(), k -> decodeOrCreate(u, e, key));
   }
   
   private <T extends StorableData> Object decodeOrCreate(UUID u, Entry e, DataKey<T> key){
      // Try lazy-decode from raw NBT
      Map<String, CompoundTag> modRaw = e.raw.get(key.modId());
      if(modRaw != null){
         CompoundTag n = modRaw.remove(key.key());
         if(n != null){
            T decoded = decode(key, n, u, key.id().toString());
            if(decoded != null){
               if(modRaw.isEmpty()) e.raw.remove(key.modId());
               return decoded;
            }
            // decode returned null (corrupted data), try to recover from backup
            BorisLib.LOGGER.warn("Corrupted data for key {} player {}, attempting recovery from backup", key.id(), u);
            if(modRaw.isEmpty()) e.raw.remove(key.modId());
            
            T recovered = tryRecoverFromBackup(u, e, key);
            if(recovered != null) return recovered;
            BorisLib.LOGGER.warn("Could not recover key {} for player {} from backup, using default value", key.id(), u);
         }
      }
      
      // Create default
      T created = key.makeDefaultPlayer(u);
      if(created == null){
         BorisLib.LOGGER.warn("DataKey<{}> default factory returned null for player {}. This may cause issues.", key.id(), u);
         throw new IllegalStateException("DataKey<" + key.id() + "> default factory returned null for player " + u);
      }
      return created;
   }
   
   public <T extends StorableData> void setLive(UUID u, DataKey<T> key, T value){
      Entry e = getEntry(u);
      T toStore = value != null ? value : key.makeDefaultPlayer(u);
      if(toStore == null){
         BorisLib.LOGGER.error("Cannot store null value for key {} player {} and default factory also returned null", key.id(), u);
         return;
      }
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
      if(e == null){
         BorisLib.LOGGER.debug("No cache entry for player {} during save, skipping", u);
         return;
      }
      
      // Build a namespaced NBT map by encoding live objects + copying remaining raw
      CompoundTag out = new CompoundTag();
      
      // 1) encode objects using StorableData.write()
      for(var modEntry : e.objects.entrySet()){
         String modId = modEntry.getKey();
         try{
            CompoundTag modTag = out.getCompoundOrEmpty(modId);
            if(modTag.isEmpty()){
               modTag = new CompoundTag();
               out.put(modId, modTag);
            }
            for(var kv : modEntry.getValue().entrySet()){
               String key = kv.getKey();
               Object value = kv.getValue();
               try{
                  if(value instanceof StorableData storable){
                     CompoundTag encoded = encode(storable, modId + ":" + key);
                     // Validate: skip empty compounds to avoid saving uninitialized data
                     if(encoded == null || encoded.isEmpty()){
                        BorisLib.LOGGER.warn("Skipping save for key {}:{} player {} - encoded data is empty/invalid", modId, key, u);
                        continue;
                     }
                     modTag.put(key, encoded);
                  }
               }catch(Exception ex){
                  BorisLib.LOGGER.error("Failed to encode key {}:{} for player {}: {}", modId, key, u, ex.getMessage());
               }
            }
         }catch(Exception ex){
            BorisLib.LOGGER.error("Failed to save data for mod {} player {}: {}", modId, u, ex.getMessage());
         }
      }
      
      // 2) copy any raw entries we never decoded
      for(var modEntry : e.raw.entrySet()){
         String modId = modEntry.getKey();
         try{
            CompoundTag modTag = out.getCompoundOrEmpty(modId);
            if(modTag.isEmpty()){
               modTag = new CompoundTag();
               out.put(modId, modTag);
            }
            for(var kv : modEntry.getValue().entrySet()){
               if(!modTag.contains(kv.getKey())){
                  modTag.put(kv.getKey(), kv.getValue());
               }
            }
         }catch(Exception ex){
            BorisLib.LOGGER.error("Failed to copy raw data for mod {} player {}: {}", modId, u, ex.getMessage());
         }
      }
      
      // Write file with backup rotation:
      // 1. Write to temp file
      // 2. Move existing main -> backup (if main exists)
      // 3. Move temp -> main
      Path main = file(u);
      Path backup = backupFile(u);
      Path temp = dir.resolve(u + ".dat_tmp");
      
      try{
         // Write to temp file first
         try(OutputStream os = Files.newOutputStream(temp); GZIPOutputStream gz = new GZIPOutputStream(os); DataOutputStream outStr = new DataOutputStream(gz)){
            NbtIo.writeUnnamedTagWithFallback(out, outStr);
         }
         
         // Rotate: main -> backup (only if main exists and is valid)
         if(Files.exists(main)){
            try{
               Files.move(main, backup, StandardCopyOption.REPLACE_EXISTING);
            }catch(Exception ex){
               BorisLib.LOGGER.warn("Failed to create backup for player {}, continuing anyway: {}", u, ex.getMessage());
            }
         }
         
         // Move temp -> main
         Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING);
         BorisLib.LOGGER.debug("Successfully saved player data for {}", u);
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
    * Safely encodes a StorableData object to NBT.
    * Returns null if encoding fails, with a warning logged.
    */
   @Nullable
   private static CompoundTag encode(StorableData data, String keyId){
      if(data == null){
         BorisLib.LOGGER.warn("Cannot encode null value for key {}", keyId);
         return null;
      }
      try{
         CompoundTag tag = new CompoundTag();
         data.writeNbt(tag);
         return tag;
      }catch(Exception e){
         BorisLib.LOGGER.warn("Failed to encode data for key {}: {}", keyId, e.getMessage());
         return null;
      }
   }
   
   /**
    * Safely decodes NBT into a StorableData object using ValueInput.
    * Creates a default instance and populates it field-by-field for partial decode resilience.
    * Returns null only if the default factory fails.
    */
   @Nullable
   private static <T extends StorableData> T decode(DataKey<T> key, CompoundTag tag, UUID playerUuid, String keyId){
      try{
         // Create a fresh default instance
         T instance = key.makeDefaultPlayer(playerUuid);
         if(instance == null){
            BorisLib.LOGGER.error("DataKey<{}> default factory returned null during decode for player {}", keyId, playerUuid);
            return null;
         }
         
         if(BorisLib.SERVER == null){
            BorisLib.LOGGER.error("Cannot decode player key {} for player {} - server is null", keyId, playerUuid);
            return null;
         }
         
         // Read field-by-field - each field handles its own errors/defaults
         ValueInput view = TagValueInput.create(new ProblemReporter.ScopedCollector(LogUtils.getLogger()), BorisLib.SERVER.registryAccess(), tag);
         instance.read(view);
         return instance;
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to decode data for key {} player {}: {}", keyId, playerUuid, e.getMessage());
         BorisLib.LOGGER.warn("  NBT contents: {}", tag.isEmpty() ? "(empty)" : tag);
         return null;
      }
   }
}
