package net.borisshoes.borislib.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.mixins.SectionBlocksUpdatePacketAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static net.borisshoes.borislib.BorisLib.LOGGER;

/**
 * Core packet buffer — one instance per player connection.
 * <p>
 * Called from a {@code @WrapOperation} on the {@code connection.send()} call
 * inside {@code ServerCommonPacketListenerImpl.send()}. By the time packets
 * reach this class, ALL other mixins (including Polymer's packet transformers,
 * server-side translations, virtual entity handlers, etc.) have already run.
 * We only control whether the underlying Netty write flushes or is deferred.
 */
public class PacketBuffer {
   
   private final ServerCommonPacketListenerImpl listener;
   private final ConnectionAccess connectionAccess;
   private final MinecraftServer server;
   
   private final AtomicInteger bufferedCount = new AtomicInteger(0);
   private final AtomicLong currentBatchBytes = new AtomicLong(0);
   
   // Block update queue for explosion optimization
   private final ConcurrentLinkedQueue<PacketEntry> blockQueue = new ConcurrentLinkedQueue<>();
   
   // Coalesce queue — packets queued here are wrapped into BundlePackets on flush
   private final ConcurrentLinkedQueue<PacketEntry> coalesceQueue = new ConcurrentLinkedQueue<>();
   
   // Write queue — ALL default buffered packets are queued here instead of calling
   // Connection.send() per packet. At flush time, ONE event loop task writes them all.
   // On the event loop thread, Connection.send() takes the direct path (no lambda
   // allocation, no cross-thread task scheduling). This replaces N expensive
   // event loop task submissions with N cheap ConcurrentLinkedQueue.add() operations
   // + 1 event loop task that writes everything.
   private final ConcurrentLinkedQueue<PacketEntry> writeQueue = new ConcurrentLinkedQueue<>();
   
   // Guard against re-entrant flush (explosion opt creates new packets that re-enter the pipeline)
   private boolean insideFlush = false;
   
   // ─── Cached config values (refreshed on reload, never read from config in hot path) ───
   private volatile boolean cachedBatchingEnabled;
   private volatile int cachedMaxBatchSize;
   private volatile int cachedMaxBatchBytes;
   private volatile int cachedSafetyMargin;
   private volatile int cachedFlushInterval;
   private volatile boolean cachedExplosionOptEnabled;
   private volatile int cachedExplosionThreshold;
   private volatile boolean cachedExplosionLogEnabled;
   private volatile boolean cachedChatBypass;
   private volatile boolean cachedOffThreadBypass;
   private volatile boolean cachedWriteQueue;
   private volatile boolean cachedPacketCoalescing;
   private volatile int cachedBundleLimit;
   private volatile BatchingMode cachedBatchingMode;
   
