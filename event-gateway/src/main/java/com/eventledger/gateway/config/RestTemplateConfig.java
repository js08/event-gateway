////package com.eventledger.gateway.config;
////
////import io.micrometer.tracing.Tracer;
////import lombok.RequiredArgsConstructor;
////import org.springframework.context.annotation.Bean;
////import org.springframework.context.annotation.Configuration;
////import org.springframework.http.client.ClientHttpRequestInterceptor;
////import org.springframework.web.client.RestTemplate;
////
////import java.util.ArrayList;
////import java.util.List;
////
////@Configuration
////@RequiredArgsConstructor
////public class RestTemplateConfig {
////
////    private final Tracer tracer;
////
////    @Bean
////    public RestTemplate restTemplate() {
////        RestTemplate restTemplate = new RestTemplate();
////        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
////
////        // Interceptor to propagate Trace ID across service calls
////        interceptors.add((request, body, execution) -> {
////            if (tracer != null && tracer.currentSpan() != null) {
////                String traceId = tracer.currentSpan().context().traceId();
////                request.getHeaders().add("X-Trace-Id", traceId);
////            }
////            return execution.execute(request, body);
////        });
////
////        restTemplate.setInterceptors(interceptors);
////        return restTemplate;
////    }
////}
//
//
//package com.eventledger.gateway.config;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.micrometer.tracing.Tracer;
//import org.springframework.beans.factory.ObjectProvider;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.client.ClientHttpRequestInterceptor;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//
//@Configuration
//public class RestTemplateConfig {
//
//    // --- ADD THIS BEAN ---
//    @Bean
//    public ObjectMapper objectMapper() {
//        return new ObjectMapper();
//    }
//    // ---------------------
//
//
//
//
//    @Bean
//    public RestTemplate restTemplate(ObjectProvider<Tracer> tracerProvider) {
//        RestTemplate restTemplate = new RestTemplate();
//        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
//
//        interceptors.add((request, body, execution) -> {
//            Tracer tracer = tracerProvider.getIfAvailable();
//            String traceId = (tracer != null && tracer.currentSpan() != null)
//                    ? tracer.currentSpan().context().traceId()
//                    : UUID.randomUUID().toString().replace("-", "");
//
//            request.getHeaders().add("X-Trace-Id", traceId);
//            return execution.execute(request, body);
//        });
//
//        restTemplate.setInterceptors(interceptors);
//        return restTemplate;
//    }
//}


package com.eventledger.gateway.config; // Defines the package location

import io.micrometer.tracing.Tracer; // Micrometer Tracing API for accessing active trace contexts
import lombok.RequiredArgsConstructor; // Lombok annotation to auto-generate constructor for final fields
import lombok.extern.slf4j.Slf4j; // SLF4J logging framework annotation
import org.springframework.context.annotation.Bean; // Spring annotation to declare a Bean definition
import org.springframework.context.annotation.Configuration; // Spring configuration class annotation
import org.springframework.http.client.ClientHttpRequestInterceptor; // Interface for intercepting client-side HTTP requests
import org.springframework.web.client.RestTemplate; // Spring HTTP client class

@Slf4j // Enables logging via 'log.info()', 'log.debug()', etc.
@Configuration // Marks this class as a Spring configuration source
@RequiredArgsConstructor // Generates constructor injecting the Tracer bean automatically
public class RestTemplateConfig {

    // Inject Micrometer Tracer provider (or Sleuth Tracer) to obtain current span details
    private final Tracer tracer;

    /**
     * Defines a RestTemplate Spring Bean with an explicit Trace Header Interceptor.
     */
    @Bean // Tells Spring to manage and inject this RestTemplate instance across the gateway
    public RestTemplate restTemplate() {
        // Instantiate a standard RestTemplate
        RestTemplate restTemplate = new RestTemplate();

        // Retrieve existing interceptors from RestTemplate (or initialize list)
        var interceptors = restTemplate.getInterceptors();

        // Add custom trace context header propagation interceptor
        interceptors.add(traceHeaderInterceptor());

        // Re-assign updated list of interceptors back to RestTemplate
        restTemplate.setInterceptors(interceptors);

        return restTemplate; // Return configured RestTemplate bean
    }

    /**
     * Interceptor that intercepts every outgoing HTTP request made by RestTemplate
     * and explicitly injects W3C Trace Context headers (traceparent / b3).
     */
    private ClientHttpRequestInterceptor traceHeaderInterceptor() {
        return (request, body, execution) -> {

            // Check if there is an active trace span in the current thread context
            if (tracer != null && tracer.currentSpan() != null) {

                // Extract active Trace ID and Span ID strings
                String traceId = tracer.currentSpan().context().traceId();
                String spanId = tracer.currentSpan().context().spanId();

                // 1. Inject W3C Trace Context standard header: traceparent
                // Format: 00-{traceId}-{spanId}-01 (01 indicates sampled trace)
                String traceparent = String.format("00-%s-%s-01", traceId, spanId);
                request.getHeaders().add("traceparent", traceparent);

                // 2. Inject Zipkin / B3 propagation headers for backwards compatibility
                request.getHeaders().add("X-B3-TraceId", traceId);
                request.getHeaders().add("X-B3-SpanId", spanId);
                request.getHeaders().add("X-B3-Sampled", "1");

                log.debug("Explicitly propagated trace headers downstream -> traceparent: {}", traceparent);
            } else {
                log.warn("No active span context found in current thread while calling downstream service.");
            }

            // Continue executing the outgoing HTTP request
            return execution.execute(request, body);
        };
    }
}