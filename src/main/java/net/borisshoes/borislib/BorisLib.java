package net.borisshoes.borislib;

import com.mojang.serialization.Lifecycle;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.callbacks.*;
import net.borisshoes.borislib.conditions.Condition;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.conditions.NearsightCondition;
import net.borisshoes.borislib.config.ConfigManager;
import net.borisshoes.borislib.config.ConfigSetting;
import net.borisshoes.borislib.config.IConfigSetting;
import net.borisshoes.borislib.config.values.BooleanConfigValue;
import net.borisshoes.borislib.config.values.IntConfigValue;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.DefaultPlayerData;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.sequences.CameraEntity;
import net.borisshoes.borislib.testmod.BorisLibCommands;
import net.borisshoes.borislib.timers.TickTimerCallback;
import net.borisshoes.borislib.tracker.PlayerMovementEntry;
import net.borisshoes.borislib.sequences.SequenceManager;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.borisshoes.borislib.utils.ItemModDataHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
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
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


public class BorisLib implements ModInitializer, ClientModInitializer {
   
   public static final Logger LOGGER = LogManager.getLogger("BorisLib");
   public static final ArrayList<TickTimerCallback> SERVER_TIMER_CALLBACKS = new ArrayList<>();
   public static final ArrayList<Pair<ServerLevel, TickTimerCallback>> WORLD_TIMER_CALLBACKS = new ArrayList<>();
   public static final String MOD_ID = "borislib";
   private static final String CONFIG_NAME = "BorisLib.properties";
   private static final Identifier LATE_PHASE = Identifier.fromNamespaceAndPath(MOD_ID, "late");
   public static final String BLANK_UUID = "00000000-0000-4000-8000-000000000000";
   public static final HashMap<ServerPlayer, PlayerMovementEntry> PLAYER_MOVEMENT_TRACKER = new HashMap<>();
   
   /** The running server, or {@code null} before it starts and after it stops (e.g. between singleplayer worlds). */
   public static MinecraftServer SERVER = null;
   public static ConfigManager CONFIG;
   
