package net.borisshoes.borislib.utils;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A mutable wrapper around Minecraft's {@link ItemContainerContents} component that provides
 * convenient methods for manipulating container items with built-in validation and slot management.
 *
 * <p>This class simplifies working with container items (like bundles, shulker boxes, or custom
 * containers) by providing:
 * <ul>
 *   <li><b>Mutable operations</b> on item lists</li>
 *   <li><b>Slot limits</b> with optional max slot enforcement</li>
 *   <li><b>Custom validation</b> via predicates that determine which items can be added</li>
 *   <li><b>Smart item stacking</b> that respects max stack sizes</li>
 *   <li><b>Conversion</b> between mutable and immutable representations</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Load from an item component
 * ItemStack bundle = // ... get bundle item
 * ItemContainerContents contents = bundle.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
 * ItemContainerContentsMutable mutable = ItemContainerContentsMutable.fromComponent(contents, 64);
 *
 * // Configure restrictions
 * mutable.setMaxSlots(64); // Max 64 slots
 * mutable.setPredicate((stack, items) -> stack.getItem().canFitInsideContainerItems());
 *
 * // Try to add items
 * ItemStack toAdd = new ItemStack(Items.DIAMOND, 32);
 * ItemStack remaining = mutable.tryAddStackToContainerComp(toAdd);
 *
 * // Save back to component
 * if (remaining.isEmpty()) {
 *     bundle.set(DataComponents.CONTAINER, mutable.toImmutable());
 * }
 * }</pre>
 *
 * @see ItemContainerContents The immutable Minecraft component this wraps
 * @see MinecraftUtils#tryAddStackToContainerComp(ItemContainerContents, int, ItemStack) For static helper
 */
public class ItemContainerContentsMutable {
   private final List<ItemStack> items;
   private int maxSlots = -1;
   private BiPredicate<ItemStack, List<ItemStack>> predicate;
   
   /**
    * Creates a mutable container from an existing list of items.
    *
    * @param items the list of items to wrap (is copied, not stored directly)
    */
   public ItemContainerContentsMutable(List<ItemStack> items){
      this.items = new ArrayList<>(items);
   }
   
   /**
    * Creates a mutable container from a varargs array of items.
    *
    * @param items the items to include
    */
   public ItemContainerContentsMutable(ItemStack... items){
      this.items = new ArrayList<>(Arrays.asList(items));
   }
   
   /**
    * Creates a mutable container from an immutable {@link ItemContainerContents} component.
    *
    * @param component the component to convert
    */
   public ItemContainerContentsMutable(ItemContainerContents component){
      this.items = component.allItemsCopyStream().collect(Collectors.toCollection(ArrayList::new));
   }
   
   /**
    * Creates a mutable container from an immutable {@link ItemContainerContents} component
    * with a fixed size, padding with empty stacks as needed.
    *
    * @param component the component to convert
    * @param size the fixed size to use (sets maxSlots automatically)
    */
   public ItemContainerContentsMutable(ItemContainerContents component, int size){
      NonNullList<ItemStack> list = NonNullList.withSize(size, ItemStack.EMPTY);
      component.copyInto(list);
      this.items = new ArrayList<>(list);
      this.maxSlots = size;
   }
   
   /**
    * Static factory method to create from an immutable component.
    *
    * @param component the component to convert
    * @return a new mutable wrapper
    */
   public static ItemContainerContentsMutable fromComponent(ItemContainerContents component){
      return new ItemContainerContentsMutable(component);
   }
   
   /**
    * Static factory method to create from an immutable component with a fixed size.
    *
    * @param component the component to convert
    * @param size the fixed size
    * @return a new mutable wrapper
    */
   public static ItemContainerContentsMutable fromComponent(ItemContainerContents component, int size){
      return new ItemContainerContentsMutable(component, size);
   }
   
   /**
    * Sets the maximum number of slots allowed in this container.
    *
    * @param maxSlots the max slots (-1 for unlimited)
    * @return this instance for method chaining
    */
   public ItemContainerContentsMutable setMaxSlots(int maxSlots){
      this.maxSlots = maxSlots;
      return this;
   }
   
