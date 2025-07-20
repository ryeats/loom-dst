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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.example.time.SimulationTime;

public class DeterministicExecutor implements Executor, AutoCloseable {
  private RandomGenerator random;
  private final List<Runnable> workQueue = new ArrayList<>();
  private final ExecutorService singleThread = Executors.newSingleThreadExecutor();
  private int maxExecutions = 20;
  private int timeout = 5;

  public DeterministicExecutor(RandomGenerator random) {
    this.random = random;
  }

  @Override
  public void execute(Runnable runnable) {
    //    System.out.println("Calling execute from "+ Thread.currentThread());
    singleThread.submit(() -> workQueue.add(runnable));
  }

  public void drain() {
    try {
      singleThread.submit(() -> this.internalDrain(true)).get(timeout, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void internalDrain(boolean shuffle) {
    //    System.out.println("Calling drain from "+Thread.currentThread());
    //    System.out.println("Executing "+workQueue.size()+" tasks.");
    SimulationTime.TIME.addAndGet(1);
    for (int count = 0; !workQueue.isEmpty() && count < maxExecutions; count++) {
      removeWorkTask(shuffle).run();
    }
  }

  public void runInCurrentQueueOrder() {
    try {
      singleThread.submit(() -> this.internalDrain(false)).get(timeout, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Runnable removeWorkTask(boolean shuffle) {
    if (shuffle) {
      Collections.shuffle(workQueue, random);
    }
    return workQueue.removeFirst();
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

  public void setMaxExecutions(int maxExecutions) {
    this.maxExecutions = maxExecutions;
  }

  public void setTimeout(int seconds) {
    this.timeout = seconds;
  }
}
