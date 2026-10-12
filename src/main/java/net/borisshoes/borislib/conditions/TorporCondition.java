package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in harmful {@link Condition} that decreases the affected entity's
 * {@link net.minecraft.world.entity.ai.attributes.Attributes#MOVEMENT_SPEED} attribute.
 *
 * <p>The magnitude attenuates total speed by {@code 1 / (1 + m)} through a total-multiplier modifier
 * ({@link AttributeModifier.Operation#ADD_MULTIPLIED_TOTAL}), so a magnitude of {@code 1} halves speed and
 * also scales vanilla Speed and Celerity. At {@code +∞} it is a full root: speed is 0 and
 * {@link Attributes#JUMP_STRENGTH} is zeroed as well.</p>
 *
 * <p>Cap: {@code +∞}.</p>
 */
public class TorporCondition extends AttributeCondition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(new DustParticleOptions(0x495F68, 0.75f), 2.0f, 10, 0.01f);

   protected TorporCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "torpor"), MobEffectCategory.HARMFUL, Float.POSITIVE_INFINITY, List.of(Attributes.MOVEMENT_SPEED), Curve.ATTENUATE_TOTAL);
   }

   @Override
   protected void applyModifiers(LivingEntity entity, float value){
      super.applyModifiers(entity, value);
      AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
      if(jump == null) return;
      if(value == Float.POSITIVE_INFINITY){
         jump.addOrUpdateTransientModifier(new AttributeModifier(getId(), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
      }else{
         jump.removeModifier(getId());
      }
   }

   @Override
   protected void removeModifiers(LivingEntity entity){
      super.removeModifiers(entity);
      AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
      if(jump != null) jump.removeModifier(getId());
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }
}
