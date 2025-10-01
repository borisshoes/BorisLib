package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.mixins.PlayerInventoryAccessor;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class ItemReturnTimerCallback extends TickTimerCallback {
   
   private int prefSlot;
   
   public ItemReturnTimerCallback(ItemStack item, ServerPlayerEntity player, int delay){
      super(delay, item, player);
      this.prefSlot = -1;
   }
   
   public ItemReturnTimerCallback(ItemStack item, ServerPlayerEntity player, int delay, int prefSlot){
      super(delay, item, player);
      this.prefSlot = prefSlot;
   }
   
   @Override
   public void onTimer(){
      ServerPlayerEntity player1 = player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid());
      if(player1 == null){
         BorisLib.addLoginCallback(new ItemReturnLoginCallback(player,item,prefSlot));
      }else{
         if(!player1.isAlive()){
            BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item,player1,1,prefSlot));
         }else{
            if(prefSlot != -1){
               PlayerInventory inv = player1.getInventory();
               boolean canAdd = ((PlayerInventoryAccessor) inv).canAddMore(inv.getStack(prefSlot),item);
               if(canAdd || inv.getStack(prefSlot).isEmpty()){
                  player1.getInventory().insertStack(prefSlot, item);
                  if(item.getCount() <= 0) return;
               }
               prefSlot = -1;
            }
            player1.getInventory().insertStack(prefSlot, item);
            int newSize = item.getCount();
            if(newSize > 0){
               BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item,player1,1,-1));
            }
         }
      }
   }
}
