package net.borisshoes.borislib.events;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Event {
   public static final List<Event> RECENT_EVENTS = new ArrayList<>();
   
   public final int lifespan;
   public final Identifier id;
   private final UUID uuid;
   private int timeAlive;
   private boolean removalMark;
   
   public Event(Identifier id, int lifespan){
      this.lifespan = lifespan;
      this.id = id;
      this.timeAlive = 0;
      this.uuid = UUID.randomUUID();
      this.removalMark = false;
   }
   
   public void tick(){
      timeAlive++;
   }
   
   public boolean isExpired(){
      return timeAlive >= lifespan || removalMark;
   }
   
   public UUID getUuid(){
      return uuid;
   }
   
   public void markForRemoval(){
      this.removalMark = true;
   }
   
   public static <T extends Event> List<T> getEventsOfType(Class<T> eventType){
      List<T> filteredEvents = new ArrayList<>();
      for (Event event : RECENT_EVENTS) {
         if (eventType.isInstance(event)) {
            filteredEvents.add(eventType.cast(event));
         }
      }
      return filteredEvents;
   }
   
   public static void addEvent(Event event){
      RECENT_EVENTS.add(event);
   }
}
