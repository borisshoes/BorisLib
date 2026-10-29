package net.borisshoes.borislib.conditions;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.ConditionData;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static net.borisshoes.borislib.conditions.AttributeCondition.Curve.*;

/**
 * Static registry and management API for {@link Condition}s applied to entities.
 *
 * <p>This class:</p>
 * <ul>
 *    <li>Declares the built-in {@link Condition}s shipped with BorisLib.</li>
 *    <li>Exposes the static API for adding, removing and querying {@link ConditionInstance}s on entities.</li>
 *    <li>Owns the single recompute path that turns every change to an entity's instances into
 *        {@link Condition#onActivate}, {@link Condition#onChange} and {@link Condition#onDeactivate}
 *        calls, and the per-entity tick that drives timers, {@link Condition#onTick} and particles.</li>
 * </ul>
 *
 * <p>State lives in one {@link EntityConditions} record per affected entity. Records are persisted through
 * {@link ConditionData} and bound to their loaded entity through {@link ConditionHolder}, so reads that
 * take a {@link LivingEntity} never touch a map. All mutators are server-side only.</p>
 */
public class Conditions {
   /** Built-in beneficial condition that increases movement speed by its magnitude × base speed. */
   public static final Holder<Condition> CELERITY = BorisLib.registerCondition(new CelerityCondition());
   /** Built-in harmful condition that deals its magnitude in magic damage per second. */
   public static final Holder<Condition> DECAY = BorisLib.registerCondition(new DecayCondition());
   /** Built-in harmful condition that attenuates the direct damage the affected entity deals. */
   public static final Holder<Condition> FEEBLE = BorisLib.registerCondition(new FeebleCondition());
   /** Built-in beneficial condition that attenuates the damage the affected entity takes. */
   public static final Holder<Condition> FORTITUDE = BorisLib.registerCondition(new FortitudeCondition());
   /** Built-in beneficial condition that amplifies the direct damage the affected entity deals. */
   public static final Holder<Condition> MIGHT = BorisLib.registerCondition(new MightCondition());
   /** Built-in beneficial condition that heals its magnitude in health per second. */
   public static final Holder<Condition> REJUVENATION = BorisLib.registerCondition(new RejuvenationCondition());
   /** Built-in harmful condition that shrinks an entity's sight radius. */
   public static final Holder<Condition> NEARSIGHT = BorisLib.registerCondition(new NearsightCondition());
   /** Built-in harmful condition that attenuates movement speed; a full root at +∞. */
   public static final Holder<Condition> TORPOR = BorisLib.registerCondition(new TorporCondition());
   /** Built-in harmful condition that amplifies the damage the affected entity takes. */
   public static final Holder<Condition> VULNERABILITY = BorisLib.registerCondition(new VulnerabilityCondition());

