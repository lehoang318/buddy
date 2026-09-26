# Buddy AI Assistant - Frequently Asked Questions (FAQ)

## 📋 Table of Contents

- [Buddy AI Assistant - Frequently Asked Questions (FAQ)](#buddy-ai-assistant---frequently-asked-questions-faq)
  - [📋 Table of Contents](#-table-of-contents)
  - [🤖 What is Buddy?](#-what-is-buddy)
  - [🚀 Getting Started](#-getting-started)
    - [How do I set up Buddy for the first time?](#how-do-i-set-up-buddy-for-the-first-time)
    - [Where do I get an API key?](#where-do-i-get-an-api-key)
    - [What are LLM and Web Search Providers?](#what-are-llm-and-web-search-providers)
      - [What is an LLM Provider?](#what-is-an-llm-provider)
      - [What is a Web Search Provider?](#what-is-a-web-search-provider)
    - [What is an API key?](#what-is-an-api-key)
      - [What it looks like:](#what-it-looks-like)
      - [How it works:](#how-it-works)
      - [Where to find it:](#where-to-find-it)
      - [Keep it safe:](#keep-it-safe)
  - [💬 Using Buddy](#-using-buddy)
    - [How do I chat with Buddy?](#how-do-i-chat-with-buddy)
    - [How do I find or resume a past conversation?](#how-do-i-find-or-resume-a-past-conversation)
    - [Are my chats deleted automatically?](#are-my-chats-deleted-automatically)
    - [How do I copy a response or jump between messages?](#how-do-i-copy-a-response-or-jump-between-messages)
    - [What can I ask Buddy?](#what-can-i-ask-buddy)
    - [How do I use web search?](#how-do-i-use-web-search)
    - [Can I share files or images with Buddy?](#can-i-share-files-or-images-with-buddy)
    - [How do I preview HTML code?](#how-do-i-preview-html-code)
    - [How do I save a code block to a file?](#how-do-i-save-a-code-block-to-a-file)
    - [How do I change AI models?](#how-do-i-change-ai-models)
  - [⚙️ Providers \& Customization](#providers--customization)
    - [What are the AI parameters for?](#what-are-the-ai-parameters-for)
    - [How do I reset my settings?](#how-do-i-reset-my-settings)
  - [🔧 Troubleshooting](#-troubleshooting)
    - [Why won't Buddy connect to the AI?](#why-wont-buddy-connect-to-the-ai)
    - [Why is Buddy taking so long to respond?](#why-is-buddy-taking-so-long-to-respond)
    - [What does "offline" mean?](#what-does-offline-mean)
    - [My images aren't being processed properly?](#my-images-arent-being-processed-properly)
    - [Web search isn't working?](#web-search-isnt-working)
  - [📱 App Features](#-app-features)
    - [What permissions does Buddy need?](#what-permissions-does-buddy-need)
    - [Where are my conversations stored?](#where-are-my-conversations-stored)
    - [Can I use Buddy without internet?](#can-i-use-buddy-without-internet)
  - [🔒 Privacy \& Security](#-privacy--security)
    - [Is my data private?](#is-my-data-private)
    - [What happens to my API keys?](#what-happens-to-my-api-keys)
    - [Can I use Buddy for sensitive information?](#can-i-use-buddy-for-sensitive-information)
  - [📞 Support](#-support)
    - [How can I get help?](#how-can-i-get-help)
    - [How do I report bugs or suggest features?](#how-do-i-report-bugs-or-suggest-features)
    - [Is Buddy free to use?](#is-buddy-free-to-use)

---

## 🤖 What is Buddy?

<details>
  <summary>🤖 What is Buddy?</summary>

**Buddy** is your personal AI assistant that runs directly on your Android phone. It helps you with various tasks like answering questions, helping with writing, analyzing documents, searching the web for current information, and much more. All your conversations stay private on your device.

</details>

## 🚀 Getting Started

### How do I set up Buddy for the first time?

<details>
  <summary>🚀 How do I set up Buddy for the first time?</summary>

1. **Download and install** the Buddy APK on your Android phone (Android 10.0 or later)
2. **Open the app** and grant necessary permissions (camera if you want to use photo features)
3. **Go to Providers** (tap the Buddy logo in the top bar)
4. **Choose your AI provider** (like OpenAI, Anthropic, local models, etc.)
5. **Enter your API key** (you'll need to get this from your chosen provider)
6. **Select a model** and adjust settings if desired
7. **Tap the sync button** to connect and start chatting!

</details>

### Where do I get an API key?

<details>
  <summary>🔑 Where do I get an API key?</summary>

You'll need an API key from an AI provider. Popular options include:
- **OpenRouter** (400+ models from 60+ providers)
- **Ollama Cloud** (privacy-focused, no data retention)
- **SiliconFlow** (high-performance model inference)
- **Custom providers** (any OpenAI-compatible endpoint)

Visit your chosen provider's website to generate an API key.
</details>

### What are LLM and Web Search Providers?

<details>
  <summary>🧠 What are LLM and Web Search Providers?</summary>

#### What is an LLM Provider?
**LLM** stands for **Large Language Model**. Think of it as the "brain" of Buddy that understands and generates human-like text. Different providers offer different AI models:

- **OpenRouter**: Access to 400+ models from 60+ providers, great for experimentation
- **Ollama Cloud**: Privacy-focused with no data retention
- **Fireworks AI / Together AI / SiliconFlow**: Fast inference with open-source models
- **Custom providers**: Run AI models on your own server or use any OpenAI-compatible endpoint

Each provider has different models with different strengths - some are better at writing, others at coding, others at analysis.

#### What is a Web Search Provider?
**Web Search** helps Buddy find current, up-to-date information from the internet. When you ask "What's the weather today?" or "What are the latest news?", Buddy uses a web search provider to find recent information:

- **Tavily**: A popular web search API that gives fast, relevant results
- **Other providers**: Some services offer web search capabilities

Web search is separate from your AI provider - you need both working together for the best experience.
</details>

### What is an API key?

<details>
  <summary>🔑 What is an API key?</summary>

An **API key** is like a special password that lets Buddy securely connect to AI and web search services. Here's what you need to know:

#### What it looks like:
- Usually starts with `sk-` or similar prefix for AI providers
- For web search, might start with `tvly-` or other prefixes
- Long string of letters and numbers
- Example: `sk-123abc456def789ghi`

#### How it works:
- **Authentication**: Proves you're allowed to use the service
- **Billing**: The provider tracks your usage through your key
- **Security**: Only grants access to the specific services you pay for

#### Where to find it:
1. Go to your chosen provider's website (OpenRouter, Ollama Cloud, etc.)
2. Sign in to your account
3. Look for "API Keys", "Developer Keys", or "Account Settings"
4. Create a new key and copy it
5. **Important**: Never share your API key with others!

#### Keep it safe:
- Don't post it online or share it publicly
- Don't include it in emails or messages
- If you think it's been compromised, create a new one immediately
</details>

## 💬 Using Buddy

### How do I chat with Buddy?

<details>
  <summary>💬 How do I chat with Buddy?</summary>

Simply type your question or message in the text input field at the bottom of the screen and tap the send button. Buddy will respond with an AI-generated answer.
</details>

### How do I find or resume a past conversation?

<details>
  <summary>🕘 How do I find or resume a past conversation?</summary>

Conversations are saved automatically after every reply, so you can safely close the app at any time.

- **Resume**: the most recent chat reopens automatically when you start Buddy.
- **Past chats**: tap the Buddy logo in the top bar and choose **History**. Tap a row to reopen that conversation.
- **Find a chat**: filter by time (All / 7 / 30 days) or by topic tags; combine tag chips to narrow further (all selected tags must match).
- **Clean up**: select rows with the checkboxes to bulk-delete, or tap **New Chat** in the Buddy logo menu to start fresh (the current chat is saved first).
</details>

### Are my chats deleted automatically?

<details>
  <summary>🗑️ Are my chats deleted automatically?</summary>

Only if you turn it on. In **History**, enable the **Auto-delete** toggle to remove chats that haven't been interacted with for 30 days:

- If old chats exist when you enable it, Buddy asks for confirmation before deleting them.
- The purge also runs on app startup while the toggle is on.
- Actively used chats keep getting re-aged on every save, so they won't be deleted while you keep using them.
- Declining the confirmation keeps auto-delete enabled; it only skips deleting the old chats right away.
</details>

### How do I copy a response or jump between messages?

<details>
  <summary>📋 How do I copy a response or jump between messages?</summary>

- **Copy**: every message has a small copy icon next to its timestamp; code blocks have their own per-block copy button.
- **Jump**: when the conversation is longer than the screen, previous/next arrows appear in the input bar — tap to hop between your messages (long-press to jump to the very first/last).
</details>

### What can I ask Buddy?

<details>
  <summary>💬 What can I ask Buddy?</summary>

You can ask Buddy almost anything! Here are some examples:
- "Help me write an email to my boss"
- "Explain quantum computing in simple terms"
- "What's the weather like today?" (with web search enabled)
- "Help me debug this Python code"
- "Summarize this article for me" (paste a URL)
- "What does this document say?" (upload a file)
</details>

### How do I use web search?

<details>
  <summary>🌐 How do I use web search?</summary>

1. **Enable web search** via the `+` button at the bottom-left of the input bar (web search toggle in the add dialog)
2. Ask questions about current events, weather, or recent information
3. Buddy will search the web and provide up-to-date answers

Buddy decides per message whether a search is actually needed — casual messages ("hello", "thank you") skip the search entirely. For questions with multiple parts (e.g. "compare the price of X and Y"), Buddy may run up to 3 searches at once and combine the results. A pill under the response shows exactly what was searched.

**Note:** Web search requires a separate API key from Tavily or another provider.
</details>

### Can I share files or images with Buddy?

<details>
  <summary>📎 Can I share files or images with Buddy?</summary>

Yes! Buddy supports:
- **Images**: Take a photo or select from your gallery
- **Text files**: .txt, .md, .log, .rst, .adoc, .AsciiDoc, .rtf
- **Code files**: .json, .xml, .html, .py, .js
- **Web pages**: Paste any URL to analyze the content

Just tap the `+` button at the bottom-left of the input bar and choose "Attach file" or "Take photo".
</details>

### How do I preview HTML code?

<details>
  <summary>🌐 How do I preview HTML code?</summary>

HTML blocks don't have an in-app renderer — tapping the **Preview** button (eye icon on the code block) writes the HTML to a cache file and opens it in your **system browser** (via a `content://` URI). A real browser has a correct viewport, so full-page decks and viewport-sized layouts render as intended.

Notes:
- Markdown blocks also use the eye icon but preview **in-app** in a native dialog.
- If no browser is installed or the launch fails, Buddy shows a toast; the **copy** button is always the fallback.
- The page runs with the browser's normal privileges (JS and network work), but it comes from an opaque origin, so it can't read your cookies or site storage for other pages.

Deck-sized pages still look best on a phone screen if they avoid locking the page to the viewport:

> Mobile-safe HTML: use `min-height: 100dvh` (with `min-height: 100vh` as a fallback) and `width: 100%`, keep elements in normal document flow, and let the page scroll.
</details>

### How do I save a code block to a file?

<details>
  <summary>💾 How do I save a code block to a file?</summary>

Code blocks have a **download** button (shown once streaming finishes). Tapping it writes the code verbatim as a UTF-8 text file straight into your system **Downloads** folder — no permission prompt, no file picker.

- The file is named `buddy_code_<timestamp>.<ext>`, with the extension derived from the block's language tag (e.g. `kotlin` → `.kt`, `python` → `.py`, `html` → `.html`, `markdown` → `.md`); unknown or unlabeled blocks fall back to `.txt`.
- The timestamp keeps repeated saves from colliding, and the button briefly shows a check mark after a successful save.
- There's no in-app "open saved file" follow-up — open it any time from the Files app or Downloads folder.

</details>

### How do I change AI models?

<details>
  <summary>🔄 How do I change AI models?</summary>

1. Tap the model name in the top bar (next to the Buddy logo)
2. Pick a model from the list — the switch applies from your next message on
3. Some models support images (multimodal) - look for the image icon next to model names
</details>

## ⚙️ Providers & Customization

### What are the AI parameters for?

<details>
  <summary>⚙️ What are the AI parameters for?</summary>

- **Temperature** (0.0-1.0): Controls how creative vs. predictable Buddy's responses are
  - Low (0.0-0.3): Factual, precise answers
  - Medium (0.5-0.8): Balanced, good for most conversations  
  - High (0.9-1.2): Creative, good for brainstorming

- **Top-p** (0.1-1.0): Controls how focused vs. diverse responses are
  - Low: More focused and deterministic
  - High: More diverse and creative

- **Top-k** (1-100): Limits how many different words Buddy considers
  - Low: Very focused responses
  - High: More variety in responses
</details>

### How do I reset my settings?

<details>
  <summary>🔄 How do I reset my settings?</summary>

Go to Providers and manually clear the fields, or uninstall and reinstall the app.
</details>

## 🔧 Troubleshooting

### Why won't Buddy connect to the AI?

<details>
  <summary>🔧 Why won't Buddy connect to the AI?</summary>

Common reasons:
- **Invalid API key**: Double-check your API key is correct
- **Internet connection**: Make sure you have a working internet connection
- **Provider server issues**: The provider's servers might be down
- **Wrong model**: The selected model might not be available

Try these steps:
1. Check your API key
2. Test your internet connection
3. Try a different model
4. Contact your provider if issues persist
</details>

### Why is Buddy taking so long to respond?

<details>
  <summary>⏱️ Why is Buddy taking so long to respond?</summary>

Response times vary based on:
- **Model complexity**: Some models are larger and slower
- **Internet speed**: Slower connections take longer
- **Server load**: High demand can cause delays
- **Web search**: Searching the web takes extra time
</details>

### What does "offline" mean?

<details>
  <summary>📶 What does "offline" mean?</summary>

"Buddy is offline" means the app couldn't connect to your AI provider. Check your:
- API key
- Internet connection
- Provider status
</details>

### My images aren't being processed properly

<details>
  <summary>📷 My images aren't being processed properly?</summary>

Make sure:
- You granted camera permissions
- The image isn't too large (under 10MB recommended)
- The image is clear and well-lit
- You're using a model that supports images (multimodal)
</details>

### Web search isn't working

<details>
  <summary>🌐 Web search isn't working?</summary>

Web search requires:
- A separate web search API key (like Tavily)
- Internet connection
- Web search enabled in the add dialog
</details>

## 📱 App Features

### What permissions does Buddy need?

<details>
  <summary>📱 What permissions does Buddy need?</summary>

- **Camera**: For taking photos and analyzing images
- **Storage**: For accessing files and saving attachments
- **Internet**: For connecting to AI services and web search
</details>

### Where are my conversations stored?

<details>
  <summary>💾 Where are my conversations stored?</summary>

All conversations are stored locally on your device. Buddy doesn't send your data to external servers (except when connecting to your AI provider for responses).
</details>

### Can I use Buddy without internet?

<details>
  <summary>📶 Can I use Buddy without internet?</summary>

You need internet to connect to AI providers, but once a conversation is loaded, you can view it offline. Web search always requires internet.
</details>

## 🔒 Privacy & Security

### Is my data private?

<details>
  <summary>🔒 Is my data private?</summary>

Yes! Your conversations are stored locally on your device. Buddy only sends your messages to the AI provider when you ask for responses, and doesn't store your data on external servers.
</details>

### What happens to my API keys?

<details>
  <summary>🔑 What happens to my API keys?</summary>

API keys are stored securely on your device and are only used to connect to your chosen AI provider. Buddy doesn't share or transmit your keys to any third parties.
</details>

### Can I use Buddy for sensitive information?

<details>
  <summary>⚠️ Can I use Buddy for sensitive information?</summary>

While Buddy is designed for privacy, remember:
- AI providers may see your messages
- Don't share highly sensitive personal information
- Some providers may store conversations temporarily
</details>

## 📞 Support

### How can I get help?

<details>
  <summary>📞 How can I get help?</summary>

- Check this FAQ for common questions
- Contact the developer through the About screen
- Visit the project GitHub page for technical documentation
</details>

### How do I report bugs or suggest features?

<details>
  <summary>🐛 How do I report bugs or suggest features?</summary>

You can:
- Contact the developer via email (found in About screen)
- Create an issue on the GitHub repository
- Join the community discussions
</details>

### Is Buddy free to use?

<details>
  <summary>💰 Is Buddy free to use?</summary>

Buddy is free to download and use, but you'll need to pay for your own API keys from the AI providers you choose. The cost depends on how much you use the service.
</details>

---
