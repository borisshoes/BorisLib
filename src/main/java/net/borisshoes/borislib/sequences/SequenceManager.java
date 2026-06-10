package net.borisshoes.borislib.sequences;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all active {@link PlayerSequence} instances.
 *
 * <p>All methods are static; the class acts as a singleton service.
 *
 * <h3>Safety guarantees</h3>
 * <ul>
 *   <li><b>Disconnect</b> — sequence is cancelled gracefully and the player's state is
 *       restored immediately so vanilla saves the correct values to disk.</li>
 *   <li><b>Death</b> — sequence is cancelled; game-mode/flags are restored so the
 *       player respawns in the correct state.</li>
 *   <li><b>Server restart</b> — sequences always cancel-and-restore; they never survive
 *       across restarts. Any dangling {@link PlayerSnapshot} found on the next login is
 *       applied immediately to correct the player's state.</li>
 *   <li><b>Crash recovery</b> — orphaned camera entities from crashed cutscenes are
 *       discovered on startup (via {@link #onServerStart}) and discarded as soon as
 *       their chunk loads (via {@link #onEntityLoad}).</li>
 *   <li><b>Exception safety</b> — every lifecycle callback is wrapped so a buggy
 *       sequence implementation cannot prevent state restoration.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Start a cutscene:
 * SequenceManager.start(player, new CutsceneSequence(player.getUUID(), path, 200));
 *
 * // Cancel programmatically:
 * SequenceManager.cancel(player);
 *
 * // Query:
 * if (SequenceManager.isInSequence(player.getUUID(), CutsceneSequence.class)) { ... }
 * }</pre>
 */
public final class SequenceManager {
   
   private static final Logger LOGGER = LogManager.getLogger("BorisLib/SequenceManager");
   
   /**
    * All currently active sequences, keyed by player UUID.
    */
   private static final Map<UUID, PlayerSequence> ACTIVE = new ConcurrentHashMap<>();
   
   /**
    * Camera-entity UUIDs that need to be discarded once their chunk loads.
    * Populated during {@link #onServerStart} for crash-recovered cameras.
    */
   private static final Set<UUID> PENDING_CAMERA_CLEANUP = ConcurrentHashMap.newKeySet();
   
   private SequenceManager(){
   }
   
   // ═══════════════════════════════ public API ═══════════════════════════════
   
   /**
    * Starts a sequence for the given player.
    *
    * <p>If the player is already in a sequence, that sequence is gracefully cancelled
    * first before starting the new one.
    *
    * @return {@code true} if the sequence started successfully.
    */
   public static boolean start(ServerPlayer player, PlayerSequence sequence){
      UUID uuid = player.getUUID();
      
      // If already in a sequence, cancel it cleanly.
      if(ACTIVE.containsKey(uuid)){
         LOGGER.warn("Player {} started a new sequence while already in one — force-cancelling previous.", uuid);
         cancelInternal(uuid, player);
      }
      
      // Capture snapshot BEFORE calling onStart (so the original state is preserved
      // even if onStart changes the player's game-mode or position).
      saveSnapshot(player, sequence.getClass().getSimpleName());
      
      try{
         sequence.onStart(player);
      }catch(Exception e){
         LOGGER.error("Exception in {}.onStart for player {}: {}", sequence.getClass().getSimpleName(), uuid, e.getMessage());
         e.printStackTrace();
         // Restore immediately; don't leave the player in a broken state.
         restoreFromSnapshot(player);
         clearSnapshot(uuid);
         return false;
      }
      
      ACTIVE.put(uuid, sequence);
      return true;
   }
   
   /**
    * Cancels the active sequence for the given player, restoring their state from
    * the saved snapshot. No-op if the player has no active sequence.
    */
   public static void cancel(ServerPlayer player){
      cancelInternal(player.getUUID(), player);
   }
   
   // ═══════════════════════════════ tick ═════════════════════════════════════
   
   /**
    * Ticks all active sequences. Called every server tick from
    * {@link BorisLib#onInitialize} via {@code ServerTickEvents.END_SERVER_TICK}.
    */
   public static void tick(MinecraftServer server){
      if(ACTIVE.isEmpty()) return;
      
      List<UUID> naturalEnds = new ArrayList<>();
      List<UUID> errorEnds = new ArrayList<>();
      
      for(Map.Entry<UUID, PlayerSequence> entry : ACTIVE.entrySet()){
         UUID uuid = entry.getKey();
         PlayerSequence seq = entry.getValue();
         
         ServerPlayer player = server.getPlayerList().getPlayer(uuid);
         if(player == null || player.isRemoved()){
            // Player went offline — snapshot is on disk, restore on next login.
            errorEnds.add(uuid);
            continue;
         }
         
         int tick = seq.getTicksElapsed();
         try{
            seq.onTick(player, tick);
         }catch(Exception e){
            LOGGER.error("Exception in {}.onTick (tick={}) for player {}: {}",
                  seq.getClass().getSimpleName(), tick, uuid, e.getMessage());
            e.printStackTrace();
            errorEnds.add(uuid);
            continue;
         }
         seq.incrementTick();
         
         int duration = seq.getDurationTicks();
         if(duration >= 0 && seq.getTicksElapsed() >= duration){
            naturalEnds.add(uuid);
         }
      }
      
      // Process natural completions (cancelled=false).
      for(UUID uuid : naturalEnds){
         ServerPlayer player = server.getPlayerList().getPlayer(uuid);
         PlayerSequence seq = ACTIVE.remove(uuid);
         if(seq != null && player != null && !player.isRemoved()){
            try{
               seq.onEnd(player, false);
            }catch(Exception e){
               LOGGER.error("Exception in {}.onEnd(natural) for player {}: {}",
                     seq.getClass().getSimpleName(), uuid, e.getMessage());
            }
            restoreFromSnapshot(player);
         }
         clearSnapshot(uuid);
      }
      
      // Remove errored/offline entries.
      for(UUID uuid : errorEnds){
         ACTIVE.remove(uuid);
         // If the player is still online (i.e. onTick threw), restore their state immediately
         // so they are not stuck in a broken game-mode for the rest of the session.
         ServerPlayer errorPlayer = server.getPlayerList().getPlayer(uuid);
         if(errorPlayer != null && !errorPlayer.isRemoved()){
            LOGGER.warn("SequenceManager: restoring online player {} after sequence onTick error.", uuid);
            restoreFromSnapshot(errorPlayer);
            clearSnapshot(uuid);
         }
         // If offline: snapshot stays on disk and is restored when the player rejoins.
      }
   }
   
   // ════════════════════════════ safety hooks ════════════════════════════════
   
   /**
    * Called when a player disconnects. The sequence is gracefully ended and the
    * player's state is restored immediately so vanilla saves the correct values.
    * The snapshot is then cleared since restoration is already complete.
    */
   public static void onPlayerDisconnect(ServerPlayer player){
      UUID uuid = player.getUUID();
      PlayerSequence seq = ACTIVE.remove(uuid);
      if(seq == null) return;
      
      try{
         seq.onEnd(player, true);
      }catch(Exception e){
         LOGGER.error("Exception in {}.onEnd(disconnect) for player {}: {}",
               seq.getClass().getSimpleName(), uuid, e.getMessage());
      }
      restoreFromSnapshot(player);
      clearSnapshot(uuid);
   }
   
   /**
    * Called when a player joins. If a {@link PlayerSnapshot} is active (from a crash
    * or missed cleanup), the state is restored immediately and the snapshot is cleared.
    */
   public static void onPlayerJoin(ServerPlayer player){
      UUID uuid = player.getUUID();
      // Remove from active in case this player re-joined in the same session
      // without the disconnect hook firing (shouldn't normally happen).
      ACTIVE.remove(uuid);
      
      PlayerSnapshot snapshot = DataAccess.getPlayer(uuid, PlayerSnapshot.KEY);
      if(snapshot.isActive()){
         LOGGER.warn("Player {} joined with a dangling sequence snapshot (type='{}') — restoring state.",
               uuid, snapshot.getSequenceType());
         restoreFromSnapshot(player, snapshot);
         clearSnapshot(uuid);
      }
   }
   
   /**
    * Called on player death. The sequence is cancelled and the player's game-mode /
    * flags are restored immediately so they respawn in the correct state.
    * Position is intentionally NOT restored here (respawn logic handles that).
    */
   public static void onPlayerDeath(LivingEntity entity, DamageSource source){
      if(!(entity instanceof ServerPlayer player)) return;
      UUID uuid = player.getUUID();
      PlayerSequence seq = ACTIVE.remove(uuid);
      if(seq == null) return;
      
      try{
         seq.onEnd(player, true);
      }catch(Exception e){
         LOGGER.error("Exception in {}.onEnd(death) for player {}: {}",
               seq.getClass().getSimpleName(), uuid, e.getMessage());
      }
      restoreFromSnapshotOnDeath(player);
      clearSnapshot(uuid);
   }
   
   /**
    * Called once after the server has fully started and player data is available.
    * Scans all stored snapshots for orphaned camera entities (from crashed cutscenes)
    * and discards them if their chunk is already loaded, or queues them for cleanup
    * via {@link #onEntityLoad} once their chunk loads.
    */
   public static void onServerStart(MinecraftServer server){
      try{
         Map<UUID, PlayerSnapshot> allSnapshots = DataAccess.allPlayerDataFor(PlayerSnapshot.KEY);
         int found = 0;
         for(PlayerSnapshot snapshot : allSnapshots.values()){
            if(!snapshot.isActive()) continue;
            
            String camUUIDStr = snapshot.getCameraEntityUUID();
            if(camUUIDStr == null || camUUIDStr.isEmpty()) continue;
            
            try{
               UUID camUUID = UUID.fromString(camUUIDStr);
               boolean discarded = tryDiscardEntity(server, camUUID);
               if(!discarded){
                  PENDING_CAMERA_CLEANUP.add(camUUID);
               }
               found++;
            }catch(IllegalArgumentException e){
               LOGGER.warn("Invalid camera entity UUID in snapshot: '{}'", camUUIDStr);
            }
         }
         if(found > 0){
            LOGGER.info("SequenceManager: found {} orphaned camera entity/entities from crashed sequences.", found);
         }
      }catch(Exception e){
         LOGGER.error("SequenceManager.onServerStart error: {}", e.getMessage());
      }
   }
   
   /**
    * Called whenever any entity loads into a {@link ServerLevel}.
    * Discards entities whose UUID is in the pending camera cleanup set.
    */
   public static void onEntityLoad(Entity entity, ServerLevel level){
      if(!PENDING_CAMERA_CLEANUP.isEmpty()
            && PENDING_CAMERA_CLEANUP.contains(entity.getUUID())){
         entity.discard();
         PENDING_CAMERA_CLEANUP.remove(entity.getUUID());
         LOGGER.info("SequenceManager: discarded orphaned camera entity {} on chunk load.", entity.getUUID());
      }
   }
   
   // ═══════════════════════════════ queries ══════════════════════════════════
   
   /**
    * Returns {@code true} if the player with the given UUID is currently in any sequence.
    */
   public static boolean isInSequence(UUID uuid){
      return ACTIVE.containsKey(uuid);
   }
   
   /**
    * Returns {@code true} if the player is currently in a sequence of the given type.
    */
   public static boolean isInSequence(UUID uuid, Class<? extends PlayerSequence> type){
      PlayerSequence seq = ACTIVE.get(uuid);
      return type.isInstance(seq);
   }
   
   /**
    * Returns the active sequence for this player, or {@code null} if none.
    */
   @Nullable
   public static PlayerSequence getActiveSequence(UUID uuid){
      return ACTIVE.get(uuid);
   }
   
   // ════════════════════════════ internal helpers ════════════════════════════
   
   private static void cancelInternal(UUID uuid, @Nullable ServerPlayer player){
      PlayerSequence seq = ACTIVE.remove(uuid);
      if(seq == null) return;
      
      if(player != null){
         try{
            seq.onEnd(player, true);
         }catch(Exception e){
            LOGGER.error("Exception in {}.onEnd(cancel) for player {}: {}",
                  seq.getClass().getSimpleName(), uuid, e.getMessage());
         }
         restoreFromSnapshot(player);
      }
      clearSnapshot(uuid);
   }
   
   // ─────────────────────── snapshot management ─────────────────────────────
   
   private static void saveSnapshot(ServerPlayer player, String sequenceType){
      UUID uuid = player.getUUID();
      PlayerSnapshot snapshot = DataAccess.getPlayer(uuid, PlayerSnapshot.KEY);
      snapshot.setActive(true);
      snapshot.setSequenceType(sequenceType);
      // Read current game type — derived from ability checks for compatibility.
      snapshot.setGameMode(deriveGameType(player));
      snapshot.setDimension(player.level().dimension().identifier().toString());
      snapshot.setPosition(player.getX(), player.getY(), player.getZ());
      snapshot.setRotation(player.getYRot(), player.getXRot());
      snapshot.setWasInvulnerable(player.isInvulnerable());
      snapshot.setWasAllowFlying(player.getAbilities().mayfly);
      snapshot.setWasFlying(player.getAbilities().flying);
      snapshot.setCameraEntityUUID("");
      DataAccess.markPlayerDirty(uuid);
   }
   
   /**
    * Full state restoration from the player's stored snapshot.
    * Resets camera, game-mode, invulnerability, flying, and teleports the player
    * back to their pre-sequence position.
    */
   private static void restoreFromSnapshot(ServerPlayer player){
      PlayerSnapshot snapshot = DataAccess.getPlayer(player.getUUID(), PlayerSnapshot.KEY);
      if(!snapshot.isActive()) return;
      restoreFromSnapshot(player, snapshot);
   }
   
   static void restoreFromSnapshot(ServerPlayer player, PlayerSnapshot snapshot){
      // ── Camera ────────────────────────────────────────────────────────────
      // Reset camera back to the player in case a cutscene left it attached.
      try{
         player.setCamera(player);
      }catch(Exception e){
         LOGGER.warn("Failed to reset camera for {}: {}", player.getUUID(), e.getMessage());
      }
      
      // ── Camera entity cleanup ──────────────────────────────────────────────
      String camUUIDStr = snapshot.getCameraEntityUUID();
      if(camUUIDStr != null && !camUUIDStr.isEmpty()){
         try{
            UUID camUUID = UUID.fromString(camUUIDStr);
            if(!tryDiscardEntity(BorisLib.SERVER, camUUID)){
               PENDING_CAMERA_CLEANUP.add(camUUID);
            }
         }catch(Exception e){
            LOGGER.warn("Failed to discard camera entity '{}': {}", camUUIDStr, e.getMessage());
         }
      }
      
      // ── Game mode ─────────────────────────────────────────────────────────
      try{
         player.setGameMode(snapshot.getGameMode());
      }catch(Exception e){
         LOGGER.warn("Failed to restore game mode for {}: {}", player.getUUID(), e.getMessage());
      }
      
      // ── Invulnerability ────────────────────────────────────────────────────
      try{
         player.setInvulnerable(snapshot.wasInvulnerable());
      }catch(Exception e){
         LOGGER.warn("Failed to restore invulnerability for {}: {}", player.getUUID(), e.getMessage());
      }
      
      // ── Flying abilities ───────────────────────────────────────────────────
      try{
         player.getAbilities().mayfly = snapshot.wasAllowFlying();
         player.getAbilities().flying = snapshot.wasFlying();
         player.onUpdateAbilities();
      }catch(Exception e){
         LOGGER.warn("Failed to restore flying abilities for {}: {}", player.getUUID(), e.getMessage());
      }
      
      // ── Position ──────────────────────────────────────────────────────────
      try{
         Identifier dimId = Identifier.parse(snapshot.getDimension());
         ResourceKey<Level> dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
         ServerLevel targetLevel = BorisLib.SERVER.getLevel(dimKey);
         if(targetLevel == null){
            LOGGER.warn("Could not resolve dimension '{}' for snapshot restore — using current dimension.", snapshot.getDimension());
            targetLevel = player.level();
         }
         Vec3 pos = new Vec3(snapshot.getX(), snapshot.getY(), snapshot.getZ());
         player.teleport(new TeleportTransition(
               targetLevel, pos, Vec3.ZERO,
               snapshot.getYaw(), snapshot.getPitch(),
               entity -> {
               }));
      }catch(Exception e){
         LOGGER.error("Failed to restore position for {}: {}", player.getUUID(), e.getMessage());
      }
   }
   
   /**
    * Partial restoration used on player death.
    * Restores game-mode and flags only; position is intentionally skipped so vanilla
    * respawn logic can place the player at their bed/world spawn.
    */
   private static void restoreFromSnapshotOnDeath(ServerPlayer player){
      PlayerSnapshot snapshot = DataAccess.getPlayer(player.getUUID(), PlayerSnapshot.KEY);
      if(!snapshot.isActive()) return;
      
      // Camera entity cleanup still needed.
      String camUUIDStr = snapshot.getCameraEntityUUID();
      if(camUUIDStr != null && !camUUIDStr.isEmpty()){
         try{
            UUID camUUID = UUID.fromString(camUUIDStr);
            if(!tryDiscardEntity(BorisLib.SERVER, camUUID)){
               PENDING_CAMERA_CLEANUP.add(camUUID);
            }
         }catch(Exception e){
            LOGGER.warn("Failed to discard camera entity on death '{}': {}", camUUIDStr, e.getMessage());
         }
      }
      
      try{
         player.setCamera(player);
      }catch(Exception ignored){
      }
      try{
         player.setGameMode(snapshot.getGameMode());
      }catch(Exception ignored){
      }
      try{
         player.setInvulnerable(snapshot.wasInvulnerable());
      }catch(Exception ignored){
      }
      try{
         player.getAbilities().mayfly = snapshot.wasAllowFlying();
         player.getAbilities().flying = snapshot.wasFlying();
         player.onUpdateAbilities();
      }catch(Exception ignored){
      }
   }
   
   private static void clearSnapshot(UUID uuid){
      PlayerSnapshot snapshot = DataAccess.getPlayer(uuid, PlayerSnapshot.KEY);
      snapshot.setActive(false);
      snapshot.setSequenceType("");
      snapshot.setCameraEntityUUID("");
      DataAccess.markPlayerDirty(uuid);
   }
   
   // ─────────────────────── utility ─────────────────────────────────────────
   
   /**
    * Attempts to find and discard an entity with the given UUID across all loaded levels.
    *
    * @return {@code true} if the entity was found and discarded.
    */
   private static boolean tryDiscardEntity(MinecraftServer server, UUID entityUUID){
      if(server == null) return false;
      for(ServerLevel level : server.getAllLevels()){
         Entity entity = level.getEntity(entityUUID);
         if(entity != null){
            entity.discard();
            return true;
         }
      }
      return false;
   }
   
   /**
    * Reads the player's current {@link GameType} via the access-widened
    * {@code ServerPlayer.gameMode} field for an exact result.
    */
   private static GameType deriveGameType(ServerPlayer player){
      try{
         return player.gameMode.getGameModeForPlayer();
      }catch(Exception e){
         // Fallback: derive from ability flags if field access fails.
         if(player.isCreative()) return GameType.CREATIVE;
         if(player.isSpectator()) return GameType.SPECTATOR;
         return GameType.SURVIVAL;
      }
   }
}





