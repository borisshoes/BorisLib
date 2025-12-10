package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

public class PlayerConnectionCallback {
   
   public static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender packetSender, MinecraftServer server){
      ServerPlayerEntity player = handler.player;
      DataAccess.onPlayerJoin(player);
      
      if(!PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
      
      UUID playerId = player.getUuid();
      LoginCallbackContainer container = DataAccess.getPlayer(playerId, BorisLib.LOGIN_CALLBACKS_KEY);
      
      ArrayList<LoginCallback> toBeRemoved = new ArrayList<>();
      for(LoginCallback callback : container.getCallbacks()){
         if(callback.getPlayer().equals(player.getUuidAsString())){
            callback.onLogin(handler,server);
            toBeRemoved.add(callback);
         }
      }
      for(LoginCallback callback :toBeRemoved){
         container.removeCallback(callback);
         DataAccess.setPlayer(playerId,BorisLib.LOGIN_CALLBACKS_KEY,container);
      }
   }
   
   public static void onPlayerLeave(ServerPlayNetworkHandler handler, MinecraftServer server){
      DataAccess.onPlayerQuit(handler.player);
   }
}
