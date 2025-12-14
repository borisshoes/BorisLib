package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.mixins.InventoryAccessor;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ItemReturnTimerCallback extends TickTimerCallback {
   
   private int prefSlot;
   
   public ItemReturnTimerCallback(ItemStack item, ServerPlayer player, int delay){
      super(delay, item, player);
      this.prefSlot = -1;
   }
   
   public ItemReturnTimerCallback(ItemStack item, ServerPlayer player, int delay, int prefSlot){
      super(delay, item, player);
      this.prefSlot = prefSlot;
   }
   
   @Override
   public void onTimer(){
      ServerPlayer player1 = player.level().getServer().getPlayerList().getPlayer(player.getUUID());
      if(player1 == null){
         BorisLib.addLoginCallback(new ItemReturnLoginCallback(player,item,prefSlot));
      }else{
         if(!player1.isAlive()){
            BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item,player1,1,prefSlot));
         }else{
            if(prefSlot != -1){
               Inventory inv = player1.getInventory();
               boolean canAdd = ((InventoryAccessor) inv).canAddMore(inv.getItem(prefSlot),item);
               if(canAdd || inv.getItem(prefSlot).isEmpty()){
                  player1.getInventory().add(prefSlot, item);
                  if(item.getCount() <= 0) return;
               }
               prefSlot = -1;
            }
            player1.getInventory().add(prefSlot, item);
            int newSize = item.getCount();
            if(newSize > 0){
               BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item,player1,1,-1));
            }
         }
      }
   }
}
