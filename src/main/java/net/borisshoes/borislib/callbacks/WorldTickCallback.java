package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.level.ServerLevel;
import com.mojang.datafixers.util.Pair;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.borisshoes.borislib.BorisLib.WORLD_TIMER_CALLBACKS;

/**
 * Per-world tick hook that drives world-scoped
 * {@link net.borisshoes.borislib.timers.TickTimerCallback}s.
 *
 * <p>Each world's timer list is independent so that callbacks scheduled in a specific dimension pause
 * with that dimension's tick rate. Wired up by BorisLib during init and called by the Fabric
 * world-tick event for every loaded {@link ServerLevel}.</p>
 */
public class WorldTickCallback {
   /**
    * Per-world-tick entry point. Decrements every queued world-tick timer that targets {@code world},
    * fires the ones that reach zero, and drops them.
    *
    * @param world the world being ticked
    */
   public static void onWorldTick(ServerLevel world){
      try{
         // Tick Timer Callbacks
         if(world.getServer().tickRateManager().runsNormally())
            WORLD_TIMER_CALLBACKS.removeIf(tickTimers(world)::contains);
         
      }catch(Exception e){
         LOGGER.log(Level.ERROR, e);
      }
   }
   
   /** Decrements queued world timers for the given dimension and returns those that fired this tick (for removal). */
   @NotNull
   private static ArrayList<Pair<ServerLevel, TickTimerCallback>> tickTimers(ServerLevel serverWorld){
      ArrayList<Pair<ServerLevel, TickTimerCallback>> toRemove = new ArrayList<>();
      for(int i = 0; i < WORLD_TIMER_CALLBACKS.size(); i++){
         Pair<ServerLevel, TickTimerCallback> pair = WORLD_TIMER_CALLBACKS.get(i);
         TickTimerCallback t = pair.getSecond();
         if(pair.getFirst().dimension() == serverWorld.dimension()){
            if(t.decreaseTimer() == 0){
               t.onTimer();
               toRemove.add(pair);
            }
         }
      }
      return toRemove;
   }
}
