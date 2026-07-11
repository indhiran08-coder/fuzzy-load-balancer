# ⚡ Intelligent API Load Balancer using Fuzzy Logic

> A production-inspired Spring Boot application that routes HTTP requests to the optimal backend server using a **Mamdani Fuzzy Inference System** — replacing traditional Round Robin with intelligent, multi-criteria decision-making.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-green)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

---

## 🎯 Project Overview

Traditional load balancers (Round Robin, Least Connection) consider **one metric at a time**. This system evaluates **four metrics simultaneously** using fuzzy logic to make smarter routing decisions:

| Input Variable | Range | Fuzzy Sets |
|---|---|---|
| CPU Usage | 0–100% | LOW / MEDIUM / HIGH |
| RAM Usage | 0–100% | LOW / MEDIUM / HIGH |
| Active Requests | 0–200 | LOW / MEDIUM / HIGH |
| Response Time | 0–5000ms | FAST / NORMAL / SLOW |

**Output:** Server Priority Score (0–100)

The server with the **highest priority score** gets the next request.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     CLIENT / API GATEWAY                        │
└────────────────────────┬────────────────────────────────────────┘
                         │ POST /api/loadbalancer/route
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SPRING BOOT APPLICATION                        │
│                                                                 │
│  ┌─────────────┐    ┌──────────────────────────────────────┐   │
│  │ Auth Module │    │        Load Balancer Module          │   │
│  │  JWT + BCrypt│   │  ┌────────────────────────────────┐  │   │
│  └─────────────┘    │  │     FuzzyRuleEngine            │  │   │
│                     │  │  Fuzzify → Rule Eval →         │  │   │
│  ┌─────────────┐    │  │  Aggregate → Defuzzify (CoG)   │  │   │
│  │   Server    │    │  └────────────────────────────────┘  │   │
│  │  Module     │◄───│            ▲                         │   │
│  │  CRUD+Health│    │            │ Crisp inputs             │   │
│  └──────┬──────┘    └────────────┼─────────────────────────┘   │
│         │                        │                               │
│  ┌──────▼──────┐    ┌────────────▼─────────────────────────┐   │
│  │  Simulation │    │      Monitoring & Dashboard          │   │
│  │  Module     │    │  DecisionLog / RequestLog / HealthLog│   │
│  │  Scheduler  │    └──────────────────────────────────────┘   │
│  └─────────────┘                                                │
└─────────────────────────────────────────────────────────────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │      MySQL DB       │
              │  users, roles,      │
              │  servers, logs      │
              └─────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- MySQL 8.0+ (or Docker)

### Option 1 — Run with Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/yourusername/fuzzy-load-balancer.git
cd fuzzy-load-balancer

# Start MySQL + Spring Boot
docker-compose up -d

# Watch logs
docker-compose logs -f app
```

Access:
- **Dashboard:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html

---

### Option 2 — Run Locally (MySQL required)

**Step 1: Create MySQL database**
```sql
CREATE DATABASE fuzzy_lb_db;
CREATE USER 'fuzzy'@'localhost' IDENTIFIED BY 'fuzzy123';
GRANT ALL ON fuzzy_lb_db.* TO 'fuzzy'@'localhost';
FLUSH PRIVILEGES;
```

**Step 2: Configure `application.properties`**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/fuzzy_lb_db
spring.datasource.username=fuzzy
spring.datasource.password=fuzzy123
```

**Step 3: Build and run**
```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

---

## 🧠 How the Fuzzy Logic Engine Works

### The Mamdani Inference Pipeline

```
Crisp Inputs           Fuzzification        Rule Evaluation      Defuzzification
(CPU=65%, RAM=40%)  →  (membership degrees) → (MIN/MAX ops)    → Crisp Score (0-100)
```

**Step 1 — Fuzzification**
Convert crisp numbers to fuzzy membership degrees:
```
CPU=65% → {LOW: 0.0, MEDIUM: 0.83, HIGH: 0.25}
RAM=40% → {LOW: 1.0, MEDIUM: 0.33, HIGH: 0.0}
```

**Step 2 — Rule Evaluation (25 expert rules)**
```
IF CPU=LOW AND RAM=LOW AND Requests=LOW AND RT=FAST
THEN Priority=VERY_HIGH (weight: 1.0)

