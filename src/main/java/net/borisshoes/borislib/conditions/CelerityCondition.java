package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Built-in beneficial {@link Condition} that increases the affected entity's
 * {@link net.minecraft.world.entity.ai.attributes.Attributes#MOVEMENT_SPEED} attribute.
 *
 * <p>The magnitude is applied as a base-multiplier modifier
 * ({@link AttributeModifier.Operation#ADD_MULTIPLIED_BASE}): a magnitude of {@code 0.5} adds 50% of base
 * speed. Shows a light blue dust particle.</p>
 *
 * <p>Cap: {@link Float#MAX_VALUE}.</p>
 */
public class CelerityCondition extends AttributeCondition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(new DustParticleOptions(0xA1E1F5, 0.75f), 2.0f, 10, 0.01f);

   protected CelerityCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "celerity"), MobEffectCategory.BENEFICIAL, Float.MAX_VALUE, List.of(Attributes.MOVEMENT_SPEED), Curve.AMPLIFY_BASE);
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }
}
