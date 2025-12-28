package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public final class GlobalState extends SavedData {
   public static final String FILE_ID = MOD_ID + "_global";
   private final Map<String, Map<String, CompoundTag>> data = new HashMap<>();
   private final Map<String, Map<String, Object>> objects = new HashMap<>();
   
   public static final Codec<GlobalState> CODEC = FlatNamespacedMap.CODEC.xmap(m -> {
      GlobalState s = new GlobalState();
      m.forEach((modId, inner) -> s.data.put(modId, new HashMap<>(inner)));
      return s;
   }, GlobalState::encodeAll);
   
   public static final SavedDataType<GlobalState> TYPE = new SavedDataType<>(FILE_ID, GlobalState::new, CODEC, DataFixTypes.LEVEL);
   
   public static GlobalState get(ServerLevel ow){
      return ow.getDataStorage().computeIfAbsent(TYPE);
   }
   
   @SuppressWarnings("unchecked")
   public <T> T getLive(DataKey<T> key){
      Map<String, Object> modObjs = objects.computeIfAbsent(key.modId(), k -> new HashMap<>());
      Object got = modObjs.get(key.key());
      if(got != null) return (T) got;
      
      Map<String, CompoundTag> modRaw = data.get(key.modId());
      if(modRaw != null){
         CompoundTag tag = modRaw.remove(key.key());
         if(tag != null){
            T decoded = decode(key.codec(), tag);
            modObjs.put(key.key(), decoded);
            if(modRaw.isEmpty()) data.remove(key.modId());
            setDirty();
            return decoded;
         }
      }
      
      T created = key.makeDefaultGlobal();
      if(created == null){
         BorisLib.LOGGER.warn("DataKey<{}> default factory returned null for GLOBAL scope. This may cause issues.", key.id());
      }
      modObjs.put(key.key(), created);
      setDirty();
      return created;
   }
   
   public <T> void setLive(DataKey<T> key, T value){
      T toStore = value != null ? value : key.makeDefaultGlobal();
      objects.computeIfAbsent(key.modId(), k -> new HashMap<>()).put(key.key(), toStore);
      Map<String, CompoundTag> modRaw = data.get(key.modId());
      if(modRaw != null){
         modRaw.remove(key.key());
         if(modRaw.isEmpty()) data.remove(key.modId());
      }
      setDirty();
   }
   
   Map<String, Map<String, CompoundTag>> encodeAll(){
      Map<String, Map<String, CompoundTag>> out = new HashMap<>();
      data.forEach((modId, inner) -> out.put(modId, new HashMap<>(inner)));
      objects.forEach((modId, objMap) -> {
         Map<String, CompoundTag> tgt = out.computeIfAbsent(modId, k -> new HashMap<>());
         objMap.forEach((key, value) -> {
            DataKey<Object> dk = DataRegistry.get(modId, key, DataKey.StorageScope.GLOBAL);
            if(dk != null){
               CompoundTag enc = encode(dk.codec(), value);
               tgt.put(key, enc);
            }
         });
      });
      return out;
   }
   
   public Map<String, Map<String, CompoundTag>> map(){
      return data;
   }
   
   private static <T> CompoundTag encode(Codec<T> codec, Object v){
      @SuppressWarnings("unchecked")
      T cast = (T) v;
      return (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, cast).result().orElse(new CompoundTag());
   }
   
   private static <T> T decode(Codec<T> codec, CompoundTag tag){
      return codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElseThrow();
   }
}