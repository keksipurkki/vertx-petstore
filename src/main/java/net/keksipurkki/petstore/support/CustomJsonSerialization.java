package net.keksipurkki.petstore.support;

import io.vertx.core.json.JsonObject;

public interface CustomJsonSerialization {
    JsonObject toJson();
}