IF CPU=HIGH AND RAM=HIGH AND Requests=HIGH AND RT=SLOW
THEN Priority=VERY_LOW (weight: 1.0)
```
Each rule's activation = MIN(all antecedent membership degrees)

**Step 3 — Aggregation**
```
VERY_HIGH: max(all rules outputting VERY_HIGH) = 0.0
HIGH:      max(all rules outputting HIGH)      = 0.50
MEDIUM:    max(all rules outputting MEDIUM)    = 0.25
```

**Step 4 — Defuzzification (Centroid of Gravity)**
```
Score = Σ(x × μ_agg(x)) / Σ(μ_agg(x))   for x in [0, 100]
```
Result: Single priority score (e.g., `72.4`)

---

## 📡 API Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, get JWT token |
| GET | `/api/auth/me` | Get current user info |

### Server Management

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/servers` | Any | List all servers (paginated) |
| POST | `/api/servers` | ADMIN | Register new server |
| GET | `/api/servers/{id}` | Any | Get server by ID |
| GET | `/api/servers/available` | Any | List HEALTHY+DEGRADED servers |
| PUT | `/api/servers/{id}` | ADMIN | Update server config |
| PATCH | `/api/servers/{id}/health` | ADMIN | Update health status |
| PUT | `/api/servers/{id}/metrics` | Any | Update performance metrics |
| DELETE | `/api/servers/{id}` | ADMIN | Remove server |

### Load Balancer

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/loadbalancer/route` | Route a request (fuzzy decision + logs) |
| GET | `/api/loadbalancer/evaluate` | Dry-run evaluation (no side effects) |
| GET | `/api/loadbalancer/best` | Get current best server |

### Simulation

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/simulation/start` | Start auto-metric updates |
| POST | `/api/simulation/stop` | Stop simulation |
| GET | `/api/simulation/status` | Check if running |
| POST | `/api/simulation/trigger` | Manual tick |
| POST | `/api/simulation/stress` | Apply heavy load to random server |
| POST | `/api/simulation/reset` | Reset all metrics to baseline |

### Dashboard & Monitoring

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/dashboard/summary` | Full KPI summary |
| GET | `/api/dashboard/distribution` | Load distribution per server |
| GET | `/api/dashboard/health` | Server health statuses |
| GET | `/api/dashboard/utilization` | CPU/RAM per server |
| GET | `/api/monitoring/decisions` | Decision log (paginated) |
| GET | `/api/monitoring/requests` | Request log (paginated) |
| GET | `/api/monitoring/health` | Health change log (paginated) |

---

## 🧪 Running Tests

```bash
# Run all tests (uses H2 in-memory DB — no MySQL needed)
mvn test

# Run only fuzzy engine unit tests
mvn test -Dtest=MembershipFunctionTest,FuzzyRuleEngineTest

