package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.conditions.ConditionBossBars;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.events.Event;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

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
    *    <li>Drop expired events.</li>
    * </ol>
    *
    * @param server the server instance being ticked
    */
   public static void onTick(MinecraftServer server){
      try{
         // Tick Timer Callbacks
         if(server.tickRateManager().runsNormally()) SERVER_TIMER_CALLBACKS.removeIf(tickTimers()::contains);
         
         // Tick events
         for(Event event : RECENT_EVENTS){
            event.tick();
         }
         
         // Persist condition changes made this tick
         Conditions.flushDirty(server);
         ConditionBossBars.tick(server);
         
         RECENT_EVENTS.removeIf(Event::isExpired);
      }catch(Exception e){
         LOGGER.log(Level.ERROR, e);
      }
   }
   
   /** Decrements every queued server-tick timer and returns those that fired this tick (for removal). */
   @NotNull
   private static ArrayList<TickTimerCallback> tickTimers(){
      ArrayList<TickTimerCallback> toRemove = new ArrayList<>();
      for(int i = 0; i < SERVER_TIMER_CALLBACKS.size(); i++){
         TickTimerCallback callback = SERVER_TIMER_CALLBACKS.get(i);
         if(callback.decreaseTimer() == 0){
            callback.onTimer();
            toRemove.add(callback);
         }
      }
      return toRemove;
   }
}
