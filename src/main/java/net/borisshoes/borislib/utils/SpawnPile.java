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
 * Helper class for generating safe spawn locations in a Minecraft world.
 *
 * <p>A {@code SpawnPile} represents a 2D position (x, z coordinate pair) and provides utilities for:</p>
 * <ul>
 *    <li>Finding the surface Y-coordinate at that (x, z) position.</li>
 *    <li>Checking if the location is safe for entity spawning (not blocked by hazards like lava, cacti,
 *        wither roses, etc.).</li>
 *    <li>Batch-generating multiple spawn locations within a range around a center point, optionally
 *        filtering for safe spawns.</li>
 * </ul>
 *
 * <p>The static {@link #makeSpawnLocations} methods are typically the main entry points.</p>
 */
public class SpawnPile {
   double x;
   double z;
   
   /**
    * Constructs a new spawn pile at the given (x, z) coordinates.
    *
    * @param x the x-coordinate
    * @param z the z-coordinate
    */
   public SpawnPile(double x, double z){
      this.x = x;
      this.z = z;
   }
   
   /**
    * Computes the Euclidean distance to another spawn pile in 2D (ignores Y coordinate).
    *
    * @param other the other pile
    * @return the distance in blocks
    */
   double getDistance(SpawnPile other){
      double d = this.x - other.x;
      double e = this.z - other.z;
      return Math.sqrt(d * d + e * e);
   }
   
   /**
    * Normalizes this pile's (x, z) vector to unit length in place.
    */
   void normalize(){
      double d = this.absolute();
      this.x /= d;
      this.z /= d;
   }
   
   /**
    * @return the magnitude (length) of this pile's (x, z) vector
    */
   double absolute(){
      return Math.sqrt(this.x * this.x + this.z * this.z);
   }
   
   /**
    * Subtracts another pile's (x, z) coordinates from this pile's coordinates in place.
    *
    * @param other the pile to subtract
    */
   public void subtract(SpawnPile other){
      this.x -= other.x;
      this.z -= other.z;
   }
   
   /**
    * Clamps this pile's coordinates to the specified 2D bounding box.
    *
    * @param minX the minimum x
    * @param minZ the minimum z
    * @param maxX the maximum x
    * @param maxZ the maximum z
    * @return {@code true} if at least one coordinate was clamped
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
    * Finds the surface Y-coordinate at the given (x, z) location. The surface is defined as the first
    * solid block encountered when scanning downward from {@code maxY}, where the two blocks above it
    * are both air.
    *
    * @param blockView the world or region to scan
    * @param maxY      the starting Y coordinate for the downward scan
    * @param x         the x coordinate
    * @param z         the z coordinate
    * @return the Y coordinate of the surface, or {@code maxY + 1} if no valid surface is found
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
    * Generates a list of spawn locations within the specified range around a center point. Uses the
    * entity type's natural spawn constraints.
    *
    * @param num        the number of spawn positions to generate
    * @param range      the horizontal radius in blocks
    * @param world      the server level
    * @param entityType the type of entity being spawned (determines spawn-type constraints)
    * @param center     the center position
    * @return a list of block positions suitable for spawning the given entity type
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, ServerLevel world, EntityType<?> entityType, BlockPos center){
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
    * Finds the surface Y-coordinate at this pile's (x, z) position using the same surface-detection
    * logic as {@link #getSurfaceY(BlockGetter, int, int, int)}.
    *
    * @param blockView the world or region to scan
    * @param maxY      the starting Y coordinate for downward scan
    * @return the Y coordinate of the surface, or {@code maxY + 1} if no valid surface found
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
    * Checks whether this pile's location is safe for entity spawning. A location is unsafe if the
    * surface block is a hazard (wither rose, cactus, powder snow, lava, etc.) or is outside the
    * valid Y range.
    *
    * @param world the world to check
    * @param maxY  the maximum Y bound
    * @return {@code true} if this location is safe for spawning
    */
   public boolean isSafe(BlockGetter world, int maxY){
      BlockPos blockPos = BlockPos.containing(this.x, (double) (this.getY(world, maxY) - 1), this.z);
      BlockState blockState = world.getBlockState(blockPos);
      FluidState fluidState = world.getFluidState(blockPos);
      boolean invalid = blockState.is(Blocks.WITHER_ROSE) || blockState.is(Blocks.SWEET_BERRY_BUSH) || blockState.is(Blocks.CACTUS) || blockState.is(Blocks.POWDER_SNOW) || blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) || WalkNodeEvaluator.isBurningBlock(blockState);
      return blockPos.getY() < maxY && fluidState.isEmpty() && !invalid;
   }
   
   /**
    * Sets this pile's coordinates to a random position within the specified bounding box.
    *
    * @param random the random source
    * @param minX   the minimum x
    * @param minZ   the minimum z
    * @param maxX   the maximum x
    * @param maxZ   the maximum z
    */
   public void setPileLocation(RandomSource random, double minX, double minZ, double maxX, double maxZ){
      this.x = Mth.nextDouble(random, minX, maxX);
      this.z = Mth.nextDouble(random, minZ, maxZ);
   }
   
   /**
    * Generates spawn locations around the origin (0, 0, 0).
    *
    * @param num   the number of spawn positions to generate
    * @param range the horizontal radius in blocks
    * @param world the server level
    * @return a list of safe spawn positions
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, ServerLevel world){
      return makeSpawnLocations(num, range, world, new BlockPos(0, 0, 0));
   }
   
   /**
    * Generates spawn locations around the specified center with a default max Y of 128.
    *
    * @param num    the number of spawn positions to generate
    * @param range  the horizontal radius in blocks
    * @param world  the server level
    * @param center the center position
    * @return a list of safe spawn positions
    */
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, ServerLevel world, BlockPos center){
      return makeSpawnLocations(num, range, 128, world, center);
   }
   
   /**
    * Generates spawn locations around the specified center, filtering for safety. This is the most
    * configurable overload, allowing you to specify the max Y limit and center position.
    *
    * @param num    the number of spawn positions to generate
    * @param range  the horizontal radius in blocks
    * @param maxY   the maximum Y coordinate for the search
    * @param world  the server level
    * @param center the center position
    * @return a list of safe spawn positions (attempts up to 10,000 tries per position)
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