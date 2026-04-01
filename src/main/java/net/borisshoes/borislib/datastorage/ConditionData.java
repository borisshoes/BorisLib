package net.borisshoes.borislib.datastorage;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.conditions.Condition;
import net.borisshoes.borislib.conditions.ConditionInstance;
import net.borisshoes.borislib.conditions.Conditions;
import net.borisshoes.borislib.utils.CodecUtils;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class ConditionData implements StorableData {
   
   private final Map<UUID, ArrayList<ConditionInstance>> entityConditions = new HashMap<>();
   
   public static final DataKey<ConditionData> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.fromNamespaceAndPath(MOD_ID, "condition_data"), ConditionData::new));
   
   @Override
   public void read(ValueInput view){
      entityConditions.clear();
      for(CompoundTag entry : view.listOrEmpty("entities", CompoundTag.CODEC)){
         try{
            UUID uuid = CodecUtils.UUID_CODEC.parse(NbtOps.INSTANCE, entry.get("uuid")).result().orElse(null);
            if(uuid == null) continue;
            ArrayList<ConditionInstance> conditions = new ArrayList<>();
            if(entry.get("conditions") instanceof ListTag list){
               for(int i = 0; i < list.size(); i++){
                  ConditionInstance.CODEC.parse(NbtOps.INSTANCE, list.get(i)).result().ifPresent(conditions::add);
               }
            }
            if(!conditions.isEmpty()){
               entityConditions.put(uuid, conditions);
            }
         }catch(Exception e){
            BorisLib.LOGGER.warn("Failed to decode condition data entry: {}", e.getMessage());
         }
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      ListTag entitiesList = new ListTag();
      for(var entry : entityConditions.entrySet()){
         if(entry.getValue().isEmpty()) continue;
         try{
            CompoundTag entryTag = new CompoundTag();
            CodecUtils.UUID_CODEC.encodeStart(NbtOps.INSTANCE, entry.getKey()).result().ifPresent(t -> entryTag.put("uuid", t));
            ListTag condList = new ListTag();
            for(ConditionInstance inst : entry.getValue()){
               ConditionInstance.CODEC.encodeStart(NbtOps.INSTANCE, inst).result().ifPresent(condList::add);
            }
            entryTag.put("conditions", condList);
            entitiesList.add(entryTag);
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to encode condition data for entity {}: {}", entry.getKey(), e.getMessage());
         }
      }
      tag.put("entities", entitiesList);
   }
   
   public ArrayList<ConditionInstance> getConditions(UUID entityId){
      return entityConditions.computeIfAbsent(entityId, k -> new ArrayList<>());
   }
   
   public void removeEntity(UUID entityId){
      entityConditions.remove(entityId);
   }
   
   public Map<UUID, ArrayList<ConditionInstance>> getAllConditions(){
      return entityConditions;
   }
   
   public void tick(MinecraftServer server){
      List<UUID> toRemove = new ArrayList<>();
      for(var entry : entityConditions.entrySet()){
         UUID entityId = entry.getKey();
         ArrayList<ConditionInstance> conditions = entry.getValue();
         if(conditions.isEmpty()){
            toRemove.add(entityId);
            continue;
         }
         
         LivingEntity entity = MinecraftUtils.findLivingEntity(server, entityId);
         if(entity == null) continue; // Entity not loaded, skip ticking but keep data
         
         try{
            // Collect all condition types present before ticking
            Set<Holder<Condition>> beforeConds = new HashSet<>();
            for(ConditionInstance ci : conditions){
               beforeConds.add(ci.getCondition());
            }
            
            // Tick and remove expired instances
            conditions.removeIf(ConditionInstance::tick);
            
            // Collect all condition types still present after ticking
            Set<Holder<Condition>> afterConds = new HashSet<>();
            for(ConditionInstance ci : conditions){
               afterConds.add(ci.getCondition());
            }
            
            // Tick active conditions
            for(Holder<Condition> cond : afterConds){
               Triple<Float, Boolean, Boolean> stats = Conditions.getPrevalingCondition(entityId, cond);
               if(stats != null && stats.getLeft() != cond.value().getBase())
                  cond.value().onTick(server, entity, stats.getLeft(), stats.getRight());
            }
            
            // Fire onRemove for condition types that are no longer present
            for(Holder<Condition> cond : beforeConds){
               if(!afterConds.contains(cond)){
                  cond.value().onRemove(server, entity, cond.value().getBase(), false);
               }
            }
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to tick conditions for entity {}: {}", entityId, e.getMessage());
            e.printStackTrace();
         }
      }
      
      for(UUID id : toRemove){
         entityConditions.remove(id);
      }
   }
}

