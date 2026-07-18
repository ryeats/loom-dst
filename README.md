This simple project was inspired by James Baker’s post: https://jbaker.io/2022/05/09/project-loom-for-distributed-systems/. You can read it for a more in-depth explanation of the topic.

I have been interested in learning more about deterministic simulation after seeing its use in FoundationDB and TigerBeetle, and have dreamed of being able to use it in my own projects. Unfortunately, deterministic simulation isn't practical in most situations because programming languages offer many features that aren't inherently deterministic. This means you have to limit yourself to a subset of language features that can be made deterministic and avoid any dependencies that could introduce indeterminism. That’s a big ask—but with Java 25 and Project Loom, idiomatic Java can approach the levels of determinism found in FoundationDB by replacing the scheduler in virtual threads and using virtual threads instead of system threads!
 
Java 24 recently resolved the remaining issues that could cause deadlocks with virtual threads. So, I wanted to create an example that demonstrates the power of determinism when testing multithreaded applications.

To run this example, you'll need Java 24 installed and a jvm argument --add-opens=java.base/java.lang=ALL-UNNAMED to allow reflection because replacing the scheduler isn't fully supported yet. 

```sh
mvn clean install
java -jar --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -javaagent:./target/loom-dst-1.0-SNAPSHOT.jar target/loom-dst-1.0-SNAPSHOT.jar 
```

Output should look something like this:
```
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
BootstrapClass added to bootstrap classloader search path.
Seed: -8769667638898062009L
beca0c1c2c0b1bd0d1d0a1a2b0e1e3c3b2e2d4c3e4b5c3d4e2a6c7c8c3a5b4d5e4a6b5d6e5a6d7b8b7e8e6a7a8a7d8d 6666666555444444445555433322224455554544576655555555888887655432234445543347666556776776556555433432222111111111111111111111111111111111
beca0c1c2c0b1bd0d1d0a1a2b0e1e3c3b2e2d4c3e4b5c3d4e2a6c7c8c3a5b4d5e4a6b5d6e5a6d7b8b7e8e6a7a8a7d8d 6666666555444444445555433322224455554544576655555555888887655432234445543347666556776776556555433432222111111111111111111111111111111111
```

As you can see above, the thread execution order can be determined by a seed value so we can randomly explore the state space of all possible execution orders! Additionally, if we run into an error, we can likely reproduce it with the same seed!