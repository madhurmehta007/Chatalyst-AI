# 🤖 Chatalyst AI: A WhatsApp Clone with Autonomous AI Group Chats

> _Ever wondered what your friends chat about when you're not there?_  
> **Chatalyst AI** simulates just that.

A **WhatsApp-style chat application** built with **Jetpack Compose** and **Firebase**, where you can not only chat 1-on-1 with AI characters but also create **autonomous group chats** between them.  
The AIs have distinct **personalities, backstories, and speaking styles**, leading to **chaotic, hilarious, and lifelike** conversations 24/7.  

---

![App Screenshot or GIF Placeholder](https://github.com/your-username/bakchod-ai/assets/your-image-id)  
_(Insert a lively group chat screenshot or GIF here)_

---

## 🌟 Core Features

### 💬 WhatsApp-Style UI
- Clean, responsive, and familiar interface built entirely with **Jetpack Compose**.  
- Mimics the look and feel of WhatsApp for instant user familiarity.

### 👤 1-on-1 AI Chat
- Start private chats with pre-built AI personalities like:  
  - *Rahul — The Meme Lord*  
  - *Priya — The Gossip Queen*

### 🤯 Autonomous Group Chats (The Core Magic!)
- Create a group, add AI characters, set a topic (e.g., “Planning a Goa Trip”), and watch them banter autonomously.
- AI participants chat with each other based on their **defined personas** and group context.

### 🧠 Personality-Driven AI
- Each AI has:
  - A name
  - Background story
  - Personality traits
  - Speaking style  
- These attributes are used in prompts to **generate realistic, in-character responses** using **Google Gemini**.

### 🧑‍🎨 Custom AI Characters
- Don’t like the defaults?  
  Create your own AI character from scratch — define their **persona**, **interests**, and **tone**.

### 🧩 Dynamic Avatars
- Every user (human or AI) gets a **unique DiceBear Avataaar** — no boring initials!

---

## 💬 Rich Chat Features

- **Typing Indicators:** See when an AI is “typing” a response.  
- **Online/Member Status:**  
  - 1-on-1: “Online”  
  - Groups: “x Members Online”  
- **Message Actions:** Long-press to select or delete messages.  
- **Group Management:** Edit group name/topic or delete entirely.  

---

## 🧱 Modern Android Stack

| Layer | Technology |
|-------|-------------|
| **UI** | Jetpack Compose |
| **Architecture** | MVVM (Model–View–ViewModel) |
| **Remote DB** | Firebase Realtime Database |
| **Local DB** | Room (Offline-first caching) |
| **AI Engine** | Google Gemini (via `com.google.ai.client.generativeai`) |
| **Authentication** | Firebase Auth |
| **Dependency Injection** | Hilt |
| **Async** | Kotlin Coroutines & Flows |
| **Navigation** | Jetpack Navigation for Compose |
| **Images** | Coil |
| **Avatars** | DiceBear API |
| **Data Persistence (Theme)** | Jetpack DataStore |

---

## ⚙️ The Magic: How Autonomous Groups Work

The real star: **`GroupChatService.kt`**

### 🧩 Lifecycle
1. **Service Start:**  
   When the app launches, `BakchodaiApplication` starts `GroupChatService` in a background coroutine.

2. **Polling Loop:**  
   Every 20 seconds, the service checks all active groups in Room DB.

3. **Decision Logic:**  
   An AI decides to respond if:
   - A human just messaged (high priority)
   - The chat’s been quiet for a while (random chance)
   - The chat isn’t inactive (to save API calls)

4. **Prompt Construction:**  
   The service builds a persona-specific prompt:
   - Fetch AI’s persona, speaking style, and chat members.
   - Example instruction:  
     > “You MUST act AS Rahul. DO NOT mention you are an AI.”

5. **Response Generation:**  
   - Gemini generates a reply.
   - The message is posted to Firebase as if the AI sent it.

🎭 **Result:**  
A self-sustaining, chaotic, and hilarious group chat — that feels alive.

---

## 🧰 Tech Stack Overview

- **UI:** Jetpack Compose  
- **Architecture:** MVVM  
- **Database:** Room + Firebase Realtime DB  
- **AI:** Google Gemini  
- **Dependency Injection:** Hilt  
- **Offline Caching:** Room + Flow  
- **Image Loading:** Coil  
- **Avatars:** DiceBear Avataars  
- **DataStore:** For theme persistence  


