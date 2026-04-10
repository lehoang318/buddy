# Supported Providers
## LLM
|Provider|Base URL|API Type|Notes|
|-|-|-|-|
|Ollama Cloud|https://ollama.com/v1|OpenAI-compatible|Ollama explicitly states it does not retain prompt or response data to ensure privacy and security.|
|OpenRouter|https://openrouter.ai/api/v1|OpenAI-compatible|Allows developers to access 400+ models from 60+ providers through a single endpoint.|
|xAI|https://api.x.ai/v1|OpenAI-compatible|This one is interesting!|
|Gemini|https://generativelanguage.googleapis.com/v1beta|Gemini API|Another stuff from Google.|
|OpenAI|https://api.openai.com/v1|OpenAI|I've never tried this one before 🤔|
|Anthropic|https://api.anthropic.com/v1|Anthropic native (Messages API)|Excellent for reasoning/coding, however, my budget does not allow me to test it :(|

## Web Search
|Provider|Base URL|Best For|
|-|-|-|
|Tavily|https://api.tavily.com|RAG pipelines & AI agents|
|Exa (ex-Metaphor)|https://api.exa.ai|Semantic/research-heavy apps|
|Brave Search|https://api.search.brave.com|Privacy-focused & cost-effective|
