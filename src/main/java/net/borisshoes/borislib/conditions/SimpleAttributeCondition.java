package net.borisshoes.borislib.conditions;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * An {@link AttributeCondition} that is fully described by one attribute, one
 * {@link AttributeCondition.Curve curve} and a particle spec. Most of BorisLib's built-in attribute
 * conditions (Alacrity, Lethargy, Bulwark, Sunder, ...) are instances of this class.
 */
public class SimpleAttributeCondition extends AttributeCondition {
   private final ConditionParticles particles;
   private final boolean playersOnly;

   /**
    * @param id          unique namespaced identifier for this condition type
    * @param category    beneficial / harmful / neutral classification
    * @param cap         the largest permitted magnitude; {@link Float#POSITIVE_INFINITY} opts in to infinity
    * @param attribute   the attribute this condition modifies
    * @param curve       how a magnitude maps onto the modifier
    * @param particles   the condition's default particle spec
    * @param playersOnly if {@code true} the condition can only be applied to players
    */
   public SimpleAttributeCondition(Identifier id, MobEffectCategory category, float cap, Holder<Attribute> attribute, Curve curve, ConditionParticles particles, boolean playersOnly){
      super(id, category, cap, List.of(attribute), curve);
      this.particles = particles;
      this.playersOnly = playersOnly;
   }

   @Override
   public boolean canApply(LivingEntity entity){
      if(playersOnly && !(entity instanceof Player)) return false;
      return super.canApply(entity);
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return particles;
   }
}
