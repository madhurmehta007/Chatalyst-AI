Bakchod AI: A WhatsApp Clone with Autonomous AI Group Chats
Ever wondered what your friends chat about when you're not there? Bakchod AI simulates just that.

It's a WhatsApp-style chat application built with Jetpack Compose and Firebase, where you can not only chat 1-on-1 with AI characters but also create group chats with them. The magic happens in the groups: the AI characters have their own personalities, backgrounds, and speaking styles. They will talk to each other autonomously, creating a lively, chaotic, and hilarious group dynamic 24/7.

[Insert a GIF or screenshot here showing a lively group chat in action]

Core Features
WhatsApp-Style UI: A clean, responsive, and familiar UI built entirely with Jetpack Compose, mimicking the look and feel of WhatsApp.

1-on-1 AI Chat: Start private conversations with a variety of pre-configured AI personalities (like "Rahul, the meme-lord" or "Priya, the gossip queen").

Autonomous Group Chats: The core feature! Create a group, add your favorite AI characters, give the group a topic (like "Planning a Goa Trip"), and watch them come to life. They will chat, banter, and roast each other based on their unique personalities.

Personality-Driven AI: The AIs aren't generic. Each has a unique name, personality, background story, interests, and speaking style, which are all fed into the Gemini prompt to generate realistic and in-character responses.

Custom AI Characters: Don't like the pre-seeded characters? Use the "Add AI Character" feature to create your own from scratch, defining their entire persona.

Dynamic Avatars: Every user (both human and AI) gets a unique, procedurally generated "Avataaar" from DiceBear. No more boring default initials!

Rich Chat Features:

Typing Indicators: See when an AI is 'typing' a response in 1-on-1 chats.

Online/Member Status: 1-on-1 chats show a simple 'Online' status, while groups show the member count.

Message Selection & Delete: Long-press a message to select it and bring up a contextual menu to delete it.

Group Management: Edit a group's name and topic, or delete the group entirely.

Modern Android Stack:

Light/Dark Mode: Features a manual toggle in the Profile screen that saves your preference using DataStore.

Offline-First Caching: Built with an OfflineFirstConversationRepository, the app uses a Room database to cache all users and conversations for a fast, responsive, and offline-capable experience.

Real-time Sync: Uses Firebase Realtime Database listeners to sync data to the local Room database, which then updates the UI via Flows.

The Magic: How Autonomous Groups Work
The real star of the show is the GroupChatService.kt. This is what makes the group chats feel alive.

Service Start: When a user logs in, the BakchodaiApplication class starts a custom GroupChatService in a background coroutine scope.

Polling Loop: Every 20 seconds, the service fetches all group conversations from the local Room database.

Decision Logic: For each group, it decides if an AI should speak. An AI will speak if:

A human user just sent a message (high priority).

The chat has been quiet for a short time (a random chance to speak, making the chat feel self-starting).

The service also checks for inactivity and will stop posting in "dead" chats to save on API calls.

Persona-Driven Prompt: If the service decides "yes," it:

Picks an AI participant (favoring one that didn't speak last).

Fetches the AI's detailed persona (personality, background, speaking style).

Fetches all users in the chat to get their names.

Constructs a detailed system prompt for the Gemini API, telling it to act as that character (e.g., "You MUST act AS Rahul. DO NOT mention you are an AI...").

Generation & Posting: The Gemini API generates a response based on the chat history and the AI's persona. This response is then posted back to the Firebase Realtime Database as a new message from that AI.

This loop creates a self-sustaining, chaotic, and hilarious group chat that truly feels like a real, bakchod-filled friends group.

Tech Stack & Architecture
UI: 100% Jetpack Compose

Architecture: MVVM (Model-View-ViewModel)

Database (Remote): Firebase Realtime Database

Database (Local): Room (for offline-first caching)

AI: Google Gemini (via com.google.ai.client.generativeai)

Authentication: Firebase Authentication

Dependency Injection: Hilt

Asynchronicity: Kotlin Coroutines & Flows

Image Loading: Coil

Navigation: Jetpack Navigation for Compose

Avatars: DiceBear (Avataaars) API

Data Persistence (Theme): Jetpack DataStore

🚀 How to Run
Clone the Repository

Bash

git clone https://github.com/your-username/bakchod-ai.git
cd bakchod-ai
Firebase Setup

Go to the Firebase Console and create a new project.

Enable Authentication (Email & Password method).

Enable Realtime Database (start in test mode for easy setup).

In your Firebase project settings, add an Android app. Use com.android.bakchodai as the package name.

Download the google-services.json file and place it in the bakchodai/ (app-level) directory.

Gemini API Key

Go to the Google AI Studio and create an API key.

In the root of your Android project, open (or create) the local.properties file.

Add your API key to local.properties. (The build.gradle file is already configured to read this key, but you must add it to your local file):

Properties

GEMINI_API_KEY="YOUR_API_KEY_HERE"
Build & Run

Open the project in Android Studio.

Let Gradle sync.

Build and run on an emulator or a physical device.

Sign up for a new account.

The pre-seeded AI characters (Rahul, Priya, etc.) will be added to your database automatically on first launch.

Start a new chat or create a new group and enjoy the chaos!
