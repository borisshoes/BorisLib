package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.DynamicOps;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.conditions.ConditionInstance;
import net.borisshoes.borislib.conditions.EntityConditions;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Global {@link StorableData} that persists the {@link EntityConditions} record of every entity that has
 * conditions, keyed by entity UUID.
 *
 * <p>Registered under {@link #KEY} as a {@link DataKey.StorageScope#GLOBAL GLOBAL} key. This class only
 * stores and serializes records; they are mutated and ticked by
 * {@link net.borisshoes.borislib.conditions.Conditions}, which binds each record to its loaded entity.
 * Records of unloaded entities stay here with their timers paused.</p>
 *
 * <p>The saved layout carries a {@link #VERSION} field. Data written before version 4 used different value
 * semantics and is discarded on load rather than converted.</p>
 */
public class ConditionData implements StorableData {
   /** The on-disk format version written by this class. */
   public static final int VERSION = 4;

   private final Map<UUID, EntityConditions> entityConditions = new HashMap<>();

   /** Registered key for this global condition store. */
   public static final DataKey<ConditionData> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.fromNamespaceAndPath(MOD_ID, "condition_data"), ConditionData::new));

   @Override
   public void read(ValueInput view){
      entityConditions.clear();
      int version = view.getIntOr("version", 0);
      if(version < VERSION){
         int discarded = 0;
         for(CompoundTag ignored : view.listOrEmpty("entities", CompoundTag.CODEC)){
            discarded++;
         }
         if(discarded > 0){
            BorisLib.LOGGER.warn("Discarded condition data for {} entities saved by an older BorisLib (format version {}, expected {})", discarded, version, VERSION);
         }
         return;
      }

      DynamicOps<Tag> ops = ops();
      for(CompoundTag entry : view.listOrEmpty("entities", CompoundTag.CODEC)){
         try{
            UUID uuid = CodecUtils.UUID_CODEC.parse(NbtOps.INSTANCE, entry.get("uuid")).result().orElse(null);
            if(uuid == null) continue;
            ArrayList<ConditionInstance> conditions = new ArrayList<>();
            if(entry.get("conditions") instanceof ListTag list){
               for(int i = 0; i < list.size(); i++){
                  ConditionInstance.CODEC.parse(ops, list.get(i))
                        .resultOrPartial(error -> BorisLib.LOGGER.warn("Dropped invalid condition instance on entity {}: {}", uuid, error))
                        .ifPresent(conditions::add);
               }
            }
            if(!conditions.isEmpty()){
               EntityConditions record = EntityConditions.fromInstances(conditions);
               // Optional fields, absent in older saves: an unknown last-seen time starts counting from now
               record.restoreSeen(entry.getLongOr("lastSeen", -1L), entry.getBooleanOr("player", false));
               entityConditions.put(uuid, record);
            }
         }catch(Exception e){
            BorisLib.LOGGER.warn("Failed to decode condition data entry: {}", e.getMessage());
         }
      }
   }

   @Override
   public void writeNbt(CompoundTag tag){
      DynamicOps<Tag> ops = ops();
      ListTag entitiesList = new ListTag();
      for(var entry : entityConditions.entrySet()){
         if(entry.getValue().isEmpty()) continue;
         try{
            CompoundTag entryTag = new CompoundTag();
            CodecUtils.UUID_CODEC.encodeStart(NbtOps.INSTANCE, entry.getKey()).result().ifPresent(t -> entryTag.put("uuid", t));
            ListTag condList = new ListTag();
            for(ConditionInstance inst : entry.getValue().getAllInstances()){
               ConditionInstance.CODEC.encodeStart(ops, inst)
                     .resultOrPartial(error -> BorisLib.LOGGER.error("Failed to encode condition instance {} for entity {}: {}", inst.getId(), entry.getKey(), error))
                     .ifPresent(condList::add);
            }
            entryTag.put("conditions", condList);
            if(entry.getValue().getLastSeen() >= 0) entryTag.putLong("lastSeen", entry.getValue().getLastSeen());
            if(entry.getValue().isPlayer()) entryTag.putBoolean("player", true);
            entitiesList.add(entryTag);
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to encode condition data for entity {}: {}", entry.getKey(), e.getMessage());
         }
      }
      tag.putInt("version", VERSION);
      tag.put("entities", entitiesList);
   }

   // Particle specs can reference registry-backed data, which plain NbtOps cannot encode
   private static DynamicOps<Tag> ops(){
      return BorisLib.SERVER != null ? BorisLib.SERVER.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
   }

   /**
    * Looks up an entity's record. Never creates one.
    *
    * @param entityId the entity UUID
    * @return the entity's record, or {@code null} if it has no conditions
    */
   public EntityConditions get(UUID entityId){
      return entityConditions.get(entityId);
   }

   /**
    * Returns an entity's record, creating an empty one if the entity has none yet. Only the add paths in
    * {@link net.borisshoes.borislib.conditions.Conditions} should call this.
    *
    * @param entityId the entity UUID
    * @return the entity's record
    */
   public EntityConditions getOrCreate(UUID entityId){
      return entityConditions.computeIfAbsent(entityId, k -> new EntityConditions());
   }

   /**
    * Drops an entity's record without firing any condition hooks.
    *
    * @param entityId the entity UUID to wipe
    */
   public void removeEntity(UUID entityId){
      entityConditions.remove(entityId);
   }

   /** @return an unmodifiable view of the map from entity UUID to that entity's record. */
   public Map<UUID, EntityConditions> getAllConditions(){
      return Collections.unmodifiableMap(entityConditions);
   }
}
