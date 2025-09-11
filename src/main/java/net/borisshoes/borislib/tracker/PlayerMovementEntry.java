package net.borisshoes.borislib.tracker;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public record PlayerMovementEntry(ServerPlayerEntity player, Vec3d position, Vec3d velocity, long timestamp) {
   public static PlayerMovementEntry blankEntry(ServerPlayerEntity player){
      return new PlayerMovementEntry(player,player.getPos(), Vec3d.ZERO, System.nanoTime());
   }
}