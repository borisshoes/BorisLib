package net.borisshoes.borislib.utils;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.timers.GenericTimer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ParticleEffectUtils {
   
   public static final double PHI = (1 + Math.sqrt(5)) / 2.0;
   
   public static void lightningBolt(ServerLevel world, Vec3 p1, Vec3 p2, int numSegments, double maxDevDist, ParticleOptions type, int particlesPerBlock, int count, double delta, double speed, boolean longDist){
      if(numSegments <= 0) return;
      List<Vec3> points = new ArrayList<>();
      points.add(p1);
      double dx = (p2.x-p1.x)/numSegments;
      double dy = (p2.y-p1.y)/numSegments;
      double dz = (p2.z-p1.z)/numSegments;
      for(int i = 0; i < numSegments-1; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         points.add(MathUtils.randomSpherePoint(new Vec3(x,y,z),maxDevDist));
      }
      points.add(p2);
      
      for(int i = 1; i < points.size(); i++){
         Vec3 ps = points.get(i-1);
         Vec3 pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         if(longDist){
            line(world,null,ps,pe,type,intervals,count,delta,speed);
         }else{
            longDistLine(world,ps,pe,type,intervals,count,delta,speed);
         }
      }
   }
   
   public static void trackedAnimatedLightningBolt(ServerLevel world, Supplier<Vec3> s1, Supplier<Vec3> s2, int numSegments, double maxDevDist, ParticleOptions type, int particlesPerBlock, int count, double delta, double speed, boolean longDist, int persistMod, int duration){
      if(numSegments <= 0) return;
      List<Vec3> points = new ArrayList<>();
      Vec3 p1 = s1.get();
      Vec3 p2 = s2.get();
      points.add(p1);
      double dx = (p2.x-p1.x)/numSegments;
      double dy = (p2.y-p1.y)/numSegments;
      double dz = (p2.z-p1.z)/numSegments;
      for(int i = 0; i < numSegments-1; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         points.add(MathUtils.randomSpherePoint(new Vec3(x,y,z),maxDevDist));
      }
      points.add(p2);
      
      int particleCount = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3 ps = points.get(i-1);
         Vec3 pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         particleCount += intervals;
      }
      
      float particlesPerTick = (float) particleCount / duration;
      HashMap<Supplier<Vec3>, Integer> pp = new HashMap<>();
      
      int c = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3 ps = points.get(i-1);
         Vec3 pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         dx = (pe.x-ps.x)/intervals;
         dy = (pe.y-ps.y)/intervals;
         dz = (pe.z-ps.z)/intervals;
         for(int j = 0; j < intervals; j++){
            final double x = ps.x + dx * j;
            final double y = ps.y + dy * j;
            final double z = ps.z + dz * j;
            
            pp.put(() -> {
               Vec3 basis = p2.subtract(p1);
               Vec3 newBasis = s2.get().subtract(s1.get());
               double magDiff = newBasis.length() / basis.length();
               Quaternionf transform = new Quaternionf().rotationTo(basis.toVector3f(), newBasis.toVector3f());
               Vec3 deltaV = new Vec3(x,y,z).subtract(p1);
               Vec3 newDeltaV = new Vec3(transform.transform(deltaV.toVector3f()));
               Vec3 normalizedBasis = newBasis.normalize();
               double projectionMagnitude = newDeltaV.dot(normalizedBasis);
               newDeltaV = normalizedBasis.scale(projectionMagnitude * magDiff).add(newDeltaV.subtract(normalizedBasis.scale(projectionMagnitude)));
               return newDeltaV.add(s1.get());
            },Math.round(c / particlesPerTick));
            c++;
         }
      }
      
      animatedLightningBoltHelper(world,pp,type,count,delta,speed,longDist,persistMod,0);
   }
   
   public static void animatedLightningBolt(ServerLevel world, Vec3 p1, Vec3 p2, int numSegments, double maxDevDist, ParticleOptions type, int particlesPerBlock, int count, double delta, double speed, boolean longDist, int persistMod, int duration){
      if(numSegments <= 0) return;
      List<Vec3> points = new ArrayList<>();
      points.add(p1);
      double dx = (p2.x-p1.x)/numSegments;
      double dy = (p2.y-p1.y)/numSegments;
      double dz = (p2.z-p1.z)/numSegments;
      for(int i = 0; i < numSegments-1; i++){
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         points.add(MathUtils.randomSpherePoint(new Vec3(x,y,z),maxDevDist));
      }
      points.add(p2);
      
      int particleCount = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3 ps = points.get(i-1);
         Vec3 pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         particleCount += intervals;
      }
      
      float particlesPerTick = (float) particleCount / duration;
      HashMap<Supplier<Vec3>, Integer> pp = new HashMap<>();
      
      int c = 0;
      for(int i = 1; i < points.size(); i++){
         Vec3 ps = points.get(i-1);
         Vec3 pe = points.get(i);
         int intervals = (int) (pe.subtract(ps).length() * particlesPerBlock);
         
         dx = (pe.x-ps.x)/intervals;
         dy = (pe.y-ps.y)/intervals;
         dz = (pe.z-ps.z)/intervals;
         for(int j = 0; j < intervals; j++){
            double x = ps.x + dx * j;
            double y = ps.y + dy * j;
            double z = ps.z + dz * j;
            
            pp.put(() -> new Vec3(x,y,z),Math.round(c / particlesPerTick));
            c++;
         }
      }
      
      animatedLightningBoltHelper(world,pp,type,count,delta,speed,longDist,persistMod,0);
   }
   
   private static void animatedLightningBoltHelper(ServerLevel world, HashMap<Supplier<Vec3>, Integer> points, ParticleOptions type, int count, double delta, double speed, boolean longDist, int persistMod, int tick){
      int highestTick = 0;
      for(Map.Entry<Supplier<Vec3>, Integer> entry : points.entrySet()){
         int pTick = entry.getValue();
         Vec3 point = entry.getKey().get();
         if(pTick > highestTick) highestTick = pTick;
         
         if(!(persistMod > 0 && tick % persistMod == 0 && pTick < tick) && pTick != tick) continue;
         
         if(longDist){
            spawnLongParticle(world,type,point.x,point.y,point.z,delta,delta,delta,speed,count);
         }else{
            world.sendParticles(type,point.x,point.y,point.z,count,delta,delta,delta,speed);
         }
      }
      
      if(tick < highestTick){
         BorisLib.addTickTimerCallback(world, new GenericTimer(1, () -> animatedLightningBoltHelper(world, points, type, count, delta, speed, longDist, persistMod, tick+1)));
      }
   }
   
   public static void longDistLine(ServerLevel world, Vec3 p1, Vec3 p2, ParticleOptions type, int intervals, int count, double delta, double speed){
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
   
   public static void line(ServerLevel world, @Nullable ServerPlayer player, Vec3 p1, Vec3 p2, ParticleOptions type, int intervals, int count, double delta, double speed){
      line(world, player, p1, p2, type, intervals, count, delta, speed,1);
   }
   
   public static void line(ServerLevel world, @Nullable ServerPlayer player, Vec3 p1, Vec3 p2, ParticleOptions type, int intervals, int count, double delta, double speed, double percent){
      percent = Mth.clamp(percent,0,1);
      double dx = (p2.x-p1.x)/intervals;
      double dy = (p2.y-p1.y)/intervals;
      double dz = (p2.z-p1.z)/intervals;
      for(int i = 0; i < intervals; i++){
         if((double)i/intervals > percent && percent != 1) continue;
         double x = p1.x + dx*i;
         double y = p1.y + dy*i;
         double z = p1.z + dz*i;
         
         if(player == null){
            world.sendParticles(type,x,y,z,count,delta,delta,delta,speed);
         }else{
            world.sendParticles(player,type,false,true,x,y,z,count,delta,delta,delta,speed);
         }
      }
   }
   
   public static void longDistCircle(ServerLevel world, Vec3 center, ParticleOptions type, double radius, int intervals, int count, double delta, double speed){
      double dA = Math.PI * 2 / intervals;
      for(int i = 0; i < intervals; i++){
         double angle = dA * i;
         double x = radius * Math.cos(angle) + center.x;
         double z = radius * Math.sin(angle) + center.z;
         double y = center.y;
         
         spawnLongParticle(world,type,x,y,z,delta,delta,delta,speed,count);
      }
   }
   
   public static void circle(ServerLevel world, @Nullable ServerPlayer player, Vec3 center, ParticleOptions type, double radius, int intervals, int count, double delta, double speed){
      circle(world,player,center,type,radius,intervals,count,delta,speed,0);
   }
   
   public static void circle(ServerLevel world, @Nullable ServerPlayer player, Vec3 center, ParticleOptions type, double radius, int intervals, int count, double delta, double speed, double theta){
      double dA = Math.PI * 2 / intervals;
      for(int i = 0; i < intervals; i++){
         double angle = dA * i + theta;
         double x = radius * Math.cos(angle) + center.x;
         double z = radius * Math.sin(angle) + center.z;
         double y = center.y;
         
         if(player == null){
            world.sendParticles(type,x,y,z,count,delta,delta,delta,speed);
         }else{
            world.sendParticles(player,type,false,true,x,y,z,count,delta,delta,delta,speed);
         }
      }
   }
   
   public static List<Vec3> getCirclePoints(Vec3 center, double radius, int intervals, double theta){
      List<Vec3> points = new ArrayList<>();
      double dA = Math.PI * 2 / intervals;
      for(int i = 0; i < intervals; i++){
         double angle = dA * i + theta;
         double x = radius * Math.cos(angle) + center.x;
         double z = radius * Math.sin(angle) + center.z;
         double y = center.y;
         points.add(new Vec3(x,y,z));
      }
      return points;
   }
   
   public static void longDistSphere(ServerLevel world, Vec3 center, ParticleOptions type, double radius, int points, int count, double delta, double speed, double theta){
      double phi = Math.PI * (3 - Math.sqrt(5));
      
      for(int i = 0; i < points; i++){
         // Fibonacci Sphere Equations
         double y = 1 - (i / (double)(points-1)) * 2;
         double r = Math.sqrt(1-y*y);
         double t = phi*i + theta;
         double x = Math.cos(t) * r;
         double z = Math.sin(t) * r;
         
         // Center Offset and Radius Scale
         Vec3 point = new Vec3(x,y,z);
         point = point.scale(radius).add(center.x, center.y, center.z);
         
         spawnLongParticle(world,type,point.x,point.y,point.z,delta,delta,delta,speed,count);
      }
   }
   
   public static void sphere(ServerLevel world, @Nullable ServerPlayer player, Vec3 center, ParticleOptions type, double radius, int points, int count, double delta, double speed, double theta){
      double phi = Math.PI * (3 - Math.sqrt(5));
      
      for(int i = 0; i < points; i++){
         // Fibonacci Sphere Equations
         double y = 1 - (i / (double)(points-1)) * 2;
         double r = Math.sqrt(1-y*y);
         double t = phi*i + theta;
         double x = Math.cos(t) * r;
         double z = Math.sin(t) * r;
         
         // Center Offset and Radius Scale
         Vec3 point = new Vec3(x,y,z);
         point = point.scale(radius).add(center.x, center.y, center.z);
         
         if(player == null){
            world.sendParticles(type,point.x,point.y,point.z,count,delta,delta,delta,speed);
         }else{
            world.sendParticles(player,type,false,true,point.x,point.y,point.z,count,delta,delta,delta,speed);
         }
      }
   }
   // Notes about the Dust Particle, size goes from .01 to 4, you can use an int represented rgb value with new Vector3f(Vec3d.unpackRgb(int))
   
   public static void spawnLongParticle(ServerLevel world, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz, double speed, int count){
      int viewChunks = world.getServer().getPlayerList().getViewDistance();
      int sqBlockDist = (viewChunks*16)*(viewChunks*16);
      List<ServerPlayer> players = world.getPlayers(player -> player.distanceToSqr(new Vec3(x,y,z)) < sqBlockDist);
      for(ServerPlayer player : players){
         player.connection.send(new ClientboundLevelParticlesPacket(type,true,true,x,y,z,(float)dx,(float)dy,(float)dz,(float)speed,count));
      }
   }
   
   public static int adjustTime(int tick, double speedMod){
      return (int) (((int)(tick / speedMod)) * speedMod);
   }
   
   public static List<Tuple<Vec3, Vec3>> getIcosahedronPairs(List<Vec3> icosPoints){
      List<Tuple<Vec3, Vec3>> icosOutlines = new ArrayList<>();
      icosOutlines.add(new Tuple<>(icosPoints.get(0),icosPoints.get(1)));
      icosOutlines.add(new Tuple<>(icosPoints.get(0),icosPoints.get(4)));
      icosOutlines.add(new Tuple<>(icosPoints.get(0),icosPoints.get(7)));
      icosOutlines.add(new Tuple<>(icosPoints.get(0),icosPoints.get(8)));
      icosOutlines.add(new Tuple<>(icosPoints.get(0),icosPoints.get(11)));
      icosOutlines.add(new Tuple<>(icosPoints.get(9),icosPoints.get(2)));
      icosOutlines.add(new Tuple<>(icosPoints.get(9),icosPoints.get(3)));
      icosOutlines.add(new Tuple<>(icosPoints.get(9),icosPoints.get(4)));
      icosOutlines.add(new Tuple<>(icosPoints.get(9),icosPoints.get(5)));
      icosOutlines.add(new Tuple<>(icosPoints.get(9),icosPoints.get(8)));
      icosOutlines.add(new Tuple<>(icosPoints.get(10),icosPoints.get(7)));
      icosOutlines.add(new Tuple<>(icosPoints.get(10),icosPoints.get(3)));
      icosOutlines.add(new Tuple<>(icosPoints.get(10),icosPoints.get(6)));
      icosOutlines.add(new Tuple<>(icosPoints.get(10),icosPoints.get(11)));
      icosOutlines.add(new Tuple<>(icosPoints.get(10),icosPoints.get(2)));
      icosOutlines.add(new Tuple<>(icosPoints.get(4),icosPoints.get(8)));
      icosOutlines.add(new Tuple<>(icosPoints.get(4),icosPoints.get(7)));
      icosOutlines.add(new Tuple<>(icosPoints.get(4),icosPoints.get(3)));
      icosOutlines.add(new Tuple<>(icosPoints.get(7),icosPoints.get(3)));
      icosOutlines.add(new Tuple<>(icosPoints.get(3),icosPoints.get(2)));
      icosOutlines.add(new Tuple<>(icosPoints.get(6),icosPoints.get(2)));
      icosOutlines.add(new Tuple<>(icosPoints.get(6),icosPoints.get(1)));
      icosOutlines.add(new Tuple<>(icosPoints.get(6),icosPoints.get(11)));
      icosOutlines.add(new Tuple<>(icosPoints.get(6),icosPoints.get(5)));
      icosOutlines.add(new Tuple<>(icosPoints.get(7),icosPoints.get(11)));
      icosOutlines.add(new Tuple<>(icosPoints.get(2),icosPoints.get(5)));
      icosOutlines.add(new Tuple<>(icosPoints.get(1),icosPoints.get(5)));
      icosOutlines.add(new Tuple<>(icosPoints.get(8),icosPoints.get(5)));
      icosOutlines.add(new Tuple<>(icosPoints.get(1),icosPoints.get(11)));
      icosOutlines.add(new Tuple<>(icosPoints.get(1),icosPoints.get(8)));
      return icosOutlines;
   }
   
   public static List<Vec3> getIcosahedronPoints(){
      List<Vec3> icosPoints = new ArrayList<>();
      icosPoints.add(new Vec3(0,1,PHI));
      icosPoints.add(new Vec3(0,-1,PHI));
      icosPoints.add(new Vec3(0,-1,-PHI));
      icosPoints.add(new Vec3(0,1,-PHI));
      icosPoints.add(new Vec3(-1,PHI,0));
      icosPoints.add(new Vec3(-1,-PHI,0));
      icosPoints.add(new Vec3(1,-PHI,0));
      icosPoints.add(new Vec3(1,PHI,0));
      icosPoints.add(new Vec3(-PHI,0,1));
      icosPoints.add(new Vec3(-PHI,0,-1));
      icosPoints.add(new Vec3(PHI,0,-1));
      icosPoints.add(new Vec3(PHI,0,1));
      return icosPoints;
   }
}
