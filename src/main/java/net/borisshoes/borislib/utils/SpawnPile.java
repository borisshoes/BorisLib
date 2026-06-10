package net.borisshoes.borislib.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.ArrayList;

/**
 * Helper class for calculating and validating potential spawn locations in 2D and 3D space.
 *
 * <p>Used to find safe ground positions, distribute entity spawns, and perform vector-like
 * operations on horizontal coordinates.
 */
public class SpawnPile {
   /** Horizontal X coordinate. */
   double x;
   /** Horizontal Z coordinate. */
   double z;
   
   /**
    * Creates a new spawn pile at the specified coordinates.
    * @param x the x coordinate
    * @param z the z coordinate
    */
   public SpawnPile(double x, double z){
      this.x = x;
      this.z = z;
   }
   
   /**
    * Calculates the Euclidean distance between this pile and another.
    * @param other the other pile
    * @return the horizontal distance
    */
   double getDistance(SpawnPile other){
      double d = this.x - other.x;
      double e = this.z - other.z;
      return Math.sqrt(d * d + e * e);
   }
   
   /**
    * Normalizes the horizontal coordinates so the vector length is 1.0.
    */
   void normalize(){
      double d = this.absolute();
      this.x /= d;
      this.z /= d;
   }
   
   /**
    * Returns the horizontal distance from (0,0).
    * @return the absolute distance
    */
   double absolute(){
      return Math.sqrt(this.x * this.x + this.z * this.z);
   }
   
   /**
    * Subtracts coordinates of another pile from this one.
    * @param other the pile to subtract
    */
   public void subtract(SpawnPile other){
      this.x -= other.x;
      this.z -= other.z;
   }
   
   /**
    * Clamps the pile's coordinates within the specified bounds.
    * @param minX minimum X
    * @param minZ minimum Z
    * @param maxX maximum X
    * @param maxZ maximum Z
    * @return {@code true} if any coordinate was changed
    */
   public boolean clamp(double minX, double minZ, double maxX, double maxZ){
      boolean bl = false;
      if(this.x < minX){
         this.x = minX;
         bl = true;
      }else if(this.x > maxX){
         this.x = maxX;
         bl = true;
      }
      if(this.z < minZ){
         this.z = minZ;
         bl = true;
      }else if(this.z > maxZ){
         this.z = maxZ;
         bl = true;
      }
      return bl;
   }
   
