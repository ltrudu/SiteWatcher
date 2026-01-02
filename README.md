# SiteWatcher

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-34%2B-brightgreen.svg)](https://android-arsenal.com/api?level=34)
[![License](https://img.shields.io/badge/License-Unlicense-blue.svg)](https://unlicense.org)

**SiteWatcher** is a powerful Android application that monitors websites for content changes and notifies you when changes exceed configurable thresholds. Perfect for tracking price changes, news updates, product availability, or any web content you care about.

## Features

### Core Monitoring
- **Website Tracking** - Monitor unlimited websites for content changes
- **Change Detection** - Configurable percentage threshold (1-99%) to filter noise
- **Quick View Changes** - Tap the percentage badge to instantly view changes
- **Multiple Comparison Modes**:
  - **Full HTML** - Compare complete page source
  - **Text Only** - Compare visible text content (ignores HTML tags)
  - **CSS Selector** - Monitor specific page elements with include/exclude filtering

### Fetch Modes
- **Static (Fast)** - Quick HTML fetching without JavaScript execution
- **JavaScript (Dynamic)** - WebView-based fetching with full JavaScript support
  - Required for Single Page Applications (SPAs), AJAX content, and dynamic pages
  - Configurable page load delay (0-10 seconds, default: 5s)
  - Configurable post-action delay (0-10 seconds, default: 5s)

### Auto-Click Actions
Automate page interactions before content capture - perfect for dismissing cookie consent dialogs, closing popups, or navigating to specific content.

#### Action Types
- **Click Element** - Click elements using CSS selectors
- **Wait** - Pause for 1-60 seconds between actions
- **Tap at Coordinates** - Tap at specific screen positions (for iframes and shadow DOM elements)

#### Built-in Click Patterns
Pre-configured patterns for common cookie consent frameworks:
- **Major Frameworks**: OneTrust, Cookiebot, TrustArc, Quantcast, Didomi, CookieYes, Termly, Osano, CookieFirst, Borlabs
- **Generic Patterns**: Language-specific reject buttons (English, French, German, Spanish)
- **Site-Specific**: Bandcamp and other popular sites

#### Interactive Element Pickers
- **CSS Selector Picker** - Tap elements visually to generate selectors
  - Context-aware instructions: shows "monitor" for include mode, "exclude" for exclude mode
- **Coordinate Picker** - Tap anywhere to set exact tap positions with crosshair preview
- **Action Tester** - Test and preview complete action sequences before saving
- **Action Execution in Pickers** - Auto-click actions are executed before element selection to ensure accurate page state
- **Real-Time Countdown** - Visual countdown (X.X seconds) while waiting for page load or after actions

#### Actions Management
- **Drag-to-Reorder** - Arrange action execution order by dragging
- **Enable/Disable** - Toggle individual actions without deleting
- **FAB Speed Dial** - Quick menu to add Standard Action, Custom Action, or Sleep
- **Tap-to-Edit** - Tap any action to edit its properties

### Smart Scheduling (Multi-Schedule System)
Create multiple schedules per site with flexible calendar-based rules:

#### Calendar Schedule Types
- **All The Time** - Check continuously based on interval
- **Selected Day** - Check only on a specific date
- **Date Range** - Check between two dates (e.g., holiday sales period)
- **Every Weeks** - Check on selected days of the week with optional even/odd week filtering

#### Interval Options
- **Periodic** - Set intervals from 15 minutes to 10 hours
- **Live Tracking** - Real-time monitoring with second-level precision (1-960 seconds)
  - Two sliders: minutes (0-15) and seconds (1-60)
  - Perfect for time-sensitive monitoring (auctions, flash sales, stock availability)
- **Specific Hour** - Check at an exact time each day

#### Schedule Management
- **Multiple Schedules** - Combine different schedules (OR logic - any matching schedule triggers check)
- **Drag-to-Reorder** - Arrange schedule priority
- **Enable/Disable** - Toggle individual schedules without deleting
- **Duplicate** - Clone schedules for quick setup
- **Clear Outdated** - One-tap removal of expired schedules with dramatic animations
  - Removes SELECTED_DAY schedules with dates before today
  - Removes DATE_RANGE schedules where end date has passed

#### Real-Time Countdown
- **Live Display** - See next check time counting down in real-time
- **Seconds Precision** - Shows seconds when under 1 minute
- **Dramatic Effect** - Shows tenths (X.X sec) when under 10 seconds with rapid updates

### Notifications
- **Instant Alerts** - Get notified immediately when changes are detected
- **Configurable Actions** - Tap to open the app or go directly to the website
- **Smart Thresholds** - Only alert when changes exceed your set percentage

### Built-in Browser
- **Discover Sites** - Browse the web directly within the app
- **Quick Add** - One tap to start monitoring any page you visit
- **Search Integration** - Choose your preferred search engine (DuckDuckGo, Google, Bing, Qwant)
- **Open in WebView** - Test sites with JavaScript fetch mode

### Content Comparison
- **Diff Viewer** - Visual comparison of changes with mode-aware viewing options
- **Quick Access** - Tap the percentage badge to instantly open the diff viewer
- **Mode-Aware Views** - Available views depend on comparison mode:
  - **Full HTML mode**: Changes Only, Rendered View, and Full Text Diff
  - **Text Only mode**: Text Diff only (no rendering needed)
  - **CSS Selector mode**: Text Diff only (partial HTML not renderable)
- **Changes Only (Default for Full HTML)** - Rendered page with changed elements highlighted in red
- **Rendered View** - Side-by-side comparison of before/after pages
- **Full Text Diff** - Complete line-by-line diff with context
- **Color Coding** - Green for additions, red for removals
- **Statistics** - See exactly how many lines changed

### View Data
- **View Comparison Data** - See raw content used for comparison
- **Word Wrap Toggle** - Better readability for long lines
- **Mode Display** - Shows current comparison mode and capture timestamp

### Backup & Restore
- **Export Sites** - Save your monitored sites to a JSON file
- **Import Sites** - Restore from backup or share configurations
- **Content Backups** - View and manage stored page snapshots
- **Backup Viewer** - View rendered page or source code for any backup

### Advanced Filtering
- **Minimum Text Length** - Filter short text blocks (timestamps, counters)
- **Minimum Word Length** - Filter short words ("a", "an", "the", numbers) in TEXT_ONLY mode
- **Boolean Search** - AND, OR, NOT operators with grouping support

### Theme System
8 beautiful themes to personalize the app:
- Orange Fire
- Blue Light
- Forest Green
- Ocean Blue
- Synthwave
- Coastal Sunset (default)
- Nordic Red
- Neon Carnival

### Additional Features
- **Check All** - Manually trigger checks for all sites at once
- **Duplicate Sites** - Quickly clone site configurations
- **Auto-Resume** - Monitoring continues after device restart
- **Network Options** - WiFi only, mobile data, or both
- **Debug Mode** - Enable logging for troubleshooting

## Screenshots

| Site List | Add/Edit Site | Browser Discovery |
|:---------:|:-------------:|:-----------------:|
| <img width="108" height="240" alt="Site List" src="https://github.com/user-attachments/assets/45449d2b-6f8e-403f-85d3-8224169e35ce" /> | <img width="108" height="240" alt="Add/Edit Site" src="https://github.com/user-attachments/assets/40ec7c7f-f06c-4e92-bc67-45f16d687704" /> | <img width="108" height="240" alt="Browser Discovery" src="https://github.com/user-attachments/assets/29390a9d-5e03-4c2f-abff-ccad5c609134" /> |

| Diff Viewer | Settings | Action Editor |
|:-----------:|:--------:|:-------------:|
| <img width="108" height="240" alt="Diff Viewer" src="https://github.com/user-attachments/assets/003a4ac1-20ad-4d57-b5fc-163418adf93f" /> | <img width="108" height="240" alt="Settings" src="https://github.com/user-attachments/assets/db397e64-513f-4012-8612-c1d6d0eccf2f" /> | <img width="108" height="240" alt="Action Editor" src="https://github.com/user-attachments/assets/3c9bbc3e-477e-4106-85dd-977904a8990b" /> |

## Quick Start

### Installation

1. **Download** the latest APK from [Releases](../../releases)
2. **Enable** installation from unknown sources if prompted
3. **Install** the app on your Android device

### Grant Permissions

When first launching the app, you'll be asked for:
- **Notifications** - Required to alert you of changes
- **Exact Alarms** - Required for precise scheduling
- **Accessibility Service** (optional) - Required only for "Tap at Coordinates" actions

### Add Your First Site

1. Tap the **+** floating button
2. Enter the website URL or tap **Browse** to discover sites
3. Configure monitoring options:
   - **Name** (optional) - Custom display name
   - **Comparison Mode** - How to detect changes
   - **Fetch Mode** - Static (fast) or JavaScript (dynamic)
   - **CSS Include/Exclude** - Filter which elements to compare (for CSS Selector mode)
   - **Calendar Schedules** - Set up one or more schedules
   - **Threshold** - Minimum change percentage to notify
4. Tap **Save**

### Configure Schedules

1. In the Add/Edit screen, tap **Calendar Schedules**
2. Tap the **+** FAB to add a schedule type:
   - **Execute All The Time** - Always active
   - **Selected Day** - Pick a specific date
   - **From Date To Date** - Set a date range
   - **Every Weeks** - Choose days and week parity
3. Configure the interval:
   - **Periodic** - Check every 15 minutes to 10 hours
   - **Live Tracking** - Check every 1 second to 16 minutes (for real-time monitoring)
   - **Specific Hour** - Check at an exact time each day
4. Toggle schedules on/off as needed
5. Drag to reorder priority
6. Tap the broom icon to clear outdated schedules

### Add Auto-Click Actions (Optional)

For sites with cookie consent dialogs or dynamic content:

1. In the Add/Edit screen, set **Fetch Mode** to "JavaScript"
2. Scroll to **Auto-Click Actions** section
3. Tap the **+** FAB to add actions:
   - **Add Standard Action** - Choose from built-in patterns
   - **Add Custom Action** - Pick elements visually
   - **Add Sleep** - Add delays between actions
4. Drag actions to reorder, toggle to enable/disable
5. Tap **Test Actions** to preview the sequence

### Monitor Changes

- Sites are checked automatically based on your schedule
- Pull down to see the latest status
- **Tap the percentage badge** to quickly view changes
- Long-press a site for quick actions:
  - Open in Browser
  - Open in WebView
  - Test Actions
  - Edit
  - Duplicate
  - Check Now
  - View Changes
  - View Data
  - Delete

## Configuration Guide

### Comparison Modes

| Mode | Best For | Description |
|------|----------|-------------|
| **Full HTML** | Technical changes | Detects any change including invisible elements |
| **Text Only** | Content updates | Ignores styling, focuses on readable text |
| **CSS Selector** | Specific elements | Monitor only matching elements with include/exclude filtering |

### CSS Selector Include/Exclude Filtering

The CSS Selector mode supports advanced filtering with two clearly labeled fields:

| Field | Default Placeholder | Behavior | Example |
|-------|---------------------|----------|---------|
| **Include CSS** | "All CSS Included" | Elements to monitor (empty = all elements) | `.product-price, #stock-status` |
| **Select CSS to exclude** | "No elements to exclude" | Elements to filter out from comparison | `.ad-banner, .timestamp, .counter` |

Each field has a visual picker button - tap to open the interactive element picker where you can select elements by tapping on them directly. The picker shows context-aware instructions based on whether you're selecting elements to include or exclude.

**Use cases:**
- Include only product prices: Include = `.price`
- Monitor everything except ads: Include = (empty), Exclude = `.advertisement`
- Watch specific section without timestamps: Include = `#main-content`, Exclude = `.last-updated`

### Fetch Modes

| Mode | Best For | Description |
|------|----------|-------------|
| **Static** | Simple pages | Fast HTML fetch, no JavaScript |
| **JavaScript** | Dynamic content | Full WebView rendering, supports SPAs and AJAX |

### Auto-Click Action Types

| Type | Best For | Description |
|------|----------|-------------|
| **Click Element** | Buttons, links | Uses CSS selector to find and click element |
| **Wait** | Animations, loads | Pauses 1-60 seconds before next action |
| **Tap at Coordinates** | iframes, shadow DOM | Taps at exact screen position via Accessibility Service |

### Calendar Schedule Types

| Type | Use Case | Example |
|------|----------|---------|
| **All The Time** | Continuous monitoring | Check every 30 minutes, always |
| **Selected Day** | One-time events | Check only on Dec 25, 2025 |
| **Date Range** | Limited periods | Check during Black Friday week |
| **Every Weeks** | Recurring patterns | Check Mon/Wed/Fri, or even weeks only |

### Interval Types

| Type | Use Case | Example |
|------|----------|---------|
| **Periodic** | Frequent checks | Every 30 minutes, 2 hours, etc. |
| **Live Tracking** | Real-time monitoring | Every 30 seconds, 2 minutes, etc. |
| **Specific Hour** | Precise timing | At exactly 9:00 AM |

### Multi-Schedule Examples

| Scenario | Schedules |
|----------|-----------|
| **Business hours monitoring** | Every Weeks (Mon-Fri) + Specific Hour (9:00 AM) |
| **Sale period + regular** | Date Range (Nov 25-30) + Every 15min, All The Time + Every 2h |
| **Weekend only** | Every Weeks (Sat, Sun) + Periodic (1 hour) |
| **Auction countdown** | Selected Day (auction date) + Live Tracking (every 30 sec) |
| **Flash sale watch** | Date Range (sale period) + Live Tracking (every 10 sec) |

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
- **Optional**: Accessibility Service (for Tap at Coordinates actions)

### Architecture
- **Pattern**: Single Activity with Navigation Component, MVVM with Repository
- **Database**: Room for persistent storage with migrations
- **Networking**: OkHttp + Jsoup for static content, WebView for JavaScript
- **Background**: AlarmManager with exact alarms
- **Accessibility**: Custom service for tap gesture execution

### Project Structure
```
com.ltrudu.sitewatcher/
├── accessibility/     # Accessibility service for tap gestures
├── data/
│   ├── dao/           # Room DAOs
│   ├── database/      # Room Database with migrations
│   ├── model/         # Entity classes (WatchedSite, Schedule, AutoClickAction, etc.)
│   └── repository/    # Data access layer
├── ui/
│   ├── sitelist/      # Main site list
│   ├── addedit/       # Add/Edit forms, schedule and action management
│   ├── selector/      # Interactive pickers (CSS, coordinates, tester)
│   ├── browser/       # Built-in browser
│   ├── diff/          # Diff viewer
│   ├── dataviewer/    # View comparison data
│   ├── backups/       # Backup manager and viewer
│   ├── settings/      # App settings
│   ├── view/          # Custom views (TapCrosshairView)
│   └── about/         # About screen
├── background/        # Schedulers and receivers
├── network/           # HTTP client, WebView fetcher, auto-click executor
├── notification/      # Notification handling
└── util/              # Utilities, theme manager, logger
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

### Auto-click actions not working?
- Ensure **Fetch Mode** is set to "JavaScript"
- Check that actions are enabled (toggle is on)
- Use **Test Actions** to verify the sequence works
- For Tap at Coordinates, enable the **Accessibility Service**

### Tap at Coordinates not working?
- Enable **SiteWatcher Accessibility Service** in Android settings
- The app will prompt you when TAP_COORDINATES actions exist
- Ensure coordinates are correct using the Coordinate Picker

### High battery usage?
- Increase check intervals to reduce frequency
- Use WiFi-only mode to avoid mobile data checks
- Reduce the number of monitored sites
- Use Static fetch mode when JavaScript isn't needed

## License

This project is released into the public domain under the [Unlicense](LICENSE).

You are free to copy, modify, publish, use, compile, sell, or distribute this software for any purpose, commercial or non-commercial.

## Acknowledgments

Dependencies are subject to their own licences. Check links for more informations.

- [OkHttp](https://square.github.io/okhttp/) - HTTP client
- [Jsoup](https://jsoup.org/) - HTML parser
- [Material Design 3](https://m3.material.io/) - UI components

---

<p align="center">Made with care for people who need to stay informed.</p>

<p align="center">❤️ Made with <a href="https://claude.ai">Claude.ai</a> ❤️</p>
