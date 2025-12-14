package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DataAccess {
   private static volatile PlayerObjectStore playerStore;
   private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();
   
   // TODO, load on first access
   public static void onServerStarted(MinecraftServer s){
      Path root = s.getOverworld().getServer().getSavePath(WorldSavePath.ROOT);
      playerStore = new PlayerObjectStore(root);
   }
   
   public static void onServerStop(MinecraftServer s){
      if(playerStore == null) return;
      // Flush everything we currently have cached
      for(UUID u : playerStore.snapshotAllObjects().keySet()){
         playerStore.save(u);
      }
      DIRTY_PLAYERS.clear();
   }
   
   public static void onServerSave(MinecraftServer s, boolean flush, boolean force){
      if(playerStore == null) return;
      
      // Always auto-save ALL ONLINE players during any save (autosave or manual).
      for(ServerPlayerEntity sp : s.getPlayerManager().getPlayerList()){
         playerStore.save(sp.getUuid());
         DIRTY_PLAYERS.remove(sp.getUuid());
      }
      
      for(UUID u : DIRTY_PLAYERS){
         playerStore.save(u);
      }
      DIRTY_PLAYERS.clear();
   }
   
   public static void onPlayerQuit(ServerPlayerEntity p){
      if(playerStore != null) playerStore.save(p.getUuid());
      DIRTY_PLAYERS.remove(p.getUuid());
   }
   
   public static void onPlayerJoin(ServerPlayerEntity p){
      if(playerStore != null){
         playerStore.save(p.getUuid());
         DIRTY_PLAYERS.remove(p.getUuid());
         playerStore.preload(p.getUuid()); // loads/creates the entry; leaves values lazily decoded
      }
   }
   
   public static void markPlayerDirty(UUID u){
      DIRTY_PLAYERS.add(u);
   }
   
   public static <T> T getGlobal(DataKey<T> key){
      GlobalState s = GlobalState.get(BorisLib.SERVER.getOverworld());
      return s.getLive(key);
   }
   
   public static <T> T getWorld(RegistryKey<World> wk, DataKey<T> key){
      ServerWorld w = BorisLib.SERVER.getWorld(wk);
      WorldState s = WorldState.get(w);
      return s.getLive(wk, key);
   }
   
   public static <T> T getPlayer(UUID u, DataKey<T> key){
      T v = playerStore.getLive(u, key);
      DIRTY_PLAYERS.add(u); // always schedule this player for serialization on next save
      return v;
   }
   
   public static <T> void setGlobal(DataKey<T> key, T value){
      GlobalState s = GlobalState.get(BorisLib.SERVER.getOverworld());
      s.setLive(key, value);
   }
   
   public static <T> void setWorld(ServerWorld w, DataKey<T> key, T value){
      WorldState s = WorldState.get(w);
      s.setLive(key, value);
   }
   
   public static <T> void setPlayer(UUID u, DataKey<T> key, T value){
      playerStore.setLive(u, key, value);
      DIRTY_PLAYERS.add(u);
   }
   
   private static <T> NbtCompound encode(Codec<T> codec, T v){
      return (NbtCompound) codec.encodeStart(NbtOps.INSTANCE, v).result().orElse(new NbtCompound());
   }
   
   private static <T> T decode(Codec<T> codec, NbtCompound tag){
      return codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElseThrow();
   }
   
   public static <T> Map<UUID, T> allPlayerDataFor(DataKey<T> key){
      Map<UUID, T> out = new HashMap<>();
      
      // Always include online players (force-decode into live objects)
      for(ServerPlayerEntity sp : BorisLib.SERVER.getPlayerManager().getPlayerList()){
         out.put(sp.getUuid(), playerStore.getLive(sp.getUuid(), key));
      }
      
      // Include any additional cached offline players (objects snapshot)
      playerStore.snapshotAllObjects().forEach((uuid, mods) -> {
         if(out.containsKey(uuid)) return;
         Map<String, Object> tg = mods.get(key.modId());
         if(tg != null){
            @SuppressWarnings("unchecked")
            T v = (T) tg.get(key.key());
            if(v != null) out.put(uuid, v);
         }
      });
      
      return out;
   }
}