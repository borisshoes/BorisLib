package net.borisshoes.borislib.mixins;

import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
   
   @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;onDimensionChanged(Lnet/minecraft/entity/Entity;)V"))
   private void borislib$resetVelTrackerTeleport1(TeleportTarget teleportTarget, CallbackInfoReturnable<ServerPlayerEntity> cir){
      ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
   }
   
   @Inject(method = "onTeleportationDone", at = @At("HEAD"))
   private void borislib$resetVelTrackerTeleport2(CallbackInfo ci){
      ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
   }
}
