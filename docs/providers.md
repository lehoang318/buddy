# Buddy AI Assistant - Supported Providers

This document provides detailed information about all supported AI and web search providers, including their characteristics, strengths, and use cases.

---

## LLM (Large Language Model) Providers

Buddy supports OpenAI-compatible LLM providers, giving you flexibility to choose the best model for your needs.

| Provider | Base URL | Description | Privacy Protection | Status |
|----------|----------|-------------|--------|--------|
| **Fireworks AI** | `https://api.fireworks.ai/inference/v1` | Fast inference, open-source models | ✅ SOC 2<br>✅ DPA<br>✅ No Training (API) | ❌ Untested |
| **Together AI** | `https://api.together.xyz/v1` | Research-focused, fast inference |  ✅ SOC 2 (Type II)<br>✅ DPA<br>✅ No Training (API) | ❌ Untested |
| **Ollama Cloud** | `https://ollama.com/v1/` | Privacy-focused, no data retention | ❌ SOC 2<br>❌ DPA<br>✅ No Training (API) | ✅ Tested |
| **OpenRouter** | `https://openrouter.ai/api/v1` | 400+ models from 60+ providers | ❌ SOC 2<br>❓ DPA<br>❓ No Training (API) | ✅ Tested |
| **SiliconFlow** | `https://api.siliconflow.com/v1` | High-performance model inference | ❌ SOC 2<br>❓ DPA<br>❓ No Training (API) | ✅ Tested |

Buddy also supports **custom OpenAI-compatible providers** via the Settings screen. You can add any provider that exposes an OpenAI-compatible chat completions API by providing its name, base URL, and API key.

### Architecture

All LLM providers in Buddy use the **OpenAI-compatible API** format. This means:
- Any provider with an OpenAI-compatible `/chat/completions` endpoint works out of the box
- No provider-specific client code is required
- Models are fetched dynamically from the provider's `/models` endpoint
- Features like streaming, reasoning effort, and multimodal detection work uniformly across all providers

---

## Web Search Providers

Web search capabilities allow Buddy to access current, up-to-date information from the internet.

| Provider | Base URL | Description | Status |
|----------|----------|-------------|--------|
| **Exa** | `https://api.exa.ai` | Semantic search, content filtering | ✅ Tested |
| **LinkUp** | `https://api.linkup.so` | Agentic search, precise content retrieval | ✅ Tested |
| **Tavily** | `https://api.tavily.com` | AI-optimized, fast results | ✅ Tested |

---

## Configuration Guide

### Setting Up LLM Providers

1. **Obtain API Key**: Sign up with your chosen provider and generate an API key
2. **Select Provider**: In Buddy settings, choose your preferred provider
3. **Enter API Key**: Input your API key in the settings
4. **Test Connection**: Use the sync button to verify connection
5. **Select Model**: Choose your preferred model from available options

### Setting Up Web Search

1. **Choose Provider**: Select from Exa, LinkUp, or Tavily
2. **Get API Key**: Sign up with your chosen provider
3. **Configure**: Enter API key in Buddy web search settings
4. **Enable**: Toggle web search in the chat interface

### Adding Custom LLM Providers

1. Open Settings and tap "Add Provider..." below the LLM provider dropdown
2. Enter a **name** for your provider (e.g., "My Local LLM")
3. Enter the **base URL** (must be OpenAI-compatible, e.g., `https://api.example.com/v1`)
4. Optionally enter an **API Key**
5. Tap "Add" — the provider will appear in the dropdown and persist across sessions

### API Key Storage

- **Per-provider keys**: API keys are stored per-provider, so switching providers automatically loads the correct key
- **Built-in provider keys**: If a built-in provider has a pre-configured API key in `providers.xml`, it is used as fallback
- **Custom provider keys**: Saved automatically when you connect a custom provider
- **Web search keys**: Stored separately per web search provider

---

## Security & Privacy

### API Key Security
- All API keys are stored locally on your device
- Keys are never transmitted to Buddy servers
- Use password field to hide keys when entering
- Rotate keys regularly if compromised

### Data Privacy
- **LLM Providers**: Your messages are sent to the provider you choose
- **Web Search**: Search queries are sent to the web search provider
- **Buddy App**: Your conversations or API usage may be stored locally but shall never leave the device
- **Recommendation**: Review each provider's privacy policy before use

### Best Practices
- Keep API keys secure and private
- Monitor your API usage and costs
- Review provider terms of service
- Consider privacy implications for your use case
