package net.borisshoes.borislib.timers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class TickTimerCallback {
   private int timer;
   protected ItemStack item;
   protected ServerPlayer player;
   
   public TickTimerCallback(int time, @Nullable ItemStack item, @Nullable ServerPlayer player){
      timer = time;
      this.item = item;
      this.player = player;
   }
   
   public abstract void onTimer();
   
   public int getTimer(){
      return timer;
   }
   
   public int decreaseTimer(){
      return this.timer--;
   }
   
   public void setTimer(int timer){
      this.timer = timer;
   }
   
   public ServerPlayer getPlayer(){
      return player;
   }
}

