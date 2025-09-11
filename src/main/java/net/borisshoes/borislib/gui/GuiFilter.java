package net.borisshoes.borislib.gui;

import net.minecraft.util.Formatting;

import java.util.function.Predicate;

public abstract class GuiFilter<A> extends GuiFilterSort<A> {
   private final Predicate<A> predicate;
   
   public GuiFilter(String key, int color, Predicate<A> predicate){
      super(key, color);
      this.predicate = predicate;
   }
   
   public boolean matches(A object){
      return predicate.test(object);
   }
   
   public Predicate<A> getPredicate(){
      return predicate;
   }
   
   public <B extends GuiFilter<A>> B cycle(B sort, boolean backwards){
      return super.cycle(sort,backwards);
   }
}