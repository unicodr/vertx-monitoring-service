package se.unicodr;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import se.unicodr.domain.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ServiceCheckerVerticle extends AbstractVerticle {

  private static final long CHECK_SERVICE_INTERVAL = Duration.ofMinutes(1).toMillis();
  private static final String CONFIG_SERVICE_PERSISTENCE_QUEUE = "services.queue";
  private static final Logger LOGGER = LoggerFactory.getLogger(ServiceCheckerVerticle.class);
  private String persistenceQueue;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    persistenceQueue = config().getString(CONFIG_SERVICE_PERSISTENCE_QUEUE, "services.queue");

    vertx.setPeriodic(CHECK_SERVICE_INTERVAL, id -> checkServices());
    startFuture.complete();
  }

  private void checkServices() {

    DeliveryOptions getServicesOptions = new DeliveryOptions().addHeader("action", "get-services");
    DeliveryOptions updateServiceOptions = new DeliveryOptions().addHeader("action", "update-service");

    HttpClientOptions options = new HttpClientOptions()
      .setTrustAll(true)
      .setSsl(true)
      .setDefaultPort(443)
      .setLogActivity(true);

    HttpClient httpClient = vertx.createHttpClient(options);

    vertx.eventBus().send(persistenceQueue, new JsonObject(), getServicesOptions, reply -> {
      if (reply.succeeded()) {
        JsonObject services = (JsonObject) reply.result().body();
        JsonArray serviceList = services.getJsonArray("services");

        List<Service> servicesAsObjects = serviceList.stream()
          .map(object -> (JsonObject) object)
          .map(jsonObject -> jsonObject.mapTo(Service.class))
          .collect(Collectors.toList());

        servicesAsObjects.stream()
          .forEach(service -> {
            String host = service.getUrl().split("://")[1];
            httpClient.get(host, "/", response -> {
              if (response.statusCode() == HttpResponseStatus.OK.code()) {
                service.setStatus("OK");
              } else {
                service.setStatus("FAIL");
              }
              LocalDateTime now = LocalDateTime.now();
              DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
              String formatDateTime = now.format(formatter);
              service.setLastCheck(formatDateTime);
              vertx.eventBus().send(persistenceQueue, JsonObject.mapFrom(service), updateServiceOptions, updateReply -> {
                if (updateReply.succeeded()) {
                  LOGGER.info("Updated status for service with id: " + service.getId());
                } else {
                  LOGGER.warn("Failed to update status for service with id: " + service.getId());
                }
              });
            }).setFollowRedirects(true).end();
          });
      }
    });
  }
}
