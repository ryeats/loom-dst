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
package org.example.time;

import java.time.Instant;

public class TimeTest {

  // build and then run with jvm arg -javaagent:./target/java-dst-0.1-SNAPSHOT.jar
  public static void main(String... args) throws InterruptedException {

    System.out.println("Current time of +" + System.currentTimeMillis());
    SimulationTime.time.incrementAndGet();
    System.out.println("Current time of +" + System.currentTimeMillis());
    SimulationTime.time.set(42);
    System.out.println("Current time of +" + System.currentTimeMillis());
    System.out.println("Current time of +" + Instant.now());
    System.out.println("Current time of +" + System.nanoTime());
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                Thread.sleep(1);
                System.out.println("slept");
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            })
        .run();
    System.out.println("main thread");
    Thread.sleep(10);
    SimulationTime.time.incrementAndGet();
    SimulationTime.time.incrementAndGet();
    SimulationTime.time.incrementAndGet();
    SimulationTime.time.incrementAndGet();
    SimulationTime.time.incrementAndGet();
    Thread.sleep(10);
  }
}
