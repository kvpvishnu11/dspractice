package com.Youtubepipeline.Youtube;

import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Date;


public class Youtubedata {
    private static String nextPageToken = null; 

    public static void main(String[] args) {
    	
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // My Database related URL and credentials - "youtube" is my db name
        String jdbcUrl = "jdbc:postgresql://localhost:5432/youtube";
        String username = "postgres";
        String password = "150030441@klU";

        try {
            Connection conn = DriverManager.getConnection(jdbcUrl, username, password);

            // Scheduling the code to run every 5 minutes
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    fetchAndInsertYouTubeComments(conn);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 300, TimeUnit.SECONDS);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Video ID fetch from database dynamically from "videos" table in DB
    private static String fetchVideoIdFromDatabase(Connection conn) {
        try {
            String query = "SELECT videoId FROM videos ORDER BY id DESC LIMIT 1";
            PreparedStatement statement = conn.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getString("videoId");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Once we fetch the video ID, use the same ID to fetch the YouTube comments
    private static void fetchAndInsertYouTubeComments(Connection conn) {
        try {
            String videoId = fetchVideoIdFromDatabase(conn);

            if (videoId == null) {
                System.out.println("No videoId found in the videos table.");
                return;
            }

            // Define the API related parameters
            String apiKey = "AIzaSyCwXjwmK-p7eWrher4jnBUHtDVfR54yhq4"; 
            int maxResults = 100;

            // API URL Generation
            String apiUrl = "https://www.googleapis.com/youtube/v3/commentThreads?key=" + apiKey
                    + "&textFormat=plainText&part=snippet&videoId=" + videoId
                    + "&maxResults=" + maxResults;

            if (nextPageToken != null) {
                apiUrl += "&pageToken=" + nextPageToken;
            }

            // Using HTTP client to send the request
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parsing the JSON response
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray comments = jsonResponse.getJSONArray("items");

                // Iterating to get what we need
                for (int i = 0; i < comments.length(); i++) {
                    JSONObject comment = comments.getJSONObject(i);
                    JSONObject snippet = comment.getJSONObject("snippet");
                    String commentId = comment.getString("id");
                    String commentText = snippet.getJSONObject("topLevelComment")
                            .getJSONObject("snippet")
                            .getString("textDisplay");
                    String commentCreatedDateTime = snippet.getJSONObject("topLevelComment")
                            .getJSONObject("snippet")
                            .getString("publishedAt");

                    // Change the format of the date to be compatible with PostgreSQL TIMESTAMP
                    SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    // Parse the date in UTC timezone
                    inputDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date parsedDate = inputDateFormat.parse(commentCreatedDateTime);

                    // Format the date for insertion into the database
                    String commentCreatedDateTimeFormatted = outputDateFormat.format(parsedDate);

                    // Insert the comment into the database while avoiding duplicates
                    insertCommentIntoDatabase(conn, commentId, commentText, commentCreatedDateTimeFormatted);
                }

                // Getting the next page token for pagination
                nextPageToken = jsonResponse.optString("nextPageToken", null);
                System.out.println("Insertion done");
            } else {
                // Error handling
                System.out.println("Error Response Code: " + response.statusCode());
                System.out.println("Error Response Body:");
                System.out.println(response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Inserting Comment into DB by checking if the comment id already exists in DB or not
    private static void insertCommentIntoDatabase(Connection conn, String commentId, String commentText, String commentCreatedDateTime) {
        try {
            // Checking if the comment id already exists in DB first
            String checkQuery = "SELECT * FROM comments WHERE comment_id = ?";
            PreparedStatement checkStatement = conn.prepareStatement(checkQuery);
            checkStatement.setString(1, commentId);
            if (checkStatement.executeQuery().next()) {
                // If a comment id exists, do nothing
                return;
            }

            // Convert the date string to a timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date parsedDate = null;
			try {
				parsedDate = dateFormat.parse(commentCreatedDateTime);
			} catch (java.text.ParseException e) {
				 
				e.printStackTrace();
			}
            Timestamp timestamp = new Timestamp(parsedDate.getTime());

            // Insert the comment into the database
            String insertQuery = "INSERT INTO comments (comment_id, comment_text, comment_created_datetime) VALUES (?, ?, ?)";
            PreparedStatement insertStatement = conn.prepareStatement(insertQuery);
            insertStatement.setString(1, commentId);
            insertStatement.setString(2, commentText);
            insertStatement.setTimestamp(3, timestamp);

            insertStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

}
