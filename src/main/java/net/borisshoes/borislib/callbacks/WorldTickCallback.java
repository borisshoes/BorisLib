package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.borisshoes.borislib.BorisLib.WORLD_TIMER_CALLBACKS;

public class WorldTickCallback {
   public static void onWorldTick(ServerLevel world){
      try{
         // Tick Timer Callbacks
         if(world.getServer().tickRateManager().runsNormally()) WORLD_TIMER_CALLBACKS.removeIf(tickTimers(world)::contains);
         
      }catch(Exception e){
         LOGGER.log(Level.ERROR,e);
      }
   }
   
   @NotNull
   private static ArrayList<Tuple<ServerLevel, TickTimerCallback>> tickTimers(ServerLevel serverWorld){
      ArrayList<Tuple<ServerLevel,TickTimerCallback>> toRemove = new ArrayList<>();
      for(int i = 0; i < WORLD_TIMER_CALLBACKS.size(); i++){
         Tuple<ServerLevel,TickTimerCallback> pair = WORLD_TIMER_CALLBACKS.get(i);
         TickTimerCallback t = pair.getB();
         if(pair.getA().dimension() == serverWorld.dimension()){
            if(t.decreaseTimer() == 0){
               t.onTimer();
               toRemove.add(pair);
            }
         }
      }
      return toRemove;
   }
}
