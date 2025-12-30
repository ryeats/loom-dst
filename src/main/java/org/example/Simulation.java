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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.example.time.SimulationTime;

public class Simulation {

  private final ScheduledThreadPoolExecutor scheduler;
  private final SchedulableVirtualThreadFactory threadFactory;
  private final RandomGenerator random;
  private final ExecutorService executorService;
  private final Duration duration;
  private final long seed;
  private final String execFingerprint;
  private long endTime;
  private final List<Runnable> workQueue = new ArrayList<>(30);
  private final StringBuffer execStats = new StringBuffer();
  private boolean nonDeterminismDetected;
  private Supplier<Boolean> stateChecker = null;
  private final List<Future<?>> testTasks = new ArrayList<>();

  public Simulation(Duration simulationTimeDuration) {
    this(simulationTimeDuration, null, null);
  }

  public Simulation(Duration simulationTimeDuration, Long seed, String execFingerprint) {
    this.duration = simulationTimeDuration;
    this.seed = seed == null ? new SecureRandom().nextLong() : seed;
    random = new Random(this.seed);
    threadFactory = new SchedulableVirtualThreadFactory(new DeterministicVirtualThreadScheduler());
    executorService = Executors.newThreadPerTaskExecutor(threadFactory);
    scheduler = new ScheduledThreadPoolExecutor(1, threadFactory);
    //    scheduler = new ScheduledThreadPoolExecutor(0, threadFactory);
    executorService.submit(this::tick);
    this.execFingerprint = execFingerprint;
  }

  public void scheduleTestTask(Runnable runnable) {
    testTasks.add(executorService.submit(runnable));
  }

  public void scheduleTestTask(Runnable runnable, Duration delay) {
    testTasks.add(scheduler.schedule(runnable, delay.toMillis(), TimeUnit.MILLISECONDS));
  }

  public void setStateChecker(Supplier<Boolean> stateChecker) {
    this.stateChecker = stateChecker;
  }

  public void addChaos(Runnable toggle, Duration delay, Duration duration) {
    addChaos(toggle, toggle, delay, duration);
  }

  public void addChaos(Runnable start, Runnable stop, Duration delay, Duration duration) {
    scheduler.schedule(start, delay.toMillis(), TimeUnit.MILLISECONDS);
    scheduler.schedule(stop, delay.plus(duration).toMillis(), TimeUnit.MILLISECONDS);
  }

  public void start() throws InterruptedException, ExecutionException {
    SimulationTime.TIME.set(0);
    endTime = SimulationTime.TIME.get() + duration.toMillis();
    loop();
  }

  private void tick() {
    SimulationTime.TIME.addAndGet(1000);
    if (SimulationTime.TIME.get() < endTime) {
      executorService.submit(this::tick);
    }
  }

  private boolean isDone() {
    if (SimulationTime.TIME.get() >= endTime) {
      return true;
    }
    if (stateChecker != null) {
      return stateChecker.get();
    }
    return !testTasks.isEmpty() && testTasks.stream().noneMatch(Predicate.not(Future::isDone));
  }

  private void loop() throws InterruptedException {
    Optional<Runnable> task = getNext();
    while (task.isPresent() && !isDone()) {
      task.get().run();
      // TODO some reason sleeping here helps make this more deterministic???
      Thread.sleep(1);
      task = getNext();
    }
  }

  private Optional<Runnable> getNext() {
    synchronized (workQueue) {
      if (workQueue.isEmpty()) {
        return Optional.empty();
      }

      int size = workQueue.size();
      int pick = random.nextInt(size);
      execStats.append(size);
      if (execFingerprint != null) {
        if (!execFingerprint.startsWith(execStats.toString())) {
          nonDeterminismDetected = true;
        }
      }
      Runnable task = workQueue.remove(pick);
      return Optional.ofNullable(task);
    }
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  public Duration getDuration() {
    return duration;
  }

  public RandomGenerator getRandom() {
    return random;
  }

  public SchedulableVirtualThreadFactory getThreadFactory() {
    return threadFactory;
  }

  public ScheduledThreadPoolExecutor getScheduler() {
    return scheduler;
  }

  public long getSeed() {
    return seed;
  }

  public boolean wasNonDeterminismDetected() {
    return nonDeterminismDetected;
  }

  public String getExecFingerprint() {
    return execStats.toString();
  }

  private class DeterministicVirtualThreadScheduler implements Executor {
    @Override
    public void execute(Runnable runnable) {
      synchronized (workQueue) {
        workQueue.add(runnable);
      }
    }
  }
}
