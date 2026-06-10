package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.timers.GenericTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import static net.borisshoes.borislib.BorisLib.LOGGER;

/**
 * Utility methods for playing sounds on the server.
 *
 * <p>Provides helpers to:</p>
 * <ul>
 *    <li>Retrieve {@link SoundEvent} references by identifier.</li>
 *    <li>Send positioned sounds to specific players.</li>
 *    <li>Play sounds in the world at block positions.</li>
 *    <li>Schedule repeating "soul sounds" effects (soul escape sound repeated over time).</li>
 * </ul>
 */
public class SoundUtils {
   
   /**
    * Looks up a {@link SoundEvent} by its identifier string (assumes the {@code minecraft} namespace).
    *
    * @param id the sound ID (path only, e.g. "entity.zombie.ambient")
    * @return a holder reference to the sound event
    */
   public static Holder.Reference<SoundEvent> getSound(String id){
      return BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace(id)).get();
   }
   
   /**
    * Sends a sound packet directly to a player at their current position.
    *
    * @param player the player receiving the sound
    * @param event  the sound event holder
    * @param vol    the volume (typically 0.0–1.0, but can exceed 1.0)
    * @param pitch  the pitch multiplier (1.0 = normal pitch)
    */
   public static void playSongToPlayer(ServerPlayer player, Holder<SoundEvent> event, float vol, float pitch){
      player.connection.send(new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, 0));
   }
   
   /**
    * Sends a sound packet directly to a player at their current position (reference holder overload).
    *
    * @param player the player receiving the sound
    * @param event  the sound event reference
    * @param vol    the volume
    * @param pitch  the pitch multiplier
    */
   public static void playSongToPlayer(ServerPlayer player, Holder.Reference<SoundEvent> event, float vol, float pitch){
      player.connection.send(new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, 0));
   }
   
   /**
    * Sends a sound packet directly to a player at their current position (raw {@link SoundEvent} overload).
    *
    * @param player the player receiving the sound
    * @param event  the sound event
    * @param vol    the volume
    * @param pitch  the pitch multiplier
    */
   public static void playSongToPlayer(ServerPlayer player, SoundEvent event, float vol, float pitch){
      player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, 0));
   }
   
   /**
    * Plays a sound in the world at the specified block position. Catches and logs any exceptions.
    *
    * @param world    the level
    * @param pos      the block position
    * @param event    the sound event holder
    * @param category the sound source category
    * @param vol      the volume
    * @param pitch    the pitch multiplier
    */
   public static void playSound(Level world, BlockPos pos, Holder<SoundEvent> event, SoundSource category, float vol, float pitch){
      try{
         world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), event.value(), category, vol, pitch);
      }catch(Exception e){
         LOGGER.log(org.apache.logging.log4j.Level.ERROR, e);
      }
   }
   
   /**
    * Plays a sound in the world at the specified block position (raw {@link SoundEvent} overload).
    *
    * @param world    the level
    * @param pos      the block position
    * @param event    the sound event
    * @param category the sound source category
    * @param vol      the volume
    * @param pitch    the pitch multiplier
    */
   public static void playSound(Level world, BlockPos pos, SoundEvent event, SoundSource category, float vol, float pitch){
      try{
         world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), event, category, vol, pitch);
      }catch(Exception e){
         LOGGER.log(org.apache.logging.log4j.Level.ERROR, e);
      }
   }
   
   /**
    * Plays a sound in the world at the specified block position (reference holder overload).
    *
    * @param world    the level
    * @param pos      the block position
    * @param event    the sound event reference
    * @param category the sound source category
    * @param vol      the volume
    * @param pitch    the pitch multiplier
    */
   public static void playSound(Level world, BlockPos pos, Holder.Reference<SoundEvent> event, SoundSource category, float vol, float pitch){
      try{
         world.playSeededSound(null, pos.getX(), pos.getY(), pos.getZ(), event, category, vol, pitch, 0L);
      }catch(Exception e){
         LOGGER.log(org.apache.logging.log4j.Level.ERROR, e);
      }
   }
   
   /**
    * Plays a sequence of soul escape sounds at a block position over time. Each "tick" plays
    * {@code count} soul sounds with randomized pitch.
    *
    * @param world    the server level
    * @param pos      the block position
    * @param count    the number of sounds to play each interval
    * @param duration the number of intervals (each interval is 2 ticks apart)
    */
   public static void soulSounds(ServerLevel world, BlockPos pos, int count, int duration){
      for(int i = 0; i < duration; i++){
         BorisLib.addTickTimerCallback(world, new GenericTimer(2 * (i + 1), () -> {
            for(int j = 0; j < count; j++){
               playSound(world, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.3f, (float) (Math.random() * 1.5 + .5));
            }
         }));
      }
   }
   
   /**
    * Plays a sequence of soul escape sounds directly to a player over time. Each "tick" plays
    * {@code count} soul sounds with randomized pitch.
    *
    * @param player   the player receiving the sounds
    * @param count    the number of sounds to play each interval
    * @param duration the number of intervals (each interval is 2 ticks apart)
    */
   public static void soulSounds(ServerPlayer player, int count, int duration){
      for(int i = 0; i < duration; i++){
         BorisLib.addTickTimerCallback(player.level(), new GenericTimer(2 * (i + 1), () -> {
            for(int j = 0; j < count; j++){
               playSongToPlayer(player, SoundEvents.SOUL_ESCAPE, 2f, (float) (Math.random() * 1.5 + .5));
            }
         }));
      }
   }
}
