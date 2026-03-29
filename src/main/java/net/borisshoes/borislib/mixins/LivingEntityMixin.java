package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.conditions.Conditions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
   
   @ModifyVariable(method = "hurtServer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private float borislib$modifyHurtDamage(float original, ServerLevel serverLevel, DamageSource damageSource){
      LivingEntity hurtEntity = (LivingEntity) (Object) this;
      if(hurtEntity.level().isClientSide()) return original;
      float vulnerable = Conditions.getConditionValue(hurtEntity.getUUID(),Conditions.VULNERABILITY);
      float fortitude = Conditions.getConditionValue(hurtEntity.getUUID(),Conditions.FORTITUDE);
      original *= vulnerable * fortitude;
      if(!(damageSource.isDirect() && damageSource.getDirectEntity() instanceof LivingEntity dealer)) return original;
      float might = Conditions.getConditionValue(dealer.getUUID(),Conditions.MIGHT);
      float feeble = Conditions.getConditionValue(dealer.getUUID(),Conditions.FEEBLE);
      original *= might * feeble;
      return original;
   }
   
   @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;"))
   private void borislib$clearConditionsOnDeath(DamageSource damageSource, CallbackInfo ci){
      LivingEntity hurtEntity = (LivingEntity) (Object) this;
      if(hurtEntity.level().isClientSide()) return;
      Conditions.removeAllConditions(hurtEntity.level().getServer(),hurtEntity);
   }
   
   @ModifyReturnValue(method= "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at=@At("RETURN"))
   private boolean borislib$canTarget(boolean original, LivingEntity target){
      LivingEntity livingEntity = (LivingEntity) (Object) this;
      if(livingEntity.level().isClientSide()) return original;
      float nearsight = Conditions.getConditionValue(livingEntity.getUUID(),Conditions.NEARSIGHT);
      boolean outOfRange = target.distanceTo(livingEntity) > nearsight / 2.0;
      if(nearsight != Conditions.NEARSIGHT.value().getBase() && outOfRange && !livingEntity.is(BorisLib.IGNORES_NEARSIGHT)){
         return false;
      }
      return original;
   }
}
