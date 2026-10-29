package net.borisshoes.borislib.sequences;

import com.mojang.serialization.Codec;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Global {@link StorableData} listing the UUIDs of saved entities that sequences spawn as stand-ins (such as a
 * cutscene's mannequin). Sequences never survive a restart, so anything still listed when the server starts is an
 * orphan that {@link SequenceManager} discards, now or once its chunk loads.
 */
public class TrackedCameraEntities implements StorableData {

   /** Registered GLOBAL key for the tracked entity set. */
   public static final DataKey<TrackedCameraEntities> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.fromNamespaceAndPath("borislib", "sequence_entities"), TrackedCameraEntities::new));

   private final Set<UUID> entities = new LinkedHashSet<>();
   private Runnable dirtyCallback = () -> {
   };

   @Override
   public void setDirtyCallback(Runnable callback){
      this.dirtyCallback = callback;
   }

   @Override
   public void markDirty(){
      dirtyCallback.run();
   }

   @Override
   public void read(ValueInput view){
      entities.clear();
      for(String s : view.listOrEmpty("entities", Codec.STRING)){
         try{
            entities.add(UUID.fromString(s));
         }catch(IllegalArgumentException ignored){
         }
      }
   }

   @Override
   public void writeNbt(CompoundTag tag){
      ListTag list = new ListTag();
      for(UUID uuid : entities){
         list.add(StringTag.valueOf(uuid.toString()));
      }
      tag.put("entities", list);
   }

   /** @return a copy of every tracked entity UUID. */
   public List<UUID> getAll(){
      return List.copyOf(entities);
   }

   /** @param uuid an entity spawned by a sequence that must not outlive it */
   public void add(UUID uuid){
      if(entities.add(uuid)) markDirty();
   }

   /** @param uuid an entity that has been discarded */
   public void remove(UUID uuid){
      if(entities.remove(uuid)) markDirty();
   }

   /** @return {@code true} if the entity is tracked */
   public boolean contains(UUID uuid){
      return entities.contains(uuid);
   }
}
