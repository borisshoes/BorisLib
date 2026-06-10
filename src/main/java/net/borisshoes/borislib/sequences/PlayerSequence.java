package net.borisshoes.borislib.sequences;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Abstract base class for a timed, player-bound server-side sequence.
 *
 * <p>All three sequence types — {@link CutsceneSequence}, {@link IFrameSequence}, and
 * {@link RespawnGhostSequence} — extend this class. The {@link SequenceManager} drives
 * the lifecycle and handles all safety concerns (snapshot persistence, crash restoration,
 * disconnect/death cleanup).
 *
 * <h3>Contract for implementors</h3>
 * <ul>
 *   <li>{@link #onStart} — apply game-mode changes, spawn entities, set flags.</li>
 *   <li>{@link #onTick}  — drive per-tick effects (move camera entity, enforce position, etc.).</li>
 *   <li>{@link #onEnd}   — clean up anything spawned or modified in {@code onStart}/{@code onTick}.
 *       <strong>Do NOT restore the player's game-mode, position, or invulnerability here</strong> —
 *       {@link SequenceManager} handles that automatically from the saved {@link PlayerSnapshot}.</li>
 * </ul>
 */
public abstract class PlayerSequence {
   
   protected final UUID playerUUID;
   private int ticksElapsed = 0;
   
   protected PlayerSequence(UUID playerUUID){
      this.playerUUID = playerUUID;
   }
   
   // ─────────────────────────────── lifecycle ────────────────────────────────
   
   /**
    * Called once when the sequence starts, after the player's state has been snapshotted.
    * Apply game-mode changes, spawn entities, toggle invulnerability, etc. here.
    *
    * @param player the player entering the sequence
    */
   public abstract void onStart(ServerPlayer player);
   
   /**
    * Called every server tick while the sequence is active.
    *
    * @param player the player in the sequence
    * @param tick   ticks elapsed since start (0-indexed: 0 on the very first tick)
    */
   public abstract void onTick(ServerPlayer player, int tick);
   
   /**
    * Called when the sequence ends — either naturally (duration elapsed) or via
    * cancellation. Clean up spawned entities, undo per-tick effects, etc.
    *
    * <p><strong>Do NOT restore game-mode, position, invulnerability, or flying state
    * here.</strong> The {@link SequenceManager} restores those from the
    * {@link PlayerSnapshot} automatically after {@code onEnd} returns.
    *
    * @param player    the player
    * @param cancelled {@code true} if the sequence was forcefully cancelled
    *                  (disconnect, death, command) rather than completing naturally
    */
   public abstract void onEnd(ServerPlayer player, boolean cancelled);
   
   // ─────────────────────────────── configuration ───────────────────────────
   
   /**
    * Total duration of the sequence in server ticks.
    * Return {@code -1} for an indefinite sequence that must be manually ended via
    * {@link SequenceManager#cancel(ServerPlayer)}.
    */
   public abstract int getDurationTicks();
   
   /**
    * When {@code true}, position updates from the player's movement packets are
    * discarded server-side. Rotation still passes through unless {@link #blocksLook()}
    * also returns {@code true}.
    *
    * <p>The {@link SequenceManager} sends periodic teleport corrections so the client
    * stays in sync with the server's authoritative position.
    */
   public boolean blocksMovement(){
      return false;
   }
   
   /**
    * When {@code true}, look (yaw/pitch) updates from the player's movement packets
    * are discarded. Combine with {@link #blocksMovement()} to suppress all input.
    */
   public boolean blocksLook(){
      return false;
   }
   
   /**
    * When {@code true}, the player cannot spectate other entities by attacking them
    * in spectator mode. Relevant for sequences that place the player in
    * {@code SPECTATOR} but should restrict them from using spectator powers.
    */
   public boolean restrictsSpectatorAbuse(){
      return false;
   }
   
   // ─────────────────────────────── internal ────────────────────────────────
   
   public UUID getPlayerUUID(){
      return playerUUID;
   }
   
   public int getTicksElapsed(){
      return ticksElapsed;
   }
   
   /**
    * Called by {@link SequenceManager} after each successful {@link #onTick}.
    */
   public void incrementTick(){
      ticksElapsed++;
   }
}

