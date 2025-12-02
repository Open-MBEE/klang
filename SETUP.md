# K Language Web Server Setup Instructions

This document explains how to set up and run the K language web server on macOS.

## Multi-Platform Support

The project includes Z3 4.13.0 libraries for multiple platforms and architectures. The build and startup scripts automatically detect your operating system and Java architecture, then select the appropriate Z3 libraries.

**Supported configurations:**
- **macOS Intel (x86_64)** with Java 8 x86_64
- **macOS Apple Silicon (ARM64)** with Java 8 ARM64 or x86_64 (via Rosetta 2)
- **Linux (x86_64)** with Java 8 x86_64

The libraries are organized as:
```
export/lib/
├── libz3.dylib / libz3.so      (active - auto-selected)
├── libz3java.dylib / libz3java.so (active - auto-selected)
├── x86_64/                     (Z3 4.13.0 for Intel Macs)
├── arm64/                      (Z3 4.13.0 for Apple Silicon)
└── linux/                      (Z3 4.13.0 for Linux x64)
```

## Prerequisites

### 1. Java 8 Installation

**Current Requirement: Java 8**

The K language project currently requires Java 8 due to **Scala 2.11.8 compatibility**. The Z3 4.13.0 libraries themselves support newer Java versions, but the Scala code needs Java 8.

**To use newer Java versions (11, 17, 21)**, you would need to:
- Upgrade Scala to 2.12+ (for Java 11) or 2.13+ (for Java 17+)
- Update the Maven compiler plugin source/target versions
- Test for deprecated API usage

You can install Java 8 using SDKMAN:

```bash
# Install SDKMAN if not already installed
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 8
sdk install java 8.0.462-zulu

# Switch to Java 8 (this is done automatically by the build scripts)
sdk use java 8.0.462-zulu
```

### 2. Verify Installation

Check that you have the required Java version available:
```bash
ls -la ~/.sdkman/candidates/java/
```

You should see `8.0.462-zulu` (or similar Java 8 version) in the list.

## Building and Running

You have two options for building and running the K language web server:

### Option 1: Maven Build (Recommended)

Maven is the standard build tool and now properly handles all aspects of the build:

```bash
# Simple startup (builds if needed and starts server)
./start-server-maven.sh
```

Or manually:
```bash
# Build with Maven
export JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.462-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean compile

# Run server
cd target/classes
java -cp ".:../../src/web/jettyService/jetty-distribution-9.2.12.v20150709/lib/*" web.jettyService.KServlet
```

### Option 2: Custom Build Script

The custom script provides more control and was the original workaround:

```bash
# Simple startup (builds if needed and starts server)  
./start-server.sh

# Or build and run manually
cd src/web/jettyService
./run-server.sh
```

## What Each Build System Does

### Maven (`mvn clean compile`)
- ✅ Compiles Scala files first (using scala-maven-plugin)
- ✅ Compiles Java files including KServlet
- ✅ Outputs compiled classes to target/classes/
- ✅ Web assets and examples remain in src/ (referenced directly by KServlet)

- ✅ Compiles Scala files first (using command-line scalac)
- ✅ Compiles Java files including KServlet  
- ✅ Outputs compiled classes to bin/
- ✅ Web assets and examples remain in src/ (referenced directly by KServlet)

Both approaches work and produce the same result! The KServlet automatically finds web assets in `src/web/` and examples in `src/examples/` - no copying needed.

## Running the Web Server

### Option 1: Maven-based Server
```bash
./start-server-maven.sh
```

### Option 2: Custom Script Server
```bash
./start-server.sh
```

### Manual Startup

If you prefer to run manually after building:

**With Maven build:**
```bash
cd target/classes
java -cp ".:../../src/web/jettyService/jetty-distribution-9.2.12.v20150709/lib/*" web.jettyService.KServlet
```

**With custom build:**
```bash
cd src/web/jettyService
./run-server.sh
```

### 2. Access the Web Interface

Once the server starts, you'll see output like:
```
Starting K web server on http://localhost:9000
Web interface: http://localhost:9000/index.html
API endpoint: http://localhost:9000/k-service
```

Open your browser to:
- **Main Interface**: http://localhost:9000
- **Direct to index**: http://localhost:9000/index.html
- **API endpoint**: http://localhost:9000/k-service (for POST requests)

### 3. Stop the Server

Press `Ctrl+C` in the terminal where the server is running.

## Troubleshooting

### Common Issues

1. **"object java.lang.Object in compiler mirror not found"**
   - This indicates you're not using Java 8. Run the build script again, which should automatically switch to Java 8.

2. **"Cannot find k script"**
   - Ensure the `export/k` script exists and is executable
   - Verify you're running from the correct directory

3. **"Web directory not found"**
   - This is usually just a warning. The web interface should still work.

4. **Port 9000 already in use**
   - Kill any existing processes using port 9000: `lsof -ti:9000 | xargs kill -9`
   - Or modify the port in `KServlet.java` (line with `new Server(9000)`)

### Verification Steps

1. Check if classes were compiled:
   ```bash
   ls -la bin/web/jettyService/KServlet.class
   ```

2. Verify Scala classes exist:
   ```bash
   ls -la bin/k/frontend/
   ```

3. Test the API endpoint:
   ```bash
   curl -X POST -d "model test" http://localhost:9000/k-service
   ```

## Project Structure

- `src/` - Source code (Scala and Java files)
- `bin/` - Compiled classes (generated by build script)
- `export/` - K language runtime and scripts
- `src/web/` - Web interface files (HTML, CSS, JS)
- `src/web/jettyService/` - Web server implementation

## Notes

- Both build approaches automatically handle Java version switching to Java 8
- The web server serves static files from the web directory and provides an API at `/k-service`
- The K language processor is called via the `export/k` script
- Temporary files are created in `/tmp/` during model processing
- **Recommended**: Use the Maven approach (`./start-server-maven.sh`) for standard Maven workflows
- **Alternative**: Use the custom script approach (`./start-server.sh`) for faster builds or troubleshooting

## Build System Comparison

| Feature | Maven | Custom Script | Ant |
|---------|-------|---------------|-----|
| Scala compilation | ✅ | ✅ | ❌ |
| Java compilation | ✅ | ✅ | ✅ |
| Web assets | ✅ | ✅ | ✅ |
| Java 8 handling | ✅ | ✅ | ❌ |
| Standard tooling | ✅ | ❌ | ✅ |
| Build speed | Medium | Fast | Fast |