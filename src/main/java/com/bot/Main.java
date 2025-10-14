package com.bot;

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.net.InetSocketAddress;

@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            AttendanceBot bot = new AttendanceBot();
            botsApi.registerBot(bot);
            log.info("Bot đang chạy với MongoDB...");

            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", exchange -> {
                String response = "Bot is running!";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            });
            server.start();
            log.info("🌐 Dummy server started on port 8080");

            // Thêm shutdown hook để đóng MongoDB connection
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("\nĐang tắt bot...");
                MongoDBService.getInstance().close();
            }));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}