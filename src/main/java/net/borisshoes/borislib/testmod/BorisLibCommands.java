package net.borisshoes.borislib.testmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.callbacks.ItemReturnTimerCallback;
import net.borisshoes.borislib.conditions.*;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DefaultPlayerData;
import net.borisshoes.borislib.gui.GuiHelper;
import net.borisshoes.borislib.network.Metrics;
import net.borisshoes.borislib.network.MetricsBar;
import net.borisshoes.borislib.network.PacketBuffer;
import net.borisshoes.borislib.timers.GenericTimer;
import net.borisshoes.borislib.timers.RepeatTimer;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.borisshoes.borislib.utils.ParticleEffectUtils;
import net.borisshoes.borislib.utils.TextUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.borisshoes.borislib.BorisLib.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.entity;
import static net.minecraft.commands.arguments.EntityArgument.getEntity;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static net.minecraft.commands.arguments.item.ItemArgument.getItem;
import static net.minecraft.commands.arguments.item.ItemArgument.item;

public class BorisLibCommands {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access, Commands.CommandSelection env){
      dispatcher.register(literal("borislib").executes(BorisLibCommands::getVersion)
            .then(literal("player").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                  .then(argument("player", string()).suggests(MinecraftUtils::getPlayerSuggestions)
                        .executes(context -> BorisLibCommands.playerInfo(context, getString(context, "player")))))
            .then(literal("condition").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                  .then(literal("add")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .then(argument("value", floatArg())
                                          .then(argument("duration", integer(1))
                                                .then(argument("operation", string()).suggests(BorisLibCommands::suggestOperations)
                                                      .then(argument("particles", bool())
                                                            .then(argument("stacking", bool())
                                                                  .then(argument("persistent", bool())
                                                                        .then(argument("inflictedBy", entity())
                                                                              .then(argument("identifier", id())
                                                                                    .executes(BorisLibCommands::conditionAddFullWithInflicter)))
                                                                        .then(argument("identifier", id())
                                                                              .executes(BorisLibCommands::conditionAddFull)))))
                                                      .then(argument("identifier", id())
                                                            .executes(BorisLibCommands::conditionAddShort))))))))
                  .then(literal("remove")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .then(argument("identifier", id()).suggests(BorisLibCommands::suggestInstanceIds)
                                          .executes(BorisLibCommands::conditionRemove)))))
                  .then(literal("clear")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .executes(BorisLibCommands::conditionClear))))
                  .then(literal("get")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .executes(BorisLibCommands::conditionGet))))
                  .then(literal("getInstance")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .then(argument("identifier", id()).suggests(BorisLibCommands::suggestInstanceIds)
                                          .executes(BorisLibCommands::conditionGetInstance)))))
                  .then(literal("getBase")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .executes(BorisLibCommands::conditionGetBase))))
                  .then(literal("getInstances")
                        .then(argument("entity", entity())
                              .then(argument("condition", id()).suggests(BorisLibCommands::suggestConditions)
                                    .executes(BorisLibCommands::conditionGetInstances)))))
            .then(literal("testmod").requires(source -> Commands.hasPermission(Commands.LEVEL_ADMINS).test(source)
                  && BorisLib.CONFIG != null && BorisLib.CONFIG.getBoolean(BorisLib.TESTMOD_FEATURES_ENABLED))
                  .then(literal("worldcallback")
                        .then(argument("ticks", integer(0))
                              .executes(context -> BorisLibCommands.worldCallback(context, getInteger(context, "ticks")))))
                  .then(literal("servercallback")
                        .then(argument("ticks", integer(0))
                              .executes(context -> BorisLibCommands.serverCallback(context, getInteger(context, "ticks")))))
                  .then(literal("pagedgui").executes(BorisLibCommands::pagedGui))
                  .then(literal("particlestress").executes(BorisLibCommands::particleStress))
                  .then(literal("returnitem")
                        .then(argument("item", item(access))
                              .then(argument("count", integer(1))
                                    .then(argument("delay", integer(0))
                                          .then(argument("slot", integer(-1))
                                                .executes(context -> BorisLibCommands.returnItem(context, getItem(context, "item").createItemStack(getInteger(context, "count"), true), getInteger(context, "delay"), getInteger(context, "slot"))))))))
                  .then(literal("returnitem")
                        .then(argument("item", item(access))
                              .then(argument("count", integer(1))
                                    .executes(context -> BorisLibCommands.returnItem2(context, getItem(context, "item").createItemStack(getInteger(context, "count"), true))))))
                  .then(literal("energybar")
                        .then(argument("prefix", word())
                              .then(argument("suffix", word())
                                    .executes(context -> BorisLibCommands.energyBar(context, getString(context, "prefix"), getString(context, "suffix"))))))
                  .then(literal("marker")
                        .then(literal("place")
                              .then(argument("name", word())
                                    .executes(context -> BorisLibCommands.placeMarker(context, getString(context, "name")))))
                        .then(literal("remove")
                              .then(argument("name", word())
                                    .executes(context -> BorisLibCommands.removeMarker(context, getString(context, "name")))))
                        .then(literal("list")
                              .executes(BorisLibCommands::listMarkers)))
                  .then(literal("timestamp")
                        .then(literal("mark")
                              .executes(BorisLibCommands::setTimestamp))
                        .then(literal("read")
                              .executes(BorisLibCommands::readTimestamp)))
            )
            .then(literal("reload").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                  .executes(BorisLibCommands::reloadConfig))
            .then(literal("netstats").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                  .executes(ctx -> sendStats(ctx, "all"))
                  .then(literal("bar").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(BorisLibCommands::toggleBar))
                  .then(literal("reset").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(BorisLibCommands::resetStats))
                  .then(argument("type", word())
                        .suggests((ctx, builder) -> {
                           builder.suggest("network");
                           builder.suggest("cpu");
                           builder.suggest("ram");
                           builder.suggest("all");
                           return builder.buildFuture();
                        })
                        .executes(ctx -> sendStats(ctx, getString(ctx, "type")))))
      );
      
      dispatcher.register(BorisLib.CONFIG.generateCommand("borislib","config"));
   }
   
   private static int readTimestamp(CommandContext<CommandSourceStack> context){
      GlobalTimestamp timestamp = DataAccess.getGlobal(GlobalTimestamp.KEY);
      context.getSource().sendSuccess(() -> Component.translatable("testmod.borislib.timestamp_read", timestamp.timestamp), false);
      return 1;
   }
   
   private static int setTimestamp(CommandContext<CommandSourceStack> context){
      GlobalTimestamp timestamp = DataAccess.getGlobal(GlobalTimestamp.KEY);
      timestamp.timestamp = System.currentTimeMillis();
      context.getSource().sendSuccess(() -> Component.translatable("testmod.borislib.timestamp_set", timestamp.timestamp), false);
      return 1;
   }
   
   private static int listMarkers(CommandContext<CommandSourceStack> context){
      for(ServerLevel world : context.getSource().getServer().getAllLevels()){
         WorldMarker.MarkerList markers = DataAccess.getWorld(world.dimension(), WorldMarker.KEY);
         context.getSource().sendSuccess(() -> Component.literal(world.dimension().toString() + ": "), false);
         for(WorldMarker marker : markers){
            context.getSource().sendSuccess(() -> Component.literal(" - " + marker.id + ": " + marker.pos.toShortString()), false);
         }
      }
      return 0;
   }
   
   private static int removeMarker(CommandContext<CommandSourceStack> context, String name){
      ServerLevel world = context.getSource().getLevel();
      WorldMarker.MarkerList markers = DataAccess.getWorld(world.dimension(), WorldMarker.KEY);
      int sizeBefore = markers.size();
      markers.removeIf(m -> m.id.equals(name));
      boolean removed = markers.size() < sizeBefore;
      if(removed){
         context.getSource().sendSuccess(() -> Component.translatable("testmod.borislib.marker_remove_some"), false);
         return 1;
      }else{
         context.getSource().sendSuccess(() -> Component.translatable("testmod.borislib.marker_remove_none"), false);
         return 0;
      }
   }
   
   private static int placeMarker(CommandContext<CommandSourceStack> context, String name){
      if(context.getSource().isPlayer()){
         ServerLevel world = context.getSource().getLevel();
         WorldMarker.MarkerList markers = DataAccess.getWorld(world.dimension(), WorldMarker.KEY);
         WorldMarker m = new WorldMarker();
         m.pos = BlockPos.containing(context.getSource().getPosition());
         m.id = name;
         markers.add(m);
         context.getSource().sendSuccess(() -> Component.translatable("testmod.borislib.marker_set", name), false);
      }else{
         context.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int pagedGui(CommandContext<CommandSourceStack> context){
      if(context.getSource().isPlayer()){
         TestGui testGui = new TestGui(context.getSource().getPlayer());
         GuiHelper.outlineGUI(testGui, 0x1155dd, Component.literal("Test Border Text"));
         testGui.buildPage();
         testGui.open();
      }else{
         context.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int particleStress(CommandContext<CommandSourceStack> context){
      if(!context.getSource().isPlayer()){
         context.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      ServerPlayer player = context.getSource().getPlayer();
      ServerLevel world = context.getSource().getLevel();
      int durationTicks = 200; // 10 seconds
      
      // ~10,000 particles per tick breakdown:
      // 3 spheres (2000 + 1500 + 1000 = 4500)
      // 6 circles at different heights (500 each = 3000)
      // 5 lightning bolts per tick (~ 500 each = 2500)
      // Total: ~10,000
      
      BorisLib.addTickTimerCallback(world, new RepeatTimer(1, durationTicks, () -> {
         if(player.isRemoved()) return;
         Vec3 center = player.position().add(0, 1, 0);
         int tick = world.getServer().getTickCount();
         double t = tick * 0.05; // Slow rotation
         
         // Large outer sphere - red dust, 2000 points, rotating
         ParticleEffectUtils.sphere(world, null, center, new DustParticleOptions(0xff2200, 1.5f), 8, 2000, 1, 0, 0, t);
         
         // Medium sphere - cyan dust, 1500 points, counter-rotating
         ParticleEffectUtils.sphere(world, null, center, new DustParticleOptions(0x00ccff, 1.0f), 5, 1500, 1, 0, 0, -t * 1.5);
         
         // Inner sphere - gold dust, 1000 points, fast rotation
         ParticleEffectUtils.sphere(world, null, center, new DustParticleOptions(0xffcc00, 0.8f), 3, 1000, 1, 0, 0, t * 3);
         
         // 6 circles at different heights, each 500 points, rotating at different speeds
         for(int i = 0; i < 6; i++){
            double height = -2.5 + i;
            double radius = 6 + 2 * Math.sin(t + i);
            int color = switch(i % 3){
               case 0 -> 0xff00ff;
               case 1 -> 0x00ff88;
               default -> 0x8800ff;
            };
            Vec3 ringCenter = center.add(0, height, 0);
            ParticleEffectUtils.circle(world, null, ringCenter, new DustParticleOptions(color, 1.2f), radius, 500, 1, 0, 0, t * (1 + i * 0.3));
         }
         
         // 5 lightning bolts radiating outward from center, regenerated every 10 ticks
         if(tick % 10 == 0){
            for(int i = 0; i < 5; i++){
               double angle = (Math.PI * 2 / 5) * i + t;
               Vec3 end = center.add(Math.cos(angle) * 12, 3 * Math.sin(t + i), Math.sin(angle) * 12);
               ParticleEffectUtils.animatedLightningBolt(world, center, end, 8, 1.5, new DustParticleOptions(0xffffff, 2.0f), 4, 1, 0.05, 0, true, 1, 10);
            }
         }
      }, world));
      
      context.getSource().sendSuccess(() -> Component.translatable("testmod.borislib.particlestress_started").withStyle(ChatFormatting.GREEN), true);
      return 1;
   }
   
   private static int returnItem(CommandContext<CommandSourceStack> context, ItemStack stack, int delay, int prefSlot){
      if(context.getSource().isPlayer()){
         BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(stack, context.getSource().getPlayer(), delay, prefSlot));
      }else{
         context.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int returnItem2(CommandContext<CommandSourceStack> context, ItemStack stack){
      if(context.getSource().isPlayer()){
         MinecraftUtils.returnItems(new SimpleContainer(stack), context.getSource().getPlayer());
      }else{
         context.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int energyBar(CommandContext<CommandSourceStack> ctx, String prefix, String suffix){
      if(ctx.getSource().isPlayer()){
         for(int i = 0; i <= 100; i++){
            double finalI = i;
            BorisLib.addTickTimerCallback(ctx.getSource().getLevel(), new GenericTimer(i * 4, () -> {
               TextUtils.energyBar(ctx.getSource().getPlayer(), finalI / 100.0, Component.literal(prefix).withStyle(ChatFormatting.RED), Component.literal(suffix).withStyle(ChatFormatting.AQUA), (style -> style.applyFormat(ChatFormatting.YELLOW)));
            }));
         }
      }else{
         ctx.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int getVersion(CommandContext<CommandSourceStack> context){
      context.getSource().sendSuccess(() -> Component.literal("BorisLib " + FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata().getVersion().getFriendlyString()), false);
      return 1;
   }
   
   private static int playerInfo(CommandContext<CommandSourceStack> context, String playerArg){
      UUID uuid = AlgoUtils.getUUID(playerArg);
      
      if(uuid.toString().equals(BLANK_UUID)){
         // Check online players first
         ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayerByName(playerArg);
         if(onlinePlayer != null){
            uuid = onlinePlayer.getUUID();
         } else {
            // Look through our stored player data for a matching username
            Map<UUID,DefaultPlayerData> allPlayerData = DataAccess.allPlayerDataFor(BorisLib.PLAYER_DATA_KEY);
            for(Map.Entry<UUID,DefaultPlayerData> entry : allPlayerData.entrySet()){
               DefaultPlayerData data = entry.getValue();
               if(playerArg.equalsIgnoreCase(data.getUsername()) || data.getKnownUsernames().stream().anyMatch(name -> name.equalsIgnoreCase(playerArg))){
                  uuid = entry.getKey();
                  break;
               }
            }
         }
      }
      
      if(uuid.toString().equals(BLANK_UUID)){
         context.getSource().sendFailure(Component.literal("Could not find player: " + playerArg));
         return 0;
      }
      
      try {
         DefaultPlayerData data = DataAccess.getPlayer(uuid, BorisLib.PLAYER_DATA_KEY);
         context.getSource().sendSuccess(() -> Component.literal("=== DefaultPlayerData ===").withStyle(ChatFormatting.GOLD), false);
         
         final UUID finalUuid = uuid;
         context.getSource().sendSuccess(() -> Component.literal("UUID: ").withStyle(ChatFormatting.GRAY)
               .append(Component.literal(finalUuid.toString()).withStyle(ChatFormatting.WHITE)), false);
         
         context.getSource().sendSuccess(() -> Component.literal("Username: ").withStyle(ChatFormatting.GRAY)
               .append(Component.literal(data.getUsername() != null ? data.getUsername() : "(none)").withStyle(ChatFormatting.WHITE)), false);
         
         context.getSource().sendSuccess(() -> Component.literal("Known Usernames: ").withStyle(ChatFormatting.GRAY)
               .append(Component.literal(data.getKnownUsernames().isEmpty() ? "(none)" : String.join(", ", data.getKnownUsernames())).withStyle(ChatFormatting.WHITE)), false);
         
         context.getSource().sendSuccess(() -> Component.literal("Has Profile Data: ").withStyle(ChatFormatting.GRAY)
               .append(Component.literal(data.getResolvableProfile() != null ? "Yes" : "No").withStyle(data.getResolvableProfile() != null ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
         
         if(data.getResolvableProfile() != null){
            // Show the player face if we have profile data
            context.getSource().sendSuccess(() -> Component.literal("Player Face: ").withStyle(ChatFormatting.GRAY)
                  .append(data.getFaceTextComponent().copy().withStyle(ChatFormatting.WHITE)), false);
         }
         
         return 1;
      } catch (Exception e) {
         context.getSource().sendFailure(Component.literal("Error retrieving player data: " + e.getMessage()));
         BorisLib.LOGGER.error("Error in playerInfo command: {}", e.getMessage());
         return 0;
      }
   }
   
   private static int worldCallback(CommandContext<CommandSourceStack> ctx, int ticks){
      BorisLib.addTickTimerCallback(ctx.getSource().getLevel(), new RepeatTimer(1, ticks, () -> {
         ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("testmod.borislib.worldcallback"), false);
         double theta = (Math.PI / ticks) * (ctx.getSource().getServer().getTickCount() % ticks);
         ParticleEffectUtils.sphere(ctx.getSource().getLevel(), null, ctx.getSource().getPosition(), new DustParticleOptions(0xff0000, 1), 5, 200, 1, 0, 0, theta);
      }, ctx.getSource().getLevel()));
      BorisLib.addTickTimerCallback(ctx.getSource().getLevel(), new GenericTimer(ticks + 1, () -> {
         ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("testmod.borislib.worldcallbackfinish"), false);
         ParticleEffectUtils.sphere(ctx.getSource().getLevel(), null, ctx.getSource().getPosition(), new DustParticleOptions(0x00ff00, 1), 3, 100, 1, 0, 0, 0);
      }));
      return 0;
   }
   
   private static int serverCallback(CommandContext<CommandSourceStack> ctx, int ticks){
      BorisLib.addTickTimerCallback(new RepeatTimer(1, ticks, () -> {
         ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("testmod.borislib.servercallback"), false);
         double theta = (Math.PI / ticks) * (ctx.getSource().getServer().getTickCount() % ticks);
         ParticleEffectUtils.sphere(ctx.getSource().getLevel(), null, ctx.getSource().getPosition(), new DustParticleOptions(0xff0000, 1), 5, 200, 1, 0, 0, theta);
      }, null));
      BorisLib.addTickTimerCallback(new GenericTimer(ticks + 1, () -> {
         ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("testmod.borislib.servercallbackfinish"), false);
         ParticleEffectUtils.sphere(ctx.getSource().getLevel(), null, ctx.getSource().getPosition(), new DustParticleOptions(0x00ff00, 1), 3, 100, 1, 0, 0, 0);
      }));
      return 0;
   }
   
   private static CompletableFuture<Suggestions> suggestConditions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      for(Identifier id : BorisLib.CONDITIONS.keySet()){
         String full = id.toString();
         String path = id.getPath();
         if(full.toLowerCase(Locale.ROOT).startsWith(start) || path.toLowerCase(Locale.ROOT).startsWith(start)){
            builder.suggest(full);
         }
      }
      return builder.buildFuture();
   }
   
   private static CompletableFuture<Suggestions> suggestOperations(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      for(AttributeModifier.Operation op : AttributeModifier.Operation.values()){
         String s = op.name().toLowerCase(Locale.ROOT);
         if(s.startsWith(start)) builder.suggest(s);
      }
      return builder.buildFuture();
   }
   
   private static CompletableFuture<Suggestions> suggestInstanceIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      try{
         Entity target = getEntity(context, "entity");
         Identifier condId = getId(context, "condition");
         Holder<Condition> holder = resolveCondition(condId);
         if(holder != null && target instanceof LivingEntity){
            String start = builder.getRemaining().toLowerCase(Locale.ROOT);
            for(ConditionInstance inst : Conditions.getConditionInstancesOf(target.getUUID(), holder)){
               String s = inst.getId().toString();
               if(s.toLowerCase(Locale.ROOT).startsWith(start)) builder.suggest(s);
            }
         }
      }catch(Exception ignored){}
      return builder.buildFuture();
   }
   
   private static Holder<Condition> resolveCondition(Identifier id){
      if(id == null) return null;
      return BorisLib.CONDITIONS.get(id).map(ref -> (Holder<Condition>) ref).orElse(null);
   }
   
   private static Holder<Condition> resolveCondition(String condStr){
      return resolveCondition(Identifier.tryParse(condStr));
   }
   
   private static AttributeModifier.Operation resolveOperation(String opStr){
      try{
         return AttributeModifier.Operation.valueOf(opStr.toUpperCase(Locale.ROOT));
      }catch(IllegalArgumentException e){
         return null;
      }
   }
   
   private static int conditionAddFullWithInflicter(CommandContext<CommandSourceStack> context){
      try{
         Entity inflicter = getEntity(context, "inflictedBy");
         return conditionAdd(context, getBool(context, "particles"), getBool(context, "stacking"), getBool(context, "persistent"), inflicter);
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionAddFull(CommandContext<CommandSourceStack> context){
      try{
         return conditionAdd(context, getBool(context, "particles"), getBool(context, "stacking"), getBool(context, "persistent"), null);
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionAddShort(CommandContext<CommandSourceStack> context){
      try{
         return conditionAdd(context, true, true, false, null);
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionAdd(CommandContext<CommandSourceStack> context, boolean particles, boolean stacking, boolean persistent, Entity inflicter) throws Exception {
      Entity target = getEntity(context, "entity");
      if(!(target instanceof LivingEntity living)){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.not_living_entity"));
         return 0;
      }
      Holder<Condition> holder = resolveCondition(getId(context, "condition"));
      if(holder == null){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
         return 0;
      }
      float value = getFloat(context, "value");
      int duration = getInteger(context, "duration");
      AttributeModifier.Operation operation = resolveOperation(getString(context, "operation"));
      if(operation == null){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_operation", getString(context, "operation")));
         return 0;
      }
      Identifier id = getId(context, "identifier");
      UUID inflictedByUuid = inflicter != null ? inflicter.getUUID() : null;
      
      ConditionInstance instance = new ConditionInstance(holder, id, duration, value, stacking, particles, persistent, operation, inflictedByUuid);
      boolean replaced = Conditions.addCondition(context.getSource().getServer(), living, instance);
      
      String detailKey;
      Object[] detailArgs;
      if(inflicter != null){
         detailKey = "command.borislib.condition.add.details_full_inflicter";
         detailArgs = new Object[]{id.toString(), value, duration, operation.name(), particles, stacking, persistent, inflicter.getDisplayName()};
      }else if(persistent || !particles || !stacking){
         detailKey = "command.borislib.condition.add.details_full";
         detailArgs = new Object[]{id.toString(), value, duration, operation.name(), particles, stacking, persistent};
      }else{
         detailKey = "command.borislib.condition.add.details_short";
         detailArgs = new Object[]{id.toString(), value, duration, operation.name()};
      }
      
      context.getSource().sendSuccess(() -> Component.translatable(replaced ? "command.borislib.condition.add.replaced" : "command.borislib.condition.add.added",
            holder.value().getName().withStyle(ChatFormatting.GREEN),
            target.getDisplayName(),
            Component.translatable(detailKey, detailArgs).withStyle(ChatFormatting.GRAY)), true);
      return 1;
   }
   
   private static int conditionRemove(CommandContext<CommandSourceStack> context){
      try{
         Entity target = getEntity(context, "entity");
         if(!(target instanceof LivingEntity living)){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.not_living_entity"));
            return 0;
         }
         Holder<Condition> holder = resolveCondition(getId(context, "condition"));
         if(holder == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
            return 0;
         }
         Identifier id = getId(context, "identifier");
         boolean removed = Conditions.removeCondition(context.getSource().getServer(), living, holder, id);
         if(removed){
            context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.remove.success",
                  holder.value().getName().withStyle(ChatFormatting.GREEN),
                  Component.literal("[" + id + "]").withStyle(ChatFormatting.GRAY),
                  target.getDisplayName()), true);
         }else{
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.remove.not_found",
                  holder.value().getName(), id.toString(), target.getDisplayName()));
         }
         return removed ? 1 : 0;
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionClear(CommandContext<CommandSourceStack> context){
      try{
         Entity target = getEntity(context, "entity");
         if(!(target instanceof LivingEntity living)){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.not_living_entity"));
            return 0;
         }
         Holder<Condition> holder = resolveCondition(getId(context, "condition"));
         if(holder == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
            return 0;
         }
         boolean removed = Conditions.removeConditions(context.getSource().getServer(), living, holder);
         if(removed){
            context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.clear.success",
                  holder.value().getName().withStyle(ChatFormatting.GREEN),
                  target.getDisplayName()), true);
         }else{
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.clear.not_found",
                  holder.value().getName(), target.getDisplayName()));
         }
         return removed ? 1 : 0;
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionGet(CommandContext<CommandSourceStack> context){
      try{
         Entity target = getEntity(context, "entity");
         Holder<Condition> holder = resolveCondition(getId(context, "condition"));
         if(holder == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
            return 0;
         }
         Triple<Float,Boolean,Boolean> stats = Conditions.getPrevalingCondition(target.getUUID(), holder);
         if(stats == null){
            context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get.not_active",
                  holder.value().getName().withStyle(ChatFormatting.GREEN),
                  target.getDisplayName(),
                  Component.translatable("command.borislib.condition.get.base", holder.value().getBase()).withStyle(ChatFormatting.GRAY)), false);
         }else{
            context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get.active",
                  holder.value().getNameWithValue(stats.getLeft()).withStyle(ChatFormatting.GREEN),
                  target.getDisplayName(),
                  Component.translatable("command.borislib.condition.get.stats", stats.getLeft(), stats.getMiddle(), stats.getRight()).withStyle(ChatFormatting.YELLOW)), false);
         }
         return 1;
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionGetInstance(CommandContext<CommandSourceStack> context){
      try{
         Entity target = getEntity(context, "entity");
         Holder<Condition> holder = resolveCondition(getId(context, "condition"));
         if(holder == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
            return 0;
         }
         Identifier id = getId(context, "identifier");
         ConditionInstance inst = Conditions.getConditionInstance(target.getUUID(), holder, id);
         if(inst == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.get_instance.not_found",
                  holder.value().getName(), id.toString(), target.getDisplayName()));
            return 0;
          }else{
             context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get_instance.success",
                   holder.value().getName().withStyle(ChatFormatting.GREEN),
                   Component.literal("[" + id + "]").withStyle(ChatFormatting.GRAY),
                   target.getDisplayName(),
                   Component.translatable("command.borislib.condition.get_instance.details", inst.getValue(), inst.getDuration(), inst.getTimer(), inst.getOperation().name(), inst.isStacking(), inst.hasParticles(), inst.isPersistent(), inst.getInflictedBy() != null ? inst.getInflictedBy().toString() : "none").withStyle(ChatFormatting.YELLOW)), false);
            return 1;
         }
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionGetBase(CommandContext<CommandSourceStack> context){
      try{
         getEntity(context, "entity"); // validate entity exists
         Holder<Condition> holder = resolveCondition(getId(context, "condition"));
         if(holder == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
            return 0;
         }
         float base = holder.value().getBase();
         context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get_base.success",
               holder.value().getName().withStyle(ChatFormatting.GREEN),
               Component.literal(String.valueOf(base)).withStyle(ChatFormatting.YELLOW)), false);
         return 1;
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   private static int conditionGetInstances(CommandContext<CommandSourceStack> context){
      try{
         Entity target = getEntity(context, "entity");
         Holder<Condition> holder = resolveCondition(getId(context, "condition"));
         if(holder == null){
            context.getSource().sendFailure(Component.translatable("command.borislib.condition.unknown_condition", getId(context, "condition").toString()));
            return 0;
         }
         List<ConditionInstance> instances = Conditions.getConditionInstancesOf(target.getUUID(), holder);
         if(instances.isEmpty()){
            context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get_instances.none",
                  holder.value().getName().withStyle(ChatFormatting.GREEN),
                  target.getDisplayName()), false);
         }else{
            context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get_instances.header",
                  holder.value().getName().withStyle(ChatFormatting.GREEN),
                  target.getDisplayName(),
                  Component.literal(String.valueOf(instances.size())).withStyle(ChatFormatting.GRAY)), false);
             for(ConditionInstance inst : instances){
                context.getSource().sendSuccess(() -> Component.translatable("command.borislib.condition.get_instances.entry",
                      Component.literal(inst.getId().toString()).withStyle(ChatFormatting.AQUA),
                      Component.translatable("command.borislib.condition.get_instance.details", inst.getValue(), inst.getDuration(), inst.getTimer(), inst.getOperation().name(), inst.isStacking(), inst.hasParticles(), inst.isPersistent(), inst.getInflictedBy() != null ? inst.getInflictedBy().toString() : "none").withStyle(ChatFormatting.GRAY)), false);
            }
         }
         return instances.size();
      }catch(Exception e){
         context.getSource().sendFailure(Component.translatable("command.borislib.condition.error", e.getMessage()));
         return 0;
      }
   }
   
   // ─── Network Optimization Commands ──────────────────────────────────
   
   private static int reloadConfig(CommandContext<CommandSourceStack> context){
      if(BorisLib.CONFIG != null){
         BorisLib.CONFIG.read();
         BorisLib.CONFIG.save();
      }
      PacketBuffer.reload();
      Metrics.reload();
      MetricsBar.reload();
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.reload.success").withStyle(ChatFormatting.GREEN), true);
      return 1;
   }
   
   private static int toggleBar(CommandContext<CommandSourceStack> context){
      ServerPlayer player = context.getSource().getPlayer();
      if(player == null){
         context.getSource().sendFailure(Component.translatable("text.borislib.must_be_executed_by_player"));
         return 0;
      }
      boolean showing = MetricsBar.toggle(player);
      if(showing){
         context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.bar.enabled").withStyle(ChatFormatting.GREEN), false);
      }else{
         context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.bar.disabled").withStyle(ChatFormatting.YELLOW), false);
      }
      return 1;
   }
   
   private static int sendStats(CommandContext<CommandSourceStack> context, String type){
      switch(type.toLowerCase(Locale.ROOT)){
         case "network" -> sendNetworkStats(context);
         case "cpu" -> sendCpuStats(context);
         case "ram" -> sendRamStats(context);
         case "all" -> {
            sendNetworkStats(context);
            sendCpuStats(context);
            sendRamStats(context);
         }
         default -> context.getSource().sendFailure(Component.translatable("command.borislib.netstats.unknown_type", type));
      }
      return 1;
   }
   
   private static void sendNetworkStats(CommandContext<CommandSourceStack> context){
      long logical = (long) Metrics.ppsLogical;
      long physical = (long) Metrics.ppsPhysical;
      long saved = logical - physical;
      String savePct = String.format("%.1f", logical > 0 ? (saved * 100.0 / logical) : 0);
      
      double speed = Metrics.networkSpeedKbs;
      String speedStr = speed > 1024 ? String.format("%.2f MB/s", speed / 1024.0) : String.format("%.1f KB/s", speed);
      
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.network.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.network.pps_logical", logical).withStyle(ChatFormatting.WHITE), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.network.pps_physical", physical).withStyle(ChatFormatting.AQUA), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.network.calls_saved", saved, savePct).withStyle(ChatFormatting.GREEN), false);
      context.getSource().sendSuccess(() -> Component.empty(), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.network.bandwidth", speedStr).withStyle(ChatFormatting.WHITE), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.network.optimized_chunks", Metrics.optimizedChunks.get()).withStyle(ChatFormatting.YELLOW), false);
   }
   
   private static void sendCpuStats(CommandContext<CommandSourceStack> context){
      String current = String.format("%.2f", Metrics.cpuUsage);
      String vanilla = String.format("%.2f", Metrics.vanillaCpuEst);
      String delta = String.format("%.3f", Metrics.vanillaCpuEst - Metrics.cpuUsage);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.cpu.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.cpu.current", current).withStyle(ChatFormatting.WHITE), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.cpu.vanilla_est", vanilla).withStyle(ChatFormatting.WHITE), false);
      context.getSource().sendSuccess(() -> Component.empty(), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.cpu.efficiency", delta).withStyle(ChatFormatting.GREEN), false);
   }
   
   private static void sendRamStats(CommandContext<CommandSourceStack> context){
      long savedBytes = Metrics.savedAllocationsBytes.get();
      String savedStr;
      if(savedBytes > 1024 * 1024 * 1024){
         savedStr = String.format("%.2f GB", savedBytes / (1024.0 * 1024.0 * 1024.0));
      }else if(savedBytes > 1024 * 1024){
         savedStr = String.format("%.0f MB", savedBytes / (1024.0 * 1024.0));
      }else if(savedBytes > 1024){
         savedStr = String.format("%.0f KB", savedBytes / 1024.0);
      }else{
         savedStr = savedBytes + " B";
      }
      
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.ram.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.ram.saved", savedStr).withStyle(ChatFormatting.WHITE), false);
   }
   
   private static int resetStats(CommandContext<CommandSourceStack> context){
      Metrics.reset();
      context.getSource().sendSuccess(() -> Component.translatable("command.borislib.netstats.reset.success").withStyle(ChatFormatting.GREEN), true);
      return 1;
   }
}
