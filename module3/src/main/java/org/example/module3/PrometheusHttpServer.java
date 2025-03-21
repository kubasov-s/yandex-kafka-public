package org.example.module3;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.example.module3.model.MetricEvent;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PrometheusHttpServer {
    private Server server;
    private QueuedThreadPool threadPool;
    private URL url;
    private final ConcurrentHashMap<String, String> metrics = new ConcurrentHashMap<>();

    public void start(URL url) throws Exception {
        if (server != null) {
            throw new RuntimeException("server is already started");
        }
        this.url = url;
        threadPool = new QueuedThreadPool();
        threadPool.setName("PrometheusHttpServer");
        server = new Server(threadPool);

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(url.getPort());
        server.addConnector(connector);

        server.setHandler(new AbstractHandler()
        {
            @Override
            public void handle(String target, Request jettyRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                if (request.getMethod() != null && request.getMethod().equalsIgnoreCase("GET")
                        && target != null && target.equals(url.getPath())) {
                    response.setContentType("text/plain");
                    response.getWriter().write(getResponseContent());
                    jettyRequest.setHandled(true);
                } else {
                    response.setStatus(404);
                    jettyRequest.setHandled(true);
                }
            }
        });

        server.start();
    }

    private String getResponseContent() {
        StringBuilder sb = new StringBuilder("# Base URL: " + url.toExternalForm() + "\n");
        metrics.values().forEach(sb::append);
        return sb.toString();
    }

    public void stop() throws Exception {
        if (server == null) {
            return;
        }
        server.stop();
        threadPool.stop();
        server = null;
        threadPool = null;
        url = null;
    }

    public void add(MetricEvent metric) {
        if (metric == null || metric.getName() == null || metric.getName().isBlank()) {
            return;
        }
        String prometheusData = String.format(
                "# HELP %s %s\n# TYPE %s %s\n%s %f\n",
                metric.getName(), metric.getDescription(), metric.getName(), metric.getType(), metric.getName(), metric.getValue()
        );
        metrics.put(metric.getName(), prometheusData);
    }
}
