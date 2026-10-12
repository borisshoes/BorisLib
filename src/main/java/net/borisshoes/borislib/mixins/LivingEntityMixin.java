package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.conditions.ConditionHolder;
import net.borisshoes.borislib.conditions.ConditionMath;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.conditions.EntityConditions;
import net.borisshoes.borislib.conditions.NearsightCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

   @Inject(method = "baseTick", at = @At("TAIL"))
   private void borislib$tickConditions(CallbackInfo ci){
      LivingEntity entity = (LivingEntity) (Object) this;
      if(entity.level().isClientSide()) return;
      EntityConditions conditions = ((ConditionHolder) this).borislib$getConditions();
      if(conditions == null) return;
      Conditions.tickEntity(entity, conditions);
   }

   // Might, Feeble, Vulnerability and Fortitude: scales the hit, or cancels it outright when the factor is 0
   @WrapMethod(method = "hurtServer")
   private boolean borislib$conditionDamage(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original){
      float factor = Conditions.damageFactor((LivingEntity) (Object) this, source);
      if(factor == 1.0f) return original.call(level, source, damage);
      if(factor == 0.0f) return false;
      if(factor == Float.POSITIVE_INFINITY) return original.call(level, source, Float.MAX_VALUE);
      return original.call(level, source, damage * factor);
   }

   @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;"))
   private void borislib$clearConditionsOnDeath(DamageSource damageSource, CallbackInfo ci){
      LivingEntity hurtEntity = (LivingEntity) (Object) this;
      if(hurtEntity.level().isClientSide()) return;
      Conditions.removeAllConditions(hurtEntity.level().getServer(), hurtEntity);
   }

   @ModifyReturnValue(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"))
   private boolean borislib$canTarget(boolean original, LivingEntity target){
      EntityConditions conditions = ((ConditionHolder) this).borislib$getConditions();
      if(conditions == null) return original;
      float nearsight = conditions.getValue(Conditions.NEARSIGHT);
      if(nearsight <= 0.0f) return original;
      LivingEntity livingEntity = (LivingEntity) (Object) this;
      double range = NearsightCondition.radiusForMagnitude(nearsight) / 2.0;
      if(target.distanceToSqr(livingEntity) > range * range && !livingEntity.is(BorisLib.IGNORES_NEARSIGHT)){
         return false;
      }
      return original;
   }

   // Falter: multiplication commutes with vanilla's knockback resistance scaling, so order does not matter
   @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private double borislib$falterKnockback(double power){
      EntityConditions conditions = ((ConditionHolder) this).borislib$getConditions();
      if(conditions == null) return power;
      return power * ConditionMath.amplify(conditions.getValue(Conditions.FALTER));
   }

   // Vitality and Blight: covers everything that heals through heal(), but not direct setHealth calls
   @WrapMethod(method = "heal")
   private void borislib$conditionHeal(float heal, Operation<Void> original){
      EntityConditions conditions = ((ConditionHolder) this).borislib$getConditions();
      if(conditions == null){
         original.call(heal);
         return;
      }
      float factor = ConditionMath.ratio(conditions.getValue(Conditions.VITALITY), conditions.getValue(Conditions.BLIGHT));
      if(factor == 0.0f) return;
      original.call(heal * factor);
   }

   // Obscurity and Exposure: the visibility percent scales the range at which mobs acquire this entity
   @ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
   private double borislib$conditionVisibility(double original, ServerLevel level, Entity targetingEntity){
      EntityConditions conditions = ((ConditionHolder) this).borislib$getConditions();
      if(conditions == null) return original;
      return original * ConditionMath.ratio(conditions.getValue(Conditions.EXPOSURE), conditions.getValue(Conditions.OBSCURITY));
   }
}
