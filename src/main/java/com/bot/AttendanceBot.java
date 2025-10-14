package com.bot;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

public class AttendanceBot extends TelegramLongPollingBot {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String BOT_TOKEN;
    private static final String BOT_USERNAME;

    private final MongoDBService mongoService;
    private final Map<Long, String> waitingForLocation = new HashMap<>(); // Lưu trạng thái chờ location

    static {
        Dotenv dotenv = Dotenv.load();
        BOT_TOKEN = dotenv.get("BOT_TOKEN");
        BOT_USERNAME = dotenv.get("BOT_USERNAME");
    }

    public AttendanceBot() {
        this.mongoService = MongoDBService.getInstance();
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasMessage() && update.getMessage().hasLocation()) {
            handleLocationMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private static final Set<Long> ADMIN_IDS = new HashSet<>(Arrays.asList(
            7909720025L, 6736326571L
    ));

    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();

        if (username == null || username.isEmpty()) {
            username = "user_" + userId;
        }

        switch (messageText) {
            case "/start":
                sendWelcomeMessage(chatId);
                break;
            case "/checkin":
                requestLocationForCheckin(chatId, userId, username);
                break;
            case "/checkout":
                requestLocationForCheckout(chatId, userId, username);
                break;
            case "/today":
                handleTodayStats(chatId, userId);
                break;
            case "/week":
                handleWeekStats(chatId, userId);
                break;
            case "/alltoday":
                handleAllTodayStats(chatId, userId);
                break;
            case "/allweek":
                handleAllWeekStats(chatId, userId);
                break;
            case "/allmonth":
                handleAllMonthStats(chatId, userId);
                break;
            default:
                sendMessage(chatId, "❌ Lệnh không hợp lệ. Sử dụng /start để xem các chức năng.");
        }
    }

    private void handleLocationMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();
        Location location = update.getMessage().getLocation();

        if (username == null || username.isEmpty()) {
            username = "user_" + userId;
        }

        String action = waitingForLocation.getOrDefault(userId, "");

        if ("checkin".equals(action)) {
            handleCheckinWithLocation(chatId, userId, username, location);
            waitingForLocation.remove(userId);
        } else if ("checkout".equals(action)) {
            handleCheckoutWithLocation(chatId, userId, username, location);
            waitingForLocation.remove(userId);
        }
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String username = update.getCallbackQuery().getFrom().getUserName();

        if (username == null || username.isEmpty()) {
            username = "user_" + userId;
        }

        switch (callbackData) {
            case "checkin":
                requestLocationForCheckin(chatId, userId, username);
                break;
            case "checkout":
                requestLocationForCheckout(chatId, userId, username);
                break;
        }

        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
                    new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(update.getCallbackQuery().getId());
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendWelcomeMessage(long chatId) {
        String welcomeText = "👋 Chào mừng bạn đến với Bot Chấm Công!\n\n" +
                "📋 Các chức năng cá nhân:\n" +
                "📍 Checkin - Bắt đầu làm việc\n" +
                "🔓 Checkout - Kết thúc ngày làm việc\n" +
                "📊 /today - Xem thống kê hôm nay\n" +
                "📈 /week - Xem thống kê tuần này\n\n";

        if (chatId > 0 && ADMIN_IDS.contains(chatId)) {
            welcomeText += "👨‍💼 Các lệnh Admin:\n" +
                    "📊 /alltoday - Thống kê tất cả nhân viên hôm nay\n" +
                    "📈 /allweek - Thống kê tất cả nhân viên tuần này\n" +
                    "📅 /allmonth - Thống kê tất cả nhân viên tháng này\n\n";
        }

        welcomeText += "💾 Dữ liệu được lưu trên MongoDB (kèm vị trí GPS)\n\n" +
                "Chọn chức năng bên dưới:";

        sendMessageWithKeyboard(chatId, welcomeText);
    }

    private InlineKeyboardMarkup createInlineKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton checkinBtn = new InlineKeyboardButton();
        checkinBtn.setText("📍 Checkin");
        checkinBtn.setCallbackData("checkin");
        row1.add(checkinBtn);

        InlineKeyboardButton checkoutBtn = new InlineKeyboardButton();
        checkoutBtn.setText("🔓 Checkout");
        checkoutBtn.setCallbackData("checkout");
        row1.add(checkoutBtn);

        keyboard.add(row1);
        markup.setKeyboard(keyboard);

