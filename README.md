This simple project was inspired by James Baker’s post: https://jbaker.io/2022/05/09/project-loom-for-distributed-systems/. You can read it for a more in-depth explanation of the topic.

I have been interested in learning more about deterministic simulation after seeing its use in FoundationDB and TigerBeetle, and have dreamed of being able to use it in my own projects. Unfortunately, deterministic simulation isn't practical in most situations because programming languages offer many features that aren't inherently deterministic. This means you have to limit yourself to a subset of language features that can be made deterministic and avoid any dependencies that could introduce indeterminism. That’s a big ask—but with Java 24 and Project Loom, idiomatic Java can approach the levels of determinism found in FoundationDB simply by replacing the scheduler in virtual threads and using virtual threads instead of system threads!
 
Java 24 recently resolved the remaining issues that could cause deadlocks with virtual threads. So, I wanted to create an example that demonstrates the power of determinism when testing multithreaded applications.

To run this example, you'll need Java 24 installed and a jvm argument --add-opens=java.base/java.lang=ALL-UNNAMED to allow reflection because replacing the scheduler isn't fully supported yet. 

```sh
mvn clean install
java -jar --add-opens=java.base/java.lang=ALL-UNNAMED target/loom-1.0-SNAPSHOT.jar
```

Output should look something like this:
```
Single Threaded (baseline to compare against when executed in order)
a1a2a3a4a5aab1b2b3b4b5bbc1c2c3c4c5ccd1d2d3d4d5dde1e2e3e4e5ee
a1a2a3a4a5aab1b2b3b4b5bbc1c2c3c4c5ccd1d2d3d4d5dde1e2e3e4e5ee true
a1a2a3a4a5aab1b2b3b4b5bbc1c2c3c4c5ccd1d2d3d4d5dde1e2e3e4e5ee true
a1a2a3a4a5aab1b2b3b4b5bbc1c2c3c4c5ccd1d2d3d4d5dde1e2e3e4e5ee true
8 Threads
a1ab1bc1ce1ed1d2a3a4a5a2d2e3d3e22cb3c3b444d5d4c5ceb5e5bacdeb
a1ab1bc1cd1de1e2222bd3b3d2ea3e3ac3c4444c54ce5eb5bda5a5dadceb false
a1ab1bc1cd1de1e22ba23db3d3a2e3e2c3c4444a5ab5b4c5cd5de5eceabd false
a1ab1be1ed1dc1c22b23be3ed3d2a3a2c3c4444b45be5ea5ac5cd5dbacde false
Virtual Threads
abcde1a1e1d1c1b2a3a4a2e3e4e2d3d4d2c3c4c2b3b4b5a5e5d5c5bcdbae
abcde1a1e1d1c1b2a3a4a2e3e4e2d3d4d2c3c4c2b3b4b5a5e5d5c5bbdaec false
abcde1a1e1d1c1b2a3a4a2e3e4e2d3d4d2c3c4c2b3b4b5a5e5d5c5bdbcea false
abcde1a1e1d1c1b2a3a4a2e3e4e2d3d4d2c3c4c2b3b4b5a5e5d5c5baedcb false
Deterministic Virtual Threads seed: -3327071828508320014
ecadb1e2e3e4e1b5e2b3b4b1d5b2d1a2a3a4a5a1c3d4d2c3c4c5d5cebadc
ecadb1e2e3e4e1b5e2b3b4b5b1d2d1a2a3a4a5a1c3d4d2c3c4c5d5cebadc false
ecadb1e2e3e4e1b5e2b3b4b1d5b2d1a2a3a4a5a1c3d4d2c3c4c5d5cebadc true
ecadb1e2e3e4e1b5e2b3b4b1d5b2d1a2a3a4a5a1c3d4d2c3c4c5d5cebadc true
ecadb1e2e3e4e1b5e2b3b4b1d5b2d1a2a3a4a5a1c3d4d2c3c4c5d5cebadc true
```

As you can see above, the thread execution order can mostly be determined by a seed value so we can randomly explore the state space of all possible execution orders! Additionally, if we run into an error, we can likely reproduce it with the same seed! 

NOTE: The issue with my current approach is that executions slept or blocked by synchronous IO won't show up in the work queue immediately so if the loop is too quick they can show up in different iterations of the loop when a simulation is rerun which introduces indeterminism.  