package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataAccess {
   private static volatile PlayerObjectStore playerStore;
   private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();
   
   // TODO, load on first access
   public static void onServerStarted(MinecraftServer s){
      Path root = s.overworld().getServer().getWorldPath(LevelResource.ROOT);
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
      for(ServerPlayer sp : s.getPlayerList().getPlayers()){
         playerStore.save(sp.getUUID());
         DIRTY_PLAYERS.remove(sp.getUUID());
      }
      
      for(UUID u : DIRTY_PLAYERS){
         playerStore.save(u);
      }
      DIRTY_PLAYERS.clear();
   }
   
   public static void onPlayerQuit(ServerPlayer p){
      if(playerStore != null) playerStore.save(p.getUUID());
      DIRTY_PLAYERS.remove(p.getUUID());
   }
   
   public static void onPlayerJoin(ServerPlayer p){
      if(playerStore != null){
         UUID pid = p.getUUID();
         playerStore.save(pid);
         DIRTY_PLAYERS.remove(pid);
         playerStore.preload(pid); // loads/creates the entry; leaves values lazily decoded
         DataAccess.getPlayer(pid, BorisLib.PLAYER_DATA_KEY).onLogin(p);
      }
   }
   
   public static void markPlayerDirty(UUID u){
      ensureServerSide("markPlayerDirty");
      DIRTY_PLAYERS.add(u);
   }
   
   public static <T> T getGlobal(DataKey<T> key){
      ensureServerSide("getGlobal");
      GlobalState s = GlobalState.get(BorisLib.SERVER.overworld());
      return s.getLive(key);
   }
   
   public static <T> T getWorld(ResourceKey<Level> wk, DataKey<T> key){
      ensureServerSide("getWorld");
      ServerLevel w = BorisLib.SERVER.getLevel(wk);
      WorldState s = WorldState.get(w);
      return s.getLive(wk, key);
   }
   
   public static <T> T getPlayer(UUID u, DataKey<T> key){
      ensureServerSide("getPlayer");
      T v = playerStore.getLive(u, key);
      DIRTY_PLAYERS.add(u); // always schedule this player for serialization on next save
      return v;
   }
   
   public static <T> void setGlobal(DataKey<T> key, T value){
      ensureServerSide("setGlobal");
      GlobalState s = GlobalState.get(BorisLib.SERVER.overworld());
      s.setLive(key, value);
   }
   
   public static <T> void setWorld(ServerLevel w, DataKey<T> key, T value){
      ensureServerSide("setWorld");
      WorldState s = WorldState.get(w);
      s.setLive(w.dimension(), key, value);
   }
   
   public static <T> void setPlayer(UUID u, DataKey<T> key, T value){
      ensureServerSide("setPlayer");
      playerStore.setLive(u, key, value);
      DIRTY_PLAYERS.add(u);
   }
   
   private static void ensureServerSide(String methodName){
      if(BorisLib.SERVER == null || playerStore == null){
         throw new IllegalStateException("DataAccess." + methodName + "() must be called from server-side code. " +
               "Server or playerStore is null - this usually means you're calling from a client that isn't hosting the world.");
      }
   }
   
   private static <T> CompoundTag encode(Codec<T> codec, T v){
      return (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, v).result().orElse(new CompoundTag());
   }
   
   private static <T> T decode(Codec<T> codec, CompoundTag tag){
      return codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElseThrow();
   }
   
   public static <T> Map<UUID, T> allPlayerDataFor(DataKey<T> key){
      ensureServerSide("allPlayerDataFor");
      Map<UUID, T> out = new HashMap<>();
      
      // Always include online players (force-decode into live objects)
      for(ServerPlayer sp : BorisLib.SERVER.getPlayerList().getPlayers()){
         out.put(sp.getUUID(), playerStore.getLive(sp.getUUID(), key));
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