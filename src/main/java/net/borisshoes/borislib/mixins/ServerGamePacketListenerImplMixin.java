package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
   
   @Shadow
   public ServerPlayer player;
   
   @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setOnGroundWithMovement(ZZLnet/minecraft/world/phys/Vec3;)V"))
   private void borislib$updateVelocityTracker(ServerboundMovePlayerPacket packet, CallbackInfo ci, @Local Vec3 velocity){
      if(PLAYER_MOVEMENT_TRACKER.containsKey(player) && !player.isDeadOrDying()){
         PlayerMovementEntry newEntry = new PlayerMovementEntry(player, player.position(), velocity, System.nanoTime());
         PLAYER_MOVEMENT_TRACKER.put(player,newEntry);
      }else{
         PLAYER_MOVEMENT_TRACKER.put(player,PlayerMovementEntry.blankEntry(player));
      }
   }
}
