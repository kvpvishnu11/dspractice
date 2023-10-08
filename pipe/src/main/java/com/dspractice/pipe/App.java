package com.dspractice.pipe;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class App {

    public static void main(String[] args) {
        AtomicInteger iterations = new AtomicInteger(10); // Set the number of iterations here
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Schedule the code to run every 20 seconds
        scheduler.scheduleAtFixedRate(() -> {
            insertVendorRecord();
            int remainingIterations = iterations.decrementAndGet();
            if (remainingIterations <= 0) {
                scheduler.shutdown(); // Stop the scheduler when all iterations are done
            }
        }, 0, 20, TimeUnit.SECONDS);
    }

    private static void insertVendorRecord() {
        // Database connection parameters
        String dbUrl = "jdbc:postgresql://localhost:5432/person"; // Replace with your database URL
        String dbUsername = "postgres"; // Replace with your database username
        String dbPassword = "150030441@klU"; // Replace with your database password

        // Data to insert
        String name = "New Vendor";
        String contactName = "John Doe";
        String email = "john@example.com";
        String phoneNumber = "123-456-7890";
        String address = "123 Main St";

        // SQL query for insertion
        String insertSql = "INSERT INTO public.vendor (name, contact_name, email, phone_number, address) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement preparedStatement = connection.prepareStatement(insertSql)) {

            // Set the parameter values
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, contactName);
            preparedStatement.setString(3, email);
            preparedStatement.setString(4, phoneNumber);
            preparedStatement.setString(5, address);

            // Execute the SQL statement to insert the record
            int rowsAffected = preparedStatement.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("Record inserted successfully!");
            } else {
                System.out.println("Failed to insert record.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

