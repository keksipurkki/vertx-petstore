package net.keksipurkki.petstore.pet;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import java.util.Optional;

public interface Pets {

    Future<Pet> add(NewPet input);
    Future<Pet> update(Pet pet, Buffer image);
    Future<Pet> update(Pet pet);
    Future<Optional<Pet>> getById(int petId);
    Future<Optional<Pet>> delete(int petId);

    static Pets create(Vertx vertx) {
        return new PetsImpl(vertx);
    }

}
