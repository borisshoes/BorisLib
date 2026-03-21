package net.borisshoes.borislib.network;

import com.sun.management.OperatingSystemMXBean;
import net.borisshoes.borislib.BorisLib;

import java.lang.management.ManagementFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static net.borisshoes.borislib.BorisLib.LOGGER;

/**
 * Centralized metrics collection for the packet batching system.
 * All counters are atomic and updated in real-time from PacketBuffer.
 * Derived metrics are recomputed on a scheduled interval.
 */
public class Metrics {
   
   // Real-time atomic counters (updated by PacketBuffer)
   public static final AtomicLong logicalCounter = new AtomicLong(0);
   public static final AtomicLong physicalCounter = new AtomicLong(0);
   public static final AtomicLong optimizedChunks = new AtomicLong(0);
   public static final AtomicLong totalBytesSent = new AtomicLong(0);
   
   // Write queue counters — track event loop task submission savings
   public static final AtomicLong writeQueuedCounter = new AtomicLong(0);    // packets that entered the writeQueue
   public static final AtomicLong eventLoopTasksSubmitted = new AtomicLong(0); // actual event loop tasks submitted in flush
   
   // Derived metrics (computed periodically)
   public static volatile double ppsLogical = 0;
   public static volatile double ppsPhysical = 0;
   public static volatile double cpuUsage = 0;
   public static volatile double vanillaCpuEst = 0;
   public static final AtomicLong savedAllocationsBytes = new AtomicLong(0);
   public static final AtomicLong totalSavedSyscalls = new AtomicLong(0);
   public static volatile double networkSpeedKbs = 0;
   
   // Write queue derived metrics
   public static volatile double writeQueuedPps = 0;        // packets queued per second
   public static volatile double eventLoopTasksPps = 0;      // event loop tasks per second
   public static volatile double savedTasksPps = 0;          // avoided task submissions per second
   public static final AtomicLong totalSavedTasks = new AtomicLong(0); // cumulative saved submissions
   
   // Previous sample values for delta calculation
   private static long lastLogical = 0;
   private static long lastPhysical = 0;
   private static long lastBytes = 0;
   private static long lastWriteQueued = 0;
   private static long lastEventLoopTasks = 0;
   
