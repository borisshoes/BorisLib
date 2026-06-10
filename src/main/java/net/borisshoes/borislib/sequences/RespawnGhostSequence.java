package net.borisshoes.borislib.sequences;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A temporary "ghost spectator" sequence used in minigame-style respawn flows.
 *
 * <p>The player is placed into {@code SPECTATOR} mode for a set duration.
 * All spectator-abuse vectors (entity spectating via attack) are blocked.
 * An optional anchor position and range can tether the ghost near a
 * death/spawn location to prevent unfair map exploration.
 *
 * <p>Movement packets are <em>not</em> blocked — the ghost can fly freely
 * within the allowed range.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * // On player death / respawn event:
 * SequenceManager.start(player, new RespawnGhostSequence(
 *     player.getUUID(),
 *     200,             // 10-second ghost phase
 *     player.position(), // anchor near death/spawn
 *     32.0             // max 32-block radius
 * ));
 * }</pre>
 *
 * <h3>Unbounded ghost (no range limit)</h3>
 * <pre>{@code
 * SequenceManager.start(player, new RespawnGhostSequence(
 *     player.getUUID(), 200, null, -1));
 * }</pre>
 */
public class RespawnGhostSequence extends PlayerSequence {
   
   private final int durationTicks;
   /**
    * World position the ghost is tethered near. {@code null} = no tether.
    */
   @Nullable
   private final Vec3 anchorPosition;
   /**
    * Maximum distance (blocks) from {@code anchorPosition} the ghost may stray.
    * {@code -1} disables the range limit entirely.
    */
   private final double maxRange;
   
   /**
    * @param playerUUID     UUID of the player.
    * @param durationTicks  Duration in server ticks. {@code -1} = indefinite.
    * @param anchorPosition Optional position the ghost is tethered near.
    * @param maxRange       Maximum distance from anchor ({@code -1} = unlimited).
    */
   public RespawnGhostSequence(UUID playerUUID, int durationTicks,
                               @Nullable Vec3 anchorPosition, double maxRange){
      super(playerUUID);
      this.durationTicks = durationTicks;
      this.anchorPosition = anchorPosition;
      this.maxRange = maxRange;
   }
   
   // ─────────────────────────────── lifecycle ───────────────────────────────
   
   @Override
   public void onStart(ServerPlayer player){
      player.setGameMode(GameType.SPECTATOR);
   }
   
   @Override
   public void onTick(ServerPlayer player, int tick){
      if(anchorPosition == null || maxRange <= 0) return;
      
      Vec3 current = player.position();
      double dist = current.distanceTo(anchorPosition);
      
      if(dist > maxRange){
         // Compute a position just inside the boundary (90% of maxRange from anchor
         // in the direction back toward the anchor).
         Vec3 toAnchor = anchorPosition.subtract(current).normalize();
         Vec3 clamped = anchorPosition.subtract(toAnchor.scale(maxRange * 0.9));
         
         // Push player back inside the range boundary via a proper TeleportTransition.
         player.teleport(new TeleportTransition(
               (net.minecraft.server.level.ServerLevel) player.level(),
               clamped, Vec3.ZERO,
               player.getYRot(), player.getXRot(),
               entity -> {
               }));
      }
   }
   
   @Override
   public void onEnd(ServerPlayer player, boolean cancelled){
      // Game mode and position restored by SequenceManager from snapshot.
   }
   
   // ─────────────────────────────── flags ───────────────────────────────────
   
   @Override
   public int getDurationTicks(){
      return durationTicks;
   }
   
   /**
    * Ghost can fly freely — movement packets are not blocked.
    */
   @Override
   public boolean blocksMovement(){
      return false;
   }
   
   @Override
   public boolean blocksLook(){
      return false;
   }
   
   /**
    * Block spectator entity-spectating to prevent unfair gameplay.
    */
   @Override
   public boolean restrictsSpectatorAbuse(){
      return true;
   }
   
   // ─────────────────────────────── accessors ───────────────────────────────
   
   @Nullable
   public Vec3 getAnchorPosition(){
      return anchorPosition;
   }
   
   public double getMaxRange(){
      return maxRange;
   }
}




