# SinChat - Chat Application

SinChat is a student project for a chat application with a JavaFX desktop
interface and backend API integration.

## Features

- [x] JavaFX GUI interface
- [x] Login with username and password
- [x] Register a new account
- [x] Forgot password flow with verification code
- [x] Basic chat screen prototype
- [x] Send message in the local UI prototype
- [ ] Receive message from server
- [ ] Chat with multiple users
- [ ] Change password from profile
- [ ] Change username
- [ ] Change avatar
- [ ] Send image, video, and voice messages
- [ ] Voice call
- [ ] Video call
- [ ] Screen sharing
- [ ] File sharing
- [ ] Group chat

## Built With

- [Java 25](https://www.oracle.com/asean/java/technologies/downloads/#java25)
- [JavaFX](https://openjfx.io/)
- [MySQL](https://www.mysql.com/)

## API Base URL

The UI uses `http://localhost:8080` by default. To use the deployed backend,
run the app with:

```powershell
java -Dchatapp.api.baseUrl=https://network-programming-project.onrender.com ...
```
