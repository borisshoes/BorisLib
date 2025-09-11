package net.borisshoes.borislib.testmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.callbacks.ItemReturnTimerCallback;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.gui.GuiHelper;
import net.borisshoes.borislib.timers.GenericTimer;
import net.borisshoes.borislib.timers.RepeatTimer;
import net.borisshoes.borislib.utils.ParticleEffectUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.GiveCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
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
                        .then(argument("ticks",integer(0))
                              .executes(context -> Commands.worldCallback(context, getInteger(context,"ticks")))))
                  .then(literal("servercallback")
                        .then(argument("ticks",integer(0))
                              .executes(context -> Commands.serverCallback(context, getInteger(context,"ticks")))))
                  .then(literal("pagedgui").executes(Commands::pagedGui))
                  .then(literal("returnitem")
                        .then(argument("item",itemStack(access))
                              .then(argument("count", integer(1))
                                 .then(argument("delay",integer(0))
                                       .then(argument("slot",integer(-1))
                                             .executes(context -> Commands.returnItem(context, getItemStackArgument(context, "item").createStack(getInteger(context,"count"),true), getInteger(context,"delay"), getInteger(context,"slot"))))))))
            )
      );
   }
   
   private static int pagedGui(CommandContext<ServerCommandSource> context){
      if(context.getSource().isExecutedByPlayer()){
         TestGui testGui = new TestGui(context.getSource().getPlayer());
         GuiHelper.outlineGUI(testGui,0x1155dd,Text.literal("Test Border Text"));
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
         BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(stack,context.getSource().getPlayer(),delay,prefSlot));
      }else{
         context.getSource().sendError(Text.translatable("text.borislib.must_be_executed_by_player"));
         return -1;
      }
      return 1;
   }
   
   private static int getVersion(CommandContext<ServerCommandSource> context){
      context.getSource().sendFeedback(() -> Text.literal("BorisLib "+ FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata().getVersion().getFriendlyString()),false);
      return 1;
   }
   
   private static int worldCallback(CommandContext<ServerCommandSource> ctx, int ticks){
      BorisLib.addTickTimerCallback(ctx.getSource().getWorld(), new RepeatTimer(1,ticks, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.worldcallback"),false);
         double theta = (Math.PI / ticks) * (ctx.getSource().getServer().getTicks() % ticks);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(),null,ctx.getSource().getPosition(),new DustParticleEffect(0xff0000,1),5,200,1,0,0,theta);
      }, ctx.getSource().getWorld()));
      BorisLib.addTickTimerCallback(ctx.getSource().getWorld(), new GenericTimer(ticks+1, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.worldcallbackfinish"),false);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(),null,ctx.getSource().getPosition(),new DustParticleEffect(0x00ff00,1),3,100,1,0,0,0);
      }));
      return 0;
   }
   
   private static int serverCallback(CommandContext<ServerCommandSource> ctx, int ticks){
      BorisLib.addTickTimerCallback(new RepeatTimer(1,ticks, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.servercallback"),false);
         double theta = (Math.PI / ticks) * (ctx.getSource().getServer().getTicks() % ticks);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(),null,ctx.getSource().getPosition(),new DustParticleEffect(0xff0000,1),5,200,1,0,0,theta);
      },null));
      BorisLib.addTickTimerCallback(new GenericTimer(ticks+1, () -> {
         ctx.getSource().getServer().getPlayerManager().broadcast(Text.translatable("testmod.borislib.servercallbackfinish"),false);
         ParticleEffectUtils.sphere(ctx.getSource().getWorld(),null,ctx.getSource().getPosition(),new DustParticleEffect(0x00ff00,1),3,100,1,0,0,0);
      }));
      return 0;
   }
}
