# 🚀 LiveTV Auto Start Configuration

## Overview

LiveTV is configured to start automatically when Android TV is powered on and when waking up from standby. This functionality ensures that the app is always available and ready to use.

## ✨ Features

### 🔌 Auto Start on Power On
- App starts automatically when TV is turned on
- Configurable through app settings
- Uses `AutoStartService` to manage startup

### 📺 Wake Up from Standby
- App restarts automatically when TV exits standby
- Handles TV state changes (screen on/off)
- Maintains active background services

### 🔄 App Updates
- Automatic service restart after app update
- Maintains settings and state

## ⚙️ Configuration

### 1. App Settings

Open LiveTV **Settings** and go to **🚀 Auto Start** section:

- **Start automatically when TV is powered on**: Enable auto start
- **Start automatically when waking up from standby**: Enable wake up auto start
- **Keep app active in background**: Keep services active
- **Set as default TV app**: Configure app as default

### 2. System Settings

#### Battery Optimizations
1. Go to **Settings** → **Apps** → **LiveTV**
2. Tap **Battery**
3. Select **No restrictions** or **Manual optimization**
4. Disable **Background battery optimization**

#### Startup Permissions
1. Go to **Settings** → **Apps** → **LiveTV**
2. Tap **Permissions**
3. Enable **Auto start** and **Background execution**

### 3. TV Settings (Brand Specific)

#### Xiaomi/Mi TV
1. **Settings** → **Apps** → **App management**
2. **LiveTV** → **Permissions** → **Auto start** ✅
3. **Settings** → **Battery & performance** → **Battery management**
4. **LiveTV** → **No restrictions**

#### Samsung TV
1. **Settings** → **Apps** → **App management**
2. **LiveTV** → **Permissions** → **Auto start** ✅
3. **Settings** → **General** → **Developer mode**
4. **Developer mode** → **ON**

#### LG TV
1. **Settings** → **General** → **Apps**
2. **LiveTV** → **Permissions** → **Auto start** ✅
3. **Settings** → **General** → **Developer mode**
4. **Developer mode** → **ON**

## 🔧 Technical Components

### BootReceiver
- Handles system events (`BOOT_COMPLETED`, `SCREEN_ON`, etc.)
- Starts appropriate services based on events
- High priority to ensure execution

### AutoStartService
- Foreground service for auto start
- Manages MainActivity startup
- Keeps app active in background

### BackgroundService
- Keeps services active
- Handles TV state changes
- Uses wake lock to maintain activity

## 📱 Testing and Verification

### Auto Start Test
1. Go to **Settings** → **🚀 Auto Start**
2. Tap **🧪 Test Start**
3. Verify that app starts correctly

### Wake Up Test
1. Put TV in standby
2. Wake up TV
3. Verify that LiveTV starts automatically

### Service Verification
1. Go to **Settings** → **Apps** → **LiveTV**
2. Tap **Memory & storage**
3. Verify that services are active

## 🚨 Troubleshooting

### App doesn't start automatically

1. **Check settings**:
   - Verify that auto start is enabled
   - Check battery optimizations

2. **Verify permissions**:
   - Check auto start permissions
   - Verify background execution permissions

3. **Check TV settings**:
   - Verify brand-specific settings
   - Check developer mode

### App closes in background

1. **Disable optimizations**:
   - Go to **Settings** → **Battery**
   - Select **No restrictions** for LiveTV

2. **Verify services**:
   - Check that services are active
   - Verify persistent notifications

### Performance issues

1. **Optimize settings**:
   - Disable unnecessary features
   - Reduce EPG update frequency

2. **Check memory**:
   - Verify memory usage
   - Restart app if necessary

## 📋 Configuration Checklist

- [ ] Auto start enabled in app settings
- [ ] Wake up auto start enabled
- [ ] Background service active
- [ ] Battery optimizations disabled
- [ ] Auto start permissions granted
- [ ] Developer mode activated (if required)
- [ ] Auto start test completed
- [ ] Wake up test completed

## 🔗 Useful Links

- [Android TV Documentation](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [Android Permissions Guide](https://developer.android.com/guide/topics/permissions/overview)
- [Android Services Management](https://developer.android.com/guide/components/services)

## 📞 Support

If you encounter issues with auto start:

1. Check app logs
2. Verify system settings
3. Consult TV brand documentation
4. Contact technical support

---

**Note**: Specific settings may vary based on Android TV model and version. Always consult manufacturer documentation for specific configurations.