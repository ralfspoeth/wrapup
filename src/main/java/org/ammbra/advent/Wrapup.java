package org.ammbra.advent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ralfspoeth.json.Element;
import io.github.ralfspoeth.json.JsonObject;
import io.github.ralfspoeth.json.io.JsonReader;
import io.github.ralfspoeth.json.io.JsonWriter;
import org.ammbra.advent.request.Choice;
import org.ammbra.advent.request.RequestConverter;
import org.ammbra.advent.request.RequestData;
import org.ammbra.advent.surprise.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.concurrent.Executors;


public class Wrapup {
    
    public void main() throws IOException {
        var server = HttpServer.create(
                new InetSocketAddress("", 8081), 0);
        var address = server.getAddress();
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("http://%s:%d%n", address.getHostString(), address.getPort());
    }


    private void handle(HttpExchange exchange) throws IOException {
        int statusCode = 200;

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            statusCode = 400;
        }

        // Get the request body input stream
        try (var rdr = new JsonReader(new InputStreamReader(exchange.getRequestBody()));
             var wrtr = JsonWriter.createDefaultWriter(new OutputStreamWriter(exchange.getResponseBody()))
        ) {
            var req = (JsonObject) rdr.readElement();
            var data = RequestConverter.convert(req);

            Postcard postcard = new Postcard(data.sender(), data.receiver(), data.celebration());
            Intention intention = extractIntention(data);
            var json = process(postcard, intention, data.choice());

            exchange.sendResponseHeaders(statusCode, 0);

            wrtr.write(json);
        }
    }

    Intention extractIntention(RequestData data) {
        return switch (data.choice()) {
            case NONE -> new Coupon(0.0, null, Currency.getInstance("USD"));
            case COUPON -> {
                LocalDate localDate = LocalDateTime.now().plusYears(1).toLocalDate();
                yield new Coupon(data.itemPrice(), localDate, Currency.getInstance("USD"));
            }
            case EXPERIENCE -> new Experience(data.itemPrice(), Currency.getInstance("EUR"));
            case PRESENT -> new Present(data.itemPrice(), data.boxPrice(), Currency.getInstance("RON"));
        };
    }

    Element process(Postcard postcard, Intention intention, Choice choice) {
        Gift gift = new Gift(postcard, intention);

        return switch (gift) {
            case Gift(Postcard _, Postcard _) -> {
                String message = "You cannot send two postcards!";
                throw new UnsupportedOperationException(message);
            }
            case Gift(Postcard p, Coupon c)
                    when (c.price() == 0.0) -> p.toJsonObject();
            case Gift(_, Coupon _), Gift(_, Experience _),
                    Gift(_, Present _) -> {
                String option = choice.name().toLowerCase();
                yield gift.merge(option);
            }
        };
    }
}