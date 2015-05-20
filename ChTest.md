# Introduction #
`ChTest` is evolution of ViCluster. ViCluster library has introduced concept of "virtual" cluster of _vi-nodes_ and offered convenient ways to configure, start and run code in "virtual" cluster. With time we have found this API so convenient that we really wished to reuse it for **real** cluster management. Generic implementation of _vi-node_ based cluster have become Nanocloud project (which is currently offers multiple ways to organize _vi-node_ cluster). `ChTest`, in turn, have inherited all Coherence specific stuff. `ChTest` is a successor of ViCluster using Nanocloud as execution framework.

Coherence is a cluster library, a lot of test scenarios require multiple JVMs to replicate functionality. Historically, predecessors of this project have started with simulating multiple JVMs using classloader isolation.
With time more features have been added, and now `ChTest` could manage both "simulated" and real JVMs, not to mention support for per `vi-node` classpath manipulation, process death simulation, etc.

# Key features #
  * Convenient way to configure virtual cluster topology.
  * Support for seamless in-process or out-of-process _vi-node_ execution.
  * Have independent **system properties** for each isolate.
  * **Classpath** of each isolate could be tweaked independently.
  * Utility to choose Coherence jar version per _vi-node_.
  * Output of all _vi\_nodes_ prefixed and gathered in master console.
  * _vi-node_ could be forcibly terminated.
  * _vi-node_ can be suspended/resumed (server crash and long GC pauses could be simulated this way).
  * Test code can be executed in context of any JVM.
  * Coherence MBean could be exposed even for in-process clusters.
  * Convenient parallel code execution.
  * A lot of helper methods for tweaking and poking Coherence cluster in test scope.

# Getting started #
TODO

# Source code and documentation #

Both jar-files, javadocs and source code are available via maven:

```
<dependency>
	<groupId>org.gridkit.coherence-tools</groupId>
	<artifactId>chtest</artifactId>
	<version>0.2.6</version>
	<scope>test</scope>
</dependency>
```

Available from Maven central repository.