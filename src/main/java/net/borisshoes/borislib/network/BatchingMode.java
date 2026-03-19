package net.borisshoes.borislib.network;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum BatchingMode implements StringRepresentable {
   SMART_EXECUTION("smart_execution"),
   STRICT_TICK("strict_tick"),
   INTERVAL("interval");
   
   private final String name;
   
   BatchingMode(String name){
      this.name = name;
   }
   
   @Override
   public @NotNull String getSerializedName(){
      return this.name;
   }
}

