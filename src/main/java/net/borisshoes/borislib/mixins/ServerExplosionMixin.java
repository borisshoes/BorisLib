package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.borisshoes.borislib.conditions.ConditionHolder;
import net.borisshoes.borislib.conditions.ConditionMath;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.conditions.EntityConditions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {

   // Rupture: explosion knockback is applied straight to velocity and never reaches LivingEntity.knockback,
   // so it is scaled where the knockback vector is built. The same vector is what gets sent to hit players.
   @ModifyExpressionValue(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
   private Vec3 borislib$ruptureKnockback(Vec3 knockback, @Local Entity entity){
      if(!(entity instanceof LivingEntity living)) return knockback;
      EntityConditions conditions = ((ConditionHolder) living).borislib$getConditions();
      if(conditions == null) return knockback;
      float rupture = conditions.getValue(Conditions.RUPTURE);
      return rupture > 0.0f ? knockback.scale(ConditionMath.amplify(rupture)) : knockback;
   }
}
