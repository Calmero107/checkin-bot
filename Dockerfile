# Dùng JDK nhẹ gọn
FROM eclipse-temurin:21-jdk-alpine

# Tạo thư mục làm việc trong container
WORKDIR /app

# Copy file JAR từ target vào container
COPY target/bot-checkin-full.jar app.jar

# Copy file .env (nếu có)
#COPY .env .env

# Mở port (nếu app có web hoặc webhook, còn nếu bot thì có thể bỏ)
EXPOSE 8080

# Lệnh chạy app
ENTRYPOINT ["java", "-jar", "app.jar"]
