package net.keksipurkki.petstore.user;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.List;
import java.util.Optional;

public interface Users {

    Future<User> create(User data);

    Future<Optional<User>> findByUsername(String username);

    Future<User> update(String username, User update);

    Future<Void> delete(User user);

    boolean areUnique(List<User> users);

    static Users create(Vertx vertx) {
        return new UsersImpl(vertx);
    }

}
