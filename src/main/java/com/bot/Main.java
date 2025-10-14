package com.bot;

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        final long PING_INTERVAL_MINUTES = 5;
        final String pingUrl = System.getenv().getOrDefault("PING_URL", "http://localhost:8080/");
        final ScheduledExecutorService pingExecutor = Executors.newSingleThreadScheduledExecutor();

        try {
            // ✅ Khởi động Telegram bot
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            AttendanceBot bot = new AttendanceBot();
            botsApi.registerBot(bot);
            log.info("🤖 Bot đang chạy và kết nối với MongoDB...");

            // ✅ Dummy HTTP server
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", exchange -> {
                String response = "✅ Bot is running on Render!";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            });
            server.start();
            log.info("🌐 Dummy server started on port 8080");

            // ✅ Tạo HttpClient cho self-ping
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            Runnable pingTask = () -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(pingUrl))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();

                    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    log.info("🔁 Ping to {} -> status {}", pingUrl, response.statusCode());
                } catch (IOException | InterruptedException e) {
                    log.warn("⚠️ Ping failed to {}: {}", pingUrl, e.toString());
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("❌ Unexpected ping error: ", e);
                }
            };

            // ✅ Lên lịch ping định kỳ (mỗi 5 phút)
            pingExecutor.scheduleAtFixedRate(pingTask, 0, PING_INTERVAL_MINUTES, TimeUnit.MINUTES);
            log.info("⏱️ Scheduled self-ping every {} minutes to {}", PING_INTERVAL_MINUTES, pingUrl);

            // ✅ Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("\n🛑 Đang tắt bot...");
                try {
                    server.stop(0);
                    log.info("Server stopped.");
                } catch (Exception e) {
                    log.warn("Error stopping server: {}", e.toString());
                }

                try {
                    MongoDBService.getInstance().close();
                    log.info("MongoDB connection closed.");
                } catch (Exception e) {
                    log.warn("Error closing MongoDB: {}", e.toString());
                }

                pingExecutor.shutdown();
                try {
                    if (!pingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        pingExecutor.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    pingExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                log.info("✅ Cleanup complete.");
            }));

            // ✅ Giữ process chạy
            Thread.currentThread().join();

        } catch (TelegramApiException e) {
            log.error("TelegramApiException:", e);
        } finally {
            if (!pingExecutor.isShutdown()) pingExecutor.shutdownNow();
        }
    }
}
