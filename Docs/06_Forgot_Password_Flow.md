# 🔑 Forgot Password Flow over TCP (FORGOT_PASSWORD Action)

This document details the operational flow of the account password recovery feature over raw TCP Sockets using the `FORGOT_PASSWORD` Action.

The password reset process consists of two independent steps triggered by a single server handler depending on the payload properties.

---

## Step 1: Request Reset Code

The Client requests a 6-digit password recovery code by sending a JSON payload containing only the `username` field.

### JSON Request (Client → Server):
```json
{
  "action": "FORGOT_PASSWORD",
  "requestId": "req-forgot-1",
  "username": "john_doe"
}
```

### JSON Response (Server → Client):
```json
{
  "action": "FORGOT_PASSWORD_RESPONSE",
  "requestId": "req-forgot-1",
  "status": "success",
  "message": "Reset code generated.",
  "code": "482719"
}
```

> **⚠️ Security Warning:** In a production environment, the reset `code` should be delivered privately via email or SMS rather than returned in the socket payload. The current response returns the code directly to facilitate academic project evaluation and simplify testing.

---

## Step 2: Reset Password

After receiving the 6-digit code in Step 1, the Client prompts the user for a new password and submits a payload containing both the `code` and the new `password`.

### JSON Request (Client → Server):
```json
{
  "action": "FORGOT_PASSWORD",
  "requestId": "req-forgot-2",
  "code": "482719",
  "password": "NewSecurePassword123"
}
```

### JSON Response (Server → Client):
```json
{
  "action": "FORGOT_PASSWORD_RESPONSE",
  "requestId": "req-forgot-2",
  "status": "success",
  "message": "Password reset successful"
}
```

---

## Error Handling Scenarios

1.  **User Account Not Found (Step 1)**:
    ```json
    {
      "action": "FORGOT_PASSWORD_RESPONSE",
      "requestId": "req-forgot-1",
      "status": "error",
      "message": "Account not found"
    }
    ```
2.  **Invalid or Expired Code (Step 2)**:
    ```json
    {
      "action": "FORGOT_PASSWORD_RESPONSE",
      "requestId": "req-forgot-2",
      "status": "error",
      "message": "Invalid or expired code"
    }
    ```
3.  **Missing Required Payload Fields**:
    ```json
    {
      "action": "FORGOT_PASSWORD_RESPONSE",
      "requestId": "req-forgot-1",
      "status": "error",
      "message": "Missing required info (username or code/password)"
    }
    ```
