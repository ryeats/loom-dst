This is a list of what I believe needs to be instrumented for this to be useful deterministic simulation covering Time, IO, Random, and Thread execution ordering determinism.

Time:
- [X] System.currentTimeMillis - replace with the simulations clock
- [X] System.nanoTime - replace with the simulations clock
- [X] Instant.now() - replace with the simulations clock
- [ ] VM.getNanoTimeAdjustment() 
- [ ] Calendar, Date
- [ ] Clock.systemDefaultZone(), Clock.system(zone): LocalDate.now(), LocalTime.now(), LocalDateTime.now(), ZoneDateTime.now()

VirtualThread:
- [X] VirtualThread.scheduler field initialized with the simulations deterministic scheduler instead of default ForkJoinPool
- [X] VirtualThread.<init> Prevent static block from starting VirtualThread-unblocker thread
- [X] VirtualThread.schedule(...) send calls to DelayedTaskSchedulers.schedule to the simulations ScheduledExecutorService

Random:
- [X] new Random(...) - replace all instantiations with the simulations single instance
- [ ] new SecureRandom(...) - Instrument with SecureRandomSpi
- [ ] ThreadLocalRandom
- [ ] RandomGeneratorFactory

Executors & Threads:
- [x] Executors.defaultThreadFactory() - virtual thread ThreadFactory
- [x] Thread.ofVirtual().scheduler - initialize field with the simulations deterministic scheduler #TODO this currently is initialized extremely late 
- [ ] ScheduledExecutorsService - May not need additional work if system time and theadFactory instrumented correctly
- [ ] Thread.ofPlatform()
- [ ] new Thread(...)
- [ ] Executors.newSingleThreadExecutor(...) - special case this should be its own work pool so we execute submitted tasks in order
- [ ] Executors.newSingleThreadScheduledExecutor(...) - special case this should be its own work pool so we execute submitted tasks in order
- [ ] ForkJoinPool, .stream().parallel() and anything that extends Thread
- [ ] Timer

java.nio:
- [x] FileSystem - JimFS or https://github.com/marschall/memoryfilesystem
- [ ] SelectorProvider or higher level equivalent - In Memory Netty LocalServerChannel,LocalChannel, LocalIoHandler.newFactory()

ImmutableCollections:
- [ ] SALT32L, REVERSE

volatile:
- [ ] fields - add Thread.yield() before or after

All of the above requires an agent it is my hope for smaller kernels of logic it will also be possible to provide an agentless API where the Simulation provides: Clock, ThreadFactory, ScheduledExecutorService, Executor, ExecutorService, Random and java.nio File/Network IO.

Native calls, ThreadLocals, synchronous IO, and anything that extends Thread will just have to be worked around. I am hopeful that they will not actually impact the utility of this endeavor.