   /** Built-in beneficial condition that amplifies block break speed. */
   public static final Holder<Condition> ALACRITY = registerAttribute("alacrity", MobEffectCategory.BENEFICIAL, false, Attributes.BLOCK_BREAK_SPEED, AMPLIFY_BASE, 0xF5D547);
   /** Built-in harmful condition that attenuates block break speed; nothing can be mined at +∞. */
   public static final Holder<Condition> LETHARGY = registerAttribute("lethargy", MobEffectCategory.HARMFUL, true, Attributes.BLOCK_BREAK_SPEED, ATTENUATE_TOTAL, 0x7A6A1F);
   /** Built-in beneficial condition that amplifies attack speed. */
   public static final Holder<Condition> FERVOR = registerAttribute("fervor", MobEffectCategory.BENEFICIAL, false, Attributes.ATTACK_SPEED, AMPLIFY_BASE, 0xF58A42);
   /** Built-in harmful condition that attenuates attack speed. */
   public static final Holder<Condition> LASSITUDE = registerAttribute("lassitude", MobEffectCategory.HARMFUL, false, Attributes.ATTACK_SPEED, ATTENUATE_TOTAL, 0x7A4520);
   /** Built-in neutral condition that linearly reduces gravity: 1 is weightless, 2 is reversed. */
   public static final Holder<Condition> LEVITY = registerAttribute("levity", MobEffectCategory.NEUTRAL, false, Attributes.GRAVITY, LINEAR_TOTAL, 0xC9A1F5);
   /** Built-in harmful condition that amplifies gravity. */
   public static final Holder<Condition> GRAVITAS = registerAttribute("gravitas", MobEffectCategory.HARMFUL, false, Attributes.GRAVITY, AMPLIFY_BASE, 0x4F3A6B);
   /** Built-in beneficial condition that amplifies a player's block interaction range. */
   public static final Holder<Condition> SPAN = registerPlayerAttribute("span", MobEffectCategory.BENEFICIAL, false, Attributes.BLOCK_INTERACTION_RANGE, AMPLIFY_BASE, 0x8EE39A);
   /** Built-in harmful condition that attenuates a player's block interaction range; no block can be reached at +∞. */
   public static final Holder<Condition> BOUND = registerPlayerAttribute("bound", MobEffectCategory.HARMFUL, true, Attributes.BLOCK_INTERACTION_RANGE, ATTENUATE_TOTAL, 0x3A6B42);
   /** Built-in beneficial condition that amplifies a player's entity interaction range. */
   public static final Holder<Condition> LUNGE = registerPlayerAttribute("lunge", MobEffectCategory.BENEFICIAL, false, Attributes.ENTITY_INTERACTION_RANGE, AMPLIFY_BASE, 0xF56A6A);
   /** Built-in harmful condition that attenuates a player's entity interaction range; no entity can be reached at +∞. */
   public static final Holder<Condition> RESTRAINT = registerPlayerAttribute("restraint", MobEffectCategory.HARMFUL, true, Attributes.ENTITY_INTERACTION_RANGE, ATTENUATE_TOTAL, 0x7A2E2E);
   /** Built-in beneficial condition that amplifies armor, including equipment armor. */
   public static final Holder<Condition> BULWARK = registerAttribute("bulwark", MobEffectCategory.BENEFICIAL, false, Attributes.ARMOR, AMPLIFY_BASE, 0xB8C4D6);
   /** Built-in harmful condition that attenuates armor; armor is 0 at +∞. */
   public static final Holder<Condition> SUNDER = registerAttribute("sunder", MobEffectCategory.HARMFUL, true, Attributes.ARMOR, ATTENUATE_TOTAL, 0x4A5361);
   /** Built-in beneficial condition that amplifies armor toughness, including equipment toughness. */
   public static final Holder<Condition> TEMPER = registerAttribute("temper", MobEffectCategory.BENEFICIAL, false, Attributes.ARMOR_TOUGHNESS, AMPLIFY_BASE, 0x6FE0D8);
   /** Built-in harmful condition that attenuates armor toughness; toughness is 0 at +∞. */
   public static final Holder<Condition> FRACTURE = registerAttribute("fracture", MobEffectCategory.HARMFUL, true, Attributes.ARMOR_TOUGHNESS, ATTENUATE_TOTAL, 0x2C6B66);
   /** Built-in beneficial condition that resists knockback; immune at +∞. */
   public static final Holder<Condition> POISE = registerAttribute("poise", MobEffectCategory.BENEFICIAL, true, Attributes.KNOCKBACK_RESISTANCE, RESISTANCE, 0xD9B38C);
   /** Built-in harmful condition that amplifies knockback taken. */
   public static final Holder<Condition> FALTER = registerPassive("falter", MobEffectCategory.HARMFUL, false, ConditionMath.Scale.AMPLIFY, 0x6B5238);
   /** Built-in beneficial condition that resists explosion knockback; immune at +∞. */
   public static final Holder<Condition> DAMPING = registerAttribute("damping", MobEffectCategory.BENEFICIAL, true, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, RESISTANCE, 0xF58AD0);
   /** Built-in harmful condition that amplifies explosion knockback taken. */
   public static final Holder<Condition> RUPTURE = registerPassive("rupture", MobEffectCategory.HARMFUL, false, ConditionMath.Scale.AMPLIFY, 0x7A3A63);
   /** Built-in beneficial condition that amplifies healing received through {@link LivingEntity#heal}. */
   public static final Holder<Condition> VITALITY = registerPassive("vitality", MobEffectCategory.BENEFICIAL, false, ConditionMath.Scale.AMPLIFY, 0xB6F542);
   /** Built-in harmful condition that attenuates healing received through {@link LivingEntity#heal}; none at +∞. */
   public static final Holder<Condition> BLIGHT = registerPassive("blight", MobEffectCategory.HARMFUL, true, ConditionMath.Scale.ATTENUATE, 0x566B1F);
   /**
    * Built-in beneficial condition that attenuates the range at which mobs detect the entity. Vanilla
    * floors that range at 2 blocks, so even +∞ is noticed up close. It only affects acquiring a target:
    * retaliation ignores visibility, and a target that is already acquired is kept within follow range.
    */
   public static final Holder<Condition> OBSCURITY = registerPassive("obscurity", MobEffectCategory.BENEFICIAL, true, ConditionMath.Scale.ATTENUATE, 0x6A7BF5);
   /** Built-in harmful condition that amplifies the range at which mobs detect the entity. */
   public static final Holder<Condition> EXPOSURE = registerPassive("exposure", MobEffectCategory.HARMFUL, false, ConditionMath.Scale.AMPLIFY, 0x2E377A);

