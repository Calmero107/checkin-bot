package com.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) throws Exception {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            AttendanceBot bot = new AttendanceBot();
            botsApi.registerBot(bot);
            System.out.println("✅ Bot đang chạy với MongoDB...");

            // Thêm shutdown hook để đóng MongoDB connection
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Đang tắt bot...");
                MongoDBService.getInstance().close();
            }));

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}