package net.borisshoes.borislib.conditions;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class DecayCondition extends Condition{
   private static final ColorParticleOption PARTICLE = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x40581E);
   
   protected DecayCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "decay"), MobEffectCategory.HARMFUL, 0.0f, 0.0f, Float.MAX_VALUE);
   }
   
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level){
         // Ambient particle drift within the entity's bounding box, like vanilla potion effects
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX() + (entity.getRandom().nextDouble() - 0.5) * width, entity.getY() + entity.getRandom().nextDouble() * height, entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * width, 2, 0.1, 0.05, 0.1, 0.01);
      }
      
      // Deal magic damage equal to the condition value every 10 ticks
      if(value > 0 && server.getTickCount() % 10 == 0){
         DamageSource source = entity.damageSources().source(DamageTypes.MAGIC);
         entity.hurtServer((ServerLevel) entity.level(), source, value);
      }
   }
   
   @Override
   public void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles){
      if(particles && entity.level() instanceof ServerLevel level){
         // Burst of particles around the entity on application
         double width = entity.getBbWidth();
         double height = entity.getBbHeight();
         level.sendParticles(PARTICLE, entity.getX(), entity.getY() + height * 0.5, entity.getZ(), 20, width * 0.5, height * 0.4, width * 0.5, 0.05);
      }
   }
   
   @Override
   public void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles){}
}
