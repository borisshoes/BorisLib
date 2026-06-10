package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.mixins.InventoryAccessor;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * {@link TickTimerCallback} companion to {@link ItemReturnLoginCallback} that retries handing an
 * {@link ItemStack} to an online player one or more ticks after the initial attempt fails.
 *
 * <p>Behavior on fire:</p>
 * <ul>
 *    <li>If the player is no longer online, schedules an {@link ItemReturnLoginCallback} and stops.</li>
 *    <li>If the player is dead, reschedules itself for the next tick.</li>
 *    <li>Otherwise tries the preferred slot first (when provided and able to accept the stack), then falls
 *        back to a normal {@code add()}. Any leftover quantity triggers another retry next tick.</li>
 * </ul>
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
      ServerPlayer player1 = player.level().getServer().getPlayerList().getPlayer(player.getUUID());
      if(player1 == null){
         BorisLib.addLoginCallback(new ItemReturnLoginCallback(player, item, prefSlot));
      }else{
         if(!player1.isAlive()){
            BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item, player1, 1, prefSlot));
         }else{
            if(prefSlot != -1){
               Inventory inv = player1.getInventory();
               boolean canAdd = ((InventoryAccessor) inv).canAddMore(inv.getItem(prefSlot), item);
               if(canAdd || inv.getItem(prefSlot).isEmpty()){
                  player1.getInventory().add(prefSlot, item);
                  if(item.getCount() <= 0) return;
               }
               prefSlot = -1;
            }
            player1.getInventory().add(prefSlot, item);
            int newSize = item.getCount();
            if(newSize > 0){
               BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item, player1, 1, -1));
            }
         }
      }
   }
}
