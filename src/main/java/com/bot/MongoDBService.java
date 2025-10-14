package com.bot;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.github.cdimascio.dotenv.Dotenv;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.bot.AttendanceRecord.UTC_ZONE;

public class MongoDBService {
    private static MongoDBService instance;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> attendanceCollection;

    private MongoDBService() {
        try {
//            Dotenv dotenv = Dotenv.load();
            String user = System.getenv("MONGO_USER");
            String pass = System.getenv("MONGO_PASS");
            String cluster = System.getenv("MONGO_CLUSTER");
            String dbName = System.getenv("MONGO_DB");
            String collectionName = System.getenv("MONGO_COLLECTION");

            String connectionString = String.format(
                    "mongodb+srv://%s:%s@%s/?retryWrites=true&w=majority&appName=Cluster0",
                    user, pass, cluster
            );

            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(dbName);
            attendanceCollection = database.getCollection(collectionName);

            System.out.println("Kết nối MongoDB Atlas thành công!");
        } catch (Exception e) {
            System.err.println("Lỗi kết nối MongoDB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static MongoDBService getInstance() {
        if (instance == null) {
            instance = new MongoDBService();
        }
        return instance;
    }

    public void saveOrUpdateRecord(AttendanceRecord record) {
        try {
            Document doc = record.toDocument();

            Bson filter = Filters.and(
                    Filters.eq("userId", record.getUserId()),
                    Filters.eq("date", Date.from(record.getDate().atStartOfDay(UTC_ZONE).toInstant()))
            );

            ReplaceOptions options = new ReplaceOptions().upsert(true);
            attendanceCollection.replaceOne(filter, doc, options);

        } catch (Exception e) {
            System.err.println("Lỗi lưu dữ liệu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public AttendanceRecord getTodayRecord(Long userId, LocalDate date) {
        try {
            Bson filter = Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("date", Date.from(date.atStartOfDay(UTC_ZONE).toInstant()))
            );

            Document doc = attendanceCollection.find(filter).first();

            if (doc != null) {
                return AttendanceRecord.fromDocument(doc);
            }
        } catch (Exception e) {
            System.err.println("Lỗi truy vấn dữ liệu: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<AttendanceRecord> getWeekRecords(Long userId, LocalDate referenceDate) {
        List<AttendanceRecord> records = new ArrayList<>();

        try {
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            LocalDate startOfWeek = referenceDate.with(weekFields.dayOfWeek(), 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            Date startDate = Date.from(startOfWeek.atStartOfDay(UTC_ZONE).toInstant());
            Date endDate = Date.from(endOfWeek.plusDays(1).atStartOfDay(UTC_ZONE).toInstant());

            Bson filter = Filters.and(
                    Filters.eq("userId", userId),
                    Filters.gte("date", startDate),
                    Filters.lt("date", endDate)
            );

            FindIterable<Document> docs = attendanceCollection.find(filter)
                    .sort(new Document("date", 1));

            for (Document doc : docs) {
                records.add(AttendanceRecord.fromDocument(doc));
            }
        } catch (Exception e) {
            System.err.println("Lỗi truy vấn dữ liệu tuần: " + e.getMessage());
            e.printStackTrace();
        }

        return records;
    }

    public List<AttendanceRecord> getAllRecords(Long userId) {
        List<AttendanceRecord> records = new ArrayList<>();

        try {
            Bson filter = Filters.eq("userId", userId);
            FindIterable<Document> docs = attendanceCollection.find(filter)
                    .sort(new Document("date", -1));

            for (Document doc : docs) {
                records.add(AttendanceRecord.fromDocument(doc));
            }
        } catch (Exception e) {
            System.err.println("Lỗi truy vấn tất cả dữ liệu: " + e.getMessage());
            e.printStackTrace();
        }

        return records;
    }

    // Lấy tất cả records của tất cả user trong một ngày cụ thể
    public List<AttendanceRecord> getAllRecordsForDate(LocalDate date) {
        List<AttendanceRecord> records = new ArrayList<>();

        try {
            Date targetDate = Date.from(date.atStartOfDay(UTC_ZONE).toInstant());

            Bson filter = Filters.eq("date", targetDate);
            FindIterable<Document> docs = attendanceCollection.find(filter)
                    .sort(new Document("username", 1));

            for (Document doc : docs) {
                records.add(AttendanceRecord.fromDocument(doc));
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi truy vấn tất cả dữ liệu ngày: " + e.getMessage());
            e.printStackTrace();
        }

        return records;
    }

    // Lấy tất cả records của tất cả user trong tuần
    public List<AttendanceRecord> getAllRecordsForWeek(LocalDate referenceDate) {
        List<AttendanceRecord> records = new ArrayList<>();

        try {
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            LocalDate startOfWeek = referenceDate.with(weekFields.dayOfWeek(), 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            Date startDate = Date.from(startOfWeek.atStartOfDay(UTC_ZONE).toInstant());
            Date endDate = Date.from(endOfWeek.plusDays(1).atStartOfDay(UTC_ZONE).toInstant());

            Bson filter = Filters.and(
                    Filters.gte("date", startDate),
                    Filters.lt("date", endDate)
            );

            FindIterable<Document> docs = attendanceCollection.find(filter)
                    .sort(new Document("date", 1));

            for (Document doc : docs) {
                records.add(AttendanceRecord.fromDocument(doc));
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi truy vấn tất cả dữ liệu tuần: " + e.getMessage());
            e.printStackTrace();
        }

        return records;
    }

    // Lấy tất cả records của tất cả user trong tháng
    public List<AttendanceRecord> getAllRecordsForMonth(LocalDate referenceDate) {
        List<AttendanceRecord> records = new ArrayList<>();

        try {
            LocalDate startOfMonth = referenceDate.withDayOfMonth(1);
            LocalDate endOfMonth = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());

            Date startDate = Date.from(startOfMonth.atStartOfDay(UTC_ZONE).toInstant());
            Date endDate = Date.from(endOfMonth.plusDays(1).atStartOfDay(UTC_ZONE).toInstant());

            Bson filter = Filters.and(
                    Filters.gte("date", startDate),
                    Filters.lt("date", endDate)
            );

            FindIterable<Document> docs = attendanceCollection.find(filter)
                    .sort(new Document("date", 1));

            for (Document doc : docs) {
                records.add(AttendanceRecord.fromDocument(doc));
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi truy vấn tất cả dữ liệu tháng: " + e.getMessage());
            e.printStackTrace();
        }

        return records;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("Đã đóng kết nối MongoDB");
        }
    }
}
