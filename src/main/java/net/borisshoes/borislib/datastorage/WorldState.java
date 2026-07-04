package net.borisshoes.borislib.datastorage;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Per-dimension {@link SavedData} holder that backs every WORLD-scoped {@link DataKey} for every mod that
 * registers one with BorisLib. Acts exactly like {@link GlobalState} but is attached per
 * {@link ServerLevel} rather than once per server.
 *
 * <p>End users should normally interact with this class through {@link DataAccess#getWorld} /
 * {@link DataAccess#setWorld}.</p>
 */
public final class WorldState extends SavedData {
   /** SavedData file id (without extension) used by Minecraft's storage layer. */
   public static final String FILE_ID = MOD_ID + "_world";
   // Thread-safe: see GlobalState for rationale. Encoding runs on the main thread during autosave
   // while the static DataAccess API can be reached from other threads.
   private final Map<String, Map<String, CompoundTag>> data = new ConcurrentHashMap<>();
   private final Map<String, Map<String, Object>> objects = new ConcurrentHashMap<>();
   // Raw NBT as loaded from disk, kept per key as a save-time fallback if a live object fails to encode.
   private final Map<String, Map<String, CompoundTag>> originalRaw = new ConcurrentHashMap<>();
   // Last successfully-encoded root tag, used to avoid ever regressing to an empty/partial write.
   private volatile CompoundTag lastGoodSave = null;
   private volatile boolean restoreAttempted = false;
   
   // Codec that reads and writes the raw compound structure
   /** Pass-through codec that round-trips the entire world-state map as raw NBT. */
   public static final Codec<WorldState> CODEC = Codec.PASSTHROUGH.xmap(
         dynamic -> {
            WorldState s = new WorldState();
            try{
               Tag tag = dynamic.getValue() instanceof Tag t ? t : null;
               if(tag instanceof CompoundTag root){
                  s.ingestRoot(root);
               }
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to parse world state data: {}", e.getMessage());
            }
            return s;
         },
         state -> {
            CompoundTag out;
            try{
               out = state.save();
            }catch(Exception e){
               // Never let a transient encode failure overwrite good data with an empty tag.
               BorisLib.LOGGER.error("Failed to encode world state, preserving last known good data: {}", e.getMessage());
               out = state.lastGoodSave;
            }
            if(out == null) out = new CompoundTag();
            return new Dynamic<>(NbtOps.INSTANCE, out);
         }
   );
   
   // Use SAVED_DATA_COMMAND_STORAGE to prevent DFU from mangling custom mod data during Minecraft version upgrades.
   // LEVEL's schema applies level.dat-specific fixes that strip unrecognized keys, causing data loss.
   // SAVED_DATA_COMMAND_STORAGE uses a permissive schema designed for arbitrary compound data.
   /** Registered {@link SavedDataType} used by Minecraft's SavedDataStorage. */
   public static final SavedDataType<WorldState> TYPE = new SavedDataType<>(Identifier.parse(FILE_ID), WorldState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
   
   /**
    * Looks up (or lazily creates) the {@code WorldState} attached to a given dimension.
    *
    * @param w the {@link ServerLevel} to fetch the state for
    * @return the {@code WorldState} for that dimension
    * @throws IllegalArgumentException if {@code w} is null
    */
   public static WorldState get(ServerLevel w){
      if(w == null){
         throw new IllegalArgumentException("WorldState.get() received null ServerLevel. Ensure the dimension is loaded before accessing world data.");
      }
      return w.getDataStorage().computeIfAbsent(TYPE);
   }
   
   /**
    * Re-encodes every live {@link StorableData} object plus all undecoded raw entries into a single
    * {@link CompoundTag} suitable for {@code SavedData} to persist.
    *
    * @return the encoded compound tag
    */
   // Custom save implementation that encodes our data
   public CompoundTag save(){
      CompoundTag tag = new CompoundTag();
      boolean hadError = false;
      
      // Encode all live objects
      for(var modEntry : objects.entrySet()){
         String modId = modEntry.getKey();
         try{
            CompoundTag modTag = new CompoundTag();
            for(var kv : modEntry.getValue().entrySet()){
               String key = kv.getKey();
               Object value = kv.getValue();
               try{
                  if(value instanceof StorableData storable){
                     CompoundTag encoded = encode(storable, modId + ":" + key);
                     if(encoded != null && !encoded.isEmpty()){
                        modTag.put(key, encoded);
                     }else{
                        hadError = true;
                        CompoundTag prev = previousFor(modId, key);
                        if(prev != null && !prev.isEmpty()){
                           modTag.put(key, prev);
                           BorisLib.LOGGER.warn("Encode for world key {}:{} was empty/invalid - kept last known good value", modId, key);
                        }else{
                           BorisLib.LOGGER.warn("Skipping save for world key {}:{} - encoded data is empty/invalid and no prior value exists", modId, key);
                        }
                     }
                  }
               }catch(Exception e){
                  hadError = true;
                  CompoundTag prev = previousFor(modId, key);
                  if(prev != null && !prev.isEmpty()){
                     modTag.put(key, prev);
                     BorisLib.LOGGER.error("Failed to encode world key {}:{} ({}) - kept last known good value", modId, key, e.getMessage());
                  }else{
                     BorisLib.LOGGER.error("Failed to encode world key {}:{}: {}", modId, key, e.getMessage());
                  }
               }
            }
            if(!modTag.isEmpty()) tag.put(modId, modTag);
         }catch(Exception e){
            hadError = true;
            BorisLib.LOGGER.error("Failed to save world data for mod {}: {}", modId, e.getMessage());
         }
      }
      
      // Copy raw data we never decoded
      for(var modEntry : data.entrySet()){
         String modId = modEntry.getKey();
         try{
            CompoundTag modTag = tag.getCompoundOrEmpty(modId);
            if(modTag.isEmpty()){
               modTag = new CompoundTag();
            }
            for(var kv : modEntry.getValue().entrySet()){
               if(!modTag.contains(kv.getKey())){
                  modTag.put(kv.getKey(), kv.getValue());
               }
            }
            if(!modTag.isEmpty()) tag.put(modId, modTag);
         }catch(Exception e){
            hadError = true;
            BorisLib.LOGGER.error("Failed to copy raw world data for mod {}: {}", modId, e.getMessage());
         }
      }
      
      // Never regress to an empty/partial write because of an error: keep the last good copy.
      if(hadError && tag.isEmpty() && lastGoodSave != null && !lastGoodSave.isEmpty()){
         BorisLib.LOGGER.error("World save produced no data after encode errors - preserving last known good save");
         return lastGoodSave;
      }
      lastGoodSave = tag;
      return tag;
   }
   
   /** Last-persisted NBT for a key: prefer this session's last good save, then the on-disk load. */
   @Nullable
   private CompoundTag previousFor(String modId, String key){
      if(lastGoodSave != null){
         CompoundTag prevMod = lastGoodSave.getCompoundOrEmpty(modId);
         CompoundTag prevKey = prevMod.getCompoundOrEmpty(key);
         if(!prevKey.isEmpty()) return prevKey;
      }
      Map<String, CompoundTag> orig = originalRaw.get(modId);
      if(orig != null){
         CompoundTag prevKey = orig.get(key);
         if(prevKey != null && !prevKey.isEmpty()) return prevKey;
      }
      return null;
   }
   
   /** Resiliently parses a namespaced {@code mod -> key -> tag} root into {@link #data} + {@link #originalRaw}. */
   private void ingestRoot(CompoundTag root){
      for(String modId : root.keySet()){
         try{
            if(root.get(modId) instanceof CompoundTag modTag){
               Map<String, CompoundTag> inner = new ConcurrentHashMap<>();
               Map<String, CompoundTag> orig = new ConcurrentHashMap<>();
               for(String key : modTag.keySet()){
                  try{
                     if(modTag.get(key) instanceof CompoundTag keyTag){
                        inner.put(key, keyTag);
                        orig.put(key, keyTag);
                     }
                  }catch(Exception e){
                     BorisLib.LOGGER.warn("Failed to parse world data key {}:{}: {}", modId, key, e.getMessage());
                  }
               }
               if(!inner.isEmpty()){
                  data.put(modId, inner);
                  originalRaw.put(modId, orig);
               }
            }
         }catch(Exception e){
            BorisLib.LOGGER.warn("Failed to parse world data for mod {}: {}", modId, e.getMessage());
         }
      }
   }
   
   /** @return {@code true} if this state currently holds no decoded objects and no raw entries. */
   public boolean isEmptyState(){
      return objects.isEmpty() && data.isEmpty();
   }
   
   /**
    * If vanilla loaded this state empty (missing/corrupt/wiped .dat) but a BorisLib backup exists
    * for this dimension, heal the state from the backup. Runs at most once, on first access.
    *
    * @param worldKey the dimension this state belongs to
    */
   private void attemptBackupRestore(ResourceKey<Level> worldKey){
      if(restoreAttempted) return;
      restoreAttempted = true;
      if(!isEmptyState()) return;
      if(worldKey == null || !DataBackups.available()) return;
      CompoundTag backup = DataBackups.read(DataBackups.worldName(worldKey));
      if(backup == null || backup.isEmpty()) return;
      BorisLib.LOGGER.warn("WorldState for {} loaded empty but a BorisLib backup exists - restoring {} mod namespace(s) from backup", worldKey.identifier(), backup.keySet().size());
      ingestRoot(backup);
      setDirty();
   }
   
   /**
    * Reads or lazily decodes the value for a WORLD-scoped {@link DataKey}, falling back to the key's
    * default factory when no stored value is available.
    *
    * @param worldKey the dimension this state belongs to (passed to the default factory if needed)
    * @param key      the registered world key
    * @param <T>      the data type
    * @return the live value (never {@code null})
    * @throws IllegalStateException if the default factory returns {@code null}
    */
   @SuppressWarnings("unchecked")
   public <T extends StorableData> T getLive(ResourceKey<Level> worldKey, DataKey<T> key){
      attemptBackupRestore(worldKey);
      Map<String, Object> modObjs = objects.computeIfAbsent(key.modId(), k -> new ConcurrentHashMap<>());
      Object got = modObjs.get(key.key());
      if(got != null){
         // Pessimistic dirty on access — covers implementations that don't call markDirty().
         // Implementations using markDirty() also propagate immediately via the injected callback.
         setDirty();
         return (T) got;
      }
      
      Map<String, CompoundTag> modRaw = data.get(key.modId());
      if(modRaw != null){
         CompoundTag tag = modRaw.remove(key.key());
         if(tag != null){
            T decoded = decode(key, tag, worldKey, key.id().toString());
            if(decoded != null){
               decoded.setDirtyCallback(this::setDirty);
               modObjs.put(key.key(), decoded);
               if(modRaw.isEmpty()) data.remove(key.modId());
               setDirty();
               return decoded;
            }
            // decode returned null (corrupted data), fall through to create default
            BorisLib.LOGGER.warn("Corrupted data for world key {} in world {}, using default value", key.id(), worldKey);
            BorisLib.LOGGER.warn("  NBT contents: {}", tag.isEmpty() ? "(empty)" : tag);
            if(modRaw.isEmpty()) data.remove(key.modId());
         }
      }
      
      T created = key.makeDefaultWorld(worldKey);
      if(created == null){
         BorisLib.LOGGER.error("DataKey<{}> default factory returned null for world {}. This is a critical error.", key.id(), worldKey);
         throw new IllegalStateException("DataKey<" + key.id() + "> default factory returned null for world " + worldKey);
      }
      created.setDirtyCallback(this::setDirty);
      modObjs.put(key.key(), created);
      setDirty();
      return created;
   }
   
   /**
    * Replaces the live value associated with the given key in this world. Passing {@code null} substitutes
    * the key's default-factory value.
    *
    * @param worldKey the dimension being written to
    * @param key      the registered world key
    * @param value    the new value (or {@code null} to reset to default)
    * @param <T>      the data type
    */
   public <T extends StorableData> void setLive(ResourceKey<Level> worldKey, DataKey<T> key, T value){
      attemptBackupRestore(worldKey);
      T toStore = value != null ? value : key.makeDefaultWorld(worldKey);
      if(toStore == null){
         BorisLib.LOGGER.error("Cannot store null value for world key {} and default factory also returned null", key.id());
         return;
      }
      toStore.setDirtyCallback(this::setDirty);
      objects.computeIfAbsent(key.modId(), k -> new ConcurrentHashMap<>()).put(key.key(), toStore);
      Map<String, CompoundTag> modRaw = data.get(key.modId());
      if(modRaw != null){
         modRaw.remove(key.key());
         if(modRaw.isEmpty()) data.remove(key.modId());
      }
      setDirty();
   }
   
   /** @return the raw, undecoded NBT map (mod id → key → tag). For diagnostic / migration use only. */
   public Map<String, Map<String, CompoundTag>> map(){
      return data;
   }
   
   @Nullable
   private static CompoundTag encode(StorableData data, String keyId){
      if(data == null){
         BorisLib.LOGGER.warn("Cannot encode null value for world key {}", keyId);
         return null;
      }
      try{
         CompoundTag tag = new CompoundTag();
         data.writeNbt(tag);
         return tag;
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to encode world data for key {}: {}", keyId, e.getMessage());
         return null;
      }
   }
   
   @Nullable
   private static <T extends StorableData> T decode(DataKey<T> key, CompoundTag tag, ResourceKey<Level> worldKey, String keyId){
      try{
         T instance = key.makeDefaultWorld(worldKey);
         if(instance == null){
            BorisLib.LOGGER.error("DataKey<{}> default factory returned null during decode", keyId);
            return null;
         }
         
         if(BorisLib.SERVER == null){
            BorisLib.LOGGER.error("Cannot decode world key {} - server is null", keyId);
            return null;
         }
         
         ValueInput view = TagValueInput.create(new ProblemReporter.ScopedCollector(LogUtils.getLogger()), BorisLib.SERVER.registryAccess(), tag);
         instance.read(view);
         return instance;
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to decode world data for key {}: {}", keyId, e.getMessage());
         BorisLib.LOGGER.warn("  NBT contents: {}", tag.isEmpty() ? "(empty)" : tag);
         return null;
      }
   }
}
