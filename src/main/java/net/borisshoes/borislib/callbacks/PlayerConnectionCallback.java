package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;
import static net.borisshoes.borislib.cca.WorldDataComponentInitializer.LOGIN_CALLBACK_LIST;

public class PlayerConnectionCallback {
   
   public static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender packetSender, MinecraftServer server){
      ServerPlayerEntity player = handler.player;
      if(!PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
      
      
      ArrayList<LoginCallback> toBeRemoved = new ArrayList<>();
      for(LoginCallback callback : LOGIN_CALLBACK_LIST.get(server.getOverworld()).getCallbacks()){
         if(callback.getPlayer().equals(player.getUuidAsString())){
            callback.onLogin(handler,server);
            toBeRemoved.add(callback);
         }
      }
      for(LoginCallback callback :toBeRemoved){
         LOGIN_CALLBACK_LIST.get(server.getOverworld()).removeCallback(callback);
      }
   }
}