   static {
      // Every built-in condition has an icon at assets/borislib/textures/item/gui/<path>.png
      for(Condition condition : BorisLib.CONDITIONS){
         if(!condition.getId().getNamespace().equals(BorisLib.MOD_ID)) continue;
         BorisLib.registerGraphicItem(new GraphicalItem.GraphicElement(condition.getId(), switch(condition.getCategory()){
            case BENEFICIAL -> Items.DYE.lime();
            case HARMFUL -> Items.DYE.red();
            case NEUTRAL -> Items.DYE.yellow();
         }, false));
      }
   }

   private static ConditionData data;
   private static boolean dirty;

   /**
    * Forces class loading, which triggers all static field initializers above to run.
    * Must be called during mod initialization to ensure conditions are registered
    * before anything queries the CONDITIONS registry.
    */
   public static void initialize(){
   }

   private static Holder<Condition> registerAttribute(String path, MobEffectCategory category, boolean infinite, Holder<Attribute> attribute, AttributeCondition.Curve curve, int color){
      return BorisLib.registerCondition(new SimpleAttributeCondition(builtinId(path), category, builtinCap(infinite), attribute, curve, builtinParticles(color), false));
   }

   private static Holder<Condition> registerPlayerAttribute(String path, MobEffectCategory category, boolean infinite, Holder<Attribute> attribute, AttributeCondition.Curve curve, int color){
      return BorisLib.registerCondition(new SimpleAttributeCondition(builtinId(path), category, builtinCap(infinite), attribute, curve, builtinParticles(color), true));
   }

   private static Holder<Condition> registerPassive(String path, MobEffectCategory category, boolean infinite, ConditionMath.Scale scale, int color){
      return BorisLib.registerCondition(new PassiveCondition(builtinId(path), category, builtinCap(infinite), scale, builtinParticles(color)));
   }

   private static Identifier builtinId(String path){
      return Identifier.fromNamespaceAndPath(BorisLib.MOD_ID, path);
   }

   private static float builtinCap(boolean infinite){
      return infinite ? Float.POSITIVE_INFINITY : Float.MAX_VALUE;
   }

   private static ConditionParticles builtinParticles(int color){
      return ConditionParticles.of(new DustParticleOptions(color, 0.75f), 2.0f, 10, 0.01f);
   }

   /**
    * @return the global {@link ConditionData} store that holds every entity's condition record. The
    *         reference is cached while the server runs.
    */
   public static ConditionData getConditionData(){
      if(data == null) data = DataAccess.getGlobal(ConditionData.KEY);
      return data;
   }

   // ------------------------------------------------------------------------------------------------
   // Queries
   // ------------------------------------------------------------------------------------------------

   /**
    * Fast path for the prevailing magnitude of a condition on a loaded entity: an O(1) read from the
    * entity's bound record.
    *
    * @param entity the affected entity
    * @param holder the condition type
    * @return the prevailing magnitude, or {@code 0} if the condition is not active
    */
   public static float getConditionValue(LivingEntity entity, Holder<Condition> holder){
      EntityConditions record = ((ConditionHolder) entity).borislib$getConditions();
      return record == null ? 0.0f : record.getValue(holder);
   }

   /**
    * Prevailing magnitude of a condition on an entity that may not be loaded. This is a map lookup; prefer
    * {@link #getConditionValue(LivingEntity, Holder)} when the entity is at hand.
    *
    * @param entityId the affected entity's UUID
    * @param holder   the condition type
    * @return the prevailing magnitude, or {@code 0} if the condition is not active
    */
   public static float getConditionValue(UUID entityId, Holder<Condition> holder){
      EntityConditions record = data == null ? null : data.get(entityId);
      return record == null ? 0.0f : record.getValue(holder);
   }

