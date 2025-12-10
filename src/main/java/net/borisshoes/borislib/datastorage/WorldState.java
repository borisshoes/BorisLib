package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public final class WorldState extends PersistentState {
   public static final String FILE_ID = MOD_ID + "_world";
   private final Map<String, Map<String, NbtCompound>> data = new HashMap<>();
   private final Map<String, Map<String, Object>> objects = new HashMap<>();
   
   public static final Codec<WorldState> CODEC = FlatNamespacedMap.CODEC.xmap(m -> {
      WorldState s = new WorldState();
      m.forEach((modId, inner) -> s.data.put(modId, new HashMap<>(inner)));
      return s;
   }, WorldState::encodeAll);
   
   public static final PersistentStateType<WorldState> TYPE = new PersistentStateType<>(FILE_ID, WorldState::new, CODEC, DataFixTypes.LEVEL);
   
   public static WorldState get(ServerWorld w){
      return w.getPersistentStateManager().getOrCreate(TYPE);
   }
   
   @SuppressWarnings("unchecked")
   public <T> T getLive(RegistryKey<World> worldKey, DataKey<T> key){
      Map<String, Object> modObjs = objects.computeIfAbsent(key.modId(), k -> new HashMap<>());
      Object got = modObjs.get(key.key());
      if(got != null) return (T) got;
      
      Map<String, NbtCompound> modRaw = data.get(key.modId());
      if(modRaw != null){
         NbtCompound tag = modRaw.remove(key.key());
         if(tag != null){
            T decoded = decode(key.codec(), tag);
            modObjs.put(key.key(), decoded);
            if(modRaw.isEmpty()) data.remove(key.modId());
            markDirty();
            return decoded;
         }
      }
      
      T created = key.makeDefaultWorld(worldKey);
      modObjs.put(key.key(), created);
      markDirty();
      return created;
   }
   
   public <T> void setLive(DataKey<T> key, T value){
      objects.computeIfAbsent(key.modId(), k -> new HashMap<>()).put(key.key(), value);
      Map<String, NbtCompound> modRaw = data.get(key.modId());
      if(modRaw != null){
         modRaw.remove(key.key());
         if(modRaw.isEmpty()) data.remove(key.modId());
      }
      markDirty();
   }
   
   Map<String, Map<String, NbtCompound>> encodeAll(){
      Map<String, Map<String, NbtCompound>> out = new HashMap<>();
      data.forEach((modId, inner) -> out.put(modId, new HashMap<>(inner)));
      objects.forEach((modId, objMap) -> {
         Map<String, NbtCompound> tgt = out.computeIfAbsent(modId, k -> new HashMap<>());
         objMap.forEach((key, value) -> {
            DataKey<Object> dk = DataRegistry.get(modId, key, DataKey.StorageScope.WORLD);
            if(dk != null){
               NbtCompound enc = encode(dk.codec(), value);
               tgt.put(key, enc);
            }
         });
      });
      return out;
   }
   
   public Map<String, Map<String, NbtCompound>> map(){
      return data;
   }
   
   private static <T> NbtCompound encode(Codec<T> codec, Object v){
      @SuppressWarnings("unchecked")
      T cast = (T) v;
      return (NbtCompound) codec.encodeStart(NbtOps.INSTANCE, cast).result().orElse(new NbtCompound());
   }
   
   private static <T> T decode(Codec<T> codec, NbtCompound tag){
      return codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElseThrow();
   }
}