package net.borisshoes.borislib.timers;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * {@link TickTimerCallback} that runs a {@link Runnable} a fixed number of times, spaced by a tick
 * interval.
 *
 * <p>Implemented by re-scheduling itself when {@link #onTimer()} fires: each fire decrements the remaining
 * iteration count and, if more are due, queues a fresh {@code RepeatTimer} for the next interval.</p>
 *
 * <p>When a {@link ServerLevel} is provided the callback is queued against that world's tick driver (so it
 * pauses while the world is paused); otherwise it is queued against the server tick driver.</p>
 *
 * <p>Example: print a message every 20 ticks (one second) for 5 seconds:</p>
 * <pre>{@code
 *   BorisLib.addTickTimerCallback(new RepeatTimer(20, 5, () -> {...}, null));
 * }</pre>
 */
public class RepeatTimer extends TickTimerCallback {
   private final Runnable task;
   private final int ticks;
   private final int interval;
   private final ServerLevel world;
   
   
   /**
    * @param interval ticks between successive fires (also the delay until the first fire)
    * @param ticks    total number of times the task should run; values {@code <= 1} mean "run once"
    * @param task     the work to execute on every fire
    * @param world    optional world whose tick driver the callback should be scheduled on; if
    *                 {@code null}, the server tick driver is used instead
    */
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
            BorisLib.addTickTimerCallback(world, new RepeatTimer(interval, ticks - 1, task, world));
         }else{
            BorisLib.addTickTimerCallback(new RepeatTimer(interval, ticks - 1, task, null));
         }
      }
   }
}
