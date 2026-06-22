# Trackit – Wireless Telemedicine & ECG/PPG Triage System

Trackit is a real-time wireless emergency telemedicine web application. It streams telemetry waveforms (ECG at 256Hz, PPG at 64Hz) to a Java backend via WebSockets, forwards windows to a Python PyTorch ML inference microservice for triage assessment, and displays real-time health indicators and alert escalations on a premium styled frontend dashboard.

---

## System Architecture

- **Frontend**: Vanilla HTML5, CSS3 (custom HSL theme, CSS grid layouts, micro-animations), and high-performance JavaScript Canvas drawing with `requestAnimationFrame`.
- **Backend Service**: Java EE web application deployed on Apache Tomcat 10.x. Uses WebSockets (`EcgMonitorEndpoint`) for sample ingestion and REST for authentication and patient administration.
- **Inference Service**: Python FastAPI microservice executing a PyTorch wearable triage classification model on CPU.
- **Database**: MySQL relational store.

---

## 1. Database Setup

1. **Start MySQL Server**: Ensure MySQL server is running locally (default port `3306`).
2. **Import Database Schema**: Create the database and tables using the schema script located at:
   - [schema.sql](file:///c:/Users/Johnny/Dev-Env/trackit/schema.sql)
   - You can import it using your MySQL client CLI or UI tool:
     ```bash
     mysql -u root -p < schema.sql
     ```
3. **Database Configuration**:
   - Connection configurations are loaded from the environment variables or the [.env](file:///c:/Users/Johnny/Dev-Env/trackit/.env) file in the root workspace directory.
   - If not set in the environment, the application falls back to defaults defined in:
     - [database.properties](file:///c:/Users/Johnny/Dev-Env/trackit/src/main/resources/database.properties)
     - Default configuration:
       - **URL**: `jdbc:mysql://localhost:3306/trackit?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
       - **Username**: `root`
       - **Password**: *(empty)*

---

## 2. Python Inference Service Setup

1. **Navigate to Directory**:
   ```bash
   cd inference-service
   ```
2. **Setup Virtual Environment & Install Dependencies**:
   ```bash
   python -m venv .venv
   # Windows:
   .venv\Scripts\activate
   # macOS/Linux:
   source .venv/bin/activate

   pip install -r requirements.txt
   ```
3. **Start FastAPI Uvicorn Server**:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8001
   ```
   *Note: The ML service runs on port `8001` and exposes the `/infer` and `/health` endpoints.*

---

## 3. Java Web App Build & Deploy

1. **Compile & Package**:
   From the root workspace directory, run Maven packaging to compile all source files and generate the WAR deployment package:
   ```bash
   mvn clean package -DskipTests
   ```
2. **Start the Application Container (Tomcat)**:
   Launch the embedded Tomcat 10.x container using Maven Cargo. Ensure you inject the `INFERENCE_SERVICE_URL` variable:
   - **PowerShell (Windows)**:
     ```powershell
     $env:INFERENCE_SERVICE_URL="http://localhost:8001"; mvn cargo:run
     ```
   - **Bash (macOS/Linux/Git Bash)**:
     ```bash
     INFERENCE_SERVICE_URL="http://localhost:8001" mvn cargo:run
     ```
   - The application context is deployed at: `http://localhost:8080/trackit/`

---

## 4. Default Credentials & Flow Validation

### Default Doctor Credentials
At application startup, if the database is clean, a default super-administrator user is seeded with the credentials defined in `.env`:
- **Email**: `admin@trackit.com`
- **Password**: `AdminSecurePassword123!`

### Telemetry Simulation Flow
1. Open the browser and visit `http://localhost:8080/trackit/login.html`.
2. Sign in using the doctor credentials.
3. Select **Find Patient** to search for existing patients, or **New Patient** to register a new record.
4. Open the **Patient Connection** dashboard tab.
5. In the left panel:
   - Search/select the patient name (uses autocomplete).
   - Click the file selector and select a simulation CSV file from the root directory.
   - Recommended simulation file: [alternating_conditions.csv](file:///c:/Users/Johnny/Dev-Env/trackit/alternating_conditions.csv) (combines segments of normal rhythm, tachycardia, afib, and bradycardia).
   - Click **Connect to patient**.
6. The dual-track canvas will display real-time scrolling ECG (256Hz) and PPG (64Hz) waveforms.
7. Triage results from the ML model will trigger every 5 seconds (zero-padded for the first few cycles until the buffer matches the 30-second model layout), showing rhythm analysis and escalations on the right vital signs board.
