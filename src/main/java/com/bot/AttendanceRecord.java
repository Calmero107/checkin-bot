package com.bot;

import org.bson.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class AttendanceRecord {
    private Long userId;
    private String username;
    private LocalDate date;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    private Double totalHours;

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
        doc.append("date", Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        doc.append("checkinTime", checkinTime != null ?
                Date.from(checkinTime.atZone(ZoneId.systemDefault()).toInstant()) : null);
        doc.append("checkoutTime", checkoutTime != null ?
                Date.from(checkoutTime.atZone(ZoneId.systemDefault()).toInstant()) : null);
        doc.append("totalHours", totalHours);
        return doc;
    }

    // Create from MongoDB Document
    public static AttendanceRecord fromDocument(Document doc) {
        AttendanceRecord record = new AttendanceRecord();
        record.userId = doc.getLong("userId");
        record.username = doc.getString("username");

        Date dateValue = doc.getDate("date");
        record.date = dateValue.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        Date checkinValue = doc.getDate("checkinTime");
        if (checkinValue != null) {
            record.checkinTime = checkinValue.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        Date checkoutValue = doc.getDate("checkoutTime");
        if (checkoutValue != null) {
            record.checkoutTime = checkoutValue.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        record.totalHours = doc.getDouble("totalHours");
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
}