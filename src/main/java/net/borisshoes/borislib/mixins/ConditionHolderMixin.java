package net.borisshoes.borislib.mixins;

import net.borisshoes.borislib.conditions.ConditionHolder;
import net.borisshoes.borislib.conditions.EntityConditions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public class ConditionHolderMixin implements ConditionHolder {

   @Unique
   private EntityConditions borislib$conditions;

   @Override
   public EntityConditions borislib$getConditions(){
      return this.borislib$conditions;
   }

   @Override
   public void borislib$setConditions(EntityConditions conditions){
      this.borislib$conditions = conditions;
   }
}
