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
import java.util.concurrent.atomic.AtomicLong;

public class SimulationTime {
  public static final AtomicLong TIME = timeInstance();

  private static AtomicLong timeInstance() {
    if (SimulationTime.class.getClassLoader() == null) {
      return new AtomicLong();
    }
    try {
      Class<?> bootClazz = Class.forName(SimulationTime.class.getCanonicalName(), true, null);
      return (AtomicLong) bootClazz.getField("TIME").get(null);
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      e.printStackTrace();
    }
    return new AtomicLong();
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
}
