# Functional View
## Startup Sequence
* Chat Screen shall be displayed
* Scenario #1: 1st time startup or if there is no saved LLM Provider => Offline mode
  * All chat widgets such as Send, Attachment icons, ... are disabled
  * Model Selection dropbox is empty

* Scenario #2: if there is a valid LLM Provider saved from previous sessions => Online mode
  * In Model Selection dropbox, default Model is selected based on saved Settings from previous sessions
  * All chat widgets such as Send, Attachment icons, ... are enabled and ready

## Switching from Chat Screen to Settings Screen
* Trigger event: user clicks on Settings icon on Chat Screen
* Scenario #1: 1st time startup or if there is no saved LLM Provider
  * All fields are blank
  * Refresh icon (on the right of Base URL Text Widget) is disabled
  * After user provides both Base URL and API Key, Refresh icon shall be enabled
  * When user click on Refresh icon, application shall try to connect to LLM Provider
    * If success, application shall get all available models which support Chat Completion then update Default Model dropbox
      * By default, the first available model which support Chat Completion shall be selected in the dropbox
      * LLM Provider information shall be stored in application persistent data
    * If failed, an error dialog shall be displayed to inform user
      * Refresh icon shall be disabled to force user to update Base URL/API Key
* Scenario #2: if there is a valid LLM Provider saved from previous sessions
  * Saved Base URL is displayed
  * Saved API Key is displayed but masked for security purposes
    * API Key masking state shall be toggled when user clicks on Visible Toggle icon at the right-end of the text widget
  * Default Model dropbox is filled and saved default model is selected
  * Refresh icon is enabled
  * When user clicks on Refresh icon, application shall try to connect to LLM Provider whether Base URL/API Key are changed or not
    * If success, application shall override LLM Provider information in persistent data
    * If failed, the persisted LLM Provider information shall be kept

## Switching from Settings Screen to Chat Screen
* Trigger event: user clicks on back arrow or perform sweep back gesture
* Scenario #1: user changed Base URL or API Key then go back to Chat Screen without clicking on Refresh icon
  * Changes are discarded
* Scenario #2: user changed default model then go back to Chat Screen
  * Selected LLM Model shall be updated and displayed on Chat Screen
* Scenario #3: user changed Base URL or API Key then clicking on Refresh icon however connection is failed
  * Changes are discarded
