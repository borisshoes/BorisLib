package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.timers.GenericTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import static net.borisshoes.borislib.BorisLib.LOGGER;

public class SoundUtils {
   public static void playSongToPlayer(ServerPlayer player, Holder<SoundEvent> event, float vol, float pitch){
      player.connection.send(new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x,player.position().y, player.position().z, vol, pitch,0));
   }
   
   public static void playSongToPlayer(ServerPlayer player, Holder.Reference<SoundEvent> event, float vol, float pitch){
      player.connection.send(new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x,player.position().y, player.position().z, vol, pitch,0));
   }
   
   public static void playSongToPlayer(ServerPlayer player, SoundEvent event, float vol, float pitch){
      player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), SoundSource.PLAYERS, player.position().x,player.position().y, player.position().z, vol, pitch,0));
   }
   
   public static void playSound(Level world, BlockPos pos, Holder<SoundEvent> event, SoundSource category, float vol, float pitch){
      try{
         world.playSound(null,pos.getX(),pos.getY(),pos.getZ(),event.value(), category, vol, pitch);
      }catch(Exception e){
         LOGGER.log(org.apache.logging.log4j.Level.ERROR,e);
      }
   }
   
   public static void playSound(Level world, BlockPos pos, SoundEvent event, SoundSource category, float vol, float pitch){
      try{
         world.playSound(null,pos.getX(),pos.getY(),pos.getZ(),event, category, vol, pitch);
      }catch(Exception e){
         LOGGER.log(org.apache.logging.log4j.Level.ERROR,e);
      }
   }
   
   public static void playSound(Level world, BlockPos pos, Holder.Reference<SoundEvent> event, SoundSource category, float vol, float pitch){
      try{
         world.playSeededSound(null,pos.getX(),pos.getY(),pos.getZ(),event,category,vol,pitch,0L);
      }catch(Exception e){
         LOGGER.log(org.apache.logging.log4j.Level.ERROR,e);
      }
   }
   
   public static void soulSounds(ServerLevel world, BlockPos pos, int count, int duration){
      for(int i = 0; i < duration; i++){
         BorisLib.addTickTimerCallback(world, new GenericTimer(2*(i+1), () -> {
            for(int j = 0; j < count; j++){
               playSound(world, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.3f, (float)(Math.random()*1.5+.5));
            }
         }));
      }
   }
   
   public static void soulSounds(ServerPlayer player, int count, int duration){
      for(int i = 0; i < duration; i++){
         BorisLib.addTickTimerCallback(player.level(), new GenericTimer(2*(i+1), () -> {
            for(int j = 0; j < count; j++){
               playSongToPlayer(player, SoundEvents.SOUL_ESCAPE, 2f, (float)(Math.random()*1.5+.5));
            }
         }));
      }
   }
}
