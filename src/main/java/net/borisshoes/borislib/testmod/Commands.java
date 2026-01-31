package net.borisshoes.borislib.testmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.callbacks.ItemReturnTimerCallback;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DefaultPlayerData;
import net.borisshoes.borislib.gui.GuiHelper;
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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.borisshoes.borislib.BorisLib.BLANK_UUID;
import static net.borisshoes.borislib.BorisLib.MOD_ID;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.item.ItemArgument.getItem;
import static net.minecraft.commands.arguments.item.ItemArgument.item;

public class Commands {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access, net.minecraft.commands.Commands.CommandSelection env){
      dispatcher.register(literal("borislib").executes(Commands::getVersion)
            .then(literal("player").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_ADMINS))
                  .then(argument("player", string()).suggests(MinecraftUtils::getPlayerSuggestions)
                        .executes(context -> Commands.playerInfo(context, getString(context, "player")))))
            .then(literal("testmod").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_ADMINS))
                  .then(literal("worldcallback")
                        .then(argument("ticks", integer(0))
                              .executes(context -> Commands.worldCallback(context, getInteger(context, "ticks")))))
                  .then(literal("servercallback")
                        .then(argument("ticks", integer(0))
                              .executes(context -> Commands.serverCallback(context, getInteger(context, "ticks")))))
                  .then(literal("pagedgui").executes(Commands::pagedGui))
                  .then(literal("returnitem")
                        .then(argument("item", item(access))
                              .then(argument("count", integer(1))
                                    .then(argument("delay", integer(0))
                                          .then(argument("slot", integer(-1))
                                                .executes(context -> Commands.returnItem(context, getItem(context, "item").createItemStack(getInteger(context, "count"), true), getInteger(context, "delay"), getInteger(context, "slot"))))))))
                  .then(literal("returnitem")
                        .then(argument("item", item(access))
                              .then(argument("count", integer(1))
                                    .executes(context -> Commands.returnItem2(context, getItem(context, "item").createItemStack(getInteger(context, "count"), true))))))
                  .then(literal("energybar")
                        .then(argument("prefix", word())
                              .then(argument("suffix", word())
                                    .executes(context -> Commands.energyBar(context, getString(context, "prefix"), getString(context, "suffix"))))))
                  .then(literal("marker")
                        .then(literal("place")
                              .then(argument("name", word())
                                    .executes(context -> Commands.placeMarker(context, getString(context, "name")))))
                        .then(literal("remove")
                              .then(argument("name", word())
                                    .executes(context -> Commands.removeMarker(context, getString(context, "name")))))
                        .then(literal("list")
                              .executes(Commands::listMarkers)))
                  .then(literal("timestamp")
                        .then(literal("mark")
                              .executes(Commands::setTimestamp))
                        .then(literal("read")
                              .executes(Commands::readTimestamp)))
            )
      );
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
}
