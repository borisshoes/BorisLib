package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in beneficial {@link Condition} that increases the affected entity's outgoing direct damage.
 *
 * <p>The magnitude amplifies damage dealt: the damage hook multiplies it by
 * {@code ratio(might, feeble)}, so a magnitude of {@code 1} doubles damage and equal Might and Feeble
 * cancel.</p>
 *
 * <p>Cap: {@link Float#MAX_VALUE}.</p>
 */
public class MightCondition extends Condition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER, 2.0f, 10, 0.01f);

   protected MightCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "might"), MobEffectCategory.BENEFICIAL);
   }

   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }
}
