package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
   
   @Shadow
   public ServerPlayerEntity player;
   
   @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;setMovement(ZZLnet/minecraft/util/math/Vec3d;)V"))
   private void borislib$updateVelocityTracker(PlayerMoveC2SPacket packet, CallbackInfo ci, @Local Vec3d velocity){
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player) && !player.isDead()){
         PlayerMovementEntry newEntry = new PlayerMovementEntry(player, player.getEntityPos(), velocity, System.nanoTime());
         PLAYER_MOVEMENT_TRACKER.put(player,newEntry);
      }else{
         PLAYER_MOVEMENT_TRACKER.put(player,PlayerMovementEntry.blankEntry(player));
      }
   }
}
