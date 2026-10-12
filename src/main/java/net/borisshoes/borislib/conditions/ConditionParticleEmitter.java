package net.borisshoes.borislib.conditions;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;

/**
 * Combines the {@link ConditionParticles} of every condition instance on one entity and sends them.
 *
 * <p>One emitter exists per {@link EntityConditions} record and runs at the end of the entity's condition
 * tick. Each entity sends at most one particle packet per distinct spec per tick, however many conditions
 * it has:</p>
 * <ol>
 *    <li><b>Pool.</b> Every instance of every active type contributes its resolved spec. Specs with the
 *        same particle and speed merge into one entry at the highest of their rates. The pool is rebuilt
 *        only when the record changes.</li>
 *    <li><b>Cap.</b> If the pooled rate exceeds {@link #MAX_PARTICLES_PER_SECOND}, every entry is scaled
 *        down proportionally.</li>
 *    <li><b>Emit.</b> Each entry accumulates its fractional rate and sends whole particles, with no
 *        randomness on the server.</li>
 *    <li><b>Bursts.</b> Newly added instances queue a one-off burst that is merged into the same packet
 *        and is not capped.</li>
 * </ol>
 */
public final class ConditionParticleEmitter {
   /** The most ambient particles per second one entity emits across all of its conditions. */
   public static final float MAX_PARTICLES_PER_SECOND = 20.0f;

   private final ArrayList<Entry> pool = new ArrayList<>();
   private final ArrayList<ConditionParticles> bursts = new ArrayList<>();
   private boolean stale = true;

   ConditionParticleEmitter(){
   }

   /** Flags the pool for a rebuild on the next tick. Called whenever the record's instances change. */
   void markChanged(){
      this.stale = true;
   }

   /**
    * Queues the one-off burst of a newly added instance.
    *
    * @param spec the instance's resolved particle spec
    */
   void queueBurst(ConditionParticles spec){
      if(spec.burst() > 0) bursts.add(spec);
   }

   /**
    * Emits this tick's particles for an entity.
    *
    * @param level  the entity's level
    * @param entity the entity the particles surround
    * @param record the entity's condition record
    */
   void tick(ServerLevel level, LivingEntity entity, EntityConditions record){
      if(entity.isSpectator()){
         bursts.clear();
         return;
      }
      if(stale || !bursts.isEmpty()){
         DynamicOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
         if(stale){
            rebuild(record, ops);
            stale = false;
         }
         for(int i = 0; i < bursts.size(); i++){
            ConditionParticles spec = bursts.get(i);
            findOrCreate(spec, ops).burst += spec.burst();
         }
         bursts.clear();
      }
      if(pool.isEmpty()) return;

      double width = entity.getBbWidth();
      double height = entity.getBbHeight();
      for(int i = pool.size() - 1; i >= 0; i--){
         Entry entry = pool.get(i);
         entry.accumulator += entry.perSecond / 20.0f;
         int count = (int) entry.accumulator;
         entry.accumulator -= count;
         count += entry.burst;
         entry.burst = 0;
         if(count > 0){
            level.sendParticles(entry.particle, entity.getX(), entity.getY() + height / 2.0, entity.getZ(), count, width * 0.3, height * 0.3, width * 0.3, entry.speed);
         }
         if(entry.perSecond <= 0.0f) pool.remove(i);
      }
   }

   private void rebuild(EntityConditions record, DynamicOps<Tag> ops){
      // Keep the entries so matching specs carry their accumulators over the rebuild
      for(int i = 0; i < pool.size(); i++){
         pool.get(i).perSecond = 0.0f;
      }
      for(var typeEntry : record.types().reference2ObjectEntrySet()){
         EntityConditions.TypeState state = typeEntry.getValue();
         if(!state.isActive()) continue;
         ConditionParticles fallback = typeEntry.getKey().value().getDefaultParticles();
         ArrayList<ConditionInstance> instances = state.instances();
         for(int i = 0; i < instances.size(); i++){
            ConditionParticles spec = instances.get(i).getParticles().orElse(fallback);
            if(spec.perSecond() <= 0.0f) continue;
            Entry entry = findOrCreate(spec, ops);
            if(spec.perSecond() > entry.perSecond) entry.perSecond = spec.perSecond();
         }
      }

      float total = 0.0f;
      for(int i = pool.size() - 1; i >= 0; i--){
         Entry entry = pool.get(i);
         if(entry.perSecond <= 0.0f && entry.burst <= 0){
            pool.remove(i);
         }else{
            total += entry.perSecond;
         }
      }
      if(total > MAX_PARTICLES_PER_SECOND){
         float scale = MAX_PARTICLES_PER_SECOND / total;
         for(int i = 0; i < pool.size(); i++){
            pool.get(i).perSecond *= scale;
         }
      }
   }

   private Entry findOrCreate(ConditionParticles spec, DynamicOps<Tag> ops){
      // Not every ParticleOptions implements equals, so identical particles are matched by their encoding
      Object key = ParticleTypes.CODEC.encodeStart(ops, spec.particle()).result().map(tag -> (Object) tag).orElse(spec.particle());
      for(int i = 0; i < pool.size(); i++){
         Entry entry = pool.get(i);
         if(entry.speed == spec.speed() && entry.key.equals(key)) return entry;
      }
      Entry entry = new Entry(key, spec.particle(), spec.speed());
      pool.add(entry);
      return entry;
   }

   private static final class Entry {
      private final Object key;
      private final ParticleOptions particle;
      private final float speed;
      private float perSecond;
      private float accumulator;
      private int burst;

      private Entry(Object key, ParticleOptions particle, float speed){
         this.key = key;
         this.particle = particle;
         this.speed = speed;
      }
   }
}
