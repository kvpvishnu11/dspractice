package com.RedditPipeline.Reddit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Redditdata {
    public static void main(String[] args) {
        // Scheduling it for every 5 minutes 
    	
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        executorService.scheduleAtFixedRate(() -> {
            try {
                // My DB parameters
                String jdbcUrl = "jdbc:postgresql://localhost:5432/reddit";
                String username = "postgres";
                String password = "150030441@klU";

                 
                Connection connection = DriverManager.getConnection(jdbcUrl, username, password);

                // Set the connection to use UTC
                ((DateFormat) connection).setTimeZone(TimeZone.getTimeZone("UTC"));

                
                String insertSql = "INSERT INTO reddit_comments (comment_id, comment_text, subreddit, comment_created_time, db_insertion_time) " +
                                    "VALUES (?, ?, ?, ?, NOW() AT TIME ZONE 'UTC') ON CONFLICT (comment_id) DO NOTHING";
                PreparedStatement preparedStatement = connection.prepareStatement(insertSql);

                 
                String checkIfExistsSql = "SELECT comment_id FROM reddit_comments WHERE comment_id = ?";
                PreparedStatement checkIfExistsStatement = connection.prepareStatement(checkIfExistsSql);

                // Fetch the sub reddits dynamically from database
                
                String fetchSubredditsSql = "SELECT subreddit_name FROM subreddits";
                PreparedStatement fetchSubredditsStatement = connection.prepareStatement(fetchSubredditsSql);

                // Fetch subreddit names from the database
                ResultSet subredditResultSet = fetchSubredditsStatement.executeQuery();

                while (subredditResultSet.next()) {
                    String subredditName = subredditResultSet.getString("subreddit_name");

                    String apiUrl = "https://api.reddit.com/r/" + subredditName + "/comments.json"; 

                    HttpClient client = HttpClient.newHttpClient();

                 // Send http request
                    String requestUrl = apiUrl;
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(requestUrl))
                            .header("User-Agent", "MyRedditApp/1.0 (by PuzzledBrother9059; kvpvishnu11@gmail.com)") // Replace with your user agent
                            .GET()
                            .build();

                    // Send the request and get the response
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    // Parsing the JSON response
                    JSONObject jsonResponse = new JSONObject(response.body());
                    System.out.println("JSON Response:");
                    System.out.println(jsonResponse);

                    // Extract the comments data 
                    if (jsonResponse.has("data")) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        if (data.has("children")) {
                            JSONArray children = data.getJSONArray("children");
                            for (int i = 0; i < children.length(); i++) {
                                JSONObject commentData = children.getJSONObject(i).getJSONObject("data");
                                String commentId = commentData.getString("id");

                                // Check if the ID already exists in the database
                                checkIfExistsStatement.setString(1, commentId);
                                ResultSet resultSet = checkIfExistsStatement.executeQuery();

                                if (!resultSet.next()) {
                                    // Comment ID doesn't exist in the database, proceed to insert
                                    String commentText = commentData.getString("body");
                                    String subreddit = subredditName;
                                    long createdUtc = commentData.getLong("created_utc");

                                    // Convert created_utc to a formatted date-time string in UTC
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                    String commentCreatedTime = sdf.format(new Date(createdUtc * 1000));

                                    // Set the parameters for the SQL statement
                                    preparedStatement.setString(1, commentId);
                                    preparedStatement.setString(2, commentText);
                                    preparedStatement.setString(3, subreddit);
                                    preparedStatement.setString(4, commentCreatedTime);

                                    // Execute the SQL statement to insert the comment or reply
                                    preparedStatement.executeUpdate();
                                }

                                resultSet.close();
                            }
                        }
                    }
                }

                subredditResultSet.close();
                // Close the database connection
                connection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 300, TimeUnit.SECONDS); 
    }
}
