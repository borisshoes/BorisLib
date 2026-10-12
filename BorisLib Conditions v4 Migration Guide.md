# BorisLib Conditions: migrating from v3 to v4

BorisLib `4.0.0+26.3` is a breaking release for anything that touches conditions. If your mod only uses other parts of BorisLib, bump the dependency and you are done.

## 1. Convert your values

Every condition value is now a **magnitude**: 0 means absent, larger means stronger. The old base-1 multipliers and "lower is stronger" conditions are gone, so the numbers you pass to `ConditionInstance` change meaning.

For an `ADD_VALUE` instance with v3 value `d`:

| Condition | v3 value `d` | v4 value |
| --- | --- | --- |
| Might, Vulnerability | offset from 1, `d ≥ 0` | `d` (unchanged) |
| Fortitude, Feeble | offset from 1, `d ≤ 0` | `ConditionMath.reductionToMagnitude(-d)` |
| Celerity | fraction of base speed added | `d` (unchanged) |
| Torpor | fraction of base speed removed | `ConditionMath.reductionToMagnitude(d)` |
| Decay | damage per 10 ticks | `2 * d` (HP per second) |
| Rejuvenation | heal per tick | `20 * d` (HP per second) |
| Nearsight | sight radius in blocks | `NearsightCondition.magnitudeForRadius(d)` |

`reductionToMagnitude` takes the fraction removed: 0.5 (half) gives 1, 0.75 gives 3, and 1.0 gives +∞ (full immunity, a full root, and so on). It throws outside `[0, 1]`, so clamp a v3 Torpor value above 1 to 1 first.

### Magnitudes or percentages

The operation says what an instance's value is:

| Operation | Value is | Two instances stack as |
| --- | --- | --- |
| `ADD_VALUE` | a magnitude | sum of magnitudes |
| `ADD_MULTIPLIED_BASE` | a percentage of the effect | sum of percentages |
| `ADD_MULTIPLIED_TOTAL` | a percentage of the effect | compounding percentages |

For a reducing condition (Fortitude, Feeble, Torpor, Nearsight, ...) the percentage is the fraction removed; for a boosting one (Might, Vulnerability, Celerity, ...) it is the fraction added.

| Fortitude instances | Damage resisted |
| --- | --- |
| `ADD_MULTIPLIED_BASE` 0.15 and 0.20 | 35% |
| `ADD_MULTIPLIED_TOTAL` 0.15 and 0.20 | 32% (0.85 × 0.80 remains) |
| `ADD_VALUE` `reductionToMagnitude(0.15)` and `(0.20)` | about 30% (diminishing returns) |

Percentages on a reducing condition saturate at 100%, which is the condition's cap: immunity for Fortitude, a root for Torpor. Summed magnitudes never reach it, so use `ADD_VALUE` where sources should not be able to stack to immunity. All three kinds can be mixed on one entity: magnitudes apply first, then base percentages (points of the unmodified effect, so each is worth the same whatever else is present), then total percentages (a share of what is left).

So there are two ways to port a v3 instance of a reducing condition, and v3's own stacking is the percentage one:

```java
// v3: 40% damage reduction
new ConditionInstance(Conditions.FORTITUDE, id, 200, -0.4f);
// v4, stacking like v3 did: 40% + 40% = 80%
new ConditionInstance(Conditions.FORTITUDE, id, 200, 0.4f, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
// v4, as a magnitude: 40% + 40% = 57%
new ConditionInstance(Conditions.FORTITUDE, id, 200, ConditionMath.reductionToMagnitude(0.4f));
```

A v3 `ADD_MULTIPLIED_TOTAL` instance on Fortitude or Feeble ports the same way: negate the value and keep the operation. Decay, Rejuvenation and Levity are plain quantities, so percentages there scale the magnitude like vanilla attribute modifiers and do nothing on their own.

## 2. Fix instances that now throw

The `ConditionInstance` constructor throws `IllegalArgumentException` for:

- a `NaN` or `-∞` value (+∞ and finite negatives are fine);
- a duration of 0 or below -1. Use `ConditionInstance.INFINITE_DURATION` (-1) for an instance that never expires;
- a non-stacking percentage instance on a plain-quantity condition (Decay, Rejuvenation, Levity, or any custom condition that does not override `getScale()`), since it could only ever be 0.

## 3. Update API calls

| v3 | v4 |
| --- | --- |
| `boolean addCondition(...)` (true = replaced) | returns `Conditions.AddResult`: `ADDED`, `REPLACED` or `REJECTED` |
| `Triple<Float, Boolean, Boolean> getPrevalingCondition(uuid, holder)`, `null` when inactive | `PrevailingCondition getPrevailingCondition(entity, holder)`, never `null`; check `isActive()` or `value() > 0` |
| `getConditionValue(uuid, holder)` | still works; prefer the new `getConditionValue(entity, holder)`, which is O(1). Both return 0 when inactive, not the old base value |
| `boolean removeConditions(...)` | returns the `int` count removed |
| `instance.hasParticles()` | `instance.getParticles()`, an `Optional<ConditionParticles>` |
| `ConditionData.getConditions(uuid)` (mutable list), `ConditionData.tick(server)` | removed. Read through `Conditions`, or `ConditionData.get(uuid)` for the read-only `EntityConditions` record |
| `condition.getBase()`, `getMin()`, `getMax()`, `isReversedImportance()` | removed. Use `getCap()` and `supportsInfinity()` |

