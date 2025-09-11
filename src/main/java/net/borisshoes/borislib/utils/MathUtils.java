package net.borisshoes.borislib.utils;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

public class MathUtils {
   
   public static boolean inCone(Vec3d center, Vec3d direction, double range, double closeWidth, double farWidth, Vec3d targetPos){
      final double angle = 2*Math.atan2((.5*(farWidth-closeWidth)),range);
      final double ha = angle/2;
      final double ri = closeWidth / (2*Math.sin(ha)); // Cone characteristics from given parameters
      final double ro = farWidth / (2*Math.sin(ha));
      // Delicious trigonometry and linear algebra at its finest
      Vec3d origin = center.add(direction.multiply(-ri*Math.cos(ha)));
      Vec3d u = center.subtract(origin).normalize();           // Linear algebra black magic stuff which
      Vec3d uvr = targetPos.subtract(origin).normalize();      // finds the angle between cone axis and target
      double targetAngle = Math.acos(uvr.dotProduct(u));
      double dist = targetPos.distanceTo(origin);
      double scalProj = targetPos.subtract(center).dotProduct(direction.normalize()); // Scalar projection to see if target is in front of player
      boolean inAngle = targetAngle <= ha;
      boolean inRadius = dist <= ro;
      boolean inFront = scalProj > 0;
      
      return inAngle && inRadius && inFront;
   }
   
   public static double distToLine(Vec3d pos, Vec3d start, Vec3d end){
      final Vec3d line = end.subtract(start);
      final Vec3d distStart = pos.subtract(start);
      final Vec3d distEnd = pos.subtract(end);
      
      if(distStart.dotProduct(line) <= 0) return distStart.length(); // Start is closest
      if(distEnd.dotProduct(line) >= 0) return distEnd.length(); // End is closest
      return (line.crossProduct(distStart)).length() / line.length(); // Infinite line case
   }
   
   public static Vec3d randomSpherePoint(Vec3d center, double range){
      Random random = new Random();
      double x = random.nextGaussian();
      double y = random.nextGaussian();
      double z = random.nextGaussian();
      
      double mag = Math.sqrt(x*x + y*y + z*z);
      x /= mag; y /= mag; z /= mag;
      
      double r = range* Math.cbrt(random.nextDouble());
      
      return new Vec3d(x*r,y*r,z*r).add(center);
   }
   
   public static Vec3d randomSpherePoint(Vec3d center, double maxRange, double minRange){
      Random random = new Random();
      double x = random.nextGaussian();
      double y = random.nextGaussian();
      double z = random.nextGaussian();
      
      double mag = Math.sqrt(x*x + y*y + z*z);
      x /= mag; y /= mag; z /= mag;
      
      double r = maxRange*Math.cbrt(random.nextDouble(minRange / maxRange,1));
      
      return new Vec3d(x*r,y*r,z*r).add(center);
   }
   
   public static Vec3d rotatePoint(Vec3d point, Vec3d direction, float roll){
      float pitch = (float) -Math.toDegrees(Math.asin(direction.y));
      float yaw = (float) -Math.toDegrees(Math.atan2(direction.x, direction.z));
      Quaternionf rotQuat1 = new Quaternionf().fromAxisAngleDeg(new Vector3f(0,1,0),-yaw-90);
      float sideAxisAngle = -(yaw+90) * ((float) Math.PI / 180);
      Vector3f sideAxis = new Vector3f((float) Math.sin(sideAxisAngle), 0, (float) Math.cos(sideAxisAngle));
      Quaternionf rotQuat2 = new Quaternionf().fromAxisAngleDeg(sideAxis,-pitch);
      Quaternionf rotQuat3 = new Quaternionf().fromAxisAngleDeg(direction.toVector3f(),roll);
      Quaternionf rotQuat = rotQuat3.mul(rotQuat2.mul(rotQuat1));
      return new Vec3d(rotQuat.transform(point.toVector3f()));
   }
   
   public static Vec3d rotatePoint(Vec3d point, float yaw, float pitch, float roll){
      Quaternionf rotQuat1 = new Quaternionf().fromAxisAngleDeg(new Vector3f(0,1,0),-yaw-90);
      float sideAxisAngle = -(yaw+90) * ((float) Math.PI / 180);
      Vector3f sideAxis = new Vector3f((float) Math.sin(sideAxisAngle), 0, (float) Math.cos(sideAxisAngle));
      Quaternionf rotQuat2 = new Quaternionf().fromAxisAngleDeg(sideAxis,-pitch);
      Quaternionf rotQuat3 = new Quaternionf().fromAxisAngleDeg(Vec3d.fromPolar(pitch,yaw).toVector3f(),roll);
      Quaternionf rotQuat = rotQuat3.mul(rotQuat2.mul(rotQuat1));
      return new Vec3d(rotQuat.transform(point.toVector3f()));
   }
   
   public static boolean hitboxRaycast(Entity e, Vec3d start, Vec3d end){
      double range = .25;
      Box entityBox = e.getBoundingBox().expand(e.getTargetingMargin());
      double len = end.subtract(start).length();
      Vec3d trace = end.subtract(start).normalize().multiply(range);
      int i = 0;
      Vec3d t2 = trace.multiply(i);
      while(t2.length() < len){
         Vec3d t3 = start.add(t2);
         Box hitBox = new Box(t3.x-range,t3.y-range,t3.z-range,t3.x+range,t3.y+range,t3.z+range);
         if(entityBox.intersects(hitBox)){
            return true;
         }
         t2 = trace.multiply(i);
         i++;
      }
      return false;
   }
}