# Run with coverage report
mvn verify
# Report at: target/site/jacoco/index.html
```

---

## 📁 Project Structure

```
src/
├── main/java/com/fuzzybalancer/
│   ├── auth/                    # JWT authentication
│   │   ├── entity/              # User, Role
│   │   ├── repository/          # UserRepository, RoleRepository
│   │   ├── dto/                 # Request/Response DTOs
│   │   ├── service/             # AuthService, JwtService
│   │   └── controller/          # AuthController
│   ├── server/                  # Backend server management
│   │   ├── entity/              # Server entity
│   │   ├── repository/          # ServerRepository
│   │   ├── dto/                 # ServerRequest, ServerResponse
│   │   ├── service/             # ServerService
│   │   └── controller/          # ServerController
│   ├── fuzzy/                   # Fuzzy Logic engine
│   │   ├── membership/          # MembershipFunction, TriangularMF, TrapezoidalMF
│   │   ├── engine/              # FuzzyRuleEngine, FuzzyVariables, FuzzyEvaluationResult
│   │   └── rules/               # FuzzyRule, FuzzyRuleBase
│   ├── loadbalancer/            # Core routing logic
│   │   ├── dto/                 # RouteResponse
│   │   ├── service/             # LoadBalancerService
│   │   └── controller/          # LoadBalancerController
│   ├── simulation/              # Load simulation module
│   │   ├── service/             # SimulationService
│   │   └── controller/          # SimulationController
│   ├── monitoring/              # Audit logs
│   │   ├── entity/              # DecisionLog, RequestLog, HealthLog
│   │   ├── repository/          # Log repositories
│   │   └── controller/          # MonitoringController
│   ├── dashboard/               # Dashboard APIs + Thymeleaf
│   │   ├── dto/                 # DashboardSummary
│   │   └── controller/          # DashboardController, DashboardViewController
│   ├── common/                  # Shared utilities
│   │   ├── exception/           # ApiException, GlobalExceptionHandler
│   │   └── response/            # ApiResponse wrapper
│   └── config/                  # Configuration
│       ├── SecurityConfig       # Spring Security + JWT filter
│       ├── SwaggerConfig        # OpenAPI documentation
│       └── DataInitializer      # Seed data on startup
├── main/resources/
│   ├── application.properties   # Main configuration
│   ├── templates/index.html     # Thymeleaf dashboard
│   └── static/
│       ├── css/dashboard.css    # Dark glassmorphism styles
│       └── js/dashboard.js      # Dashboard logic + API calls
└── test/
    ├── java/com/fuzzybalancer/
    │   ├── fuzzy/               # Unit tests (no Spring context)
    │   │   ├── MembershipFunctionTest
    │   │   └── FuzzyRuleEngineTest
    │   └── AuthIntegrationTest  # Full integration tests (H2)
    └── resources/
        └── application-test.properties  # H2 test config
```

---

## 🐳 Docker Commands

```bash
# Build the image
docker build -t fuzzy-load-balancer:latest .

# Run with Docker Compose (MySQL + App)
docker-compose up -d

# View running containers
docker-compose ps

# View application logs
docker-compose logs -f app

# Stop everything
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

---

## ☁️ AWS EC2 Deployment

```bash
# 1. Launch an EC2 instance (Amazon Linux 2023, t3.small or larger)
# 2. SSH into the instance
ssh -i "your-key.pem" ec2-user@<EC2-PUBLIC-IP>

# 3. Install Docker
sudo yum update -y
sudo yum install -y docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user

# 4. Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 5. Clone the project
git clone https://github.com/yourusername/fuzzy-load-balancer.git
cd fuzzy-load-balancer

# 6. Set production JWT secret
export JWT_SECRET="your-very-long-random-secret-key-for-production"

# 7. Start
docker-compose up -d

# 8. Open EC2 Security Group:
#    Inbound Rule: TCP port 8080, Source: 0.0.0.0/0
```

Access: `http://<EC2-PUBLIC-IP>:8080`

---

## 🔐 Default Credentials

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| User | `user` | `user123` |

> ⚠️ **Change these before any public deployment!**

---

## 📊 Comparison: Fuzzy Logic vs Traditional Algorithms

| Scenario | Round Robin | Least Connections | **Fuzzy Logic** |
|---|---|---|---|
| Server A: 5% CPU, 3 active requests | Picks in order | Picks (least connections) | ✅ Picks (highest priority score) |
| Server B: 90% CPU, 5 active requests | Picks in order | Might pick this | ❌ Avoids (high CPU penalizes score) |
| Server C: 50% CPU, 1 request but 3000ms RT | Picks in order | Picks (least connections) | ⚠️ Penalized (slow response time) |

Fuzzy Logic considers **all four dimensions simultaneously**, making it more adaptive.

---

## 📄 License

MIT License — free for educational and commercial use.

---

## 👤 Author

Built as a Final Year Engineering Project demonstrating the application of fuzzy logic in distributed systems.
