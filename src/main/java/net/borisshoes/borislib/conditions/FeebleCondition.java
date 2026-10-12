package net.borisshoes.borislib.conditions;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in harmful {@link Condition} that reduces the affected entity's outgoing direct damage.
 *
 * <p>The magnitude attenuates damage dealt: the damage hook multiplies it by
 * {@code ratio(might, feeble)}, so a magnitude of {@code 1} halves damage. At {@code +∞} the entity deals
 * no direct damage. Entities tagged {@link net.borisshoes.borislib.BorisLib#IGNORES_FEEBLE} are immune.</p>
 *
 * <p>Cap: {@code +∞}.</p>
 */
public class FeebleCondition extends Condition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(ParticleTypes.FALLING_HONEY, 2.0f, 20, 0.01f);

   protected FeebleCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "feeble"), MobEffectCategory.HARMFUL, Float.POSITIVE_INFINITY);
   }

   @Override
   public boolean canApply(LivingEntity entity){
      return !entity.is(BorisLib.IGNORES_FEEBLE);
   }

   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
   }

   @Override
   public ConditionMath.Scale getScale(){
      return ConditionMath.Scale.ATTENUATE;
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }
}
