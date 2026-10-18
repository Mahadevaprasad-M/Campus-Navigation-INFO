# 🗺️ Campus Navigation System — GEC Chamarajanagar

A smart, offline-capable **Campus Navigation Web Application** designed for the Government Engineering College, Chamarajanagar. This project uses **Java** for the backend routing engine and **Vanilla HTML/CSS/JS** for the frontend.

The system calculates the shortest walking path between any two locations on campus using **Dijkstra's Algorithm**, provides turn-by-turn directions, supports **English & Kannada**, offers voice guidance, and generates printable QR codes for physical locations.

![Java](https://img.shields.io/badge/Java-11%2B-orange)
![HTML5](https://img.shields.io/badge/HTML5-E34F26?logo=html5&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?logo=javascript&logoColor=black)
![Algorithm](https://img.shields.io/badge/Algorithm-Dijkstra%20%7C%20BFS-success)

---

## 📸 Screenshots

| Main Interface | Finding a Route | QR Code Generator |
|---|---|---|
| ![Main Page](Images/Screenshots/Main%20Page.png) | ![Finding Route](Images/Screenshots/Finding%20Route.png) | ![QR Code](Images/Screenshots/QR%20Code%20Generate%20Page.png) |

---

## ✨ Features

### 🧭 Navigation & Routing
- **Shortest Path (Dijkstra):** Calculates the most accurate route based on real-world metres.
- **Fewest Stops (BFS):** Alternative route calculation prioritizing fewer turns/junctions.
- **Turn-by-Turn Directions:** Instructions like *"Turn left at Library Junction"* calculated using cross-products of coordinates.
- **Emergency Mode:** Instantly finds the nearest exit gate (Main Entrance or Rear Gate).
- **Interactive Map:** Hover/click on map nodes to set destinations.
- **Text-to-Speech:** Reads the directions aloud using the Web Speech API.

### 🌐 Accessibility & UI
- **Bilingual Support:** Toggle between English and Kannada (ಕನ್ನಡ) instantly.
- **Responsive Design:** Works seamlessly on mobile phones, tablets, and desktops.
- **Zero External Dependencies:** The Java backend uses the built-in `com.sun.net.httpserver`—no Spring Boot or Maven required.

### 📱 QR Code Integration
- **Generate QR Codes:** Includes a standalone `generate-qr.html` tool to create a printable A4 sheet of QR codes for all 36 campus locations.
- **Scan to Navigate:** Scanning a QR code opens the app with your current location pre-selected (e.g., `/?loc=library`).

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | HTML5, CSS3, Vanilla JavaScript |
| **Backend** | Java (Standard Edition) |
| **Data Store** | JSON files (`locations.json`, `edges.json`) |
| **Routing Algorithms** | Dijkstra's Algorithm, Breadth-First Search (BFS) |
| **QR Generation** | External API integration (qrserver.com) |

---

## 📁 Folder Structure

```text
campus-navigation/
│
├── .gitignore
├── README.md
│
├── assets/                  # Static assets
│   ├── campus-map.svg       # Base map background
│   └── logo.png
│
├── backend/                 # Java Backend Server
│   ├── ApiHandler.java      # Handles HTTP requests & JSON responses
│   ├── CampusGraph.java     # Graph data structure & algorithms (Dijkstra, BFS)
│   ├── JsonHelper.java      # Manual JSON parser/builder (no dependencies)
│   ├── LocationLoader.java  # Reads JSON files into the Graph
│   ├── NavigationServer.java# Main entry point, starts the HttpServer
│   ├── StaticFileHandler.java# Serves frontend files
│   ├── Dockerfile           # For containerized deployment (optional)
│   └── data/                # Graph Data
│       ├── locations.json   # Nodes (ID, Name, X, Y, Hours)
│       └── edges.json       # Edges (From, To, Distance in metres)
│
├── frontend/                # Web User Interface
│   ├── index.html           # Main UI layout
│   ├── app.js               # UI logic, API fetching, Speech API
│   ├── map.js               # SVG map rendering engine
│   └── style.css            # Styling (Mobile-first, responsive)
│
├── Images/
│   └── Screenshots/         # README images
│
└── qr-codes/
    └── generate-qr.html     # Standalone QR code generator tool
```

---

## 🚀 How to Run Locally

Since this project has a Java backend, it must be run on your local machine. 

### Prerequisites
- **Java JDK** installed (Java 11 or higher recommended).
- A command-line terminal.

### Step-by-Step Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/campus-navigation.git
   cd campus-navigation
   ```

2. **Compile the Java Backend:**
   Navigate into the `backend` folder and compile all `.java` files:
   ```bash
   cd backend
   javac *.java
   ```

3. **Start the Server:**
   Run the main server file:
   ```bash
   java NavigationServer
   ```

4. **Open the App:**
   Once the server starts, you will see a success message in your terminal.
   Open your web browser and go to:
   ```
   http://localhost:8080
   ```

5. **Generate QR Codes (Optional):**
   Simply double-click `qr-codes/generate-qr.html` to open it in your browser. Enter your deployed URL (or `http://localhost:8080`) and generate your printable QR sheets.

---

## 🔌 API Endpoints

If you want to interact with the backend directly, the Java server exposes the following REST API endpoints:

| Method | Endpoint | Description | Example |
|---|---|---|---|
| `GET` | `/api/health` | Server status & graph stats | `/api/health` |
| `GET` | `/api/locations` | Get all 36 locations | `/api/locations` |
| `GET` | `/api/location/{id}` | Get details of a specific location | `/api/location/library` |
| `GET` | `/api/path` | Find route between two locations | `/api/path?from=entrance&to=principal` |
| `GET` | `/api/path?...&algo=bfs` | Force route via BFS (fewest stops) | `/api/path?from=entrance&to=principal&algo=bfs` |

### Example JSON Response (`/api/path`):
```json
{
  "from": "entrance",
  "to": "principal",
  "algorithm": "dijkstra",
  "totalDistance": 265,
  "estimatedTimeMinutes": 4,
  "path": ["entrance", "junction1", "junction2", "junction4", "principal"],
  "steps": [
    {
      "step": 1,
      "instruction": "Start at Main Entrance and walk 50m towards Central Junction.",
      "distance": 50,
      "direction": "straight",
      "nodeId": "junction1",
      "nodeName": "Central Junction"
    }
  ]
}
```

---

## 🗺️ Data Management (Adding Locations)

To add or modify locations on the map:
1. Open `backend/data/locations.json`. Add a new node with `x` and `y` coordinates (Map size is `800x600`).
2. Open `backend/data/edges.json`. Add paths (edges) connecting your new node to existing ones. Ensure you specify the distance in metres.
3. Restart the Java server. The frontend will automatically render the new map!

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE). 
Built by 3rd Semester CSE students · GEC Chamarajanagar.