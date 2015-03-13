#Metric Agent

Sample code to use Java instrumentation to get performance 
metric of a JAX-RS application without having to annotate 
or modify the JAX-RS application.

#Running
For example using the sample JAX-RS application at
https://github.com/axhixh/resteasy-bootstrap/

    ```java -javaagent:metric-agent.jar -cp resteasy-bootstrap.jar np.com.axhixh.bootstrap.App```

The jars are uber jars with dependencies.
