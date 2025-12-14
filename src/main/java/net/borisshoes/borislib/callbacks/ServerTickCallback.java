package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.events.Event;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.borisshoes.borislib.BorisLib.SERVER_TIMER_CALLBACKS;
import static net.borisshoes.borislib.events.Event.RECENT_EVENTS;

public class ServerTickCallback {
   public static void onTick(MinecraftServer server){
      try{
         // Tick Timer Callbacks
         if(server.tickRateManager().runsNormally()) SERVER_TIMER_CALLBACKS.removeIf(tickTimers()::contains);
         
         // Tick events
         for(Event event : RECENT_EVENTS){
            event.tick();
         }
         RECENT_EVENTS.removeIf(Event::isExpired);
      }catch(Exception e){
         LOGGER.log(Level.ERROR,e);
      }
   }
   
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
