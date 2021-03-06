package net.keksipurkki.petstore.http;

import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import io.vertx.ext.web.validation.BadRequestException;
import net.keksipurkki.petstore.api.*;
import net.keksipurkki.petstore.pet.Pets;
import net.keksipurkki.petstore.store.Orders;
import net.keksipurkki.petstore.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

public class HttpVerticle extends AbstractVerticle {

    public static String CONTEXT_PATH = "/petstore/v1";
    public static String DIAGNOSTICS_PATH = "/_diagnostics";

    private final static Logger logger = LoggerFactory.getLogger(HttpVerticle.class);
    private Api api;

    private HttpServer server;

    public HttpVerticle withApi(Api api) {
        this.api = api;
        return this;
    }

    @Override
    final public void start(Promise<Void> promise) {
        var openapi = "api.yaml";
        logger.info("Reading OpenAPI specification from {}", openapi);

        RouterBuilder.create(vertx, openapi)
                     .map(this::setOptions)
                     .map(this::createRouter)
                     .map(this::createServer)
                     .onComplete(done(promise));
    }

    private RouterBuilder setOptions(RouterBuilder builder) {
        var opts = new RouterBuilderOptions()
            .setRequireSecurityHandlers(false);

        builder.setOptions(opts);
        return builder;
    }

    public Router createRouter(RouterBuilder builder) {
        logger.trace("Mounting OpenAPI routes");

        builder.bodyHandler(Middlewares.bodyHandler());

        for (var route : builder.operations()) {

            logger.info("Mounting {} ({} {})", route.getOperationId(), route.getHttpMethod(), route.getOpenAPIPath());

            var operation = ApiOperation.from(route.getOperationId());

            builder.operation(operation.name())
                   .handler(Middlewares.defaultHeaders())
                   .handler(Middlewares.requestTracing(System.getenv()))
                   .handler(Middlewares.jwtVerification(operation))
                   .handler(operation.withApi(api));
        }

        return builder.createRouter();
    }

    public HttpServer createServer(Router openApiRouter) {
        var root = Router.router(vertx);
        var server = vertx.createHttpServer();
        var failureHandler = createFailureHandler();

        root.route(wildcard(CONTEXT_PATH)).subRouter(openApiRouter);
        root.route(wildcard(DIAGNOSTICS_PATH)).subRouter(diagnosticsRouter());
        root.route().handler(Middlewares.routeNotFound());

        // Route *all* errors during requests to a failure handler
        root.route().failureHandler(failureHandler);

        // Why would this be ever invoked?
        root.errorHandler(500, rc -> {
            logger.warn("Error handler invoked unexpectedly");
            failureHandler.handle(rc);
        });

        return server.requestHandler(root);
    }

    public FailureHandler createFailureHandler() {
        var failureHandler = new FailureHandler();

        // Default
        failureHandler.on(ApiException.class, e -> e);

        // OpenAPI schema violation as per Vert.x Validation Service
        failureHandler.on(BadRequestException.class, cause -> new ApiContractException("Bad request", cause));

        // Authentication related problems
        failureHandler.on(SecurityException.class, cause -> new UnauthorizedException("Unauthorized", cause));

        // Last resort to Internal Server Error
        failureHandler.on(Throwable.class, cause -> new UnexpectedApiException("Internal server error", cause));

        return failureHandler;
    }

    private String wildcard(String path) {
        return path + "*";
    }

    private Router diagnosticsRouter() {
        var router = Router.router(vertx);

        router.route().handler(rc -> {
            rc.response().setStatusCode(200).end("Diagnostics!");
        });

        return router;
    }

    private Handler<AsyncResult<HttpServer>> done(Promise<Void> promise) {
        return ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
            } else {
                this.server = ar.result();
                promise.complete();
            }
        };
    }

    public Future<HttpServer> listen(int port) {
        requireNonNull(server, "Server has not been deployed yet");
        return vertx.executeBlocking(p -> {
            logger.info("Server will bind to port {}", port);
            server.listen(port).onSuccess(p::complete).onFailure(p::fail);
        });
    }

    public int getPort() {
        requireNonNull(server, "Server has not been deployed yet");
        return server.actualPort();
    }

}