        return markup;
    }

    private void requestLocationForCheckin(long chatId, Long userId, String username) {
        waitingForLocation.put(userId, "checkin");

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("📍 Vui lòng chia sẻ vị trí hiện tại của bạn để checkin");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setOneTimeKeyboard(true);
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton();
        button.setText("📍 Gửi vị trí hiện tại");
        button.setRequestLocation(true);
        row.add(button);
        rows.add(row);

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void requestLocationForCheckout(long chatId, Long userId, String username) {
        waitingForLocation.put(userId, "checkout");

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("📍 Vui lòng chia sẻ vị trí hiện tại của bạn để checkout");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setOneTimeKeyboard(true);
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton();
        button.setText("📍 Gửi vị trí hiện tại");
        button.setRequestLocation(true);
        row.add(button);
        rows.add(row);

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleCheckinWithLocation(long chatId, Long userId, String username, Location location) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        AttendanceRecord existingRecord = mongoService.getTodayRecord(userId, today);

        if (existingRecord != null && existingRecord.getCheckinTime() != null) {
            sendMessage(chatId, "⚠️ Bạn đã checkin hôm nay lúc " +
                    existingRecord.getCheckinTime().format(TIME_FORMATTER) + "\n" +
                    "📍 Vị trí: " + existingRecord.getCheckinAddress());
            return;
        }

        AttendanceRecord record = new AttendanceRecord(userId, username, today, now, null, 0.0);
        record.setCheckinLatitude(location.getLatitude());
        record.setCheckinLongitude(location.getLongitude());
        record.setCheckinAddress(getLocation(location.getLatitude(), location.getLongitude()));

        mongoService.saveOrUpdateRecord(record);

        String responseText = "✅ Checkin thành công lúc " + now.format(TIME_FORMATTER) + "\n" +
                "📍 Vị trí: " + record.getCheckinAddress() + "\n" +
                "🔓 Nhấn \"Checkout\" khi bạn kết thúc ngày làm việc";

        sendMessageWithKeyboard(chatId, responseText);
    }

    private void handleCheckoutWithLocation(long chatId, Long userId, String username, Location location) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        AttendanceRecord record = mongoService.getTodayRecord(userId, today);

        if (record == null || record.getCheckinTime() == null) {
            sendMessage(chatId, "⚠️ Bạn chưa checkin hôm nay. Vui lòng checkin trước!");
            return;
        }

        if (record.getCheckoutTime() != null) {
            sendMessage(chatId, "⚠️ Bạn đã checkout rồi lúc " +
                    record.getCheckoutTime().format(TIME_FORMATTER) + "\n" +
                    "📍 Vị trí: " + record.getCheckoutAddress());
            return;
        }

        record.setCheckoutTime(now);
        record.setCheckoutLatitude(location.getLatitude());
        record.setCheckoutLongitude(location.getLongitude());
        record.setCheckoutAddress(getLocation(location.getLatitude(), location.getLongitude()));

        Duration duration = Duration.between(record.getCheckinTime(), record.getCheckoutTime());
        record.setTotalHours(duration.toMinutes() / 60.0);

        mongoService.saveOrUpdateRecord(record);

        String responseText = "🔓 Checkout thành công lúc " + now.format(TIME_FORMATTER) + "\n" +
                "📍 Vị trí: " + record.getCheckoutAddress() + "\n" +
                "⏱ Tổng thời gian làm việc hôm nay: " +
                String.format("%.2f giờ", record.getTotalHours());

        sendMessage(chatId, responseText);
    }

    private String getLocation(Double latitude, Double longitude) {
        return GeoService.getAddressFromCoordinates(latitude, longitude);
//        return String.format("%.4f, %.4f", latitude, longitude);
    }

    private void handleTodayStats(long chatId, Long userId) {
        LocalDate today = LocalDate.now();
        AttendanceRecord record = mongoService.getTodayRecord(userId, today);

        if (record == null || record.getCheckinTime() == null) {
            sendMessage(chatId, "📊 Chưa có dữ liệu chấm công hôm nay.");
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("📊 THỐNG KÊ HÔM NAY (").append(today.format(DATE_FORMATTER)).append(")\n\n");
        stats.append("📍 Checkin: ").append(record.getCheckinTime().format(TIME_FORMATTER)).append("\n");
        stats.append("   Vị trí: ").append(record.getCheckinAddress()).append("\n\n");

        if (record.getCheckoutTime() != null) {
            stats.append("🔓 Checkout: ").append(record.getCheckoutTime().format(TIME_FORMATTER)).append("\n");
            stats.append("   Vị trí: ").append(record.getCheckoutAddress()).append("\n\n");
            stats.append("⏱ Tổng giờ: ").append(String.format("%.2f giờ", record.getTotalHours()));
        } else {
            stats.append("🔓 Checkout: Chưa checkout\n");
            Duration currentDuration = Duration.between(record.getCheckinTime(), LocalDateTime.now());
            double currentHours = currentDuration.toMinutes() / 60.0;
            stats.append("⏱ Đã làm: ").append(String.format("%.2f giờ", currentHours)).append(" (đang làm việc)");
        }

        sendMessage(chatId, stats.toString());
    }

    private void handleWeekStats(long chatId, Long userId) {
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int currentWeek = today.get(weekFields.weekOfWeekBasedYear());

        List<AttendanceRecord> weekRecords = mongoService.getWeekRecords(userId, today);

        if (weekRecords.isEmpty()) {
            sendMessage(chatId, "📈 Chưa có dữ liệu chấm công tuần này.");
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("📈 THỐNG KÊ TUẦN NÀY (Tuần ").append(currentWeek).append(")\n\n");

        double totalWeekHours = 0.0;
        int workDays = 0;

        for (AttendanceRecord record : weekRecords) {
            stats.append("📅 ").append(record.getDate().format(DATE_FORMATTER)).append("\n");
            stats.append("   📍 In: ").append(record.getCheckinTime().format(TIME_FORMATTER));
            stats.append(" (").append(record.getCheckinAddress()).append(")");

            if (record.getCheckoutTime() != null) {
                stats.append("\n   🔓 Out: ").append(record.getCheckoutTime().format(TIME_FORMATTER));
                stats.append(" (").append(record.getCheckoutAddress()).append(")");
                stats.append("\n   ⏱ ").append(String.format("%.2fh", record.getTotalHours()));
                totalWeekHours += record.getTotalHours();
                workDays++;
            } else {
                stats.append("\n   🔓 Chưa checkout");
            }
            stats.append("\n\n");
        }

        stats.append("📊 Tổng kết:\n");
        stats.append("   • Số ngày làm: ").append(workDays).append("\n");
        stats.append("   • Tổng giờ: ").append(String.format("%.2f giờ", totalWeekHours)).append("\n");
        if (workDays > 0) {
            stats.append("   • TB/ngày: ").append(String.format("%.2f giờ", totalWeekHours / workDays));
        }

        sendMessage(chatId, stats.toString());
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageWithKeyboard(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(createInlineKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean isAdmin(Long userId) {
        return ADMIN_IDS.contains(userId);
    }

    private void handleAllTodayStats(long chatId, Long userId) {
        if (!isAdmin(userId)) {
            sendMessage(chatId, "❌ Bạn không có quyền sử dụng lệnh này!");
            return;
        }

        LocalDate today = LocalDate.now();
        List<AttendanceRecord> allRecords = mongoService.getAllRecordsForDate(today);

        if (allRecords.isEmpty()) {
            sendMessage(chatId, "📊 Chưa có ai checkin hôm nay.");
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("📊 THỐNG KÊ TẤT CẢ NHÂN VIÊN HÔM NAY\n");
        stats.append("📅 ").append(today.format(DATE_FORMATTER)).append("\n");
        stats.append("═══════════════════════════════════\n\n");

        double totalHours = 0.0;
        int checkedIn = 0;
        int checkedOut = 0;

        for (AttendanceRecord record : allRecords) {
            stats.append("👤 ").append(record.getUsername()).append("\n");
            stats.append("   📍 In: ").append(record.getCheckinTime().format(TIME_FORMATTER));
            stats.append(" (").append(record.getCheckinAddress()).append(")");

            if (record.getCheckoutTime() != null) {
                stats.append("\n   🔓 Out: ").append(record.getCheckoutTime().format(TIME_FORMATTER));
                stats.append(" (").append(record.getCheckoutAddress()).append(")");
                stats.append("\n   ⏱ ").append(String.format("%.2fh", record.getTotalHours()));
                totalHours += record.getTotalHours();
                checkedOut++;
            } else {
                Duration currentDuration = Duration.between(record.getCheckinTime(), LocalDateTime.now());
                double currentHours = currentDuration.toMinutes() / 60.0;
                stats.append("\n   ⏱ ").append(String.format("%.2fh", currentHours)).append(" (đang làm)");
            }
            stats.append("\n\n");
            checkedIn++;
        }

        stats.append("═══════════════════════════════════\n");
        stats.append("📈 TỔNG KẾT:\n");
        stats.append("   • Đã checkin: ").append(checkedIn).append(" người\n");
        stats.append("   • Đã checkout: ").append(checkedOut).append(" người\n");
        stats.append("   • Tổng giờ: ").append(String.format("%.2fh", totalHours));

        sendMessage(chatId, stats.toString());
    }

    private void handleAllWeekStats(long chatId, Long userId) {
        if (!isAdmin(userId)) {
            sendMessage(chatId, "❌ Bạn không có quyền sử dụng lệnh này!");
            return;
        }

        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int currentWeek = today.get(weekFields.weekOfWeekBasedYear());

        List<AttendanceRecord> allRecords = mongoService.getAllRecordsForWeek(today);

        if (allRecords.isEmpty()) {
            sendMessage(chatId, "📈 Chưa có dữ liệu tuần này.");
            return;
        }

        Map<String, List<AttendanceRecord>> userRecords = new HashMap<>();
        for (AttendanceRecord record : allRecords) {
            userRecords.computeIfAbsent(record.getUsername(), k -> new ArrayList<>()).add(record);
        }

        StringBuilder stats = new StringBuilder();
        stats.append("📈 THỐNG KÊ TẤT CẢ NHÂN VIÊN TUẦN NÀY\n");
        stats.append("📅 Tuần ").append(currentWeek).append("\n");
        stats.append("═══════════════════════════════════\n\n");

        double grandTotalHours = 0.0;

        for (Map.Entry<String, List<AttendanceRecord>> entry : userRecords.entrySet()) {
            String username = entry.getKey();
            List<AttendanceRecord> records = entry.getValue();

            double userTotalHours = 0.0;
            int workDays = 0;

            stats.append("👤 ").append(username).append("\n");

            for (AttendanceRecord record : records) {
                if (record.getCheckoutTime() != null) {
                    userTotalHours += record.getTotalHours();
                    workDays++;
                }
            }

            stats.append("   • Số ngày: ").append(workDays).append("\n");
            stats.append("   • Tổng giờ: ").append(String.format("%.2fh", userTotalHours)).append("\n");
            if (workDays > 0) {
                stats.append("   • TB/ngày: ").append(String.format("%.2fh", userTotalHours / workDays)).append("\n");
            }
            stats.append("\n");

            grandTotalHours += userTotalHours;
        }

        stats.append("═══════════════════════════════════\n");
        stats.append("📊 TỔNG KẾT TOÀN BỘ:\n");
        stats.append("   • Tổng nhân viên: ").append(userRecords.size()).append("\n");
        stats.append("   • Tổng giờ làm: ").append(String.format("%.2fh", grandTotalHours));

        sendMessage(chatId, stats.toString());
    }

    private void handleAllMonthStats(long chatId, Long userId) {
        if (!isAdmin(userId)) {
            sendMessage(chatId, "❌ Bạn không có quyền sử dụng lệnh này!");
            return;
        }

        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();

        List<AttendanceRecord> allRecords = mongoService.getAllRecordsForMonth(today);

        if (allRecords.isEmpty()) {
            sendMessage(chatId, "📅 Chưa có dữ liệu tháng này.");
            return;
        }

        Map<String, List<AttendanceRecord>> userRecords = new HashMap<>();
        for (AttendanceRecord record : allRecords) {
            userRecords.computeIfAbsent(record.getUsername(), k -> new ArrayList<>()).add(record);
        }

        StringBuilder stats = new StringBuilder();
        stats.append("📅 THỐNG KÊ TẤT CẢ NHÂN VIÊN THÁNG NÀY\n");
        stats.append("📆 Tháng ").append(currentMonth).append("/").append(currentYear).append("\n");
        stats.append("═══════════════════════════════════\n\n");

        double grandTotalHours = 0.0;

        List<Map.Entry<String, List<AttendanceRecord>>> sortedUsers = new ArrayList<>(userRecords.entrySet());
        sortedUsers.sort((a, b) -> {
            double totalA = a.getValue().stream()
                    .filter(r -> r.getCheckoutTime() != null)
                    .mapToDouble(AttendanceRecord::getTotalHours)
                    .sum();
            double totalB = b.getValue().stream()
                    .filter(r -> r.getCheckoutTime() != null)
                    .mapToDouble(AttendanceRecord::getTotalHours)
                    .sum();
            return Double.compare(totalB, totalA);
        });

        for (Map.Entry<String, List<AttendanceRecord>> entry : sortedUsers) {
            String username = entry.getKey();
            List<AttendanceRecord> records = entry.getValue();

            double userTotalHours = 0.0;
            int workDays = 0;

            stats.append("👤 ").append(username).append("\n");

            for (AttendanceRecord record : records) {
                if (record.getCheckoutTime() != null) {
                    userTotalHours += record.getTotalHours();
                    workDays++;
                }
            }

            stats.append("   • Số ngày: ").append(workDays).append("\n");
            stats.append("   • Tổng giờ: ").append(String.format("%.2fh", userTotalHours)).append("\n");
            if (workDays > 0) {
                stats.append("   • TB/ngày: ").append(String.format("%.2fh", userTotalHours / workDays)).append("\n");
            }
            stats.append("\n");

            grandTotalHours += userTotalHours;
        }

        stats.append("═══════════════════════════════════\n");
        stats.append("📊 TỔNG KẾT TOÀN BỘ:\n");
        stats.append("   • Tổng nhân viên: ").append(userRecords.size()).append("\n");
        stats.append("   • Tổng giờ làm: ").append(String.format("%.2fh", grandTotalHours));
        if (!userRecords.isEmpty()) {
            stats.append("\n   • TB/người: ").append(String.format("%.2fh", grandTotalHours / userRecords.size()));
        }

        sendMessage(chatId, stats.toString());
    }
}
