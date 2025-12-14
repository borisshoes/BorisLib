package net.borisshoes.borislib.timers;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class RepeatTimer extends TickTimerCallback {
   private final Runnable task;
   private final int ticks;
   private final int interval;
   private final ServerLevel world;
   
   
   public RepeatTimer(int interval, int ticks, Runnable task, @Nullable ServerLevel world){
      super(interval, null, null);
      this.ticks = ticks;
      this.world = world;
      this.interval = interval;
      this.task = task;
   }
   
   @Override
   public void onTimer(){
      task.run();
      
      if(ticks > 1){
         if(world != null){
            BorisLib.addTickTimerCallback(world, new RepeatTimer(interval,ticks-1, task, world));
         }else{
            BorisLib.addTickTimerCallback(new RepeatTimer(interval, ticks-1, task, null));
         }
      }
   }
}
