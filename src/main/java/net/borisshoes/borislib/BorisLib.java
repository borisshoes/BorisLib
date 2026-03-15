package net.borisshoes.borislib;

import com.mojang.serialization.Lifecycle;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.callbacks.*;
import net.borisshoes.borislib.conditions.Condition;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.config.ConfigManager;
import net.borisshoes.borislib.config.ConfigSetting;
import net.borisshoes.borislib.config.IConfigSetting;
import net.borisshoes.borislib.config.values.BooleanConfigValue;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.DefaultPlayerData;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.testmod.BorisLibCommands;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.borisshoes.borislib.utils.ItemModDataHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;


public class BorisLib implements ModInitializer, ClientModInitializer {
   
   public static final Logger LOGGER = LogManager.getLogger("BorisLib");
   public static final ArrayList<TickTimerCallback> SERVER_TIMER_CALLBACKS = new ArrayList<>();
   public static final ArrayList<Tuple<ServerLevel, TickTimerCallback>> WORLD_TIMER_CALLBACKS = new ArrayList<>();
   public static final String MOD_ID = "borislib";
   private static final String CONFIG_NAME = "BorisLib.properties";
   public static final String BLANK_UUID = "00000000-0000-4000-8000-000000000000";
   public static final HashMap<ServerPlayer, PlayerMovementEntry> PLAYER_MOVEMENT_TRACKER = new HashMap<>();
   
   public static MinecraftServer SERVER = null;
   public static ConfigManager CONFIG;
   
   public static final Registry<GraphicalItem.GraphicElement> GRAPHIC_ITEM_REGISTRY = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "graphic_elements")), Lifecycle.stable());
   public static final Registry<Item> ITEMS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "item")), Lifecycle.stable());
   public static final Registry<LoginCallback> LOGIN_CALLBACKS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "login_callback")), Lifecycle.stable());
   public static final Registry<IConfigSetting<?>> CONFIG_SETTINGS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "config_settings")), Lifecycle.stable());
   public static final Registry<Condition> CONDITIONS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "condition")), Lifecycle.stable());
   
   public static final Item GRAPHICAL_ITEM = registerItem("graphical_item", new GraphicalItem(new Item.Properties().stacksTo(99)));
   
   public static final LoginCallback ITEM_RETURN_LOGIN_CALLBACK = registerCallback(new ItemReturnLoginCallback());
   
   public static final ItemModDataHandler BORISLIB_ITEM_DATA = new ItemModDataHandler(MOD_ID);
   
   public static final TagKey<EntityType<?>> IGNORES_NEARSIGHT = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_nearsight"));
   public static final TagKey<EntityType<?>> IGNORES_FEEBLE = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_feeble"));
   public static final TagKey<EntityType<?>> IGNORES_VULNERABLE = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_vulnerable"));
   public static final TagKey<EntityType<?>> IGNORES_DECAY = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_decay"));
   
   public static final DataKey<LoginCallbackContainer> LOGIN_CALLBACKS_KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "login_callbacks"), LoginCallbackContainer::new));
   public static final DataKey<DefaultPlayerData> PLAYER_DATA_KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "playerdata"), DefaultPlayerData::new));
   
   public static final IConfigSetting<?> PARTICLE_PACKET_BUNDLE_OPTIMIZATION = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("particlePacketBundleOptimization", false)));
   public static final IConfigSetting<?> TESTMOD_FEATURES_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("testmodFeaturesEnabled", false)));
   
   @Override
   public void onInitialize(){
      CONFIG = new ConfigManager(MOD_ID, "Boris Lib", CONFIG_NAME, BorisLib.CONFIG_SETTINGS);
      
      Conditions.initialize();
      PolymerResourcePackUtils.addModAssets(MOD_ID);
      
      ServerTickEvents.END_WORLD_TICK.register(WorldTickCallback::onWorldTick);
      ServerTickEvents.END_SERVER_TICK.register(ServerTickCallback::onTick);
      ServerPlayConnectionEvents.JOIN.register(PlayerConnectionCallback::onPlayerJoin);
      ServerPlayConnectionEvents.DISCONNECT.register(PlayerConnectionCallback::onPlayerLeave);
      ServerLifecycleEvents.SERVER_STARTED.register(DataAccess::onServerStarted);
      ServerLifecycleEvents.SERVER_STOPPED.register(DataAccess::onServerStop);
      ServerLifecycleEvents.AFTER_SAVE.register(DataAccess::onServerSave);
      CommandRegistrationCallback.EVENT.register(BorisLibCommands::register);
      
      LOGGER.info("BorisLib ready and waiting!");
   }
   
   @Override
   public void onInitializeClient(){
      LOGGER.info("BorisLib Client loaded and ready!");
   }
   
   public static boolean addTickTimerCallback(TickTimerCallback callback){
      return SERVER_TIMER_CALLBACKS.add(callback);
      // TODO serialize on world stop?
   }
   
   public static boolean addTickTimerCallback(ServerLevel world, TickTimerCallback callback){
      return WORLD_TIMER_CALLBACKS.add(new Tuple<>(world, callback));
      // TODO serialize on world stop?
   }
   
   public static boolean addLoginCallback(LoginCallback callback){
      UUID playerId = AlgoUtils.getUUID(callback.getPlayer());
      LoginCallbackContainer container = DataAccess.getPlayer(playerId, LOGIN_CALLBACKS_KEY);
      boolean added = container.addCallback(callback);
      DataAccess.setPlayer(playerId, LOGIN_CALLBACKS_KEY, container);
      return added;
   }
   
   public static LoginCallback createCallback(Identifier id){
      LoginCallback callback = LOGIN_CALLBACKS.getValue(id);
      if(callback == null) return null;
      return callback.makeNew();
   }
   
   private static Item registerItem(String id, Item item){
      Identifier identifier = Identifier.fromNamespaceAndPath(MOD_ID, id);
      Registry.register(ITEMS, identifier, Registry.register(BuiltInRegistries.ITEM, identifier, item));
      return item;
   }
   
   public static LoginCallback registerCallback(LoginCallback callback){
      return Registry.register(LOGIN_CALLBACKS, callback.getId(), callback);
   }
   
   public static GraphicalItem.GraphicElement registerGraphicItem(GraphicalItem.GraphicElement item){
      return Registry.register(BorisLib.GRAPHIC_ITEM_REGISTRY, item.id(), item);
   }
   
   public static Holder<Condition> registerCondition(Condition condition){
      return Registry.registerForHolder(CONDITIONS, condition.getId(), condition);
   }
   
   private static IConfigSetting<?> registerConfigSetting(IConfigSetting<?> setting){
      Identifier identifier = Identifier.fromNamespaceAndPath(MOD_ID, setting.getId());
      Registry.register(CONFIG_SETTINGS, identifier, setting);
      return setting;
   }
}
