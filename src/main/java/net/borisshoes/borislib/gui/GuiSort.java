package net.borisshoes.borislib.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class GuiSort<A> extends GuiFilterSort<A> {
   private final Comparator<A> comparator;
   
   public GuiSort(String key, int color, Comparator<A> comparator){
      super(key,color);
      this.comparator = comparator;
   }
   
   public List<A> sortList(List<A> unsorted){
      List<A> copy = new ArrayList<>(unsorted);
      copy.sort(comparator);
      return copy;
   }
   
   public Comparator<A> getComparator(){
      return comparator;
   }
   
   public <B extends GuiSort<A>> B cycle(B sort, boolean backwards){
      return super.cycle(sort,backwards);
   }
}
