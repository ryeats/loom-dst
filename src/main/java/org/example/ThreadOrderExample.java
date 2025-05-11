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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ThreadOrderExample {
  /*
   Run with java 24 jvm args: --add-opens=java.base/java.lang=ALL-UNNAMED
  */
  public static void main(String[] args) throws InterruptedException {
    //    System.setProperty("jdk.virtualThreadScheduler.parallelism","1");
    //    System.setProperty("jdk.virtualThreadScheduler.maxPoolSize","1");
    //    System.setProperty("jdk.virtualThreadScheduler.minRunnable","1");
    System.out.println("8 Threads");
    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      times(executor, 4);
    }
    // Single threaded is deterministic, but you don't get thread interleaving
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
      DeterministicTestExecutor dte = new DeterministicTestExecutor(new Random(seed));
      StringBuffer buffer = new StringBuffer();
      submitAppendTasks(dte, buffer);
      dte.drain();
      Thread.sleep(100); // Wait for threads to wake up
      dte.drain(); // The slept threads get added back to the queue so we have to drain again
      // which is not great for deterministic simulation because its system time not simulation time
      System.out.println("Result: " + buffer);
    }

    System.out.println("Deterministic Virtual ThreadFactory: " + seed);
    for (int i = 0; i < 4; i++) {
      DeterministicExecutor de = new DeterministicExecutor(new Random(seed));
      SchedulableVirtualThreadFactory tf = new SchedulableVirtualThreadFactory(de);
      try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf)) {
        StringBuffer buffer = new StringBuffer();
        submitAppendTasks(executor, buffer);
        de.drain();
        Thread.sleep(100); // Wait for threads to wake up
        de.drain(); // The slept threads get added back to the queue so we have to drain again
        // which is not great for deterministic simulation because its system time not simulation
        // time
        System.out.println("Result: " + buffer);
      }
    }

    ScheduledExecutorService simStub = Executors.newScheduledThreadPool(1);
    System.out.println("Deterministic Virtual ThreadFactory: " + seed);
    System.out.println("Simulation loop in separate thread.");
    for (int i = 0; i < 4; i++) {
      DeterministicExecutor de = new DeterministicExecutor(new Random(seed));
      ScheduledFuture<?> future =
          simStub.scheduleAtFixedRate(de::drain, 10, 10, TimeUnit.MILLISECONDS);
      SchedulableVirtualThreadFactory tf = new SchedulableVirtualThreadFactory(de);
      try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf)) {
        StringBuffer buffer = new StringBuffer();
        submitAppendTasks(executor, buffer);
        Thread.sleep(
            100); // TODO have to wait until the sim loop finishes... not sure its practical to run
        // simulation this way since its hard to know when its done.
        System.out.println("Result: " + buffer);
      }
      future.cancel(true);
    }
    simStub.shutdownNow();
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
    writeBufferToFile(buffer, str);
    buffer.append(str);
    testSync(buffer);
    Thread.yield();
    buffer.append(str);
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  // TODO This doesn't seem to yield for the DeterministicExecutor? you can see this because of the
  // A.A or B.B pattern
  // When setting parallelism to 1 it doesn't look like the normal virtual thread scheduler is
  // actually yielding either???
  public static void writeBufferToFile(StringBuffer buffer, String str) {
    buffer.append(".");

    try (FileWriter writer =
        new FileWriter(Path.of("./target/"+ str + ".txt").toFile())) {
      writer.write(buffer.toString());
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
