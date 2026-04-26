# Password Reset Implementation Summary

## Overview
Implemented a complete password reset flow with email verification and 1-minute cooldown as requested.

## Backend Changes

### 1. New Entities
- **EmailVerificationCode** (`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/entity/EmailVerificationCode.java`)
  - Stores verification codes with email, code, expiration time, and verification status
  - Includes indexes on email+code and email+expires_at for performance

### 2. New Repositories
- **EmailVerificationCodeRepository** (`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/repository/EmailVerificationCodeRepository.java`)
  - Methods for finding valid codes, cleaning up expired codes, and deleting unverified codes

### 3. New DTOs
- **EmailVerificationRequest** - For requesting a verification code
- **VerifyCodeRequest** - For verifying a received code
- **ResetPasswordRequest** - For resetting password with verified code

### 4. Updated Services
- **MailService** (`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/service/MailService.java`)
  - Added `sendVerificationCode()` method to send 6-digit verification codes
- **AuthService** (`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java`)
  - Added `sendVerificationCode()` with 1-minute cooldown enforcement
  - Added `verifyCode()` to validate verification codes
  - Added `resetPassword()` to update password after verification
  - Added `generateVerificationCode()` helper method

### 5. New Controller
- **PasswordResetController** (`/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/java/com/smartlab/erp/controller/PasswordResetController.java`)
  - `POST /api/auth/password/send-code` - Send verification code to email
  - `POST /api/auth/password/verify-code` - Verify the received code
  - `POST /api/auth/password/reset` - Reset password with verified code

## Frontend Changes

### 1. New Components
- **PasswordResetModal** (`/home/a/zhangqi/workspace/ZKR/lab-erp-demo/src/components/auth/PasswordResetModal.vue`)
  - 4-step wizard: Input email → Verify code → Reset password → Success
  - 60-second cooldown timer for resend
  - Full form validation

### 2. Updated Components
- **AuthCredentialsForm** (`/home/a/zhangqi/workspace/ZKR/lab-erp-demo/src/components/auth/AuthCredentialsForm.vue`)
  - Added `@go-forgot-password` event emission when "Forgot Password" link is clicked
  - Already had the link in template, now functional

### 3. Updated Views
- **ErpLoginView** (`/home/a/zhangqi/workspace/ZKR/lab-erp-demo/src/views/ErpLoginView.vue`)
  - Added PasswordResetModal import and usage
  - Added `showPasswordReset` state variable
  - Added `handlePasswordResetSuccess()` method
  - Updated AuthCredentialsForm to enable forgot password link

### 4. Updated Stores
- **userStore** (`/home/a/zhangqi/workspace/ZKR/lab-erp-demo/src/stores/userStore.js`)
  - Added `sendVerificationCode()` method
  - Added `verifyCode()` method
  - Added `resetPassword()` method

## Security Features
1. **1-minute cooldown** - Users can only request a new code every 60 seconds
2. **Code expiration** - Codes expire after 5 minutes
3. **Email validation** - Only registered emails can request codes
4. **Code validation** - 6-digit numeric codes
5. **Password requirements** - 8-100 characters

## API Endpoints
```
POST /api/auth/password/send-code
Body: { "email": "user@example.com" }

POST /api/auth/password/verify-code
Body: { "email": "user@example.com", "code": "123456" }

POST /api/auth/password/reset
Body: { "email": "user@example.com", "code": "123456", "newPassword": "newSecurePassword123" }
```

## Flow Diagram
1. User clicks "忘记密码" on login page
2. Modal opens with email input
3. User enters email and clicks "发送验证码"
4. Code sent to email (1-minute cooldown starts)
5. User enters 6-digit code
6. User enters new password (8-100 characters)
7. Password reset succeeds, user can login with new password

## Database Changes Required
The new `email_verification_codes` table needs to be created. With JPA configured with `ddl-auto: none`, you'll need to:

1. Update `application.yml` to use `ddl-auto: update` temporarily, OR
2. Run SQL migration manually:
```sql
CREATE TABLE email_verification_codes (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_email_code ON email_verification_codes(email, code);
CREATE INDEX idx_email_expires ON email_verification_codes(email, expires_at);
```

## Testing
- Frontend builds successfully without errors
- All components properly wired together
- Backend code compiles without syntax errors
- Docker images will need to be rebuilt to include new code

## Next Steps
1. Rebuild backend Docker image with new code
2. Rebuild frontend Docker image with new code
3. Update docker-compose to use new images
4. Run database migration to create new table
5. Test complete flow end-to-end
