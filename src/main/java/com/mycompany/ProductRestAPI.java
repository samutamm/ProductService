package com.mycompany;

import com.google.gson.Gson;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mycompany.domain.*;
import com.mycompany.domain.Error;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import java.lang.management.ManagementFactory;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.halt;
import static spark.SparkBase.port;

public class ProductRestAPI {

    private Gson gson;
    private Morphia morphia;
    private Datastore datastore;

    public ProductRestAPI() {
        this.gson = new Gson();
        setUpDB();
        configure();
        createRoutes();
    }

    private void configure() {
        String port = System.getenv("PORT");
        if (port != null) {
            port(Integer.parseInt(port));
        }
    }

    private void setUpDB() {
        this.morphia = new Morphia();
        MongoClient mongo = new MongoClient();
        MongoDatabase persons = mongo.getDatabase("microservices");
        morphia.mapPackage("com.mycompany.domain");
        this.datastore = morphia.createDatastore(mongo, persons.getName());
    }

    private void createRoutes() {
        get("/ping", (request, response) -> {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            String dir = System.getProperty("user.dir");

            return "{ \"name\": \"" + name + "\", \"dir\": \"" + dir + "\" }";
        });

        get("/products", (request, response) -> {
            return datastore.find(Product.class).asList();
        }, new JsonTransformer());

        post("/products", (request, response) -> {
            Product product = gson.fromJson(request.body(), Product.class);

            if ( product == null || !product.valid()) {
                halt(400, gson.toJson(com.mycompany.domain.Error.withCause("all fields must have a value")));
            }

            if ( datastore.createQuery(Product.class).field("name").equal(product.name()).get() != null ){
                halt(400, gson.toJson(Error.withCause("name must be unique")));
            }

            datastore.save(product);
            return product;
        });
    }
}
