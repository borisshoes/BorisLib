package net.borisshoes.borislib.datastorage;

import net.borisshoes.borislib.BorisLib;
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
   
   public static void onServerStarted(MinecraftServer server){
      try{
         Path root = server.getWorldPath(LevelResource.ROOT);
         playerStore = new PlayerObjectStore(root);
         BorisLib.LOGGER.info("BorisLib data storage initialized at {}", root);
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to initialize BorisLib data storage: {}", e.getMessage());
      }
   }
   
   public static void onServerStop(MinecraftServer s){
      if(playerStore == null){
         BorisLib.LOGGER.warn("PlayerStore is null during server stop, skipping data save");
         return;
      }
      // Flush everything we currently have cached
      try{
         int count = 0;
         for(UUID u : playerStore.snapshotAllObjects().keySet()){
            playerStore.save(u);
            count++;
         }
         BorisLib.LOGGER.info("Saved {} player data entries on server stop", count);
      }catch(Exception e){
         BorisLib.LOGGER.error("Error during server stop data save: {}", e.getMessage());
      }
      DIRTY_PLAYERS.clear();
   }
   
   public static void onServerSave(MinecraftServer s, boolean flush, boolean force){
      if(playerStore == null){
         BorisLib.LOGGER.warn("PlayerStore is null during server save, skipping");
         return;
      }
      
      try{
         // Always auto-save ALL ONLINE players during any save (autosave or manual).
         for(ServerPlayer sp : s.getPlayerList().getPlayers()){
            try{
               playerStore.save(sp.getUUID());
               DIRTY_PLAYERS.remove(sp.getUUID());
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to save data for online player {}: {}", sp.getUUID(), e.getMessage());
            }
         }
         
         for(UUID u : DIRTY_PLAYERS){
            try{
               playerStore.save(u);
            }catch(Exception e){
               BorisLib.LOGGER.error("Failed to save data for dirty player {}: {}", u, e.getMessage());
            }
         }
         DIRTY_PLAYERS.clear();
      }catch(Exception e){
         BorisLib.LOGGER.error("Error during server save: {}", e.getMessage());
      }
   }
   
   public static void onPlayerQuit(ServerPlayer p){
      if(playerStore != null){
         try{
            playerStore.save(p.getUUID());
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to save data on player quit for {}: {}", p.getUUID(), e.getMessage());
         }
      }
      DIRTY_PLAYERS.remove(p.getUUID());
   }
   
   public static void onPlayerJoin(ServerPlayer p){
      if(playerStore != null){
         try{
            UUID pid = p.getUUID();
            playerStore.preload(pid);
            DataAccess.getPlayer(pid, BorisLib.PLAYER_DATA_KEY).onLogin(p);
            DIRTY_PLAYERS.add(pid); // Mark dirty so it gets saved on next server save
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to process player join for {}: {}", p.getUUID(), e.getMessage());
         }
      }else{
         BorisLib.LOGGER.warn("PlayerStore is null during player join for {}", p.getUUID());
      }
   }
   
   public static void markPlayerDirty(UUID u){
      ensureServerSide("markPlayerDirty");
      DIRTY_PLAYERS.add(u);
   }
   
   public static <T extends StorableData> T getGlobal(DataKey<T> key){
      ensureServerSide("getGlobal");
      GlobalState s = GlobalState.get(BorisLib.SERVER.overworld());
      return s.getLive(key);
   }
   
   public static <T extends StorableData> T getWorld(ResourceKey<Level> wk, DataKey<T> key){
      ensureServerSide("getWorld");
      ServerLevel w = BorisLib.SERVER.getLevel(wk);
      WorldState s = WorldState.get(w);
      return s.getLive(wk, key);
   }
   
   public static <T extends StorableData> T getPlayer(UUID u, DataKey<T> key){
      ensureServerSide("getPlayer");
      T v = playerStore.getLive(u, key);
      DIRTY_PLAYERS.add(u); // always schedule this player for serialization on next save
      return v;
   }
   
   public static <T extends StorableData> void setGlobal(DataKey<T> key, T value){
      ensureServerSide("setGlobal");
      GlobalState s = GlobalState.get(BorisLib.SERVER.overworld());
      s.setLive(key, value);
   }
   
   public static <T extends StorableData> void setWorld(ServerLevel w, DataKey<T> key, T value){
      ensureServerSide("setWorld");
      WorldState s = WorldState.get(w);
      s.setLive(w.dimension(), key, value);
   }
   
   public static <T extends StorableData> void setPlayer(UUID u, DataKey<T> key, T value){
      ensureServerSide("setPlayer");
      playerStore.setLive(u, key, value);
      DIRTY_PLAYERS.add(u);
   }
   
   private static void ensureServerSide(String methodName){
      if(BorisLib.SERVER == null || playerStore == null){
         throw new IllegalStateException("DataAccess." + methodName + "() must be called from server-side code. " +
               "Server or playerStore is null - this usually means you're calling from a client that isn't hosting the world.");
      }
      
      if(!BorisLib.SERVER.isSameThread()){
         throw new IllegalStateException("DataAccess." + methodName + "() called from non-server thread");
      }
   }
   
   public static <T extends StorableData> Map<UUID, T> allPlayerDataFor(DataKey<T> key){
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