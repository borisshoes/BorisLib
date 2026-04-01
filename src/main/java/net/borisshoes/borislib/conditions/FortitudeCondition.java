package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class FortitudeCondition extends Condition {
   private static final SimpleParticleType PARTICLE = ParticleTypes.FALLING_OBSIDIAN_TEAR;
   
   protected FortitudeCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "fortitude"), MobEffectCategory.BENEFICIAL, 1.0f, 0.0f, 1.0f, true);
   }
   
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < 0.1){
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX() + (entity.getRandom().nextDouble() - 0.5) * width, entity.getY() + entity.getRandom().nextDouble() * height, entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * width, 1, 0.1, 0.15, 0.1, 0);
      }
   }
   
   @Override
   public void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level){
         // Burst of particles around the entity on application
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX(), entity.getY() + height * 0.5, entity.getZ(), 20, width * 0.5, height * 0.4, width * 0.5, 0.01);
      }
   }
   
   @Override
   public void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles){
   
   }
}
