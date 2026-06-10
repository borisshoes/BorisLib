package net.borisshoes.borislib.sequences;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An invincibility-frame (i-frame) animation sequence.
 *
 * <p>The player may have their position frozen or driven along a {@link CameraPath}.
 * Optionally grants complete damage immunity (and knockback immunity) via
 * {@link ServerPlayer#setInvulnerable}.
 *
 * <p>Camera (look) control is <em>always</em> free unless {@code locksCamera} is
 * explicitly set to {@code true}.  When the camera is locked the path's keyframe
 * yaw/pitch values drive the view; when it is free the position sync uses
 * <em>relative</em> rotation (delta=0) so the server never touches the client's
 * camera angle.
 *
 * <p>Designed for abilities like a burrow, where the player:
 * <ul>
 *   <li>Cannot move under their own input during the animation.</li>
 *   <li>May be driven along a path by the server (e.g. burrowing underground).</li>
 *   <li>Cannot take damage or be knocked back.</li>
 *   <li>Can still aim / look around freely (unless {@code locksCamera=true}).</li>
 * </ul>
 *
 * <h3>Example — position-frozen i-frame (free-look)</h3>
 * <pre>{@code
 * SequenceManager.start(player, new IFrameSequence(
 *     player.getUUID(), 30, true, true, null));
 * }</pre>
 *
 * <h3>Example — path-driven i-frame with camera lock</h3>
 * <pre>{@code
 * SequenceManager.start(player, new IFrameSequence(
 *     player.getUUID(), 40, true, true, true, burrowPath));
 * }</pre>
 */
public class IFrameSequence extends PlayerSequence {
   
   private final int durationTicks;
   /** Whether to grant full invulnerability (and thus knockback immunity) during the frame. */
   private final boolean immuneToDamage;
   /** Whether to freeze / drive the player's position server-side. */
   private final boolean freezePosition;
   /**
    * When {@code true} <em>and</em> a {@link CameraPath} is provided, the path's
    * keyframe yaw/pitch values are used to drive the camera rotation each tick, and
    * look input is blocked.
    *
    * <p>When {@code false} (the default), the position sync packet always uses
    * relative yaw/pitch (delta=0) so the server never modifies the client's camera
    * angle — eliminating the jitter that absolute-rotation teleports cause.
    */
   private final boolean locksCamera;
   /**
    * Optional path the player's position (and optionally rotation) follows.
    * When {@code null} and {@code freezePosition} is true, the player is pinned
    * to their position at sequence start.
    */
   @Nullable
   private final CameraPath movementPath;
   
   // Position captured on start, used as the pin target when there is no path.
   private double pinnedX, pinnedY, pinnedZ;
   
   // ─────────────────────────────── constructors ────────────────────────────
   
   /**
    * Full constructor.
    *
    * @param playerUUID     UUID of the player.
    * @param durationTicks  Sequence duration in ticks. {@code -1} = indefinite.
    * @param immuneToDamage When {@code true}, {@link ServerPlayer#setInvulnerable} grants
    *                       full damage and knockback immunity.
    * @param freezePosition When {@code true}, the player's position is controlled
    *                       server-side and client movement input is discarded.
    * @param locksCamera    When {@code true} <em>and</em> a path is provided, the path's
    *                       yaw/pitch values drive the camera; look input is blocked.
    *                       When {@code false} (default), the player always has free-look
    *                       and the position sync uses relative rotation to avoid jitter.
    * @param movementPath   Optional: if not {@code null} the player's position is driven
    *                       along this path over the duration. Requires {@code freezePosition=true}.
    */
   public IFrameSequence(UUID playerUUID, int durationTicks,
                         boolean immuneToDamage, boolean freezePosition,
                         boolean locksCamera, @Nullable CameraPath movementPath){
      super(playerUUID);
      this.durationTicks = durationTicks;
      this.immuneToDamage = immuneToDamage;
      this.freezePosition = freezePosition;
      this.locksCamera = locksCamera;
      this.movementPath = movementPath;
   }
   
   /**
    * Convenience constructor — camera is always free-look ({@code locksCamera=false}).
    */
   public IFrameSequence(UUID playerUUID, int durationTicks,
                         boolean immuneToDamage, boolean freezePosition,
                         @Nullable CameraPath movementPath){
      this(playerUUID, durationTicks, immuneToDamage, freezePosition, false, movementPath);
   }
   
   // ─────────────────────────────── lifecycle ───────────────────────────────
   
   @Override
   public void onStart(ServerPlayer player){
      // Record the pin position (start of path or current position).
      if(movementPath != null && !movementPath.getKeyframes().isEmpty()){
         CameraPathSample s = movementPath.evaluate(0.0);
         pinnedX = s.position().x;
         pinnedY = s.position().y;
         pinnedZ = s.position().z;
      }else{
         pinnedX = player.getX();
         pinnedY = player.getY();
         pinnedZ = player.getZ();
      }
      
      // Warn if a movement path was provided but freeze is disabled — the path will never run.
      if(movementPath != null && !freezePosition){
         BorisLib.LOGGER.warn("IFrameSequence: movementPath was provided but freezePosition=false — the path will be ignored during this sequence.");
      }
      
      if(immuneToDamage){
         player.setInvulnerable(true);
      }
   }
   
   @Override
   public void onTick(ServerPlayer player, int tick){
      if(!freezePosition) return;
      
      // t=0.0 on tick 0, t=1.0 on the final tick.
      double t;
      if(durationTicks > 0){
         t = Math.min(1.0, (double) tick / Math.max(1, durationTicks - 1));
      }else{
         t = 0.0; // indefinite sequence — hold at path start
      }
      
      double x, y, z;
      if(movementPath != null){
         CameraPathSample sample = movementPath.evaluate(t);
         x = sample.position().x;
         y = sample.position().y;
         z = sample.position().z;
         // Camera-locked: apply the path's rotation to the server-side player so
         // the mixin correction packet and the sync packet both use the correct values.
         // Free-look: rotation is never touched — the sync packet uses relative flags.
         if(locksCamera){
            player.setYRot(sample.yaw());
            player.setXRot(sample.pitch());
         }
      }else{
         x = pinnedX;
         y = pinnedY;
         z = pinnedZ;
      }
      
      // ── Velocity hint ─────────────────────────────────────────────────────
      // For path-driven movement, send ClientboundSetEntityMotionPacket BEFORE
      // updating the position.  The client uses this for smooth visual interpolation
      // between the previous position and the incoming position-sync packet,
      // eliminating the per-tick "popping" artefact.
      if(movementPath != null){
         Vec3 prevPos = player.position();
         Vec3 velocity = new Vec3(x - prevPos.x, y - prevPos.y, z - prevPos.z);
         player.setDeltaMovement(velocity);
         player.connection.send(new ClientboundSetEntityMotionPacket(player));
      }
      
      // ── Server-side position update ────────────────────────────────────────
      // Must happen before connection.teleport() so the server's authoritative
      // position is correct immediately (the mixin's @ModifyVariable reads it).
      player.setPos(x, y, z);
      
      // ── Client position sync ───────────────────────────────────────────────
      // Use connection.teleport(PositionMoveRotation, Set<Relative>) directly so
      // we can pass RELATIVE yaw+pitch flags when free-look is active.
      //
      // • locksCamera=true  → Relative.unpack(0)       — absolute position AND
      //                        rotation; camera is driven to the path values.
      // • locksCamera=false → Relative.unpack(0b11000) — absolute position only;
      //                        yaw/pitch delta=0 means the client's camera angle
      //                        is completely untouched, preventing jitter.
      if(locksCamera && movementPath != null){
         // Absolute rotation — camera follows the path.
         player.connection.teleport(
               new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO,
                     player.getYRot(), player.getXRot()),
               Relative.unpack(0));
      }else{
         // Relative yaw+pitch (delta=0) — position syncs, camera is untouched.
         player.connection.teleport(
               new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, 0f, 0f),
               Relative.unpack(0b11000));
      }
   }
   
   @Override
   public void onEnd(ServerPlayer player, boolean cancelled){
      if(!cancelled){
         // Natural completion: keep the player at their final animation position AND
         // rotation so the snapshot restore in SequenceManager does not snap the camera
         // back to the pre-sequence orientation.
         //
         // • locksCamera=false: player.getYRot()/getXRot() reflect wherever the player
         //   was looking (the mixin does not override rotation in free-look mode, so
         //   client movement packets keep the server-side values up to date).
         // • locksCamera=true:  the path's final rotation was applied each tick, so
         //   the values are already at the path's end orientation.
         PlayerSnapshot snapshot = DataAccess.getPlayer(player.getUUID(), PlayerSnapshot.KEY);
         snapshot.setPosition(player.getX(), player.getY(), player.getZ());
         snapshot.setRotation(player.getYRot(), player.getXRot());
         DataAccess.markPlayerDirty(player.getUUID());
      }
      // Invulnerability and other flags are restored by SequenceManager from the snapshot.
   }
   
   // ─────────────────────────────── flags ───────────────────────────────────
   
   @Override
   public int getDurationTicks(){
      return durationTicks;
   }
   
   /** Block position updates from client — server drives position. */
   @Override
   public boolean blocksMovement(){
      return freezePosition;
   }
   
   /**
    * Look input is blocked only when {@code locksCamera=true} AND a path is active.
    * With {@code locksCamera=false} (the default) the player always has free camera
    * control; the position sync uses relative rotation to avoid any angle interference.
    */
   @Override
   public boolean blocksLook(){
      return locksCamera && movementPath != null;
   }
   
   // ─────────────────────────────── accessors ───────────────────────────────
   
   public boolean isImmuneToDamage(){
      return immuneToDamage;
   }
   
   public boolean isFreezePosition(){
      return freezePosition;
   }
   
   public boolean isLocksCamera(){
      return locksCamera;
   }
   
   @Nullable
   public CameraPath getMovementPath(){
      return movementPath;
   }
}

