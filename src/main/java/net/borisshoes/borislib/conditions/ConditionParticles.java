package net.borisshoes.borislib.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Describes the particles a {@link Condition} or a single {@link ConditionInstance} shows on its entity.
 *
 * <p>Specs are never drawn by condition hooks. The {@link ConditionParticleEmitter} pools the specs of
 * every instance on an entity and sends at most one packet per distinct spec per tick.</p>
 *
 * @param particle  the particle to show, including any color it carries
 * @param perSecond ambient particles per second while the condition type is active
 * @param burst     particles emitted once when the instance is first added
 * @param speed     the speed passed through to the particle packet
 */
public record ConditionParticles(ParticleOptions particle, float perSecond, int burst, float speed) {
   /** A spec that shows no particles at all. */
   public static final ConditionParticles NONE = new ConditionParticles(ParticleTypes.POOF, 0.0f, 0, 0.0f);

   /** Codec for persisting a spec; the particle is encoded with {@link ParticleTypes#CODEC}. */
   public static final Codec<ConditionParticles> CODEC = RecordCodecBuilder.create(i -> i.group(
         ParticleTypes.CODEC.fieldOf("particle").forGetter(ConditionParticles::particle),
         Codec.FLOAT.fieldOf("perSecond").forGetter(ConditionParticles::perSecond),
         Codec.INT.fieldOf("burst").forGetter(ConditionParticles::burst),
         Codec.FLOAT.fieldOf("speed").forGetter(ConditionParticles::speed)
   ).apply(i, ConditionParticles::new));

   /**
    * @throws IllegalArgumentException if {@code perSecond} is negative or not finite, or if {@code burst}
    *                                  or {@code speed} is negative
    */
   public ConditionParticles{
      if(particle == null) throw new IllegalArgumentException("Condition particles need a particle");
      if(!Float.isFinite(perSecond) || perSecond < 0.0f) throw new IllegalArgumentException("perSecond must be finite and >= 0, got " + perSecond);
      if(burst < 0) throw new IllegalArgumentException("burst must be >= 0, got " + burst);
      if(!(speed >= 0.0f)) throw new IllegalArgumentException("speed must be >= 0, got " + speed);
   }

   /**
    * Creates a spec.
    *
    * @param particle  the particle to show
    * @param perSecond ambient particles per second while the condition type is active
    * @param burst     particles emitted once when the instance is first added
    * @param speed     the speed passed through to the particle packet
    * @return the new spec
    */
   public static ConditionParticles of(ParticleOptions particle, float perSecond, int burst, float speed){
      return new ConditionParticles(particle, perSecond, burst, speed);
   }

   /** @return {@code true} if this spec shows neither ambient nor burst particles. */
   public boolean isNone(){
      return perSecond <= 0.0f && burst <= 0;
   }
}
