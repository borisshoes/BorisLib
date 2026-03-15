package net.borisshoes.borislib.mixins;

import io.netty.channel.ChannelFutureListener;
import net.borisshoes.borislib.utils.ParticlePacketBuffer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMxiin {
   
   @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
   private void borislib$bufferParticlePacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci){
      // ParticlePacketBuffer.buffer() checks the bypass flag internally, so fallback sends during flush pass through
      ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
      if(ParticlePacketBuffer.buffer(self, packet)){
         ci.cancel();
      }
   }
   
   @Inject(method = "onDisconnect", at = @At("HEAD"))
   private void borislib$cleanupOnDisconnect(CallbackInfo ci){
      ParticlePacketBuffer.removeConnection((ServerCommonPacketListenerImpl) (Object) this);
   }
}
