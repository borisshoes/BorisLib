package net.borisshoes.borislib.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.ChannelFutureListener;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.network.FlushReason;
import net.borisshoes.borislib.network.PacketBuffer;
import net.borisshoes.borislib.network.PacketBufferAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMxiin implements PacketBufferAccess {
   
   @Shadow
   @Final
   protected MinecraftServer server;
   
   @Shadow
   @Final
   protected Connection connection;
   
   @Unique
   private PacketBuffer borislib$buffer;
   
   @Inject(method = "<init>", at = @At("TAIL"))
   private void borislib$initBuffer(CallbackInfo ci){
      this.borislib$buffer = new PacketBuffer((ServerCommonPacketListenerImpl) (Object) this, this.connection, this.server);
   }
   
   @Override
   public PacketBuffer borislib$getBuffer(){
      return this.borislib$buffer;
   }
   
   /**
    * Wraps the {@code connection.send(packet, listener, flush)} call INSIDE
    * {@code ServerCommonPacketListenerImpl.send(Packet, ChannelFutureListener)}.
    * <p>
    * This fires AFTER all other mixins on {@code send()} have run (Polymer's packet
    * transformers, server-side translations, virtual entity handlers, etc.).
    * By the time we see the packet here, it is fully transformed.
    * We only control whether the underlying write flushes immediately or is deferred.
    */
   @WrapOperation(
         method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
         at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
   private void borislib$wrapConnectionSend(Connection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, Operation<Void> original){
      // When batching is enabled, delegate to the PacketBuffer for flush control
      if(borislib$buffer != null && BorisLib.CONFIG != null && BorisLib.CONFIG.getBoolean(BorisLib.BATCHING_ENABLED)){
         borislib$buffer.handleOutgoingPacket(packet, listener, flush, original, connection);
         
         // Handle terminal packets
         if(packet.isTerminal()){
            borislib$buffer.flush(FlushReason.INSTANT);
         }
         return;
      }
      
      // Batching disabled — vanilla behavior
      original.call(connection, packet, listener, flush);
   }
   
   @Inject(method = "onDisconnect", at = @At("HEAD"))
   private void borislib$cleanupOnDisconnect(CallbackInfo ci){
      if(borislib$buffer != null){
         borislib$buffer.cleanup();
      }
   }
}
