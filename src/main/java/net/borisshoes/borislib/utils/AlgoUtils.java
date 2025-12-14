package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.util.Tuple;

import java.util.*;

public class AlgoUtils {
   
   public static <T> T getWeightedOption(List<Tuple<T,Integer>> options){
      return getWeightedOption(options, new Random().nextLong());
   }
   
   public static <T> T getWeightedOption(List<Tuple<T,Integer>> options, long seed){
      ArrayList<T> weightedList = new ArrayList<>();
      for(Tuple<T, Integer> option : options){
         for(int i = 0; i < option.getB(); i++){
            weightedList.add(option.getA());
         }
      }
      Random random = new Random(seed);
      return weightedList.get(random.nextInt(weightedList.size()));
   }
   
   public static <T> List<Tuple<T,Integer>> randomlySpace(List<T> items, int size, long seed){
      Random random = new Random(seed);
      
      List<Integer> remaining = new ArrayList<>();
      List<Tuple<T,Integer>> randomized = new ArrayList<>();
      
      for(int i = 0; i < size; i++){
         remaining.add(i);
      }
      
      int i = 0;
      while(i < items.size() && !remaining.isEmpty()){
         int index = random.nextInt(remaining.size());
         randomized.add(new Tuple<>(items.get(i),remaining.get(index)));
         remaining.remove(remaining.get(index));
         i++;
      }
      
      return randomized;
   }
   
   public static <T> List<T> listToPage(List<T> items, int page, int pageSize){
      if(page <= 0){
         return items;
      }else if(pageSize*(page-1) >= items.size()){
         return new ArrayList<>();
      }else{
         return items.subList(pageSize*(page-1), Math.min(items.size(), pageSize*page));
      }
   }
   
   public static String convertToBase64(String binaryString) {
      int byteLength = (binaryString.length() + 7) / 8;
      byte[] byteArray = new byte[byteLength];
      
      for (int i = 0; i < binaryString.length(); i++) {
         if (binaryString.charAt(i) == '1') {
            byteArray[i / 8] |= (byte) (1 << (7 - (i % 8)));
         }
      }
      
      return Base64.getEncoder().encodeToString(byteArray);
   }
   
   public static UUID getUUID(String str){
      try{
         return UUID.fromString(str);
      }catch(Exception e){
         return UUID.fromString(BorisLib.BLANK_UUID);
      }
   }
}
