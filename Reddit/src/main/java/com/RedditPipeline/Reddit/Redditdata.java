package com.RedditPipeline.Reddit;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

public class Redditdata 
{
    public static void main(String[] args) {
        // Scheduling our code for every 5 minutes
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        // Defining our Reddit OAuth credentials
        
        String clientId = "qlQUzr9kQi2k12vy0C4RBA";
        String clientSecret = "ohKP0Ao2IPhZcqfc_E2TfBsPkXlbyg";
        String userAgent = "MyRedditApp/1.0 (by PuzzledBrother9059; kvpvishnu11@gmail.com)";

        // Variables to store the access token
        AtomicReference<String> accessToken = new AtomicReference<>(null);

        executorService.scheduleAtFixedRate(() -> {
            try {
                // Obtain the access token if not already obtained
                if (accessToken.get() == null) {
                    String token = getRedditAccessToken(clientId, clientSecret, userAgent);
                    if (token != null) {
                        accessToken.set(token);
                    } else {
                        return; 
                    }
                }

                // Setting up our DB parameters
                String jdbcUrl = "jdbc:postgresql://localhost:5432/reddit";
                String username = "postgres";
                String password = "150030441@klU";
                
                /* Establishing connections and preparing required SQL queries for our scenario */
                Connection connection = DriverManager.getConnection(jdbcUrl, username, password);

                String insertSql = "INSERT INTO reddit_comments (comment_id, comment_text, subreddit, comment_created_time, db_insertion_time) " +
                        "VALUES (?, ?, ?, ?, NOW()) ON CONFLICT (comment_id) DO NOTHING";
                PreparedStatement preparedStatement = connection.prepareStatement(insertSql);

                String checkIfExistsSql = "SELECT comment_id FROM reddit_comments WHERE comment_id = ?";
                PreparedStatement checkIfExistsStatement = connection.prepareStatement(checkIfExistsSql);

                String fetchSubredditsSql = "SELECT subreddit_name FROM subreddits";
                PreparedStatement fetchSubredditsStatement = connection.prepareStatement(fetchSubredditsSql);
                ResultSet subredditResultSet = fetchSubredditsStatement.executeQuery();

                while (subredditResultSet.next()) {
                    String subredditName = subredditResultSet.getString("subreddit_name");

                    String apiUrl = "https://oauth.reddit.com/r/" + subredditName + "/comments.json?";

                    String credentials = clientId + ":" + clientSecret;

                    HttpClient client = HttpClient.newHttpClient();

                    // Send HTTP request with OAuth authorization
                    // We are sending the access Token in the header of OAuth request
                    // This will make sure our request is good every time
                    String requestUrl = apiUrl;
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(requestUrl))
                            .header("Authorization", "Bearer " + accessToken.get())
                            .header("User-Agent", userAgent)
                            .GET()
                            .build();

                    // Capturing the response from the API
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    // Check for HTTP response code
                    int statusCode = response.statusCode();
                    if (statusCode != 200) {
                        System.out.println("HTTP Request Error: " + statusCode);
                        continue; // Skip processing if there's an HTTP error
                    }

                    // Parsing the JSON response
                    JSONObject jsonResponse = new JSONObject(response.body());
                    System.out.println(jsonResponse);
           
                    if (jsonResponse.has("error")) {
                        String errorMessage = jsonResponse.getString("error");
                        System.out.println("Reddit API Error: " + errorMessage);
                        continue; // Skip processing if there's a Reddit API error
                    }

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
                                    String commentText = commentData.getString("body");
                                    String subreddit = subredditName;
                                    long createdUtc = commentData.getLong("created_utc");

                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                    String commentCreatedTimeString = sdf.format(new Date(createdUtc * 1000));
                                    Timestamp commentCreatedTime = Timestamp.valueOf(commentCreatedTimeString);

                                    preparedStatement.setString(1, commentId);
                                    preparedStatement.setString(2, commentText);
                                    preparedStatement.setString(3, subreddit);
                                    preparedStatement.setTimestamp(4, commentCreatedTime);
                                    preparedStatement.executeUpdate();
                                }

                                resultSet.close();
                            }
                        }
                    }
                }

                subredditResultSet.close();
                connection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 300, TimeUnit.SECONDS);
    }

    // Obtaining the access token for OAUTH
    // We want to use this Access Token in the header of OAuth request in above method
    private static String getRedditAccessToken(String clientId, String clientSecret, String userAgent) {
        try {
           
            String authUrl = "https://www.reddit.com/api/v1/access_token";
            String requestBody = "grant_type=client_credentials";

            // Using http client
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(authUrl))
                    .header("User-Agent", userAgent)
                    .header("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send request and getting the response here
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                return json.getString("access_token");
            } else {
                System.err.println("Couldnt get access token due to the error. HTTP Status Code: " + response.statusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
