package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in beneficial {@link Condition} that heals the affected entity every tick.
 *
 * <p>The magnitude is health restored per second, applied as {@code m / 20} each tick. At {@code +∞} the
 * entity is healed by its full maximum health every tick. Healing always goes through
 * {@link LivingEntity#heal(float)}, so Vitality and Blight apply to it.</p>
 *
 * <p>Cap: {@code +∞}.</p>
 */
public class RejuvenationCondition extends Condition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(ParticleTypes.HEART, 0.5f, 3, 0.0f);

   protected RejuvenationCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "rejuvenation"), MobEffectCategory.BENEFICIAL, Float.POSITIVE_INFINITY);
   }

   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
      entity.heal(value == Float.POSITIVE_INFINITY ? entity.getMaxHealth() : value / 20.0f);
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }
}
