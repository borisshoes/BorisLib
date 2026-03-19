# BorisLib Network Optimization — Fabric Port Blueprint

> Derived from PulseMC (Paper/Purpur fork for MC 1.21.11).
> This document covers **only** the features being ported: Smart Packet Batching, Explosion/Chunk Optimization, Metrics & Monitoring, Configuration, and Commands.
> Virtual blocks, virtual block events, and virtual entities are **excluded**.

---

## Table of Contents

1. [Feature 1: Smart Packet Batching](#1-smart-packet-batching)
2. [Feature 2: Explosion & Chunk Optimization](#2-explosion--chunk-optimization)
3. [Feature 3: Metrics & Monitoring](#3-metrics--monitoring)
4. [Feature 4: Configuration (BorisLib Config)](#4-configuration)
5. [Feature 5: Commands](#5-commands)
6. [Access Wideners Required](#6-access-wideners-required)
7. [Mixin Targets Summary](#7-mixin-targets-summary)

---

## 1. Smart Packet Batching

### 1.1 Concept

Vanilla Minecraft calls `channel.writeAndFlush()` for every single outbound packet, which results in one kernel `send()` syscall per packet. On a busy server this means thousands of syscalls per second per player.

Pulse replaces this with a **write-only buffer + periodic flush** model:
- Packets are written to the Netty channel with `channel.write()` (no flush — no syscall).
- The channel is flushed (one syscall for the entire batch) when a trigger condition is met.

### 1.2 The Core Hook — Intercepting Packet Sends

Pulse patches `ServerCommonPacketListenerImpl.send()` to route all packets through `PulseBuffer`. In Fabric, this is a **Mixin** into the same class (mapped name may differ — use `ServerCommonPacketListenerImpl` or the Yarn/Mojmap equivalent).

**Original vanilla flow (simplified):**
```java
// ServerCommonPacketListenerImpl.java (vanilla)
public void send(Packet<?> packet, @Nullable PacketSendListener sendListener) {
    // ... null/disconnect checks ...
    boolean flush = !this.suspendFlushingOnServerThread || !this.server.isSameThread();
    this.connection.send(packet, sendListener, flush);  // flush=true most of the time
}
```

**What Pulse does — redirect into the buffer:**
```java
// Pulse's patched version of send()
public void send(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
    if (packet == null || this.processedDisconnect) return;

    if (this.pulseBuffer != null) {
        this.pulseBuffer.add(packet, sendListener);

        if (packet.isTerminal()) {
            this.pulseBuffer.flush();
            this.close();
        }
        return;
    }

    // Fallback: vanilla behavior if buffer not initialized
    this.connection.send(packet, sendListener, true);
}
```

**Fabric Mixin equivalent (WrapOperation approach):**

> **Key design decision:** We use `@WrapOperation` on the `connection.send()` call
> *inside* `ServerCommonPacketListenerImpl.send()` rather than `@Inject` at `HEAD`.
> This ensures our batching layer runs **after** all other mixins (Polymer's packet
> transformers, virtual entity handlers, etc.) have already processed the packet.
> We only control the final flush behavior.

```java
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin implements PacketBufferAccess {

    @Unique
    private PacketBuffer borislib$buffer; // Your buffer instance

    // Initialize buffer in constructor or via @Inject into <init>

    @WrapOperation(
          method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
          at = @At(value = "INVOKE",
                target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
    private void borislib$wrapConnectionSend(Connection connection, Packet<?> packet,
            @Nullable ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        if (borislib$buffer != null && ConfigManager.enabled) {
            // Packet is fully transformed by Polymer etc. — decide flush behavior
            borislib$buffer.handleOutgoingPacket(packet, listener, flush, original, connection);
            if (packet.isTerminal()) {
                borislib$buffer.flush(FlushReason.INSTANT);
            }
        } else {
            original.call(connection, packet, listener, flush);
        }
    }
}
```

### 1.3 The Tick-End Flush Hook

Pulse flushes the buffer at the **end of every server tick** per player, inside `ServerGamePacketListenerImpl.tick()`:

```java
// Pulse's patch to ServerGamePacketListenerImpl.tick()
// Inserted AFTER the idle-kick check, at the END of the tick method:
if (this.pulseBuffer != null) {
    this.pulseBuffer.flush();
}
```

**Fabric Mixin equivalent:**
```java
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void borislib$flushOnTickEnd(CallbackInfo ci) {
        // Access the buffer attached to this connection and flush it
        PacketBuffer buffer = ((PacketBufferAccess) this).borislib$getBuffer();
        if (buffer != null) {
            buffer.flush(FlushReason.TICK);
        }
    }
}
```

### 1.4 The Buffer — Full Implementation Logic

This is the heart of the system. Below is the complete `PulseBuffer.add()` method with annotations explaining every decision. **Strip out the virtual-block lines** (marked with `// [VIRTUAL - SKIP]`) when porting.

```java
public void add(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
    if (packet == null) return;

    // [VIRTUAL - SKIP] Virtual block replacement logic was here

    // --- MASTER TOGGLE ---
    // If the system is disabled, send everything immediately with flush (vanilla behavior).
    if (!ConfigManager.enabled) {
        listener.connection.send(packet, sendListener, true);
        return;
    }

    // --- FULL CHUNK PACKETS: always send immediately ---
    // ClientboundLevelChunkWithLightPacket is huge; flush buffer first, then send it directly.
    if (packet instanceof ClientboundLevelChunkWithLightPacket) {
        flush(FlushReason.INSTANT);
        listener.connection.send(packet, sendListener, true);
        return;
    }

    Class<?> packetClass = packet.getClass();

    // --- IGNORED PACKETS (config-defined, always bypass) ---
    if (ignoredPacketClasses.contains(packetClass)) {
        listener.connection.send(packet, sendListener, true);
        return;
    }

    // --- THREAD SAFETY + CHAT SAFETY ---
    // Off-thread packets and chat packets are sent immediately to avoid concurrency issues
    // and to prevent chat messages from being delayed/batched.
    if (!server.isSameThread() || chatPacketClasses.contains(packetClass)) {
        listener.connection.send(packet, sendListener, true);
        return;
    }

    // --- JOIN SAFETY ---
    // During login/config phase, the connection is not yet a ServerGamePacketListenerImpl.
    // Don't buffer packets in this state.
    if (!(listener instanceof ServerGamePacketListenerImpl)) {
        listener.connection.send(packet, sendListener, true);
        return;
    }

    // --- COUNT THIS AS A LOGICAL PACKET ---
    Metrics.logicalCounter.incrementAndGet();

    // --- EXPLOSION/CHUNK OPTIMIZATION ---
    // If enabled, block-update packets are routed to a separate queue for batch analysis.
    if (ConfigManager.optExplosions && isBlockUpdate(packet)) {
        handleBlockUpdate(packet, sendListener);
        return;
    }

    // --- CRITICAL / INSTANT PACKETS ---
    // These must arrive ASAP. Flush the entire buffer first, then send this packet immediately.
    if (criticalPacketClasses.contains(packetClass) || instantPacketClasses.contains(packetClass)) {
        flush(FlushReason.INSTANT);
        listener.connection.send(packet, sendListener, true);
        Metrics.physicalCounter.incrementAndGet();
        return;
    }

    // --- DEFAULT: BUFFER THE PACKET ---
    queuePacketToNetty(packet, sendListener);
}
```

### 1.5 Writing Without Flushing + Flush Triggers

The key to batching is the difference between `send(packet, listener, false)` (write only) and `send(packet, listener, true)` (write + flush). The third boolean parameter controls whether `Channel.flush()` is called.

**Queuing a packet (write-only, no syscall):**
```java
public void queuePacketToNetty(Packet<?> packet, @Nullable ChannelFutureListener listenerCb) {
    // false = write to channel buffer WITHOUT flushing to the network
    listener.connection.send(packet, listenerCb, false);
    int count = bufferedCount.incrementAndGet();

    // --- BYTE LIMIT FLUSH ---
    // Check if the Netty outbound buffer has exceeded the configured byte limit.
    // If so, flush immediately to prevent MTU overflow.
    if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safetyMargin)) {
        flush(FlushReason.LIMIT_BYTES);
        return;
    }

    // --- COUNT LIMIT FLUSH ---
    if (count >= ConfigManager.maxBatchSize) {
        flush(FlushReason.LIMIT_COUNT);
    }
}
```

**The flush method itself:**
```java
public synchronized void flush(FlushReason reason) {
    // Process any pending block-update queue first (explosion optimization)
    if (!blockQueue.isEmpty()) {
        processBlockQueue();
    }

    long pending = getPendingBytes();

    // Nothing to flush
    if (bufferedCount.get() == 0 && pending == 0) return;

    // Track bytes for metrics
    Metrics.totalBytesSent.addAndGet(pending);

    // THE ACTUAL FLUSH — one kernel syscall for the entire batch
    listener.connection.flushChannel();

    // Reset counters
    bufferedCount.set(0);
    currentBatchBytes.set(0);
    Metrics.physicalCounter.incrementAndGet();
}
```

**Reading pending bytes from Netty (used for byte-limit checks):**
```java
public long getPendingBytes() {
    var channel = listener.connection.channel;
    if (channel != null && channel.unsafe() != null && channel.unsafe().outboundBuffer() != null) {
        return channel.unsafe().outboundBuffer().totalPendingWriteBytes();
    }
    return 0;
}
```

### 1.6 Packet Classification Sets

Pulse pre-resolves packet class names from config into `Set<Class<?>>` for O(1) lookup. Uses `ReferenceOpenHashSet` from fastutil for identity-based hashing (faster than `equals()`).

**Four packet categories:**

| Category | Purpose | Contents |
|---|---|---|
| `criticalPacketClasses` | Must arrive instantly, always flush-before-send | `ClientboundKeepAlivePacket`, `ClientboundDisconnectPacket`, `ClientboundLoginDisconnectPacket`, `ClientboundStatusResponsePacket`, `ClientboundPingPacket` |
| `instantPacketClasses` | User-configured bypass packets (PvP, etc.) | Default: `ClientboundHurtAnimationPacket`, `ClientboundDamageEventPacket`, `ClientboundBlockEntityDataPacket` |
| `chatPacketClasses` | Always sent immediately (never batched) | `ClientboundPlayerChatPacket`, `ClientboundSystemChatPacket`, `ClientboundDisguisedChatPacket`, `ClientboundResourcePackPushPacket` |
| `ignoredPacketClasses` | User-configured, Pulse never touches these | (empty by default) |

**Class resolution from config strings:**
```java
private static void resolveClasses(List<String> names, Set<Class<?>> into) {
    for (String name : names) {
        try {
            String[] packages = {
                "net.minecraft.network.protocol.game.",
                "net.minecraft.network.protocol.common.",
                "net.minecraft.network.protocol.login.",
                "net.minecraft.network.protocol.status.",
                "net.minecraft.network.protocol.handshake."
            };
            boolean found = false;
            for (String pkg : packages) {
                try {
                    into.add(Class.forName(pkg + name));
                    found = true;
                    break;
                } catch (ClassNotFoundException ignored) {}
            }
            if (!found) {
                into.add(Class.forName(name));  // try fully-qualified name
            }
        } catch (Exception ignored) {}
    }
}
```

### 1.7 Three Batching Modes

```java
public enum BatchingMode {
    SMART_EXECUTION,  // Flush on tick end + byte/count limits + critical packets
    STRICT_TICK,      // Flush ONLY on tick end (max batching, highest latency)
    INTERVAL          // Flush every N ms via ScheduledExecutorService
}
```

**Interval mode setup (in buffer constructor):**
```java
private void setupIntervalTask() {
    if (ConfigManager.batchingMode == ConfigManager.BatchingMode.INTERVAL) {
        intervalTask = scheduler.scheduleAtFixedRate(
            () -> flush(FlushReason.INTERVAL),
            ConfigManager.flushInterval,
            ConfigManager.flushInterval,
            TimeUnit.MILLISECONDS
        );
    }
}

// Shared single-thread scheduler for ALL players' interval flushes:
private static final ScheduledExecutorService scheduler =
    Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("borislib-interval-flusher")
            .setDaemon(true)
            .build()
    );
```

### 1.8 FlushReason Enum

```java
public enum FlushReason {
    LIMIT_BYTES,   // Buffer exceeded max byte size
    LIMIT_COUNT,   // Buffer exceeded max packet count
    TICK,          // End of server tick
    INSTANT,       // Critical/instant packet triggered immediate flush
    INTERVAL,      // Interval scheduler (INTERVAL mode only)
    MANUAL         // API or command triggered
}
```

### 1.9 Bypass Conditions Summary (Decision Tree)

When a packet enters `add()`, it is sent **immediately with flush** (bypassing the buffer entirely) if ANY of these are true:

1. `packet == null`
2. Connection is disconnected (`processedDisconnect`)
3. Master toggle is off (`!ConfigManager.enabled`)
4. Packet is `ClientboundLevelChunkWithLightPacket` (too large to batch; flush buffer first, then send)
5. Packet class is in `ignoredPacketClasses`
6. Packet is sent from a non-server thread (`!server.isSameThread()`)
7. Packet is a chat packet (`chatPacketClasses`)
8. Connection is not in GAME state (still in login/config phase)

If none of those match, the packet enters the buffer. It may still be flushed immediately if:

9. It's a block update and explosion optimization is on → routed to block queue
10. It's a critical or instant packet → flush buffer first, then send this one immediately

Otherwise → `queuePacketToNetty()` (write without flush, check byte/count limits).

---

## 2. Explosion & Chunk Optimization

### 2.1 Concept

When a large explosion (TNT, creeper, etc.) destroys many blocks in a single chunk, vanilla sends hundreds of individual `ClientboundBlockUpdatePacket`s or `ClientboundSectionBlocksUpdatePacket`s. This is wasteful — a single `ClientboundLevelChunkWithLightPacket` (full chunk resend) is more efficient.

Pulse intercepts block-update packets, groups them by chunk, and if the count exceeds a threshold, replaces them all with one full chunk packet.

### 2.2 Block Update Detection

```java
private boolean isBlockUpdate(Packet<?> packet) {
    return packet instanceof ClientboundBlockUpdatePacket ||
           packet instanceof ClientboundSectionBlocksUpdatePacket ||
           packet instanceof ClientboundBlockEntityDataPacket;
}
```

### 2.3 Block Update Queuing

When a block update packet arrives and explosion optimization is enabled, it's not sent immediately — it goes into a concurrent queue:

```java
private record PacketEntry(Packet<?> packet, @Nullable ChannelFutureListener listener) {}
private final ConcurrentLinkedQueue<PacketEntry> blockQueue = new ConcurrentLinkedQueue<>();

private void handleBlockUpdate(Packet<?> packet, @Nullable ChannelFutureListener listener) {
    // [VIRTUAL - SKIP] Fake block detection logic was here.
    // For BorisLib, ALL real block updates go straight to the queue:
    blockQueue.add(new PacketEntry(packet, listener));
}
```

### 2.4 Chunk Key Extraction

Each packet is associated with a chunk via a `long` key:

```java
private long getChunkKey(Packet<?> packet) {
    if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
        return ChunkPos.asLong(blockPacket.getPos());
    } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
        return sectionPacket.sectionPos.chunk().toLong();
    } else if (packet instanceof ClientboundBlockEntityDataPacket dataPacket) {
        return ChunkPos.asLong(dataPacket.getPos());
    }
    return 0;
}
```

> **Note:** `ClientboundSectionBlocksUpdatePacket` has private fields `sectionPos`, `positions`, and `states` in vanilla. Pulse makes them `public` via a source patch. In Fabric, you need an **Access Widener**. See [Section 6](#6-access-wideners-required).

### 2.5 The Core Algorithm — `processBlockQueue()`

This runs inside `flush()` before the actual Netty flush. It groups queued block updates by chunk and decides whether to optimize:

```java
private void processBlockQueue() {
    if (blockQueue.isEmpty()) return;

    // Drain the concurrent queue into a local list
    List<PacketEntry> processingQueue = new ArrayList<>();
    PacketEntry packetEntry;
    while ((packetEntry = blockQueue.poll()) != null) {
        processingQueue.add(packetEntry);
    }
    if (processingQueue.isEmpty()) return;

    // Need the player reference for world access
    if (!(listener instanceof ServerGamePacketListenerImpl gameListener) || gameListener.player == null) {
        processingQueue.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
        return;
    }

    // Group packets by chunk key
    Map<Long, List<PacketEntry>> batchMap = new HashMap<>();
    Map<Long, Integer> chunkBlockCounts = new HashMap<>();

    for (PacketEntry entry : processingQueue) {
        long key = getChunkKey(entry.packet());
        batchMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);

        // SectionBlocksUpdate can contain multiple positions in one packet
        int count = 1;
        if (entry.packet() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            count = sectionPacket.positions.length;  // Access-widened field
        }
        chunkBlockCounts.merge(key, count, Integer::sum);
    }

    // Per-chunk decision
    for (Map.Entry<Long, List<PacketEntry>> entry : batchMap.entrySet()) {
        long chunkKey = entry.getKey();
        List<PacketEntry> entries = entry.getValue();
        int totalChanges = chunkBlockCounts.getOrDefault(chunkKey, 0);

        if (totalChanges >= ConfigManager.optExplosionThreshold) {
            // OPTIMIZATION: Replace all individual packets with one full chunk packet
            int x = ChunkPos.getX(chunkKey);
            int z = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = gameListener.player.level().getChunkIfLoaded(x, z);

            if (chunk != null) {
                Metrics.optimizedChunks.incrementAndGet();

                // Construct a full chunk data + light packet
                ClientboundLevelChunkWithLightPacket chunkPacket =
                    new ClientboundLevelChunkWithLightPacket(
                        chunk,
                        gameListener.player.level().getLightEngine(),
                        null,  // bitSet for sky light (null = all)
                        null   // bitSet for block light (null = all)
                    );
                queuePacketToNetty(chunkPacket, null);
            } else {
                // Chunk not loaded — fall through and send individual packets
                entries.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
            }
        } else {
            // Below threshold — send individual packets as-is
            entries.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
        }
    }
}
```

### 2.6 Fabric Considerations

In Fabric, the Yarn-mapped equivalents are:
- `ClientboundBlockUpdatePacket` → `BlockUpdateS2CPacket`
- `ClientboundSectionBlocksUpdatePacket` → `ChunkDeltaUpdateS2CPacket`
- `ClientboundBlockEntityDataPacket` → `BlockEntityUpdateS2CPacket`
- `ClientboundLevelChunkWithLightPacket` → `ChunkDataS2CPacket`
- `LevelChunk` → `WorldChunk`
- `ChunkPos` → `ChunkPos`

The `ChunkDeltaUpdateS2CPacket` has private fields that need access widening:
- `sectionPos` (type `ChunkSectionPos`)
- `positions` (type `short[]`)
- `states` (type `BlockState[]`)

---

## 3. Metrics & Monitoring

### 3.1 Collected Counters (AtomicLongs, updated in real-time)

```java
public static final AtomicLong logicalCounter = new AtomicLong(0);   // Every packet that enters add()
public static final AtomicLong physicalCounter = new AtomicLong(0);  // Every flush() call
public static final AtomicLong optimizedChunks = new AtomicLong(0);  // Chunks replaced by full resend
public static final AtomicLong totalBytesSent = new AtomicLong(0);   // Raw bytes flushed
```

**Where these are incremented:**
- `logicalCounter` — incremented once per packet that passes all bypass checks in `PulseBuffer.add()` (line 182 of PulseBuffer.java).
- `physicalCounter` — incremented once per `flush()` call (line 232) AND once per instant-packet direct send (line 207).
- `totalBytesSent` — incremented in `flush()` with the value of `getPendingBytes()` right before flushing (line 226).
- `optimizedChunks` — incremented in `processBlockQueue()` when a chunk is replaced (line 329).

### 3.2 Derived Metrics (computed periodically)

```java
public static double ppsLogical = 0;          // Logical packets per second
public static double ppsPhysical = 0;         // Physical flush operations per second
public static double cpuUsage = 0;            // Current JVM CPU load %
public static double vanillaCpuEst = 0;       // Estimated CPU if not batching
public static long savedAllocationsBytes = 0; // Estimated memory savings
public static long totalSavedSyscalls = 0;    // Cumulative saved syscalls
public static double networkSpeedKbs = 0;     // Bandwidth in KB/s
```

### 3.3 Full Metrics Computation — Scheduled Task

Runs on a `ScheduledExecutorService` every N seconds (configurable, default 1):

```java
public static void start() {
    if (currentTask != null && !currentTask.isCancelled()) {
        currentTask.cancel(false);
    }

    int interval = Math.max(1, ConfigManager.metricsUpdateInterval);

    scheduler.scheduleAtFixedRate(() -> {
        if (!ConfigManager.metricsEnabled) return;

        long currentLogical = logicalCounter.get();
        long currentPhysical = physicalCounter.get();
        long currentBytes = totalBytesSent.get();

        // PPS calculation: delta since last sample / interval seconds
        ppsLogical = (double) (currentLogical - lastLogical) / interval;
        ppsPhysical = (double) (currentPhysical - lastPhysical) / interval;
        double bytesDiff = currentBytes - lastBytes;
        networkSpeedKbs = (bytesDiff / 1024.0) / interval;

        lastLogical = currentLogical;
        lastPhysical = currentPhysical;
        lastBytes = currentBytes;

        // CPU usage via com.sun.management.OperatingSystemMXBean
        // getProcessCpuLoad() returns 0.0-1.0 fraction of one core.
        // Multiply by 100 * cores for total system-relative percentage.
        cpuUsage = osBean.getProcessCpuLoad() * 100.0 * Runtime.getRuntime().availableProcessors();

        // CPU savings estimation:
        // Each saved syscall is estimated to save ~8.5 microseconds of CPU time.
        // Formula: (savedPerSec * 8.5µs) / 10000 = percentage points recovered
        double savedPerSec = ppsLogical - ppsPhysical;
        if (savedPerSec > 0) {
            totalSavedSyscalls += (long) (savedPerSec * interval);
            double cpuRecovered = (savedPerSec * 8.5) / 10000.0;
            vanillaCpuEst = cpuUsage + cpuRecovered;
        } else {
            vanillaCpuEst = cpuUsage;
        }

        // Memory savings estimation:
        // Each saved packet avoids ~512 bytes of buffer allocation
        savedAllocationsBytes += (long) (savedPerSec * 512 * interval);

    }, 1, interval, TimeUnit.SECONDS);
}
```

### 3.4 BossBar Display — `MetricsBar`

A toggleable boss bar that updates on the same interval as metrics. Uses Adventure API (available in Fabric via adventure-platform-fabric).

**Efficiency calculation:**
```java
double logical = Math.max(1, Metrics.ppsLogical);
double efficiency = (logical - Metrics.ppsPhysical) / logical;
// Clamped to 0.0 - 1.0
```

**Color coding:**
| Efficiency | Color |
|---|---|
| > 75% | GREEN |
| > 40% | YELLOW |
| ≤ 40% | RED |

**Bandwidth display:**
```java
double speed = Metrics.networkSpeedKbs;
String speedStr;
if (speed > 1024) {
    speedStr = String.format("%.2f MB/s", speed / 1024.0);
} else {
    speedStr = String.format("%.1f KB/s", speed);
}
```

**Full BossBar title format (MiniMessage):**
```
<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>|
  <white>Eff: <color:COLOR>XX%</color> <dark_gray>|
  <white>Vanilla: <aqua>NNN p/s <dark_gray>|
  <white>Out: <aqua>NNN p/s</aqua> <gray>(X.X KB/s)
```

**Fabric equivalent:** Use `ServerBossBar` (vanilla) or Adventure API boss bars. Toggle per-player with a `Set<UUID>` of viewers.

**Full MetricsBar update logic:**
```java
private static void updateBar() {
    double logical = Math.max(1, Metrics.ppsLogical);
    double efficiency = (logical - Metrics.ppsPhysical) / logical;
    if (efficiency < 0) efficiency = 0;
    if (efficiency > 1) efficiency = 1;

    BossBar.Color color;
    if (efficiency > 0.75) color = BossBar.Color.GREEN;
    else if (efficiency > 0.40) color = BossBar.Color.YELLOW;
    else color = BossBar.Color.RED;

    double speed = Metrics.networkSpeedKbs;
    String speedStr;
    if (speed > 1024) {
        speedStr = String.format("%.2f MB/s", speed / 1024.0);
    } else {
        speedStr = String.format("%.1f KB/s", speed);
    }

    String title = String.format(
        "<bold><gradient:#FF005D:#FF0048>BorisLib</gradient></bold> <dark_gray>| "
        + "<white>Eff: <color:%s>%d%%</color> <dark_gray>| "
        + "<white>Vanilla: <aqua>%d p/s <dark_gray>| "
        + "<white>Out: <aqua>%d p/s</aqua> <gray>(%s)",
        (efficiency > 0.75 ? "#55FF55" : (efficiency > 0.4 ? "#FFFF55" : "#FF5555")),
        (int)(efficiency * 100),
        (int) Metrics.ppsLogical,
        (int) Metrics.ppsPhysical,
        speedStr
    );

    bossBar.name(mm.deserialize(title));
    bossBar.progress((float) efficiency);
    bossBar.color(color);
}
```

---

## 4. Configuration

All config values below should be exposed via BorisLib's config system. Each optimization feature must be independently toggleable.

### 4.1 Complete Config Option Reference

#### Core

| Key | Type | Default | Validation | Description |
|---|---|---|---|---|
| `enabled` | `boolean` | `true` | Type check (Boolean) | Master toggle. When false, all packets are sent vanilla-style (immediate flush). |

#### Batching

| Key | Type | Default | Validation / Limits | Description |
|---|---|---|---|---|
| `batching.mode` | `enum` | `SMART_EXECUTION` | Must be one of: `SMART_EXECUTION`, `STRICT_TICK`, `INTERVAL`. Falls back to `SMART_EXECUTION` on parse failure. | The batching strategy. |
| `batching.maxBatchSize` | `int` | `128` | **Error if:** `≤ 0` ("Batch size must be > 0!"). **Warning if:** `> 4096` ("Values > 4096 may cause packet loss/disconnects!"). Config default file says 128; code default field is 4096. | Max packets in a single batch before forced flush. |
| `batching.maxBatchBytes` | `int` | `32000` | **Error if:** `< 512` ("MTU limit too low (<512)! Network may stall."). **Warning if:** `> 64000` ("> 64000 bytes is dangerous due to packet fragmentation risks"). | Max bytes in Netty outbound buffer before forced flush. |
| `batching.flushInterval` | `int` | `25` | **Error if:** `< 1` ("Flush interval must be at least 1ms!"). | Flush period in milliseconds for INTERVAL mode. |
| `batching.safetyMarginBytes` | `int` | `64` | Type check (Integer), no range validation. | Buffer is flushed when pending bytes exceed `maxBatchBytes - safetyMarginBytes`. Prevents MTU overflow without expensive per-packet size calculation. |
| `batching.instantPackets` | `List<String>` | `["ClientboundHurtAnimationPacket", "ClientboundDamageEventPacket", "ClientboundBlockEntityDataPacket"]` | Each entry resolved against NMS protocol packages. Invalid names silently ignored. | Packet class simple names that bypass batching and are sent immediately. Important for PvP responsiveness. |
| `batching.ignoredPackets` | `List<String>` | `[]` | Same resolution as instantPackets. | Packet class names that Pulse/BorisLib never touches — sent vanilla-style immediately. |

#### Explosion/Chunk Optimization

| Key | Type | Default | Validation / Limits | Description |
|---|---|---|---|---|
| `optimization.explosions.enabled` | `boolean` | `true` | Type check (Boolean) | Toggle for chunk optimization feature. |
| `optimization.explosions.blockChangeThreshold` | `int` | `512` | Type check (Integer), no explicit range validation. | Number of block changes in one chunk that triggers a full chunk resend instead of individual packets. |

#### Metrics

| Key | Type | Default | Validation / Limits | Description |
|---|---|---|---|---|
| `metrics.enabled` | `boolean` | `true` | Type check (Boolean) | Toggle for metrics collection. When false, the scheduled task still runs but early-returns. |
| `metrics.updateInterval` | `int` | `1` | **Error if:** `< 1` ("Interval must be at least 1 second!"). | How often (in seconds) metrics are recalculated and the BossBar is updated. |
| `metrics.modules.network` | `boolean` | `true` | None | Enable network stats (PPS, bandwidth). |
| `metrics.modules.cpuEstimation` | `boolean` | `true` | None | Enable CPU savings estimation. |
| `metrics.modules.memoryImpact` | `boolean` | `true` | None | Enable memory/GC impact tracking. |

### 4.2 BorisLib-Specific Additions

Beyond Pulse's config, BorisLib should add:

| Key | Type | Default | Description |
|---|---|---|---|
| `batching.enabled` | `boolean` | `true` | Independent toggle for batching (separate from master toggle, allows disabling batching while keeping other features). |
| `batching.chatPacketsBypass` | `boolean` | `true` | Whether chat packets always bypass the buffer. Could be toggled off if not needed. |
| `batching.offThreadBypass` | `boolean` | `true` | Whether off-main-thread packets bypass the buffer. |
| `optimization.explosions.logOptimizations` | `boolean` | `false` | Log to console when a chunk is optimized (for debugging compat issues). |

### 4.3 Config Validation Behavior

Pulse's config system uses a builder pattern for validation. On errors, the value is reset to the default and a report is generated. On warnings, the value is kept but a warning is logged. All errors/warnings are collected into a list and displayed to the admin on `/borislib reload`.

**Validation types used:**
1. **Type check** — ensures the YAML value is the expected Java type (Boolean, Integer, String, etc.)
2. **Predicate validation** — `validate(val -> val >= 1, "Must be >= 1!")` — resets to default on failure
3. **Predicate warning** — `warn(val -> val <= 4096, "High values may cause issues")` — keeps value, logs warning
4. **Parser** — custom parse function (e.g., enum from string), falls back to default on parse failure

### 4.4 Live Reload

On config reload:
1. Re-read all config values from file.
2. Call `PulseBuffer.reload()` which sets `classesInitialized = false` — next packet send will re-resolve all packet class sets from the new config.
3. Call `MetricsBar.reload()` which cancels the old scheduled task and starts a new one with the updated interval.
4. Call `Metrics.reload()` (same — restart with new interval).

---

## 5. Commands

### 5.1 Command Tree (Brigadier)

```
/borislib
  ├── reload              — Reload config, restart metrics/bar, reinitialize packet caches
  ├── bar                 — Toggle BossBar metrics display (player-only)
  ├── stats [type]        — Show performance statistics
  │     ├── network       — PPS, bandwidth, calls saved, optimized chunks
  │     ├── cpu           — CPU usage, vanilla estimate, efficiency delta
  │     ├── ram           — Saved allocations
  │     └── all           — Everything (default)
  └── (BorisLib config commands)
```

### 5.2 Fabric Command Registration

```java
// In mod initializer or via CommandRegistrationCallback:
CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    dispatcher.register(
        CommandManager.literal("borislib")
            .requires(source -> source.hasPermissionLevel(2))  // OP level 2

            .then(CommandManager.literal("reload")
                .executes(ctx -> reload(ctx)))

            .then(CommandManager.literal("bar")
                .executes(ctx -> toggleBar(ctx)))

            .then(CommandManager.literal("stats")
                .executes(ctx -> sendStats(ctx, "all"))
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("network");
                        builder.suggest("cpu");
                        builder.suggest("ram");
                        builder.suggest("all");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> sendStats(ctx, StringArgumentType.getString(ctx, "type")))
                ))
    );
});
```

### 5.3 Stats Output Format

**Network stats:**
```
--- [ BorisLib Network ] ---
PPS (Logical):  1432 pkt/s (Vanilla)
PPS (Physical): 87 pkt/s   (BorisLib)
Calls Saved:    1345/s (+93.9%)

Bandwidth:      142.50 kB/s
Optimized Chunks: 12 (mass updates prevented)
```

**CPU stats:**
```
--- [ BorisLib CPU Analyzer ] ---
Current Usage: 12.45%
Vanilla Est:   13.59%

BorisLib Efficiency: -1.143% Total Load
```

**RAM stats:**
```
--- [ BorisLib Memory ] ---
Saved Allocations: 847 MB (Total)
```

### 5.4 Reload Logic

```java
private static int reload(CommandContext<ServerCommandSource> ctx) {
    // 1. Re-read config
    boolean hasIssues = ConfigManager.load();

    // 2. Reinitialize packet class caches
    PacketBuffer.reload();  // sets classesInitialized = false

    // 3. Restart metrics scheduler with potentially new interval
    Metrics.reload();
    MetricsBar.reload();

    // 4. Report to admin
    if (!hasIssues) {
        ctx.getSource().sendFeedback(() -> Text.literal("Configuration reloaded successfully!"), false);
    } else {
        ctx.getSource().sendFeedback(() -> Text.literal("Configuration reloaded with issues:"), false);
        // ... display error/warning report ...
    }
    return 1;
}
```

---

## 6. Access Wideners Required

Create a file `borislib.accesswidener` in `src/main/resources/` and reference it in `fabric.mod.json` (`"accessWidener": "borislib.accesswidener"`).

```
accessWidener v2 named

# --- Connection internals ---
# Needed to access the Netty channel for pending-bytes check and flush control.
accessible field net/minecraft/network/Connection channel Lio/netty/channel/Channel;
accessible method net/minecraft/network/Connection flushChannel ()V

# The 3-arg send method (write without flush):
# void send(Packet<?>, PacketSendListener, boolean flush)
accessible method net/minecraft/network/Connection send (Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V

# --- ChunkDeltaUpdateS2CPacket (ClientboundSectionBlocksUpdatePacket) ---
# Needed to read positions/states for block count in explosion optimization.
accessible field net/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket sectionPos Lnet/minecraft/core/SectionPos;
accessible field net/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket positions [S
accessible field net/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket states [Lnet/minecraft/world/level/block/state/BlockState;

# --- ServerCommonPacketListenerImpl ---
# Needed to access the connection object.
accessible field net/minecraft/server/network/ServerCommonPacketListenerImpl connection Lnet/minecraft/network/Connection;
```

> **Important:** Field/method names above use Mojang mappings. If using Yarn mappings, remap them accordingly. Use `yarn`/`intermediary` mapped names in the access widener file based on your mappings setup. The Fabric Loom plugin handles remapping if you use intermediary names.

---

## 7. Mixin Targets Summary

| Mixin Target Class | What To Do | Priority |
|---|---|---|
| `ServerCommonPacketListenerImpl` | `@Inject` into `send()` at HEAD to redirect packets to the buffer. `@Inject` into `<init>` to create buffer instance. | **Critical** |
| `ServerGamePacketListenerImpl` | `@Inject` into `tick()` at TAIL to flush buffer every tick. `@Inject` into disconnect handler to clean up buffer/stop interval task. | **Critical** |
| `Connection` | Possibly need a duck interface to expose `channel` field and `flushChannel()` if access widener isn't sufficient. Alternative: `@Accessor` mixin. | **Critical** |
| `ClientboundSectionBlocksUpdatePacket` | `@Accessor` mixin to expose `sectionPos`, `positions`, `states` if using accessor mixins instead of access wideners. | **Required for explosion opt** |

### Duck Interface Pattern (alternative to Access Widener for Connection):

```java
// Duck interface
public interface ConnectionAccess {
    Channel borislib$getChannel();
    void borislib$flushChannel();
    void borislib$sendNoFlush(Packet<?> packet, @Nullable PacketSendListener listener);
}

// Mixin
@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionAccess {
    @Shadow @Final private Channel channel;

    @Override
    public Channel borislib$getChannel() { return this.channel; }

    @Override
    public void borislib$flushChannel() {
        if (this.channel != null && this.channel.isOpen()) {
            this.channel.flush();
        }
    }

    @Override
    public void borislib$sendNoFlush(Packet<?> packet, @Nullable PacketSendListener listener) {
        // Call the internal send with flush=false
        ((Connection)(Object)this).send(packet, listener, false);
    }
}
```

---

## Appendix: Complete Packet Flow Diagram (BorisLib Version)

```
Server code calls listener.send(packet)
    │
    ▼
┌──────────────────────────────────────────────┐
│ ServerCommonPacketListenerImpl.send()          │
│                                               │
│ ★ ALL other mixins run here FIRST:            │
│   - Polymer packet transformers               │
│   - Virtual entity / block handlers           │
│   - Server-side translations                  │
│   - Any other mod's @Inject HEAD/etc.         │
│                                               │
│ Finally: connection.send(packet, cb, flush)    │
│          ↓ (wrapped by our @WrapOperation)     │
└──────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────┐
│ @WrapOperation → PacketBuffer                 │
│   .handleOutgoingPacket(packet, ..., original)│
│                                               │
│ Packet is FULLY TRANSFORMED at this point.    │
│ We only control the flush parameter.          │
│                                               │
│ 1. inside flush guard?         → send+flush   │
│ 2. master toggle off?          → original(flush)│
│ 3. bundle packet?              → flush, send  │
│ 4. full chunk packet?          → flush, send  │
│ 5. ignored packet class?       → original(flush)│
│ 6. off-thread or chat?         → original(flush)│
│ 7. not in GAME state?          → original(flush)│
│ 8. increment logicalCounter                   │
│ 9. block update + opt enabled? → blockQueue   │
│ 10. critical / instant?        → flush, send  │
│ 11. default                    → original(false)│
│     check byte limit → flush if exceeded      │
│     check count limit → flush if exceeded     │
└──────────────────────────────────────────────┘
    │
    ▼ (on flush trigger)
┌──────────────────────────────────────────────┐
│ PacketBuffer.flush(reason)                    │
│                                               │
│ 1. processBlockQueue() if non-empty           │
│    → group by chunk                           │
│    → if changes ≥ threshold:                  │
│        call listener.send(chunkPacket)        │
│        ↑ goes through FULL pipeline again     │
│          (Polymer transforms chunk data)      │
│          insideFlush guard → send+flush       │
│    → else: send individual packets            │
│ 2. get pending bytes for metrics              │
│ 3. channel.flush()             ← ONE syscall  │
│ 4. reset counters                             │
│ 5. increment physicalCounter                  │
└──────────────────────────────────────────────┘
    │
    ▼
  Kernel send() → Network → Client
```

