package net.borisshoes.borislib.datastorage;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Server-wide {@link SavedData} holder that backs every GLOBAL-scoped {@link DataKey} for every mod that
 * registers one with BorisLib.
 *
 * <p>Data is stored as a two-level map: <em>mod id → key → encoded NBT</em>. Values are loaded lazily:
 * the encoded NBT sits in {@link #data} until first access via {@link #getLive(DataKey)}, at which point
 * it is decoded into a live {@link StorableData} instance and moved into {@link #objects}. On save, live
 * objects are re-encoded and any never-decoded raw entries are copied through unchanged so that data
 * from mods not currently loaded is preserved.</p>
 *
 * <p>End users should normally interact with this class through {@link DataAccess#getGlobal} /
 * {@link DataAccess#setGlobal}.</p>
 */
public final class GlobalState extends SavedData {
   /** SavedData file id (without extension) used by Minecraft's storage layer. */
   public static final String FILE_ID = MOD_ID + "_global";
   private final Map<String, Map<String, CompoundTag>> data = new HashMap<>();
   private final Map<String, Map<String, Object>> objects = new HashMap<>();
   
   // Codec that reads and writes the raw compound structure
   /** Pass-through codec that round-trips the entire global map as raw NBT. */
   public static final Codec<GlobalState> CODEC = Codec.PASSTHROUGH.xmap(
         dynamic -> {
            GlobalState s = new GlobalState();
            try{
               Tag tag = dynamic.getValue() instanceof Tag t ? t : null;
               if(tag instanceof CompoundTag root){
                  for(String modId : root.keySet()){
                     try{
                        if(root.get(modId) instanceof CompoundTag modTag){
                           Map<String, CompoundTag> inner = new HashMap<>();
                           for(String key : modTag.keySet()){
                              try{
                                 if(modTag.get(key) instanceof CompoundTag keyTag){
                                    inner.put(key, keyTag);
                                 }
                              }catch(Exception e){
                                 BorisLib.LOGGER.warn("Failed to parse global data key {}:{}: {}", modId, key, e.getMessage());
                              }
                           }
                           s.data.put(modId, inner);
                        }
                     }catch(Exception e){
                        BorisLib.LOGGER.warn("Failed to parse global data for mod {}: {}", modId, e.getMessage());
                     }
                  }
               }
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to parse global state data: {}", e.getMessage());
            }
            return s;
         },
         state -> {
            try{
               return new Dynamic<>(NbtOps.INSTANCE, state.save());
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to encode global state: {}", e.getMessage());
               return new Dynamic<>(NbtOps.INSTANCE, new CompoundTag());
            }
         }
   );
   
   // Use SAVED_DATA_COMMAND_STORAGE to prevent DFU from mangling custom mod data during Minecraft version upgrades.
   // LEVEL's schema applies level.dat-specific fixes that strip unrecognized keys, causing data loss.
   // SAVED_DATA_COMMAND_STORAGE uses a permissive schema designed for arbitrary compound data.
   /** Registered {@link SavedDataType} used by Minecraft's SavedDataStorage. */
   public static final SavedDataType<GlobalState> TYPE = new SavedDataType<>(Identifier.parse(FILE_ID), GlobalState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
   
   /**
    * Looks up (or lazily creates) the {@code GlobalState} attached to the server's overworld.
    *
    * @param ow the overworld {@link ServerLevel}
    * @return the singleton {@code GlobalState} for this server
    * @throws IllegalArgumentException if {@code ow} is null
    */
   public static GlobalState get(ServerLevel ow){
      if(ow == null){
         throw new IllegalArgumentException("GlobalState.get() received null overworld ServerLevel. Ensure the server is fully started before accessing global data.");
      }
      return ow.getDataStorage().computeIfAbsent(TYPE);
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
                        BorisLib.LOGGER.warn("Skipping save for global key {}:{} - encoded data is empty/invalid", modId, key);
                     }
                  }
               }catch(Exception e){
                  BorisLib.LOGGER.error("Failed to encode global key {}:{}: {}", modId, key, e.getMessage());
               }
            }
            if(!modTag.isEmpty()) tag.put(modId, modTag);
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to save global data for mod {}: {}", modId, e.getMessage());
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
            BorisLib.LOGGER.error("Failed to copy raw global data for mod {}: {}", modId, e.getMessage());
         }
      }
      return tag;
   }
   
   /**
    * Reads or lazily decodes the value for a GLOBAL-scoped {@link DataKey}, falling back to the key's
    * default factory when no stored value is available. Marks the state dirty so the value is persisted.
    *
    * @param key the registered global key
    * @param <T> the data type
    * @return the live value (never {@code null})
    * @throws IllegalStateException if the default factory returns {@code null}
    */
   @SuppressWarnings("unchecked")
   public <T extends StorableData> T getLive(DataKey<T> key){
      Map<String, Object> modObjs = objects.computeIfAbsent(key.modId(), k -> new HashMap<>());
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
            T decoded = decode(key, tag, key.id().toString());
            if(decoded != null){
               decoded.setDirtyCallback(this::setDirty);
               modObjs.put(key.key(), decoded);
               if(modRaw.isEmpty()) data.remove(key.modId());
               setDirty();
               return decoded;
            }
            // decode returned null (corrupted data), fall through to create default
            BorisLib.LOGGER.warn("Corrupted data for global key {}, using default value", key.id());
            BorisLib.LOGGER.warn("  NBT contents: {}", tag.isEmpty() ? "(empty)" : tag);
            if(modRaw.isEmpty()) data.remove(key.modId());
         }
      }
      
      T created = key.makeDefaultGlobal();
      if(created == null){
         BorisLib.LOGGER.error("DataKey<{}> default factory returned null for GLOBAL scope. This is a critical error.", key.id());
         throw new IllegalStateException("DataKey<" + key.id() + "> default factory returned null for GLOBAL scope");
      }
      created.setDirtyCallback(this::setDirty);
      modObjs.put(key.key(), created);
      setDirty();
      return created;
   }
   
   /**
    * Replaces the live value associated with the given key. Passing {@code null} substitutes the key's
    * default-factory value. Marks the state dirty.
    *
    * @param key   the registered global key
    * @param value the new value (or {@code null} to reset to default)
    * @param <T>   the data type
    */
   public <T extends StorableData> void setLive(DataKey<T> key, T value){
      T toStore = value != null ? value : key.makeDefaultGlobal();
      if(toStore == null){
         BorisLib.LOGGER.error("Cannot store null value for global key {} and default factory also returned null", key.id());
         return;
      }
      toStore.setDirtyCallback(this::setDirty);
      objects.computeIfAbsent(key.modId(), k -> new HashMap<>()).put(key.key(), toStore);
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
         BorisLib.LOGGER.warn("Cannot encode null value for global key {}", keyId);
         return null;
      }
      try{
         CompoundTag tag = new CompoundTag();
         data.writeNbt(tag);
         return tag;
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to encode global data for key {}: {}", keyId, e.getMessage());
         return null;
      }
   }
   
   @Nullable
   private static <T extends StorableData> T decode(DataKey<T> key, CompoundTag tag, String keyId){
      try{
         T instance = key.makeDefaultGlobal();
         if(instance == null){
            BorisLib.LOGGER.error("DataKey<{}> default factory returned null during decode", keyId);
            return null;
         }
         
         if(BorisLib.SERVER == null){
            BorisLib.LOGGER.error("Cannot decode global key {} - server is null", keyId);
            return null;
         }
         
         ValueInput view = TagValueInput.create(new ProblemReporter.ScopedCollector(LogUtils.getLogger()), BorisLib.SERVER.registryAccess(), tag);
         instance.read(view);
         return instance;
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to decode global data for key {}: {}", keyId, e.getMessage());
         BorisLib.LOGGER.warn("  NBT contents: {}", tag.isEmpty() ? "(empty)" : tag);
         return null;
      }
   }
}
