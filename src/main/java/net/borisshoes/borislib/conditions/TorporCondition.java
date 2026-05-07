package net.borisshoes.borislib.conditions;

import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class TorporCondition extends Condition{
   private static final DustParticleOptions PARTICLE = new DustParticleOptions(0x495F68, 0.75f);
   
   protected TorporCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "torpor"), MobEffectCategory.HARMFUL, 0.0f, 0.0f, Float.MAX_VALUE);
   }
   
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < 0.1){
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX() + (entity.getRandom().nextDouble() - 0.5) * width, entity.getY() + entity.getRandom().nextDouble() * height, entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * width, 1, 0.1, 0.15, 0.1, 0.01);
      }
      
      if(value > 0){
         MinecraftUtils.updateAttributeEffect(entity, Attributes.MOVEMENT_SPEED, -value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, getId(), true);
      }else{
         MinecraftUtils.attributeEffect(entity, Attributes.MOVEMENT_SPEED, -value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, getId(),true);
      }
   }
   
   @Override
   public void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level){
         // Burst of particles around the entity on application
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX(), entity.getY() + height * 0.5, entity.getZ(), 10, width * 0.5, height * 0.4, width * 0.5, 0.05);
      }
      MinecraftUtils.updateAttributeEffect(entity, Attributes.MOVEMENT_SPEED, -value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, getId(), true);
   }
   
   @Override
   public void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      MinecraftUtils.attributeEffect(entity, Attributes.MOVEMENT_SPEED, -value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, getId(),true);
   }
}
