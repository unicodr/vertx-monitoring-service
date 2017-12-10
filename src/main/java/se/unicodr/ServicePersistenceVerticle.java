package se.unicodr;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import se.unicodr.domain.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ServicePersistenceVerticle extends AbstractVerticle {

  private static final String CONFIG_SERVICE_PERSISTENCE_QUEUE = "services.queue";
  private static final String FILE_PATH = "services.json";
  private static final Logger LOGGER = LoggerFactory.getLogger(ServicePersistenceVerticle.class);

  @Override
  public void start(Future<Void> startFuture) {
    vertx.eventBus().consumer(config().getString(CONFIG_SERVICE_PERSISTENCE_QUEUE, "services.queue"),
      this::onMessage);
    startFuture.complete();
    LOGGER.info("ServicePersistenceVerticle deployed");
  }

  private void onMessage(Message<JsonObject> message) {
    if (!message.headers().contains("action")) {
      LOGGER.error("Message headers did not contain any action, headers: " + message.headers());
      message.fail(HttpResponseStatus.BAD_REQUEST.code(), "No action specified.");
      return;
    }
    String action = message.headers().get("action");

    switch (action) {
      case "get-services":
        getServices(message);
        break;
      case "create-service":
        createService(message);
        break;
      case "update-service":
        updateService(message);
        break;
      case "delete-service":
        deleteService(message);
        break;
      default:
        message.fail(HttpResponseStatus.BAD_REQUEST.code(), String.format("Action: %s unexpected", action));
    }
  }

  private void getServices(Message<JsonObject> message) {
    vertx.fileSystem().readFile(FILE_PATH, result -> {
      if (result.succeeded()) {
        JsonObject services = result.result().toJsonObject();
        message.reply(services);
      } else {
        message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), result.cause().getMessage());
      }
    });
  }

  private void createService(Message<JsonObject> message) {
    vertx.fileSystem().readFile(FILE_PATH, readResult -> {
      if (readResult.succeeded()) {
        JsonObject serviceToCreate = message.body();
        if (validServiceRequest(serviceToCreate)) {
          JsonObject services = readResult.result().toJsonObject();
          JsonArray serviceList = services.getJsonArray("services");

          boolean serviceAlreadyExists = serviceList.stream()
            .map(JsonObject::mapFrom)
            .map(jsonObject -> jsonObject.getString("name"))
            .anyMatch(name -> name.equals(serviceToCreate.getString("name")));

          if (!serviceAlreadyExists) {
            serviceToCreate.put("id", UUID.randomUUID().toString());
            serviceList.add(serviceToCreate);
            vertx.fileSystem().writeFile(FILE_PATH, new JsonObject().put("services", serviceList).toBuffer(),
              writeResult -> {
                if (writeResult.succeeded()) {
                  message.reply("ok");
                } else {
                  message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), writeResult.cause().getMessage());
                }
              });
          } else {
            message.fail(HttpResponseStatus.BAD_REQUEST.code(), String.format("Service %s already exists",
              serviceToCreate.getString("name")));
          }
        } else {
          message.fail(HttpResponseStatus.BAD_REQUEST.code(), String.format("Valid name: %s and url: %s are required.",
            serviceToCreate.getString("name"),
            serviceToCreate.getString("url")));
        }
      } else {
        message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), readResult.cause().getMessage());
      }
    });

  }

  private void updateService(Message<JsonObject> message) {
    vertx.fileSystem().readFile(FILE_PATH, readResult -> {
      if (readResult.succeeded()) {
        JsonObject incomingService = message.body();
        if (validServiceRequest(incomingService)) {
          JsonObject services = readResult.result().toJsonObject();
          JsonArray serviceList = services.getJsonArray("services");

          List<Service> servicesAsObjects = serviceList.stream()
            .map(object -> (JsonObject) object)
            .map(jsonObject -> jsonObject.mapTo(Service.class))
            .collect(Collectors.toList());

          List<Service> updatedServices = servicesAsObjects.stream()
            .filter(service -> service.getId().equals(incomingService.getString("id")))
            .map(service -> updateService(service, incomingService))
            .collect(Collectors.toList());

          if (updatedServices == null || updatedServices.isEmpty() || updatedServices.size() != 1) {
            message.fail(HttpResponseStatus.BAD_REQUEST.code(), String.format("Cannot update, service with id %s didn't match any existing service",
              incomingService.getString("id")));
          }

          JsonArray array = new JsonArray();
          servicesAsObjects.stream()
            .map(JsonObject::mapFrom)
            .forEach(array::add);

          vertx.fileSystem().writeFile(FILE_PATH, new JsonObject().put("services", array).toBuffer(),
            writeResult -> {
              if (writeResult.succeeded()) {
                message.reply("ok");
              } else {
                message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), writeResult.cause().getMessage());
              }
            });
        } else {
          message.fail(HttpResponseStatus.BAD_REQUEST.code(), String.format("Valid name: %s and url: %s are required.",
            incomingService.getString("name"),
            incomingService.getString("url")));
        }
      } else {
        message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), readResult.cause().getMessage());
      }
    });
  }

  private Service updateService(Service serviceToUpdate, JsonObject incomingService) {
    serviceToUpdate.setName(incomingService.getString("name"));
    serviceToUpdate.setUrl(incomingService.getString("url"));
    serviceToUpdate.setStatus(incomingService.getString("status"));
    serviceToUpdate.setLastCheck(incomingService.getString("lastCheck"));
    return serviceToUpdate;
  }


  private void deleteService(Message<JsonObject> message) {
    vertx.fileSystem().readFile(FILE_PATH, readResult -> {
      if (readResult.succeeded()) {
        String idToDelete = message.body().getString("id");
        JsonObject services = readResult.result().toJsonObject();
        JsonArray serviceList = services.getJsonArray("services");

        List<Service> servicesAsObjects = serviceList.stream()
          .map(object -> (JsonObject) object)
          .map(jsonObject -> jsonObject.mapTo(Service.class))
          .collect(Collectors.toList());

        List<Service> servicesToDelete = servicesAsObjects.stream()
          .filter(service -> service.getId().equals(idToDelete))
          .collect(Collectors.toList());

        if (servicesToDelete != null && !servicesToDelete.isEmpty() && servicesToDelete.size() == 1) {
          JsonArray array = new JsonArray();
          servicesAsObjects.stream()
            .filter(service -> !service.getId().equals(idToDelete))
            .map(JsonObject::mapFrom)
            .forEach(array::add);

          vertx.fileSystem().writeFile(FILE_PATH, new JsonObject().put("services", array).toBuffer(),
            writeResult -> {
              if (writeResult.succeeded()) {
                message.reply("ok");
              } else {
                message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), writeResult.cause().getMessage());
              }
            });
        } else {
          message.fail(HttpResponseStatus.BAD_REQUEST.code(), String.format("Cannot delete, service with id %s didn't match any existing service",
            idToDelete));
        }
      } else {
        message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), readResult.cause().getMessage());
      }
    });
  }

  private boolean validServiceRequest(JsonObject serviceToCreate) {
    if (serviceToCreate.containsKey("name") && serviceToCreate.containsKey("url")) {
      String name = serviceToCreate.getString("name");
      String url = serviceToCreate.getString("url");
      return name != null && !name.isEmpty()
        && url != null && !url.isEmpty() && url.startsWith("http");
    }
    return false;
  }
}
