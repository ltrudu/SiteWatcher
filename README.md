# SiteWatcher

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-34%2B-brightgreen.svg)](https://android-arsenal.com/api?level=34)
[![License](https://img.shields.io/badge/License-Unlicense-blue.svg)](https://unlicense.org)

**SiteWatcher** is a powerful Android application that monitors websites for content changes and notifies you when changes exceed configurable thresholds. Perfect for tracking price changes, news updates, product availability, or any web content you care about.

## Features

### Core Monitoring
- **Website Tracking** - Monitor unlimited websites for content changes
- **Change Detection** - Configurable percentage threshold (1-99%) to filter noise
- **Multiple Comparison Modes**:
  - **Full HTML** - Compare complete page source
  - **Text Only** - Compare visible text content (ignores HTML tags)
  - **CSS Selector** - Monitor specific page elements only

### Smart Scheduling
- **Periodic Checks** - Set intervals from 15 minutes to 10 hours
- **Daily Schedule** - Check at a specific time each day
- **Day Selection** - Choose which days to monitor (weekdays, weekends, or custom)
- **Quick Presets** - One-tap selection for common schedules

### Notifications
- **Instant Alerts** - Get notified immediately when changes are detected
- **Configurable Actions** - Tap to open the app or go directly to the website
- **Smart Thresholds** - Only alert when changes exceed your set percentage

### Built-in Browser
- **Discover Sites** - Browse the web directly within the app
- **Quick Add** - One tap to start monitoring any page you visit
- **Search Integration** - Choose your preferred search engine (DuckDuckGo, Google, Bing, Qwant)

### Content Comparison
- **Diff Viewer** - Visual side-by-side comparison of changes
- **Color Coding** - Green for additions, red for removals
- **Statistics** - See exactly how many lines changed

### Backup & Restore
- **Export Sites** - Save your monitored sites to a JSON file
- **Import Sites** - Restore from backup or share configurations
- **Content Backups** - View and manage stored page snapshots

### Advanced Search
- **Boolean Operators** - Use AND, OR, NOT for precise filtering
- **Grouping** - Parentheses support for complex queries
- **Real-time** - Results update as you type (with smart debouncing)

### Additional Features
- **Check All** - Manually trigger checks for all sites at once
- **Duplicate Sites** - Quickly clone site configurations
- **Auto-Resume** - Monitoring continues after device restart
- **Network Options** - WiFi only, mobile data, or both
- **Dark Mode** - Full Material Design 3 theming support

## Screenshots

| Site List | Add/Edit Site | Browser Discovery |
|:---------:|:-------------:|:-----------------:|
| *Coming soon* | *Coming soon* | *Coming soon* |

| Diff Viewer | Settings | Search |
|:-----------:|:--------:|:------:|
| *Coming soon* | *Coming soon* | *Coming soon* |

## Quick Start

### Installation

1. **Download** the latest APK from [Releases](../../releases)
2. **Enable** installation from unknown sources if prompted
3. **Install** the app on your Android device

### Grant Permissions

When first launching the app, you'll be asked for:
- **Notifications** - Required to alert you of changes
- **Exact Alarms** - Required for precise scheduling

### Add Your First Site

1. Tap the **+** floating button
2. Enter the website URL or tap **Browse** to discover sites
3. Configure monitoring options:
   - **Name** (optional) - Custom display name
   - **Comparison Mode** - How to detect changes
   - **Schedule** - When to check
   - **Threshold** - Minimum change percentage to notify
4. Tap **Save**

### Monitor Changes

- Sites are checked automatically based on your schedule
- Pull down to see the latest status
- Long-press a site for quick actions (Edit, Check Now, View Diff, Delete)

## Configuration Guide

### Comparison Modes

| Mode | Best For | Description |
|------|----------|-------------|
| **Full HTML** | Technical changes | Detects any change including invisible elements |
| **Text Only** | Content updates | Ignores styling, focuses on readable text |
| **CSS Selector** | Specific elements | Monitor only matching elements (e.g., `.price`, `#stock-status`) |

### Schedule Types

| Type | Use Case | Example |
|------|----------|---------|
| **Periodic** | Frequent monitoring | Check every 30 minutes |
| **Specific Hour** | Daily updates | Check at 9:00 AM every day |

### Change Threshold

- **Low (1-10%)** - Detect minor changes, may include dynamic content
- **Medium (10-30%)** - Balance between sensitivity and noise
- **High (30%+)** - Only major content overhauls

### Search Query Examples

| Query | Matches |
|-------|---------|
| `news` | Sites containing "news" |
| `google AND news` | Sites with both "google" and "news" |
| `google OR bing` | Sites with either term |
| `NOT facebook` | Sites without "facebook" |
| `(google OR bing) AND news` | Complex grouping |

## Technical Details

### Requirements
- **Android 14** (API 34) or higher
- **Permissions**: Notifications, Exact Alarms

### Architecture
- **Pattern**: Single Activity with Navigation Component, MVVM with Repository
- **Database**: Room for persistent storage
- **Networking**: OkHttp + Jsoup for content fetching and parsing
- **Background**: AlarmManager with exact alarms

### Project Structure
```
com.ltrudu.sitewatcher/
├── data/
│   ├── dao/           # Room DAOs
│   ├── database/      # Room Database
│   ├── model/         # Entity classes
│   └── repository/    # Data access layer
├── ui/
│   ├── sitelist/      # Main site list
│   ├── addedit/       # Add/Edit forms
│   ├── browser/       # Built-in browser
│   ├── diff/          # Diff viewer
│   ├── backups/       # Backup manager
│   ├── settings/      # App settings
│   └── about/         # About screen
├── background/        # Schedulers and receivers
├── network/           # HTTP client and comparators
├── notification/      # Notification handling
└── util/              # Utilities and helpers
```

## Building from Source

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11+
- Android SDK 34+

### Build Steps

```bash
# Clone the repository
git clone https://github.com/YourUsername/SiteWatcher.git
cd SiteWatcher

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing configuration)
./gradlew assembleRelease
```

The APK will be in `SiteWatcher/build/outputs/apk/`

## Contributing

Contributions are welcome! Here's how you can help:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Guidelines
- Follow existing code style and patterns
- Use `strings.xml` for all user-facing text (no hardcoded strings)
- Test on multiple Android versions when possible
- Update documentation for new features

## Troubleshooting

### Sites not checking on schedule?
- Ensure **Exact Alarms** permission is granted in Settings
- Check that battery optimization is disabled for SiteWatcher
- Verify the site is enabled (toggle is on)

### Not receiving notifications?
- Grant **Notification** permission in Settings
- Check that notifications are enabled for the app in system settings
- Ensure the change exceeds your configured threshold

### High battery usage?
- Increase check intervals to reduce frequency
- Use WiFi-only mode to avoid mobile data checks
- Reduce the number of monitored sites

## License

This project is released into the public domain under the [Unlicense](LICENSE).

You are free to copy, modify, publish, use, compile, sell, or distribute this software for any purpose, commercial or non-commercial.

## Acknowledgments

- [OkHttp](https://square.github.io/okhttp/) - HTTP client
- [Jsoup](https://jsoup.org/) - HTML parser
- [Material Design 3](https://m3.material.io/) - UI components

---

Made with care for people who need to stay informed.

<p align="center">❤️ Made with <a href="https://claude.ai">Claude.ai</a> ❤️</p>