   /**
    * @param entity the affected entity
    * @param holder the condition type
    * @return the prevailing state of the condition; its value is {@code 0} when nothing is in effect
    */
   public static PrevailingCondition getPrevailingCondition(LivingEntity entity, Holder<Condition> holder){
      EntityConditions record = ((ConditionHolder) entity).borislib$getConditions();
      EntityConditions.TypeState state = record == null ? null : record.getState(holder);
      return state == null ? PrevailingCondition.NONE : new PrevailingCondition(state.getValue(), state.isFromStacking());
   }

   /**
    * @param entity the affected entity
    * @param holder the condition type
    * @return {@code true} if the condition's prevailing magnitude on the entity is greater than 0
    */
   public static boolean isActive(LivingEntity entity, Holder<Condition> holder){
      return getConditionValue(entity, holder) > 0.0f;
   }

   /**
    * Looks up a specific instance by its {@code (condition, id)} pair.
    *
    * @param entityId the entity UUID
    * @param holder   the condition type
    * @param id       the per-source identifier
    * @return the matching instance, or {@code null} if no such instance exists
    */
   public static ConditionInstance getConditionInstance(UUID entityId, Holder<Condition> holder, Identifier id){
      EntityConditions record = data == null ? null : data.get(entityId);
      EntityConditions.TypeState state = record == null ? null : record.getState(holder);
      if(state == null) return null;
      int index = indexOf(state.instances(), id);
      return index < 0 ? null : state.instances().get(index);
   }

   /**
    * @param entityId the entity UUID
    * @return an unmodifiable copy of every {@link ConditionInstance} currently on the entity (all types)
    */
   public static List<ConditionInstance> getConditionInstances(UUID entityId){
      EntityConditions record = data == null ? null : data.get(entityId);
      return record == null ? List.of() : Collections.unmodifiableList(record.getAllInstances());
   }

   /**
    * @param entityId the entity UUID
    * @param holder   the condition type to filter by
    * @return an unmodifiable copy of every instance of the given condition type currently on the entity
    */
   public static List<ConditionInstance> getConditionInstancesOf(UUID entityId, Holder<Condition> holder){
      EntityConditions record = data == null ? null : data.get(entityId);
      EntityConditions.TypeState state = record == null ? null : record.getState(holder);
      return state == null ? List.of() : List.copyOf(state.instances());
   }

   // ------------------------------------------------------------------------------------------------
   // Mutators
   // ------------------------------------------------------------------------------------------------

   /** Outcome of {@link #addCondition} and {@link #addConditionIfStronger}. */
   public enum AddResult {
      /** The instance was added as a new {@code (condition, id)} pair. */
      ADDED,
      /** An existing instance with the same {@code (condition, id)} pair was replaced. */
      REPLACED,
      /** Nothing changed: the entity cannot take the condition, or the existing instance was stronger. */
      REJECTED
   }

   /**
    * Adds a {@link ConditionInstance} to an entity.
    *
    * <p>Behavior:</p>
    * <ul>
    *    <li>Returns {@link AddResult#REJECTED} if {@link Condition#canApply(LivingEntity)} rejects the
    *        entity.</li>
    *    <li>For harmful conditions, returns {@link AddResult#REJECTED} when the inflicting entity is an
    *        ally of or cannot harm the target (e.g. PvP-protected players).</li>
    *    <li>If an instance with the same {@code (condition, id)} pair already exists it is replaced in
    *        place and {@link AddResult#REPLACED} is returned.</li>
    *    <li>Otherwise the instance is appended, its particle burst is queued and {@link AddResult#ADDED}
    *        is returned.</li>
    * </ul>
    * <p>Either way the prevailing value is recomputed, firing {@link Condition#onActivate},
    * {@link Condition#onChange} or {@link Condition#onDeactivate} as it crosses or moves above 0.</p>
    *
    * @param server   the server context
    * @param entity   the entity to affect
    * @param instance the instance to add or replace
    * @return what happened to the instance
    */
   public static AddResult addCondition(MinecraftServer server, LivingEntity entity, ConditionInstance instance){
      return addCondition(server, entity, instance, false);
   }

