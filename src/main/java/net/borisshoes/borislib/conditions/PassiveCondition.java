package net.borisshoes.borislib.conditions;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * A {@link Condition} with no behavior of its own: its magnitude is read by a hook elsewhere (a mixin or
 * another mod's code) through {@link Conditions#getConditionValue(LivingEntity, net.minecraft.core.Holder)}.
 * BorisLib's Falter, Rupture, Vitality, Blight, Obscurity and Exposure are instances of this class.
 */
public class PassiveCondition extends Condition {
   private final ConditionParticles particles;
   private final ConditionMath.Scale scale;

   /**
    * @param id        unique namespaced identifier for this condition type
    * @param category  beneficial / harmful / neutral classification
    * @param cap       the largest permitted magnitude; {@link Float#POSITIVE_INFINITY} opts in to infinity
    * @param scale     how the hook reading this condition turns its magnitude into an effect
    * @param particles the condition's default particle spec
    */
   public PassiveCondition(Identifier id, MobEffectCategory category, float cap, ConditionMath.Scale scale, ConditionParticles particles){
      super(id, category, cap);
      this.scale = scale;
      this.particles = particles;
   }

   @Override
   public ConditionMath.Scale getScale(){
      return scale;
   }

   /** Passive conditions do nothing per tick. */
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return particles;
   }
}
