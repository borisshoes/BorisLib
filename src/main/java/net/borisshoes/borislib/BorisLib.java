package net.borisshoes.borislib;

import com.mojang.serialization.Lifecycle;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.callbacks.*;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.testmod.Commands;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.borisshoes.borislib.utils.ItemModDataHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;

import static net.borisshoes.borislib.cca.WorldDataComponentInitializer.LOGIN_CALLBACK_LIST;

public class BorisLib implements ModInitializer, ClientModInitializer {
   
   public static final Logger LOGGER = LogManager.getLogger("BorisLib");
   public static final ArrayList<TickTimerCallback> SERVER_TIMER_CALLBACKS = new ArrayList<>();
   public static final ArrayList<Pair<ServerWorld,TickTimerCallback>> WORLD_TIMER_CALLBACKS = new ArrayList<>();
   public static final String MOD_ID = "borislib";
   public static final String BLANK_UUID = "00000000-0000-4000-8000-000000000000";
   public static final HashMap<ServerPlayerEntity, PlayerMovementEntry> PLAYER_MOVEMENT_TRACKER = new HashMap<>();
   
   public static MinecraftServer SERVER = null;
   
   public static final Registry<GraphicalItem.GraphicElement> GRAPHIC_ITEM_REGISTRY = new SimpleRegistry<>(RegistryKey.ofRegistry(Identifier.of(MOD_ID,"graphic_elements")), Lifecycle.stable());
   public static final Registry<Item> ITEMS = new SimpleRegistry<>(RegistryKey.ofRegistry(Identifier.of(MOD_ID,"item")), Lifecycle.stable());
   public static final Registry<LoginCallback> LOGIN_CALLBACKS = new SimpleRegistry<>(RegistryKey.ofRegistry(Identifier.of(MOD_ID,"login_callback")), Lifecycle.stable());
   
   public static final Item GRAPHICAL_ITEM = registerItem("graphical_item", new GraphicalItem(new Item.Settings().maxCount(99)));
   
   public static final LoginCallback ITEM_RETURN_LOGIN_CALLBACK = registerCallback(new ItemReturnLoginCallback());
   
   public static final ItemModDataHandler BORISLIB_ITEM_DATA = new ItemModDataHandler(MOD_ID);
   
   @Override
   public void onInitialize(){
      PolymerResourcePackUtils.addModAssets(MOD_ID);
      
      ServerTickEvents.END_WORLD_TICK.register(WorldTickCallback::onWorldTick);
      ServerTickEvents.END_SERVER_TICK.register(ServerTickCallback::onTick);
      ServerPlayConnectionEvents.JOIN.register(PlayerConnectionCallback::onPlayerJoin);
      ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> SERVER = minecraftServer);
      CommandRegistrationCallback.EVENT.register(Commands::register);
      
      LOGGER.info("BorisLib ready and waiting!");
   }
   
   @Override
   public void onInitializeClient(){
      LOGGER.info("BorisLib Client loaded and ready!");
   }
   
   public static boolean addTickTimerCallback(TickTimerCallback callback){
      return SERVER_TIMER_CALLBACKS.add(callback);
   }
   
   public static boolean addTickTimerCallback(ServerWorld world, TickTimerCallback callback){
      return WORLD_TIMER_CALLBACKS.add(new Pair<>(world,callback));
   }
   
   public static boolean addLoginCallback(LoginCallback callback){
      return LOGIN_CALLBACK_LIST.get(callback.getWorld()).addCallback(callback);
   }
   
   public static LoginCallback createCallback(Identifier id){
      LoginCallback callback = LOGIN_CALLBACKS.get(id);
      if(callback == null) return null;
      return callback.makeNew();
   }
   
   private static Item registerItem(String id, Item item){
      Identifier identifier = Identifier.of(MOD_ID,id);
      Registry.register(ITEMS, identifier, Registry.register(Registries.ITEM, identifier, item));
      return item;
   }
   
   public static LoginCallback registerCallback(LoginCallback callback){
      return Registry.register(LOGIN_CALLBACKS,callback.getId(),callback);
   }
   
   public static GraphicalItem.GraphicElement registerGraphicItem(GraphicalItem.GraphicElement item){
      return Registry.register(BorisLib.GRAPHIC_ITEM_REGISTRY,item.id(),item);
   }
}