   /**
    * Like {@link #addCondition}, but an existing instance with the same {@code (condition, id)} pair is
    * only replaced when the new value is greater than or equal to the old one. Use it for exclusivity
    * groups that share one id, so a weaker source never overwrites a stronger one.
    *
    * @param server   the server context
    * @param entity   the entity to affect
    * @param instance the instance to add or replace
    * @return what happened to the instance; {@link AddResult#REJECTED} if the existing one was stronger
    */
   public static AddResult addConditionIfStronger(MinecraftServer server, LivingEntity entity, ConditionInstance instance){
      return addCondition(server, entity, instance, true);
   }

   private static AddResult addCondition(MinecraftServer server, LivingEntity entity, ConditionInstance instance, boolean onlyIfStronger){
      if(!(entity.level() instanceof ServerLevel level)) return AddResult.REJECTED;
      Holder<Condition> holder = instance.getCondition();
      Condition condition = holder.value();
      if(!condition.canApply(entity)) return AddResult.REJECTED;
      if(condition.isHarmful() && instance.getInflictedBy() != null){
         LivingEntity inflicter = findInflicter(server, level, instance.getInflictedBy());
         if(inflicter != null){
            if(inflicter.isAlliedTo(entity)) return AddResult.REJECTED;
            if(entity instanceof ServerPlayer player && inflicter instanceof ServerPlayer otherPlayer && !otherPlayer.canHarmPlayer(player)){
               return AddResult.REJECTED;
            }
         }
      }

      ConditionData store = getConditionData();
      EntityConditions record = resolveRecord(store, entity);
      EntityConditions.TypeState existingState = record == null ? null : record.getState(holder);
      int index = existingState == null ? -1 : indexOf(existingState.instances(), instance.getId());
      if(index >= 0 && onlyIfStronger && instance.getValue() < existingState.instances().get(index).getValue()){
         return AddResult.REJECTED;
      }

      if(record == null){
         record = store.getOrCreate(entity.getUUID());
         record.markSeen(level.getGameTime(), entity instanceof Player);
         ((ConditionHolder) entity).borislib$setConditions(record);
      }
      EntityConditions.TypeState state = record.getOrCreateState(holder);
      AddResult result;
      if(index >= 0){
         ConditionInstance old = state.instances().set(index, instance);
         if(!old.getParticles().equals(instance.getParticles())) record.emitter().markChanged();
         result = AddResult.REPLACED;
      }else{
         state.instances().add(instance);
         record.emitter().markChanged();
         record.emitter().queueBurst(instance.getParticles().orElseGet(condition::getDefaultParticles));
         result = AddResult.ADDED;
      }
      dirty = true;
      recompute(server, entity, record, holder);
      return result;
   }

   /**
    * Removes every condition (of every type) from an entity, firing {@link Condition#onDeactivate} once
    * per active condition type.
    *
    * @param server the server context
    * @param entity the entity whose conditions are cleared
    */
   public static void removeAllConditions(MinecraftServer server, LivingEntity entity){
      ConditionData store = data;
      EntityConditions record = store == null ? null : resolveRecord(store, entity);
      if(record == null) return;
      for(Holder<Condition> holder : record.getTypes()){
         EntityConditions.TypeState state = record.getState(holder);
         if(state == null) continue;
         state.instances().clear();
         recompute(server, entity, record, holder);
      }
      dropRecord(store, entity, record);
      dirty = true;
   }

   /**
    * Removes every instance of one condition type from an entity, firing {@link Condition#onDeactivate}
    * if the condition was active.
    *
    * @param server the server context
    * @param entity the entity to update
    * @param holder the condition type to clear
    * @return the number of instances removed
    */
   public static int removeConditions(MinecraftServer server, LivingEntity entity, Holder<Condition> holder){
      EntityConditions record = data == null ? null : resolveRecord(data, entity);
      EntityConditions.TypeState state = record == null ? null : record.getState(holder);
      if(state == null) return 0;
      int removed = state.instances().size();
      state.instances().clear();
      record.emitter().markChanged();
      dirty = true;
      recompute(server, entity, record, holder);
      return removed;
   }

   /**
    * Removes every instance, of any type, that matches a predicate. A typical cleanse removes instances
    * that are harmful and not {@link ConditionInstance#isPersistent() persistent}.
    *
    * @param server    the server context
    * @param entity    the entity to update
    * @param predicate selects the instances to remove
    * @return the number of instances removed
    */
   public static int removeConditionsIf(MinecraftServer server, LivingEntity entity, Predicate<ConditionInstance> predicate){
      EntityConditions record = data == null ? null : resolveRecord(data, entity);
      if(record == null) return 0;
      int removed = 0;
      for(Holder<Condition> holder : record.getTypes()){
         EntityConditions.TypeState state = record.getState(holder);
         if(state == null) continue;
         int before = state.instances().size();
         if(!state.instances().removeIf(predicate)) continue;
         removed += before - state.instances().size();
         record.emitter().markChanged();
         dirty = true;
         recompute(server, entity, record, holder);
      }
      return removed;
   }

