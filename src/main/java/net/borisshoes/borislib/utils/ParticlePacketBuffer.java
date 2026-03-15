package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buffers particle packets per-connection and flushes them as bundle packets at the end of each server tick.
 * This drastically reduces network overhead when many particles are spawned in a single tick.
 * <p>
 * Vanilla Minecraft enforces a hard limit of 4096 packets per bundle on the client side.
 * Exceeding this causes an {@code IllegalStateException("Too many packets in a bundle")} and disconnects the client.
 * To respect this, the buffer automatically sends a bundle and resets when a connection's buffer reaches
 * {@link #BUNDLE_SIZE_LIMIT} packets. Any remaining packets are sent at the end of the tick via {@link #flush()}.
 * <p>
 * If sending a bundle fails for a connection, packets are sent individually as a fallback (vanilla behavior).
 */
public class ParticlePacketBuffer {
   
   /**
    * Maximum number of packets per bundle. Vanilla enforces 4096 on the client.
    * We use a slightly lower value to leave headroom in case vanilla or other mods
    * also include packets in their own bundles within the same network flush.
    */
   private static final int BUNDLE_SIZE_LIMIT = 4000;
   
   private static final Map<ServerCommonPacketListenerImpl, List<ClientboundLevelParticlesPacket>> BUFFERS = new ConcurrentHashMap<>();
   
   /**
    * Thread-local flag set during flush and immediate-send operations to prevent the mixin
    * from re-intercepting packets we are intentionally sending unbuffered.
    */
   private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
   
   /**
    * Attempts to buffer a particle packet for the given connection.
    * If the buffer reaches {@link #BUNDLE_SIZE_LIMIT}, it immediately sends the accumulated
    * packets as a bundle and resets the buffer before adding the new packet.
    *
    * @return true if the packet was buffered (or sent as part of an immediate bundle flush),
    *         false if buffering is disabled, bypassed, or the packet is not a particle packet
    */
   public static boolean buffer(ServerCommonPacketListenerImpl connection, Packet<?> packet){
      if(BYPASS.get()) return false;
      if(!(packet instanceof ClientboundLevelParticlesPacket particlePacket)) return false;
      if(!isEnabled()) return false;
      
      List<ClientboundLevelParticlesPacket> list = BUFFERS.computeIfAbsent(connection, k -> Collections.synchronizedList(new ArrayList<>()));
      
      // If the buffer is at the bundle size limit, immediately flush it as a bundle before adding more
      if(list.size() >= BUNDLE_SIZE_LIMIT){
         List<ClientboundLevelParticlesPacket> toSend;
         synchronized(list){
            toSend = new ArrayList<>(list);
            list.clear();
         }
         sendBundle(connection, toSend);
      }
      
      list.add(particlePacket);
      return true;
   }
   
   /**
    * Flushes all buffered particle packets for all connections as bundle packets.
    * Should be called at the end of each server tick from the server thread.
    * <p>
    * If sending a bundle fails for a connection, falls back to sending each packet individually.
    * All sends during flush bypass the mixin to prevent re-buffering.
    */
   public static void flush(){
      if(BUFFERS.isEmpty()) return;
      
      // Snapshot and clear the map atomically per entry to avoid issues with concurrent modification
      Map<ServerCommonPacketListenerImpl, List<ClientboundLevelParticlesPacket>> snapshot = new HashMap<>();
      Iterator<Map.Entry<ServerCommonPacketListenerImpl, List<ClientboundLevelParticlesPacket>>> it = BUFFERS.entrySet().iterator();
      while(it.hasNext()){
         Map.Entry<ServerCommonPacketListenerImpl, List<ClientboundLevelParticlesPacket>> entry = it.next();
         List<ClientboundLevelParticlesPacket> packets;
         synchronized(entry.getValue()){
            packets = new ArrayList<>(entry.getValue());
            entry.getValue().clear();
         }
         it.remove();
         if(!packets.isEmpty()){
            snapshot.put(entry.getKey(), packets);
         }
      }
      
      BYPASS.set(true);
      try{
         for(Map.Entry<ServerCommonPacketListenerImpl, List<ClientboundLevelParticlesPacket>> entry : snapshot.entrySet()){
            ServerCommonPacketListenerImpl connection = entry.getKey();
            List<ClientboundLevelParticlesPacket> packets = entry.getValue();
            
            // Split into chunks of BUNDLE_SIZE_LIMIT to respect the vanilla client limit
            for(int i = 0; i < packets.size(); i += BUNDLE_SIZE_LIMIT){
               List<ClientboundLevelParticlesPacket> chunk = packets.subList(i, Math.min(i + BUNDLE_SIZE_LIMIT, packets.size()));
               sendBundleWithFallback(connection, chunk);
            }
         }
      }finally{
         BYPASS.set(false);
      }
   }
   
   /**
    * Sends a list of particle packets as a bundle. Sets the bypass flag to prevent re-interception.
    * Used for immediate mid-tick flushes when the buffer fills up.
    */
   private static void sendBundle(ServerCommonPacketListenerImpl connection, List<ClientboundLevelParticlesPacket> packets){
      if(packets.isEmpty()) return;
      BYPASS.set(true);
      try{
         sendBundleWithFallback(connection, packets);
      }finally{
         BYPASS.set(false);
      }
   }
   
   /**
    * Attempts to send packets as a bundle. If that fails, sends each packet individually.
    * Assumes BYPASS is already set to true.
    */
   private static void sendBundleWithFallback(ServerCommonPacketListenerImpl connection, List<ClientboundLevelParticlesPacket> packets){
      try{
         @SuppressWarnings("unchecked")
         List<Packet<? super ClientGamePacketListener>> bundleContents = (List<Packet<? super ClientGamePacketListener>>) (List<?>) packets;
         connection.send(new ClientboundBundlePacket(bundleContents));
      }catch(Exception e){
         // Bundle failed — fall back to sending each packet individually
         BorisLib.LOGGER.debug("Bundle send failed for a connection, falling back to individual packets: {}", e.getMessage());
         for(ClientboundLevelParticlesPacket packet : packets){
            try{
               connection.send(packet);
            }catch(Exception ignored){
               // Connection is likely dead, stop trying
               break;
            }
         }
      }
   }
   
   /**
    * Removes the buffer for a disconnected connection to prevent memory leaks.
    */
   public static void removeConnection(ServerCommonPacketListenerImpl connection){
      BUFFERS.remove(connection);
   }
   
   /**
    * Checks if particle packet bundling is enabled via config.
    */
   public static boolean isEnabled(){
      return BorisLib.CONFIG != null && BorisLib.CONFIG.getBoolean(BorisLib.PARTICLE_PACKET_BUNDLE_OPTIMIZATION);
   }
}

