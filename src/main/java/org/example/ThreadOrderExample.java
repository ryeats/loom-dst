package org.example;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadOrderExample {
  /*
   Run with java 24 jvm args: --add-opens=java.base/java.lang=ALL-UNNAMED
  */
  public static void main(String[] args) throws InterruptedException {
    System.out.println("8 Threads");
    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      times(executor, 4);
    }
    //Single threaded is deterministic, but you don't get thread interleaving
    System.out.println("Single Threaded always A,AAB,BBC,CCD,DDE,EEF,FFG,GGH,HH");
    try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
      times(executor, 4);
    }
    System.out.println("Virtual Threads");
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      times(executor, 4);
    }
    long seed = new Random().nextLong();
    System.out.println("Deterministic Virtual Threads Seed: " + seed);
    for (int i = 0; i < 4; i++) {
      DeterministicTestExecutor executor = new DeterministicTestExecutor(new Random(seed));
      StringBuffer buffer = new StringBuffer();
      submitAppendTasks(executor, buffer);
      executor.drain();
      Thread.sleep(100); // Wait for threads to wake up
      executor.drain(); // The slept threads get added back to the queue so we have to drain again
      // which is not great for deterministic simulation because its system time not simulation time
      System.out.println("Result: " + buffer);
    }

    System.out.println("Deterministic Virtual ThreadFactory: "+seed);
    for (int i = 0; i < 4; i++) {
      DeterministicExecutor de = new DeterministicExecutor(new Random(seed));
      SchedulableVirtualThreadFactory tf = new SchedulableVirtualThreadFactory(de);
      try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf)) {
        StringBuffer buffer = new StringBuffer();
        submitAppendTasks(executor, buffer);
        de.drain();
        Thread.sleep(100); // Wait for threads to wake up
        de.drain(); // The slept threads get added back to the queue so we have to drain again
        // which is not great for deterministic simulation because its system time not simulation time
        System.out.println("Result: " + buffer);
      }
    }
  }

  private static final class DeterministicTestExecutor implements Executor {
    private final Random random;
    private final List<Runnable> queue = new ArrayList<>();

    private DeterministicTestExecutor(Random random) {
      this.random = random;
    }

    @Override
    public void execute(Runnable command) {
      HackVirtualThreads.virtualThreadBuilderFor(this::virtualThreadSchedulerCallback)
          .start(command);
    }

    private void virtualThreadSchedulerCallback(Runnable virtualThread) {
      queue.add(virtualThread);
    }

    public void drain() {
      while (!queue.isEmpty()) {
        Collections.shuffle(queue, random);
        Runnable task = queue.removeFirst();
        task.run();
      }
    }
  }

  public static void times(Executor executor, int times) {
    for (int i = 0; i < times; i++) {
      StringBuffer buffer = new StringBuffer();
      submitAppendTasks(executor, buffer);
      try {
        Thread.sleep(100); // Wait for threads to wake up
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      System.out.println("Result: " + buffer);
    }
  }

  public static void submitAppendTasks(Executor executor, StringBuffer buffer) {
    executor.execute(() -> appendToBuffer(buffer, "A"));
    executor.execute(() -> appendToBuffer(buffer, "B"));
    executor.execute(() -> appendToBuffer(buffer, "C"));
    executor.execute(() -> appendToBuffer(buffer, "D"));
    executor.execute(() -> appendToBuffer(buffer, "E"));
    executor.execute(() -> appendToBuffer(buffer, "F"));
    executor.execute(() -> appendToBuffer(buffer, "G"));
    executor.execute(() -> appendToBuffer(buffer, "H"));
  }

  public static synchronized void testSync(StringBuffer buffer) {
    buffer.append(",");
  }

  public static void appendToBuffer(StringBuffer buffer, String str) {
    buffer.append(str);
    testSync(buffer);
    Thread.yield();
    buffer.append(str);
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    buffer.append(str);
  }
}