   private static final ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "borislib-metrics");
      t.setDaemon(true);
      return t;
   });
   private static ScheduledFuture<?> currentTask;
   
   private static OperatingSystemMXBean osBean;
   
   static{
      try{
         osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
      }catch(Exception e){
         LOGGER.warn("Could not obtain OperatingSystemMXBean for CPU metrics");
         osBean = null;
      }
   }
   
   /**
    * Starts or restarts the metrics computation scheduler.
    */
   public static void start(){
      if(currentTask != null && !currentTask.isCancelled()){
         currentTask.cancel(false);
      }
      
      int interval = getUpdateInterval();
      
      currentTask = metricsScheduler.scheduleAtFixedRate(() -> {
         try{
            if(!isMetricsEnabled()) return;
            
            long currentLogical = logicalCounter.get();
            long currentPhysical = physicalCounter.get();
            long currentBytes = totalBytesSent.get();
            
            // PPS calculation
            ppsLogical = (double) (currentLogical - lastLogical) / interval;
            ppsPhysical = (double) (currentPhysical - lastPhysical) / interval;
            double bytesDiff = currentBytes - lastBytes;
            if(isModuleNetworkEnabled()){
               networkSpeedKbs = (bytesDiff / 1024.0) / interval;
            }
            
             lastLogical = currentLogical;
             lastPhysical = currentPhysical;
             lastBytes = currentBytes;
             
              // Write queue metrics
              if(isModuleWriteQueueEnabled()){
                 long currentWQ = writeQueuedCounter.get();
                 long currentEL = eventLoopTasksSubmitted.get();
                 writeQueuedPps = (double) (currentWQ - lastWriteQueued) / interval;
                 eventLoopTasksPps = (double) (currentEL - lastEventLoopTasks) / interval;
                 savedTasksPps = Math.max(0, writeQueuedPps - eventLoopTasksPps);
                 if(savedTasksPps > 0){
                    totalSavedTasks.addAndGet((long) (savedTasksPps * interval));
                 }
                 lastWriteQueued = currentWQ;
                 lastEventLoopTasks = currentEL;
              }
            
            // CPU usage
            if(osBean != null && isModuleCpuEnabled()){
               double processCpu = osBean.getProcessCpuLoad();
               if(processCpu >= 0){
                  cpuUsage = processCpu * 100.0 * Runtime.getRuntime().availableProcessors();
               }
            }
            
            // CPU savings estimation
            double savedPerSec = ppsLogical - ppsPhysical;
            if(savedPerSec > 0){
               totalSavedSyscalls.addAndGet((long) (savedPerSec * interval));
               double cpuRecovered = (savedPerSec * 8.5) / 10000.0;
               vanillaCpuEst = cpuUsage + cpuRecovered;
            }else{
               vanillaCpuEst = cpuUsage;
            }
            
            // Memory savings estimation (each saved packet ~ 512 bytes)
            if(isModuleMemoryEnabled()){
               savedAllocationsBytes.addAndGet((long) (savedPerSec * 512 * interval));
            }
            
            // Update boss bar
            MetricsBar.updateBar();
            
         }catch(Exception e){
            LOGGER.debug("Metrics computation error: {}", e.getMessage());
         }
      }, 1, interval, TimeUnit.SECONDS);
   }
   
   /**
    * Restarts the scheduler (called on config reload).
    */
   public static void reload(){
      start();
   }
   
   /**
    * Resets all counters to zero.
    */
   public static void reset(){
      logicalCounter.set(0);
      physicalCounter.set(0);
      optimizedChunks.set(0);
      totalBytesSent.set(0);
      writeQueuedCounter.set(0);
      eventLoopTasksSubmitted.set(0);
      ppsLogical = 0;
      ppsPhysical = 0;
      cpuUsage = 0;
      vanillaCpuEst = 0;
      savedAllocationsBytes.set(0);
      totalSavedSyscalls.set(0);
      networkSpeedKbs = 0;
      writeQueuedPps = 0;
      eventLoopTasksPps = 0;
      savedTasksPps = 0;
      totalSavedTasks.set(0);
      lastLogical = 0;
      lastPhysical = 0;
      lastBytes = 0;
      lastWriteQueued = 0;
      lastEventLoopTasks = 0;
   }
   
   // ─── Config helpers ─────────────────────────────────────────────────
   
   private static boolean isMetricsEnabled(){
      if(BorisLib.CONFIG == null) return true;
      return BorisLib.CONFIG.getBoolean(BorisLib.METRICS_ENABLED);
   }
   
   private static int getUpdateInterval(){
      if(BorisLib.CONFIG == null) return 1;
      int val = BorisLib.CONFIG.getInt(BorisLib.METRICS_UPDATE_INTERVAL);
      return Math.max(1, val);
   }
   
   private static boolean isModuleCpuEnabled(){
      if(BorisLib.CONFIG == null) return true;
      return BorisLib.CONFIG.getBoolean(BorisLib.METRICS_MODULE_CPU);
   }
   
   private static boolean isModuleNetworkEnabled(){
      if(BorisLib.CONFIG == null) return true;
      return BorisLib.CONFIG.getBoolean(BorisLib.METRICS_MODULE_NETWORK);
   }
   
   private static boolean isModuleMemoryEnabled(){
      if(BorisLib.CONFIG == null) return true;
      return BorisLib.CONFIG.getBoolean(BorisLib.METRICS_MODULE_MEMORY);
   }
   
   private static boolean isModuleWriteQueueEnabled(){
      if(BorisLib.CONFIG == null) return true;
      return BorisLib.CONFIG.getBoolean(BorisLib.METRICS_MODULE_WRITE_QUEUE);
   }
}