   /**
    * Removes a specific instance identified by its {@code (condition, id)} pair.
    *
    * @param server the server context
    * @param entity the entity to update
    * @param holder the condition type
    * @param id     the per-source identifier of the instance to remove
    * @return {@code true} if a matching instance was found and removed
    */
   public static boolean removeCondition(MinecraftServer server, LivingEntity entity, Holder<Condition> holder, Identifier id){
      EntityConditions record = data == null ? null : resolveRecord(data, entity);
      EntityConditions.TypeState state = record == null ? null : record.getState(holder);
      if(state == null) return false;
      int index = indexOf(state.instances(), id);
      if(index < 0) return false;
      state.instances().remove(index);
      record.emitter().markChanged();
      dirty = true;
      recompute(server, entity, record, holder);
      return true;
   }

   /**
    * The single path through which a condition type's prevailing value changes. Every mutation calls it
    * after editing the instance list. The new value is stored before any hook fires, so hooks may freely
    * call back into the mutators.
    */
   private static void recompute(MinecraftServer server, LivingEntity entity, EntityConditions record, Holder<Condition> holder){
      EntityConditions.TypeState state = record.getState(holder);
      if(state == null) return;
      Condition condition = holder.value();
      float old = state.getValue();
      state.set(ConditionMath.combine(state.instances(), condition));
      float now = state.getValue();

      if(old <= 0.0f && now > 0.0f){
         record.emitter().markChanged();
         condition.onActivate(server, entity, now);
      }else if(old > 0.0f && now <= 0.0f){
         record.emitter().markChanged();
         condition.onDeactivate(server, entity, old);
      }else if(old != now){
         condition.onChange(server, entity, old, now);
      }

      if(state.instances().isEmpty() && record.getState(holder) == state) record.types().remove(holder);
      if(record.isEmpty() && data != null) dropRecord(data, entity, record);
   }

   private static void dropRecord(ConditionData store, LivingEntity entity, EntityConditions record){
      if(store.get(entity.getUUID()) == record) store.removeEntity(entity.getUUID());
      ConditionHolder bound = (ConditionHolder) entity;
      if(bound.borislib$getConditions() == record) bound.borislib$setConditions(null);
   }

   /** The record bound to the entity, falling back to the store for an entity that was never bound. */
   private static EntityConditions resolveRecord(ConditionData store, LivingEntity entity){
      ConditionHolder bound = (ConditionHolder) entity;
      EntityConditions record = bound.borislib$getConditions();
      if(record == null && !entity.level().isClientSide()){
         record = store.get(entity.getUUID());
         if(record != null) bound.borislib$setConditions(record);
      }
      return record;
   }

   private static int indexOf(ArrayList<ConditionInstance> instances, Identifier id){
      for(int i = 0; i < instances.size(); i++){
         if(instances.get(i).getId().equals(id)) return i;
      }
      return -1;
   }

   private static LivingEntity findInflicter(MinecraftServer server, ServerLevel level, UUID inflictedBy){
      // Auras reapply every tick, so look in the target's own level before scanning the others
      if(level.getEntity(inflictedBy) instanceof LivingEntity living) return living;
      for(ServerLevel other : server.getAllLevels()){
         if(other != level && other.getEntity(inflictedBy) instanceof LivingEntity living) return living;
      }
      return null;
   }

   // ------------------------------------------------------------------------------------------------
   // Ticking and hooks used by the mixins
   // ------------------------------------------------------------------------------------------------

