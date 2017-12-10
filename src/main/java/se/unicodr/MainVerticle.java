package se.unicodr;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    Future<String> servicePersistenceVerticleDeployment = Future.future();
    vertx.deployVerticle(
      new ServicePersistenceVerticle(),
      servicePersistenceVerticleDeployment.completer());

    servicePersistenceVerticleDeployment.compose(result -> {

      Future<String> httpVerticleDeployment = Future.future();
      vertx.deployVerticle(
        //Don't pass an instance but instead what to instance
        HttpServerVerticle.class.getName(),
        new DeploymentOptions().setInstances(2),
        httpVerticleDeployment.completer());

      return httpVerticleDeployment;

    }).compose(result -> {
      Future<String> serviceCheckerDeployment = Future.future();
      vertx.deployVerticle(
        new ServiceCheckerVerticle(),
        serviceCheckerDeployment.completer());

      return serviceCheckerDeployment;
    }).setHandler(ar -> {
      if (ar.succeeded()) {
        startFuture.complete();
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }

}
