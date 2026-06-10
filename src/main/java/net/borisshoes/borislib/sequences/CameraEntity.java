package net.borisshoes.borislib.sequences;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A server-side camera mount entity for {@link CutsceneSequence}.
 *
 * <p>Uses the Polymer library to disguise as a vanilla {@link EntityType#INTERACTION}
 * entity on the client — which is completely invisible, has no hitbox, and correctly
 * propagates {@code yRot}/{@code xRot} for camera use via {@code setCamera()}.
 *
 * <p>The entity type is registered (with {@code updateInterval=1} and
 * {@code clientTrackingRange=128}) as {@link net.borisshoes.borislib.BorisLib#CAMERA_ENTITY_TYPE}.
 *
 * <h3>Why not ArmorStand?</h3>
 * ArmorStand overrides eye-height and pose logic which produces incorrect view
 * vectors when used as a {@code setCamera} target. The vanilla {@code Interaction}
 * entity uses the default {@link Entity#getViewYRot}/{@link Entity#getViewXRot}
 * implementations with full interpolation support.
 */
public class CameraEntity extends Entity implements PolymerEntity {

   public CameraEntity(EntityType<?> type, Level level){
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
      this.setInvisible(true);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder){
      // No custom synced data
   }

   @Override
   protected void readAdditionalSaveData(ValueInput tag){}

   @Override
   protected void addAdditionalSaveData(ValueOutput tag){}

   @Override
   public boolean hurtServer(ServerLevel level, DamageSource source, float amount){
      return false; // camera entity cannot take damage
   }

   // ── Polymer ───────────────────────────────────────────────────────────────

   /**
    * Appear as an Interaction entity to the client.
    * Interaction is fully invisible, has no hitbox, and uses the default
    * Entity rotation accessors that work correctly with setCamera.
    */
   @Override
   public EntityType<?> getPolymerEntityType(PacketContext context){
      return EntityType.INTERACTION;
   }
}