   /**
    * Finds the Y coordinate of the highest solid surface at a given position.
    *
    * <p>Searches downwards from {@code maxY} looking for a solid block with at least
    * two air blocks above it.
    *
    * @param blockView the world access
    * @param maxY starting height for the search
    * @param x target X
    * @param z target Z
    * @return the surface Y coordinate
    */
   public static int getSurfaceY(BlockGetter blockView, int maxY, int x, int z){
      BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, (double) (maxY + 1), z);
      boolean bl = blockView.getBlockState(mutable).isAir();
      mutable.move(Direction.DOWN);
      boolean bl2 = blockView.getBlockState(mutable).isAir();
      while(mutable.getY() > blockView.getMinY()){
         mutable.move(Direction.DOWN);
         boolean bl3 = blockView.getBlockState(mutable).isAir();
         if(!bl3 && bl2 && bl){
            return mutable.getY() + 1;
         }
         bl = bl2;
         bl2 = bl3;
      }
      return maxY + 1;
   }
   
   /**
    * Finds an appropriate spawn position for an entity type at the specified horizontal coordinates.
    *
    * @param world the level
    * @param entityType the type of entity to spawn
    * @param x target X
    * @param z target Z
    * @param ignoreRestrictions whether to skip vanilla spawn rule checks
    * @return a potential spawn BlockPos
    */
   private static BlockPos getEntitySpawnPos(Level world, EntityType<?> entityType, int x, int z, boolean ignoreRestrictions){
      //int i = world.getTopY(SpawnRestriction.getHeightmapType(entityType), x, z);
      int i = world.getMaxY();
      BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, i, z);
      if(world.dimensionType().hasCeiling()){
         do{
            mutable.move(Direction.DOWN);
         }while(!world.getBlockState(mutable).isAir());
      }
      do{
         mutable.move(Direction.DOWN);
      }while(world.getBlockState(mutable).isAir() && mutable.getY() > world.getMinY());
      mutable.move(Direction.UP);
      
      if(ignoreRestrictions){
         return mutable.immutable();
      }else{
         return SpawnPlacements.getPlacementType(entityType).adjustSpawnPosition(world, mutable.immutable());
      }
      
      
   }
   
   /**
    * Generates a list of valid spawn locations for a specific entity type around a center point.
    *
    * @param num number of locations to find
    * @param range maximum horizontal distance from center
    * @param world the level
    * @param entityType the entity type
    * @param center center point of the search area
    * @return a list of found BlockPos
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, ServerLevel world, EntityType<?> entityType, BlockPos center){
      ArrayList<BlockPos> positions = new ArrayList<>();
      for(int i = 0; i < num; i++){
         BlockPos entitySpawnPos;
         int tries = 0;
         do{
            int x = center.getX() + world.getRandom().nextInt(range * 2) - range;
            int z = center.getZ() + world.getRandom().nextInt(range * 2) - range;
            entitySpawnPos = getEntitySpawnPos(world, entityType, x, z, true);
            tries++;
         }while(Math.abs(entitySpawnPos.getY() - center.getY()) > range && tries < 10000);
         positions.add(entitySpawnPos);
      }
      return positions;
   }
   
   /**
    * Gets the surface Y coordinate at this pile's horizontal position.
    * @param blockView the world access
    * @param maxY search start height
    * @return the surface Y
    */
   public int getY(BlockGetter blockView, int maxY){
      BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(this.x, (double) (maxY + 1), this.z);
      boolean bl = blockView.getBlockState(mutable).isAir();
      mutable.move(Direction.DOWN);
      boolean bl2 = blockView.getBlockState(mutable).isAir();
      while(mutable.getY() > blockView.getMinY()){
         mutable.move(Direction.DOWN);
         boolean bl3 = blockView.getBlockState(mutable).isAir();
         if(!bl3 && bl2 && bl){
            return mutable.getY() + 1;
         }
         bl = bl2;
         bl2 = bl3;
      }
      return maxY + 1;
   }
   
   /**
    * Checks if the ground at this pile's position is safe for mob spawning.
    *
    * <p>Verifies:
    * <ul>
    *    <li>Position is below maxY</li>
    *    <li>Not inside a fluid</li>
    *    <li>Not on damaging/preventative blocks (Wither Rose, Cactus, Sweet Berry, etc.)</li>
    * </ul>
    *
    * @param world the world access
    * @param maxY maximum allowed height
    * @return {@code true} if position is considered safe
    */
   public boolean isSafe(BlockGetter world, int maxY){
      BlockPos blockPos = BlockPos.containing(this.x, (double) (this.getY(world, maxY) - 1), this.z);
      BlockState blockState = world.getBlockState(blockPos);
      FluidState fluidState = world.getFluidState(blockPos);
      boolean invalid = blockState.is(Blocks.WITHER_ROSE) || blockState.is(Blocks.SWEET_BERRY_BUSH) || blockState.is(Blocks.CACTUS) || blockState.is(Blocks.POWDER_SNOW) || blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) || WalkNodeEvaluator.isBurningBlock(blockState);
      return blockPos.getY() < maxY && fluidState.isEmpty() && !invalid;
   }
   
   /**
    * Sets the pile to a random location within the given bounds.
    * @param random source of randomness
    * @param minX min X
    * @param minZ min Z
    * @param maxX max X
    * @param maxZ max Z
    */
   public void setPileLocation(RandomSource random, double minX, double minZ, double maxX, double maxZ){
      this.x = Mth.nextDouble(random, minX, maxX);
      this.z = Mth.nextDouble(random, minZ, maxZ);
   }
   
   /**
    * Generates valid spawn locations around (0,0,0).
    * @see #makeSpawnLocations(int, int, ServerLevel, BlockPos)
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, ServerLevel world){
      return makeSpawnLocations(num, range, world, new BlockPos(0, 0, 0));
   }
   
   /**
    * Generates valid spawn locations around a center point with default maxY (128).
    * @see #makeSpawnLocations(int, int, int, ServerLevel, BlockPos)
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, ServerLevel world, BlockPos center){
      return makeSpawnLocations(num, range, 128, world, center);
   }
   
   /**
    * Generates a specific number of safe spawn locations within a range.
    *
    * @param num number of locations to find
    * @param range horizontal range from center
    * @param maxY maximum allowed spawn height
    * @param world the level
    * @param center center point
    * @return valid spawn positions
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, int maxY, ServerLevel world, BlockPos center){
      ArrayList<BlockPos> positions = new ArrayList<>();
      for(int i = 0; i < num; i++){
         SpawnPile pile;
         int tries = 0;
         do{
            int x = center.getX() + world.getRandom().nextInt(range * 2) - range;
            int z = center.getZ() + world.getRandom().nextInt(range * 2) - range;
            pile = new SpawnPile(x, z);
            tries++;
         }while(!pile.isSafe(world, maxY) && tries < 10000);
         positions.add(BlockPos.containing(pile.x, pile.getY(world, maxY), pile.z));
      }
      return positions;
   }
}