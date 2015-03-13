package np.com.axhixh.agent.metric;

import java.lang.instrument.Instrumentation;

public class Agent {
    public static void premain(String agentArgs, Instrumentation inst) {
        // registers the transformer
        inst.addTransformer(new RestApiTransformer());
    }

    public static void report(String metricName, long duration) {
        System.out.println(metricName + " (ms): " + duration);
    }
}
