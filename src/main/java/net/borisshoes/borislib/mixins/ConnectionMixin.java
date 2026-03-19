package net.borisshoes.borislib.mixins;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.borisshoes.borislib.network.ConnectionAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionAccess {
   
   @Shadow
   private Channel channel;
   
   @Invoker("send")
   public abstract void borislib$invokeSend(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);
   
   @Override
   public Channel borislib$getChannel(){
      return this.channel;
   }
   
   @Override
   public void borislib$flushChannel(){
      if(this.channel != null && this.channel.isOpen()){
         this.channel.flush();
      }
   }
   
   @Override
   public void borislib$sendNoFlush(Packet<?> packet, @Nullable ChannelFutureListener listener){
      borislib$invokeSend(packet, listener, false);
   }
}
