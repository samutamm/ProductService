package com.mycompany;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mycompany.domain.*;
import com.mycompany.domain.Error;
import com.mycompany.rest.ApiResponse;
import com.mycompany.rest.Http;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import java.lang.management.ManagementFactory;
import spark.Response;

import static spark.Spark.*;
import static spark.SparkBase.port;

public class ProductRestAPI {

    private Http http;
    private String personURL = "http://localhost:4567/";
    private Gson gson;
    private Morphia morphia;
    private Datastore datastore;

    public ProductRestAPI() {
        this.gson = new Gson();
        this.http = new Http();
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

        before("/products", (spark.Request request, Response response) -> {
            String authorization = request.headers("Authorization");
            System.out.println("AUTH: " + authorization);
            if ( request.requestMethod().equals("GET")) {
                ApiResponse apiResponse = http
                        .get(http.endpointForTokens() + "/" + authorization);
                JsonObject json = new JsonParser()
                        .parse(apiResponse.getJson())
                        .getAsJsonObject();
                if (json.get("valid").toString().equals("false")) {
                    halt(401, gson.toJson(Error.withCause("missing or invalid token")));
                }
            }
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
