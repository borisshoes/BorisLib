package net.borisshoes.borislib.timers;

/**
 * Simple {@link TickTimerCallback} that runs an arbitrary {@link Runnable} once after a fixed delay.
 *
 * <p>Convenience wrapper for cases where you just want to defer a piece of work by N ticks and don't need
 * any of the {@code item}/{@code player} context the base class supports.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 *   BorisLib.addTickTimerCallback(new GenericTimer(40, () -> player.sendSystemMessage(...)));
 * }</pre>
 */
public class GenericTimer extends TickTimerCallback {
   private final Runnable task;
   
   /**
    * @param time number of ticks to wait before running the task
    * @param task the work to execute when the timer fires
    */
   public GenericTimer(int time, Runnable task){
      super(time, null, null);
      this.task = task;
   }
   
   @Override
   public void onTimer(){
      task.run();
   }
}
