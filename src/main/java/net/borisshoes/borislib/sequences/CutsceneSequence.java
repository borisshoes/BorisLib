package net.borisshoes.borislib.sequences;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DefaultPlayerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A scripted camera-path cutscene.
 *
 * <h3>Architecture</h3>
 * <ol>
 *   <li>The player enters {@code SPECTATOR} mode.</li>
 *   <li>Each server tick the player is teleported along the {@link CameraPath} via
 *       {@link TeleportTransition}.  Because {@code ClientboundPlayerPositionPacket}
 *       encodes yaw and pitch as full-precision {@code float} values (not the
 *       byte-quantised ~1.4° increments of entity rotation packets), camera rotation
 *       is perfectly smooth even for long cutscenes with slow turns.</li>
 *   <li>The Arcana-style {@code @ModifyVariable} mixin (both position <em>and</em>
 *       rotation locked via {@link PlayerSequence#blocksMovement()} /
 *       {@link PlayerSequence#blocksLook()}) ensures any stray movement packets from
 *       the client are corrected back to the scripted position + rotation.</li>
 * </ol>
 *
 * <h3>Mannequin stand-in</h3>
 * When {@code spawnMannequin} is {@code true}, a vanilla {@link Mannequin} entity
 * carrying the player's skin, equipment, body rotation, and current pose is spawned at
 * the player's original body position for the duration of the cutscene.
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * CameraPath path = CameraPath.builder()
 *     .add(CameraKeyframe.at(0.0).pos(x0, y0, z0).rot(0, 0).build())
 *     .add(CameraKeyframe.at(1.0).pos(x1, y1, z1).rot(90, -10).interp(EASE_IN_OUT).build())
 *     .build();
 *
 * // Pure spectator — no visible body:
 * SequenceManager.start(player, new CutsceneSequence(player.getUUID(), path, 200));
 *
 * // With a Mannequin stand-in at the player's original position:
 * SequenceManager.start(player, new CutsceneSequence(player.getUUID(), path, 200, true));
 * }</pre>
 */
public class CutsceneSequence extends PlayerSequence {

   private final CameraPath path;
   private final int durationTicks;

   /**
    * When {@code true}, a {@link Mannequin} with the player's skin (equipment,
    * rotation, and pose) is spawned at their original body position for the
    * duration of the cutscene.
    */
   private final boolean spawnMannequin;

   /** Optional mannequin stand-in. Non-null only when {@link #spawnMannequin} is {@code true}. */
   private Mannequin mannequin = null;

   /** Body position captured before entering spectator; used for mannequin placement. */
   private double pinnedX, pinnedY, pinnedZ;

   // ─────────────────────────────── constructors ────────────────────────────

   /**
    * @param playerUUID     UUID of the player.
    * @param path           Camera path evaluated from t=0 to t=1 over {@code durationTicks}.
    * @param durationTicks  Duration in server ticks. {@code -1} = indefinite.
    * @param spawnMannequin When {@code true}, a Mannequin is spawned as a visual body stand-in.
    */
   public CutsceneSequence(UUID playerUUID, CameraPath path, int durationTicks, boolean spawnMannequin){
      super(playerUUID);
      this.path = path;
      this.durationTicks = durationTicks;
      this.spawnMannequin = spawnMannequin;
   }

   /** Convenience constructor: spectator-only, no mannequin. */
   public CutsceneSequence(UUID playerUUID, CameraPath path, int durationTicks){
      this(playerUUID, path, durationTicks, false);
   }

   /** Convenience factory for a completely static (non-moving) camera view. */
   public static CutsceneSequence staticView(UUID playerUUID, double x, double y, double z,
                                             float yaw, float pitch, int durationTicks, boolean spawnMannequin){
      return new CutsceneSequence(playerUUID,
            CameraPath.staticView(new Vec3(x, y, z), yaw, pitch),
            durationTicks, spawnMannequin);
   }

   public static CutsceneSequence staticView(UUID playerUUID, double x, double y, double z,
                                             float yaw, float pitch, int durationTicks){
      return staticView(playerUUID, x, y, z, yaw, pitch, durationTicks, false);
   }

   // ─────────────────────────────── lifecycle ───────────────────────────────

   @Override
   public void onStart(ServerPlayer player){
      CameraPathSample initial = path.evaluate(0.0);
      ServerLevel level = (ServerLevel) player.level();

      // Capture the body position before entering spectator.
      pinnedX = player.getX();
      pinnedY = player.getY();
      pinnedZ = player.getZ();

      // ── Optional Mannequin stand-in ─────────────────────────────────────
      if(spawnMannequin){
         DefaultPlayerData data = DataAccess.getPlayer(player.getUUID(), BorisLib.PLAYER_DATA_KEY);
         mannequin = data.createMannequin(level);
         if(mannequin != null){
            // Position
            mannequin.setPos(pinnedX, pinnedY, pinnedZ);

            // ── Rotation ────────────────────────────────────────────────
            // yRot      = general entity yaw (also used as body yaw in absence of explicit override)
            mannequin.setYRot(player.getYRot());
            // xRot      = head/eye pitch (sent in spawn packet and move/rotate packets)
            mannequin.setXRot(player.getXRot());
            // yHeadRot  = head yaw (sent via ClientboundRotateHeadPacket after spawn)
            mannequin.setYHeadRot(player.getYHeadRot());
            // yHeadRotO / yBodyRotO = "previous tick" values used for client interpolation;
            // pre-seed to the same value so there is no 1-tick lerp artefact from 0.
            mannequin.yHeadRotO = player.getYHeadRot();
            // yBodyRot  = body yaw (server-side, informs client-side body direction)
            mannequin.setYBodyRot(player.yBodyRot);
            mannequin.yBodyRotO = player.yBodyRot;

            // ── Pose ─────────────────────────────────────────────────────
            // Copies STANDING / CROUCHING / SLEEPING / SWIMMING (crawl), etc.
            // Mannequin only supports poses in its VALID_POSES set; unsupported
            // poses fall back to STANDING naturally.
            mannequin.setPose(player.getPose());

            mannequin.setNoGravity(true);
            mannequin.setInvulnerable(true);
            mannequin.setSilent(true);

            // ── Equipment ────────────────────────────────────────────────
            if(mannequin instanceof LivingEntity livingMannequin){
               for(EquipmentSlot slot : EquipmentSlot.values()){
                  ItemStack item = player.getItemBySlot(slot);
                  if(!item.isEmpty()){
                     livingMannequin.setItemSlot(slot, item.copy());
                  }
               }
            }
            level.addFreshEntity(mannequin);

            // Persist UUID for crash-recovery cleanup in SequenceManager.
            PlayerSnapshot snapshot = DataAccess.getPlayer(player.getUUID(), PlayerSnapshot.KEY);
            snapshot.setCameraEntityUUID(mannequin.getUUID().toString());
            DataAccess.markPlayerDirty(player.getUUID());
         }
      }

      // ── Enter spectator and teleport to path start ───────────────────────
      // The player IS the camera in spectator mode.  Set server-side rotation
      // first so the mixin correction packet uses the correct values immediately.
      player.setGameMode(GameType.SPECTATOR);
      player.setYRot(initial.yaw());
      player.setXRot(initial.pitch());
      player.teleport(new TeleportTransition(
            level,
            new Vec3(initial.position().x, initial.position().y, initial.position().z),
            Vec3.ZERO,
            initial.yaw(), initial.pitch(),
            entity -> {}));
   }

   @Override
   public void onTick(ServerPlayer player, int tick){
      // t=0.0 on tick 0, t=1.0 on the final tick.
      double t;
      if(durationTicks > 0){
         t = Math.min(1.0, (double) tick / Math.max(1, durationTicks - 1));
      }else{
         t = 0.0; // indefinite — hold at path start
      }
      CameraPathSample sample = path.evaluate(t);

      // Update server-side rotation BEFORE the teleport so the mixin's
      // correction packet (which reads player.getYRot()/getXRot()) already
      // has the current path rotation when a client movement packet arrives.
      player.setYRot(sample.yaw());
      player.setXRot(sample.pitch());

      // Teleport the player (= the camera) to the new path position+rotation.
      // TeleportTransition sends ClientboundPlayerPositionPacket, which encodes
      // yaw and pitch as full-precision floats — no byte-quantisation artefacts.
      player.teleport(new TeleportTransition(
            (ServerLevel) player.level(),
            new Vec3(sample.position().x, sample.position().y, sample.position().z),
            Vec3.ZERO,
            sample.yaw(), sample.pitch(),
            entity -> {}));
   }

   @Override
   public void onEnd(ServerPlayer player, boolean cancelled){
      // Discard the optional mannequin.
      if(mannequin != null && !mannequin.isRemoved()){
         mannequin.discard();
      }
      mannequin = null;

      // Clear the persisted entity UUID so SequenceManager skips re-discard.
      PlayerSnapshot snapshot = DataAccess.getPlayer(player.getUUID(), PlayerSnapshot.KEY);
      snapshot.setCameraEntityUUID("");
      DataAccess.markPlayerDirty(player.getUUID());

      // NOTE: Game-mode, position, and flags are restored by SequenceManager
      // from the PlayerSnapshot automatically — do NOT restore them here.
   }

   // ─────────────────────────────── flags ───────────────────────────────────

   @Override
   public int getDurationTicks(){ return durationTicks; }

   /**
    * Block position input — the per-tick TeleportTransition drives position.
    * The Arcana-style @ModifyVariable mixin corrects any drift.
    */
   @Override
   public boolean blocksMovement(){ return true; }

   /**
    * Lock look input to the scripted path rotation.
    * Combined with the Arcana-style correction packet (absolute float rotation),
    * this gives the client sub-degree precision without byte quantisation.
    */
   @Override
   public boolean blocksLook(){ return true; }

   /** Always restrict spectator abuse — the player is always in spectator mode. */
   @Override
   public boolean restrictsSpectatorAbuse(){ return true; }

   // ─────────────────────────────── accessors ───────────────────────────────

   public CameraPath getPath(){ return path; }
   public boolean isSpawnMannequin(){ return spawnMannequin; }
}
