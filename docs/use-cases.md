# Buddy AI Assistant - Use Cases Guide

## Table of Contents

- [1. Settings Configuration](#1-settings-configuration)
- [2. Adding a Custom LLM Provider](#2-adding-a-custom-llm-provider)
- [3. Changing AI Models](#3-changing-ai-models)
  - [3.1 Switch to Another Model from Same Provider](#31-switch-to-another-model-from-same-provider)
  - [3.2 Switch to Model from Different Provider](#32-switch-to-model-from-different-provider)
- [4. Web Search Configuration](#4-web-search-configuration)
  - [4.1 Change Web Search Provider](#41-change-web-search-provider)
  - [4.2 Enable/Disable Web Search](#42-enabledisable-web-search)
- [5. First Time Startup](#5-first-time-startup)

---

## 1. Settings Configuration

<details>
  <summary>1. Settings Configuration</summary>

### Overview
Configure your AI provider, API keys, and model settings to get started with Buddy.

### Steps

#### Step 1: Access Settings
1. Open Buddy app
2. Tap the Buddy logo in the top bar to open the menu
3. Select "Settings" from the dropdown menu

![Settings menu screenshot placeholder]

#### Step 2: Choose Your AI Provider
1. In the "LLM Provider" section, tap the dropdown field
2. Select your preferred AI provider from the built-in list:
   - Fireworks AI
   - Ollama Cloud
   - OpenRouter
   - SiliconFlow
   - Together AI
3. Or tap "Add Provider..." to add a custom OpenAI-compatible provider

![Provider selection screenshot placeholder]

#### Step 3: Enter Your API Key
1. In the "API Key" field, enter your API key
2. API keys typically look like: `sk-123abc456def789ghi`
3. Tap the eye icon to show/hide your key for verification
4. Tap the sync button (🔄) to test the connection

![API key entry screenshot placeholder]

#### Step 4: Select Your Model
1. Once connected, available models will appear in the "Default Model" dropdown
2. Choose your preferred model
3. Models with image icons support image processing (multimodal)

![Model selection screenshot placeholder]

#### Step 5: Configure AI Parameters (Optional)
AI parameters are configured in a separate **Parameters** screen (accessible from the main menu via the Buddy logo icon). From Settings, you can return to chat, open the menu, and select "Parameters" to adjust:

1. Adjust the sliders for:
   - **Temperature**: Controls creativity (0.0 = factual, 1.0 = creative)
   - **Top-p**: Controls response diversity (0.1 = focused, 1.0 = diverse)
   - **Top-k**: Controls word variety (1 = very focused, 100 = more variety)

2. Use the info icon (i) for tooltips explaining each parameter

3. Parameters are saved automatically when you tap back

![AI parameters screenshot placeholder]

#### Step 6: Configure Web Search (Optional)
1. In the "Web Search Provider" section, select your provider:
   - **Exa** — Semantic search, content filtering
   - **LinkUp** — Agentic search, precise content retrieval
   - **Tavily** — AI-optimized, fast results
2. Enter your web search API key (if required)
3. Web search enables current information queries

![Web search configuration screenshot placeholder]

#### Step 7: Save Settings
1. Tap the back button to return to chat — model selection and web search settings are saved automatically
2. Buddy will use your chosen provider and model
3. You're now ready to start chatting!

![Save settings screenshot placeholder]

</details>

## 2. Adding a Custom LLM Provider

<details>
  <summary>2. Adding a Custom LLM Provider</summary>

### Overview
Add any OpenAI-compatible LLM provider that is not in the built-in list.

### When to Use This
- You have a self-hosted model (e.g., via Ollama, vLLM, LM Studio)
- Your provider is not in the built-in list
- You want to use a private or corporate endpoint

### Steps

#### Step 1: Open Settings
1. Tap the Buddy logo in the top bar
2. Select "Settings" from the menu

#### Step 2: Add Custom Provider
1. In the "LLM Provider" section, tap the dropdown
2. Scroll to the bottom and tap "Add Provider..."

#### Step 3: Enter Provider Details
1. **Name**: Enter a display name (e.g., "My Ollama Server")
2. **Base URL**: Enter the API endpoint (must be OpenAI-compatible, e.g., `https://my-server.com/v1`)
3. **API Key** (optional): Enter if your endpoint requires authentication

#### Step 4: Save and Connect
1. Tap "Add" — the provider appears in the dropdown
2. Select your new provider
3. Enter the API key if you didn't already
4. Tap the sync button (🔄) to fetch models and test the connection

#### Step 5: Select Model
1. Once connected, choose a model from the "Default Model" dropdown
2. Tap back to save

### Requirements
- The endpoint must support OpenAI-compatible `/chat/completions` and `/models` endpoints
- The endpoint must accept `Authorization: Bearer <api_key>` headers
- Streaming responses via Server-Sent Events (SSE) are supported

</details>

## 3. Changing AI Models

* Tips
- **Multimodal Models**: Image icons next to models' names indicate multimodal capability. Grey color means that multimodal is unsupported!
- **Model Capabilities**: Some models are better at specific tasks (writing, coding, analysis)
- **Response Quality**: Try different models to find your preferred response style

### 3.1 Switch to Another Model from Same Provider

<details>
  <summary>3.1 Switch to Another Model from Same Provider</summary>

### Overview
Change to a different AI model while keeping the same AI provider.

### When to Use This
- You want to try a different model from the same provider
- You need a model with different capabilities (e.g., switch from text-only to image-capable)
- You want to compare responses from different models

### Steps

#### Method 1: Using Chat Screen Dropdown (Quick Switch)

1. **Open Chat Screen**
   - If not already on the chat screen, tap back button from any other screen to return to chat screen

2. **Access Model Dropdown**
   - Tap the dropdown arrow next to the current model name in the top bar
   - This shows all available models from your current provider

3. **Select New Model**
   - Scroll through the list of available models
   - Tap the model you want to switch to

4. **Confirmation**
   - The model name in the top bar updates immediately
   - A brief loading indicator may appear
   - You're now using the new model for all subsequent messages

![Chat screen model switch screenshot placeholder]

#### Method 2: Using Settings (For Provider-Specific Settings)

1. **Go to Settings**
   - Tap the Buddy logo → "Settings"

2. **Change Model**
   - In the "Default Model" dropdown, select a different model
   - This may show different parameter options depending on the model

3. **Adjust Parameters (Optional)**
   - Different models may have different optimal settings
   - Adjust temperature, top-p, and top-k as needed

4. **Save and Test**
   - Tap back to save
   - Send a test message to verify the new model works

![Settings model change screenshot placeholder]

</details>

### 3.2 Switch to Model from Different Provider

<details>
  <summary>3.2 Switch to Model from Different Provider</summary>

### Overview
Change to a completely different AI provider and model.

### When to Use This
- Your current provider isn't meeting your needs
- You want to try a different AI service entirely
- You have access to multiple providers and want to compare them

### Steps

#### Step 1: Go to Settings
1. Tap the Buddy logo in the top bar
2. Select "Settings" from the menu

#### Step 2: Change AI Provider
1. In the "LLM Provider" section, tap the dropdown
2. Select a different provider from the list
3. The available models will update based on the new provider

#### Step 3: Enter New API Key
1. If the new provider requires a different API key, enter it in the "API Key" field
2. Tap the sync button (🔄) to test the connection
3. Wait for the connection to be verified

#### Step 4: Select New Model
1. Once connected, choose a model from the "Default Model" dropdown
2. Note that different providers have different model names and capabilities

#### Step 5: Configure New Provider Settings
1. Adjust AI parameters as needed (they may work differently with different providers)
2. Some providers may have specific recommendations for optimal settings

#### Step 6: Test the New Provider
1. Tap back to save your settings
2. Send a test message to verify everything works
3. Compare responses with your previous provider if desired

### Provider Comparison Tips

#### OpenRouter
- **Strengths**: 400+ models from 60+ providers, great for experimentation
- **Best for**: Model experimentation, accessing niche models

#### Ollama Cloud
- **Strengths**: Privacy-focused, no data retention
- **Best for**: Privacy-conscious users, local model deployment

#### Fireworks AI / Together AI / SiliconFlow
- **Strengths**: Fast inference, open-source models, research-focused
- **Best for**: Coding, development, cost-effective inference

#### Custom Providers
- **Strengths**: Any OpenAI-compatible endpoint
- **Requirements**: Name, base URL, and optional API key
- **Best for**: Self-hosted models, private endpoints, specialized services

### Troubleshooting Provider Switches

#### Connection Issues
- Double-check your API key for the new provider
- Verify you have internet connection
- Some providers may have rate limits or maintenance periods
- For custom providers, ensure the base URL ends with `/v1` and supports `/chat/completions`

#### Model Not Available
- Not all models are available in all regions
- Some models may require specific API key permissions
- Try a different model or contact provider support

#### Parameter Differences
- Different providers may interpret parameters differently
- Start with default settings and adjust based on results
- Some providers may have recommended settings

</details>

## 4. Web Search Configuration

### 4.1 Change Web Search Provider

<details>
  <summary>4.1 Change Web Search Provider</summary>

### Overview
Switch between different web search services to find current information.

### When to Use This
- Your current web search provider isn't giving good results
- You want to try a different web search service
- You have access to multiple web search providers

### Steps

#### Step 1: Access Settings
1. Tap the Buddy logo in the top bar
2. Select "Settings" from the menu

#### Step 2: Navigate to Web Search Section
1. Scroll down to the "Web Search Provider" section
2. This section may be collapsed initially

#### Step 3: Select New Provider
1. Tap the dropdown in the "Web Search Provider" field
2. Choose from available providers:
   - **Exa** — Semantic search, content filtering
   - **LinkUp** — Agentic search, precise content retrieval
   - **Tavily** — AI-optimized, fast results

#### Step 4: Enter New API Key (if needed)
1. If the new provider requires an API key, enter it in the "API Key" field
2. API keys vary by provider (e.g., `tvly-` for Tavily)
3. Tap the sync button to test the connection

#### Step 5: Test Web Search
1. Go back to the chat screen
2. Enable web search (globe icon in top bar)
3. Ask a current events question to test the new provider
4. Verify you're getting up-to-date information

### Provider Comparison

#### Exa
- **Strengths**: Powerful semantic search, content filtering
- **Best for**: Research, finding specific content
- **Cost**: Competitive pricing

#### LinkUp
- **Strengths**: Agentic search, precise content retrieval
- **Best for**: Privacy-focused users, targeted searches
- **Cost**: Competitive pricing

#### Tavily
- **Strengths**: Fast, reliable, good coverage, optimized for AI
- **Best for**: General web search needs
- **Cost**: Competitive pricing

### Web Search Best Practices

#### When to Use Web Search
- Questions about current events
- Weather queries
- Recent news or information
- Factual questions that may have changed

#### When Not to Use Web Search
- General knowledge questions (AI models already know these)
- Creative writing tasks
- Personal advice or opinions
- Questions about the past (AI models have historical knowledge)

</details>

### 4.2 Enable/Disable Web Search

<details>
  <summary>4.2 Enable/Disable Web Search</summary>

### Overview
Toggle web search on or off to control whether Buddy searches the internet for answers.

### When to Use This
- You want current information (enable web search)
- You want to save API costs (disable web search)
- You're asking about historical information (disable web search)
- You want faster responses (disable web search)

### Steps

#### Method 1: Using Top Bar Toggle (Quick Switch)

1. **Locate Web Search Icon**
   - Look at the top bar of the chat screen
   - Find the globe icon (🌐) on the right side

2. **Toggle Web Search**
   - **Tap once**: Enables web search
   - **Tap again**: Disables web search

3. **Visual Feedback**
   - **Enabled**: Globe icon is filled/colorful
   - **Disabled**: Globe icon is outlined/gray

4. **Test the Setting**
   - Ask a question that benefits from web search
   - If enabled, Buddy will search the web for current information
   - If disabled, Buddy will use only its existing knowledge

![Web search toggle screenshot placeholder]

#### Method 2: Using Settings (Permanent Configuration)

1. **Go to Settings**
   - Tap the Buddy logo → "Settings"

2. **Configure Web Search**
   - In the "Web Search Provider" section, select your provider
   - Enter API key if required
   - This sets up web search capability

3. **Enable/Disable via Top Bar**
   - Use the top bar toggle for temporary changes
   - Settings provide the underlying capability

### Understanding Web Search Behavior

#### When Web Search is Enabled
- **Current Information**: Buddy can answer questions about recent events
- **Weather**: Can provide current weather information
- **News**: Can access latest news and updates
- **Verification**: Can verify facts against current sources

#### When Web Search is Disabled
- **Existing Knowledge**: Buddy uses its training data (up to 2024 or earlier)
- **Faster Responses**: No internet lookup needed
- **No Current Info**: Cannot answer questions about very recent events
- **Lower Cost**: No web search API usage

### Cost Considerations

#### Web Search Costs
- **API Usage**: Each web search uses your web search API quota
- **Per Request**: Typically charged per search or per result
- **Monthly Limits**: Some providers have monthly usage limits

#### When to Disable Web Search
- **Cost Saving**: If you're on a tight budget
- **Simple Questions**: For questions that don't need current info
- **Testing**: When testing AI capabilities without web influence
- **Offline Mode**: When you don't need current information

#### When to Enable Web Search
- **Current Events**: For questions about recent news or developments
- **Weather**: For current weather information
- **Verification**: To verify facts against current sources
- **Research**: For comprehensive research tasks

</details>

## 5. First Time Startup

<details>
  <summary>5. First Time Startup</summary>

### Overview
What to expect and how to proceed when opening Buddy for the first time.

### First Time Experience

#### Initial Screen
When you first open Buddy, you'll see:

1. **Welcome Message**: Brief introduction to Buddy
2. **Chat Interface**: Basic chat screen ready for use
3. **Limited Functionality**: Some features may be disabled
4. **Prompt to Configure**: Visual cues to guide you to settings

![First time startup screenshot placeholder]

#### Initial Limitations
When no providers are configured:

1. **Offline Mode**: Buddy can still chat but with limited capabilities
2. **No Model Selection**: Default to basic functionality
3. **Web Search Disabled**: Cannot perform web searches
4. **File Attachments**: May have limited support

#### What You'll See
- "Buddy is offline" indicator in the top bar
- Limited model options (if any)
- Disabled web search icon
- Settings prompt or icon

### Step-by-Step First Time Setup

#### Step 1: Recognize the Need for Configuration
- Look for "Buddy is offline" message
- Notice disabled features
- Find the settings prompt

#### Step 2: Access Settings
- Tap the Buddy logo in the top bar
- Select "Settings" from the menu
- This is your first configuration step

#### Step 3: Configure Your First Provider
- Choose an AI provider (OpenAI recommended for beginners)
- Enter your API key
- Select your first model

#### Step 4: Test Basic Functionality
- Go back to chat screen
- Send a simple test message
- Verify Buddy responds correctly

#### Step 5: Optional Web Search Setup
- If you want current information capability
- Configure web search provider
- Enter web search API key

#### Step 6: Explore Features
- Try file attachments
- Test image capabilities (if available)
- Experiment with different questions

### Troubleshooting First Time Issues

#### Common Problems

##### "Buddy is offline" Persists
- **Check API Key**: Verify you entered it correctly
- **Internet Connection**: Ensure you have working internet
- **Provider Status**: The provider's servers might be down
- **API Key Validity**: Your key might be expired or invalid

##### No Models Available
- **Provider Connection**: Verify the provider is connected
- **API Key Permissions**: Your key might not have model access
- **Provider Issues**: The provider's API might be experiencing issues
- **Try Different Provider**: Some providers work better than others

##### Web Search Not Working
- **API Key**: Verify your web search API key
- **Provider Selection**: Ensure you selected a valid provider
- **Internet Connection**: Web search requires internet
- **Provider Limits**: You might have reached usage limits

##### App Crashes or Freezes
- **Restart App**: Close and reopen Buddy
- **Check Permissions**: Ensure all required permissions are granted
- **Update App**: Make sure you have the latest version
- **Device Compatibility**: Verify your device meets requirements

#### Solutions

##### Verify API Key
1. Go to your provider's website
2. Check your API key in account settings
3. Copy and paste carefully (avoid extra spaces)
4. Test with a simple key first

##### Test Internet Connection
1. Open a web browser
2. Visit any website to verify connectivity
3. Check if other apps can access the internet
4. Try connecting to different networks if needed

##### Try Alternative Providers
1. Start with OpenAI (most popular and reliable)
2. If issues persist, try Anthropic
3. Consider local providers if you have technical setup
4. Contact provider support if problems continue

##### Contact Support
1. Use the "About" screen to find contact information
2. Check the GitHub repository for issues and solutions
3. Look for community forums or discussion groups
4. Provide detailed information about your issue

### First Time Tips

#### Start Simple
- Begin with basic questions to test functionality
- Gradually try more complex queries
- Test different features one at a time

#### Keep Notes
- Record which providers and models work best for you
- Note any settings that produce good results
- Track any issues and their solutions

#### Explore Gradually
- Don't try to configure everything at once
- Master basic chat before exploring advanced features
- Take time to understand each setting's impact

#### Backup Your Settings
- If you find good configurations, note them down
- Some providers may have different optimal settings
- You can always reset and start over

### What to Expect After Setup

#### Normal Operation
- Buddy responds to your messages
- Model selection works as expected
- Web search functions when enabled
- File attachments work properly

#### Performance Indicators
- Response times vary by model and provider
- Web search adds slight delay when enabled
- Image processing may take longer
- Complex queries may take more time

#### Quality Expectations
- Response quality depends on your chosen model
- Different providers produce different styles
- Web search results depend on the provider's capabilities
- Your questions and prompts affect results

</details>

---

**Note**: Screenshots will be added to illustrate each step. The placeholders indicate where visual guidance will be inserted to help users understand each process better.

**Tip**: When adding screenshots, consider showing:
- Before and after states for toggles
- Dropdown menus expanded
- Error messages and their solutions
- Success indicators and confirmations
