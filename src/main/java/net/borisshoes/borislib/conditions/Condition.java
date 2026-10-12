package net.borisshoes.borislib.conditions;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Defines a single <em>type</em> of condition that can be applied to a {@link LivingEntity}.
 *
 * <p>A {@code Condition} is comparable to a vanilla {@link net.minecraft.world.effect.MobEffect}, but with
 * additional granularity: many independent {@link ConditionInstance} objects of the same type can coexist on
 * one entity, and their values are combined into a single "prevailing" value (similar to how
 * {@link net.minecraft.world.entity.ai.attributes.AttributeModifier}s stack on an attribute).</p>
 *
 * <p>The prevailing value is a <em>magnitude</em> in {@code [0, cap]}. {@code 0} means the condition is
 * absent, larger is always stronger in the condition's own direction, and {@code +∞} is available to
 * conditions that pass {@link Float#POSITIVE_INFINITY} as their cap. A condition is <em>active</em> while
 * its prevailing magnitude is greater than 0. See {@link ConditionMath} for the curves that turn a
 * magnitude into a multiplier, and for how instances can stack in percent of the effect instead of in
 * magnitude.</p>
 *
 * <p>A {@code Condition} subclass typically:</p>
 * <ul>
 *    <li>Declares its identity, {@link MobEffectCategory category} and cap via the constructor.</li>
 *    <li>Implements {@link #onTick} and overrides {@link #onActivate}, {@link #onChange},
 *        {@link #onDeactivate} and {@link #onEntityLoad} to react as the condition becomes active, changes
 *        strength, stops being active, or is reattached to a freshly loaded entity.</li>
 *    <li>Optionally overrides {@link #canApply(LivingEntity)} to gate which entities are affected and
 *        {@link #getDefaultParticles()} to show particles.</li>
 * </ul>
 *
 * <p>All hooks fire server-side only, and may call any {@link Conditions} mutator on any entity.</p>
 *
 * <p>Conditions must be registered via {@link net.borisshoes.borislib.BorisLib#registerCondition(Condition)}
 * during mod initialization. See {@link Conditions} for the library's built-in conditions and the static API
 * used to add/remove/query instances on entities.</p>
 */
public abstract class Condition {

   private final Identifier id;
   private final MobEffectCategory category;
   private final float cap;

   /**
    * Constructor for a condition with a finite magnitude; its cap is {@link Float#MAX_VALUE} and infinite
    * inputs clamp to it.
    *
    * @param id       unique namespaced identifier for this condition type
    * @param category beneficial / harmful / neutral classification, also used by harm-friendly-fire checks
    */
   protected Condition(Identifier id, MobEffectCategory category){
      this(id, category, Float.MAX_VALUE);
   }

   /**
    * Full constructor.
    *
    * @param id       unique namespaced identifier for this condition type
    * @param category beneficial / harmful / neutral classification, also used by harm-friendly-fire checks
    * @param cap      the largest permitted prevailing magnitude, in {@code (0, +∞]}; passing
    *                 {@link Float#POSITIVE_INFINITY} opts in to infinite magnitudes
    * @throws IllegalArgumentException if {@code cap} is not greater than 0 or is {@code NaN}
    */
   protected Condition(Identifier id, MobEffectCategory category, float cap){
      if(!(cap > 0.0f)) throw new IllegalArgumentException("Condition cap must be in (0, +inf], got " + cap);
      this.id = id;
      this.category = category;
      this.cap = cap;
   }

   /**
    * Called every entity tick while this condition is active, after expired instances have been removed.
    *
    * @param server the server instance
    * @param entity the affected entity
    * @param value  the prevailing magnitude, always greater than 0
    */
   public abstract void onTick(MinecraftServer server, LivingEntity entity, float value);

   /**
    * Called when the prevailing magnitude goes from 0 to above 0, for any cause.
    *
    * @param server the server instance
    * @param entity the affected entity
    * @param value  the new prevailing magnitude
    */
   public void onActivate(MinecraftServer server, LivingEntity entity, float value){
   }

   /**
    * Called while this condition is active when the prevailing magnitude changes to another value above 0.
    *
    * @param server   the server instance
    * @param entity   the affected entity
    * @param oldValue the previous prevailing magnitude
    * @param newValue the new prevailing magnitude
    */
   public void onChange(MinecraftServer server, LivingEntity entity, float oldValue, float newValue){
   }

   /**
    * Called when the prevailing magnitude goes from above 0 to 0, for any cause: expiry, removal,
    * cancelling instances, a clear, or death. Subclasses should undo any lasting side effects (e.g.
    * attribute modifiers) here.
    *
    * @param server    the server instance
    * @param entity    the (formerly) affected entity
    * @param lastValue the prevailing magnitude that was active immediately before
    */
   public void onDeactivate(MinecraftServer server, LivingEntity entity, float lastValue){
   }

   /**
    * Called when an entity that already has this condition active is loaded (chunk load, join, respawn or
    * dimension change). Subclasses should reattach anything that does not survive a reload, such as
    * transient attribute modifiers.
    *
    * @param server the server instance
    * @param entity the freshly loaded entity
    * @param value  the prevailing magnitude
    */
   public void onEntityLoad(MinecraftServer server, LivingEntity entity, float value){
   }

   /**
    * The particles shown for instances of this condition that do not carry their own spec. Resolved every
    * time particles are pooled, so changing the returned value also changes existing instances.
    *
    * @return this condition's particle spec; {@link ConditionParticles#NONE} by default
    */
   public ConditionParticles getDefaultParticles(){
      return ConditionParticles.NONE;
   }

   /**
    * How this condition's magnitude turns into its effect, which decides what instances using
    * {@code ADD_MULTIPLIED_BASE} and {@code ADD_MULTIPLIED_TOTAL} mean. Override to return
    * {@link ConditionMath.Scale#AMPLIFY} if the effect is multiplied by {@code 1 + m}, or
    * {@link ConditionMath.Scale#ATTENUATE} if it is multiplied by {@code 1 / (1 + m)}; such conditions can
    * then be stacked in percent of their effect as well as in magnitude.
    *
    * @return this condition's scale; {@link ConditionMath.Scale#LINEAR} by default, where percentages
    *         scale the magnitude like vanilla attribute modifiers
    */
   public ConditionMath.Scale getScale(){
      return ConditionMath.Scale.LINEAR;
   }

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

   /** @return the largest permitted prevailing magnitude; {@code +∞} if {@link #supportsInfinity()}. */
   public float getCap(){
      return cap;
   }

   /** @return {@code true} if this condition accepts an infinite magnitude. */
   public boolean supportsInfinity(){
      return cap == Float.POSITIVE_INFINITY;
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
    * Builds a translatable display name that embeds the current magnitude, using the
    * {@code "condition.<namespace>.<path>.name_value"} key. An infinite magnitude renders as {@code ∞}.
    *
    * @param value the magnitude to interpolate into the translation
    * @return a translatable component including the value
    */
   public MutableComponent getNameWithValue(float value){
      Object shown = value == Float.POSITIVE_INFINITY ? "∞" : (Object) value;
      return Component.translatable("condition." + id.getNamespace() + "." + id.getPath() + ".name_value", shown);
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
    * Clamps and validates a candidate magnitude. {@code NaN} is replaced with 0; otherwise the value is
    * clamped to {@code [0, cap]}.
    *
    * @param d the raw value to sanitize
    * @return a safe, in-range magnitude for this condition
    */
   public float sanitizeValue(float d){
      if(Float.isNaN(d) || d <= 0.0f) return 0.0f;
      return Math.min(d, this.cap);
   }
}
