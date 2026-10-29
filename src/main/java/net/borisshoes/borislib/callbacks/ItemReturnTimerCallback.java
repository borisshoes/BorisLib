package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@link TickTimerCallback} companion to {@link ItemReturnLoginCallback} that retries handing an
 * {@link ItemStack} to an online player one or more ticks after the initial attempt fails.
 *
 * <p>On fire, the stack is moved into the player's {@link PendingItemReturns} queue, which hands it over
 * (preferred slot first) as soon as the player is online, alive and has room, and keeps it in their data file
 * until then. Only the delay before firing is held in memory; use {@link PendingItemReturns#queue} directly when
 * the item must survive a restart during that delay too.</p>
 */
public class ItemReturnTimerCallback extends TickTimerCallback {
   
   private int prefSlot;
   
   /**
    * @param item   the stack to return (may be modified in place as items are consumed)
    * @param player the target player
    * @param delay  delay in ticks before the first attempt
    */
   public ItemReturnTimerCallback(ItemStack item, ServerPlayer player, int delay){
      super(delay, item, player);
      this.prefSlot = -1;
   }
   
   /**
    * @param item     the stack to return
    * @param player   the target player
    * @param delay    delay in ticks before the first attempt
    * @param prefSlot inventory slot index to try first; {@code -1} disables the preference
    */
   public ItemReturnTimerCallback(ItemStack item, ServerPlayer player, int delay, int prefSlot){
      super(delay, item, player);
      this.prefSlot = prefSlot;
   }
   
   @Override
   public void onTimer(){
      // The queue lives in the player's data file, so the item is safe from here on whether or not the player
      // is online, alive, or has room
      PendingItemReturns.queue(player.getUUID(), item, prefSlot);
      item.setCount(0);
   }
}
