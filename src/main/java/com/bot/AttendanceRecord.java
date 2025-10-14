package com.bot;

import org.bson.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class AttendanceRecord {
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private Long userId;
    private String username;
    private LocalDate date;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    private Double totalHours;
    private Double checkinLatitude;
    private Double checkinLongitude;
    private String checkinAddress;
    private Double checkoutLatitude;
    private Double checkoutLongitude;
    private String checkoutAddress;

    public AttendanceRecord() {}

    public AttendanceRecord(Long userId, String username, LocalDate date,
                            LocalDateTime checkinTime, LocalDateTime checkoutTime, Double totalHours) {
        this.userId = userId;
        this.username = username;
        this.date = date;
        this.checkinTime = checkinTime;
        this.checkoutTime = checkoutTime;
        this.totalHours = totalHours;
    }

    // Convert to MongoDB Document
    public Document toDocument() {
        Document doc = new Document();
        doc.append("userId", userId);
        doc.append("username", username);
        doc.append("date", Date.from(date.atStartOfDay(VN_ZONE).toInstant()));
        doc.append("checkinTime", checkinTime != null ?
                Date.from(checkinTime.atZone(VN_ZONE).toInstant()) : null);
        doc.append("checkoutTime", checkoutTime != null ?
                Date.from(checkoutTime.atZone(VN_ZONE).toInstant()) : null);
        doc.append("totalHours", totalHours);

        // Thêm location info
        doc.append("checkinLatitude", checkinLatitude);
        doc.append("checkinLongitude", checkinLongitude);
        doc.append("checkinAddress", checkinAddress);
        doc.append("checkoutLatitude", checkoutLatitude);
        doc.append("checkoutLongitude", checkoutLongitude);
        doc.append("checkoutAddress", checkoutAddress);

        return doc;
    }

    // Create from MongoDB Document
    public static AttendanceRecord fromDocument(Document doc) {
        AttendanceRecord record = new AttendanceRecord();
        record.userId = doc.getLong("userId");
        record.username = doc.getString("username");

        Date dateValue = doc.getDate("date");
        record.date = dateValue.toInstant().atZone(VN_ZONE).toLocalDate();

        Date checkinValue = doc.getDate("checkinTime");
        if (checkinValue != null) {
            record.checkinTime = checkinValue.toInstant().atZone(VN_ZONE).toLocalDateTime();
        }

        Date checkoutValue = doc.getDate("checkoutTime");
        if (checkoutValue != null) {
            record.checkoutTime = checkoutValue.toInstant().atZone(VN_ZONE).toLocalDateTime();
        }

        record.totalHours = doc.getDouble("totalHours");

        // Lấy location info
        record.checkinLatitude = doc.getDouble("checkinLatitude");
        record.checkinLongitude = doc.getDouble("checkinLongitude");
        record.checkinAddress = doc.getString("checkinAddress");
        record.checkoutLatitude = doc.getDouble("checkoutLatitude");
        record.checkoutLongitude = doc.getDouble("checkoutLongitude");
        record.checkoutAddress = doc.getString("checkoutAddress");

        return record;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalDateTime getCheckinTime() { return checkinTime; }
    public void setCheckinTime(LocalDateTime checkinTime) { this.checkinTime = checkinTime; }

    public LocalDateTime getCheckoutTime() { return checkoutTime; }
    public void setCheckoutTime(LocalDateTime checkoutTime) { this.checkoutTime = checkoutTime; }

    public Double getTotalHours() { return totalHours; }
    public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }

    public Double getCheckinLatitude() { return checkinLatitude; }
    public void setCheckinLatitude(Double checkinLatitude) { this.checkinLatitude = checkinLatitude; }

    public Double getCheckinLongitude() { return checkinLongitude; }
    public void setCheckinLongitude(Double checkinLongitude) { this.checkinLongitude = checkinLongitude; }

    public String getCheckinAddress() { return checkinAddress; }
    public void setCheckinAddress(String checkinAddress) { this.checkinAddress = checkinAddress; }

    public Double getCheckoutLatitude() { return checkoutLatitude; }
    public void setCheckoutLatitude(Double checkoutLatitude) { this.checkoutLatitude = checkoutLatitude; }

    public Double getCheckoutLongitude() { return checkoutLongitude; }
    public void setCheckoutLongitude(Double checkoutLongitude) { this.checkoutLongitude = checkoutLongitude; }

    public String getCheckoutAddress() { return checkoutAddress; }
    public void setCheckoutAddress(String checkoutAddress) { this.checkoutAddress = checkoutAddress; }
}