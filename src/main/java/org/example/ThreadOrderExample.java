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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import org.example.net.EchoServer;

public class ThreadOrderExample {
  static long seed = new SecureRandom().nextLong();

  /*
   Run with java 24 jvm args: --add-opens=java.base/java.lang=ALL-UNNAMED
  */
  public static void main(String[] args) throws InterruptedException, IOException {
    //    System.setProperty("jdk.virtualThreadScheduler.parallelism","1");
    //    System.setProperty("jdk.virtualThreadScheduler.maxPoolSize","1");
    //    System.setProperty("jdk.virtualThreadScheduler.minRunnable","1");
//    ExecutorService serverExec = Executors.newSingleThreadExecutor();
//    EchoServer server = new EchoServer(serverExec, 4242);
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

    System.out.println("Deterministic Virtual Threads Seed: " + seed);
    DeterministicTestExecutor dte = new DeterministicTestExecutor(new Random(seed));
    deterministicTimes(dte::setRandom, dte::drain, dte, 4);

    //    server.close();
    //    serverExec.close();
    System.out.println("Deterministic Virtual ThreadFactory: " + seed);
    for (int i = 0; i < 4; i++) {
    DeterministicExecutor de = new DeterministicExecutor(new Random(seed));
    SchedulableVirtualThreadFactory tf = new SchedulableVirtualThreadFactory(de);
    //      try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf);EchoServer
    // echoServer = new EchoServer(executor,4242)) {
    try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf)) {
        StringBuffer buffer = new StringBuffer();
        submitAppendTasks(executor, buffer);
        de.drain();
        Thread.sleep(100); // Wait for threads to wake up
        de.drain();
        System.out.println("Result: " + buffer);
      //        executor.submit(()->echoServer.close());//The close will block the main thread
      //        de.drain();
      }
    }

    ScheduledExecutorService simStub = Executors.newScheduledThreadPool(1);
    System.out.println("Deterministic Virtual ThreadFactory: " + seed);
    System.out.println("Simulation loop in separate thread.");
    for (int i = 0; i < 4; i++) {
      DeterministicExecutor de = new DeterministicExecutor(new Random(seed));
      ScheduledFuture<?> future =
              simStub.scheduleWithFixedDelay(de::drain, 10, 10, TimeUnit.MILLISECONDS);
      ThreadFactory tf = new SchedulableVirtualThreadFactory(de);
      //      try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf);EchoServer
      // echoServer = new EchoServer(executor,4242)) {
      try (ExecutorService executor = Executors.newThreadPerTaskExecutor(tf)) {
        StringBuffer buffer = new StringBuffer();
        submitAppendTasks(executor, buffer);
        de.drain();
        Thread.sleep(100); // Wait for threads to wake up
        de.drain();
        System.out.println("Result: " + buffer);
      }
      future.cancel(true);
    }
    simStub.shutdownNow();
//    server.close();
//    serverExec.close();
  }

  private static final class DeterministicTestExecutor implements Executor {
    private RandomGenerator random;
    private final List<Runnable> queue = new ArrayList<>();

    private DeterministicTestExecutor(RandomGenerator random) {
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

    public void setRandom(RandomGenerator random) {
      this.random = random;
    }
  }

  public static void times(Executor executor, int times) {
    for (int i = 0; i < times; i++) {
      StringBuffer buffer = new StringBuffer();
      submitAppendTasks(executor, buffer);
      try {
        Thread.sleep(1000); // Wait for threads to wake up
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      System.out.println("Result: " + buffer);
    }
  }

  public static void deterministicTimes(
      Consumer<RandomGenerator> reset, Runnable exec, Executor executor, int times) {
    for (int i = 0; i < times; i++) {
      reset.accept(new Random(seed));
      StringBuffer buffer = new StringBuffer();
      submitAppendTasks(executor, buffer);
      exec.run();
      try {
        Thread.sleep(1000); // Wait for threads to wake up
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      exec.run();

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
    try {
      buffer.append(str);
      writeBufferToFile(buffer, str);
      buffer.append(str);
      testSync(buffer);
      //TODO couldn't get this to work it seems to be blocking for some reason
      //    EchoClient echoClient = new EchoClient("localhost",4242);
      //    buffer.append(echoClient.send(str));
      Thread.yield();
      buffer.append(str);
      Thread.sleep(10);
      buffer.append(str+isHostAlive("www.google.com",80,100));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  // Synchronous file IO captures the virtual thread you can see this because of the
  // A.A or B.B pattern is and no interleaving
  public static void writeBufferToFile(StringBuffer buffer, String str) {
    buffer.append(".");

    try (FileWriter writer = new FileWriter(Path.of("./target/" + str + ".txt").toFile())) {
      writer.write(buffer.toString());
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static String isHostAlive(String host, int port, int timeoutMillis) {
    try (Socket socket = new Socket()) {
      SocketAddress socketAddress = new InetSocketAddress(host, port);
      socket.connect(socketAddress, timeoutMillis);
      return "Y"; // Host is reachable
    } catch (IOException e) {
      return "N"; // Host is not reachable
    }
  }
}
