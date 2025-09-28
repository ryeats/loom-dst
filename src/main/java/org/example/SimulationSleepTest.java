package org.example;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class SimulationSleepTest {

  public static void main(String... args) throws ExecutionException, InterruptedException {
//        long seed = new SecureRandom().nextLong();
    long seed = -4745267424838260031L;
    System.out.println("Seed: " + seed + "L");
    String execFingerPrint = null;
    Simulation simulation = new Simulation(Duration.ofSeconds(30), seed, execFingerPrint);
    simulation.scheduleTestTask(()->testSleep("a",1000));
    simulation.scheduleTestTask(()->testSleep("b",1000));
    simulation.start();
    System.out.println("\n "+simulation.getExecFingerprint());

  }
//  public static void main(String... args) throws ExecutionException, InterruptedException {
//    long seed = new SecureRandom().nextLong();
//    System.out.println("Seed: " + seed + "L");
//    String execFingerPrint = null;
//    for (int i = 0; i < 100; i++) {
//      Simulation simulation = new Simulation(Duration.ofSeconds(30), seed, execFingerPrint);
//      simulation.scheduleTestTask(() -> doubleSleep("a",2000,1000));
//      simulation.scheduleTestTask(() -> doubleSleep("b",2000,2000));
//      simulation.scheduleTestTask(() -> doubleSleep("c",3000,1000));
//      simulation.scheduleTestTask(() -> doubleSleep("d",1000,2000));
//      simulation.scheduleTestTask(() -> doubleSleep("e",2000,1000));
//      simulation.start();
//      if (simulation.wasNonDeterminismDetected()) {
//        // Non-deterministic
//        System.out.print("ND ");
//      } else {
//        execFingerPrint = simulation.getExecFingerprint();
//      }
//      System.out.println(simulation.getExecFingerprint());
//      Thread.sleep(1000);
//    }
//  }

  public static void testSleep(String id,long sleep)
  {
    try {
      System.out.print(id);
      Thread.sleep(sleep);
      System.out.print(id);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
  public static void doubleSleep(String id,long sleep,long sleep2)
  {
    try {
      System.out.print(id);
      Thread.sleep(sleep);
      System.out.print(id);
      Thread.sleep(sleep2);
      System.out.print(id);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
