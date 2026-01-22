package net.borisshoes.borislib.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MathUtils {
   
   /**
    * Merges co-linear line segments in a list of line tuples.
    * Handles: shared endpoints, overlapping segments, and segments where one is inside another.
    */
   public static List<Tuple<Vec3, Vec3>> mergeColinearLines(List<Tuple<Vec3, Vec3>> lines){
      if(lines.size() < 2) return lines;
      
      List<Tuple<Vec3, Vec3>> merged = new ArrayList<>(lines);
      boolean changed = true;
      
      while(changed){
         changed = false;
         outer:
         for(int i = 0; i < merged.size(); i++){
            Tuple<Vec3, Vec3> line1 = merged.get(i);
            for(int j = i + 1; j < merged.size(); j++){
               Tuple<Vec3, Vec3> line2 = merged.get(j);
               
               // Check if lines are co-linear
               if(!areColinear(line1.getA(), line1.getB(), line2.getA(), line2.getB())){
                  continue;
               }
               
               // Lines are co-linear - check if they overlap or touch
               Tuple<Vec3, Vec3> mergedLine = mergeColinearSegments(line1, line2);
               if(mergedLine != null){
                  merged.remove(j);
                  merged.remove(i);
                  merged.add(mergedLine);
                  changed = true;
                  break outer;
               }
            }
         }
      }
      
      return merged;
   }
   
   /**
    * Checks if four points are co-linear (all lie on the same line).
    */
   private static boolean areColinear(Vec3 a, Vec3 b, Vec3 c, Vec3 d){
      // Check if c and d are on the line defined by a-b
      Vec3 ab = b.subtract(a);
      Vec3 ac = c.subtract(a);
      Vec3 ad = d.subtract(a);
      
      // Cross products should be zero if co-linear
      Vec3 cross1 = ab.cross(ac);
      Vec3 cross2 = ab.cross(ad);
      
      return cross1.lengthSqr() < 1e-9 && cross2.lengthSqr() < 1e-9;
   }
   
   /**
    * Attempts to merge two co-linear line segments.
    * Returns the merged segment if they overlap or touch, null otherwise.
    */
   private static Tuple<Vec3, Vec3> mergeColinearSegments(Tuple<Vec3, Vec3> line1, Tuple<Vec3, Vec3> line2){
      Vec3 a1 = line1.getA();
      Vec3 b1 = line1.getB();
      Vec3 a2 = line2.getA();
      Vec3 b2 = line2.getB();
      
      // Find the primary axis (the one with largest extent)
      Vec3 dir = b1.subtract(a1);
      if(dir.lengthSqr() < 1e-9) dir = b2.subtract(a2);
      if(dir.lengthSqr() < 1e-9) return new Tuple<>(a1, a1); // Degenerate case
      
      // Project all points onto the line direction to get 1D coordinates
      double t1a = projectOntoLine(a1, a1, dir);
      double t1b = projectOntoLine(b1, a1, dir);
      double t2a = projectOntoLine(a2, a1, dir);
      double t2b = projectOntoLine(b2, a1, dir);
      
      // Ensure t1a <= t1b and t2a <= t2b
      if(t1a > t1b){
         double tmp = t1a;
         t1a = t1b;
         t1b = tmp;
      }
      if(t2a > t2b){
         double tmp = t2a;
         t2a = t2b;
         t2b = tmp;
      }
      
      // Check if segments overlap or touch (with small epsilon for floating point)
      double epsilon = 1e-9;
      if(t1b < t2a - epsilon || t2b < t1a - epsilon){
         return null; // No overlap
      }
      
      // Merge: take the min and max extents
      double tMin = Math.min(t1a, t2a);
      double tMax = Math.max(t1b, t2b);
      
      // Convert back to Vec3
      Vec3 newA = a1.add(dir.normalize().scale(tMin));
      Vec3 newB = a1.add(dir.normalize().scale(tMax));
      
      return new Tuple<>(newA, newB);
   }
   
   /**
    * Projects a point onto a line defined by origin and direction, returning the scalar parameter.
    */
   private static double projectOntoLine(Vec3 point, Vec3 origin, Vec3 direction){
      Vec3 toPoint = point.subtract(origin);
      double dirLengthSqr = direction.lengthSqr();
      if(dirLengthSqr < 1e-9) return 0;
      return toPoint.dot(direction) / Math.sqrt(dirLengthSqr);
   }
   
   /**
    * Checks if the line segment between two points falls completely inside a set of blocks.
    * Uses ray marching to sample points along the line and verify each is within the block set.
    *
    * @param start  The starting point of the line
    * @param end    The ending point of the line
    * @param blocks The set of BlockPos that define the valid region
    * @return true if the entire line is inside the block set, false otherwise
    */
   public static boolean isLineInsideBlocks(Vec3 start, Vec3 end, Set<BlockPos> blocks, double stepSize){
      if(blocks.isEmpty()) return false;
      
      // Check start and end points
      BlockPos startBlock = BlockPos.containing(start);
      BlockPos endBlock = BlockPos.containing(end);
      if(!blocks.contains(startBlock) || !blocks.contains(endBlock)){
         return false;
      }
      
      // Ray march along the line, checking each point
      Vec3 direction = end.subtract(start);
      double length = direction.length();
      if(length < 0.001) return true; // Start and end are essentially the same point
      
      int steps = (int) Math.ceil(length / stepSize);
      Vec3 step = direction.normalize().scale(stepSize);
      
      Vec3 current = start;
      for(int i = 0; i <= steps; i++){
         BlockPos currentBlock = BlockPos.containing(current);
         if(!blocks.contains(currentBlock)){
            return false;
         }
         current = current.add(step);
      }
      
      return true;
   }
   
   public static boolean inCone(Vec3 center, Vec3 direction, double range, double closeWidth, double farWidth, Vec3 targetPos){
      final double angle = 2*Math.atan2((.5*(farWidth-closeWidth)),range);
      final double ha = angle/2;
      final double ri = closeWidth / (2*Math.sin(ha)); // Cone characteristics from given parameters
      final double ro = farWidth / (2*Math.sin(ha));
      // Delicious trigonometry and linear algebra at its finest
      Vec3 origin = center.add(direction.scale(-ri*Math.cos(ha)));
      Vec3 u = center.subtract(origin).normalize();           // Linear algebra black magic stuff which
      Vec3 uvr = targetPos.subtract(origin).normalize();      // finds the angle between cone axis and target
      double targetAngle = Math.acos(uvr.dot(u));
      double dist = targetPos.distanceTo(origin);
      double scalProj = targetPos.subtract(center).dot(direction.normalize()); // Scalar projection to see if target is in front of player
      boolean inAngle = targetAngle <= ha;
      boolean inRadius = dist <= ro;
      boolean inFront = scalProj > 0;
      
      return inAngle && inRadius && inFront;
   }
   
   public static double distToLine(Vec3 pos, Vec3 start, Vec3 end){
      final Vec3 line = end.subtract(start);
      final Vec3 distStart = pos.subtract(start);
      final Vec3 distEnd = pos.subtract(end);
      
      if(distStart.dot(line) <= 0) return distStart.length(); // Start is closest
      if(distEnd.dot(line) >= 0) return distEnd.length(); // End is closest
      return (line.cross(distStart)).length() / line.length(); // Infinite line case
   }
   
   public static Vec3 randomSpherePoint(Vec3 center, double range){
      Random random = new Random();
      double x = random.nextGaussian();
      double y = random.nextGaussian();
      double z = random.nextGaussian();
      
      double mag = Math.sqrt(x*x + y*y + z*z);
      x /= mag; y /= mag; z /= mag;
      
      double r = range* Math.cbrt(random.nextDouble());
      
      return new Vec3(x*r,y*r,z*r).add(center);
   }
   
   public static Vec3 randomSpherePoint(Vec3 center, double maxRange, double minRange){
      Random random = new Random();
      double x = random.nextGaussian();
      double y = random.nextGaussian();
      double z = random.nextGaussian();
      
      double mag = Math.sqrt(x*x + y*y + z*z);
      x /= mag; y /= mag; z /= mag;
      
      double r = maxRange*Math.cbrt(random.nextDouble(minRange / maxRange,1));
      
      return new Vec3(x*r,y*r,z*r).add(center);
   }
   
   public static Vec3 rotatePoint(Vec3 point, Vec3 direction, float roll){
      float pitch = (float) -Math.toDegrees(Math.asin(direction.y));
      float yaw = (float) -Math.toDegrees(Math.atan2(direction.x, direction.z));
      Quaternionf rotQuat1 = new Quaternionf().fromAxisAngleDeg(new Vector3f(0,1,0),-yaw-90);
      float sideAxisAngle = -(yaw+90) * ((float) Math.PI / 180);
      Vector3f sideAxis = new Vector3f((float) Math.sin(sideAxisAngle), 0, (float) Math.cos(sideAxisAngle));
      Quaternionf rotQuat2 = new Quaternionf().fromAxisAngleDeg(sideAxis,-pitch);
      Quaternionf rotQuat3 = new Quaternionf().fromAxisAngleDeg(direction.toVector3f(),roll);
      Quaternionf rotQuat = rotQuat3.mul(rotQuat2.mul(rotQuat1));
      return new Vec3(rotQuat.transform(point.toVector3f()));
   }
   
   public static Vec3 rotatePoint(Vec3 point, float yaw, float pitch, float roll){
      Quaternionf rotQuat1 = new Quaternionf().fromAxisAngleDeg(new Vector3f(0,1,0),-yaw-90);
      float sideAxisAngle = -(yaw+90) * ((float) Math.PI / 180);
      Vector3f sideAxis = new Vector3f((float) Math.sin(sideAxisAngle), 0, (float) Math.cos(sideAxisAngle));
      Quaternionf rotQuat2 = new Quaternionf().fromAxisAngleDeg(sideAxis,-pitch);
      Quaternionf rotQuat3 = new Quaternionf().fromAxisAngleDeg(Vec3.directionFromRotation(pitch,yaw).toVector3f(),roll);
      Quaternionf rotQuat = rotQuat3.mul(rotQuat2.mul(rotQuat1));
      return new Vec3(rotQuat.transform(point.toVector3f()));
   }
   
   public static boolean hitboxRaycast(Entity e, Vec3 start, Vec3 end){
      double range = .25;
      AABB entityBox = e.getBoundingBox().inflate(e.getPickRadius());
      double len = end.subtract(start).length();
      Vec3 trace = end.subtract(start).normalize().scale(range);
      int i = 0;
      Vec3 t2 = trace.scale(i);
      while(t2.length() < len){
         Vec3 t3 = start.add(t2);
         AABB hitBox = new AABB(t3.x-range,t3.y-range,t3.z-range,t3.x+range,t3.y+range,t3.z+range);
         if(entityBox.intersects(hitBox)){
            return true;
         }
         t2 = trace.scale(i);
         i++;
      }
      return false;
   }
}
