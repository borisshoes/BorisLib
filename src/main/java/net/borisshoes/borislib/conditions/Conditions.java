package net.borisshoes.borislib.conditions;

import com.mojang.serialization.Lifecycle;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.ConditionData;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conditions {
   public static final Holder<Condition> DECAY = BorisLib.registerCondition(new DecayCondition());
   public static final Holder<Condition> FEEBLE = BorisLib.registerCondition(new FeebleCondition());
   public static final Holder<Condition> FORTITUDE = BorisLib.registerCondition(new FortitudeCondition());
   public static final Holder<Condition> MIGHT = BorisLib.registerCondition(new MightCondition());
   public static final Holder<Condition> REJUVENATION = BorisLib.registerCondition(new RejuvenationCondition());
   public static final Holder<Condition> NEARSIGHT = BorisLib.registerCondition(new NearsightCondition());
   public static final Holder<Condition> VULNERABILITY = BorisLib.registerCondition(new VulnerabilityCondition());
   
   /**
    * Forces class loading, which triggers all static field initializers above to run.
    * Must be called during mod initialization to ensure conditions are registered
    * before anything queries the CONDITIONS registry.
    */
   public static void initialize(){
   }
   
   public static ConditionData getConditionData(){
      return DataAccess.getGlobal(ConditionData.KEY);
   }
   
   public static float getConditionValue(UUID entityId, Holder<Condition> holder){
      Triple<Float, Boolean, Boolean> cond = getPrevalingCondition(entityId, holder);
      return cond == null ? holder.value().getBase() : cond.getLeft();
   }
   
   public static Triple<Float, Boolean, Boolean> getPrevalingCondition(UUID entityId, Holder<Condition> holder){
      List<ConditionInstance> conditions = getConditionInstancesOf(entityId, holder);
      if(conditions.isEmpty()) return null;
      return getPrevalingCondition(conditions, holder);
   }
   
   // Returns the value, stacking, particles of the overall condition status for an entity
   public static Triple<Float, Boolean, Boolean> getPrevalingCondition(List<ConditionInstance> conditions, Holder<Condition> holder){
      List<ConditionInstance> stacking = conditions.stream().filter(ConditionInstance::isStacking).toList();
      List<ConditionInstance> nonStacking = conditions.stream().filter(i -> !i.isStacking()).toList();
      if(stacking.isEmpty() && nonStacking.isEmpty()) return null;
      
      boolean prevailStacking = true;
      boolean prevailParticles = false;
      boolean hasStacking = !stacking.isEmpty();
      boolean hasNonStacking = !nonStacking.isEmpty();
      float prevailValue = 0;
      
      float base = holder.value().getBase();
      for(ConditionInstance added : stacking.stream().filter(i -> i.getOperation() == AttributeModifier.Operation.ADD_VALUE).toList()){
         base += added.getValue();
         if(added.hasParticles()) prevailParticles = true;
      }
      
      float baseModded = base;
      for(ConditionInstance multAdd : stacking.stream().filter(i -> i.getOperation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE).toList()){
         baseModded += base * multAdd.getValue();
         if(multAdd.hasParticles()) prevailParticles = true;
      }
      for(ConditionInstance multTot : stacking.stream().filter(i -> i.getOperation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).toList()){
         baseModded *= 1.0f + multTot.getValue();
         if(multTot.hasParticles()) prevailParticles = true;
      }
      prevailValue = holder.value().sanitizeValue(baseModded);
      if(!hasNonStacking){
         if(prevailValue == holder.value().getBase()) return null;
         return new ImmutableTriple<>(prevailValue, true, prevailParticles);
      }
      
      boolean init = false;
      for(ConditionInstance instance : nonStacking){
         base = holder.value().getBase();
         if(instance.getOperation() == AttributeModifier.Operation.ADD_VALUE){
            base += instance.getValue();
         }else if(instance.getOperation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE){
            base += base * instance.getValue();
         }else if(instance.getOperation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL){
            base *= 1.0f + instance.getValue();
         }
         base = holder.value().sanitizeValue(base);
         boolean replace = (!init && !hasStacking) || (holder.value().isReversedImportance() ? base < prevailValue : base > prevailValue);
         if(replace){
            prevailValue = base;
            prevailStacking = false;
            init = true;
            prevailParticles = instance.hasParticles();
         }
      }
      
      if(prevailValue == holder.value().getBase()) return null;
      return new ImmutableTriple<>(prevailValue, prevailStacking, prevailParticles);
   }
   
   // Adds a condition to an entity, returns if that condition instance was already applied
   // If a condition instance with the same type and id already exists, it is replaced in-place
   // Calls onApply if the entity had no instances of this condition type before
   public static boolean addCondition(MinecraftServer server, LivingEntity entity, ConditionInstance instance){
      ConditionData data = getConditionData();
      ArrayList<ConditionInstance> conditions = data.getConditions(entity.getUUID());
      
      if(!instance.getCondition().value().canApply(entity)) return false;
      if(instance.getCondition().value().isHarmful()){
         UUID inflictedBy = instance.getInflictedBy();
         LivingEntity inflictedEntity = MinecraftUtils.findLivingEntity(server, inflictedBy);
         if(inflictedEntity != null){
            if(inflictedEntity.isAlliedTo(entity)){
               return false;
            }
            if(entity instanceof ServerPlayer player){
               if(inflictedEntity instanceof ServerPlayer otherPlayer && !otherPlayer.canHarmPlayer(player))
                  return false;
            }
         }
      }
      
      boolean hadType = conditions.stream().anyMatch(ci -> ci.getCondition().equals(instance.getCondition()));
      for(int i = 0; i < conditions.size(); i++){
         ConditionInstance existing = conditions.get(i);
         if(existing.getCondition().equals(instance.getCondition()) && existing.getId().equals(instance.getId())){
            conditions.set(i, instance);
            return true;
         }
      }
      conditions.add(instance);
      if(!hadType){
         Triple<Float, Boolean, Boolean> stats = getPrevalingCondition(entity.getUUID(), instance.getCondition());
         if(stats != null) instance.getCondition().value().onApply(server, entity, stats.getLeft(), stats.getRight());
      }
      return false;
   }
   
   // Removes all conditions from an entity
   public static void removeAllConditions(MinecraftServer server, LivingEntity entity){
      ConditionData data = getConditionData();
      ArrayList<ConditionInstance> conditions = data.getConditions(entity.getUUID());
      // Collect distinct condition types and trigger onRemove for each
      conditions.stream().map(ConditionInstance::getCondition).distinct()
            .forEach(holder -> {
               Triple<Float, Boolean, Boolean> stats = getPrevalingCondition(entity.getUUID(), holder);
               if(stats != null) holder.value().onRemove(server, entity, stats.getLeft(), stats.getRight());
            });
      data.getAllConditions().remove(entity.getUUID());
   }
   
   // Removes all conditions of type from an entity, returns if that condition instance existed on the entity
   public static boolean removeConditions(MinecraftServer server, LivingEntity entity, Holder<Condition> holder){
      ConditionData data = getConditionData();
      ArrayList<ConditionInstance> conditions = data.getConditions(entity.getUUID());
      Triple<Float, Boolean, Boolean> stats = getPrevalingCondition(entity.getUUID(), holder);
      if(stats != null) holder.value().onRemove(server, entity, stats.getLeft(), stats.getRight());
      return conditions.removeIf(inst -> inst.getCondition().equals(holder));
   }
   
   // Removes a condition from an entity, returns if that condition instance existed on the entity
   public static boolean removeCondition(MinecraftServer server, LivingEntity entity, Holder<Condition> holder, Identifier id){
      ConditionData data = getConditionData();
      ArrayList<ConditionInstance> conditions = data.getConditions(entity.getUUID());
      Triple<Float, Boolean, Boolean> stats = getPrevalingCondition(entity.getUUID(), holder);
      boolean removed = conditions.removeIf(inst -> inst.getCondition().equals(holder) && inst.getId().equals(id));
      if(removed){
         if(stats != null && conditions.stream().noneMatch(inst -> inst.getCondition().equals(holder)))
            holder.value().onRemove(server, entity, stats.getLeft(), stats.getRight());
      }
      return removed;
   }
   
   // Gets a specific condition instance by its type and id
   public static ConditionInstance getConditionInstance(UUID entityId, Holder<Condition> holder, Identifier id){
      ConditionData data = getConditionData();
      for(ConditionInstance inst : data.getConditions(entityId)){
         if(inst.getCondition().equals(holder) && inst.getId().equals(id)){
            return inst;
         }
      }
      return null;
   }
   
   // Gets all condition instances for an entity
   public static List<ConditionInstance> getConditionInstances(UUID entityId){
      ConditionData data = getConditionData();
      return new ArrayList<>(data.getConditions(entityId));
   }
   
   // Gets all condition instances of a type for an entity
   public static List<ConditionInstance> getConditionInstancesOf(UUID entityId, Holder<Condition> holder){
      ConditionData data = getConditionData();
      List<ConditionInstance> result = new ArrayList<>();
      for(ConditionInstance inst : data.getConditions(entityId)){
         if(inst.getCondition().equals(holder)){
            result.add(inst);
         }
      }
      return result;
   }
}
