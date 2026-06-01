# Project chat application with GUI via TCP/IP protocol

## Features

- [x] GUI interface
- [x] Login with username and password
- [x] Register new account
- [x] Forgot password
- [ ] Change password from profile
- [ ] Change username
- [x] Change avatar
- [x] Send message through TCP socket
- [x] Receive message from server (via Stateful TCP Sockets)
- [x] Send message with image, video, voice
- [x] Chat with multiple users
- [ ] Voice call
- [ ] Video call
- [ ] Screen sharing
- [x] File sharing
- [x] Group chat

## Built With

### Programming language

- [Java 25](https://www.oracle.com/asean/java/technologies/downloads/#java25)
  - [JavaFX](https://openjfx.io/) - GUI interface
  - Stateful Raw TCP Sockets (`java.net.Socket`)

### Database

- [MySQL](https://www.mysql.com/) - database hosted on 123host.vn (utilizing HikariCP connection pooling)

### Server host

- [Render](https://render.com/) - server host
  - [Docker](https://www.docker.com/) - containerization

### Image hosting

- [ImgBB](https://imgbb.com/) - image hosting

## Running the Application

### Launching with Windows Command Scripts

The project includes convenient double-click script files at the root level:

- Run the server: Double-click **`run_server.cmd`**
- Run the client: Double-click **`run_client.cmd`**

### Running Manually with Maven

Alternatively, you can run the modules using the command line:

#### 1. Start the Stateful TCP Server
```powershell
cd Code/Server
mvn compile
mvn exec:java -Dexec.mainClass="com.server.Main"
```

#### 2. Start the JavaFX UI Client
```powershell
cd Code/Client
mvn compile
mvn javafx:run
```
