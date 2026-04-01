package net.borisshoes.borislib.datastorage;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataAccess {
   private static volatile PlayerObjectStore playerStore;
   private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();
   
   // Legacy dimension folder names used in Minecraft 1.21.x and earlier
   private static final Map<ResourceKey<Level>, String> LEGACY_DIMENSION_FOLDERS = Map.of(
         Level.OVERWORLD, ".",        // overworld data was at worldRoot/data/
         Level.NETHER, "DIM-1",       // nether data was at worldRoot/DIM-1/data/
         Level.END, "DIM1"            // end data was at worldRoot/DIM1/data/
   );
   
   public static void onServerStarted(MinecraftServer server){
      try{
         Path root = server.getWorldPath(LevelResource.ROOT);
         
         // Migrate legacy saved data files from 1.21.x flat paths to 26.1 namespaced/dimension paths.
         // Must run BEFORE any data access so SavedDataStorage finds files at the correct new locations.
         migrateLegacySavedData(root);
         
         playerStore = new PlayerObjectStore(root);
         BorisLib.LOGGER.info("BorisLib data storage initialized at {}", root);
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to initialize BorisLib data storage: {}", e.getMessage());
      }
   }
   
   /**
    * Fallback migration for BorisLib saved data files from the 1.21.x directory layout to 26.1.
    * <p>
    * The primary migration is handled by {@code DimensionStorageFileFixMixin} which hooks into
    * Minecraft's file-fix system during world upgrade. This fallback catches edge cases where
    * the mixin didn't fire (e.g., interrupted upgrade, mod installed after upgrade).
    * <p>
    * Only handles vanilla dimensions (overworld, nether, end). Modded dimensions require the
    * dimension mod to handle its own folder restructuring — same limitation as vanilla's
    * {@code DimensionStorageFileFix}.
    */
   private static void migrateLegacySavedData(Path worldRoot){
      String[] fileIds = {GlobalState.FILE_ID, WorldState.FILE_ID};
      
      for(var entry : LEGACY_DIMENSION_FOLDERS.entrySet()){
         ResourceKey<Level> dimension = entry.getKey();
         String legacyFolder = entry.getValue();
         
         // Old path: worldRoot/<legacyFolder>/data/<name>.dat
         Path oldDataDir = legacyFolder.equals(".")
               ? worldRoot.resolve("data")
               : worldRoot.resolve(legacyFolder).resolve("data");
         
         // New path: worldRoot/dimensions/<ns>/<name>/data/<ns>/<name>.dat
         // DimensionType.getStorageFolder gives us: worldRoot/dimensions/<ns>/<name>
         Path newDimensionDir = DimensionType.getStorageFolder(dimension, worldRoot);
         Path newDataDir = newDimensionDir.resolve("data");
         
         for(String fileId : fileIds){
            // Skip GlobalState for non-overworld dimensions (it's only stored in overworld)
            if(fileId.equals(GlobalState.FILE_ID) && dimension != Level.OVERWORLD) continue;
            
            String fileName = fileId + ".dat";
            Path oldFile = oldDataDir.resolve(fileName);
            // Identifier.parse(fileId) defaults to "minecraft:" namespace
            Path newFile = newDataDir.resolve("minecraft").resolve(fileName);
            
            if(Files.exists(oldFile) && !Files.exists(newFile)){
               try{
                  Files.createDirectories(newFile.getParent());
                  Files.move(oldFile, newFile);
                  BorisLib.LOGGER.info("Migrated legacy saved data for {}: {} -> {}", dimension.identifier(), oldFile, newFile);
               }catch(Exception e){
                  BorisLib.LOGGER.error("Failed to migrate saved data for {}: {} -> {}: {}", dimension.identifier(), oldFile, newFile, e.getMessage());
                  // Try copy as fallback (move may fail across filesystems)
                  try{
                     Files.copy(oldFile, newFile);
                     BorisLib.LOGGER.info("Copied legacy saved data as fallback for {}: {} -> {}", dimension.identifier(), oldFile, newFile);
                  }catch(Exception e2){
                     BorisLib.LOGGER.error("Failed to copy saved data for {}: {}", dimension.identifier(), e2.getMessage());
                  }
               }
            }
         }
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
   }
   
   public static <T extends StorableData> Map<UUID, T> allPlayerDataFor(DataKey<T> key){
      ensureServerSide("allPlayerDataFor");
      Map<UUID, T> out = new HashMap<>();
      
      // Discover ALL player UUIDs: online players + cached + on-disk files
      Set<UUID> allUuids = playerStore.listAllStoredUuids();
      for(ServerPlayer sp : BorisLib.SERVER.getPlayerList().getPlayers()){
         allUuids.add(sp.getUUID());
      }
      
      // Load data for every known player
      for(UUID uuid : allUuids){
         try{
            T value = playerStore.getLive(uuid, key);
            if(value != null){
               out.put(uuid, value);
            }
         }catch(Exception e){
            BorisLib.LOGGER.warn("Failed to load data for key {} player {}: {}", key.id(), uuid, e.getMessage());
         }
      }
      
      return out;
   }
}