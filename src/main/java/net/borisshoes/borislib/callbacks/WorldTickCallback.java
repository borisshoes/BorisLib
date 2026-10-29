package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.level.ServerLevel;
import com.mojang.datafixers.util.Pair;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
      if(!world.getServer().tickRateManager().runsNormally()) return;
      // Same live-list pass as ServerTickCallback: timers scheduled while firing are ticked in this pass too
      Set<Pair<ServerLevel, TickTimerCallback>> fired = Collections.newSetFromMap(new IdentityHashMap<>());
      for(int i = 0; i < WORLD_TIMER_CALLBACKS.size(); i++){
         Pair<ServerLevel, TickTimerCallback> pair = WORLD_TIMER_CALLBACKS.get(i);
         if(pair.getFirst().dimension() != world.dimension()) continue;
         if(pair.getSecond().decreaseTimer() > 0) continue;
         fired.add(pair);
         ServerTickCallback.runTimer(pair.getSecond());
      }
      if(!fired.isEmpty()) WORLD_TIMER_CALLBACKS.removeIf(fired::contains);
   }
}
