package com.mycompany.chatserver;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.apache.commons.codec.digest.Crypt;

public class ChatDatabase {

    private static ChatDatabase singleton = null;
    private String databaseName = "";
    private final SecureRandom secureRandom;

    private ChatDatabase() {
        secureRandom = new SecureRandom();
    }

    public static synchronized ChatDatabase getInstance() {
        //Function is synchronized to prevent creating multiple instances of the same object
        if (null == singleton) {
            singleton = new ChatDatabase();
        }
        return singleton;
    }

    public void open(String dbName) throws SQLException {
        //Remove first 12 characters from db name (jdbc:sqlite:)
        File f = new File(dbName.substring(12));
        //Check if a file exists
        Boolean exists = f.exists() && !f.isDirectory();

        databaseName = dbName;

        if (exists == false) {
            initializeDatabase();
        } else {
            System.out.println("Connected to database.");
        }
    }

    private boolean initializeDatabase() throws SQLException {
        //Create a new database if one does not yet exist
        try (Connection db = DriverManager.getConnection(databaseName)) {

            Statement s = db.createStatement();
            
            s.execute("CREATE TABLE IF NOT EXISTS Users(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, username TEXT UNIQUE, nickname TEXT, password TEXT, email TEXT, salt TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS Messages(id INTEGER PRIMARY KEY AUTOINCREMENT, channel TEXT, tag TEXT, message TEXT, timestamp INTEGER, username REFERENCES Users)");

            System.out.println("Database created.");
            System.out.println("Connected to database.");

            s.close();
            return true;
        } catch (SQLException e) {
            System.out.println("Error creating new database.");
        }
        return false;
    }

    private String getHashedPasswordWithSalt(String password) {
        //Create salt for password
        byte[] bytes = new byte[13];
        secureRandom.nextBytes(bytes);

        //Conver salt bytes to a string
        String saltBytes = new String(Base64.getEncoder().encode(bytes));
        String salt = "$6$" + saltBytes;

        //Hash password with salt
        String hashedPassword = Crypt.crypt(password, salt);

        return hashedPassword + " " + salt;
    }

    public boolean addUser(String role, String username, String password, String email) throws SQLException {

        // Hash password with salt
        String split[] = getHashedPasswordWithSalt(password).split(" ");
        String hashedPassword = split[0];
        String salt = split[1];

        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {

            s = db.createStatement();

            //Get count of users with the same username in database, should be 0
            PreparedStatement p = db.prepareStatement("SELECT COUNT(Users.username) AS COUNT FROM Users WHERE Users.username = ?");
            p.setString(1, username);

            ResultSet r = p.executeQuery();

            //Add user to database if username is available
            try {
                if (r.getInt("COUNT") == 0) {
                    
                    PreparedStatement p2 = db.prepareStatement("INSERT INTO Users(role, username, nickname, password, email, salt) VALUES (?, ?, ?, ?, ?, ?)");
                    
                    p2.setString(1, role);
                    p2.setString(2, username);
                    p2.setString(3, username);
                    p2.setString(4, hashedPassword);
                    p2.setString(5, email);
                    p2.setString(6, salt);
                    
                    p2.execute();
                    //s.execute("INSERT INTO Users(role, username,  password, email, salt) VALUES ('" + role + "', '" + username + "', '" + hashedPassword + "','" + email + "','" + salt + "')");
                    System.out.println("Added user " + username + " with role " + role + " to database.");
                    return true;

                } else {
                    System.out.println("Username already exists.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("Error when adding user credentials to database.");
            }
            s.close();

        } catch (SQLException e) {
            System.out.println("Could not connect to database.");
        }
        return false;
    }

    public boolean authenticateUser(String username, String password) throws SQLException {
        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {

            s = db.createStatement();

            //Get user info matching given username and password
            PreparedStatement p = db.prepareStatement("SELECT Users.username, Users.password FROM Users WHERE username = ?");

            p.setString(1, username);

            ResultSet r = p.executeQuery();

            if (r.next()) {
                String hashedPassword = r.getString("password");
                //Check if username and password match
                //Check if hashed password in database matches new hashed password with salt
                if (r.getString("username").equals(username) && hashedPassword.equals(Crypt.crypt(password, hashedPassword))) {
                    return true;
                } else {
                    System.out.println("Wrong username or password");
                    return false;
                }
            } else {
                System.out.println("Invalid user credentials");
            }
            s.close();
        } catch (SQLException e) {
            System.out.println("Could not connect to database");
        }
        return false;
    }

    public void adminDeleteUser(String username) {
        Statement s;

        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            PreparedStatement p = db.prepareStatement("DELETE FROM Users WHERE username = ?");

            p.setString(1, username);

            int result = p.executeUpdate();

            if (result != 0) {
                System.out.println("User " + username + " deleted.");
            } else {
                System.out.println("Can't delete user: username not found");
            }
            s.close();
        } catch (SQLException e) {
            System.out.println("Could not connect to database.");
        }
    }
    
    public boolean editUserDetails(String user, String username, String email, String role, String nickname) throws SQLException {
        // Edit user's info by giving the current username and updated info
        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            PreparedStatement p = db.prepareStatement("UPDATE Users SET username = ? , email = ?, role = ?, nickname = ? WHERE username = ?");

            p.setString(1, username);
            p.setString(2, email);
            p.setString(3, role);
            p.setString(4, nickname);
            p.setString(5, user);

            int num = p.executeUpdate();
            s.close();
            if (num != 0) {
                System.out.println("User " + user + " edited.");
                return true;
            } else {
                System.out.println("Error updating user data: could not find user");
                return false;
            }
        }
    }

