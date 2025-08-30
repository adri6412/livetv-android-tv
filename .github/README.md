# GitHub Actions Workflows

This directory contains GitHub Actions workflows for automated building, testing, and releasing of LiveTV Android TV application.

## 🚀 Available Workflows

### 1. **Build Release APK** (`build-release.yml`)
Automatically builds APK files when you create a version tag.

**Triggers:**
- Push to version tags (e.g., `v1.0.0`, `v1.5.31`)
- Manual trigger from GitHub Actions tab

**Outputs:**
- Debug APK (`app-debug.apk`)
- Release APK (`app-release.apk`) - Unsigned
- GitHub Release (if triggered by tag)

### 2. **Build Signed Release APK** (`build-signed-release.yml`)
Builds signed APK files for production distribution.

**Triggers:**
- Push to version tags (e.g., `v1.0.0`, `v1.5.31`)
- Manual trigger from GitHub Actions tab

**Requirements:**
- Repository secrets for keystore signing (optional)

**Outputs:**
- Debug APK (`app-debug.apk`)
- Signed Release APK (`app-release.apk`) - If keystore provided
- Unsigned Release APK - If no keystore provided
- GitHub Release (if triggered by tag)

### 3. **Run Tests** (`test.yml`)
Runs automated tests and lint checks.

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` branch
- Manual trigger from GitHub Actions tab

**Outputs:**
- Unit test results
- Lint check results
- Test reports in artifacts

## 🔧 Setup Instructions

### For Basic APK Building (Unsigned)

1. **Create a version tag:**
   ```bash
   git tag v1.5.32
   git push origin v1.5.32
   ```

2. **Or trigger manually:**
   - Go to GitHub Actions tab
   - Select "Build Release APK" workflow
   - Click "Run workflow"

### For Signed APK Building (Production)

1. **Generate a keystore:**
   ```bash
   keytool -genkey -v -keystore livetv-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias livetv-key
   ```

2. **Add repository secrets:**
   - Go to repository Settings → Secrets and variables → Actions
   - Add these secrets:
     - `KEYSTORE_BASE64`: Base64 encoded keystore file
     - `KEYSTORE_PASSWORD`: Keystore password
     - `KEY_ALIAS`: Key alias (e.g., `livetv-key`)
     - `KEY_PASSWORD`: Key password

3. **Encode keystore to base64:**
   ```bash
   base64 -i livetv-release-key.jks -o keystore-base64.txt
   ```

4. **Create version tag:**
   ```bash
   git tag v1.5.32
   git push origin v1.5.32
   ```

## 📱 APK Distribution

### Automatic Releases
When you push a version tag, GitHub Actions will:
1. Build the APK(s)
2. Create a GitHub Release
3. Attach APK files to the release
4. Generate release notes automatically

### Manual Downloads
- Go to repository Actions tab
- Select the latest workflow run
- Download APK files from artifacts

## 🔍 Workflow Details

### Build Environment
- **OS**: Ubuntu Latest
- **Java**: JDK 17 (Temurin)
- **Android SDK**: Latest stable
- **Gradle**: Cached for faster builds

### Security
- Keystore secrets are encrypted
- No sensitive data in logs
- APK signing happens securely in GitHub Actions

### Performance
- Gradle dependencies cached
- Parallel job execution
- Optimized build steps

## 📋 License Compliance

All workflows respect the Mozilla Public License 2.0 (MPL 2.0):
- ✅ Source code remains open
- ✅ Dependencies documented in `THIRD_PARTY_LICENSES`
- ✅ Build artifacts include license information
- ✅ No proprietary code in workflows

## 🛠️ Troubleshooting

### Common Issues

**Build fails with "SDK not found"**
- Check Android SDK setup in workflow
- Verify `local.properties` is not committed

**Signing fails**
- Verify all keystore secrets are set correctly
- Check keystore file encoding (must be base64)

**APK not uploaded to release**
- Ensure you're using version tags (v*.*.*)
- Check GitHub token permissions

**Tests fail**
- Review test results in artifacts
- Check for lint errors in reports

### Getting Help
- Check workflow logs in GitHub Actions tab
- Review build artifacts for detailed error messages
- Ensure all dependencies are compatible with MPL 2.0

---

**LiveTV Android TV** - Automated builds with GitHub Actions! 🚀📱
