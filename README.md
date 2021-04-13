# ChatServer
Chat Server made in Programming 3 course.
<br>
<br>
Server handles HTTP requests. User can register a new user with username, password and email, login with user, and post messages that are saved to  SQLite database.  
Passwords are encrypted for extra security.  
<br>
Client coming soon..

# Startup parameters
To start the server, pass these tree startup parameters to server, <strong>in this order:</strong> 
1. Database file name and path
2. Certificate file name and path
3. Password of the certificate

For example: java -jar target/my-server-jar.jar chat-database.db keystore.jks mypassword123

If using the example certificate "keystore.jks", the password is "123456789". 

# REST API
Server uses the following API. Requests have to be in JSON-format.
<br><br>
__HTTP body's for specific requests have to follow these formats__
<br>
## <strong>__/registration__</strong>  
#### Register new user
<pre>
{
    “user” : “nickname”,
    “action” : “edit”, //or remove
    “userdetails” :
    {
        “username” : “username”,
        “password” : “password”,
        “role” : “user”, //or administrator
        “email” : user.email@for-contacting.com”
     }
}
</pre>
<br></br>
## <strong>__/chat__</strong>  

### POST-request
Post a message
<pre>
{
    “user” : “nickname”,
    “channel” : “channel”,
    “message” : “contents of the message”,
    “sent” : “2021-04-13T07:57:47.123Z” // Timestamp for message
}
</pre>
### GET-request
Get messages or get a list of available channels
<pre>
{
    ”channel: ”channel”, // specifies whihch channel to return messages from. Must not be empty.
    ”action”: ”getMessages” // or ”listChannels” 
}
</pre>
Action specifies what action to perform. getMessages returns all messages in specified channel and listChannels returns list of all available channels. Action must not be empty.
<br>
### PUT-request 
Edit message
<pre>
{
    “user” : “nickname”,
    “action” : “editmessage”
    “messageid” : “293”, // message id to delete
    “channel” : “channel”
    “message” : “new contents of the message”,
    “sent” : “2020-12-21T07:57:47.123Z”
}
</pre>
Edit user (with admin rights)
<pre>
{
    “user” : “nickname”, // Details of the user performing the action
    "role" : "role",
    “action” : “editUser",
    "userdetails": // Details of user to be edited
    {
      “username : “currentUsername” // Current username of the user to be edited
      “newUsername” : “updatedUsername”, // New user details || or old details if not updating everything
      “password” : “updatedPassword”, 
      “email” : “updatedEmail”
    }
}
</pre>
### DELETE-request
Delete message
<pre>
{
    “user” : “nickname”,
    “action” : “deletemessage”
    “messageid” : “293”
    “channel” : “channel”,
    “sent” : “2020-12-21T07:57:47.123Z”
}
</pre>

Delete user (with admin rights)
<pre>
{
    “user” : “username”, // user to remove
    “action” : “remove”
    "userdetails": // details of the user requesting the action
    {
        "username" : "username",
        "password" : "password",
        "role": "admin",
        "email" : "email@admin.com"
    }
}
</pre>