    public boolean editUserPassword(String username, String newPassword) throws SQLException {
        // Hash new password with salt
        String split[] = getHashedPasswordWithSalt(newPassword).split(" ");
        String hashedPassword = split[0];

        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            PreparedStatement p = db.prepareStatement("UPDATE Users SET password = ? WHERE username = ?");

            p.setString(1, hashedPassword);
            p.setString(2, username);

            int num = p.executeUpdate();
            s.close();
            if (num != 0) {
                System.out.println(username + " password changed.");
                return true;
            } else {
                System.out.println("Error updating user data: could not find user");
                return false;
            }
        }
    }
    
    public ArrayList getUserDetails(String username) throws SQLException {
        ArrayList<String> userDetails = new ArrayList<>();
        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            PreparedStatement p = db.prepareStatement("SELECT Users.email, Users.nickname FROM Users WHERE username = ?");
            p.setString(1, username);
            
            ResultSet r = p.executeQuery();
            String email = "";
            String nickname = "";
            
            if (r.next()) {
                email = r.getString("email");
                nickname = r.getString("nickname");
                userDetails.add(email);
                userDetails.add(nickname);
            } else {
                System.out.println("wtf");
            }
            return userDetails;
        }
    }

    public void insertMessage(ChatMessage message) {
        Statement s;

        long time = message.sent.toInstant(ZoneOffset.UTC).toEpochMilli();
        String user = message.userName;
        String msg = message.message;
        String channel = message.channel;
        String tag = "";

        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            String msgBody = "INSERT INTO Messages(channel, message, timestamp, username, tag) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement p = db.prepareStatement(msgBody);

            p.setString(1, channel);
            p.setString(2, msg);
            p.setLong(3, time);
            p.setString(4, user);
            p.setString(5, tag);

            p.executeUpdate();
            System.out.println("Message inserted");
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error inserting message into database.");
        }
    }

    public ArrayList<ChatMessage> getMessages(String channel, long messagesSince) {
        //Return all messages from database in a ArrayList
        ArrayList<ChatMessage> messages = new ArrayList<>();
        String msg;
        Long timestamp;
        String user;
        String tag;

        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            String query;
            PreparedStatement p;

            if (messagesSince == -1) {
                //Get 100 newest messages from db if no last-modified header is found
                query = "SELECT Messages.message, Messages.timestamp, Messages.username, Messages.tag"
                        + " FROM Messages WHERE channel = ? ORDER BY timestamp DESC LIMIT 100";
                p = db.prepareStatement(query);
                p.setString(1, channel);
            } else {
                //If last-modified header is found get all new messages
                query = "SELECT Messages.message, Messages.timestamp, Messages.username, Messages.tag "
                        + "FROM Messages WHERE channel = ? AND Messages.timestamp > ? ORDER BY timestamp";
                p = db.prepareStatement(query);
                p.setString(1, channel);
                p.setLong(2, messagesSince);
            }

            ResultSet r = p.executeQuery();

            while (r.next()) {

                msg = r.getString("message");
                timestamp = r.getLong("timestamp");
                user = r.getString("username");
                tag = r.getString("tag");
                //Convert long to LocalDateTime
                LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);

                //Create ChatMessage object from variables, and add it to arraylist
                ChatMessage message = new ChatMessage(channel, time, user, msg, tag);
                messages.add(message);
            }
            s.close();
        } catch (SQLException e) {
            System.out.println("Could not connect to database.");
        }
        return messages;
    }

    public void deleteMessage(int messageID, String username) {
        Statement s;

        LocalDateTime time = LocalDateTime.now();
        long timestamp = time.toInstant(ZoneOffset.UTC).toEpochMilli();
        String tag = "<deleted>";

        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();

            //String query = "DELETE FROM Messages WHERE id = ? AND username = ?)";
            String query = "Update Messages SET message = ?, tag = ?, timestamp = ? WHERE id = ? AND username = ?";
            PreparedStatement p = db.prepareStatement(query);

            p.setString(1, "");
            p.setString(2, tag);
            p.setLong(3, timestamp);
            p.setInt(4, messageID);
            p.setString(5, username);

            int result = p.executeUpdate();

            if (result != 0) {
                System.out.println("Message deleted.");
            } else {
                System.out.println("Could not delete message. Invalid message ID or username");
            }
            s.close();
        } catch (SQLException e) {
            System.out.println("Could not connect to database.");
        }
    }

    public void editMessage(int messageID, String username, String newMessage) throws SQLException {

        LocalDateTime time = LocalDateTime.now();
        long timestamp = time.toInstant(ZoneOffset.UTC).toEpochMilli();
        String tag = "<edited>";
        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {

            s = db.createStatement();
            // Edit message only if it doesn't have deleted tag
            String query = "Update Messages SET message = ?, tag = ?, timestamp = ? WHERE id = ? AND username = ? AND tag IS NOT ?";

            PreparedStatement p = db.prepareStatement(query);

            p.setString(1, newMessage);
            p.setString(2, tag);
            p.setLong(3, timestamp);
            p.setInt(4, messageID);
            p.setString(5, username);
            p.setString(6, "<deleted>");

            int result = p.executeUpdate();

            if (result != 0) {
                System.out.println("Message succesfully edited.");
            } else {
                System.out.println("Error editing message. Message does not exist. ");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ArrayList listChannels() throws SQLException {
        //Returns a list containing all different channels
        ArrayList<String> channels = new ArrayList<>();
        Statement s;
        try (Connection db = DriverManager.getConnection(databaseName)) {
            s = db.createStatement();
            PreparedStatement p = db.prepareStatement("SELECT DISTINCT channel FROM messages");

            ResultSet r = p.executeQuery();

            while (r.next()) {
                channels.add(r.getString("channel"));
            }
        }
        return channels;
    }
}
