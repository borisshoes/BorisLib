package net.borisshoes.borislib.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.borisshoes.borislib.BorisLib.LOGGER;

/**
 * BossBar-based metrics display for the packet batching system.
 * Players toggle it on/off with /borislib bar.
 */
public class MetricsBar {
   
   private static final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
   private static final ServerBossEvent bossBar;
   
   static{
      bossBar = new ServerBossEvent(
            Component.translatable("text.borislib.metrics.bar_title"),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.PROGRESS
      );
      bossBar.setVisible(false);
   }
   
   /**
    * Toggles the boss bar for a player. Returns true if now showing, false if hidden.
    */
   public static boolean toggle(ServerPlayer player){
      UUID uuid = player.getUUID();
      if(viewers.contains(uuid)){
         viewers.remove(uuid);
         bossBar.removePlayer(player);
         return false;
      }else{
         viewers.add(uuid);
         bossBar.addPlayer(player);
         bossBar.setVisible(true);
         return true;
      }
   }
   
   /**
    * Removes a player (e.g., on disconnect).
    */
   public static void removePlayer(ServerPlayer player){
      viewers.remove(player.getUUID());
      bossBar.removePlayer(player);
      if(viewers.isEmpty()){
         bossBar.setVisible(false);
      }
   }
   
   /**
    * Adds a player back (e.g., on rejoin) if they were a viewer.
    */
   public static void addPlayerIfViewer(ServerPlayer player){
      if(viewers.contains(player.getUUID())){
         bossBar.addPlayer(player);
         bossBar.setVisible(true);
      }
   }
   
   /**
    * Updates the boss bar content with current metrics.
    * Called from the Metrics scheduler thread.
    */
   public static void updateBar(){
      if(viewers.isEmpty()) return;
      
      try{
         double logical = Math.max(1, Metrics.ppsLogical);
         double efficiency = (logical - Metrics.ppsPhysical) / logical;
         if(efficiency < 0) efficiency = 0;
         if(efficiency > 1) efficiency = 1;
         
         BossEvent.BossBarColor color;
         ChatFormatting effColor;
         if(efficiency > 0.75){
            color = BossEvent.BossBarColor.GREEN;
            effColor = ChatFormatting.GREEN;
         }else if(efficiency > 0.40){
            color = BossEvent.BossBarColor.YELLOW;
            effColor = ChatFormatting.YELLOW;
         }else{
            color = BossEvent.BossBarColor.RED;
            effColor = ChatFormatting.RED;
         }
         
         double speed = Metrics.networkSpeedKbs;
         String speedStr;
         if(speed > 1024){
            speedStr = String.format("%.2f MB/s", speed / 1024.0);
         }else{
            speedStr = String.format("%.1f KB/s", speed);
         }
         
         Component title = Component.empty()
               .append(Component.translatable("text.borislib.metrics.label").withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE))
               .append(Component.translatable("text.borislib.metrics.separator").withStyle(ChatFormatting.DARK_GRAY))
               .append(Component.translatable("text.borislib.metrics.efficiency", Component.literal((int) (efficiency * 100) + "%").withStyle(effColor)).withStyle(ChatFormatting.WHITE))
               .append(Component.translatable("text.borislib.metrics.separator").withStyle(ChatFormatting.DARK_GRAY))
               .append(Component.translatable("text.borislib.metrics.vanilla_pps", Component.literal((int) Metrics.ppsLogical + " p/s").withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.WHITE))
               .append(Component.translatable("text.borislib.metrics.separator").withStyle(ChatFormatting.DARK_GRAY))
               .append(Component.translatable("text.borislib.metrics.actual_pps", Component.literal((int) Metrics.ppsPhysical + " p/s").withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.WHITE))
               .append(Component.literal(" (" + speedStr + ")").withStyle(ChatFormatting.GRAY));
         
         bossBar.setName(title);
         bossBar.setProgress((float) efficiency);
         bossBar.setColor(color);
      }catch(Exception e){
         LOGGER.debug("MetricsBar update error: {}", e.getMessage());
      }
   }
   
   /**
    * Restarts the bar (called on config reload).
    */
   public static void reload(){
      // Boss bar persists, metrics scheduler handles updates
   }
}
