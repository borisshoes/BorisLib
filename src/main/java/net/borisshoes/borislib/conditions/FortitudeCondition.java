package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in beneficial {@link Condition} that reduces incoming damage to the affected entity.
 *
 * <p>The magnitude attenuates damage taken: the damage hook multiplies it by
 * {@code ratio(vulnerability, fortitude)}, so a magnitude of {@code 1} halves damage. At {@code +∞} the
 * entity is invulnerable to everything except damage that bypasses invulnerability.</p>
 *
 * <p>Cap: {@code +∞}.</p>
 */
public class FortitudeCondition extends Condition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(ParticleTypes.FALLING_OBSIDIAN_TEAR, 2.0f, 20, 0.0f);

   protected FortitudeCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "fortitude"), MobEffectCategory.BENEFICIAL, Float.POSITIVE_INFINITY);
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
