🛰️ GEOSAFE – A Place to Be Safe

GEOSAFE is a community-driven mobile app that helps users stay aware of crime activity in their surroundings.
It allows users to report nearby incidents, visualize unsafe zones on an interactive OpenStreetMap, and receive alerts when entering high-risk areas.

⚙️ Features

🧭 Live Crime Map – Displays reported incidents on OpenStreetMap.
🚨 Instant Alerts – Warns users when entering unsafe areas.
📝 Crime Reporting – Users can quickly report crimes with location and details.
🔥 Heatmap View – Highlights danger-prone zones based on report density.

🧠 How It Works

The app continuously tracks the user’s location and displays real-time crime data stored locally on the device.
Users can contribute by reporting incidents, which are immediately reflected on the map.
When the user enters a high-risk zone, the app automatically sends an in-app alert to ensure awareness and safety.

🧩 Tech Stack
COMPONENT	TECHNOLOGY
Language -	Kotlin
IDE -	Android Studio
Map -	OpenStreetMap (via OSMDroid / MapLibre)
Database -	Local Storage (SQLite / Room Database)
Architecture -	MVVM (Model–View–ViewModel)

💻 Setting Up GEOSAFE Locally
1️⃣ Clone the Repository

Open a terminal and run:

git clone https://github.com/elatedvortex/G3OSAF3/tree/main

2️⃣ Open the Project in Android Studio

Launch Android Studio.

Click on “Open an existing project”.

Navigate to the folder you just cloned and select it.

3️⃣ Configure Dependencies

Make sure you have the latest Android SDK and Gradle installed.

Sync Gradle when prompted.

4️⃣ Run the App

Connect your Android device or start an emulator.

Click on Run ▶️ in Android Studio.

5️⃣ Test the Features

Allow location permissions.

View your current location on the map.

Tap to report a crime — it will appear instantly on the map.

Move around (or simulate movement in the emulator) to test alert notifications.
