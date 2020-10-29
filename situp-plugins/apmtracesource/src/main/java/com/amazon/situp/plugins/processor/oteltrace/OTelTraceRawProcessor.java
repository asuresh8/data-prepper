package com.amazon.situp.plugins.processor.oteltrace;

import com.amazon.situp.model.PluginType;
import com.amazon.situp.model.annotations.SitupPlugin;
import com.amazon.situp.model.configuration.PluginSetting;
import com.amazon.situp.model.processor.Processor;
import com.amazon.situp.model.record.Record;
import com.amazon.situp.plugins.processor.oteltrace.model.OTelProtoHelper;
import com.amazon.situp.plugins.processor.oteltrace.model.RawSpanBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


@SitupPlugin(name = "otel_trace_raw_processor", type = PluginType.PROCESSOR)
public class OTelTraceRawProcessor implements Processor<Record<ExportTraceServiceRequest>, Record<String>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INSTRUMENTATION_LIBRARY_SPANS = "instrumentationLibrarySpans";
    private static final String INSTRUMENTATION_LIBRARY = "instrumentationLibrary";
    private static final String SPANS = "spans";
    private static final String RESOURCE = "resource";
    private static final String ATTRIBUTES = "attributes";
    private static final String START_TIME_UNIX_NANOS = "startTimeUnixNano";
    private static final String END_TIME_UNIX_NANOS = "endTimeUnixNano";
    private static final String START_TIME = "startTime";
    private static final String END_TIME = "endTime";
    private static final BigDecimal MILLIS_TO_NANOS = new BigDecimal(1_000_000);
    private static final BigDecimal SEC_TO_MILLIS = new BigDecimal(1_000);

    private static final Logger log = LoggerFactory.getLogger(OTelTraceRawProcessor.class);

    //TODO: https://github.com/opendistro-for-elasticsearch/simple-ingest-transformation-utility-pipeline/issues/66
    public OTelTraceRawProcessor(final PluginSetting pluginSetting) {
        this();
    }

    private OTelTraceRawProcessor() {

    }

    /**
     * execute the processor logic which could potentially modify the incoming record. The level to which the record has
     * been modified depends on the implementation
     *
     * @param records Input records that will be modified/processed
     * @return Record  modified output records
     */
    @Override
    public Collection<Record<String>> execute(Collection<Record<ExportTraceServiceRequest>> records) {
        final List<Record<String>> finalRecords = new LinkedList<>();
        for(Record<ExportTraceServiceRequest> ets: records) {
            for (ResourceSpans rs : ets.getData().getResourceSpansList()) {
                try {
                    final String serviceName = OTelProtoHelper.getServiceName(rs.getResource()).orElse(null);
                    final Map<String, Object> resourceAttributes = OTelProtoHelper.getResourceAttributes(rs.getResource());
                    for (InstrumentationLibrarySpans is : rs.getInstrumentationLibrarySpansList()) {
                        for (Span sp : is.getSpansList()) {
                            try {
                                finalRecords.add(new Record<>(new RawSpanBuilder()
                                        .setFromSpan(sp, is.getInstrumentationLibrary(), serviceName, resourceAttributes)
                                        .build().toJson()));
                            } catch (Exception ex) {
                                log.error("Unable to process invalid Span {}:", sp, ex);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("Unable to process invalid ResourceSpan {} :", rs, ex);
                }

            }
        }
        return finalRecords;
    }
}
