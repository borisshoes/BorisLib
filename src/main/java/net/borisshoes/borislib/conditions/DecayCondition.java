package net.borisshoes.borislib.conditions;

import net.borisshoes.borislib.BorisLib;
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

/**
 * Built-in harmful {@link Condition} that deals magic damage over time.
 *
 * <p>The magnitude is health lost per second, dealt as {@code m / 2} every 10 ticks of the entity's own
 * tick counter. The interval is not shorter because vanilla ignores hits landing inside the invulnerability
 * window. Entities tagged {@link net.borisshoes.borislib.BorisLib#IGNORES_DECAY} are immune.</p>
 *
 * <p>Cap: {@link Float#MAX_VALUE}.</p>
 */
public class DecayCondition extends Condition {
   private static final ConditionParticles PARTICLES = ConditionParticles.of(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x40581E), 40.0f, 20, 0.01f);

   protected DecayCondition(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "decay"), MobEffectCategory.HARMFUL);
   }

   @Override
   public boolean canApply(LivingEntity entity){
      return !entity.is(BorisLib.IGNORES_DECAY);
   }

   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
      if(entity.tickCount % 10 == 0 && entity.level() instanceof ServerLevel level){
         DamageSource source = entity.damageSources().source(DamageTypes.MAGIC);
         entity.hurtServer(level, source, value / 2.0f);
      }
   }

   @Override
   public ConditionParticles getDefaultParticles(){
      return PARTICLES;
   }
}
