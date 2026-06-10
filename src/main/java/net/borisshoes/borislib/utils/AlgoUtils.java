package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.util.Tuple;

import java.util.*;

/**
 * Algorithmic utility methods for common programming tasks including weighted random selection,
 * list pagination, randomized distribution, encoding, and safe UUID parsing.
 *
 * <p>This class provides generic, type-safe methods that are useful across many mod scenarios:
 * <ul>
 *   <li><b>Weighted Selection</b> — Choose random elements with different probabilities</li>
 *   <li><b>Random Distribution</b> — Distribute items across positions without collision</li>
 *   <li><b>Pagination</b> — Split lists into pages for GUI display</li>
 *   <li><b>Encoding</b> — Convert binary strings to Base64</li>
 *   <li><b>UUID Parsing</b> — Safely parse UUIDs with fallback</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Weighted loot selection
 * List<Tuple<Item, Integer>> loot = new ArrayList<>();
 * loot.add(new Tuple<>(Items.DIAMOND, 1));  // 1% chance
 * loot.add(new Tuple<>(Items.IRON_INGOT, 10)); // 10% chance
 * loot.add(new Tuple<>(Items.DIRT, 89));    // 89% chance
 * Item reward = AlgoUtils.getWeightedOption(loot);
 *
 * // Paginate player list
 * List<String> allPlayers = // ... get all players
 * List<String> page1 = AlgoUtils.listToPage(allPlayers, 1, 10); // First 10 players
 * }</pre>
 *
 * @see MathUtils For mathematical and geometric utilities
 * @see TextUtils For text processing utilities
 */
public class AlgoUtils {
   
   /**
    * Selects a random option from a weighted list using a new random seed.
    *
    * <p>Each option has an associated weight (integer). The probability of selection
    * is proportional to the weight. For example, an item with weight 10 is 10 times
    * more likely to be selected than an item with weight 1.
    *
    * @param <T> the type of options
    * @param options list of tuples where each tuple contains (value, weight)
    * @return the randomly selected option based on weights
    * @throws IllegalArgumentException if options is empty
    * @see #getWeightedOption(List, long) For deterministic selection with a seed
    */
   public static <T> T getWeightedOption(List<Tuple<T, Integer>> options){
      return getWeightedOption(options, new Random().nextLong());
   }
   
   /**
    * Selects a random option from a weighted list using the specified seed.
    *
    * <p>This method is deterministic - the same seed with the same options list
    * will always produce the same result. Useful for procedural generation or
    * reproducible testing.
    *
    * @param <T> the type of options
    * @param options list of tuples where each tuple contains (value, weight)
    * @param seed random seed for deterministic results
    * @return the randomly selected option based on weights
    * @throws IllegalArgumentException if options is empty
    */
   public static <T> T getWeightedOption(List<Tuple<T, Integer>> options, long seed){
      ArrayList<T> weightedList = new ArrayList<>();
      for(Tuple<T, Integer> option : options){
         for(int i = 0; i < option.getB(); i++){
            weightedList.add(option.getA());
         }
      }
      Random random = new Random(seed);
      return weightedList.get(random.nextInt(weightedList.size()));
   }
   
   /**
    * Randomly distributes items across a range of positions without collision.
    *
    * <p>Given a list of items and a total size, this method assigns each item to
    * a unique random position. This is useful for placing items in slots, distributing
    * spawns across locations, or creating randomized layouts.
    *
    * <p>Example use case: Placing 5 quest items randomly in a 20-slot chest so they
    * don't stack together.
    *
    * @param <T> the type of items
    * @param items the items to distribute
    * @param size the total number of positions available
    * @param seed random seed for deterministic placement
    * @return list of tuples containing each item and its assigned position index
    * @throws IllegalArgumentException if size is less than items.size()
    */
   public static <T> List<Tuple<T, Integer>> randomlySpace(List<T> items, int size, long seed){
      Random random = new Random(seed);
      
      List<Integer> remaining = new ArrayList<>();
      List<Tuple<T, Integer>> randomized = new ArrayList<>();
      
      for(int i = 0; i < size; i++){
         remaining.add(i);
      }
      
      int i = 0;
      while(i < items.size() && !remaining.isEmpty()){
         int index = random.nextInt(remaining.size());
         randomized.add(new Tuple<>(items.get(i), remaining.get(index)));
         remaining.remove(remaining.get(index));
         i++;
      }
      
      return randomized;
   }
   
   /**
    * Extracts a specific page from a list for pagination display.
    *
    * <p>Pages are 1-indexed. If page is 0 or negative, returns the entire list.
    * If the page is out of bounds, returns an empty list.
    *
    * <h3>Example:</h3>
    * <pre>{@code
    * List<String> items = // ... 100 items
    * List<String> page1 = AlgoUtils.listToPage(items, 1, 10); // Items 0-9
    * List<String> page2 = AlgoUtils.listToPage(items, 2, 10); // Items 10-19
    * List<String> page11 = AlgoUtils.listToPage(items, 11, 10); // Empty (out of range)
    * }</pre>
    *
    * @param <T> the type of items in the list
    * @param items the full list to paginate
    * @param page the page number (1-indexed, 0 or negative returns full list)
    * @param pageSize the number of items per page
    * @return sublist containing the items for the requested page
    */
   public static <T> List<T> listToPage(List<T> items, int page, int pageSize){
      if(page <= 0){
         return items;
      }else if(pageSize * (page - 1) >= items.size()){
         return new ArrayList<>();
      }else{
         return items.subList(pageSize * (page - 1), Math.min(items.size(), pageSize * page));
      }
   }
   
   /**
    * Converts a binary string (containing only '0' and '1' characters) to Base64 encoding.
    *
    * <p>The binary string is first converted to bytes (MSB first), then encoded using
    * Java's standard Base64 encoder. Partial bytes are padded with zeros on the right.
    *
    * @param binaryString string containing binary digits (e.g., "10110101")
    * @return Base64-encoded string representation
    * @throws IllegalArgumentException if binaryString contains non-binary characters
    */
   public static String convertToBase64(String binaryString){
      int byteLength = (binaryString.length() + 7) / 8;
      byte[] byteArray = new byte[byteLength];
      
      for(int i = 0; i < binaryString.length(); i++){
         if(binaryString.charAt(i) == '1'){
            byteArray[i / 8] |= (byte) (1 << (7 - (i % 8)));
         }
      }
      
      return Base64.getEncoder().encodeToString(byteArray);
   }
   
   /**
    * Safely parses a UUID from a string, returning a blank UUID if parsing fails.
    *
    * <p>Instead of throwing {@link IllegalArgumentException} on invalid input,
    * this method returns {@code UUID.fromString(BorisLib.BLANK_UUID)} as a fallback.
    * This is useful when loading potentially corrupted data or accepting user input.
    *
    * @param str the string to parse as a UUID
    * @return the parsed UUID, or a blank UUID if parsing fails
    * @see BorisLib#BLANK_UUID
    */
   public static UUID getUUID(String str){
      try{
         return UUID.fromString(str);
      }catch(Exception e){
         return UUID.fromString(BorisLib.BLANK_UUID);
      }
   }
}
