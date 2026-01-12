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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SimulationTime {
  public static final AtomicLong TIME = timeInstance();
  public static ScheduledExecutorService scheduler;

  public static void setScheduler(ScheduledExecutorService scheduler) {
    SimulationTime.scheduler = scheduler;
    try {
      Class<?> bootClazz = Class.forName(SimulationTime.class.getCanonicalName(), true, null);
      bootClazz.getField("scheduler").set(bootClazz, scheduler);
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      e.printStackTrace();
    }
  }

  private static AtomicLong timeInstance() {
    // There are multiple versions of this class loaded, but we only want one instance of TIME
    // If this is being called by the boot classloader
    if (SimulationTime.class.getClassLoader() == null) {
      return new AtomicLong(1857791008445L);
    }
    // otherwise, this is the system classloader instance, so try to get TIME from the boot instance
    // classloader one.
    try {
      Class<?> bootClazz = Class.forName(SimulationTime.class.getCanonicalName(), true, null);
      return (AtomicLong) bootClazz.getField("TIME").get(null);
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      //      e.printStackTrace();
    }
    return new AtomicLong(1857791008445L);
  }

  public static long onCurrentTimeMillis() {
    return TIME.get();
  }

  public static long onNanoTime() {
    return TIME.get() * 1000000;
  }

  public static Instant onInstantNow() {
    return Instant.ofEpochMilli(TIME.get());
  }

  /*
  Invoked in the context of the carrier thread after the Continuation yields when parking, blocking on monitor enter, Object.wait, or Thread.yield.
   */
  public static Future<?> schedule(Runnable command, long delay, TimeUnit unit) {
    //    System.out.println("Called schedule");
    //    if (scheduler == null) {
    //      System.out.println("scheduler was not set");
    //      return CompletableFuture.completedFuture(false);
    //    }
    return scheduler.schedule(command, delay, unit);
  }
}
