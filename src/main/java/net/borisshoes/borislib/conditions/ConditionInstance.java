package net.borisshoes.borislib.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Optional;
import java.util.UUID;

/**
 * A single, stackable instance of a {@link Condition} applied to an entity.
 *
 * <p>Multiple {@code ConditionInstance}s of the same {@link Condition} type can coexist on one entity; each
 * carries its own value, duration, stacking behavior and source. The {@link Conditions} manager combines all
 * current instances into a single "prevailing" magnitude that is what subclasses of {@link Condition}
 * actually react to (see {@link ConditionMath#combine}).</p>
 *
 * <p>Each instance is uniquely identified by the tuple ({@link #getCondition() condition},
 * {@link #getId() id}); adding a new instance with an identical pair to an existing one replaces it in
 * place. Use a distinct {@link Identifier} for each independent source so they can coexist and be removed
 * individually, or share one identifier between sources that should be mutually exclusive.</p>
 *
 * <p>The {@link #getOperation() operation} says what the value is. {@code ADD_VALUE} is a magnitude.
 * {@code ADD_MULTIPLIED_BASE} and {@code ADD_MULTIPLIED_TOTAL} are percentages of the condition's effect
 * (for example {@code 0.15} for 15% damage resistance on Fortitude) that stack additively and by
 * compounding respectively; see {@link ConditionMath.Scale}.</p>
 *
 * <p>A value may be {@code +∞} or a finite negative number, but never {@code NaN} or {@code -∞}. Infinity
 * is absorbing: a stacking {@code -100%} multiplier cannot cancel an infinite magnitude, only removing the
 * infinite instance can. Whether an infinite value survives is decided by the condition's
 * {@link Condition#getCap() cap} when the instances are combined.</p>
 */
public class ConditionInstance implements ConditionMath.Term {

   /** Duration of an instance that never expires from its timer. */
   public static final int INFINITE_DURATION = -1;