   /**
    * Per-entity condition tick, called from the tail of {@link LivingEntity#baseTick()} for server-side
    * entities that have a bound record. Advances timers, expires instances, fires
    * {@link Condition#onTick} for each active type and emits particles.
    *
    * @param entity the ticking entity
    * @param record the record bound to the entity
    */
   public static void tickEntity(LivingEntity entity, EntityConditions record){
      if(!(entity.level() instanceof ServerLevel level)) return;
      MinecraftServer server = level.getServer();
      if(!server.tickRateManager().runsNormally()) return;
      ConditionHolder bound = (ConditionHolder) entity;
      dirty = true;
      try{
         int count = record.snapshot();
         for(int i = 0; i < count; i++){
            @SuppressWarnings("unchecked")
            Holder<Condition> holder = (Holder<Condition>) record.scratch()[i];
            EntityConditions.TypeState state = record.getState(holder);
            if(state == null) continue;
            if(state.tickTimers()){
               record.emitter().markChanged();
               recompute(server, entity, record, holder);
            }
            // An earlier hook may have killed or cleansed the entity
            if(bound.borislib$getConditions() != record) return;
            if(record.getState(holder) != state || !state.isActive()) continue;
            holder.value().onTick(server, entity, state.getValue());
            if(bound.borislib$getConditions() != record) return;
         }
         record.emitter().tick(level, entity, record);
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to tick conditions for entity {}", entity.getUUID(), e);
      }
   }

   /**
    * Computes the factor conditions apply to a hit: {@code ratio(vulnerability, fortitude)} on the hurt
    * entity times {@code ratio(might, feeble)} on a direct living attacker, with attenuation winning over
    * amplification.
    *
    * @param hurt   the entity taking damage
    * @param source the damage source
    * @return {@code 1} when conditions do not alter the hit, {@code 0} when the hit is negated entirely
    */
   public static float damageFactor(LivingEntity hurt, DamageSource source){
      EntityConditions hurtRecord = ((ConditionHolder) hurt).borislib$getConditions();
      EntityConditions dealerRecord = source.isDirect() && source.getDirectEntity() instanceof LivingEntity dealer ? ((ConditionHolder) dealer).borislib$getConditions() : null;
      if(hurtRecord == null && dealerRecord == null) return 1.0f;
      // Keeps /kill and the void working on invulnerable entities
      if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return 1.0f;
      float incoming = hurtRecord == null ? 1.0f : ConditionMath.ratio(hurtRecord.getValue(VULNERABILITY), hurtRecord.getValue(FORTITUDE));
      float outgoing = dealerRecord == null ? 1.0f : ConditionMath.ratio(dealerRecord.getValue(MIGHT), dealerRecord.getValue(FEEBLE));
      return ConditionMath.multiplyFactors(incoming, outgoing);
   }

   // ------------------------------------------------------------------------------------------------
   // Lifecycle
   // ------------------------------------------------------------------------------------------------

   /**
    * Server-start hook: caches the {@link ConditionData} reference and binds every already-loaded entity
    * to its record.
    *
    * @param server the freshly started server
    */
   public static void onServerStarted(MinecraftServer server){
      data = DataAccess.getGlobal(ConditionData.KEY);
      dirty = false;
      for(ServerLevel level : server.getAllLevels()){
         for(Entity entity : level.getAllEntities()){
            onEntityLoad(entity, level);
         }
      }
      int days = BorisLib.CONFIG == null ? 0 : BorisLib.CONFIG.getInt(BorisLib.CONDITION_RECORD_EXPIRY_DAYS);
      if(days > 0){
         int pruned = pruneRecords(server, days);
         if(pruned > 0) BorisLib.LOGGER.info("Pruned condition records of {} entities not seen for {} days", pruned, days);
      }
   }

   /**
    * Drops the condition records of non-player entities that have not been loaded for at least {@code days}
    * (in game time, which every dimension shares), e.g. mobs in chunks that were deleted. Off by default because such an entity, if it
    * ever came back, would otherwise resume its paused timers.
    *
    * <p>Player records are never removed. A record is kept if it has ever been bound to a player, if its UUID
    * belongs to anyone with BorisLib or vanilla player data, or if its entity is currently loaded. Records saved
    * before last-seen tracking existed start counting from now. No hooks fire, as for any destroyed entity.</p>
    *
    * @param server the server
    * @param days   minimum age in in-game days (24000 ticks); 0 or less does nothing
    * @return the number of records removed
    */
   public static int pruneRecords(MinecraftServer server, int days){
      if(days <= 0) return 0;
      ConditionData store = getConditionData();
      long now = server.overworld().getGameTime();
      long maxAge = days * 24000L;
      Set<UUID> players = DataAccess.knownPlayerUuids();
      players.addAll(vanillaPlayerUuids(server));

      List<UUID> expired = new ArrayList<>();
      for(var entry : store.getAllConditions().entrySet()){
         UUID uuid = entry.getKey();
         EntityConditions record = entry.getValue();
         if(record.isPlayer() || players.contains(uuid)) continue;
         if(record.getLastSeen() < 0){
            // Saved before last-seen tracking existed: start the clock now
            record.markSeen(now, false);
            dirty = true;
            continue;
         }
         if(now - record.getLastSeen() < maxAge) continue;
         if(MinecraftUtils.findLivingEntity(server, uuid) != null) continue;
         expired.add(uuid);
      }
      for(UUID uuid : expired){
         store.removeEntity(uuid);
      }
      if(!expired.isEmpty()) dirty = true;
      return expired.size();
   }

