package org.example;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JavaDeterminisimTest {
        static long seed = new SecureRandom().nextLong();
//    static long seed = 6426425772315889486L;

    /*
     Run with java 24 jvm args: --add-opens=java.base/java.lang=ALL-UNNAMED
     Disable GC: -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
     Disable JIT: -Djava.compiler=NONE
    */
    public static void main(String... args) throws Exception {
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1");
        System.setProperty("jdk.virtualThreadScheduler.minRunnable", "1");
        System.out.println("Single Threaded (always the same used as baseline to compare when things are done in order)");
        testDeterminism(Executors::newSingleThreadExecutor, 4);
        System.out.println("8 Threads");
        testDeterminism(() -> Executors.newFixedThreadPool(8), 4);
        System.out.println("Virtual Threads");
        testDeterminism(Executors::newVirtualThreadPerTaskExecutor, 4);
        System.out.println("Deterministic Virtual Threads seed: " + seed);
        testDeterminismWithContext(() -> {
            DeterministicExecutor de = new DeterministicExecutor(new Random(seed));
            SchedulableVirtualThreadFactory tf = new SchedulableVirtualThreadFactory(de);
            return new DeterministicContext(Executors.newThreadPerTaskExecutor(tf), de::drain, de::close);
        }, 20);
    }

    public record DeterministicContext(ExecutorService executorSupplier, Runnable tick, Runnable cleanup) {
    }

    public static void testDeterminism(Supplier<ExecutorService> executorSupplier, int times) throws Exception {
        testDeterminismWithContext(()-> new DeterministicContext(executorSupplier.get(),()->{},()->{}),times);
    }

    public static void testDeterminismWithContext(Supplier<DeterministicContext> contextSupplier, int times) throws Exception {
        String firstResult = null;

        for (int i = 0; i < times; i++) {
            Lock lock = new ReentrantLock();
            StringBuffer log = new StringBuffer();
            DeterministicContext context = contextSupplier.get();
            List<Future<?>> exerciseFutures = new ArrayList<>();
            try (ExecutorService executorService = context.executorSupplier()) {
                exerciseFutures.add(executorService.submit(() -> {
                    exerciseFutures.add(executorService.submit(() -> threadExercise(log, "a", lock, executorService)));
                    exerciseFutures.add(executorService.submit(() -> threadExercise(log, "b", lock, executorService)));
                    exerciseFutures.add(executorService.submit(() -> threadExercise(log, "c", lock, executorService)));
                    exerciseFutures.add(executorService.submit(() -> threadExercise(log, "d", lock, executorService)));
                    exerciseFutures.add(executorService.submit(() -> threadExercise(log, "e", lock, executorService)));
//                    exerciseFutures.add(executorService.submit(() -> threadExercise(log, "f", lock,executorService)));
                }));
                while (exerciseFutures.stream().anyMatch(Predicate.not(Future::isDone))) {
                    context.tick().run();
                    //TODO smaller loop times introduce indeterminism since threads that end up resuming beyond this timeout due to being slept, connecting or do synchronous file IO get shuffled into the work queue at differing times
                    Thread.sleep(15);//has to be bigger than the largest sleep by some margin to keep things somewhat deterministic
                }
            } finally {
                context.cleanup().run();
            }
            String result = log.toString();
            if (firstResult != null) {
                log.append(" ").append(firstResult.equals(result));
            } else {
                firstResult = result;
            }
            System.out.println(log);
        }
    }

    public static void threadExercise(StringBuffer log, String id, Lock lock, ExecutorService es) {
        try {
            AtomicInteger i = new AtomicInteger();
            log.append(id);
            synchronizedYield(log, i.incrementAndGet());
            log.append(id);
            sleepThread(log, i.incrementAndGet());//this introduces indeterminism if the sleep is longer than the drain loop time due to variability in  when the thread gets started by the system
            log.append(id);
            synchronizedMethod(log, i.incrementAndGet());// I didn't think this would interleave, but it does seem to since we don't always see b3b d3d
            log.append(id);
            synchronousFileIO(log, i.incrementAndGet());// Not useful because virtual threads are captured by synchronous IO so we cannot simulate interleaving, also occasionally causes indeterminism I don't know why though
            log.append(id);
            lock(log, i.incrementAndGet(), lock);
            log.append(id);
            synchronousNetworkIO(log, i.incrementAndGet());//this introduces indeterminism I assume because the time it takes to connect is variable so this won't be a problem for local connections?
            log.append(id);
//            wait(log, id,lock);
//            log.append(id);

            //InputStream.read():
            //OutputStream.write():
            //BlockingQueue.take(): Waits for an element to become available in the queue.
            //BlockingQueue.put(): Waits for space to become available in the queue.
            //CountDownLatch.await(): Waits until the counter reaches zero.
            //CyclicBarrier.await(): Waits for all parties to arrive at the barrier.
            //Future.get()
            //Thread.join()
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void sleepThread(StringBuffer log, int id) throws InterruptedException {
        Thread.sleep(10);
        log.append(id);
    }

    public static synchronized void synchronizedYield(StringBuffer log, int id) {
        Thread.yield();
        log.append(id);
//        System.out.print(id);
    }

    public static void wait(StringBuffer log, int id, Lock lock) throws InterruptedException {
        lock.notifyAll();
        lock.wait(10);
//        new Object().wait(1);
//        id.wait(1);
        log.append(id);
    }

    public static synchronized void synchronizedMethod(StringBuffer log, int id) throws InterruptedException {
        log.append(id);
    }

    public static void lock(StringBuffer log, int id, Lock lock) throws InterruptedException {
        lock.lock();
        Thread.yield();
        log.append(id);
        lock.unlock();
    }


    public static void synchronousFileIO(StringBuffer log, int id) {
        try (FileWriter writer = new FileWriter(Path.of("./target/" + id + ".txt").toFile())) {
            writer.write(log.toString());
            writer.flush();
            log.append(id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void synchronousNetworkIO(StringBuffer log, int id) {
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

}
