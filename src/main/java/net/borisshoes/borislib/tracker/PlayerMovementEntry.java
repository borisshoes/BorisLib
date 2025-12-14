package net.borisshoes.borislib.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record PlayerMovementEntry(ServerPlayer player, Vec3 position, Vec3 velocity, long timestamp) {
   public static PlayerMovementEntry blankEntry(ServerPlayer player){
      return new PlayerMovementEntry(player,player.position(), Vec3.ZERO, System.nanoTime());
   }
}