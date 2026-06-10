package net.borisshoes.borislib.timers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for tick-driven scheduled callbacks managed by BorisLib.
 *
 * <p>A {@code TickTimerCallback} represents one piece of work that should fire after a given number of
 * server (or world) ticks have elapsed. Instances are registered with
 * {@link net.borisshoes.borislib.BorisLib#addTickTimerCallback(TickTimerCallback)} (server timer) or
 * {@link net.borisshoes.borislib.BorisLib#addTickTimerCallback(net.minecraft.server.level.ServerLevel, TickTimerCallback)}
 * (world timer); the library calls {@link #decreaseTimer()} each tick and fires {@link #onTimer()} when
 * the counter reaches zero.</p>
 *
 * <p>The optional {@link #item} and {@link #player} fields exist as a convenience for the common pattern
 * of "do something to this stack or player after N ticks" (e.g. {@link net.borisshoes.borislib.callbacks.ItemReturnTimerCallback}).</p>
 */
public abstract class TickTimerCallback {
   private int timer;
   /** Optional item stack carried along with the callback for the subclass's use. May be {@code null}. */
   protected ItemStack item;
   /** Optional player associated with the callback. May be {@code null}. */
   protected ServerPlayer player;
   
   /**
    * @param time   number of ticks before {@link #onTimer()} fires
    * @param item   optional item stack to associate with the callback
    * @param player optional player to associate with the callback
    */
   public TickTimerCallback(int time, @Nullable ItemStack item, @Nullable ServerPlayer player){
      timer = time;
      this.item = item;
      this.player = player;
   }
   
   /**
    * Implement to perform the timer's action. Called exactly once by the timer driver when the countdown
    * reaches zero.
    */
   public abstract void onTimer();
   
   /** @return the remaining ticks before the callback fires. */
   public int getTimer(){
      return timer;
   }
   
   /**
    * Post-decrement of the internal timer. Called by the timer driver each tick.
    *
    * @return the value of the timer before it was decremented
    */
   public int decreaseTimer(){
      return this.timer--;
   }
   
   /**
    * Replaces the remaining-ticks counter. Useful for "resetting" a timer in flight.
    *
    * @param timer the new tick count
    */
   public void setTimer(int timer){
      this.timer = timer;
   }
   
   /** @return the player associated with this callback, or {@code null} if none. */
   public ServerPlayer getPlayer(){
      return player;
   }
}