   // Interval mode scheduler
   private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "borislib-interval-flusher");
      t.setDaemon(true);
      return t;
   });
   private ScheduledFuture<?> intervalTask;
   
   // Pre-resolved packet classification map (shared across all buffers, rebuilt on reload)
   // Single IdentityHashMap lookup replaces multiple separate set lookups per packet
   private static volatile boolean classesInitialized = false;
   private static volatile IdentityHashMap<Class<?>, PacketAction> packetClassification = new IdentityHashMap<>();
   
   private enum PacketAction {
      CRITICAL,   // flush buffer first, then send with flush (keepalive, disconnect, etc.)
      INSTANT,    // flush buffer first, then send with flush (config-defined)
      CHAT,       // send with flush if chat bypass enabled, otherwise buffer
      IGNORED,    // always send with vanilla flush, never buffer
      COALESCE    // queue for bundling into BundlePackets on flush (particles, sounds, etc.)
   }
   
   // Active buffer registry for config reload propagation
   private static final Set<PacketBuffer> activeBuffers = ConcurrentHashMap.newKeySet();
   
   private record PacketEntry(Packet<?> packet, @Nullable ChannelFutureListener listener) {
   }
   
   public PacketBuffer(ServerCommonPacketListenerImpl listener, Connection connection, MinecraftServer server){
      this.listener = listener;
      this.connectionAccess = (ConnectionAccess) connection;
      this.server = server;
      ensureClassesInitialized();
      refreshConfig();
      setupIntervalTask();
      activeBuffers.add(this);
   }
   
   /**
    * Snapshots all config values into local fields so the hot path never touches the config system.
    * Called on construction and on config reload.
    */
   public void refreshConfig(){
      if(BorisLib.CONFIG == null){
         cachedBatchingEnabled = false;
         cachedMaxBatchSize = 1024;
         cachedMaxBatchBytes = 32000;
         cachedSafetyMargin = 64;
         cachedFlushInterval = 25;
         cachedExplosionOptEnabled = true;
         cachedExplosionThreshold = 512;
         cachedExplosionLogEnabled = false;
         cachedChatBypass = true;
         cachedOffThreadBypass = true;
         cachedWriteQueue = true;
         cachedPacketCoalescing = true;
         cachedBundleLimit = 4000;
         cachedBatchingMode = BatchingMode.SMART_EXECUTION;
         return;
      }
      cachedBatchingEnabled = BorisLib.CONFIG.getBoolean(BorisLib.BATCHING_ENABLED);
      cachedMaxBatchSize = BorisLib.CONFIG.getInt(BorisLib.BATCHING_MAX_BATCH_SIZE);
      cachedMaxBatchBytes = BorisLib.CONFIG.getInt(BorisLib.BATCHING_MAX_BATCH_BYTES);
      cachedSafetyMargin = BorisLib.CONFIG.getInt(BorisLib.BATCHING_SAFETY_MARGIN);
      cachedFlushInterval = BorisLib.CONFIG.getInt(BorisLib.BATCHING_FLUSH_INTERVAL);
      cachedExplosionOptEnabled = BorisLib.CONFIG.getBoolean(BorisLib.OPT_EXPLOSIONS_ENABLED);
      cachedExplosionThreshold = BorisLib.CONFIG.getInt(BorisLib.OPT_EXPLOSIONS_THRESHOLD);
      cachedExplosionLogEnabled = BorisLib.CONFIG.getBoolean(BorisLib.OPT_EXPLOSIONS_LOG);
      cachedChatBypass = BorisLib.CONFIG.getBoolean(BorisLib.BATCHING_CHAT_BYPASS);
      cachedOffThreadBypass = BorisLib.CONFIG.getBoolean(BorisLib.BATCHING_OFF_THREAD_BYPASS);
      cachedWriteQueue = BorisLib.CONFIG.getBoolean(BorisLib.BATCHING_WRITE_QUEUE);
      cachedPacketCoalescing = BorisLib.CONFIG.getBoolean(BorisLib.BATCHING_PACKET_COALESCING);
      cachedBundleLimit = BorisLib.CONFIG.getInt(BorisLib.BATCHING_COALESCE_BUNDLE_LIMIT);
      Object modeVal = BorisLib.CONFIG.getValue(BorisLib.BATCHING_MODE);
      cachedBatchingMode = modeVal instanceof BatchingMode mode ? mode : BatchingMode.SMART_EXECUTION;
   }
   
   // ─── Packet class resolution ────────────────────────────────────────
   
   private static synchronized void ensureClassesInitialized(){
      if(classesInitialized) return;
      
      IdentityHashMap<Class<?>, PacketAction> map = new IdentityHashMap<>();
      
      // Critical packets - always bypass, must arrive ASAP
      resolveClasses(List.of(
            "ClientboundKeepAlivePacket",
            "ClientboundDisconnectPacket",
            "ClientboundLoginDisconnectPacket",
            "ClientboundStatusResponsePacket",
            "ClientboundPingResponsePacket"
      ), PacketAction.CRITICAL, map);
      
      // Chat packets - bypass when chat bypass is enabled
      resolveClasses(List.of(
            "ClientboundPlayerChatPacket",
            "ClientboundSystemChatPacket",
            "ClientboundDisguisedChatPacket",
            "ClientboundResourcePackPushPacket"
      ), PacketAction.CHAT, map);
      
      // Instant packets from config
      Object instantObj = BorisLib.CONFIG != null ? BorisLib.CONFIG.getValue(BorisLib.BATCHING_INSTANT_PACKETS) : null;
      if(instantObj instanceof List<?> list){
         List<String> names = new ArrayList<>();
         for(Object o : list) names.add(o.toString());
         resolveClasses(names, PacketAction.INSTANT, map);
      }
      
      // Ignored packets from config
      Object ignoredObj = BorisLib.CONFIG != null ? BorisLib.CONFIG.getValue(BorisLib.BATCHING_IGNORED_PACKETS) : null;
      if(ignoredObj instanceof List<?> list){
         List<String> names = new ArrayList<>();
         for(Object o : list) names.add(o.toString());
         resolveClasses(names, PacketAction.IGNORED, map);
      }
      
      // Coalesce packets from config (bundled into BundlePackets on flush)
      Object coalesceObj = BorisLib.CONFIG != null ? BorisLib.CONFIG.getValue(BorisLib.BATCHING_COALESCE_PACKETS) : null;
      if(coalesceObj instanceof List<?> list){
         List<String> names = new ArrayList<>();
         for(Object o : list) names.add(o.toString());
         resolveClasses(names, PacketAction.COALESCE, map);
      }
      
      // Publish atomically
      packetClassification = map;
      classesInitialized = true;
   }
   
   /**
    * Resolves packet class names to their Class objects and maps them to an action.
    * Supports simple names (e.g. "ClientboundLevelParticlesPacket"),
    * inner classes with dot or dollar syntax (e.g. "ClientboundMoveEntityPacket.Pos"),
    * and fully-qualified names.
    */
   private static void resolveClasses(List<String> names, PacketAction action, IdentityHashMap<Class<?>, PacketAction> map){
      String[] packages = {
            "net.minecraft.network.protocol.game.",
            "net.minecraft.network.protocol.common.",
            "net.minecraft.network.protocol.login.",
            "net.minecraft.network.protocol.status.",
            "net.minecraft.network.protocol.handshake."
      };
      for(String name : names){
         // Support dot syntax for inner classes (e.g. "Foo.Bar" → "Foo$Bar")
         String resolved = name.contains(".") && !name.contains("net.") ? name : name;
         String dollarForm = resolved.replace('.', '$');
         
         try{
            boolean found = false;
            for(String pkg : packages){
               // Try the name as-is first, then with $ for inner classes
               for(String candidate : new String[]{resolved, dollarForm}){
                  try{
                     map.put(Class.forName(pkg + candidate), action);
                     found = true;
                     break;
                  }catch(ClassNotFoundException ignored){
                  }
               }
               if(found) break;
            }
            if(!found){
               // Try as fully-qualified name
               try{
                  map.put(Class.forName(resolved), action);
               }catch(ClassNotFoundException e1){
                  map.put(Class.forName(dollarForm), action);
               }
            }
         }catch(Exception e){
            LOGGER.debug("Could not resolve packet class: {}", name);
         }
      }
   }
   
   // ─── Interval mode ──────────────────────────────────────────────────
   
   private void setupIntervalTask(){
      cancelIntervalTask();
      if(!cachedBatchingEnabled) return;
      if(getBatchingMode() == BatchingMode.INTERVAL){
         int interval = getFlushInterval();
         intervalTask = scheduler.scheduleAtFixedRate(
               () -> flush(FlushReason.INTERVAL),
               interval, interval, TimeUnit.MILLISECONDS
         );
      }
   }
   
   private void cancelIntervalTask(){
      if(intervalTask != null){
         intervalTask.cancel(false);
         intervalTask = null;
      }
   }
   
   // ─── Main entry point (called from WrapOperation) ───────────────────
   
   /**
    * Called from the {@code @WrapOperation} in the mixin. By this point, the packet has
    * already been through the full {@code ServerCommonPacketListenerImpl.send()} pipeline
    * including ALL other mixins (Polymer, etc.). We decide whether to write-with-flush
    * (bypass) or write-without-flush (buffer) via the {@code original} operation.
    *
    * @param packet     the fully-transformed packet
    * @param listener   the channel future listener (may be null)
    * @param flush      the original flush flag (always true in vanilla)
    * @param original   the original {@code Connection.send()} call to invoke
    * @param connection the connection instance
    */
   public void handleOutgoingPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush,
                                    Operation<Void> original, Connection connection){
      // If we're inside our own flush (e.g., explosion opt created a new chunk packet
      // that re-entered the pipeline), just send normally to avoid recursion.
      if(insideFlush){
         original.call(connection, packet, listener, true);
         return;
      }
      
      // Master toggle off → vanilla behavior
      if(!cachedBatchingEnabled){
         original.call(connection, packet, listener, flush);
         return;
      }
      
      // Bundle packets (used by Polymer for virtual entities) → send immediately with flush
      if(packet instanceof BundlePacket<?>){
         flush(FlushReason.INSTANT);
         original.call(connection, packet, listener, true);
         return;
      }
      
      // Full chunk packets are huge → flush buffer first, then send with flush
      if(packet instanceof ClientboundLevelChunkWithLightPacket){
         flush(FlushReason.INSTANT);
         original.call(connection, packet, listener, true);
         return;
      }
      
      // Off-thread packets → send with flush
      if(cachedOffThreadBypass && !server.isSameThread()){
         original.call(connection, packet, listener, flush);
         return;
      }
      
      // Not in GAME state yet (login/config phase) → don't buffer
      if(!(this.listener instanceof ServerGamePacketListenerImpl)){
         original.call(connection, packet, listener, flush);
         return;
      }
      
      // Single map lookup for packet classification
      PacketAction action = packetClassification.get(packet.getClass());
      if(action != null){
         switch(action){
            case IGNORED -> {
               original.call(connection, packet, listener, flush);
               return;
            }
            case CHAT -> {
               if(cachedChatBypass){
                  original.call(connection, packet, listener, flush);
                  return;
               }
               // Chat bypass disabled — fall through to buffer
            }
            case CRITICAL, INSTANT -> {
               Metrics.logicalCounter.incrementAndGet();
               flush(FlushReason.INSTANT);
               original.call(connection, packet, listener, true);
               Metrics.physicalCounter.incrementAndGet();
               return;
            }
            case COALESCE -> {
               if(cachedPacketCoalescing){
                  Metrics.logicalCounter.incrementAndGet();
                  coalesceQueue.add(new PacketEntry(packet, listener));
                  return;
               }
               // Coalescing disabled — fall through to normal buffering
            }
         }
      }
      
      // Count as a logical packet
      Metrics.logicalCounter.incrementAndGet();
      
      // Explosion/chunk optimization: queue block updates for later processing
      if(cachedExplosionOptEnabled && isBlockUpdate(packet)){
         blockQueue.add(new PacketEntry(packet, listener));
         return;
      }
      
      // ─── Default: QUEUE or BUFFER the packet ────────────
      if(cachedWriteQueue){
         // Write queue enabled: queue cheaply on the server thread instead of calling
         // Connection.send() per packet. At flush time, ONE event loop task writes all
         // queued packets — each write runs ON the event loop thread where Connection.send()
         // takes the direct path (no lambda allocation, no cross-thread task scheduling).
         writeQueue.add(new PacketEntry(packet, listener));
         Metrics.writeQueuedCounter.incrementAndGet();
      }else{
         // Write queue disabled: fall back to per-packet Connection.send() with flush=false.
         // Each call schedules a lambda on the event loop from the server thread.
         original.call(connection, packet, listener, false);
      }
      int count = bufferedCount.incrementAndGet();
      
      // Count limit flush (cheap integer compare)
      if(count >= cachedMaxBatchSize){
         flush(FlushReason.LIMIT_COUNT);
      }
   }
   
   // ─── Flush ──────────────────────────────────────────────────────────
   
   public synchronized void flush(FlushReason reason){
      if(insideFlush) return; // guard re-entrant flush
      insideFlush = true;
      try{
         // Collect packets from internal queues (block optimization, coalescing, write queue)
         List<PacketEntry> toWrite = new ArrayList<>();
         
         // Process any pending block-update queue first
         // (may trigger listener.send() for chunk resends, which bypass via insideFlush)
         if(!blockQueue.isEmpty()){
            processBlockQueue(toWrite);
         }
         
         // Coalesce queued packets into BundlePackets
         if(!coalesceQueue.isEmpty()){
            processCoalesceQueue(toWrite);
         }
         
         // Drain the general write queue (empty when write queue is disabled)
         PacketEntry entry;
         while((entry = writeQueue.poll()) != null){
            toWrite.add(entry);
         }
         
         Channel channel = connectionAccess.borislib$getChannel();
         
         if(!toWrite.isEmpty()){
            // Queued packets need to be written — submit ONE event loop task.
            // On the event loop thread, Connection.send() takes the direct write path —
            // no lambda allocation, no cross-thread task scheduling per packet.
            // The final channel.flush() also flushes any packets already in Netty's
            // outbound buffer (from original.call() when write queue is disabled).
            if(channel != null && channel.isOpen()){
               channel.eventLoop().execute(() -> {
                  if(!channel.isOpen()) return;
                  for(PacketEntry pe : toWrite){
                     connectionAccess.borislib$sendNoFlush(pe.packet(), pe.listener());
                  }
                  // Track bytes for metrics (safe to query Netty on event loop thread)
                  if(channel.unsafe() != null && channel.unsafe().outboundBuffer() != null){
                     Metrics.totalBytesSent.addAndGet(channel.unsafe().outboundBuffer().totalPendingWriteBytes());
                  }
                  // THE ACTUAL FLUSH — one kernel syscall for the entire batch
                  channel.flush();
               });
               Metrics.physicalCounter.incrementAndGet();
               Metrics.eventLoopTasksSubmitted.incrementAndGet();
            }
         }else if(bufferedCount.get() > 0){
            // Write queue disabled — default packets are already in Netty's outbound buffer
            // (written via original.call() with flush=false). Just flush the channel directly.
            if(channel != null && channel.isOpen()){
               Metrics.totalBytesSent.addAndGet(getPendingBytes());
               connectionAccess.borislib$flushChannel();
               Metrics.physicalCounter.incrementAndGet();
            }
         }
         
         // Reset counters
         bufferedCount.set(0);
         currentBatchBytes.set(0);
      }finally{
         insideFlush = false;
      }
   }
   
   // ─── Pending bytes ──────────────────────────────────────────────────
   
   public long getPendingBytes(){
      Channel channel = connectionAccess.borislib$getChannel();
      if(channel != null && channel.unsafe() != null && channel.unsafe().outboundBuffer() != null){
         return channel.unsafe().outboundBuffer().totalPendingWriteBytes();
      }
      return 0;
   }
   
   // ─── Block update detection & optimization ──────────────────────────
   
   private boolean isBlockUpdate(Packet<?> packet){
      return packet instanceof ClientboundBlockUpdatePacket ||
            packet instanceof ClientboundSectionBlocksUpdatePacket ||
            packet instanceof ClientboundBlockEntityDataPacket;
   }
   
   private long getChunkKey(Packet<?> packet){
      if(packet instanceof ClientboundBlockUpdatePacket blockPacket){
         return ChunkPos.asLong(blockPacket.getPos());
      }else if(packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket){
         return ((SectionBlocksUpdatePacketAccessor) sectionPacket).getSectionPos().chunk().toLong();
      }else if(packet instanceof ClientboundBlockEntityDataPacket dataPacket){
         return ChunkPos.asLong(dataPacket.getPos());
      }
      return 0;
   }
   
   private void processBlockQueue(List<PacketEntry> toWrite){
      if(blockQueue.isEmpty()) return;
      
      // Drain into local list
      List<PacketEntry> processingQueue = new ArrayList<>();
      PacketEntry entry;
      while((entry = blockQueue.poll()) != null){
         processingQueue.add(entry);
      }
      if(processingQueue.isEmpty()) return;
      
      // Need the player reference for world access
      if(!(listener instanceof ServerGamePacketListenerImpl gameListener)){
         // Can't optimize without player — add to write batch
         toWrite.addAll(processingQueue);
         return;
      }
      
      // Group packets by chunk key
      Map<Long, List<PacketEntry>> batchMap = new HashMap<>();
      Map<Long, Integer> chunkBlockCounts = new HashMap<>();
      
      for(PacketEntry pe : processingQueue){
         long key = getChunkKey(pe.packet());
         batchMap.computeIfAbsent(key, k -> new ArrayList<>()).add(pe);
         
         int count = 1;
         if(pe.packet() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket){
            count = ((SectionBlocksUpdatePacketAccessor) sectionPacket).getPositions().length;
         }
         chunkBlockCounts.merge(key, count, Integer::sum);
      }
      
      int threshold = getExplosionThreshold();
      boolean logOpt = isExplosionLogEnabled();
      
      for(Map.Entry<Long, List<PacketEntry>> mapEntry : batchMap.entrySet()){
         long chunkKey = mapEntry.getKey();
         List<PacketEntry> entries = mapEntry.getValue();
         int totalChanges = chunkBlockCounts.getOrDefault(chunkKey, 0);
         
         if(totalChanges >= threshold){
            int x = ChunkPos.getX(chunkKey);
            int z = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = gameListener.player.level().getChunk(x, z);
            
            if(chunk != null){
                Metrics.optimizedChunks.incrementAndGet();
                if(logOpt){
                   LOGGER.info("Explosion optimization: replaced {} block updates in chunk [{}, {}] with full chunk resend",
                         totalChanges, x, z);
                }
                
                ServerLevel serverLevel = (ServerLevel) gameListener.player.level();
                ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(
                      chunk,
                      serverLevel.getLightEngine(),
                      null, null
                );
                // Send through the FULL pipeline (Polymer transforms the chunk data).
                // insideFlush=true prevents our handleOutgoingPacket from re-buffering it;
                // it will send with flush=true, which is fine for large chunk packets.
                listener.send(chunkPacket);
            }else{
                toWrite.addAll(entries);
            }
         }else{
            toWrite.addAll(entries);
         }
      }
   }
   
   // ─── Packet coalescing ──────────────────────────────────────────────
   
   /**
    * Drains the coalesce queue and wraps packets into BundlePackets.
    * Any mix of clientbound game packet types can be coalesced together.
    * Splits into multiple bundles if exceeding vanilla's 4096 sub-packet limit.
    */
   @SuppressWarnings("unchecked")
   private void processCoalesceQueue(List<PacketEntry> toWrite){
      if(coalesceQueue.isEmpty()) return;
      
      // Drain queue
      List<Packet<? super ClientGamePacketListener>> batch = new ArrayList<>();
      PacketEntry entry;
      while((entry = coalesceQueue.poll()) != null){
         batch.add((Packet<? super ClientGamePacketListener>) entry.packet());
      }
      if(batch.isEmpty()) return;
      
      // Split into bundles respecting vanilla's 4096 client-side limit
      int limit = cachedBundleLimit;
      for(int i = 0; i < batch.size(); i += limit){
         List<Packet<? super ClientGamePacketListener>> chunk = batch.subList(i, Math.min(i + limit, batch.size()));
         ClientboundBundlePacket bundle = new ClientboundBundlePacket(chunk);
         toWrite.add(new PacketEntry(bundle, null));
      }
   }
   
   // ─── Config helpers (return cached values — refreshed via refreshConfig()) ────
   
   private BatchingMode getBatchingMode(){
      return cachedBatchingMode;
   }
   
   private int getFlushInterval(){
      return cachedFlushInterval;
   }
   
   private int getExplosionThreshold(){
      return cachedExplosionThreshold;
   }
   
   private boolean isExplosionLogEnabled(){
      return cachedExplosionLogEnabled;
   }
   
   // ─── Lifecycle ──────────────────────────────────────────────────────
   
   /**
    * Called on disconnect or cleanup. Flushes remaining packets and stops interval task.
    */
   public void cleanup(){
      activeBuffers.remove(this);
      try{
         flush(FlushReason.MANUAL);
      }catch(Exception ignored){
      }
      cancelIntervalTask();
   }
   
   /**
    * Called on config reload — forces re-resolution of packet class sets
    * and refreshes cached config on all active buffers.
    */
   public static void reload(){
      classesInitialized = false;
      ensureClassesInitialized();
      for(PacketBuffer buffer : activeBuffers){
         buffer.refreshConfig();
         buffer.setupIntervalTask();
      }
   }
}





