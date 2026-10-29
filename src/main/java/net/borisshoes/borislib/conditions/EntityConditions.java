package net.borisshoes.borislib.conditions;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every condition on one entity: a {@link TypeState} per {@link Condition} type, plus the entity's
 * {@link ConditionParticleEmitter}.
 *
 * <p>One record exists per affected entity. It is persisted by
 * {@link net.borisshoes.borislib.datastorage.ConditionData} and bound to the loaded entity through
 * {@link ConditionHolder}. Records are only mutated by {@link Conditions}; everything exposed here is
 * read-only.</p>
 *
 * <p>Type states are keyed by holder identity, so always use the canonical holder returned by
 * {@link net.borisshoes.borislib.BorisLib#registerCondition(Condition)}.</p>
 */
public final class EntityConditions {
   private static final Holder<?>[] NO_HOLDERS = new Holder<?>[0];

   private final Reference2ObjectOpenHashMap<Holder<Condition>, TypeState> types = new Reference2ObjectOpenHashMap<>();
   private final ConditionParticleEmitter emitter = new ConditionParticleEmitter();
   private Holder<?>[] scratch = NO_HOLDERS;
   // Overworld game time when the entity was last loaded or unloaded, or -1 if unknown (older saves); used only
   // by the opt-in pruning of records whose entity never comes back
   private long lastSeen = -1L;
   private boolean player;

   /** Creates an empty record. */
   public EntityConditions(){
   }

   /** @return the overworld game time the entity was last seen loaded, or {@code -1} if unknown */
   public long getLastSeen(){
      return lastSeen;
   }

   /** @return {@code true} if this record has ever belonged to a player; such records are never pruned */
   public boolean isPlayer(){
      return player;
   }

   void markSeen(long gameTime, boolean isPlayer){
      this.lastSeen = gameTime;
      this.player |= isPlayer;
   }

   /**
    * Restores the last-seen bookkeeping from saved data.
    *
    * @param lastSeen overworld game time last seen, or {@code -1} if unknown
    * @param player   whether the record belongs to a player
    */
   public void restoreSeen(long lastSeen, boolean player){
      this.lastSeen = lastSeen;
      this.player = player;
   }

   /**
    * Rebuilds a record from decoded instances, computing each type's prevailing value without firing any
    * hooks.
    *
    * @param instances every instance on one entity, of any type
    * @return the record holding those instances
    */
   public static EntityConditions fromInstances(List<ConditionInstance> instances){
      EntityConditions record = new EntityConditions();
      for(ConditionInstance instance : instances){
         record.getOrCreateState(instance.getCondition()).instances.add(instance);
      }
      for(var entry : record.types.reference2ObjectEntrySet()){
         entry.getValue().set(ConditionMath.combine(entry.getValue().instances, entry.getKey().value()));
      }
      return record;
   }

   /**
    * @param holder the condition type
    * @return the cached prevailing magnitude of that type, or {@code 0} if it is not present
    */
   public float getValue(Holder<Condition> holder){
      TypeState state = types.get(holder);
      return state == null ? 0.0f : state.value;
   }

   /**
    * @param holder the condition type
    * @return the state of that type, or {@code null} if this entity has no instances of it
    */
   public TypeState getState(Holder<Condition> holder){
      return types.get(holder);
   }

   /** @return {@code true} if this record holds no condition types. */
   public boolean isEmpty(){
      return types.isEmpty();
   }

   /** @return a new list of every condition type that has at least one instance in this record. */
   public List<Holder<Condition>> getTypes(){
      return new ArrayList<>(types.keySet());
   }

   /** @return a new list of every instance in this record, of every type. */
   public List<ConditionInstance> getAllInstances(){
      List<ConditionInstance> all = new ArrayList<>();
      for(TypeState state : types.values()){
         all.addAll(state.instances);
      }
      return all;
   }

   Reference2ObjectOpenHashMap<Holder<Condition>, TypeState> types(){
      return types;
   }

   ConditionParticleEmitter emitter(){
      return emitter;
   }

   TypeState getOrCreateState(Holder<Condition> holder){
      TypeState state = types.get(holder);
      if(state == null){
         state = new TypeState();
         types.put(holder, state);
      }
      return state;
   }

   /**
    * Copies the current holders into a reused scratch array so the entity tick can iterate while hooks
    * mutate the record. Only the entity tick may use this; other paths take their own copy.
    *
    * @return the number of holders copied into {@link #scratch()}
    */
   int snapshot(){
      int size = types.size();
      if(scratch.length < size) scratch = new Holder<?>[Math.max(size, scratch.length * 2)];
      int i = 0;
      for(Holder<Condition> holder : types.keySet()){
         scratch[i++] = holder;
      }
      return size;
   }

   Holder<?>[] scratch(){
      return scratch;
   }

   /**
    * All instances of one condition type on one entity, plus the prevailing value cached by the last
    * recompute.
    */
   public static final class TypeState {
      private final ArrayList<ConditionInstance> instances = new ArrayList<>();
      private float value;
      private boolean fromStacking = true;

      TypeState(){
      }

      /** @return the cached prevailing magnitude; {@code 0} when the type is not active. */
      public float getValue(){
         return value;
      }

      /** @return {@code true} if the cached value comes from the stacked instances. */
      public boolean isFromStacking(){
         return fromStacking;
      }

      /** @return {@code true} if the cached prevailing magnitude is greater than 0. */
      public boolean isActive(){
         return value > 0.0f;
      }

      /** @return an unmodifiable view of this type's instances. */
      public List<ConditionInstance> getInstances(){
         return Collections.unmodifiableList(instances);
      }

      ArrayList<ConditionInstance> instances(){
         return instances;
      }

      void set(PrevailingCondition prevailing){
         this.value = prevailing.value();
         this.fromStacking = prevailing.fromStacking();
      }

      /**
       * Advances every timer by one tick and drops the instances that expired.
       *
       * @return {@code true} if at least one instance expired
       */
      boolean tickTimers(){
         boolean expired = false;
         for(int i = instances.size() - 1; i >= 0; i--){
            if(instances.get(i).tick()){
               instances.remove(i);
               expired = true;
            }
         }
         return expired;
      }
   }
}
