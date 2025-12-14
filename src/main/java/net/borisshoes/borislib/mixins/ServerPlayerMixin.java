package net.borisshoes.borislib.mixins;

import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
   
   @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addDuringTeleport(Lnet/minecraft/world/entity/Entity;)V"))
   private void borislib$resetVelTrackerTeleport1(TeleportTransition teleportTarget, CallbackInfoReturnable<ServerPlayer> cir){
      ServerPlayer player = (ServerPlayer) (Object) this;
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
   }
   
   @Inject(method = "hasChangedDimension", at = @At("HEAD"))
   private void borislib$resetVelTrackerTeleport2(CallbackInfo ci){
      ServerPlayer player = (ServerPlayer) (Object) this;
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
   }
}
