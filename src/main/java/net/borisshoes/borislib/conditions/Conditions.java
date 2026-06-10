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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static registry and management API for {@link Condition}s applied to entities.
 *
 * <p>This class:</p>
 * <ul>
 *    <li>Declares the built-in {@link Condition}s shipped with BorisLib (Celerity, Decay, Feeble,
 *        Fortitude, Might, Rejuvenation, Nearsight, Torpor, Vulnerability).</li>
 *    <li>Exposes the static API for adding, removing and querying {@link ConditionInstance}s on entities.</li>
 *    <li>Implements the prevailing-value calculation that combines stacking and non-stacking instances
 *        into the single value passed to {@link Condition#onTick}, {@link Condition#onApply} and
 *        {@link Condition#onRemove}.</li>
 * </ul>
 *
 * <p>State is stored globally via {@link DataAccess} on the {@link ConditionData#KEY} key.</p>
 */
public class Conditions {
   /** Built-in beneficial condition that scales movement speed. */
   public static final Holder<Condition> CELERITY = BorisLib.registerCondition(new CelerityCondition());
   /** Built-in harmful condition that deals withering damage over time. */
   public static final Holder<Condition> DECAY = BorisLib.registerCondition(new DecayCondition());
   /** Built-in harmful condition that reduces a damage multiplier on the affected entity. */
   public static final Holder<Condition> FEEBLE = BorisLib.registerCondition(new FeebleCondition());
   /** Built-in beneficial condition that scales armor / damage resistance. */
   public static final Holder<Condition> FORTITUDE = BorisLib.registerCondition(new FortitudeCondition());
   /** Built-in beneficial condition that scales attack damage. */
   public static final Holder<Condition> MIGHT = BorisLib.registerCondition(new MightCondition());
   /** Built-in beneficial condition that heals the affected entity over time. */
   public static final Holder<Condition> REJUVENATION = BorisLib.registerCondition(new RejuvenationCondition());
   /** Built-in harmful condition that reduces an entity's effective sight / follow range. */
   public static final Holder<Condition> NEARSIGHT = BorisLib.registerCondition(new NearsightCondition());
   /** Built-in harmful condition that slows the affected entity. */
   public static final Holder<Condition> TORPOR = BorisLib.registerCondition(new TorporCondition());
   /** Built-in harmful condition that amplifies incoming damage. */
   public static final Holder<Condition> VULNERABILITY = BorisLib.registerCondition(new VulnerabilityCondition());
   
   /**
    * Forces class loading, which triggers all static field initializers above to run.
    * Must be called during mod initialization to ensure conditions are registered
    * before anything queries the CONDITIONS registry.
    */
   public static void initialize(){
   }
   
   /** @return the global {@link ConditionData} store that holds all entity condition instances. */
   public static ConditionData getConditionData(){
      return DataAccess.getGlobal(ConditionData.KEY);
   }
   
   /**
    * Convenience helper that returns the prevailing value for a given condition on an entity, falling back
    * to the condition's {@link Condition#getBase() base value} when no instances are present.
    *
    * @param entityId the affected entity's UUID
    * @param holder   the condition type
    * @return the effective value for this entity / condition pair
    */
   public static float getConditionValue(UUID entityId, Holder<Condition> holder){
      Triple<Float, Boolean, Boolean> cond = getPrevalingCondition(entityId, holder);
      return cond == null ? holder.value().getBase() : cond.getLeft();
   }
   
   /**
    * Computes the prevailing condition state for an entity by gathering all current instances of the
    * specified type and combining them.
    *
    * @param entityId the affected entity's UUID
    * @param holder   the condition type
    * @return a triple of {@code (value, isStacking, hasParticles)}, or {@code null} if no instances are
    *         present or if the combined value equals the condition's base value
    */
   public static Triple<Float, Boolean, Boolean> getPrevalingCondition(UUID entityId, Holder<Condition> holder){
      List<ConditionInstance> conditions = getConditionInstancesOf(entityId, holder);
      if(conditions.isEmpty()) return null;
      return getPrevalingCondition(conditions, holder);
   }
   
   /**
    * Combines an arbitrary list of {@link ConditionInstance}s of one type into a prevailing
    * {@code (value, stacking, particles)} triple.
    *
    * <p>The algorithm:</p>
    * <ol>
    *    <li>Sums all stacking instances using their {@link AttributeModifier.Operation} (additive, then
    *        multiplied-base, then multiplied-total), producing a stacked value.</li>
    *    <li>If non-stacking instances exist, picks whichever non-stacking instance applied on its own
    *        produces the most "important" value (largest, or smallest when
    *        {@link Condition#isReversedImportance()} is set) and compares it against the stacked value
    *        the same way.</li>
    *    <li>Returns the winner, clamped via {@link Condition#sanitizeValue(float)}.</li>
    * </ol>
    *
    * @param conditions instances of one condition type to combine
    * @param holder     the condition type
    * @return the prevailing state, or {@code null} if no net effect remains
    */
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
   
   /**
    * Adds a {@link ConditionInstance} to an entity.
    *
    * <p>Behavior:</p>
    * <ul>
    *    <li>Returns {@code false} if {@link Condition#canApply(LivingEntity)} rejects the entity.</li>
    *    <li>For harmful conditions, returns {@code false} when the inflicting entity is an ally of or
    *        cannot harm the target (e.g. PvP-protected players).</li>
    *    <li>If an instance with the same {@code (condition, id)} pair already exists it is replaced in
    *        place and {@code true} is returned.</li>
    *    <li>Otherwise the instance is appended. {@link Condition#onApply} is fired only if this is the
    *        first instance of this condition type on the entity.</li>
    * </ul>
    *
    * @param server   the server context
    * @param entity   the entity to affect
    * @param instance the instance to add or replace
    * @return {@code true} if an instance with the same {@code (condition, id)} pair was replaced,
    *         {@code false} otherwise (including outright rejection cases)
    */
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
   
   /**
    * Removes every condition (of every type) from an entity, firing {@link Condition#onRemove} once per
    * distinct condition type before clearing.
    *
    * @param server the server context
    * @param entity the entity whose conditions are cleared
    */
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
   
   /**
    * Removes every instance of one condition type from an entity, firing {@link Condition#onRemove} once
    * for the condition.
    *
    * @param server the server context
    * @param entity the entity to update
    * @param holder the condition type to clear
    * @return {@code true} if at least one instance was removed
    */
   // Removes all conditions of type from an entity, returns if that condition instance existed on the entity
   public static boolean removeConditions(MinecraftServer server, LivingEntity entity, Holder<Condition> holder){
      ConditionData data = getConditionData();
      ArrayList<ConditionInstance> conditions = data.getConditions(entity.getUUID());
      Triple<Float, Boolean, Boolean> stats = getPrevalingCondition(entity.getUUID(), holder);
      if(stats != null) holder.value().onRemove(server, entity, stats.getLeft(), stats.getRight());
      return conditions.removeIf(inst -> inst.getCondition().equals(holder));
   }
   
   /**
    * Removes a specific instance identified by its {@code (condition, id)} pair. {@link Condition#onRemove}
    * is fired only if this was the last instance of the condition type on the entity.
    *
    * @param server the server context
    * @param entity the entity to update
    * @param holder the condition type
    * @param id     the per-source identifier of the instance to remove
    * @return {@code true} if a matching instance was found and removed
    */
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
   
   /**
    * Looks up a specific instance by its {@code (condition, id)} pair.
    *
    * @param entityId the entity UUID
    * @param holder   the condition type
    * @param id       the per-source identifier
    * @return the matching instance, or {@code null} if no such instance exists
    */
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
   
   /**
    * @param entityId the entity UUID
    * @return a defensive copy of every {@link ConditionInstance} currently on the entity (all types)
    */
   // Gets all condition instances for an entity
   public static List<ConditionInstance> getConditionInstances(UUID entityId){
      ConditionData data = getConditionData();
      return new ArrayList<>(data.getConditions(entityId));
   }
   
   /**
    * @param entityId the entity UUID
    * @param holder   the condition type to filter by
    * @return a new list containing every instance of the given condition type currently on the entity
    */
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
   
   /**
    * Event hook fired when a {@link LivingEntity} dies; clears all condition instances on the deceased
    * entity (firing {@link Condition#onRemove} as needed).
    *
    * @param entity       the entity that died
    * @param damageSource the cause of death (currently unused)
    */
   public static void entityDied(LivingEntity entity, DamageSource damageSource){
      removeAllConditions(entity.level().getServer(),entity);
   }
}