   /**
    * Sets a validation predicate that determines which items can be added.
    *
    * <p>The predicate receives the item being added and the current list of items.
    * Return {@code true} to allow the addition, {@code false} to reject it.
    *
    * <h3>Example:</h3>
    * <pre>{@code
    * // Only allow diamonds
    * container.setPredicate((stack, items) -> stack.is(Items.DIAMOND));
    *
    * // Limit to 3 unique item types
    * container.setPredicate((stack, items) ->
    *     items.stream().map(ItemStack::getItem).distinct().count() < 3);
    * }</pre>
    *
    * @param predicate the validation predicate
    * @return this instance for method chaining
    */
   public ItemContainerContentsMutable setPredicate(BiPredicate<ItemStack, List<ItemStack>> predicate){
      this.predicate = predicate;
      return this;
   }
   
   /**
    * Gets the current maximum slot limit.
    *
    * @return the max slots (-1 means unlimited)
    */
   public int getMaxSlots(){
      return maxSlots;
   }
   
   /**
    * Gets the current validation predicate.
    *
    * @return the predicate, or null if none is set
    */
   public BiPredicate<ItemStack, List<ItemStack>> getPredicate(){
      return predicate;
   }
   
   /**
    * Sets an item at a specific slot, respecting validation predicates.
    *
    * @param slot the slot index
    * @param stack the item to set
    * @return {@code true} if the item was set successfully, {@code false} if rejected
    */
   public boolean setItem(int slot, ItemStack stack){
      if(slot < 0 || slot >= items.size()) return false;
      if(maxSlots >= 0 && slot >= maxSlots) return false;
      if(predicate != null && !predicate.test(stack, items)) return false;
      items.set(slot, stack);
      return true;
   }
   
   /**
    * Adds an item to the container, respecting max slots and validation predicates.
    *
    * @param stack the item to add
    * @return {@code true} if the item was added successfully, {@code false} if rejected
    */
   public boolean addItem(ItemStack stack){
      if(maxSlots >= 0 && items.size() >= maxSlots) return false;
      if(predicate != null && !predicate.test(stack, items)) return false;
      return items.add(stack);
   }
   
