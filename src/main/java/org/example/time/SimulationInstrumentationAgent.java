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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
        } catch (Exception ignored) {
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
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    ClassVisitor cv = new TimeInstrumenatorVisitor(cw);
    cr.accept(cv, ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  public static class TimeInstrumenatorVisitor extends ClassVisitor {

    public static final Type TIME_CLASS =
        Type.getObjectType(SimulationTime.class.getName().replace('.', '/'));
    private final Method onCurrentMillis;
    private final Method onInstantNow;
    private final Method onNanoTime;

    public TimeInstrumenatorVisitor(ClassVisitor cv) {
      super(Opcodes.ASM9, cv);
      try {

        onCurrentMillis =
            Method.getMethod(SimulationTime.class.getDeclaredMethod("onCurrentTimeMillis", null));
        onNanoTime = Method.getMethod(SimulationTime.class.getDeclaredMethod("onNanoTime", null));
        onInstantNow =
            Method.getMethod(SimulationTime.class.getDeclaredMethod("onInstantNow", null));
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

      return new GeneratorAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
        @Override
        public void visitMethodInsn(
            int opcode, String owner, String name, String descriptor, boolean isInterface) {
          if ("java/lang/System".equals(owner) && "nanoTime".equals(name)) {
            invokeStatic(TIME_CLASS, onNanoTime);
          } else if ("java/lang/System".equals(owner) && "currentTimeMillis".equals(name)) {
            invokeStatic(TIME_CLASS, onCurrentMillis);
          } else if ("java/time/Instant".equals(owner) && "now".equals(name)) {
            invokeStatic(TIME_CLASS, onInstantNow);
          } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }
        }
      };
    }
  }
}
