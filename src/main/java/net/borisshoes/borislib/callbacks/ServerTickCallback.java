package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.conditions.ConditionBossBars;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.events.Event;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.borisshoes.borislib.BorisLib.SERVER_TIMER_CALLBACKS;
import static net.borisshoes.borislib.events.Event.RECENT_EVENTS;

/**
 * Server-wide tick hook that drives the global pieces of BorisLib: server-level
 * {@link net.borisshoes.borislib.timers.TickTimerCallback}s, the {@link Event} queue, and flushing
 * condition changes to storage.
 *
 * <p>Wired up by BorisLib during init and called by the Fabric server-tick event.</p>
 */
public class ServerTickCallback {
   /**
    * Per-server-tick entry point.
    *
    * <p>Order of work:</p>
    * <ol>
    *    <li>Tick every server-scoped {@link net.borisshoes.borislib.timers.TickTimerCallback}, firing and
    *        removing the ones that reach zero (skipped if tickrate is paused).</li>
    *    <li>Tick every {@link Event} in {@link Event#RECENT_EVENTS}.</li>
    *    <li>Mark condition storage dirty if any condition changed this tick, via
    *        {@link Conditions#flushDirty(MinecraftServer)}. Conditions themselves tick with their entity.</li>
    *    <li>Refresh the condition boss bars of the players who switched them on.</li>
    *    <li>Retry delivering {@link PendingItemReturns} to online players.</li>
    *    <li>Drop expired events.</li>
    * </ol>
    *
    * @param server the server instance being ticked
    */
   public static void onTick(MinecraftServer server){
      // Each step is isolated so one misbehaving timer, event or subsystem cannot stall the others
      if(server.tickRateManager().runsNormally()){
         tickTimers();
      }
      
      // Events added while ticking start next tick, as they would have with the original iteration
      int eventCount = RECENT_EVENTS.size();
      for(int i = 0; i < eventCount && i < RECENT_EVENTS.size(); i++){
         Event event = RECENT_EVENTS.get(i);
         try{
            event.tick();
         }catch(Exception e){
            LOGGER.error("Event {} threw while ticking and was removed", event.id, e);
            event.markForRemoval();
         }
      }
      
      try{
         // Persist condition changes made this tick
         Conditions.flushDirty(server);
      }catch(Exception e){
         LOGGER.error("Failed to flush condition changes", e);
      }
      
      try{
         ConditionBossBars.tick(server);
      }catch(Exception e){
         LOGGER.error("Failed to update condition boss bars", e);
      }
      
      try{
         PendingItemReturns.tick(server);
      }catch(Exception e){
         LOGGER.error("Failed to deliver pending returned items", e);
      }
      
      RECENT_EVENTS.removeIf(Event::isExpired);
   }
   
   /**
    * Decrements every queued server-tick timer, firing the ones that are due and then removing them. The loop runs
    * over the live list, so a timer scheduled by a firing timer is ticked in the same pass (a 0-delay one fires
    * this tick). Each timer is isolated, so one exception cannot leak the others or keep them from firing.
    */
   private static void tickTimers(){
      Set<TickTimerCallback> fired = Collections.newSetFromMap(new IdentityHashMap<>());
      for(int i = 0; i < SERVER_TIMER_CALLBACKS.size(); i++){
         TickTimerCallback callback = SERVER_TIMER_CALLBACKS.get(i);
         if(callback.decreaseTimer() > 0) continue;
         fired.add(callback);
         runTimer(callback);
      }
      if(!fired.isEmpty()) SERVER_TIMER_CALLBACKS.removeIf(fired::contains);
   }

   /** Fires one timer, logging instead of propagating anything it throws. */
   static void runTimer(TickTimerCallback callback){
      try{
         callback.onTimer();
      }catch(Exception e){
         LOGGER.error("Tick timer {} threw while firing", callback.getClass().getName(), e);
      }
   }
}