   private static Set<UUID> vanillaPlayerUuids(MinecraftServer server){
      Set<UUID> uuids = new HashSet<>();
      try(var files = Files.list(server.getWorldPath(LevelResource.PLAYER_DATA_DIR))){
         files.forEach(path -> {
            String name = path.getFileName().toString();
            int dot = name.indexOf('.');
            try{
               uuids.add(UUID.fromString(dot < 0 ? name : name.substring(0, dot)));
            }catch(IllegalArgumentException ignored){
            }
         });
      }catch(Exception e){
         BorisLib.LOGGER.warn("Could not list vanilla player data while pruning condition records: {}", e.getMessage());
      }
      return uuids;
   }

   /**
    * Server-stop hook: drops the cached {@link ConditionData} reference and every condition boss bar.
    *
    * @param server the server shutting down
    */
   public static void onServerStopped(MinecraftServer server){
      data = null;
      dirty = false;
      ConditionBossBars.onServerStopped();
   }

   /**
    * End-of-server-tick hook: marks the global state dirty at most once per tick if any condition changed.
    *
    * @param server the server instance being ticked
    */
   public static void flushDirty(MinecraftServer server){
      if(!dirty) return;
      dirty = false;
      DataAccess.markGlobalDirty();
   }

   /**
    * Entity-load hook: binds the entity to its stored record and fires {@link Condition#onEntityLoad} for
    * each active type, which reattaches transient attribute modifiers after a relog or chunk reload.
    *
    * @param entity the entity that started being tracked
    * @param level  the level it was loaded into
    */
   public static void onEntityLoad(Entity entity, ServerLevel level){
      if(data == null || !(entity instanceof LivingEntity living)) return;
      EntityConditions record = data.get(entity.getUUID());
      if(record == null) return;
      MinecraftServer server = level.getServer();
      ((ConditionHolder) living).borislib$setConditions(record);
      record.markSeen(level.getGameTime(), living instanceof Player);
      record.emitter().markChanged();
      for(Holder<Condition> holder : record.getTypes()){
         EntityConditions.TypeState state = record.getState(holder);
         if(state == null || !state.isActive()) continue;
         try{
            holder.value().onEntityLoad(server, living, state.getValue());
         }catch(Exception e){
            BorisLib.LOGGER.error("Failed to reattach condition {} to entity {}", holder.value().getId(), entity.getUUID(), e);
         }
      }
   }

   /**
    * Entity-unload hook: unbinds the entity from its record. If the entity was destroyed rather than
    * merely unloaded, its record is dropped without firing hooks; otherwise the record stays stored with
    * its timers paused.
    *
    * @param entity the entity that stopped being tracked
    * @param level  the level it was unloaded from
    */
   public static void onEntityUnload(Entity entity, ServerLevel level){
      if(!(entity instanceof LivingEntity living)) return;
      ConditionHolder bound = (ConditionHolder) living;
      EntityConditions record = bound.borislib$getConditions();
      if(record == null) return;
      record.markSeen(level.getGameTime(), living instanceof Player);
      bound.borislib$setConditions(null);
      Entity.RemovalReason reason = entity.getRemovalReason();
      if(reason != null && reason.shouldDestroy() && data != null && data.get(entity.getUUID()) == record){
         data.removeEntity(entity.getUUID());
         dirty = true;
      }
   }

   /**
    * Event hook fired when a {@link LivingEntity} dies; clears all condition instances on the deceased
    * entity (firing {@link Condition#onDeactivate} as needed).
    *
    * @param entity       the entity that died
    * @param damageSource the cause of death (currently unused)
    */
   public static void entityDied(LivingEntity entity, DamageSource damageSource){
      removeAllConditions(entity.level().getServer(), entity);
   }
}
