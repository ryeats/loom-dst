This simple project was inspired by James Baker’s post: https://jbaker.io/2022/05/09/project-loom-for-distributed-systems/. You can read it for a more in-depth explanation of the topic.

I have been interested in trying and learning more about deterministic simulation through after seeing its use in FoundationDB and TigerBeetle, and have dreamed of being able to use it in my own projects. Unfortunately, deterministic simulation isn't practical in most situations because programming languages since they offer many features that aren't inherently deterministic. This means you have to limit yourself to a subset of language features that can be made deterministic and avoid any dependencies that could introduce indeterminism. That’s a big ask—but with Java 24 and Project Loom, idiomatic Java can approach the levels of determinism found in FoundationDB simply by replacing the scheduler in virtual threads and using virtual threads instead of system threads!
 
Java 24 recently completed the implementation of the final blockers that previously caused deadlocks when using virtual threads. So, I wanted to create an example that demonstrates the power of determinism when testing multi-threaded applications, using a simple and easy-to-understand example.

To run this example, you'll need Java 24 installed and a jvm argument --add-opens=java.base/java.lang=ALL-UNNAMED to allow reflection because replacing the scheduler isn't fully supported yet. 

```sh
mvn clean install
java -jar --add-opens=java.base/java.lang=ALL-UNNAMED target/loom-1.0-SNAPSHOT.jar
```

Output should look something like this:
```
8 Threads
Result: A,AB,BC,CD,DE,EF,FG,GH,HBADECGHF
Result: A,AB,BC,CD,DE,EF,FG,GH,HHABCFGED
Result: A,AB,BC,CD,DE,EF,FG,GH,HEHDBCAFG
Result: A,AB,BC,CD,DE,EF,FG,GH,HGDHEBFAC
Single Threaded always A,AAB,BBC,CCD,DDE,EEF,FFG,GGH,HH
Result: A,AAB,BBC,CCD,DDE,EEF,FFG,GGH,HH
Result: A,AAB,BBC,CCD,DDE,EEF,FFG,GGH,HH
Result: A,AAB,BBC,CCD,DDE,EEF,FFG,GGH,HH
Result: A,AAB,BBC,CCD,DDE,EEF,FFG,GGH,HH
Virtual Threads
Result: A,B,C,E,D,BECDAF,G,FH,GHHGDFCBEA
Result: B,A,C,D,AE,BDEF,CFG,H,GHADFHBEGC
Result: B,A,C,D,E,F,BG,HA,FCEGDHBCEAFDGH
Result: A,C,B,D,E,G,F,ABFGH,DCHEADFCHGBE
Deterministic Virtual Threads Seed: -651276032454985122
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Deterministic Virtual ThreadFactory: -651276032454985122
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
Result: B,A,F,E,D,FAEH,BG,C,HDCGGCHAFBED
```

As you can see above, the thread execution order is determined by a seed value so we can randomly explore the state space of all possible execution orders! Additionally, if we run into an error, we can reproduce it with the same seed! This is only simulating thread execution ordering if we simulate system time, network IO and file IO, we can get more benefits from deterministic simulation each trading off by making it a bit  trade off of complexity and .