   /**
    * Codec used to persist condition instances to NBT via mojang's data fixer framework. Both the condition
    * type and the inflicting entity UUID are serialized. Entries that fail constructor validation decode to
    * an error instead of throwing. Never use this with {@code JsonOps}: Gson rejects infinite values.
    */
   public static final Codec<ConditionInstance> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.<Raw>create(i -> i.group(
         BorisLib.CONDITIONS.holderByNameCodec().fieldOf("condition").forGetter(Raw::condition),
         Identifier.CODEC.fieldOf("id").forGetter(Raw::id),
         Codec.INT.fieldOf("duration").forGetter(Raw::duration),
         Codec.FLOAT.fieldOf("value").forGetter(Raw::value),
         Codec.BOOL.fieldOf("stacking").forGetter(Raw::stacking),
         ConditionParticles.CODEC.optionalFieldOf("particles").forGetter(Raw::particles),
         AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(Raw::operation),
         Codec.INT.fieldOf("timer").forGetter(Raw::timer),
         Codec.BOOL.fieldOf("persistent").forGetter(Raw::persistent),
         CodecUtils.UUID_CODEC.optionalFieldOf("inflictedBy").forGetter(Raw::inflictedBy),
         ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Raw::name)
   ).apply(i, Raw::new)).flatXmap(Raw::toInstance, inst -> DataResult.success(Raw.of(inst))));

   private record Raw(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, Optional<ConditionParticles> particles, AttributeModifier.Operation operation, int timer, boolean persistent, Optional<UUID> inflictedBy, Optional<Component> name) {
      private static Raw of(ConditionInstance inst){
         return new Raw(inst.condition, inst.id, inst.duration, inst.value, inst.stacking, inst.particles, inst.operation, inst.timer, inst.persistent, Optional.ofNullable(inst.inflictedBy), Optional.ofNullable(inst.name));
      }

      private DataResult<ConditionInstance> toInstance(){
         try{
            ConditionInstance inst = new ConditionInstance(condition, id, duration, value, stacking, particles, persistent, operation, inflictedBy.orElse(null), name.orElse(null));
            inst.timer = timer;
            return DataResult.success(inst);
         }catch(IllegalArgumentException e){
            return DataResult.error(() -> "Invalid condition instance " + id + ": " + e.getMessage());
         }
      }
   }

   private final Holder<Condition> condition;
   private final Identifier id;
   private final float value;
   private final boolean stacking;
   private final Optional<ConditionParticles> particles;
   private final AttributeModifier.Operation operation;
   private final int duration;
   private final boolean persistent;
   private final UUID inflictedBy;
   private final Component name;
   private int timer;

   /**
    * Convenience constructor that defaults to a stacking, non-persistent instance showing the condition's
    * default particles and using {@link AttributeModifier.Operation#ADD_VALUE}.
    *
    * @param condition the registered condition type holder
    * @param id        a per-source identifier used to distinguish this instance from others of the same type
    * @param duration  number of entity ticks the instance lasts, or {@link #INFINITE_DURATION}
    * @param value     this instance's value contribution
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value){
      this(condition, id, duration, value, true, true, false, AttributeModifier.Operation.ADD_VALUE, null);
   }

   /**
    * Convenience constructor that defaults to a stacking, non-persistent instance showing the condition's
    * default particles, with a custom stacking operation.
    *
    * @param condition the registered condition type holder
    * @param id        a per-source identifier used to distinguish this instance from others of the same type
    * @param duration  number of entity ticks the instance lasts, or {@link #INFINITE_DURATION}
    * @param value     this instance's value contribution
    * @param operation how this instance's value combines with others in the prevailing-value calculation
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, AttributeModifier.Operation operation){
      this(condition, id, duration, value, true, true, false, operation, null);
   }

   /**
    * Convenience constructor that defaults to a non-persistent instance with no inflicting entity.
    *
    * @param condition the registered condition type holder
    * @param id        a per-source identifier
    * @param duration  number of entity ticks the instance lasts, or {@link #INFINITE_DURATION}
    * @param value     this instance's value contribution
    * @param stacking  if {@code true} this instance contributes to the stacked sum, otherwise it competes
    *                  with other non-stacking instances for the prevailing slot
    * @param particles {@code true} to show the condition's default particles, {@code false} to show none
    * @param operation what {@code value} means: a magnitude ({@code ADD_VALUE}) or a percentage of the
    *                  condition's effect, see {@link ConditionMath.Scale}
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, boolean particles, AttributeModifier.Operation operation){
      this(condition, id, duration, value, stacking, particles, false, operation, null);
   }

   /**
    * Full constructor taking a boolean particle switch.
    *
    * @param condition   the registered condition type holder
    * @param id          a per-source identifier used to distinguish this instance from others of the same
    *                    type; identical {@code (condition, id)} pairs replace each other on add
    * @param duration    number of entity ticks the instance lasts, or {@link #INFINITE_DURATION}
    * @param value       this instance's value contribution
    * @param stacking    if {@code true} this instance contributes to the stacked sum, otherwise it
    *                    competes for the prevailing slot among non-stacking instances
    * @param particles   {@code true} to show the condition's {@link Condition#getDefaultParticles() default
    *                    particles}, {@code false} to show none
    * @param persistent  if {@code true} the source reapplies this instance continuously (see
    *                    {@link #isPersistent()})
    * @param operation   stacking operation used when computing the prevailing value
    * @param inflictedBy optional UUID of the entity that applied this condition; used to skip friendly fire
    *                    on harmful conditions
    * @throws IllegalArgumentException under the same rules as the
    *                                  {@link #ConditionInstance(Holder, Identifier, int, float, boolean, Optional, boolean, AttributeModifier.Operation, UUID, Component) full constructor}
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, boolean particles, boolean persistent, AttributeModifier.Operation operation, UUID inflictedBy){
      this(condition, id, duration, value, stacking, particles ? Optional.empty() : Optional.of(ConditionParticles.NONE), persistent, operation, inflictedBy);
   }

   /**
    * Full constructor for an instance without a display name.
    *
    * @param condition   the registered condition type holder
    * @param id          a per-source identifier used to distinguish this instance from others of the same
    *                    type; identical {@code (condition, id)} pairs replace each other on add
    * @param duration    number of entity ticks the instance lasts, or {@link #INFINITE_DURATION};
    *                    {@link #tick()} returns {@code true} when the timer reaches this value
    * @param value       this instance's value contribution
    * @param stacking    if {@code true} this instance contributes to the stacked sum, otherwise it
    *                    competes for the prevailing slot among non-stacking instances
    * @param particles   this instance's particle spec: empty uses the condition's
    *                    {@link Condition#getDefaultParticles() default}, {@link ConditionParticles#NONE}
    *                    shows none, and any other value is this instance's own override
    * @param persistent  if {@code true} the source reapplies this instance continuously (see
    *                    {@link #isPersistent()})
    * @param operation   what {@code value} means: {@code ADD_VALUE} adds magnitude, while
    *                    {@code ADD_MULTIPLIED_BASE} (additive) and {@code ADD_MULTIPLIED_TOTAL}
    *                    (compounding) are percentages of the condition's effect, see
    *                    {@link ConditionMath.Scale}
    * @param inflictedBy optional UUID of the entity that applied this condition; used to skip friendly fire
    *                    on harmful conditions
    * @throws IllegalArgumentException under the same rules as the
    *                                  {@link #ConditionInstance(Holder, Identifier, int, float, boolean, Optional, boolean, AttributeModifier.Operation, UUID, Component) full constructor}
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, Optional<ConditionParticles> particles, boolean persistent, AttributeModifier.Operation operation, UUID inflictedBy){
      this(condition, id, duration, value, stacking, particles, persistent, operation, inflictedBy, null);
   }

   /**
    * Full constructor.
    *
    * @param condition   the registered condition type holder
    * @param id          a per-source identifier used to distinguish this instance from others of the same
    *                    type; identical {@code (condition, id)} pairs replace each other on add
    * @param duration    number of entity ticks the instance lasts, or {@link #INFINITE_DURATION};
    *                    {@link #tick()} returns {@code true} when the timer reaches this value
    * @param value       this instance's value contribution
    * @param stacking    if {@code true} this instance contributes to the stacked sum, otherwise it
    *                    competes for the prevailing slot among non-stacking instances
    * @param particles   this instance's particle spec: empty uses the condition's
    *                    {@link Condition#getDefaultParticles() default}, {@link ConditionParticles#NONE}
    *                    shows none, and any other value is this instance's own override
    * @param persistent  if {@code true} the source reapplies this instance continuously (see
    *                    {@link #isPersistent()})
    * @param operation   what {@code value} means: {@code ADD_VALUE} adds magnitude, while
    *                    {@code ADD_MULTIPLIED_BASE} (additive) and {@code ADD_MULTIPLIED_TOTAL}
    *                    (compounding) are percentages of the condition's effect, see
    *                    {@link ConditionMath.Scale}
    * @param inflictedBy optional UUID of the entity that applied this condition; used to skip friendly fire
    *                    on harmful conditions
    * @param name        optional display name shown to players, either literal text or a translatable
    *                    component; {@code null} for none (see {@link #getName()})
    * @throws IllegalArgumentException if {@code value} is {@code NaN} or {@code -∞}, if {@code duration} is
    *                                  0 or below {@link #INFINITE_DURATION}, or if a non-stacking instance
    *                                  of a {@link ConditionMath.Scale#LINEAR linear} condition uses an
    *                                  operation other than {@code ADD_VALUE} (it could only ever be 0)
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, Optional<ConditionParticles> particles, boolean persistent, AttributeModifier.Operation operation, UUID inflictedBy, Component name){
      if(Float.isNaN(value) || value == Float.NEGATIVE_INFINITY) throw new IllegalArgumentException("Condition value must not be NaN or -Infinity, got " + value);
      if(duration == 0 || duration < INFINITE_DURATION) throw new IllegalArgumentException("Condition duration must be positive or INFINITE_DURATION, got " + duration);
      if(!stacking && operation != AttributeModifier.Operation.ADD_VALUE && condition.value().getScale() == ConditionMath.Scale.LINEAR){
         throw new IllegalArgumentException("Non-stacking instances of a linear condition must use ADD_VALUE, got " + operation);
      }
      this.condition = condition;
      this.id = id;
      this.duration = duration;
      this.value = value;
      this.stacking = stacking;
      this.particles = particles;
      this.operation = operation;
      this.timer = 0;
      this.persistent = persistent;
      this.inflictedBy = inflictedBy;
      this.name = name;
   }

   /**
    * Advances this instance's internal timer by one tick. Instances with {@link #INFINITE_DURATION} never
    * advance.
    *
    * @return {@code true} if the instance has reached its full duration and should be removed
    */
   public boolean tick(){
      if(this.duration == INFINITE_DURATION) return false;
      return ++this.timer >= this.duration;
   }

   /** @return the registered {@link Condition} type this instance belongs to. */
   public Holder<Condition> getCondition(){
      return condition;
   }

   /** @return the per-source identifier distinguishing this instance from other instances of the same type. */
   public Identifier getId(){
      return id;
   }

   /** @return this instance's value contribution to the prevailing-value calculation. */
   @Override
   public float getValue(){
      return value;
   }

   /** @return {@code true} if this instance contributes to the stacked sum rather than competing as a non-stacking instance. */
   @Override
   public boolean isStacking(){
      return stacking;
   }

   /**
    * @return this instance's particle spec: empty to use the condition's
    *         {@link Condition#getDefaultParticles() default}, {@link ConditionParticles#NONE} for none, or
    *         its own override
    */
   public Optional<ConditionParticles> getParticles(){
      return particles;
   }

   /** @return the operation used when combining this instance's value into the prevailing total. */
   @Override
   public AttributeModifier.Operation getOperation(){
      return operation;
   }

   /** @return total duration in entity ticks, or {@link #INFINITE_DURATION}. */
   public int getDuration(){
      return duration;
   }

   /** @return current elapsed ticks since this instance was applied (0 through {@link #getDuration()}). */
   public int getTimer(){
      return timer;
   }

   /**
    * Overwrites the elapsed-ticks counter. Useful when extending an existing condition's life or when
    * deserializing.
    *
    * @param timer the new elapsed tick count
    */
   public void setTimer(int timer){
      this.timer = timer;
   }

   /**
    * @return {@code true} if the source of this instance reapplies it continuously, so removing it is
    *         pointless; cleanse effects should skip it. The library itself never reads this flag.
    */
   public boolean isPersistent(){
      return persistent;
   }

   /** @return the UUID of the entity that applied this condition, or {@code null} if not attributable. */
   public UUID getInflictedBy(){
      return inflictedBy;
   }

   /**
    * @return the display name shown to players for this instance, or {@code null} if it has none. Named
    *         instances that share a name are merged into one boss bar by {@link ConditionBossBars}; unnamed
    *         instances never get a boss bar.
    */
   public Component getName(){
      return name;
   }

   /**
    * @param name the display name, either literal text or a translatable component; {@code null} for none
    * @return a copy of this instance carrying the given name and this instance's elapsed timer
    */
   public ConditionInstance withName(Component name){
      ConditionInstance copy = new ConditionInstance(condition, id, duration, value, stacking, particles, persistent, operation, inflictedBy, name);
      copy.timer = timer;
      return copy;
   }

   /** @return the ticks left before this instance expires, or {@link #INFINITE_DURATION} if it never does. */
   public int getRemainingTicks(){
      return duration == INFINITE_DURATION ? INFINITE_DURATION : Math.max(0, duration - timer);
   }
}
