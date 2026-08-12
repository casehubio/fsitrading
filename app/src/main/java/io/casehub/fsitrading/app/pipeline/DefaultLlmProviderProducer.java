package io.casehub.fsitrading.app.pipeline;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@ApplicationScoped
public class DefaultLlmProviderProducer {

    private static final Logger log = Logger.getLogger(DefaultLlmProviderProducer.class);

    @Produces @DefaultBean @Singleton @Named("llmProvider")
    public Function<String, CompletionStage<String>> defaultLlmProvider() {
        return prompt -> {
            log.warn("No LLM provider configured — returning empty response. Set up a real provider for regime/narrative synthesis.");
            return CompletableFuture.completedFuture("{}");
        };
    }
}
