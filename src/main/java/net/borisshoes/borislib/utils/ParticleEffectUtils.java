package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.timers.GenericTimer;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ParticleEffectUtils {
   
   public static final double PHI = (1 + Math.sqrt(5)) / 2.0;
   
   public static void lightningBolt(ServerWorld world, Vec3d p1, Vec3d p2, int numSegments, double maxDevDist, ParticleEffect type, int particlesPerBlock, int count, double delta, double speed, boolean longDist){
      if(numSegments <= 0) return;
      List<Vec3d> points = new ArrayList<>();
      points.add(p1);
      double dx = (p2.x-p1.x)/numSegments;
      double dy = (p2.y-p1.y)/numSegments;
      double dz = (p2.z-p1.z)/numSegments;
      for(int i = 0; i < numSegments-1; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         points.add(MathUtils.randomSpherePoint(new Vec3d(x,y,z),maxDevDist));
      }
      points.add(p2);
      
      for(int i = 1; i < points.size(); i++){
         Vec3d ps = points.get(i-1);
         Vec3d pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         if(longDist){
            line(world,null,ps,pe,type,intervals,count,delta,speed);
         }else{
            longDistLine(world,ps,pe,type,intervals,count,delta,speed);
         }
      }
   }
   
   public static void trackedAnimatedLightningBolt(ServerWorld world, Supplier<Vec3d> s1, Supplier<Vec3d> s2, int numSegments, double maxDevDist, ParticleEffect type, int particlesPerBlock, int count, double delta, double speed, boolean longDist, int persistMod, int duration){
      if(numSegments <= 0) return;
      List<Vec3d> points = new ArrayList<>();
      Vec3d p1 = s1.get();
      Vec3d p2 = s2.get();
      points.add(p1);
      double dx = (p2.x-p1.x)/numSegments;
      double dy = (p2.y-p1.y)/numSegments;
      double dz = (p2.z-p1.z)/numSegments;
      for(int i = 0; i < numSegments-1; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         points.add(MathUtils.randomSpherePoint(new Vec3d(x,y,z),maxDevDist));
      }
      points.add(p2);
      
      int particleCount = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3d ps = points.get(i-1);
         Vec3d pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         particleCount += intervals;
      }
      
      float particlesPerTick = (float) particleCount / duration;
      HashMap<Supplier<Vec3d>, Integer> pp = new HashMap<>();
      
      int c = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3d ps = points.get(i-1);
         Vec3d pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         dx = (pe.x-ps.x)/intervals;
         dy = (pe.y-ps.y)/intervals;
         dz = (pe.z-ps.z)/intervals;
         for(int j = 0; j < intervals; j++){
            final double x = ps.x + dx * j;
            final double y = ps.y + dy * j;
            final double z = ps.z + dz * j;
            
            pp.put(() -> {
               Vec3d basis = p2.subtract(p1);
               Vec3d newBasis = s2.get().subtract(s1.get());
               double magDiff = newBasis.length() / basis.length();
               Quaternionf transform = new Quaternionf().rotationTo(basis.toVector3f(), newBasis.toVector3f());
               Vec3d deltaV = new Vec3d(x,y,z).subtract(p1);
               Vec3d newDeltaV = new Vec3d(transform.transform(deltaV.toVector3f()));
               Vec3d normalizedBasis = newBasis.normalize();
               double projectionMagnitude = newDeltaV.dotProduct(normalizedBasis);
               newDeltaV = normalizedBasis.multiply(projectionMagnitude * magDiff).add(newDeltaV.subtract(normalizedBasis.multiply(projectionMagnitude)));
               return newDeltaV.add(s1.get());
            },Math.round(c / particlesPerTick));
            c++;
         }
      }
      
      animatedLightningBoltHelper(world,pp,type,count,delta,speed,longDist,persistMod,0);
   }
   
   public static void animatedLightningBolt(ServerWorld world, Vec3d p1, Vec3d p2, int numSegments, double maxDevDist, ParticleEffect type, int particlesPerBlock, int count, double delta, double speed, boolean longDist, int persistMod, int duration){
      if(numSegments <= 0) return;
      List<Vec3d> points = new ArrayList<>();
      points.add(p1);
      double dx = (p2.x-p1.x)/numSegments;
      double dy = (p2.y-p1.y)/numSegments;
      double dz = (p2.z-p1.z)/numSegments;
      for(int i = 0; i < numSegments-1; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         points.add(MathUtils.randomSpherePoint(new Vec3d(x,y,z),maxDevDist));
      }
      points.add(p2);
      
      int particleCount = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3d ps = points.get(i-1);
         Vec3d pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         particleCount += intervals;
      }
      
      float particlesPerTick = (float) particleCount / duration;
      HashMap<Supplier<Vec3d>, Integer> pp = new HashMap<>();
      
      int c = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3d ps = points.get(i-1);
         Vec3d pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         dx = (pe.x-ps.x)/intervals;
         dy = (pe.y-ps.y)/intervals;
         dz = (pe.z-ps.z)/intervals;
         for(int j = 0; j < intervals; j++){
            double x = ps.x + dx * j;
            double y = ps.y + dy * j;
            double z = ps.z + dz * j;
            
            pp.put(() -> new Vec3d(x,y,z),Math.round(c / particlesPerTick));
            c++;
         }
      }
      
      animatedLightningBoltHelper(world,pp,type,count,delta,speed,longDist,persistMod,0);
   }
   
   private static void animatedLightningBoltHelper(ServerWorld world, HashMap<Supplier<Vec3d>, Integer> points, ParticleEffect type, int count, double delta, double speed, boolean longDist, int persistMod, int tick){
      int highestTick = 0;
      for(Map.Entry<Supplier<Vec3d>, Integer> entry : points.entrySet()){
         int pTick = entry.getValue();
         Vec3d point = entry.getKey().get();
         if(pTick > highestTick) highestTick = pTick;
         
         if(!(persistMod > 0 && tick % persistMod == 0 && pTick < tick) && pTick != tick) continue;
         
         if(longDist){
            spawnLongParticle(world,type,point.x,point.y,point.z,delta,delta,delta,speed,count);
         }else{
            world.spawnParticles(type,point.x,point.y,point.z,count,delta,delta,delta,speed);
         }
      }
      
      if(tick < highestTick){
         BorisLib.addTickTimerCallback(world, new GenericTimer(1, () -> animatedLightningBoltHelper(world, points, type, count, delta, speed, longDist, persistMod, tick+1)));
      }
   }
   
   public static void longDistLine(ServerWorld world, Vec3d p1, Vec3d p2, ParticleEffect type, int intervals, int count, double delta, double speed){
      double dx = (p2.x-p1.x)/intervals;
      double dy = (p2.y-p1.y)/intervals;
      double dz = (p2.z-p1.z)/intervals;
      for(int i = 0; i < intervals; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         
         spawnLongParticle(world,type,x,y,z,delta,delta,delta,speed,count);
      }
   }
   
   public static void line(ServerWorld world, @Nullable ServerPlayerEntity player, Vec3d p1, Vec3d p2, ParticleEffect type, int intervals, int count, double delta, double speed){
      line(world, player, p1, p2, type, intervals, count, delta, speed,1);
   }
   
   public static void line(ServerWorld world, @Nullable ServerPlayerEntity player, Vec3d p1, Vec3d p2, ParticleEffect type, int intervals, int count, double delta, double speed, double percent){
      percent = MathHelper.clamp(percent,0,1);
      double dx = (p2.x-p1.x)/intervals;
      double dy = (p2.y-p1.y)/intervals;
      double dz = (p2.z-p1.z)/intervals;
      for(int i = 0; i < intervals; i++){
         if((double)i/intervals > percent && percent != 1) continue;
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         
         if(player == null){
            world.spawnParticles(type,x,y,z,count,delta,delta,delta,speed);
         }else{
            world.spawnParticles(player,type,false,true,x,y,z,count,delta,delta,delta,speed);
         }
      }
   }
   
   public static void longDistCircle(ServerWorld world, Vec3d center, ParticleEffect type, double radius, int intervals, int count, double delta, double speed){
      double dA = Math.PI * 2 / intervals;
      for(int i = 0; i < intervals; i++){
         double angle = dA * i;
         double x = radius * Math.cos(angle) + center.x;
         double z = radius * Math.sin(angle) + center.z;
         double y = center.y;
         
         spawnLongParticle(world,type,x,y,z,delta,delta,delta,speed,count);
      }
   }
   
   public static void circle(ServerWorld world, @Nullable ServerPlayerEntity player, Vec3d center, ParticleEffect type, double radius, int intervals, int count, double delta, double speed){
      circle(world,player,center,type,radius,intervals,count,delta,speed,0);
   }
   
   public static void circle(ServerWorld world, @Nullable ServerPlayerEntity player, Vec3d center, ParticleEffect type, double radius, int intervals, int count, double delta, double speed, double theta){
      double dA = Math.PI * 2 / intervals;
      for(int i = 0; i < intervals; i++){
         double angle = dA * i + theta;
         double x = radius * Math.cos(angle) + center.x;
         double z = radius * Math.sin(angle) + center.z;
         double y = center.y;
         
         if(player == null){
            world.spawnParticles(type,x,y,z,count,delta,delta,delta,speed);
         }else{
            world.spawnParticles(player,type,false,true,x,y,z,count,delta,delta,delta,speed);
         }
      }
   }
   
   public static List<Vec3d> getCirclePoints(Vec3d center, double radius, int intervals, double theta){
      List<Vec3d> points = new ArrayList<>();
      double dA = Math.PI * 2 / intervals;
      for(int i = 0; i < intervals; i++){
         double angle = dA * i + theta;
         double x = radius * Math.cos(angle) + center.x;
         double z = radius * Math.sin(angle) + center.z;
         double y = center.y;
         points.add(new Vec3d(x,y,z));
      }
      return points;
   }
   
   public static void longDistSphere(ServerWorld world, Vec3d center, ParticleEffect type, double radius, int points, int count, double delta, double speed, double theta){
      double phi = Math.PI * (3 - Math.sqrt(5));
      
      for(int i = 0; i < points; i++){
         // Fibonacci Sphere Equations
         double y = 1 - (i / (double)(points-1)) * 2;
         double r = Math.sqrt(1-y*y);
         double t = phi*i + theta;
         double x = Math.cos(t) * r;
         double z = Math.sin(t) * r;
         
         // Center Offset and Radius Scale
         Vec3d point = new Vec3d(x,y,z);
         point = point.multiply(radius).add(center.x, center.y, center.z);
         
         spawnLongParticle(world,type,point.x,point.y,point.z,delta,delta,delta,speed,count);
      }
   }
   
   public static void sphere(ServerWorld world, @Nullable ServerPlayerEntity player, Vec3d center, ParticleEffect type, double radius, int points, int count, double delta, double speed, double theta){
      double phi = Math.PI * (3 - Math.sqrt(5));
      
      for(int i = 0; i < points; i++){
         // Fibonacci Sphere Equations
         double y = 1 - (i / (double)(points-1)) * 2;
         double r = Math.sqrt(1-y*y);
         double t = phi*i + theta;
         double x = Math.cos(t) * r;
         double z = Math.sin(t) * r;
         
         // Center Offset and Radius Scale
         Vec3d point = new Vec3d(x,y,z);
         point = point.multiply(radius).add(center.x, center.y, center.z);
         
         if(player == null){
            world.spawnParticles(type,point.x,point.y,point.z,count,delta,delta,delta,speed);
         }else{
            world.spawnParticles(player,type,false,true,point.x,point.y,point.z,count,delta,delta,delta,speed);
         }
      }
   }
   // Notes about the Dust Particle, size goes from .01 to 4, you can use an int represented rgb value with new Vector3f(Vec3d.unpackRgb(int))
   
   public static void spawnLongParticle(ServerWorld world, ParticleEffect type, double x, double y, double z, double dx, double dy, double dz, double speed, int count){
      int viewChunks = world.getServer().getPlayerManager().getViewDistance();
      int sqBlockDist = (viewChunks*16)*(viewChunks*16);
      List<ServerPlayerEntity> players = world.getPlayers(player -> player.squaredDistanceTo(new Vec3d(x,y,z)) < sqBlockDist);
      for(ServerPlayerEntity player : players){
         player.networkHandler.sendPacket(new ParticleS2CPacket(type,true,true,x,y,z,(float)dx,(float)dy,(float)dz,(float)speed,count));
      }
   }
   
   public static int adjustTime(int tick, double speedMod){
      return (int) (((int)(tick / speedMod)) * speedMod);
   }
   
   public static List<Pair<Vec3d,Vec3d>> getIcosahedronPairs(List<Vec3d> icosPoints){
      List<Pair<Vec3d,Vec3d>> icosOutlines = new ArrayList<>();
      icosOutlines.add(new Pair<>(icosPoints.get(0),icosPoints.get(1)));
      icosOutlines.add(new Pair<>(icosPoints.get(0),icosPoints.get(4)));
      icosOutlines.add(new Pair<>(icosPoints.get(0),icosPoints.get(7)));
      icosOutlines.add(new Pair<>(icosPoints.get(0),icosPoints.get(8)));
      icosOutlines.add(new Pair<>(icosPoints.get(0),icosPoints.get(11)));
      icosOutlines.add(new Pair<>(icosPoints.get(9),icosPoints.get(2)));
      icosOutlines.add(new Pair<>(icosPoints.get(9),icosPoints.get(3)));
      icosOutlines.add(new Pair<>(icosPoints.get(9),icosPoints.get(4)));
      icosOutlines.add(new Pair<>(icosPoints.get(9),icosPoints.get(5)));
      icosOutlines.add(new Pair<>(icosPoints.get(9),icosPoints.get(8)));
      icosOutlines.add(new Pair<>(icosPoints.get(10),icosPoints.get(7)));
      icosOutlines.add(new Pair<>(icosPoints.get(10),icosPoints.get(3)));
      icosOutlines.add(new Pair<>(icosPoints.get(10),icosPoints.get(6)));
      icosOutlines.add(new Pair<>(icosPoints.get(10),icosPoints.get(11)));
      icosOutlines.add(new Pair<>(icosPoints.get(10),icosPoints.get(2)));
      icosOutlines.add(new Pair<>(icosPoints.get(4),icosPoints.get(8)));
      icosOutlines.add(new Pair<>(icosPoints.get(4),icosPoints.get(7)));
      icosOutlines.add(new Pair<>(icosPoints.get(4),icosPoints.get(3)));
      icosOutlines.add(new Pair<>(icosPoints.get(7),icosPoints.get(3)));
      icosOutlines.add(new Pair<>(icosPoints.get(3),icosPoints.get(2)));
      icosOutlines.add(new Pair<>(icosPoints.get(6),icosPoints.get(2)));
      icosOutlines.add(new Pair<>(icosPoints.get(6),icosPoints.get(1)));
      icosOutlines.add(new Pair<>(icosPoints.get(6),icosPoints.get(11)));
      icosOutlines.add(new Pair<>(icosPoints.get(6),icosPoints.get(5)));
      icosOutlines.add(new Pair<>(icosPoints.get(7),icosPoints.get(11)));
      icosOutlines.add(new Pair<>(icosPoints.get(2),icosPoints.get(5)));
      icosOutlines.add(new Pair<>(icosPoints.get(1),icosPoints.get(5)));
      icosOutlines.add(new Pair<>(icosPoints.get(8),icosPoints.get(5)));
      icosOutlines.add(new Pair<>(icosPoints.get(1),icosPoints.get(11)));
      icosOutlines.add(new Pair<>(icosPoints.get(1),icosPoints.get(8)));
      return icosOutlines;
   }
   
   public static List<Vec3d> getIcosahedronPoints(){
      List<Vec3d> icosPoints = new ArrayList<>();
      icosPoints.add(new Vec3d(0,1,PHI));
      icosPoints.add(new Vec3d(0,-1,PHI));
      icosPoints.add(new Vec3d(0,-1,-PHI));
      icosPoints.add(new Vec3d(0,1,-PHI));
      icosPoints.add(new Vec3d(-1,PHI,0));
      icosPoints.add(new Vec3d(-1,-PHI,0));
      icosPoints.add(new Vec3d(1,-PHI,0));
      icosPoints.add(new Vec3d(1,PHI,0));
      icosPoints.add(new Vec3d(-PHI,0,1));
      icosPoints.add(new Vec3d(-PHI,0,-1));
      icosPoints.add(new Vec3d(PHI,0,-1));
      icosPoints.add(new Vec3d(PHI,0,1));
      return icosPoints;
   }
}
