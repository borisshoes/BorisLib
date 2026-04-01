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

import java.util.HashMap;
import java.util.Map;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public final class WorldState extends SavedData {
   public static final String FILE_ID = MOD_ID + "_world";
   private final Map<String, Map<String, CompoundTag>> data = new HashMap<>();
   private final Map<String, Map<String, Object>> objects = new HashMap<>();
   
   // Codec that reads and writes the raw compound structure
   public static final Codec<WorldState> CODEC = Codec.PASSTHROUGH.xmap(
         dynamic -> {
            WorldState s = new WorldState();
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
                                 BorisLib.LOGGER.warn("Failed to parse world data key {}:{}: {}", modId, key, e.getMessage());
                              }
                           }
                           s.data.put(modId, inner);
                        }
                     }catch(Exception e){
                        BorisLib.LOGGER.warn("Failed to parse world data for mod {}: {}", modId, e.getMessage());
                     }
                  }
               }
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to parse world state data: {}", e.getMessage());
            }
            return s;
         },
         state -> {
            try{
               return new Dynamic<>(NbtOps.INSTANCE, state.save());
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to encode world state: {}", e.getMessage());
               return new Dynamic<>(NbtOps.INSTANCE, new CompoundTag());
            }
         }
   );
   
   // Use SAVED_DATA_COMMAND_STORAGE to prevent DFU from mangling custom mod data during Minecraft version upgrades.
   // LEVEL's schema applies level.dat-specific fixes that strip unrecognized keys, causing data loss.
   // SAVED_DATA_COMMAND_STORAGE uses a permissive schema designed for arbitrary compound data.
   public static final SavedDataType<WorldState> TYPE = new SavedDataType<>(Identifier.parse(FILE_ID), WorldState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
   
   public static WorldState get(ServerLevel w){
      return w.getDataStorage().computeIfAbsent(TYPE);
   }
   
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
                        BorisLib.LOGGER.warn("Skipping save for world key {}:{} - encoded data is empty/invalid", modId, key);
                     }
                  }
               }catch(Exception e){
                  BorisLib.LOGGER.error("Failed to encode world key {}:{}: {}", modId, key, e.getMessage());
               }
            }
            if(!modTag.isEmpty()) tag.put(modId, modTag);
         }catch(Exception e){
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
            BorisLib.LOGGER.error("Failed to copy raw world data for mod {}: {}", modId, e.getMessage());
         }
      }
      return tag;
   }
   
   @SuppressWarnings("unchecked")
   public <T extends StorableData> T getLive(ResourceKey<Level> worldKey, DataKey<T> key){
      Map<String, Object> modObjs = objects.computeIfAbsent(key.modId(), k -> new HashMap<>());
      Object got = modObjs.get(key.key());
      if(got != null) return (T) got;
      
      Map<String, CompoundTag> modRaw = data.get(key.modId());
      if(modRaw != null){
         CompoundTag tag = modRaw.remove(key.key());
         if(tag != null){
            T decoded = decode(key, tag, worldKey, key.id().toString());
            if(decoded != null){
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
      modObjs.put(key.key(), created);
      setDirty();
      return created;
   }
   
   public <T extends StorableData> void setLive(ResourceKey<Level> worldKey, DataKey<T> key, T value){
      T toStore = value != null ? value : key.makeDefaultWorld(worldKey);
      if(toStore == null){
         BorisLib.LOGGER.error("Cannot store null value for world key {} and default factory also returned null", key.id());
         return;
      }
      objects.computeIfAbsent(key.modId(), k -> new HashMap<>()).put(key.key(), toStore);
      Map<String, CompoundTag> modRaw = data.get(key.modId());
      if(modRaw != null){
         modRaw.remove(key.key());
         if(modRaw.isEmpty()) data.remove(key.modId());
      }
      setDirty();
   }
   
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