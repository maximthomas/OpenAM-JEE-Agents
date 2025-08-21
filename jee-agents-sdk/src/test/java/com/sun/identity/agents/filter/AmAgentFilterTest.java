package com.sun.identity.agents.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.forgerock.openam.sdk.com.fasterxml.jackson.core.type.TypeReference;
import org.forgerock.openam.sdk.com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AmAgentFilterTest {

    HttpClient client = HttpClient.newHttpClient();

    @Test
    public void testFilterInitialization() throws Exception {

        System.setProperty("com.sun.identity.agents.config.local.logfile", "");

        Server jetty = new Server(8081); // You can change the port

        // Create a ServletContextHandler to manage servlets and filters
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/"); // Set the context path for your application
        context.addFilter(AmAgentFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        jetty.setHandler(context);

        // Add your custom servlet
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("text/plain");
                resp.getWriter().write("Hello World!");
            }
        }), "/demo/");

        // Start the server
        jetty.start();

        HttpResponse<String> unauthResponse = callDemoServlet("");
        assertThat(unauthResponse.statusCode()).isEqualTo(HttpURLConnection.HTTP_MOVED_TEMP);

        String token = getAuthenticationToken();

        HttpResponse<String> authResponse = callDemoServlet(token);
        assertThat(authResponse.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    }

    protected String getAuthenticationToken() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://openam.example.org:8080/openam/json/authenticate"))
                .header("X-OpenAM-Username", "demo")
                .header("X-OpenAM-Password", "changeit")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        String body = response.body();
        Map<String, String> responseMap = mapper.readValue(body, new TypeReference<>() {
        });
        return responseMap.get("tokenId");
    }

    protected HttpResponse<String> callDemoServlet(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/demo/"))
                .header("Cookie", "iPlanetDirectoryPro="+ token)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}