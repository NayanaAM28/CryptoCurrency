import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CryptoServer {

    // In-memory data structures
    private static final Map<String, String> users = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> watchlists = new ConcurrentHashMap<>();
    private static final Map<String, String> portfolios = new ConcurrentHashMap<>(); // JSON array string per user
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) throws IOException {
        // Pre-populate demo user
        users.put("admin", "admin123");
        watchlists.put("admin", new HashSet<>(Arrays.asList("bitcoin", "ethereum")));
        portfolios.put("admin", "[{\"coinId\":\"bitcoin\",\"quantity\":1.5,\"buyPrice\":45000.0},{\"coinId\":\"ethereum\",\"quantity\":5.0,\"buyPrice\":2800.0}]");

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/coins/markets", new MarketsHandler());
        server.createContext("/api/coins/detail", new CoinDetailHandler());
        server.createContext("/api/watchlist", new WatchlistHandler());
        server.createContext("/api/portfolio", new PortfolioHandler());

        server.setExecutor(null);
        System.out.println("==================================================");
        System.out.println(" CryptoPulse Pure Java Backend Started!");
        System.out.println(" Listening on port: " + port);
        System.out.println(" Open index.html in your browser to test.");
        System.out.println("==================================================");
        server.start();
    }

    // Helper to enable CORS and handle standard HTTP responses
    private static void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", "application/json");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    // --- 1. User Registration ---
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            String body = readRequestBody(exchange);
            String username = extractJsonVal(body, "username");
            String password = extractJsonVal(body, "password");

            if (username == null || password == null || username.isEmpty()) {
                sendResponse(exchange, 400, "{\"message\":\"Invalid username or password\"}");
                return;
            }

            if (users.containsKey(username)) {
                sendResponse(exchange, 400, "{\"message\":\"User already exists!\"}");
                return;
            }

            users.put(username, password);
            watchlists.put(username, new HashSet<>());
            portfolios.put(username, "[]");
            sendResponse(exchange, 200, "{\"message\":\"Registration successful!\"}");
        }
    }

    // --- 2. User Login ---
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            String body = readRequestBody(exchange);
            String username = extractJsonVal(body, "username");
            String password = extractJsonVal(body, "password");

            if (users.containsKey(username) && users.get(username).equals(password)) {
                sendResponse(exchange, 200, "{\"message\":\"Login successful!\",\"username\":\"" + username + "\"}");
            } else {
                sendResponse(exchange, 401, "{\"message\":\"Invalid credentials!\"}");
            }
        }
    }

    // --- 3. Live Crypto Market Data Proxy ---
    static class MarketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            try {
                String apiUrl = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=20&page=1&sparkline=false";
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();
                HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
                sendResponse(exchange, 200, response.body());
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"message\":\"Failed to fetch market data\"}");
            }
        }
    }

    // --- 4. Coin Details Proxy ---
    static class CoinDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            String coinId = "bitcoin";
            if (query != null && query.contains("id=")) {
                coinId = query.split("id=")[1].split("&")[0];
            }

            try {
                String apiUrl = "https://api.coingecko.com/api/v3/coins/" + coinId + "?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false";
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();
                HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
                sendResponse(exchange, 200, response.body());
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"message\":\"Failed to fetch coin details\"}");
            }
        }
    }

    // --- 5. Watchlist Endpoint ---
    static class WatchlistHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                String username = (query != null && query.contains("user=")) ? query.split("user=")[1].split("&")[0] : "admin";
                Set<String> list = watchlists.getOrDefault(username, new HashSet<>());
                sendResponse(exchange, 200, toJsonArray(list));
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                String username = extractJsonVal(body, "username");
                String coinId = extractJsonVal(body, "coinId");

                Set<String> list = watchlists.computeIfAbsent(username, k -> new HashSet<>());
                if (list.contains(coinId)) {
                    list.remove(coinId);
                } else {
                    list.add(coinId);
                }
                sendResponse(exchange, 200, "{\"message\":\"Watchlist updated\",\"watchlist\":" + toJsonArray(list) + "}");
            }
        }
    }

    // --- 6. Portfolio Endpoint ---
    static class PortfolioHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                String username = (query != null && query.contains("user=")) ? query.split("user=")[1].split("&")[0] : "admin";
                String userPortfolio = portfolios.getOrDefault(username, "[]");
                sendResponse(exchange, 200, userPortfolio);
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                String username = extractJsonVal(body, "username");
                String coinId = extractJsonVal(body, "coinId");
                String quantityStr = extractJsonVal(body, "quantity");
                String buyPriceStr = extractJsonVal(body, "buyPrice");

                String existing = portfolios.getOrDefault(username, "[]");
                String newItem = "{\"coinId\":\"" + coinId + "\",\"quantity\":" + quantityStr + ",\"buyPrice\":" + buyPriceStr + "}";

                String updated;
                if (existing.equals("[]")) {
                    updated = "[" + newItem + "]";
                } else {
                    updated = existing.substring(0, existing.length() - 1) + "," + newItem + "]";
                }

                portfolios.put(username, updated);
                sendResponse(exchange, 200, "{\"message\":\"Asset added to portfolio successfully!\"}");
            }
        }
    }

    // Helper Utilities
    private static String extractJsonVal(String json, String key) {
        if (json == null || !json.contains("\"" + key + "\"")) return null;
        try {
            int keyIndex = json.indexOf("\"" + key + "\"");
            int colonIndex = json.indexOf(":", keyIndex);
            int start = json.indexOf("\"", colonIndex);
            if (start == -1 || json.substring(colonIndex, start).trim().length() > 1) { // Number value
                int endComma = json.indexOf(",", colonIndex);
                int endBrace = json.indexOf("}", colonIndex);
                int end = (endComma != -1 && endComma < endBrace) ? endComma : endBrace;
                return json.substring(colonIndex + 1, end).replaceAll("[^0-9.]", "").trim();
            }
            int end = json.indexOf("\"", start + 1);
            return json.substring(start + 1, end);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJsonArray(Set<String> set) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String s : set) {
            sb.append("\"").append(s).append("\"");
            if (++i < set.size()) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}