   public static final Registry<GraphicalItem.GraphicElement> GRAPHIC_ITEM_REGISTRY = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "graphic_elements")), Lifecycle.stable());
   public static final Registry<Item> ITEMS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "item")), Lifecycle.stable());
   public static final Registry<LoginCallback> LOGIN_CALLBACKS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "login_callback")), Lifecycle.stable());
   public static final Registry<IConfigSetting<?>> CONFIG_SETTINGS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "config_settings")), Lifecycle.stable());
   public static final Registry<Condition> CONDITIONS = new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MOD_ID, "condition")), Lifecycle.stable());
   
   public static final Item GRAPHICAL_ITEM = registerItem("graphical_item", new GraphicalItem(new Item.Properties().stacksTo(99)));
   
   public static final EntityType<CameraEntity> CAMERA_ENTITY_TYPE = registerEntity("camera_entity",
         EntityType.Builder.of(CameraEntity::new, MobCategory.MISC)
               .sized(0f, 0f)
               .clientTrackingRange(128)
               .updateInterval(1)
               .noSummon()
               .noSave());
   
   public static final LoginCallback ITEM_RETURN_LOGIN_CALLBACK = registerCallback(new ItemReturnLoginCallback());
   
   public static final ItemModDataHandler BORISLIB_ITEM_DATA = new ItemModDataHandler(MOD_ID);
   
   public static final TagKey<EntityType<?>> IGNORES_NEARSIGHT = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_nearsight"));
   public static final TagKey<EntityType<?>> IGNORES_FEEBLE = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_feeble"));
   public static final TagKey<EntityType<?>> IGNORES_VULNERABLE = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_vulnerable"));
   public static final TagKey<EntityType<?>> IGNORES_DECAY = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "ignores_decay"));
   
   public static final DataKey<LoginCallbackContainer> LOGIN_CALLBACKS_KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "login_callbacks"), LoginCallbackContainer::new));
   public static final DataKey<DefaultPlayerData> PLAYER_DATA_KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "playerdata"), DefaultPlayerData::new));
   
   public static final IConfigSetting<?> TESTMOD_FEATURES_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("testmodFeaturesEnabled", false)));
   
   public static final IConfigSetting<?> CONDITION_RECORD_EXPIRY_DAYS = registerConfigSetting(new ConfigSetting<>(
         new IntConfigValue("conditionRecordExpiryDays", 0, new IntConfigValue.IntLimits(0))));
   
   @Override
   public void onInitialize(){
      CONFIG = new ConfigManager(MOD_ID, "Boris Lib", CONFIG_NAME, BorisLib.CONFIG_SETTINGS);
      
      Conditions.initialize();
      PolymerResourcePackUtils.addModAssets(MOD_ID);
      
      ServerTickEvents.END_LEVEL_TICK.register(WorldTickCallback::onWorldTick);
      ServerTickEvents.END_SERVER_TICK.register(ServerTickCallback::onTick);
      ServerPlayConnectionEvents.JOIN.register(PlayerConnectionCallback::onPlayerJoin);
      ServerPlayConnectionEvents.DISCONNECT.register(PlayerConnectionCallback::onPlayerLeave);
      ServerPlayerEvents.AFTER_RESPAWN.register(PlayerConnectionCallback::onPlayerRespawn);
      ServerLifecycleEvents.SERVER_STARTED.register(DataAccess::onServerStarted);
      ServerLifecycleEvents.SERVER_STARTED.register(Conditions::onServerStarted);
      ServerLifecycleEvents.SERVER_STARTED.register(SequenceManager::onServerStart);
      ServerLifecycleEvents.SERVER_STOPPED.register(Conditions::onServerStopped);
      // Flushing and clearing static state run after every other mod's stop handler, which may still write data
      ServerLifecycleEvents.SERVER_STOPPED.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
      ServerLifecycleEvents.SERVER_STOPPED.register(LATE_PHASE, DataAccess::onServerStop);
      ServerLifecycleEvents.SERVER_STOPPED.register(LATE_PHASE, BorisLib::onServerStopped);
      // Saving runs after every other mod's BEFORE_SAVE handler, so data they change there is in the same save. It is
      // still before the chunk save, right after vanilla writes its player files.
      ServerLifecycleEvents.BEFORE_SAVE.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
      ServerLifecycleEvents.BEFORE_SAVE.register(LATE_PHASE, DataAccess::onServerPreSave);
      ServerLifecycleEvents.BEFORE_SAVE.register(LATE_PHASE, DataAccess::onServerSave);
      ServerLivingEntityEvents.AFTER_DEATH.register(Conditions::entityDied);
      ServerLivingEntityEvents.AFTER_DEATH.register(SequenceManager::onPlayerDeath);
      ServerTickEvents.END_SERVER_TICK.register(SequenceManager::tick);
      ServerEntityEvents.ENTITY_LOAD.register(SequenceManager::onEntityLoad);
      ServerEntityEvents.ENTITY_LOAD.register(Conditions::onEntityLoad);
      ServerEntityEvents.ENTITY_UNLOAD.register(Conditions::onEntityUnload);
      CommandRegistrationCallback.EVENT.register(BorisLibCommands::register);
      
      if(FabricLoader.getInstance().isDevelopmentEnvironment()){
         // Mixins otherwise apply when their target first loads, so a broken one could stay hidden until e.g. the
         // first explosion. Auditing once the server is up applies them all, surfacing failures at dev startup.
         ServerLifecycleEvents.SERVER_STARTED.register(server -> MixinEnvironment.getCurrentEnvironment().audit());
      }
      
      LOGGER.info("BorisLib ready and waiting!");
   }
   
   @Override
   public void onInitializeClient(){
      LOGGER.info("BorisLib Client loaded and ready!");
   }
   
   /**
    * Late {@code SERVER_STOPPED} hook: clears every piece of static, per-server state so nothing leaks into the
    * next world opened in the same game session (singleplayer), then forgets the server.
    *
    * @param server the server that stopped
    */
   private static void onServerStopped(MinecraftServer server){
      SERVER_TIMER_CALLBACKS.clear();
      WORLD_TIMER_CALLBACKS.clear();
      net.borisshoes.borislib.events.Event.RECENT_EVENTS.clear();
      PLAYER_MOVEMENT_TRACKER.clear();
      SequenceManager.onServerStopped();
      NearsightCondition.onServerStopped();
      PendingItemReturns.onServerStopped();
      if(SERVER == server) SERVER = null;
   }
   
   public static boolean addTickTimerCallback(TickTimerCallback callback){
      return SERVER_TIMER_CALLBACKS.add(callback);
   }
   
   public static boolean addTickTimerCallback(ServerLevel world, TickTimerCallback callback){
      return WORLD_TIMER_CALLBACKS.add(Pair.of(world, callback));
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
   
   public static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> builder){
      Identifier identifier = Identifier.fromNamespaceAndPath(MOD_ID, id);
      EntityType<T> entityType = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, identifier));
      Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, id), entityType);
      PolymerEntityUtils.registerType(entityType);
      return entityType;
   }
}
