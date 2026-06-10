package net.borisshoes.borislib.events;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base class for short-lived game events tracked by BorisLib.
 *
 * <p>The event system keeps a flat list of recent {@code Event}s in {@link #RECENT_EVENTS}. Each event
 * carries an {@link #id} (the event "kind"), a unique {@link #uuid} (so individual events can be matched),
 * and a {@link #lifespan} after which the event is automatically marked as expired. Subclasses can extend
 * {@code Event} with additional payload data (for example: which player triggered it, the target entity,
 * a position, etc.) and use {@link #getEventsOfType(Class)} to query the recent log for events of their
 * concrete type.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 *   // Push an event when something interesting happens
 *   Event.addEvent(new MyAttackEvent(playerId, targetId, 200));
 *
 *   // Later, query for recent attack events by some other system
 *   for (MyAttackEvent e : Event.getEventsOfType(MyAttackEvent.class)) { ... }
 * }</pre>
 *
 * <p>Driving code is expected to call {@link #tick()} every server tick and to periodically remove any
 * events for which {@link #isExpired()} returns {@code true}.</p>
 */
public abstract class Event {
   /** Global flat log of every event that has been added via {@link #addEvent(Event)} and has not yet been pruned. */
   public static final List<Event> RECENT_EVENTS = new ArrayList<>();
   
   /** Number of server ticks this event remains "active" before {@link #isExpired()} flips. */
   public final int lifespan;
   /** Identifier describing the <em>kind</em> of event (e.g. {@code "mymod:attack"}). */
   public final Identifier id;
   private final UUID uuid;
   /** Number of ticks since this event was created. Incremented by {@link #tick()}. */
   protected int timeAlive;
   private boolean removalMark;
   
   /**
    * @param id       identifier describing the kind of event
    * @param lifespan number of ticks the event should live before it auto-expires
    */
   public Event(Identifier id, int lifespan){
      this.lifespan = lifespan;
      this.id = id;
      this.timeAlive = 0;
      this.uuid = UUID.randomUUID();
      this.removalMark = false;
   }
   
   /**
    * Advances this event's age by one tick. When {@link #timeAlive} reaches {@link #lifespan} the
    * {@link #onExpiry()} hook is fired exactly once.
    */
   public void tick(){
      timeAlive++;
      if(timeAlive >= lifespan){
         onExpiry();
      }
   }
   
   /** @return {@code true} if this event has reached its lifespan or was explicitly {@linkplain #markForRemoval() marked for removal}. */
   public boolean isExpired(){
      return timeAlive >= lifespan || removalMark;
   }
   
   /** @return a UUID unique to this event instance (handy for matching one specific event later). */
   public UUID getUuid(){
      return uuid;
   }
   
   /** Flag this event so {@link #isExpired()} returns {@code true} on the next check, regardless of lifespan. */
   public void markForRemoval(){
      this.removalMark = true;
   }
   
   /**
    * Hook invoked once by {@link #tick()} when {@link #timeAlive} first reaches {@link #lifespan}.
    * Subclasses may override to perform a finalization side effect (e.g. award something, clean up state).
    */
   public void onExpiry(){
   }
   
   /**
    * Returns every event currently in {@link #RECENT_EVENTS} that is an instance of the given subclass.
    *
    * @param eventType the concrete event class to filter by
    * @param <T>       the event subtype
    * @return a new list of matching events (may be empty, never {@code null})
    */
   public static <T extends Event> List<T> getEventsOfType(Class<T> eventType){
      List<T> filteredEvents = new ArrayList<>();
      for(Event event : RECENT_EVENTS){
         if(eventType.isInstance(event)){
            filteredEvents.add(eventType.cast(event));
         }
      }
      return filteredEvents;
   }
   
   /**
    * Pushes a new event onto {@link #RECENT_EVENTS} so other systems can observe it via
    * {@link #getEventsOfType(Class)}.
    *
    * @param event the event to record
    */
   public static void addEvent(Event event){
      RECENT_EVENTS.add(event);
   }
}
