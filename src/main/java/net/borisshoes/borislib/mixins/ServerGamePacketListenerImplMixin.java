package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.borisshoes.borislib.sequences.PlayerSequence;
import net.borisshoes.borislib.sequences.SequenceManager;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

   @Shadow
   public ServerPlayer player;

   // ── Shadow fields used for the position-correction protocol ──────────────

   /**
    * Incremented each time the server sends a {@code ClientboundPlayerPositionPacket}.
    * The client echoes this back in {@code ServerboundAcceptTeleportationPacket},
    * allowing the server to confirm the client acknowledged the correction.
    */
   @Shadow
   private int awaitingTeleport;

   /**
    * The position the server expects the client to confirm it is at.
    * Set alongside {@link #awaitingTeleport} when a correction is sent.
    */
   @Shadow
   @Nullable
   private Vec3 awaitingPositionFromClient;

   // ─────────────────────── movement / look blocking ─────────────────────────

   /**
    * <b>Arcana-style position lock — override the {@code x} local.</b>
    *
    * <p>When an active sequence blocks movement, the first {@code double} local
    * stored in {@code handleMovePlayer} (i.e. the x-coordinate read from the packet)
    * is replaced with the server's authoritative x.  The packet is never cancelled,
    * so all other vanilla processing — on-ground flag, rotation, etc. — continues
    * to run normally.
    */
   @ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), ordinal = 0)
   private double borislib$lockX(double x){
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      return (seq != null && seq.blocksMovement()) ? player.getX() : x;
   }

   /** Arcana-style position lock — override the {@code y} local. */
   @ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), ordinal = 1)
   private double borislib$lockY(double y){
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      return (seq != null && seq.blocksMovement()) ? player.getY() : y;
   }

   /** Arcana-style position lock — override the {@code z} local. */
   @ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), ordinal = 2)
   private double borislib$lockZ(double z){
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      return (seq != null && seq.blocksMovement()) ? player.getZ() : z;
   }

   /**
    * Arcana-style rotation lock — override the {@code yaw} (first float) local.
    *
    * <p>When {@link PlayerSequence#blocksLook()} is {@code true}, the server-side
    * yaw is substituted for whatever the client sent.  Combined with the correction
    * packet below (which uses absolute yaw/pitch), this gives float-precision camera
    * rotation without the ~1.4° byte-quantisation of entity-rotation packets.
    */
   @ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), ordinal = 0)
   private float borislib$lockYaw(float yaw){
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      return (seq != null && seq.blocksLook()) ? player.getYRot() : yaw;
   }

   /** Arcana-style rotation lock — override the {@code pitch} (second float) local. */
   @ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), ordinal = 1)
   private float borislib$lockPitch(float pitch){
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      return (seq != null && seq.blocksLook()) ? player.getXRot() : pitch;
   }

   /**
    * Send a {@code ClientboundPlayerPositionPacket} correction immediately before
    * {@code player.move()} is invoked.
    *
    * <p>The {@code @ModifyVariable} injects above have already pinned the local
    * x/y/z (and yaw/pitch when {@code blocksLook}) to the server's authoritative
    * values, so the correction packet keeps the client perfectly in sync.
    *
    * <ul>
    *   <li><b>blocksLook = true</b> (e.g. CutsceneSequence, path-driven IFrame) —
    *       sends <em>absolute</em> position <em>and</em> rotation using the
    *       server's current yaw/pitch.  This gives the client full float-precision
    *       rotation, eliminating the ~1.4° byte-quantisation artefact of entity
    *       rotation packets.</li>
    *   <li><b>blocksLook = false</b> (e.g. frozen IFrame) — sends absolute position
    *       with <em>relative</em> yaw/pitch (delta = 0) so the client's own look
    *       direction is preserved.</li>
    * </ul>
    */
   @Inject(method = "handleMovePlayer",
         at = @At(value = "INVOKE",
               target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
   private void borislib$sendPositionCorrection(ServerboundMovePlayerPacket packet, CallbackInfo ci){
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      if(seq == null || !seq.blocksMovement()) return;

      // Claim the next teleport-confirmation slot.
      if(++awaitingTeleport == Integer.MAX_VALUE) awaitingTeleport = 0;
      awaitingPositionFromClient = player.position();

      if(seq.blocksLook()){
         // Absolute position + absolute rotation → client camera driven to the
         // server's yaw/pitch with float precision each movement-packet cycle.
         player.connection.send(new ClientboundPlayerPositionPacket(
               awaitingTeleport,
               new PositionMoveRotation(player.position(), Vec3.ZERO,
                     player.getYRot(), player.getXRot()),
               Relative.unpack(0)));
      }else{
         // Absolute position, relative yaw+pitch (bits 3+4, delta = 0) so the
         // client's own look direction is preserved (IFrame free-look).
         player.connection.send(new ClientboundPlayerPositionPacket(
               awaitingTeleport,
               new PositionMoveRotation(player.position(), Vec3.ZERO, 0f, 0f),
               Relative.unpack(0b11000)));
      }
   }

   // ─────────────── spectator abuse prevention (entity-spectating) ───────────

   /**
    * Blocks all interaction packets when the player is in spectator mode within a
    * sequence that has {@link PlayerSequence#restrictsSpectatorAbuse()} set.
    */
   @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
   private void borislib$blockSpectatorAbuse(ServerboundInteractPacket packet, CallbackInfo ci){
      if(!player.isSpectator()) return;
      PlayerSequence seq = SequenceManager.getActiveSequence(player.getUUID());
      if(seq != null && seq.restrictsSpectatorAbuse()){
         ci.cancel();
      }
   }

   // ─────────────────────── velocity tracker (existing) ──────────────────────

   @Inject(method = "handleMovePlayer",
         at = @At(value = "INVOKE",
               target = "Lnet/minecraft/server/level/ServerPlayer;setOnGroundWithMovement(ZZLnet/minecraft/world/phys/Vec3;)V"))
   private void borislib$updateVelocityTracker(ServerboundMovePlayerPacket packet, CallbackInfo ci, @Local Vec3 velocity){
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player) && !player.isDeadOrDying()){
         PlayerMovementEntry newEntry = new PlayerMovementEntry(player, player.position(), velocity, System.nanoTime());
         PLAYER_MOVEMENT_TRACKER.put(player, newEntry);
      }else{
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
   }
}
