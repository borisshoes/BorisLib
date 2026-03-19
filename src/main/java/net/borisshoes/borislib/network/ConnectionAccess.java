package net.borisshoes.borislib.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;

/**
 * Duck interface applied to Connection via mixin
 * to expose internal channel and flush/send methods.
 */
public interface ConnectionAccess {
   Channel borislib$getChannel();
   
   void borislib$flushChannel();
   
   void borislib$sendNoFlush(Packet<?> packet, @Nullable ChannelFutureListener listener);
}