   /**
    * Gets the item at a specific slot.
    *
    * @param slot the slot index
    * @return the item stack, or {@link ItemStack#EMPTY} if the slot is invalid or empty
    */
   public ItemStack getItem(int slot){
      if(slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
      if(maxSlots >= 0 && slot >= maxSlots) return ItemStack.EMPTY;
      ItemStack stack = items.get(slot);
      return stack != null ? stack : ItemStack.EMPTY;
   }
   
   /**
    * Gets the internal mutable list of items.
    *
    * <p><b>Warning:</b> Direct modification of this list will affect the container's state.
    * Use this when you need full control, otherwise use the provided methods.
    *
    * @return the internal list of items
    */
   public List<ItemStack> getItems(){
      return items;
   }
   
   /**
    * Gets a filtered list containing only non-empty item stacks.
    *
    * @return list of non-empty stacks
    */
   public List<ItemStack> getNonEmpty(){
      return items.stream().filter(stack -> !stack.isEmpty()).toList();
   }
   
   /**
    * Returns a stream of all item stacks (including empty), with each stack copied.
    *
    * @return stream of copied item stacks
    */
   public Stream<ItemStack> getAllCopyStream(){
      return items.stream().map(ItemStack::copy);
   }
   
   /**
    * Returns a stream of only non-empty stacks, with each stack copied.
    *
    * @return stream of copied non-empty item stacks
    */
   public Stream<ItemStack> getNonEmptyCopyStream(){
      return items.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
   }
   
   /**
    * Returns a list of copies of all items.
    *
    * @return list of copied item stacks
    */
   public List<ItemStack> getAllCopy(){
      return getAllCopyStream().toList();
   }
   
   /**
    * Returns a list of copies of only non-empty items.
    *
    * @return list of copied non-empty item stacks
    */
   public List<ItemStack> getNonEmptyCopy(){
      return getNonEmptyCopyStream().toList();
   }
   
   /**
    * Converts this mutable container back to an immutable {@link ItemContainerContents}.
    *
    * @return the immutable component representation
    */
   public ItemContainerContents toImmutable(){
      return ItemContainerContents.fromItems(items);
   }
   
   /**
    * Removes and returns the first non-empty item stack, clearing it from the container.
    *
    * @return the removed item, or {@link ItemStack#EMPTY} if all slots are empty
    */
   public ItemStack tryRemoveFirstNonEmpty(){
      for(ItemStack stack : items){
         if(stack != null && !stack.isEmpty()){
            return stack.copyAndClear();
         }
      }
      return ItemStack.EMPTY;
   }
   
   /**
    * Removes and returns the last non-empty item stack, clearing it from the container.
    *
    * @return the removed item, or {@link ItemStack#EMPTY} if all slots are empty
    */
   public ItemStack tryRemoveLastNonEmpty(){
      for(ItemStack stack : items.reversed()){
         if(stack != null && !stack.isEmpty()){
            return stack.copyAndClear();
         }
      }
      return ItemStack.EMPTY;
   }
   
   /**
    * Attempts to add a stack to the container, combining with existing stacks when possible.
    *
    * <p>This method implements smart item insertion:
    * <ol>
    *   <li>First, fills up existing partial stacks of the same item type</li>
    *   <li>Then, fills empty slots with the remaining items</li>
    *   <li>Finally, adds new slots if under the max slot limit</li>
    * </ol>
    *
    * <p>The provided stack is mutated by this method - its count is reduced as items are added.
    *
    * @param stack the stack to add (will be mutated)
    * @return the same stack instance with remaining items, or empty if everything fit
    * @see #canInsertPerfectly(ItemStack) To check if items will fit before adding
    */
   public ItemStack tryAddStackToContainerComp(ItemStack stack){
      for(ItemStack existingStack : items){
         if(existingStack == null || existingStack.isEmpty()) continue;
         if(stack.isEmpty()) break;
         int curCount = stack.getCount();
         
         boolean canCombine = ItemStack.isSameItemSameComponents(existingStack, stack) && existingStack.isStackable() && existingStack.getCount() < existingStack.getMaxStackSize();
         if(canCombine){
            int toAdd = Math.min(existingStack.getMaxStackSize() - existingStack.getCount(), curCount);
            existingStack.grow(toAdd);
            stack.setCount(curCount - toAdd);
         }
      }
      
      if(stack.isEmpty()) return stack;
      if(predicate != null && !predicate.test(stack, items)) return stack;
      
      for(int i = 0; i < items.size(); i++){
         ItemStack item = items.get(i);
         if(item == null || item.isEmpty()){
            items.set(i,stack.copyAndClear());
            return stack;
         }
      }
      
      if(maxSlots >= 0 && items.size() >= maxSlots) return stack;
      items.add(stack.copyAndClear());
      return stack;
   }
   
   /**
    * Checks if a stack can be completely inserted without any items remaining.
    *
    * <p>This method considers:
    * <ul>
    *   <li>Existing partial stacks that can be filled</li>
    *   <li>Empty slots available</li>
    *   <li>Max slot limits</li>
    *   <li>Validation predicates</li>
    * </ul>
    *
    * @param stack the stack to test
    * @return {@code true} if all items will fit, {@code false} if some will remain
    */
   public boolean canInsertPerfectly(ItemStack stack){
      if(stack.isEmpty()) return true;
      int remaining = stack.getCount();
      
      boolean hasEmptySlot = false;
      for(ItemStack existingStack : items){
         if(existingStack == null || existingStack.isEmpty()){
            hasEmptySlot = true;
            continue;
         }
         if(ItemStack.isSameItemSameComponents(existingStack, stack) && existingStack.isStackable()){
            remaining -= (existingStack.getMaxStackSize() - existingStack.getCount());
            if(remaining <= 0) return true;
         }
      }
      
      if(predicate != null && !predicate.test(stack, items)) return false;
      return hasEmptySlot || maxSlots < 0 || items.size() < maxSlots;
   }
   
   @Override
   public int hashCode(){
      return ItemStack.hashStackList(items);
   }
}
