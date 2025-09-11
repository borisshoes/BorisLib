package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.borisshoes.borislib.BorisLib.WORLD_TIMER_CALLBACKS;

public class WorldTickCallback {
   public static void onWorldTick(ServerWorld world){
      try{
         // Tick Timer Callbacks
         if(world.getServer().getTickManager().shouldTick()) WORLD_TIMER_CALLBACKS.removeIf(tickTimers(world)::contains);
         
      }catch(Exception e){
         LOGGER.log(Level.ERROR,e);
      }
   }
   
   @NotNull
   private static ArrayList<Pair<ServerWorld, TickTimerCallback>> tickTimers(ServerWorld serverWorld){
      ArrayList<Pair<ServerWorld,TickTimerCallback>> toRemove = new ArrayList<>();
      for(int i = 0; i < WORLD_TIMER_CALLBACKS.size(); i++){
         Pair<ServerWorld,TickTimerCallback> pair = WORLD_TIMER_CALLBACKS.get(i);
         TickTimerCallback t = pair.getRight();
         if(pair.getLeft().getRegistryKey() == serverWorld.getRegistryKey()){
            if(t.decreaseTimer() == 0){
               t.onTimer();
               toRemove.add(pair);
            }
         }
      }
      return toRemove;
   }
}
