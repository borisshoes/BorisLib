package net.borisshoes.borislib.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

public class MathUtils {
   
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
