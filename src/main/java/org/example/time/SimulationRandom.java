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

import java.security.SecureRandom;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class SimulationRandom extends SecureRandom {
  public static final Random RANDOM = randomInstance();
  public static Long SEED;

  public SimulationRandom(long seed) {
    super(longToByteArray(seed));
  }

  public SimulationRandom() {
    super();
  }

  public SimulationRandom(byte[] seed) {
    super(seed);
  }

  private static Random randomInstance() {
    // There are multiple versions of this class loaded, but we only want one instance of TIME
    // If this is being called by the boot classloader
    try {
      String seedStr = System.getProperty("seed");
      if (seedStr != null) {
        SEED = Long.parseLong(seedStr);
      } else {
        SEED = new SecureRandom().nextLong();
      }
    } catch (NumberFormatException e) {
      SEED = new SecureRandom().nextLong();
    }
    if (SimulationRandom.class.getClassLoader() == null) {
      return new Random(SEED);
    }
    // otherwise, this is the system classloader instance, so try to get TIME from the boot instance
    // classloader one.
    try {
      Class<?> bootClazz = Class.forName(SimulationRandom.class.getCanonicalName(), true, null);
      return (Random) bootClazz.getField("RANDOM").get(null);
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
      //      e.printStackTrace();
    }
    return new Random(SEED);
  }

  // TODO save off the JVM state periodically then we can replay JVM from previous states to do a
  // binary search of when
  // failure became inevitable
  public void nextSeed() {
    // TODO SAVE JVM state
    SEED = new SecureRandom().nextLong();
    RANDOM.setSeed(SEED);
  }

  public long getSimulationSeed() {
    return SEED;
  }

  public void resetSeed() {
    RANDOM.setSeed(SEED);
  }

  public static Random getRandom() {
    return RANDOM;
  }

  public static SecureRandom getSecureRandom() {
    return new SimulationRandom();
  }

  @Override
  public void setSeed(long seed) {
    //    random.setSeed(seed);
  }

  @Override
  public void setSeed(byte[] seed) {
    //    // Random doesn't take byte[], so we can convert or ignore/delegate
    //    if (seed != null && seed.length >= 8) {
    //      long l = 0;
    //      for (int i = 0; i < 8; i++) {
    //        l |= ((long) (seed[i] & 0xff) << (56 - (i * 8)));
    //      }
    //      random.setSeed(l);
    //    }
  }

  @Override
  public void nextBytes(byte[] bytes) {
    RANDOM.nextBytes(bytes);
  }

  @Override
  public int nextInt() {
    return RANDOM.nextInt();
  }

  @Override
  public int nextInt(int bound) {
    return RANDOM.nextInt(bound);
  }

  @Override
  public long nextLong() {
    return RANDOM.nextLong();
  }

  @Override
  public boolean nextBoolean() {
    return RANDOM.nextBoolean();
  }

  @Override
  public float nextFloat() {
    return RANDOM.nextFloat();
  }

  @Override
  public double nextDouble() {
    return RANDOM.nextDouble();
  }

  @Override
  public double nextGaussian() {
    return RANDOM.nextGaussian();
  }

  @Override
  public IntStream ints() {
    return RANDOM.ints();
  }

  @Override
  public IntStream ints(long streamSize) {
    return RANDOM.ints(streamSize);
  }

  @Override
  public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
    return RANDOM.ints(streamSize, randomNumberOrigin, randomNumberBound);
  }

  @Override
  public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
    return RANDOM.ints(randomNumberOrigin, randomNumberBound);
  }

  @Override
  public LongStream longs() {
    return RANDOM.longs();
  }

  @Override
  public LongStream longs(long streamSize) {
    return RANDOM.longs(streamSize);
  }

  @Override
  public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
    return RANDOM.longs(streamSize, randomNumberOrigin, randomNumberBound);
  }

  @Override
  public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
    return RANDOM.longs(randomNumberOrigin, randomNumberBound);
  }

  @Override
  public DoubleStream doubles() {
    return RANDOM.doubles();
  }

  @Override
  public DoubleStream doubles(long streamSize) {
    return RANDOM.doubles(streamSize);
  }

  @Override
  public DoubleStream doubles(
      long streamSize, double randomNumberOrigin, double randomNumberBound) {
    return RANDOM.doubles(streamSize, randomNumberOrigin, randomNumberBound);
  }

  @Override
  public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
    return RANDOM.doubles(randomNumberOrigin, randomNumberBound);
  }

  private static byte[] longToByteArray(long l) {
    byte[] retVal = new byte[8];

    for (int i = 0; i < 8; i++) {
      retVal[i] = (byte) l;
      l >>= 8;
    }

    return retVal;
  }
}
