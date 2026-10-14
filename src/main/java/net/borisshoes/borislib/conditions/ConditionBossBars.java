package net.borisshoes.borislib.conditions;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DefaultPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Optional per-player boss bars that count down the player's own timed condition instances.
 *
 * <p>Only instances with a {@link ConditionInstance#getName() name} get a bar, and only if they are not
 * {@link ConditionInstance#isPersistent() persistent} and have a finite duration. Instances that share a
 * name, even across different condition types, are merged into one bar that tracks whichever of them has
 * the most time left. The bar's title is the shared name followed by that time left, with the icon of every
 * merged condition in front when the player has the server resource pack.</p>
 *
 * <p>The bars are off by default. Each player's choice is stored in {@link DefaultPlayerData}.</p>
 */
public final class ConditionBossBars {
   /** Bars are refreshed this often; the client interpolates progress in between. */
   private static final int UPDATE_INTERVAL = 5;

   private static final Map<UUID, Map<Component, ServerBossEvent>> BARS = new HashMap<>();

   private ConditionBossBars(){
   }

   /**
    * @param player the player to check
    * @return {@code true} if the player has condition boss bars switched on
    */
   public static boolean isEnabled(ServerPlayer player){
      return BARS.containsKey(player.getUUID());
   }

   /**
    * Switches a player's condition boss bars on or off and stores the choice.
    *
    * @param player  the player to update
    * @param enabled whether the bars should be shown
    */
   public static void setEnabled(ServerPlayer player, boolean enabled){
      DefaultPlayerData playerData = DataAccess.getPlayer(player.getUUID(), BorisLib.PLAYER_DATA_KEY);
      if(playerData.hasConditionBars() != enabled){
         playerData.setConditionBars(enabled);
         DataAccess.markPlayerDirty(player.getUUID());
      }
      if(enabled){
         update(player, BARS.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>()));
      }else{
         clear(BARS.remove(player.getUUID()));
      }
   }

   /**
    * Join hook: starts showing bars to a player who had them switched on.
    *
    * @param player the player who joined
    */
   public static void onPlayerJoin(ServerPlayer player){
      if(DataAccess.getPlayer(player.getUUID(), BorisLib.PLAYER_DATA_KEY).hasConditionBars()){
         BARS.put(player.getUUID(), new LinkedHashMap<>());
      }
   }

   /**
    * Leave hook: drops the player's bars.
    *
    * @param player the player who left
    */
   public static void onPlayerLeave(ServerPlayer player){
      clear(BARS.remove(player.getUUID()));
   }

   /** Server-stop hook: drops every bar. */
   public static void onServerStopped(){
      BARS.values().forEach(ConditionBossBars::clear);
      BARS.clear();
   }

   /**
    * Server-tick hook: refreshes the bars of every player who has them switched on.
    *
    * @param server the server instance being ticked
    */
   public static void tick(MinecraftServer server){
      if(BARS.isEmpty() || server.getTickCount() % UPDATE_INTERVAL != 0) return;
      for(var entry : BARS.entrySet()){
         ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
         if(player != null) update(player, entry.getValue());
      }
   }

   private static void update(ServerPlayer player, Map<Component, ServerBossEvent> bars){
      EntityConditions record = ((ConditionHolder) player).borislib$getConditions();
      Map<Component, Group> groups = new LinkedHashMap<>();
      if(record != null){
         for(var entry : record.types().reference2ObjectEntrySet()){
            Condition condition = entry.getKey().value();
            for(ConditionInstance instance : entry.getValue().instances()){
               if(instance.getName() == null || instance.isPersistent() || instance.getDuration() == ConditionInstance.INFINITE_DURATION) continue;
               groups.computeIfAbsent(instance.getName(), k -> new Group()).add(instance, condition);
            }
         }
      }

      bars.entrySet().removeIf(entry -> {
         if(groups.containsKey(entry.getKey())) return false;
         entry.getValue().removeAllPlayers();
         return true;
      });

      boolean icons = ConditionDisplay.showsIcons(player);
      for(var entry : groups.entrySet()){
         Group group = entry.getValue();
         Component name = ConditionDisplay.withIcons(group.conditions, entry.getKey(), icons);
         Component title = Component.translatable("text.borislib.conditions.bar_title", name, ConditionDisplay.timeLeft(group.remaining));
         ServerBossEvent bar = bars.get(entry.getKey());
         if(bar == null){
            bar = new ServerBossEvent(UUID.randomUUID(), title, group.color(), BossEvent.BossBarOverlay.PROGRESS);
            bars.put(entry.getKey(), bar);
         }
         bar.setName(title);
         bar.setColor(group.color());
         bar.setProgress(group.progress());
         // A respawn replaces the player object, which the bar would otherwise keep sending to
         if(!bar.getPlayers().contains(player)){
            bar.removeAllPlayers();
            bar.addPlayer(player);
         }
      }
   }

   private static void clear(Map<Component, ServerBossEvent> bars){
      if(bars == null) return;
      bars.values().forEach(ServerBossEvent::removeAllPlayers);
      bars.clear();
   }

   /** The instances that share one name. */
   private static final class Group {
      private int remaining = -1;
      private int duration = 1;
      private boolean beneficial = true;
      private boolean harmful = true;
      /** Every condition the instances belong to, in id order so the title stays stable. */
      private final Set<Condition> conditions = new TreeSet<>(Comparator.comparing(Condition::getId));

      void add(ConditionInstance instance, Condition condition){
         if(instance.getRemainingTicks() > remaining){
            remaining = instance.getRemainingTicks();
            duration = instance.getDuration();
         }
         beneficial &= condition.getCategory() == MobEffectCategory.BENEFICIAL;
         harmful &= condition.getCategory() == MobEffectCategory.HARMFUL;
         conditions.add(condition);
      }

      float progress(){
         return Math.clamp((float) remaining / duration, 0.0f, 1.0f);
      }

      BossEvent.BossBarColor color(){
         if(beneficial) return BossEvent.BossBarColor.GREEN;
         return harmful ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE;
      }
   }
}
