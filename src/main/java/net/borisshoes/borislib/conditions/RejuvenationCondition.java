package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class RejuvenationCondition extends Condition{
   private static final SimpleParticleType PARTICLE = ParticleTypes.HEART;
   
   protected RejuvenationCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID,"rejuvenation"), MobEffectCategory.BENEFICIAL, 0.0f, 0.0f, Float.MAX_VALUE);
   }
   
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level && level.random.nextFloat() < 0.025){
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX() + (entity.getRandom().nextDouble() - 0.5) * width, entity.getY() + entity.getRandom().nextDouble() * height, entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * width, 1, 0.1, 0.15, 0.1, 0.0);
      }
      
      // Heal every tick equal to the condition value
      if(value > 0){
         entity.heal(value);
      }
   }
   
   @Override
   public void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level){
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX(), entity.getY() + height * 0.5, entity.getZ(), 3, width * 0.5, height * 0.4, width * 0.5, 0.0);
      }
   }
   
   @Override
   public void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles){}
}
