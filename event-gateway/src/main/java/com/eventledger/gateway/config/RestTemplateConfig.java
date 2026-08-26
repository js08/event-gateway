package com.eventledger.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RestTemplate restTemplate(ObjectProvider<Tracer> tracerProvider) {
        RestTemplate restTemplate = new RestTemplate();
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());

        // Interceptor to propagate Trace ID across service calls
        interceptors.add((request, body, execution) -> {
            Tracer tracer = tracerProvider.getIfAvailable();
            String traceId = (tracer != null && tracer.currentSpan() != null)
                    ? tracer.currentSpan().context().traceId()
                    : UUID.randomUUID().toString().replace("-", "");

            request.getHeaders().add("X-Trace-Id", traceId);
            log.debug("Propagating traceId: {} to downstream service", traceId);
            return execution.execute(request, body);
        });

        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}