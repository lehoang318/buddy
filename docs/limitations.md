# Buddy AI Assistant - Technical Limitations

This document outlines the current technical limitations and constraints of the Buddy application.

---

## Image Processing

### Current Implementation

| Aspect | Limitation |
|--------|------------|
| **Format Conversion** | All images are converted to JPEG format at 85% quality, regardless of original format |
| **Maximum Size** | Long edge capped at 1440px; aspect ratio is preserved |
| **Resampling Method** | Two-pass decoding: power-of-2 downsampling (`inSampleSize`) followed by `Bitmap.createScaledBitmap()` |
| **Quality Impact** | Minor quality loss may occur from the two-step resizing process |

### Known Issues

- **EXIF Orientation**: Images taken in portrait mode on some devices may appear rotated (no EXIF orientation handling)
- **Animated Images**: GIF and animated WebP are converted to static JPEG of the first frame only
- **Metadata Stripping**: All EXIF data, GPS coordinates, and camera information are removed during JPEG re-encoding

### Recommendations

- Use high-quality source images for best results
- Be aware that location data from photos is not preserved
- For animated content, consider using static images or alternative formats

---

## File Type Support

### Supported Text Files (Maximum 100KB)

| Category | Supported Extensions | Use Cases |
|----------|---------------------|-----------|
| **Plain Text** | `.txt`, `.log` | Raw text content, application/system logs |
| **Documentation** | `.md`, `.rst`, `.adoc`, `.asciidoc` | Markdown, reStructuredText, AsciiDoc documents |
| **Data/Structured** | `.json`, `.xml`, `.html` | Configuration files, data exchange, web documents |
| **Rich Text** | `.rtf` | Formatted text documents |

### Unsupported File Types

| Type | Examples | Reason | Workaround |
|------|----------|--------|------------|
| **PDF Documents** | `.pdf` | No built-in text extraction on Android | Convert to text first |
| **Binary Files** | Images, executables, archives | Only UTF-8 text encoding supported | Use text-based formats |
| **Spreadsheets** | `.csv`, `.xlsx`, `.ods` | Not in supported list | Export as `.txt` or `.json` |
| **Source Code** | `.java`, `.cpp`, `.ts`, `.go`, `.rs`, `.rb`, `.php`, `.swift`, `.kt`, `.scala` | Not in supported list | Rename to `.txt` or `.md` |
| **Executable Scripts** | `.sh`, `.bat`, `.ps1` | Not in supported list | Rename to `.txt` |

### Important Notes

- **Extension-Based Validation**: File type validation is based on file extension, not content inspection or MIME type sniffing
- **Size Limit**: Files larger than 100KB are rejected with an inline error message
- **Memory Usage**: File content is read entirely into memory before being sent to the LLM
- **Encoding**: Only UTF-8 encoding is supported; other encodings may cause display issues

---

## Attachment Constraints

### Current Limitations

| Feature | Status | Details |
|---------|--------|---------|
| **Single Attachment** | ✅ Implemented | One image OR one text file per message |
| **Multiple Attachments** | ❌ Not Supported | Cannot attach multiple files at once |
| **Batch Upload** | ❌ Not Supported | No multi-file selection |
| **Attachment Replacement** | ✅ Implemented | New attachment replaces previous one |
| **Text File Preview** | ❌ Not Available | Only filename shown as chip |
| **Drag & Drop** | ❌ Not Supported | Not available on Android |
| **Clipboard Paste** | ❌ Not Supported | Cannot paste images/files from clipboard |

### Attachment Workflow

1. **Select Attachment**: Tap the paperclip icon to choose from gallery or file browser
2. **Camera Capture**: Tap the camera icon to take a photo (images only)
3. **Review**: Attachment appears as a chip with filename
4. **Replace**: Selecting a new attachment automatically removes the previous one
5. **Send**: Attachment is included with your message

### Known Limitations

- No visual preview for text files (only filename displayed)
- No ability to attach multiple files in a single message
- No ability to attach both an image and a text file simultaneously
- No drag-and-drop functionality
- No clipboard paste support for attachments

---

## LLM Provider Limitations

### Reasoning (Extended Thinking)

| Provider | Reasoning Support | API Parameter |
|----------|-------------------|---------------|
| **OpenAI API Compatible** | ✅ Supported | `reasoning: {effort: "low"/"high"}` |

### Notes

- The reasoning toggle (💡 icon) in the chat input bar sends reasoning parameters to all OpenAI-compatible providers
- Providers that do not support reasoning will ignore the parameter
- Current reasoning effort levels: **Low** and **High**

### Provider Architecture

- All built-in and custom providers use the **OpenAI-compatible API** format
- No provider-specific client implementations are required
- Any endpoint supporting `/chat/completions` and `/models` works uniformly

---

## Platform Limitations

### Android-Specific Constraints

- **Minimum SDK**: Android 10.0 (API level 29) or later
- **Recommended**: Android 11.0 (API level 30) or later for best experience
- **Storage**: Limited by device storage capacity
- **Memory**: Large files may cause memory issues on devices with limited RAM
- **Background Restrictions**: Battery optimization may affect background tasks

### Permission Requirements

| Permission | Purpose | Required For |
|------------|---------|--------------|
| **Camera** | Photo capture | Image attachments via camera |
| **Storage** | File access | Selecting files from device |
| **Internet** | API calls | All AI and web search features |

---

## Security Considerations

### Data Privacy

- **Local Storage**: Conversations are stored locally on your device
- **API Transmission**: Messages are sent to your chosen AI provider
- **No Cloud Backup**: No automatic backup to external servers
- **API Key Security**: Keys stored locally; not transmitted to Buddy servers

### Recommendations

- Do not share API keys publicly
- Use strong, unique API keys from providers
- Be cautious when sharing sensitive information with AI models
- Regularly review and rotate API keys if compromised

---

## Future Considerations

### Potential Enhancements

- [ ] PDF text extraction support
- [ ] Spreadsheet support (CSV, XLSX)
- [ ] Multi-file attachment support
- [ ] Text file preview functionality
- [ ] Drag-and-drop support
- [ ] Clipboard paste for images
- [ ] EXIF orientation handling
- [ ] Animated image support
- [ ] Larger file size limits

---

**Last Updated**: v0.4.0

**Note**: These limitations are subject to change in future updates.
