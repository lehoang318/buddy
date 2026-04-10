# Limitations

## Image Resize

- All images are normalized to JPEG at 85% quality regardless of original format
- Long edge capped at 1440px; aspect ratio is preserved
- Two-pass decoding with `inSampleSize` (power-of-2 downsampling) for memory efficiency, followed by exact `Bitmap.createScaledBitmap()` resize — minor quality loss may occur from the two-step process
- No EXIF orientation handling — images taken in portrait mode on some devices may appear rotated
- Animated images (GIF, animated WebP) are converted to a static JPEG of the first frame only
- Original image metadata (EXIF, GPS, camera info) is stripped during JPEG re-encoding

## File Types

### Supported Text Files (max 100KB)

| Category | Extensions | Purpose |
|----------|-----------|---------|
| Plain text | `.txt`, `.log` | Raw text content, application/system logs |
| Documentation | `.md`, `.rst`, `.adoc`, `.asciidoc` | Markdown, reStructuredText, AsciiDoc documents |
| Data / Structured | `.json`, `.xml`, `.html` | Configuration files, data exchange, web documents |
| Rich text | `.rtf` | Formatted text documents |

### Excluded

- **PDF** — no text extraction capability on Android without external dependencies
- **Binary files** — only UTF-8 text encoding is supported
- **Spreadsheets** (`.csv`, `.xlsx`) — not currently in the supported list
- **Source code** (`.py`, `.js`, `.java`, etc.) — not currently in the supported list (use `.txt` or `.md` as workaround)

### Notes

- File type validation is based on file extension, not content inspection or MIME type sniffing
- Files larger than 100KB are rejected with an inline error message
- File content is read entirely into memory before being sent to the LLM

## Attachment Support

- **Single attachment per message** — either one image OR one text file, not both simultaneously
- Selecting a new attachment replaces the previous one
- No multi-file selection or batch upload
- No attachment preview for text files — only the filename is shown as a chip
- No drag-and-drop support
- No clipboard paste support for images or files
