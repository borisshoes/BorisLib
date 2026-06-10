package net.borisshoes.borislib.conditions;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Defines a single <em>type</em> of condition that can be applied to a {@link LivingEntity}.
 *
 * <p>A {@code Condition} is comparable to a vanilla {@link net.minecraft.world.effect.MobEffect}, but with
 * additional granularity: many independent {@link ConditionInstance} objects of the same type can coexist on
 * one entity, and their values are combined into a single "prevailing" value (similar to how
 * {@link net.minecraft.world.entity.ai.attributes.AttributeModifier}s stack on an attribute).</p>
 *
 * <p>A {@code Condition} subclass typically:</p>
 * <ul>
 *    <li>Declares its identity, {@link MobEffectCategory category} and value range via the constructor.</li>
 *    <li>Implements {@link #onApply}, {@link #onTick} and {@link #onRemove} to react when the condition is
 *        first added, ticked while present, or fully cleared from an entity.</li>
 *    <li>Optionally overrides {@link #canApply(LivingEntity)} to gate which entities are affected.</li>
 * </ul>
 *
 * <p>Conditions must be registered via {@link net.borisshoes.borislib.BorisLib#registerCondition(Condition)}
 * during mod initialization. See {@link Conditions} for the library's built-in conditions and the static API
 * used to add/remove/query instances on entities.</p>
 */
public abstract class Condition {
   
   private final Identifier id;
   private final MobEffectCategory category;
   private final float base;
   private final float min;
   private final float max;
   private final boolean reversedImportance;
   
   /**
    * Convenience constructor for a condition where a <em>higher</em> value is more impactful
    * (equivalent to calling {@link #Condition(Identifier, MobEffectCategory, float, float, float, boolean)}
    * with {@code reversed=false}).
    *
    * @param id       unique namespaced identifier for this condition type
    * @param category beneficial / harmful / neutral classification
    * @param base     the default value used when no instances are present
    * @param min      the minimum permitted prevailing value (used by {@link #sanitizeValue(float)})
    * @param max      the maximum permitted prevailing value (used by {@link #sanitizeValue(float)})
    */
   protected Condition(Identifier id, MobEffectCategory category, float base, float min, float max){
      this(id, category, base, min, max, false);
   }
   
   /**
    * Full constructor.
    *
    * @param id       unique namespaced identifier for this condition type
    * @param category beneficial / harmful / neutral classification, also used by harm-friendly-fire checks
    * @param base     the default value used when no instances are present
    * @param min      the minimum permitted prevailing value (used by {@link #sanitizeValue(float)})
    * @param max      the maximum permitted prevailing value (used by {@link #sanitizeValue(float)})
    * @param reversed if {@code true}, lower values are treated as more "important" when picking the
    *                 prevailing non-stacking instance (e.g. for conditions where a smaller value is worse)
    */
   protected Condition(Identifier id, MobEffectCategory category, float base, float min, float max, boolean reversed){
      this.id = id;
      this.category = category;
      this.base = base;
      this.min = min;
      this.max = max;
      this.reversedImportance = reversed;
   }
   
   /**
    * Called every server tick while at least one instance of this condition is present on the entity.
    *
    * @param server    the server instance
    * @param entity    the affected entity
    * @param value     the prevailing combined value computed from all current instances
    * @param particles {@code true} if any contributing instance requested particle display
    */
   public abstract void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles);
   
   /**
    * Called once when this condition transitions from "not present" to "present" on the entity,
    * i.e. when the first {@link ConditionInstance} of this type is added.
    *
    * @param server    the server instance
    * @param entity    the affected entity
    * @param value     the prevailing combined value at the moment of application
    * @param particles {@code true} if particle display was requested
    */
   public abstract void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles);
   
   /**
    * Called once when the last {@link ConditionInstance} of this type is removed from the entity.
    * Subclasses should undo any persistent side effects (e.g. attribute modifiers) here.
    *
    * @param server    the server instance
    * @param entity    the (formerly) affected entity
    * @param value     the prevailing value that was active immediately before removal
    * @param particles {@code true} if particle display was requested
    */
   public abstract void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles);
   
   /**
    * Gates whether the condition can be applied to a given entity. Defaults to {@code true}.
    * Override to exclude certain entities (e.g. via tags).
    *
    * @param entity the candidate entity
    * @return {@code true} if {@link Conditions#addCondition} should accept new instances on this entity
    */
   public boolean canApply(LivingEntity entity){
      return true;
   }
   
   /** @return the unique namespaced identifier of this condition type. */
   public Identifier getId(){
      return id;
   }
   
   /** @return this condition's category (beneficial / harmful / neutral). */
   public MobEffectCategory getCategory(){
      return category;
   }
   
   /** @return the base (default) value reported when no instances are present. */
   public float getBase(){
      return base;
   }
   
   /** @return the minimum permitted prevailing value after sanitization. */
   public float getMin(){
      return min;
   }
   
   /** @return the maximum permitted prevailing value after sanitization. */
   public float getMax(){
      return max;
   }
   
   /**
    * @return {@code true} if a <em>lower</em> value is considered the "stronger" non-stacking instance
    *         when computing the prevailing condition value
    */
   public boolean isReversedImportance(){
      return reversedImportance;
   }
   
   /** @return {@code true} if {@link #getCategory()} equals {@link MobEffectCategory#BENEFICIAL}. */
   public boolean isBeneficial(){
      return this.category == MobEffectCategory.BENEFICIAL;
   }
   
   /** @return {@code true} if {@link #getCategory()} equals {@link MobEffectCategory#HARMFUL}. */
   public boolean isHarmful(){
      return this.category == MobEffectCategory.HARMFUL;
   }
   
   /** @return {@code true} if {@link #getCategory()} equals {@link MobEffectCategory#NEUTRAL}. */
   public boolean isNeutral(){
      return this.category == MobEffectCategory.NEUTRAL;
   }
   
   /**
    * @return the translation key for this condition's display name
    *         (format: {@code "condition.<namespace>.<path>.name"})
    */
   public String getTranslationKey(){
      return "condition." + id.getNamespace() + "." + id.getPath() + ".name";
   }
   
   /** @return a translatable component for the condition's localized display name. */
   public MutableComponent getName(){
      return Component.translatable(getTranslationKey());
   }
   
   /**
    * Builds a translatable display name that embeds the current value, using the
    * {@code "condition.<namespace>.<path>.name_value"} key.
    *
    * @param value the value to interpolate into the translation
    * @return a translatable component including the value
    */
   public MutableComponent getNameWithValue(float value){
      return Component.translatable("condition." + id.getNamespace() + "." + id.getPath() + ".name_value", value);
   }
   
   /** Two conditions are equal iff they share the same {@link #getId() identifier}. */
   @Override
   public boolean equals(Object o){
      if(this == o) return true;
      if(!(o instanceof Condition condition)) return false;
      return id.equals(condition.id);
   }
   
   @Override
   public int hashCode(){
      return id.hashCode();
   }
   
   /**
    * Clamps and validates a candidate value. {@code NaN} is replaced with the base value;
    * otherwise the value is clamped to {@code [min, max]}.
    *
    * @param d the raw value to sanitize
    * @return a safe, in-range value for this condition
    */
   public float sanitizeValue(float d){
      return Float.isNaN(d) ? this.base : Mth.clamp(d, this.min, this.max);
   }
}
