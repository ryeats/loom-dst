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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.example.net.EchoClient;
import org.example.net.NettyEchoClient;

/*

run with the following JVM args
 --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -javaagent:./target/loom-dst-1.0-SNAPSHOT.jar
 */
public class SimulationTest {

  private static StringBuffer LOG = new StringBuffer();
  static FileSystem FS;
  private static Lock LOCK = new ReentrantLock();

  public static void main(String... args) throws Exception {
    FS = FileSystems.getDefault();
    // Was hoping an in memory file system would make things more deterministic, but it actually
    // made things less deterministic!
    //        FS = Jimfs.newFileSystem(Configuration.unix());
    Path filePath = FS.getPath("./target/test.txt");
    Files.createDirectories(filePath.getParent());
    Files.write(
        filePath, "This is a test".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);

    //    System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
    //    System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1");
    //    System.setProperty("jdk.virtualThreadScheduler.minRunnable", "1");
    long seed = new SecureRandom().nextLong();
    System.out.println("Seed: " + seed + "L");
    String execFingerPrint = null;
    for (int i = 0; i < 100; i++) {
      Simulation simulation = new Simulation(Duration.ofSeconds(30), seed, execFingerPrint);
      LOCK = new ReentrantLock();
      simulation.getExecutorService().submit(() -> contentiousTestMethod(simulation, "a"));
      simulation.getExecutorService().submit(() -> contentiousTestMethod(simulation, "b"));
      simulation.getExecutorService().submit(() -> contentiousTestMethod(simulation, "c"));
      simulation.getExecutorService().submit(() -> contentiousTestMethod(simulation, "d"));
      simulation.getExecutorService().submit(() -> contentiousTestMethod(simulation, "e"));

      simulation.start();
      if (simulation.wasNonDeterminismDetected()) {
        // Non-deterministic
        System.out.print("ND ");
      } else {
        execFingerPrint = simulation.getExecFingerprint();
      }
      System.out.print(LOG);
      System.out.println(" "+simulation.getExecFingerprint());
      LOG = new StringBuffer();
      LOCK = new ReentrantLock();
      Thread.sleep(1000);
    }
  }

  public static void contentiousTestMethod(Simulation simulation, String id) {
    try {
      int i = 0;
      LOG.append(id);

      synchronizedYield(i++);
      LOG.append(id);

      // this introduces indeterminism if the sleep is longer than the
      // drain loop time due to variability in when the thread gets
      // started by the system
//      sleepThread(i++);
//      LOG.append(id);

      // I didn't think this would interleave, but it does seem to
      // since we don't always see b3b d3d
      synchronizedMethod(i++);
      LOG.append(id);

      // Not useful because virtual threads are captured by synchronous
      // IO so we cannot simulate interleaving, also occasionally
      // causes indeterminism I don't know why though
      //      synchronousFileIO(i++);
      //      LOG.append(id);

      //      asyncFileRead(i++);
      //      LOG.append(id);
      //
      //      asyncFileWrite(i++);
      //      LOG.append(id);

      lock(i++);
      LOG.append(id);

      //      wait(i++, simulation.getExecutorService());
      //      LOG.append(id);

      // this introduces indeterminism I assume because the time it
      // takes to connect is variable
      //      synchronousNetworkIO(i++);
      //      LOG.append(id);

      //      nettyAsyncLocalNetworkIO(i++, simulation.getExecutorService());
      //      LOG.append(id);

      // TODO Not exactly sure how this introduces indeterminism yet but it does
      //      syncLocalNetworkIO(i++);
      //      LOG.append(id);

      // TODO
      // InputStream.read():
      // OutputStream.write():
      // BlockingQueue.take(): Waits for an element to become available in the queue.
      // BlockingQueue.put(): Waits for space to become available in the queue.
      // CountDownLatch.await(): Waits until the counter reaches zero.
      // CyclicBarrier.await(): Waits for all parties to arrive at the barrier.
      // Future.get()
      // Thread.join()
      // java file and network nio
    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  public static void sleepThread(int id) throws InterruptedException {
    Thread.sleep(3);
    LOG.append(id);
  }

  public static synchronized void synchronizedYield(int id) {
    Thread.yield();
    LOG.append(id);
    //        System.out.print(id);
  }

  public static void wait(int id, ExecutorService es) throws InterruptedException {
    es.submit(
        () -> {
          synchronized (LOG) {
            LOG.notify(); // Wakes up one waiting thread
          }
        });
    synchronized (LOG) {
      LOG.wait(5);
      LOG.append(id);
    }
  }

  public static synchronized void synchronizedMethod(int id) {
    LOG.append(id);
  }

  public static void lock(int id) throws InterruptedException {
    LOCK.lock();
    Thread.yield();
    LOG.append(id);
    LOCK.unlock();
  }

  public static void synchronousFileIO(int id) {
    try (FileWriter writer = new FileWriter(Path.of("./target/" + id + ".txt").toFile())) {
      writer.write(LOG.toString());
      writer.flush();
      LOG.append(id);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void synchronousNetworkIO(int id) {
    String host = "www.google.com";
    int port = 80;

    try (Socket socket = new Socket(host, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

      out.println("GET / HTTP/1.0");
      out.println("Host: " + host);
      out.println();

      String line;
      while ((line = in.readLine()) != null) {
        //                System.out.println(line.isEmpty());
        //                System.out.println(line);
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void syncLocalNetworkIO(int id) {
    EchoClient echoClient = new EchoClient("localhost", 4242);
    try {
      String resp = echoClient.send(LOG.toString());
      LOG.append(id);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void nettyAsyncLocalNetworkIO(int id, ExecutorService es) {
    NettyEchoClient nettyEchoClient =
        new NettyEchoClient(
            es,
            "localhost",
            4242,
            (nec, s) -> {
              LOG.append(id);

              nec.close();
            });
    try {
      nettyEchoClient.start();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    nettyEchoClient.sendMessage(LOG.toString());
  }

  public static void asyncFileRead(int id) {
    try {
      Path path = FS.getPath("./target/test.txt");
      AsynchronousFileChannel fileChannel =
          AsynchronousFileChannel.open(path, StandardOpenOption.READ);

      ByteBuffer buffer = ByteBuffer.allocate(1024);

      fileChannel.read(
          buffer,
          0,
          buffer,
          new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer result, ByteBuffer attachment) {
              LOG.append(id);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
              LOG.append(id);
              LOG.append("!");
            }
          });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void asyncFileWrite(int id) {
    try {
      Path path = FS.getPath("./target/  " + id + ".txt");
      AsynchronousFileChannel fileChannel =
          AsynchronousFileChannel.open(path, WRITE, CREATE, DELETE_ON_CLOSE);

      ByteBuffer buffer = ByteBuffer.allocate(2046);
      buffer.put(LOG.toString().getBytes());
      buffer.flip();

      fileChannel.write(
          buffer,
          0,
          buffer,
          new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer result, ByteBuffer attachment) {
              LOG.append(id);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
              LOG.append(id);
              LOG.append("!");
            }
          });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
