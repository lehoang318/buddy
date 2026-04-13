# Buddy AI Assistant - Supported Providers

This document provides detailed information about all supported AI and web search providers, including their characteristics, strengths, and use cases.

---

## 🧠 LLM (Large Language Model) Providers

Buddy supports multiple LLM providers, giving you flexibility to choose the best model for your needs.

| Provider | Base URL | API Type | Description | Status |
|----------|----------|----------|--------------------|--------|
| **Ollama Cloud** | `https://ollama.com/v1` | OpenAI-compatible | Privacy-focused, no data retention | ✅ Tested |
| **OpenRouter** | `https://openrouter.ai/api/v1` | OpenAI-compatible | 400+ models from 60+ providers, experimentation | ✅ Tested |
| **Gemini** | `https://generativelanguage.googleapis.com/v1beta` | Gemini API | Google's multimodal models, strong reasoning | ✅ Tested |
| **Anthropic** | `https://api.anthropic.com/v1` | Anthropic Messages API | Excellent reasoning, coding, safety-focused | ⚠️ Planned |
| **OpenAI** | `https://api.openai.com/v1` | OpenAI-native | Industry standard, GPT-4 models, reliable | ⚠️ Planned |
| **xAI (Grok)** | `https://api.x.ai/v1` | OpenAI-compatible | Real-time info, unique perspective | ⚠️ Planned |

---

## 🔍 Web Search Providers

Web search capabilities allow Buddy to access current, up-to-date information from the internet.

| Provider | Base URL | Description | Status |
|----------|----------|-------------|--------|
| **Tavily** | `https://api.tavily.com` | AI agents, RAG pipelines - AI-optimized, fast results, content filtering | ✅ Tested |
| **Brave Search** | `https://api.search.brave.com` | Privacy-focused search - Privacy-first, cost-effective, comprehensive index | ⚠️ Planned |
| **Exa** | `https://api.exa.ai` | Semantic search, research - Powerful semantic search, content filtering | ⚠️ Planned |

---

## 📋 Configuration Guide

### Setting Up LLM Providers

1. **Obtain API Key**: Sign up with your chosen provider and generate an API key
2. **Select Provider**: In Buddy settings, choose your preferred provider
3. **Enter API Key**: Input your API key in the settings
4. **Test Connection**: Use the sync button to verify connection
5. **Select Model**: Choose your preferred model from available options

### Setting Up Web Search

1. **Choose Provider**: Select from Tavily, Exa, or Brave Search
2. **Get API Key**: Sign up with your chosen provider
3. **Configure**: Enter API key in Buddy web search settings
4. **Enable**: Toggle web search in the chat interface

### Provider Selection Tips

| Use Case | Recommended Provider(s) |
|----------|------------------------|
| **Privacy-focused** | Ollama Cloud, Brave Search |
| **Model experimentation** | OpenRouter |
| **Current events** | xAI, Tavily |
| **Coding/development** | Anthropic, OpenAI |
| **Cost-effective** | OpenRouter, Brave Search |
| **Production use** | OpenAI, Anthropic |
| **Research** | Exa, Gemini |

---

## 🔐 Security & Privacy

### API Key Security
- All API keys are stored locally on your device
- Keys are never transmitted to Buddy servers
- Use password field to hide keys when entering
- Rotate keys regularly if compromised

### Data Privacy
- **LLM Providers**: Your messages are sent to the provider you choose
- **Web Search**: Search queries are sent to the web search provider
- **Buddy App**: Does not store or log your conversations or API usage
- **Recommendation**: Review each provider's privacy policy before use

### Best Practices
- Use strong, unique API keys
- Keep API keys secure and private
- Monitor your API usage and costs
- Review provider terms of service
- Consider privacy implications for your use case
