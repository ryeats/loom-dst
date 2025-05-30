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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.random.RandomGenerator;


public class DeterministicExecutor implements Executor, AutoCloseable {
    private RandomGenerator random;
    private final List<Runnable> workQueue = new CopyOnWriteArrayList<>();
    ExecutorService singleThread = Executors.newSingleThreadExecutor();

    public DeterministicExecutor(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public void execute(Runnable runnable) {
//    System.out.println("Calling execute from "+ Thread.currentThread());
        singleThread.submit(() -> workQueue.add(runnable));
    }

    public Future<?> drain() {
        return singleThread.submit(() -> this.internalDrain(true));
    }


    private void internalDrain(boolean shuffle) {
//    System.out.println("Calling drain from "+Thread.currentThread());
        //        System.out.println("Executing "+workQueue.size()+" tasks.");
        while (!workQueue.isEmpty()) {
            if (shuffle) {
                Collections.shuffle(workQueue, random);
            }
            Runnable task = workQueue.removeFirst();
//            Runnable task = workQueue.remove(random.nextInt(1,workQueue.size()) - 1);
            task.run();
        }
    }

    public Future<?> runInCurrentQueueOrder() {
        return singleThread.submit(() -> this.internalDrain(false));
    }

    public int queueSize() {
        return workQueue.size();
    }

    public void setRandom(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public void close() {
        singleThread.close();
    }
}
