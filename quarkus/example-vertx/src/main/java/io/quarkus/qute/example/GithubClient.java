package io.quarkus.qute.example;

import static io.quarkus.qute.TemplateExtension.ANY;
import static io.quarkus.qute.ValueResolver.match;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.TemplateExtension;
import io.vertx.axle.core.Vertx;
import io.vertx.axle.ext.web.client.WebClient;
import io.vertx.axle.ext.web.codec.BodyCodec;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Singleton
@Named("client")
public class GithubClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubClient.class);

    Map<String, String> repos;

    WebClient webClient;

    @Inject
    Instance<Vertx> vertx;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx.get());
        repos = new HashMap<>();
        repos.put("quarkus", "/repos/quarkusio/quarkus/pulls?state=open&per_page=10");
        repos.put("thorntail", "/repos/thorntail/thorntail/pulls?state=open&per_page=10");
    }

    // Declarative approach
    @TemplateExtension(matchName = ANY)
    static Object resolveJsonObject(JsonObject object, String name) {
        return object.getValue(name);
    }

    void addJsonResolver(@Observes EngineBuilder builder) {
        // Programmatic approach
        builder.addValueResolver(
                match(JsonArray.class).andMatch("size")
                        .resolve((a, n) -> a.size()));
    }

    public CompletionStage<JsonArray> getPullRequests(String repo) {
        if (!repos.containsKey(repo)) {
            return CompletableFuture.completedFuture(new JsonArray());
        }
        long start = System.currentTimeMillis();
        return webClient
                .get(80, "api.github.com", repos.get(repo)).as(BodyCodec.jsonArray()).send().thenCompose(r -> {
                    LOGGER.info("Response for repo {} received in {} ms", repo, System.currentTimeMillis() - start);
                    if (r.statusCode() == 200) {
                        return CompletableFuture.completedFuture(r.body());
                    } else {
                        CompletableFuture<JsonArray> error = new CompletableFuture<>();
                        error.completeExceptionally(new IllegalStateException());
                        return error;
                    }
                });
    }

}
