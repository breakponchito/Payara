package fish.payara.microprofile.faulttolerance;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/**
 * Class to define methods to register Telemetry Metrics for FaultTolerance
 */
public class FaultToleranceTelemetryMetricsRecorder {

    private static final String FT_INVOCATIONS_TOTAL = "ft.invocations.total";
    private static final String FT_INVOCATIONS_TOTAL_DESCRIPTION = """
            The number of times the method was called.
            """;
    private static final String FALLBACK_NAME = "fallback";
    private static final String METHOD_ATTRIBUTE_NAME = "method";

    /**
     * this method will help to report ft.invocations.total metric for Fault Tolerance using Telemetry api
     * @param classAndMethodName
     * @param currentMeter
     */
    public static void createFTInvocationTotalMeter(String classAndMethodName, Meter currentMeter) {
        Attributes methodAttribute = Attributes.builder().put(AttributeKey.stringKey(METHOD_ATTRIBUTE_NAME), classAndMethodName).build();
        LongCounter longCounter = currentMeter.counterBuilder(FT_INVOCATIONS_TOTAL).setDescription(FT_INVOCATIONS_TOTAL_DESCRIPTION).build();
        AttributeKey<String> key = AttributeKey.stringKey(FALLBACK_NAME);
        AttributeKey<String> resultKey = AttributeKey.stringKey("result");
        Attributes attribute = Attributes.builder().putAll(methodAttribute).put(key, "notApplied").put(resultKey, "valueReturned").build();
        longCounter.add(1, attribute);
    }
    
}
