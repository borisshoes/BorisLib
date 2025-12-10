package net.borisshoes.borislib.testmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.callbacks.ItemReturnTimerCallback;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.gui.GuiHelper;
import net.borisshoes.borislib.timers.GenericTimer;
import net.borisshoes.borislib.timers.RepeatTimer;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.borisshoes.borislib.utils.ParticleEffectUtils;
import net.borisshoes.borislib.utils.TextUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.GiveCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.borisshoes.borislib.BorisLib.MOD_ID;
import static net.minecraft.command.argument.ItemStackArgumentType.getItemStackArgument;
import static net.minecraft.command.argument.ItemStackArgumentType.itemStack;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
   public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env){
      dispatcher.register(literal("borislib").executes(Commands::getVersion)
            .then(literal("testmod").requires(source -> source.hasPermissionLevel(2))
                  .then(literal("worldcallback")
                        .then(argument("ticks", integer(0))
                              .executes(context -> Commands.worldCallback(context, getInteger(context, "ticks")))))
                  .then(literal("servercallback")
                        .then(argument("ticks", integer(0))
                              .executes(context -> Commands.serverCallback(context, getInteger(context, "ticks")))))
                  .then(literal("pagedgui").executes(Commands::pagedGui))
                  .then(literal("returnitem")
                        .then(argument("item", itemStack(access))
                              .then(argument("count", integer(1))
                                    .then(argument("delay", integer(0))
                                          .then(argument("slot", integer(-1))
                                                .executes(context -> Commands.returnItem(context, getItemStackArgument(context, "item").createStack(getInteger(context, "count"), true), getInteger(context, "delay"), getInteger(context, "slot"))))))))
                  .then(literal("returnitem")
                        .then(argument("item", itemStack(access))
                              .then(argument("count", integer(1))
                                    .executes(context -> Commands.returnItem2(context, getItemStackArgument(context, "item").createStack(getInteger(context, "count"), true))))))
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
   
   private static int readTimestamp(CommandContext<ServerCommandSource> context){
      GlobalTimestamp timestamp = DataAccess.getGlobal(GlobalTimestamp.KEY);
      context.getSource().sendFeedback(() -> Text.translatable("testmod.borislib.timestamp_read",timestamp.timestamp), false);
      return 1;
   }
   
   private static int setTimestamp(CommandContext<ServerCommandSource> context){
      GlobalTimestamp timestamp = DataAccess.getGlobal(GlobalTimestamp.KEY);
      timestamp.timestamp = System.currentTimeMillis();
      context.getSource().sendFeedback(() -> Text.translatable("testmod.borislib.timestamp_set",timestamp.timestamp), false);
      return 1;
   }
   
   private static int listMarkers(CommandContext<ServerCommandSource> context){
      for(ServerWorld world : context.getSource().getServer().getWorlds()){
         List<WorldMarker> markers = DataAccess.getWorld(world.getRegistryKey(),WorldMarker.KEY);
         context.getSource().sendFeedback(() -> Text.literal(world.getRegistryKey().toString()+": "),false);
         for(WorldMarker marker : markers){
            context.getSource().sendFeedback(() -> Text.literal(" - "+marker.id+": "+marker.pos.toShortString()),false);
         }
      }
      return 0;
   }
   
   private static int removeMarker(CommandContext<ServerCommandSource> context, String name){
      ServerWorld world = context.getSource().getWorld();
      List<WorldMarker> markers = DataAccess.getWorld(world.getRegistryKey(),WorldMarker.KEY);
      List<WorldMarker> newMarkers = markers.stream().filter(m -> !m.id.equals(name)).toList();
      DataAccess.setWorld(world,WorldMarker.KEY,newMarkers);
      boolean removed = newMarkers.size() < markers.size();
      if(removed){
         context.getSource().sendFeedback(() -> Text.translatable("testmod.borislib.marker_remove_some"), false);
         return 1;
      }else{
         context.getSource().sendFeedback(() -> Text.translatable("testmod.borislib.marker_remove_none"), false);
         return 0;
      }
   }
   
   private static int placeMarker(CommandContext<ServerCommandSource> context, String name){
      if(context.getSource().isExecutedByPlayer()){
         ServerWorld world = context.getSource().getWorld();
         List<WorldMarker> markers = DataAccess.getWorld(world.getRegistryKey(),WorldMarker.KEY);
         WorldMarker m = new WorldMarker();
         m.pos = BlockPos.ofFloored(context.getSource().getPosition());
         m.id = name;
         markers.add(m);
         context.getSource().sendFeedback(() -> Text.translatable("testmod.borislib.marker_set",name), false);
      }else{
         context.getSource().sendError(Text.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int pagedGui(CommandContext<ServerCommandSource> context){
      if(context.getSource().isExecutedByPlayer()){
         TestGui testGui = new TestGui(context.getSource().getPlayer());
         GuiHelper.outlineGUI(testGui, 0x1155dd, Text.literal("Test Border Text"));
         testGui.buildPage();
         testGui.open();
      }else{
         context.getSource().sendError(Text.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int returnItem(CommandContext<ServerCommandSource> context, ItemStack stack, int delay, int prefSlot){
      if(context.getSource().isExecutedByPlayer()){
         BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(stack, context.getSource().getPlayer(), delay, prefSlot));
      }else{
         context.getSource().sendError(Text.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int returnItem2(CommandContext<ServerCommandSource> context, ItemStack stack){
      if(context.getSource().isExecutedByPlayer()){
         MinecraftUtils.returnItems(new SimpleInventory(stack), context.getSource().getPlayer());
      }else{
         context.getSource().sendError(Text.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int energyBar(CommandContext<ServerCommandSource> ctx, String prefix, String suffix){
      if(ctx.getSource().isExecutedByPlayer()){
         for(int i = 0; i <= 100; i++){
            double finalI = i;
            BorisLib.addTickTimerCallback(ctx.getSource().getWorld(), new GenericTimer(i * 4, () -> {
               TextUtils.energyBar(ctx.getSource().getPlayer(), finalI / 100.0, Text.literal(prefix).formatted(Formatting.RED), Text.literal(suffix).formatted(Formatting.AQUA), (style -> style.withFormatting(Formatting.YELLOW)));
            }));
         }
      }else{
         ctx.getSource().sendError(Text.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int getVersion(CommandContext<ServerCommandSource> context){
      context.getSource().sendFeedback(() -> Text.literal("BorisLib " + FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata().getVersion().getFriendlyString()), false);
      return 1;
   }
   
   private static int worldCallback(CommandContext<ServerCommandSource> ctx, int ticks){
      BorisLib.addTickTimerCallback(ctx.getSource().getWorld(), new RepeatTimer(1, ticks, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.worldcallback"), false);
         double theta = (Math.PI / ticks) * (ctx.getSource().getServer().getTicks() % ticks);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(), null, ctx.getSource().getPosition(), new DustParticleEffect(0xff0000, 1), 5, 200, 1, 0, 0, theta);
      }, ctx.getSource().getWorld()));
      BorisLib.addTickTimerCallback(ctx.getSource().getWorld(), new GenericTimer(ticks + 1, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.worldcallbackfinish"), false);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(), null, ctx.getSource().getPosition(), new DustParticleEffect(0x00ff00, 1), 3, 100, 1, 0, 0, 0);
      }));
      return 0;
   }
   
   private static int serverCallback(CommandContext<ServerCommandSource> ctx, int ticks){
      BorisLib.addTickTimerCallback(new RepeatTimer(1, ticks, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.servercallback"), false);
         double theta = (Math.PI / ticks) * (ctx.getSource().getServer().getTicks() % ticks);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(), null, ctx.getSource().getPosition(), new DustParticleEffect(0xff0000, 1), 5, 200, 1, 0, 0, theta);
      }, null));
      BorisLib.addTickTimerCallback(new GenericTimer(ticks + 1, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.servercallbackfinish"), false);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(), null, ctx.getSource().getPosition(), new DustParticleEffect(0x00ff00, 1), 3, 100, 1, 0, 0, 0);
      }));
      return 0;
   }
}
