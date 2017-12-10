#!/usr/bin/env bash

export LAUNCHER="io.vertx.core.Launcher"
export VERTICLE="se.unicodr.MainVerticle"
export CMD="mvn compile"
export VERTX_CMD="run"
export VERTX_DISABLE_DNS="-Dvertx.disableDnsResolver=true"

mvn compile dependency:copy-dependencies
java \
  -cp  $(echo target/dependency/*.jar | tr ' ' ':'):"target/classes" \
  $LAUNCHER $VERTX_CMD $VERTICLE $VERTX_DISABLE_DNS \
  --redeploy="src/main/**/*" --on-redeploy="$CMD" \
  --launcher-class=$LAUNCHER \
  $@
