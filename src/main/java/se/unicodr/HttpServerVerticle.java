package se.unicodr;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.UUID;

public class HttpServerVerticle extends AbstractVerticle {

  private static final String CONFIG_SERVICE_PERSISTENCE_QUEUE = "services.queue";
  private static final int DEFAULT_PORT = 8080;
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  private String persistenceQueue;

  @Override
  public void start(Future<Void> startFuture) {

    persistenceQueue = config().getString(CONFIG_SERVICE_PERSISTENCE_QUEUE, "services.queue");

    Router router = Router.router(vertx);
    router.route().handler(CorsHandler.create("*")
      .allowedMethod(HttpMethod.GET)
      .allowedMethod(HttpMethod.POST)
      .allowedMethod(HttpMethod.DELETE)
      .allowedMethod(HttpMethod.OPTIONS)
      .allowedHeader("Content-Type"));
    router.route().handler(BodyHandler.create());
    router.get("/services/").handler(this::getServices);
    router.post("/services/").handler(this::addService);
    router.put("/services/:serviceId").handler(this::updateService);
    router.delete("/services/:serviceId").handler(this::deleteService);

    String port = System.getenv("PORT");
    int portToUse = port != null ? Integer.parseInt(port) : DEFAULT_PORT;

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(portToUse, result -> {
        if (result.succeeded()) {
          LOGGER.info("HTTP server is running on port: " + portToUse);
          startFuture.complete();
        } else {
          LOGGER.error("Could not start HTTP server", result.cause());
          startFuture.fail(result.cause());
        }
      });
  }

  private void getServices(final RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-services");

    vertx.eventBus().send(persistenceQueue, new JsonObject(), options, reply -> {
      if (reply.succeeded()) {
        JsonObject body = (JsonObject) reply.result().body();
        if (body == null) {
          response
            .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
            .end("Bad request, missing body");
        } else {
          JsonArray services = body.getJsonArray("services");
          response.setStatusCode(HttpResponseStatus.OK.code())
            .putHeader("Content-Type", "application/json")
            .putHeader("Access-Control-Allow-Origin", "*")
            .end(services.encodePrettily());
        }
      } else {
        response
          .setStatusCode(((ReplyException) reply.cause()).failureCode())
          .end(reply.cause().getMessage());
      }
    });
  }

  private void addService(final RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    JsonObject serviceFromRequest = routingContext.getBodyAsJson();
    String id = UUID.randomUUID().toString();
    serviceFromRequest.put("id", id);

    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("action", "create-service");
    vertx.eventBus().send(persistenceQueue, serviceFromRequest, options, reply -> {
      if (reply.succeeded()) {
        response.setStatusCode(HttpResponseStatus.CREATED.code())
          .end(String.format("Created service with id %s", id));
      } else {
        response
          .setStatusCode(((ReplyException) reply.cause()).failureCode())
          .end(reply.cause().getMessage());
      }
    });
  }

  private void updateService(final RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    String id = routingContext.request().getParam("serviceId");
    JsonObject serviceFromRequest = routingContext.getBodyAsJson();
    serviceFromRequest.put("id", id);

    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("action", "update-service");
    vertx.eventBus().send(persistenceQueue, serviceFromRequest, options, reply -> {
      if (reply.succeeded()) {
        response.setStatusCode(HttpResponseStatus.OK.code())
          .putHeader("Content-Type", "application/json")
          .end();
      } else {
        response
          .setStatusCode(((ReplyException) reply.cause()).failureCode())
          .end(reply.cause().getMessage());
      }
    });
  }

  private void deleteService(final RoutingContext routingContext) {
    String id = routingContext.request().getParam("serviceId");
    HttpServerResponse response = routingContext.response();
    JsonObject request = new JsonObject().put("id", id);
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-service");
    vertx.eventBus().send(persistenceQueue, request, options, reply -> {
      if (reply.succeeded()) {
        response.setStatusCode(HttpResponseStatus.OK.code())
          .putHeader("Location", "/")
          .end();
      } else {
        response.putHeader("Content-Type", "application/json")
          .setStatusCode(((ReplyException) reply.cause()).failureCode())
          .end(reply.cause().getMessage());
      }
    });
  }
}
