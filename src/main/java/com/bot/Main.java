package com.bot;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            AttendanceBot bot = new AttendanceBot();
            botsApi.registerBot(bot);
            log.info("Bot đang chạy với MongoDB...");

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