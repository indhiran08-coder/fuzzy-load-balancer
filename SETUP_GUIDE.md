# 🚀 Fuzzy Load Balancer — Complete Setup Guide
### (Written for this exact PC — Windows 11, Java 25, Maven 3.9.15, MySQL 8.0)

---

## ✅ STEP 1 — Check What's Already Installed

Open **PowerShell** (search "PowerShell" in Start menu) and run:

```powershell
java -version
```
✅ You should see: `java version "25.0.1"`

```powershell
mvn -version
```
✅ You should see: `Apache Maven 3.9.15`

> Maven is at: `C:\Users\HP\OneDrive\Desktop\apache-maven-3.9.15`
> If `mvn` is not found, just use `.\mvnw.cmd` inside the project folder (always works).

---

## ✅ STEP 2 — Make Sure MySQL is Running

MySQL 8.0 is already installed on this PC.

### Start MySQL (if not running):
Option A — Search "Services" in Start menu → find **MySQL80** → Right-click → Start

Option B — PowerShell:
```powershell
Start-Service -Name "MySQL80"
```

### Verify it's running:
```powershell
Get-Service -Name "MySQL80"
```
✅ Should show: `Status: Running`

---

## ✅ STEP 3 — Go to the Project Folder

Open PowerShell and run:
```powershell
cd "C:\Users\HP\OneDrive\Desktop\fuzzy-load-balancer"
```

> Tip: Right-click inside the project folder and choose "Open in Terminal"

---

## ✅ STEP 4 — Build the Project

```powershell
.\mvnw.cmd install -DskipTests
```

First time: ~2-3 minutes (downloads libraries)
After that: ~10 seconds

✅ You should see at the end:
```
BUILD SUCCESS
```

> If you see "Failed to delete target\..." error, run this first:
> ```powershell
> Remove-Item -Recurse -Force target -ErrorAction SilentlyContinue
> .\mvnw.cmd install -DskipTests
> ```

---

## ✅ STEP 5 — Run the Application

```powershell
.\mvnw.cmd spring-boot:run
```

Wait ~15-20 seconds until you see:
```
Started FuzzyLoadBalancerApplication in XX seconds
```

✅ App is now running!

---

## ✅ STEP 6 — Open in Browser

| What | URL |
|---|---|
| Dashboard | http://localhost:8080 |
| API Explorer (Swagger) | http://localhost:8080/swagger-ui.html |
| Health Check | http://localhost:8080/actuator/health |

---

## 🔑 Login Credentials

| Role | Username | Password |
|---|---|---|
| Admin | admin | admin123 |
| User | user | user123 |

---

## 🗄️ Database Info

The app auto-connects to MySQL using these settings (in application.properties):
- Database: fuzzy_lb (created automatically on first run)
- Username: root
- Password: root

---

## 🛑 How to Stop the App

In the PowerShell window where it is running:
Press Ctrl + C

---

## 🔄 Quick Start (Every Time)

```powershell
cd "C:\Users\HP\OneDrive\Desktop\fuzzy-load-balancer"
.\mvnw.cmd spring-boot:run
```
Then open http://localhost:8080. Done!

---

## 🧪 Demo Steps (For Presentation)

### 1. Login via Swagger
- Open http://localhost:8080/swagger-ui.html
- Find POST /api/auth/login → "Try it out"
- Enter: { "username": "admin", "password": "admin123" }
- Copy the token from the response

### 2. Authorize Swagger
- Click the lock icon (top right)
- Type: Bearer <your token>
- Click Authorize

### 3. Add Servers
Use POST /api/servers:
```json
{
  "name": "Server-A",
  "address": "192.168.1.10",
  "port": 8081,
  "description": "Primary backend server"
}
```

### 4. See Fuzzy Logic in Action
- POST /api/loadbalancer/route — picks best server using fuzzy logic
- POST /api/simulation/start — auto-updates metrics every 5 seconds
- Open http://localhost:8080 — watch live dashboard

---

## 🐛 Troubleshooting

### "This site can't be reached"
→ App isn't running. Run: .\mvnw.cmd spring-boot:run

### App crashes immediately after starting
→ MySQL not running. Run: Start-Service -Name "MySQL80"

### "Access denied for user root"
→ Your MySQL password is different from "root"
→ Edit src\main\resources\application.properties
→ Change: spring.datasource.password=YOUR_ACTUAL_PASSWORD

### "Port 8080 already in use"
```powershell
netstat -ano | findstr :8080
taskkill /PID <the number shown> /F
```

### BUILD FAILURE during mvnw install
```powershell
Remove-Item -Recurse -Force target -ErrorAction SilentlyContinue
.\mvnw.cmd install -DskipTests
```

---

## ⚙️ Fixes Applied (Why pom.xml Was Changed)

This project was written for Java 21 but this PC has Java 25.
Three fixes were made:

1. Lombok upgraded to 1.18.46 (adds Java 25 support)
2. Compiler set to output Java 21 bytecode (Spring Boot 3.2.5 needs this)
3. FuzzyRuleBase.java: Fixed ambiguous imports for Java 25 strict compiler

These fixes are already saved. You do NOT need to do anything — just build and run!

---

*Guide created: July 2026 | Project: Intelligent API Load Balancer using Fuzzy Logic*
