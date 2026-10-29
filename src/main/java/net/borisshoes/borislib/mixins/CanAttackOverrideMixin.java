package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.borisshoes.borislib.conditions.NearsightCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// These mobs override canAttack without calling super, so LivingEntityMixin's Nearsight check never reaches them
@Mixin({Warden.class, EnderDragon.class})
public class CanAttackOverrideMixin {

   @ModifyReturnValue(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"))
   private boolean borislib$canTarget(boolean original, LivingEntity target){
      return original && !NearsightCondition.hidesTarget((LivingEntity) (Object) this, target);
   }
}
