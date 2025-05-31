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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class SchedulableVirtualThreadFactory implements ThreadFactory {

  private static final Class<?> VIRTUAL_THREAD_CLASS;
  private static final Constructor<?> VIRTUAL_THREAD_CONSTRUCTOR;
  private static final Method VIRTUAL_THREAD_UEH_SETTER;
  private static final Thread.UncaughtExceptionHandler DEFAULT_UNCAUGHT_EXCEPTION_HANDLER =
      new DefaultUncaughtExceptionHandler();

  static {
    try {
      VIRTUAL_THREAD_CLASS = Class.forName("java.lang.VirtualThread");

      Constructor<?> constructor =
          VIRTUAL_THREAD_CLASS.getDeclaredConstructor(
              Executor.class, String.class, int.class, Runnable.class);
      constructor.setAccessible(true);
      VIRTUAL_THREAD_CONSTRUCTOR = constructor;

      Method uehSetter =
          Thread.class.getDeclaredMethod(
              "uncaughtExceptionHandler", Thread.UncaughtExceptionHandler.class);
      uehSetter.setAccessible(true);
      VIRTUAL_THREAD_UEH_SETTER = uehSetter;

    } catch (Exception e) {
      throw new InternalError(e);
    }
  }

  private final Executor scheduler;
  private final Thread.UncaughtExceptionHandler ueh;
  private final String threadName;
  private final int characteristics;

  public SchedulableVirtualThreadFactory(Executor scheduler) {
    this(scheduler, "", 0);
  }

  public SchedulableVirtualThreadFactory(Executor scheduler, String threadName) {
    this(scheduler, threadName, 0);
  }

  public SchedulableVirtualThreadFactory(
      Executor scheduler, String threadName, int characteristics) {
    this(scheduler, threadName, characteristics, DEFAULT_UNCAUGHT_EXCEPTION_HANDLER);
  }

  public SchedulableVirtualThreadFactory(
      Executor scheduler,
      String threadName,
      int characteristics,
      Thread.UncaughtExceptionHandler ueh) {
    this.scheduler = scheduler;
    this.ueh = ueh;
    this.threadName = threadName;
    this.characteristics = characteristics;
  }

  public SchedulableVirtualThreadFactory(Executor scheduler, Thread.UncaughtExceptionHandler ueh) {
    this(scheduler, "", 0, ueh);
  }

  @Override
  public Thread newThread(Runnable r) {
    try {
      Thread t =
          (Thread)
              VIRTUAL_THREAD_CONSTRUCTOR.newInstance(scheduler, threadName, characteristics, r);
      if (ueh != null) {
        setUncaughtExceptionHandler(t, ueh);
      }
      return t;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  protected void setUncaughtExceptionHandler(Thread t, Thread.UncaughtExceptionHandler ueh) {
    try {
      VIRTUAL_THREAD_UEH_SETTER.invoke(t, ueh);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      System.out.println(t);
      e.printStackTrace();
    }
  }
}
