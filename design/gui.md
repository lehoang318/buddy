# GUI
## Chat Screen
* Top bar
  * [top-left] Application name
  * Model Selection dropbox
  * [top-right] Settings icon
* Conversation area
* User input area
  * 1st row: user input text widget
  * 2nd row
    * [left-end] Attached icon
    * Web Search icon with two states: Online/Offline
    * [right-end] Send icon

## Settings Screen
### Top bar
* [top-left] Back icon
* Title: `Settings`

### LLM Provider panel
  * 1st row
    * [left] Provider Selection dropbox which supports the following providers
        * OpenRouter
        * Ollama Cloud
        * Anthopic
        * OpenAI
        * xAI
  * 2nd row
    * [left] API Key text widget (contents shall be masked by default)
    * Visible Toggle icon
    * [right] Refresh icon
* Model settings panel
  * 1st row: Default Model dropbox
  * next row: Temperature slider
    * Label: `Temperature`
    * Tooltip
```
0.0 – 0.3: factual answers, math, code, precise tasks
0.5 – 0.8: balanced chat, reasoning, general use (0.7 is a popular default)
0.9 – 1.2+: creative writing, brainstorming, storytelling
```
    * Value type: Float
    * Value range: [0.0;1.0]
    * Default: 0.7
  * next row: Top-p slider
    * Label: `Top-p`
    * Tooltip
```
0.1 – 0.5: more focused, deterministic output
0.7 – 0.95: good balance (0.9 is very common)
1.0: no restriction (consider all tokens)
```
    * Value type: Float
    * Value range: [0.0;1.0]
    * Default: 0.95
  * next row: Top-k slider (float range: 0.0 to 1.0)
    * Label: `Top-k`
    * Tooltip
```
1: greedy decoding (very deterministic)
40 – 100: common default in many open-source setups
Higher values: more diversity
```
    * Value type: Int
    * Value range: [1;100]
    * Default: 20
