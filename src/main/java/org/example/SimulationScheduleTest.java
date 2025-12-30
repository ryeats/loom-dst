/*
 * (c) Copyright 2025 Ryan Yeats. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class SimulationScheduleTest {
  public static void main(String... args) throws ExecutionException, InterruptedException {
    long seed = new SecureRandom().nextLong();
    System.out.println("Seed: " + seed + "L");
    String execFingerPrint = null;
    //    for (int i = 0; i < 100; i++) {
    for (int i = 0; i < 1; i++) {
      Simulation simulation = new Simulation(Duration.ofSeconds(30), seed, execFingerPrint);
      simulation.scheduleTestTask(() -> testSchedule("a"), Duration.ofMillis(2000));
      //      simulation.scheduleTestTask(() -> testSchedule("b"),Duration.ofMillis(2000));
      //      simulation.scheduleTestTask(() -> testSchedule("c"),Duration.ofMillis(3000));
      //      simulation.scheduleTestTask(() -> testSchedule("d"),Duration.ofMillis(1000));
      //      simulation.scheduleTestTask(() -> testSchedule("e"),Duration.ofMillis(2000));
      simulation.start();
      if (simulation.wasNonDeterminismDetected()) {
        // Non-deterministic
        System.out.print("ND ");
      } else {
        execFingerPrint = simulation.getExecFingerprint();
      }
      System.out.println(simulation.getExecFingerprint());
      Thread.sleep(1000);
    }
  }

  public static void testSchedule(String id) {
    System.out.print(id);
  }
}
