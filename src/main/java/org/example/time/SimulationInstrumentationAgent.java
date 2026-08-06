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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class SimulationInstrumentationAgent implements ClassFileTransformer {
  public static void premain(String agentArgs, Instrumentation inst) {
    try {

      // Create a temporary JAR file
      File tempJarFile = File.createTempFile("bootstrapClass", ".jar");
      tempJarFile.deleteOnExit(); // Ensure the temporary file is deleted on exit

      try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJarFile))) {
        // Add the class file to the JAR
        String entryName =
            SimulationTime.class.getCanonicalName().replace(".", "/") + ".class"; // Full class path
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        try (InputStream in =
            SimulationTime.class.getResourceAsStream(
                "/" + SimulationTime.class.getCanonicalName().replace(".", "/") + ".class")) {
          Objects.requireNonNull(in).transferTo(jos);
        }
        jos.closeEntry();
      }

      // Add the temporary JAR to the bootstrap classloader's search path
      inst.appendToBootstrapClassLoaderSearch(new JarFile(tempJarFile));
      System.out.println("BootstrapClass added to bootstrap classloader search path.");

    } catch (Exception e) {
      e.printStackTrace();
    }

    inst.addTransformer(new SimulationInstrumentationAgent(), true);
    for (Class<?> clazz : inst.getAllLoadedClasses()) {
      if (inst.isModifiableClass(clazz)) {
        try {
          inst.retransformClasses(clazz);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void agentmain(String agentArgs, Instrumentation inst) {
    premain(agentArgs, inst);
  }

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {

    ClassReader cr = new ClassReader(classfileBuffer);
    ClassWriter cw = new CachingComputeClassWriter(ClassWriter.COMPUTE_FRAMES);
    ClassVisitor cv = cw;

    // For debugging purposes
    //    StringWriter sw = new StringWriter();
    //    if ("org/example/Simulation".equals(className)) {
    //      System.err.println("Adding TraceClassVisitor");
    //
    //      PrintWriter pw = new PrintWriter(System.err);
    //      cv = new TraceClassVisitor(cw, new Textifier(), pw);
    //    }
    cv = new TimeInstrumenatorVisitor(cv);
    cr.accept(cv, ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  public static class TimeInstrumenatorVisitor extends ClassVisitor {

    public static final Type TIME_CLASS =
        Type.getObjectType(SimulationTime.class.getName().replace('.', '/'));
    public static final Type THREAD_CLASS =
        Type.getObjectType(Thread.class.getName().replace('.', '/'));
    public static final Type OF_VIRTUAL_CLASS =
        Type.getObjectType(Thread.Builder.OfVirtual.class.getName().replace('.', '/'));
    private final Method ofVirtual;
    private final Method factory;
    private final Method onCurrentMillis;
    private final Method onInstantNow;
    private final Method onNanoTime;
    private final Method schedule;
    private final Method afterYieldHook;
    private final Method getRandom;
    private final Method threadFactory;
    private final Method executor;
    private final Method executorService;
    private String className = "";

    public TimeInstrumenatorVisitor(ClassVisitor cv) {
      super(Opcodes.ASM9, cv);
      try {

        onCurrentMillis =
            Method.getMethod(SimulationTime.class.getDeclaredMethod("onCurrentTimeMillis", null));
        onNanoTime = Method.getMethod(SimulationTime.class.getDeclaredMethod("onNanoTime", null));
        ofVirtual = Method.getMethod(Thread.class.getDeclaredMethod("ofVirtual"));
        factory = Method.getMethod(Thread.Builder.class.getDeclaredMethod("factory"));
        onInstantNow =
            Method.getMethod(SimulationTime.class.getDeclaredMethod("onInstantNow", null));
        getRandom = Method.getMethod(SimulationRandom.class.getDeclaredMethod("getRandom", null));
        threadFactory =
            Method.getMethod(SimulationTime.class.getDeclaredMethod("threadFactory", null));
        executor = Method.getMethod(SimulationTime.class.getDeclaredMethod("executor", null));
        executorService =
            Method.getMethod(SimulationTime.class.getDeclaredMethod("executorService", null));
        schedule =
            Method.getMethod(
                SimulationTime.class.getDeclaredMethod(
                    "schedule", Runnable.class, long.class, TimeUnit.class));
        afterYieldHook =
            Method.getMethod(
                SimulationTime.class.getDeclaredMethod("afterYieldHook", Object.class));
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.className = name;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      if (className.equals("org/example/time/SimulationRandom")) {
        return mv;
      }
      // Prevent VirtualThread-unblocker thread from being started
      if ("java/lang/VirtualThread".equals(className) && "<clinit>".equals(name)) {
        // Return a visitor specifically for to modify the static block of the VirtualThread class
        return new MethodVisitor(Opcodes.ASM9, mv) {

          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Intercept any call to Thread.start()
            if (opcode == Opcodes.INVOKEVIRTUAL
                && "java/lang/Thread".equals(owner)
                && "start".equals(name)
                && "()V".equals(descriptor)) {

              super.visitInsn(Opcodes.POP);
              return; // Skip visiting the actual instruction
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }
        };
      }
      if ("java/lang/ThreadBuilders$VirtualThreadBuilder".equals(className)
          && "<init>".equals(name)
          && "()V".equals(descriptor)) {
        return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
          @Override
          //          protected void onMethodExit(int opcode) {
          protected void onMethodEnter() {
            // Ensure the constructor executes successfully before injecting field injection
            //            if (opcode != ATHROW) {
            loadThis();
            invokeStatic(TIME_CLASS, executor);

            mv.visitFieldInsn(
                Opcodes.PUTFIELD,
                "java/lang/ThreadBuilders$VirtualThreadBuilder",
                "scheduler",
                "Ljava/util/concurrent/Executor;");
            //            }
          }
        };
      }
      return new GeneratorAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
        private boolean replacingRandom;

        // Remove new Random()
        @Override
        public void visitTypeInsn(int opcode, String type) {
          if (opcode == Opcodes.NEW && type.equals("java/util/Random")) {
            replacingRandom = true;
            return;
          }

          super.visitTypeInsn(opcode, type);
        }

        // Remove new Random()
        @Override
        public void visitInsn(int opcode) {
          if (replacingRandom && opcode == Opcodes.DUP) {
            return;
          }
          super.visitInsn(opcode);
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String name, String descriptor, boolean isInterface) {
          if ("java/lang/System".equals(owner) && "nanoTime".equals(name)) {
            invokeStatic(TIME_CLASS, onNanoTime);
          } else if ("java/lang/System".equals(owner) && "currentTimeMillis".equals(name)) {
            invokeStatic(TIME_CLASS, onCurrentMillis);
          } else if ("java/time/Instant".equals(owner) && "now".equals(name)) {
            invokeStatic(TIME_CLASS, onInstantNow);
          } else if ("java/lang/VirtualThread$DelayedTaskSchedulers".equals(owner)
              && "schedule".equals(name)) {
            //            System.out.println(
            //                "opcode:"
            //                    + opcode
            //                    + " owner:"
            //                    + owner
            //                    + " name:"
            //                    + name
            //                    + " descriptor:"
            //                    + descriptor
            //                    + " isInterface:"
            //                    + isInterface);
            invokeStatic(TIME_CLASS, schedule);
          } else if ("java/lang/VirtualThread".equals(owner) && "afterYield".equals(name)) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            loadThis();
            invokeStatic(TIME_CLASS, afterYieldHook);
          } else if (replacingRandom
              && opcode == Opcodes.INVOKESPECIAL
              && owner.equals("java/util/Random")
              && name.equals("<init>")) {
            // Remove constructor args Random(long seed)
            Type[] args = Type.getArgumentTypes(descriptor);
            for (int i = args.length - 1; i >= 0; i--) {
              if (args[i].getSize() == 2) {
                pop2();
              } else {
                pop();
              }
            }
            // replace with our single instance of random
            invokeStatic(Type.getType(SimulationRandom.class), getRandom);
            replacingRandom = false;
          } else if ("java/util/concurrent/Executors".equals(owner)
              && "defaultThreadFactory".equals(name)) {
            // Replace with: Thread.ofVirtual().factory()
            invokeStatic(THREAD_CLASS, ofVirtual);
            invokeInterface(OF_VIRTUAL_CLASS, factory);
          } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }
        }
      };
    }
  }
}
