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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// TODO is this needed anymore??
public class SimulationScheduledExecutor extends AbstractExecutorService
    implements ScheduledExecutorService {
  private static final long MAX_NANOS = (Long.MAX_VALUE >>> 1) - 1;
  private final Clock clock;

  private final PriorityQueue<SimulatorScheduledFutureTask<?>> delayedQueue = new PriorityQueue<>();
  private final ExecutorService executorService;

  public SimulationScheduledExecutor(Clock clock, ExecutorService executorService) {
    this.clock = clock;
    this.executorService = executorService;
  }

  public void tick() {
    delayedQueue.removeIf(
        scheduledFuture -> {
          boolean remove = Instant.now(clock).isAfter(scheduledFuture.getTriggerTime());
          if (remove) {
            executorService.submit(scheduledFuture);
          }
          return remove;
        });
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    SimulatorScheduledFutureTask<Void> task =
        new SimulatorScheduledFutureTask<>(command, null, triggerTime(delay, unit));
    delayedExecute(task);
    return task;
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    SimulatorScheduledFutureTask<V> task =
        new SimulatorScheduledFutureTask<>(callable, triggerTime(delay, unit));
    delayedExecute(task);
    return task;
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    SimulatorScheduledFutureTask<Void> task =
        new SimulatorScheduledFutureTask<>(
            command,
            null,
            triggerTime(initialDelay, unit),
            Duration.of(period, toTemporalUnit(unit)));
    delayedExecute(task);
    return task;
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    // TODO this is different than fixed rate in that the delay is between execution end and the
    // next one beginning need to make sure that what scheduledFutureTask actually is doing
    SimulatorScheduledFutureTask<Void> task =
        new SimulatorScheduledFutureTask<>(
            command,
            null,
            triggerTime(initialDelay, unit),
            Duration.of(delay, toTemporalUnit(unit)).negated());
    delayedExecute(task);
    return task;
  }

  @Override
  public void shutdown() {}

  @Override
  public List<Runnable> shutdownNow() {
    return List.of();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return false;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    //    return schedule(task, 0, NANOSECONDS);
    return executorService.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    //    return schedule(Executors.callable(task, result), 0, NANOSECONDS);
    return executorService.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    //    return schedule(task, 0, NANOSECONDS);
    return executorService.submit(task);
  }

  @Override
  public void close() {}

  @Override
  public void execute(Runnable command) {
    executorService.execute(command);
  }

  public long getDelayedQueueSize() {
    return delayedQueue.size();
  }

  public Queue<SimulatorScheduledFutureTask<?>> getQueue() {
    return delayedQueue;
  }

  protected void remove(RunnableScheduledFuture<?> future) {
    throw new UnsupportedOperationException("remove not supported yet");
  }

  private Instant triggerTime(long delay, TimeUnit unit) {
    return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
  }

  private Instant triggerTime(long delay) {
    return Instant.now(clock).plus(Math.min(delay, MAX_NANOS), ChronoUnit.NANOS);
  }

  private static TemporalUnit toTemporalUnit(TimeUnit timeUnit) {
    return switch (timeUnit) {
      case NANOSECONDS -> ChronoUnit.NANOS;
      case MICROSECONDS -> ChronoUnit.MICROS;
      case MILLISECONDS -> ChronoUnit.MILLIS;
      case SECONDS -> ChronoUnit.SECONDS;
      case MINUTES -> ChronoUnit.MINUTES;
      case HOURS -> ChronoUnit.HOURS;
      case DAYS -> ChronoUnit.DAYS;
    };
  }

  private void delayedExecute(SimulatorScheduledFutureTask<?> task) {
    // TODO make this configurable what gets added directly to the work queue
    if (task.getDelay(NANOSECONDS) == 0) {
      executorService.execute(task);
    } else {
      delayedQueue.add(task);
    }
  }

  protected void reExecutePeriodic(SimulatorScheduledFutureTask<?> future) {
    delayedQueue.add(future);
  }

  private class SimulatorScheduledFutureTask<V> extends FutureTask<V>
      implements RunnableScheduledFuture<V> {

    public Instant getTriggerTime() {
      return time;
    }

    /** time when the task is enabled to execute. */
    Instant time;

    /**
     * Period for repeating tasks, in nanoseconds. A positive value indicates fixed-rate execution.
     * A negative value indicates fixed-delay execution. A value of 0 indicates a non-repeating
     * (one-shot) task.
     */
    private final Duration period;

    /** The actual task to be re-enqueued by reExecutePeriodic */
    SimulatorScheduledFutureTask<V> outerTask = this;

    /** Index into delay queue, to support faster cancellation. */
    int heapIndex;

    private boolean removeOnCancel;

    /** Creates a one-shot action with given nanoTime-based trigger time. */
    SimulatorScheduledFutureTask(Runnable r, V result, Instant triggerTime) {
      super(r, result);
      this.time = triggerTime;
      this.period = Duration.ZERO;
    }

    /** Creates a periodic action with given nanoTime-based initial trigger time and period. */
    SimulatorScheduledFutureTask(Runnable r, V result, Instant triggerTime, Duration period) {
      super(r, result);
      this.time = triggerTime;
      this.period = period;
    }

    /** Creates a one-shot action with given nanoTime-based trigger time. */
    SimulatorScheduledFutureTask(Callable<V> callable, Instant triggerTime) {
      super(callable);
      this.time = triggerTime;
      this.period = Duration.ZERO;
    }

    protected static TemporalUnit toTemporalUnit(TimeUnit timeUnit) {
      return switch (timeUnit) {
        case NANOSECONDS -> ChronoUnit.NANOS;
        case MICROSECONDS -> ChronoUnit.MICROS;
        case MILLISECONDS -> ChronoUnit.MILLIS;
        case SECONDS -> ChronoUnit.SECONDS;
        case MINUTES -> ChronoUnit.MINUTES;
        case HOURS -> ChronoUnit.HOURS;
        case DAYS -> ChronoUnit.DAYS;
      };
    }

    public long getDelay(TimeUnit unit) {
      return unit.convert(clock.instant().until(time));
    }

    // TODO this currently returns zero if they have the same delay we may need to add a counter to
    // order them in those cases?
    public int compareTo(Delayed other) {
      if (other == this) // compare zero if same object
      {
        return 0;
      }
      if (other instanceof SimulatorScheduledFutureTask<?> x) {
        return time.compareTo(x.time);
      }
      long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
      return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
    }

    /**
     * Returns {@code true} if this is a periodic (not a one-shot) action.
     *
     * @return {@code true} if periodic
     */
    public boolean isPeriodic() {
      return !period.isZero();
    }

    /** Sets the next time to run for a periodic task. */
    private void setNextRunTime() {
      time.plus(period);
    }

    public void setRemoveOnCancelPolicy(boolean value) {
      removeOnCancel = value;
    }

    public boolean getRemoveOnCancelPolicy() {
      return removeOnCancel;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
      // The racy read of heapIndex below is benign:
      // if heapIndex < 0, then OOTA guarantees that we have surely
      // been removed; else we recheck under lock in remove()
      boolean cancelled = super.cancel(mayInterruptIfRunning);
      if (cancelled && removeOnCancel && heapIndex >= 0) remove(this);
      return cancelled;
    }

    /** Overrides FutureTask version so as to reset/requeue if periodic. */
    public void run() {
      if (!isPeriodic()) {
        super.run();
      } else if (super.runAndReset()) {
        setNextRunTime();
        reExecutePeriodic(outerTask);
      }
    }
  }
}
