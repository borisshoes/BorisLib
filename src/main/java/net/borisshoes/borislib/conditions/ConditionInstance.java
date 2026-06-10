package net.borisshoes.borislib.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/**
 * A single, stackable instance of a {@link Condition} applied to an entity.
 *
 * <p>Multiple {@code ConditionInstance}s of the same {@link Condition} type can coexist on one entity; each
 * carries its own value, duration, stacking behavior and source. The {@link Conditions} manager combines all
 * current instances into a single "prevailing" value that is what subclasses of {@link Condition} actually
 * react to.</p>
 *
 * <p>Each instance is uniquely identified by the tuple ({@link #getCondition() condition},
 * {@link #getId() id}); adding a new instance with an identical pair to an existing one replaces it in
 * place. Use a distinct {@link Identifier} for each independent source so they can coexist and be removed
 * individually.</p>
 */
public class ConditionInstance {
   
   /**
    * Codec used to persist condition instances to NBT/JSON via mojang's data fixer framework.
    * Both the condition type and the inflicting entity UUID are serialized.
    */
   public static final Codec<ConditionInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
         BorisLib.CONDITIONS.holderByNameCodec().fieldOf("condition").forGetter(ConditionInstance::getCondition),
         Identifier.CODEC.fieldOf("id").forGetter(ConditionInstance::getId),
         Codec.INT.fieldOf("duration").forGetter(ConditionInstance::getDuration),
         Codec.FLOAT.fieldOf("value").forGetter(ConditionInstance::getValue),
         Codec.BOOL.fieldOf("stacking").forGetter(ConditionInstance::isStacking),
         Codec.BOOL.fieldOf("particles").forGetter(ConditionInstance::hasParticles),
         AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(ConditionInstance::getOperation),
         Codec.INT.fieldOf("timer").forGetter(ConditionInstance::getTimer),
         Codec.BOOL.fieldOf("persistent").forGetter(ConditionInstance::isPersistent),
         CodecUtils.UUID_CODEC.optionalFieldOf("inflictedBy").forGetter(inst -> java.util.Optional.ofNullable(inst.getInflictedBy()))
   ).apply(i, (condition, id, duration, value, stacking, particles, operation, timer, persistent, inflictedBy) -> {
      ConditionInstance inst = new ConditionInstance(condition, id, duration, value, stacking, particles, persistent, operation, inflictedBy.orElse(null));
      inst.timer = timer;
      return inst;
   }));
   
   private final Holder<Condition> condition;
   private final Identifier id;
   private final float value;
   private final boolean stacking;
   private final boolean particles;
   private final AttributeModifier.Operation operation;
   private final int duration;
   private final boolean persistent;
   private final UUID inflictedBy;
   private int timer;
   
   /**
    * Convenience constructor that defaults to a stacking, particle-emitting, non-persistent instance using
    * {@link AttributeModifier.Operation#ADD_VALUE}.
    *
    * @param condition the registered condition type holder
    * @param id        a per-source identifier used to distinguish this instance from others of the same type
    * @param duration  number of server ticks the instance lasts (see {@link #tick()})
    * @param value     this instance's value contribution
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value){
      this(condition, id, duration, value, true, true, false, AttributeModifier.Operation.ADD_VALUE, null);
   }
   
   /**
    * Convenience constructor that defaults to a stacking, particle-emitting, non-persistent instance with
    * a custom stacking operation.
    *
    * @param condition the registered condition type holder
    * @param id        a per-source identifier used to distinguish this instance from others of the same type
    * @param duration  number of server ticks the instance lasts
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
    * @param duration  number of server ticks the instance lasts
    * @param value     this instance's value contribution
    * @param stacking  if {@code true} this instance contributes to the stacked sum, otherwise it competes
    *                  with other non-stacking instances for the prevailing slot
    * @param particles whether the condition's particle effect should be emitted on behalf of this instance
    * @param operation stacking operation (ignored when {@code stacking} is {@code false} except for the
    *                  prevailing-value formula)
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, boolean particles, AttributeModifier.Operation operation){
      this(condition, id, duration, value, stacking, particles, false, operation, null);
   }
   
   /**
    * Full constructor.
    *
    * @param condition   the registered condition type holder
    * @param id          a per-source identifier used to distinguish this instance from others of the same
    *                    type; identical {@code (condition, id)} pairs replace each other on add
    * @param duration    number of server ticks the instance lasts; {@link #tick()} returns {@code true}
    *                    when the timer reaches this value
    * @param value       this instance's value contribution
    * @param stacking    if {@code true} this instance contributes to the stacked sum, otherwise it
    *                    competes for the prevailing slot among non-stacking instances
    * @param particles   whether the condition's particle effect should be emitted on behalf of this
    *                    instance
    * @param persistent  if {@code true} the instance should be preserved across special situations (consumers of {@link Conditions} may use this flag to decide)
    * @param operation   stacking operation used when computing the prevailing value
    * @param inflictedBy optional UUID of the entity that applied this condition; used to skip friendly fire
    *                    on harmful conditions
    */
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, boolean particles, boolean persistent, AttributeModifier.Operation operation, UUID inflictedBy){
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
   }
   
   /**
    * Advances this instance's internal timer by one tick.
    *
    * @return {@code true} if the instance has reached its full duration and should be removed
    */
   public boolean tick(){
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
   public float getValue(){
      return value;
   }
   
   /** @return {@code true} if this instance contributes to the stacked sum rather than competing as a non-stacking instance. */
   public boolean isStacking(){
      return stacking;
   }
   
   /** @return {@code true} if this instance requests that the condition's particles be rendered. */
   public boolean hasParticles(){
      return particles;
   }
   
   /** @return the operation used when combining this instance's value into the prevailing total. */
   public AttributeModifier.Operation getOperation(){
      return operation;
   }
   
   /** @return total duration in server ticks. */
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
   
   /** @return {@code true} if this instance is flagged as persistent (e.g. survives respawn-like cleanups). */
   public boolean isPersistent(){
      return persistent;
   }
   
   /** @return the UUID of the entity that applied this condition, or {@code null} if not attributable. */
   public UUID getInflictedBy(){
      return inflictedBy;
   }
}
