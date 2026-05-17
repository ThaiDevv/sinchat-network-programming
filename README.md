# Project chat application with GUI via TCP/IP protocol

## Features

- [x] GUI interface
- [x] Login with username and password
- [x] Register new account
- [x] Forgot password
- [ ] Change password from profile
- [ ] Change username
- [ ] Change avatar
- [x] Send message in local UI prototype
- [ ] Receive message from server
- [ ] Send message with image, video, voice
- [ ] Chat with multiple users
- [ ] Voice call
- [ ] Video call
- [ ] Screen sharing
- [ ] File sharing
- [ ] Group chat

## Built With

### Programming language

- [Java 25](https://www.oracle.com/asean/java/technologies/downloads/#java25)
  - [JavaFX](https://openjfx.io/) - GUI interface
  - [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) - WebSocket library

### Database

- [MySQL](https://www.mysql.com/) - database hosted on 123host.vn

### Server host

- [Render](https://render.com/) - server host
  - [Docker](https://www.docker.com/) - containerization

### Image hosting

- [ImgBB](https://imgbb.com/) - image hosting

## Client API Base URL

The JavaFX client uses `http://localhost:8080` by default. To use the deployed
backend, run the client with:

```powershell
java -Dchatapp.api.baseUrl=https://network-programming-project.onrender.com ...
```