New and worth using:

- `Conditions.isActive(entity, holder)`.
- `Conditions.addConditionIfStronger(...)`: for sources sharing one id, replaces only if the new value is at least the old one.
- `Conditions.removeConditionsIf(server, entity, predicate)`: for cleanses, e.g. `i -> i.getCondition().value().isHarmful() && !i.isPersistent()`.

The list getters now return unmodifiable copies, and no getter ever creates data.

## 4. Update custom conditions

**Constructor.** `base`, `min`, `max` and `reversed` are gone:

```java
super(id, category);                           // finite, cap = Float.MAX_VALUE
super(id, category, Float.POSITIVE_INFINITY);  // opts in to infinite magnitudes
```

**Scale.** If your condition's effect is a multiplier, override `getScale()` to return `ConditionMath.Scale.AMPLIFY` (effect × `1 + m`) or `ATTENUATE` (effect × `1 / (1 + m)`), so instances can be given as percentages of the effect. The default, `LINEAR`, treats the magnitude as a plain quantity. `AttributeCondition` picks the scale from its curve.

**Hooks.** Rename and drop the `particles` argument:

| v3 | v4 |
| --- | --- |
| `onApply(server, entity, value, particles)` | `onActivate(server, entity, value)` |
| `onRemove(server, entity, value, particles)` | `onDeactivate(server, entity, lastValue)` |
| `onTick(server, entity, value, particles)` | `onTick(server, entity, value)` |
| | new: `onChange(server, entity, oldValue, newValue)` |
| | new: `onEntityLoad(server, entity, value)` |

Only `onTick` is abstract. Behavior differences to check:

- Hooks follow the **value**, not the instance list. `onActivate` fires when the value goes from 0 to above 0, `onDeactivate` when it returns to 0, even if instances remain (for example a +0.5 and a -0.5 cancelling).
- `onTick` fires only while the value is above 0, from the entity's own tick. Use `entity.tickCount` rather than the server tick for intervals. It does not run for unloaded entities or during `/tick freeze`.
- Anything that does not survive a reload (transient attribute modifiers) must be reapplied in `onEntityLoad`.

**Attribute conditions.** If your condition just modifies attributes, extend `AttributeCondition` (or instantiate `SimpleAttributeCondition`) and delete your hook code. It applies, updates, reattaches and removes the modifier for you, and rejects entities that lack the attribute.

**Particles.** Hooks no longer draw particles. Override `getDefaultParticles()` instead:

```java
@Override
public ConditionParticles getDefaultParticles(){
   return ConditionParticles.of(ParticleTypes.CRIT, 5.0f, 12, 0.05f); // particle, per second, burst on add, speed
}
```

A single instance can override it by passing `Optional.of(spec)` to the full `ConditionInstance` constructor, or `Optional.of(ConditionParticles.NONE)` for none. The boolean constructors still work: `true` means the condition's default, `false` means none. Each entity is capped at 20 ambient particles per second across all its conditions.

## 5. Behavior changes to be aware of

- **Old saves are discarded.** Condition data written by v3 is dropped on first load with one warning. Continuously reapplied conditions come back on the next tick; timed ones active at update time are lost.
- **Fortitude at +∞ is true invulnerability:** no damage, knockback or hurt sound. Only damage that bypasses invulnerability (`/kill`, the void) gets through.
- **Torpor is now a total multiplier,** so it also scales vanilla Speed and Celerity. At +∞ it zeroes jump strength too.
- **Decay ticks on the entity's own tick counter,** so entities no longer all take damage on the same server tick.
- **Rejuvenation heals through `heal()`,** so the new Blight and Vitality conditions affect it.
- **Entities removed from the world lose their conditions.** Unloaded entities keep theirs with timers paused.
- **Use the registered holder.** Records are keyed by holder identity, so always pass the `Holder` returned by `BorisLib.registerCondition` (or the constants in `Conditions`), not a `Holder.direct` copy.
- `getNameWithValue` now shows the magnitude, and `∞` for an infinite one. If you override the `name_value` translation of a built-in, update its wording.

## 6. New built-in conditions

All follow the same magnitude rules; opposing pairs cancel at equal magnitude (Levity and Gravitas excepted).

| Beneficial | Harmful | Affects |
| --- | --- | --- |
| Alacrity | Lethargy (∞) | block break speed |
| Fervor | Lassitude | attack speed |
| Span | Bound (∞) | block interaction range, players only |
| Lunge | Restraint (∞) | entity interaction range, players only |
| Bulwark | Sunder (∞) | armor |
| Temper | Fracture (∞) | armor toughness |
| Poise (∞) | Falter | knockback taken |
| Damping (∞) | Rupture | explosion knockback taken |
| Vitality | Blight (∞) | healing through `heal()` |
| Obscurity (∞) | Exposure | mob detection range |
| Levity (neutral) | Gravitas | gravity: Levity 1 is weightless, 2 is reversed |

(∞) marks the conditions that accept an infinite magnitude.
