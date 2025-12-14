package net.borisshoes.borislib.gui;

import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class GuiFilterSort<A>{
   private final String key;
   private final int color;
   
   protected GuiFilterSort(String key, int color){
      this.key = key;
      this.color = color;
   }
   
   public int getColor(){
      return color;
   }
   
   public Component getColoredLabel(){
      return Component.translatable(key).withColor(color);
   }
   
   protected abstract List<? extends GuiFilterSort<A>> getList();
   
   protected abstract <T extends GuiFilterSort<A>> T getStaticDefault();
   
   protected GuiFilterSort<A> getDefault(){
      return getList().getFirst();
   }
   
   @SuppressWarnings("unchecked")
   protected <B extends GuiFilterSort<A>> B cycle(B sort, boolean backwards){
      List<? extends GuiFilterSort<A>> list = getList();
      int idx = list.indexOf(sort);
      if (idx == -1) {
         return (B) list.getFirst();
      }
      int shift = backwards ? -1 : 1;
      int nextIndex = (idx + shift + list.size()) % list.size();
      return (B) list.get(nextIndex);
   }
}