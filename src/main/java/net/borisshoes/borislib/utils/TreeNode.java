package net.borisshoes.borislib.utils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A generic tree node representing one element in a hierarchical tree structure.
 *
 * <p>Each node holds data of type {@code T}, a set of children, and an optional single parent.
 * Provides methods to:</p>
 * <ul>
 *    <li>Query depth, leaf / root status.</li>
 *    <li>Add / remove children.</li>
 *    <li>Count or collect descendant nodes.</li>
 * </ul>
 *
 * @param <T> the type of data held by the node
 */
public class TreeNode<T> {
   private final T data;
   private Set<TreeNode<T>> children;
   private TreeNode<T> parent;
   
   /**
    * Constructs a new tree node.
    *
    * @param data     the data this node holds
    * @param children the set of child nodes (mutable)
    * @param parent   the parent node, or {@code null} if this node is a root
    */
   public TreeNode(T data, Set<TreeNode<T>> children, @Nullable TreeNode<T> parent){
      this.data = data;
      this.children = children;
      this.parent = parent;
   }
   
   /**
    * @return the depth (distance from the root) of this node. The root node has depth 0.
    */
   public int getDepth(){
      if(parent == null){
         return 0;
      }else{
         return parent.getDepth() + 1;
      }
   }
   
   /** @return {@code true} if this node has no children. */
   public boolean isLeaf(){
      return children.isEmpty();
   }
   
   /** @return {@code true} if this node has no parent (i.e. it is the root). */
   public boolean isRoot(){
      return parent == null;
   }
   
   /** @return the set of child nodes (mutable). */
   public Set<TreeNode<T>> getChildren(){
      return children;
   }
   
   /**
    * Adds a child to this node's child set. Does not update the child's parent field automatically.
    *
    * @param child the child to add
    */
   public void addChild(TreeNode<T> child){
      this.children.add(child);
   }
   
   /**
    * Removes a child from this node's child set.
    *
    * @param child the child to remove
    */
   public void removeChild(TreeNode<T> child){
      this.children.remove(child);
   }
   
   /** @return this node's parent (may be {@code null}). */
   public TreeNode<?> getParent(){
      return parent;
   }
   
   /**
    * Replaces this node's parent reference.
    *
    * @param parent the new parent
    */
   public void setParent(TreeNode<T> parent){
      this.parent = parent;
   }
   
   /** @return the data held by this node. */
   public T getData(){
      return data;
   }
   
   /**
    * Counts the total number of descendants (not including this node).
    *
    * @return the number of descendant nodes
    */
   public int countAllDescendants(){
      int count = 0;
      List<TreeNode<?>> nodes = new ArrayList<>(children);
      
      Iterator<TreeNode<?>> iter = nodes.iterator();
      while(iter.hasNext()){
         nodes.addAll(iter.next().children);
         iter.remove();
         count++;
      }
      
      return count;
   }
   
   /**
    * Recursively collects all leaf nodes in the subtree rooted at this node (including this node if
    * it is a leaf).
    *
    * @param list the accumulator list (modified in place)
    * @return the same list (for chaining)
    */
   public List<TreeNode<T>> getAllLeaves(List<TreeNode<T>> list){
      if(isLeaf()){
         list.add(this);
      }else{
         for(TreeNode<T> child : this.children){
            child.getAllLeaves(list);
         }
      }
      return list;
   }
   
   /**
    * Recursively collects all nodes in the subtree rooted at this node (including this node itself).
    *
    * @param list the accumulator list (modified in place)
    * @return the same list (for chaining)
    */
   public List<TreeNode<T>> getAllNodes(List<TreeNode<T>> list){
      list.add(this);
      for(TreeNode<T> child : this.children){
         child.getAllNodes(list);
      }
      
      return list;
   }
}
