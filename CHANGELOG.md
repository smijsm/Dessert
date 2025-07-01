# Changelog

All notable changes to the Dessert Test Generator plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-07-01

### 🎉 Initial Release - Making Testing a Piece of Cake

#### ✨ Core Functionality
- **AI-Powered Test Generation**
  - Intelligent unit test creation from debugger context
  - Integration with IntelliJ IDEA debugger via context menu action
  - Real-time capture of stack frames, variables, and method signatures
  - Context-aware test generation using runtime data

- **Multi-Language Support**
  - Java (.java) file support with method signature detection
  - Kotlin (.kt) file support including Kotlin Multiplatform projects
  - Scala (.scala) file support with appropriate syntax handling
  - Language-specific test naming conventions and patterns

- **AI Provider Integration**
  - OpenAI API support with configurable models (default: o4-mini)
  - Google Gemini API support with configurable models (default: gemini-2.5-flash)
  - Anthropic Claude API support with configurable models (default: claude-3-7-sonnet-latest)
  - Environment variable configuration for API keys and model selection

- **Build System Detection**
  - Gradle project support (build.gradle / build.gradle.kts)
  - Maven project support (pom.xml)
  - SBT project support (build.sbt)
  - Bazel project support (WORKSPACE, BUILD files)
  - Automatic build configuration extraction for context-aware test generation

- **Smart File Management**
  - Automatic test file creation in appropriate directories
  - Existing test file extension with new test methods
  - Language-specific test file naming conventions (ClassNameTest.ext)
  - Support for complex project structures including multi-module projects

#### 🍰 "Piece of Cake" User Experience
- **One-Click Test Generation**: Right-click in debugger → instant comprehensive tests
- **Animated Loading Dialog**: Visual progress feedback with real-time status updates
- **Automatic File Opening**: Generated tests open automatically for immediate review
- **Cancellable Operations**: User can cancel generation with proper cleanup
- **Error Handling**: User-friendly error messages and fallback behaviors

#### 🔧 Advanced Features
- **Kotlin Multiplatform Support**
  - Automatic KMP project detection and handling
  - Source set detection for KMP projects (commonTest, jvmTest, etc.)
  - Proper test directory structure for shared modules

- **Intelligent Context Extraction**
  - Package name extraction and preservation
  - Build system context inclusion in AI prompts
  - Timeout handling for debugger variable extraction
  - Method signature parsing for multiple languages

- **Robust Threading Model**
  - Background task execution with progress indicators
  - Proper EDT (Event Dispatch Thread) handling for UI operations
  - Read/Write action management for file system operations
  - Cancellation support throughout the pipeline

#### 🎯 Technical Implementation
- **Debugger Integration**
  - XDebugger API integration for stack frame access
  - Variable extraction with type safety and timeout protection

- **File System Operations**
  - Automatic directory creation for test files
  - Cross-platform path handling
  - File content reading

- **AI Communication**
  - HTTP client configuration for API calls
  - JSON serialization for different AI provider formats
  - Error handling and retry logic
  - Secure API key management through environment variables

#### 📋 Supported Configurations
- **IDE Compatibility**: IntelliJ IDEA 2025.1 - 2025.2.*
- **Java Compatibility**: Java 17+ required
- **Project Types**: Standard, Multi-module, Kotlin Multiplatform
- **Test Frameworks**: framework-agnostic generation

---

**Note**: This changelog will be updated with each release. For the latest changes, please check our [GitHub repository](https://github.com/smijsm/Dessert).