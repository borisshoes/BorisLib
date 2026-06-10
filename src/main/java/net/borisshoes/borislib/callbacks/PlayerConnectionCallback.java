package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.sequences.SequenceManager;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.ArrayList;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.PLAYER_MOVEMENT_TRACKER;

/**
 * Hooks bound to Fabric's player connection events that drive several BorisLib subsystems.
 *
 * <p>On join: re-loads the player's data stores via {@link DataAccess#onPlayerJoin(ServerPlayer)},
 * restores any dangling sequence state, ensures the movement tracker has an entry, and drains the
 * player's {@link LoginCallbackContainer}, firing every queued {@link LoginCallback} bound to this
 * player's UUID exactly once before removing it.</p>
 *
 * <p>On leave: notifies the {@link SequenceManager} so cleanup can happen, then calls
 * {@link DataAccess#onPlayerQuit(ServerPlayer)} to flush player state to disk.</p>
 */
public class PlayerConnectionCallback {
   
   /**
    * Called by Fabric's join event. Restores per-player state and runs all pending login callbacks.
    *
    * @param handler      the player's connection handler
    * @param packetSender packet sender supplied by Fabric (unused here, kept for the handler signature)
    * @param server       the server instance
    */
   public static void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender packetSender, MinecraftServer server){
      ServerPlayer player = handler.player;
      DataAccess.onPlayerJoin(player);
      
      // Restore any state from a dangling sequence snapshot (crash / missed cleanup).
      SequenceManager.onPlayerJoin(player);
      
      if(!PLAYER_MOVEMENT_TRACKER.containsKey(player)){
         PLAYER_MOVEMENT_TRACKER.put(player, PlayerMovementEntry.blankEntry(player));
      }
      
      UUID playerId = player.getUUID();
      LoginCallbackContainer container = DataAccess.getPlayer(playerId, BorisLib.LOGIN_CALLBACKS_KEY);
      
      ArrayList<LoginCallback> toBeRemoved = new ArrayList<>();
      for(LoginCallback callback : container.getCallbacks()){
         if(callback.getPlayer().equals(player.getStringUUID())){
            callback.onLogin(handler, server);
            toBeRemoved.add(callback);
         }
      }
      for(LoginCallback callback : toBeRemoved){
         container.removeCallback(callback);
         DataAccess.setPlayer(playerId, BorisLib.LOGIN_CALLBACKS_KEY, container);
      }
   }
   
   /**
    * Called by Fabric's disconnect event. Flushes per-player BorisLib state to disk and lets running
    * sequences clean up.
    *
    * @param handler the disconnecting player's connection handler
    * @param server  the server instance
    */
   public static void onPlayerLeave(ServerGamePacketListenerImpl handler, MinecraftServer server){
      ServerPlayer player = handler.player;
      SequenceManager.onPlayerDisconnect(player);
      DataAccess.onPlayerQuit(player);
   }
}
