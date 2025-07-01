# 🍰 Dessert - AI-Powered Test Generation Plugin

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![IntelliJ Plugin](https://img.shields.io/badge/IntelliJ-Plugin-blue.svg)](https://plugins.jetbrains.com/)

**Making automated testing a piece of cake** 🍰 — Generate comprehensive unit tests directly from your debugger using AI.

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/gl03zfnjpKQ/0.jpg)](https://www.youtube.com/watch?v=gl03zfnjpKQ)

## 🎯 Why Dessert Makes Testing a Piece of Cake

Traditional test writing is time-consuming and often disconnected from real runtime behavior. Dessert changes this by implementing a sophisticated approach that captures comprehensive context from your debugging session and uses **Retrieval Augmented Generation (RAG)** to produce tests that reflect actual runtime scenarios rather than theoretical edge cases.

## ✨ Features

- **🔍 Debugger Integration**: Generate tests directly from debugger variables and stack frames
- **🤖 Multi-AI Support**: Works with OpenAI, Google Gemini, and Anthropic Claude
- **🌐 Multi-Language**: Supports Java, Kotlin, and Scala
- **🏗️ Build System Aware**: Automatically detects Gradle, Maven, SBT, and Bazel projects
- **📁 Smart File Management**: Creates or extends existing test files intelligently
- **🎯 Context-Aware**: Captures method signatures, variables, and build configurations

## 🚀 Quick Start

### Prerequisites

- IntelliJ IDEA 2025.1 or later
- API key for one of the supported AI providers (OpenAI, Gemini, or Claude)

### Installation

1. Download the plugin from the JetBrains Plugin Repository
2. Install via **File → Settings → Plugins → Install Plugin from Disk**
3. Restart IntelliJ IDEA

### Configuration

Set the following environment variables before starting IntelliJ IDEA:

Required: Your AI provider API key
- `export DESSERT_API_KEY=your_api_key_here`

Optional: AI provider (default: gemini)
- `export DESSERT_AI_PROVIDER=gemini # Options: openai, claude, gemini`

Optional: Model name (uses provider defaults if not specified)
- `export DESSERT_MODEL_NAME=gemini-2.5-flash`

**Default Models:**
- OpenAI: `o4-mini`
- Claude: `claude-3-7-sonnet-latest`
- Gemini: `gemini-2.5-flash`

## 🎯 How to Use

1. **Set a breakpoint** in your code and start debugging
2. **Pause execution** at the desired location
3. **Right-click** in the debugger variables panel
4. **Select "Generate Test with Dessert"** from the context menu
5. **Wait** for AI to analyze your code and generate tests
6. **Review** the generated test file that opens automatically

### Supported Project Structures

**Build Systems:**
- Gradle (build.gradle / build.gradle.kts)
- Maven (pom.xml)
- SBT (build.sbt)
- Bazel (WORKSPACE, BUILD files)

**Languages:**
- Java (.java files)
- Kotlin (.kt files) - including Kotlin Multiplatform
- Scala (.scala files)

## 🏗️ How It Works

1. **Capture Context**: Extracts current stack frame, variables, method signatures, and build configuration
2. **AI Analysis**: Sends captured data to your chosen AI provider with optimized prompts
3. **Test Generation**: AI generates comprehensive unit tests following best practices
4. **File Management**: Creates new test files or extends existing ones in the appropriate test directory
5. **IDE Integration**: Opens generated tests in the editor for immediate review

## 📁 Test File Organization

Dessert intelligently places test files based on your project structure:

- **Standard Projects**: `src/test/{language}/generated/`
- **Kotlin Multiplatform**: `shared/src/{sourceSet}Test/kotlin/generated/`
- **Bazel Projects**: Alongside source files with appropriate BUILD targets

## 🔧 Advanced Configuration

### Custom Model Selection

OpenAI models
- `export DESSERT_MODEL_NAME=gpt-4`
- `export DESSERT_MODEL_NAME=gpt-3.5-turbo`

Claude models
- `export DESSERT_MODEL_NAME=claude-3-opus-20240229`
- `export DESSERT_MODEL_NAME=claude-3-sonnet-20240229`

Gemini models
- `export DESSERT_MODEL_NAME=gemini-pro`
- `export DESSERT_MODEL_NAME=gemini-2.5-flash`

### Troubleshooting

**Plugin not appearing in context menu?**
- Ensure you're debugging and execution is paused
- Check that you're right-clicking in the debugger variables panel

**API errors?**
- Verify your API key is correctly set
- Check your internet connection
- Ensure your AI provider account has sufficient credits

**Test files not opening?**
- Check IDE permissions for file creation
- Verify project structure is supported

## 🤝 Contributing

We welcome contributions! Please see our [GitHub repository](https://github.com/smijsm/Dessert) for:

- 🐛 Bug reports
- 💡 Feature requests
- 🔧 Pull requests
- 📖 Documentation improvements

### Development Setup

1. Clone the repository
2. Open in IntelliJ IDEA
3. Configure Gradle JVM to Java 17+
4. Run `./gradlew runIde` to test the plugin

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Michael Solovev** - [smijsm@gmail.com](mailto:smijsm@gmail.com)

---

**Happy Testing!** 🧪✨