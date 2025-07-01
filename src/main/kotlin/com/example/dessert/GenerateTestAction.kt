package com.example.dessert

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.debugger.engine.JavaValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import javax.swing.*
import java.awt.*

class GenerateTestAction : AnAction("Generate Test with Dessert") {

    // Configuration - can be moved to settings later
    private val aiProvider = System.getenv("DESSERT_AI_PROVIDER") ?: "gemini" // openai, claude, gemini
    private val modelName = System.getenv("DESSERT_MODEL_NAME") ?: getDefaultModel(aiProvider)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getDefaultModel(provider: String): String {
        return when (provider.lowercase()) {
            "openai" -> "o4-mini"
            "claude" -> "claude-3-7-sonnet-latest"
            "gemini" -> "gemini-2.5-flash"
            else -> "o4-mini"
        }
    }

    // Store dialog components as class properties for easier access
    private var statusLabel: JLabel? = null

    // Language detection enum
    enum class SourceLanguage(val extension: String, val testSuffix: String) {
        KOTLIN("kt", "Test"),
        JAVA("java", "Test"),
        SCALA("scala", "Test")
    }

    // Build system detection enum
    enum class BuildSystem(val displayName: String) {
        GRADLE("Gradle"),
        MAVEN("Maven"),
        SBT("SBT"),
        BAZEL("Bazel"),
        UNKNOWN("Unknown")
    }

    private fun showLoadingDialogWithGif(project: com.intellij.openapi.project.Project, onCancel: () -> Unit): JDialog {
        val dialog = JDialog()
        dialog.title = "Generating Test(s) with Dessert"
        dialog.isModal = true
        dialog.defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        dialog.setSize(400, 200)
        dialog.setLocationRelativeTo(null)

        val panel = JPanel(BorderLayout())

        // Create animated loading icon (GIF or fallback) - no resizing
        val loadingLabel = createAnimatedLoadingIcon()
        panel.add(loadingLabel, BorderLayout.CENTER)

        // Add status text with padding - store reference for updates
        statusLabel = JLabel("Preparing debugger data...", JLabel.CENTER)
        statusLabel!!.font = statusLabel!!.font.deriveFont(14f)
        statusLabel!!.border = BorderFactory.createEmptyBorder(15, 10, 10, 10) // Added padding: top, left, bottom, right
        panel.add(statusLabel, BorderLayout.NORTH)

        // Add cancel button
        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener {
            onCancel()
            dialog.dispose()
        }
        val buttonPanel = JPanel(FlowLayout())
        buttonPanel.add(cancelButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        dialog.add(panel)

        return dialog
    }

    private fun createAnimatedLoadingIcon(): JLabel {
        return try {
            // Try to load GIF from resources - no resizing
            val gifUrl = javaClass.getResource("/icons/loading.gif")
            if (gifUrl != null) {
                val gifIcon = ImageIcon(gifUrl)
                val label = JLabel(gifIcon, JLabel.CENTER)
                println("DEBUG: Successfully loaded loading.gif")
                label
            } else {
                println("DEBUG: loading.gif not found, using fallback animation")
                createTextBasedLoadingIcon()
            }
        } catch (e: Exception) {
            println("DEBUG: Could not load loading.gif: ${e.message}")
            createTextBasedLoadingIcon()
        }
    }

    private fun createTextBasedLoadingIcon(): JLabel {
        val label = JLabel("⏳", JLabel.CENTER)
        label.font = Font(Font.SANS_SERIF, Font.PLAIN, 30)

        // Simple text animation as fallback
        val timer = Timer(500) {
            val currentText = label.text
            label.text = when (currentText) {
                "⏳" -> "⌛"
                "⌛" -> "⏳"
                else -> "⏳"
            }
        }
        timer.start()

        // Store timer to stop it later
        label.putClientProperty("timer", timer)

        return label
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val debuggerManager = XDebuggerManager.getInstance(project)
        val currentSession = debuggerManager.currentSession

        if (currentSession?.isPaused != true) {
            Messages.showErrorDialog(project, "No active debugging session found", "Error")
            return
        }

        val stackFrame = currentSession.currentStackFrame ?: run {
            Messages.showErrorDialog(project, "No stack frame available", "Error")
            return
        }

        // Detect source language
        val sourceLanguage = detectSourceLanguage(stackFrame)
        if (sourceLanguage == null) {
            Messages.showErrorDialog(project, "Unsupported file type. Only Kotlin (.kt), Java (.java), and Scala (.scala) files are supported.", "Error")
            return
        }

        println("DEBUG: Detected source language: $sourceLanguage")

        // Show custom loading dialog
        var cancelled = false
        val loadingDialog = showLoadingDialogWithGif(project) { cancelled = true }

        // Create background task
        val task = object : Task.Backgroundable(project, "Generating Test(s) with AI...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Show dialog first
                    SwingUtilities.invokeLater {
                        loadingDialog.isVisible = true
                    }

                    indicator.text = "Preparing debugger data..."
                    indicator.fraction = 0.1
                    updateStatus("Preparing debugger data...")

                    if (cancelled) return

                    // Collect all file-related data in a single read action with timeout
                    val fileData = try {
                        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction<FileData> {
                            val buildSystem = detectBuildSystem(project)
                            FileData(
                                packageName = extractPackageFromCurrentFileInReadAction(stackFrame, sourceLanguage),
                                fileContent = extractFileContentInReadAction(stackFrame),
                                methodName = extractMethodNameInReadAction(stackFrame, sourceLanguage),
                                buildContent = extractBuildContentInReadAction(project, buildSystem),
                                sourceLanguage = sourceLanguage,
                                buildSystem = buildSystem
                            )
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Error in read action: ${e.message}")
                        // Use fallback data
                        FileData("", "", "unknown", "", sourceLanguage, BuildSystem.UNKNOWN)
                    }

                    if (cancelled) return

                    indicator.text = "Capturing debugger state..."
                    indicator.fraction = 0.2
                    updateStatus("Capturing debugger state...")

                    // Simplified debugger capture with timeout
                    val debugOutput = try {
                        captureDebuggerWithCallTreeSimplified(stackFrame, currentSession, fileData)
                    } catch (e: Exception) {
                        println("DEBUG: Error capturing debugger data: ${e.message}")
                        "Error capturing debugger data: ${e.message}"
                    }

                    if (cancelled) return

                    indicator.text = "Sending request to AI provider ($aiProvider)..."
                    indicator.fraction = 0.4
                    updateStatus("Sending request to AI provider ($aiProvider)...")

                    val aiResponse = sendToAI(debugOutput, stackFrame, project, fileData.packageName, indicator, sourceLanguage, fileData.buildSystem)

                    if (cancelled) return

                    indicator.text = "Creating test file..."
                    indicator.fraction = 0.8
                    updateStatus("Creating test file...")

                    val testFilePath = createOrExtendTestFile(project, stackFrame, aiResponse, sourceLanguage, fileData.buildSystem)

                    indicator.text = "Opening test file..."
                    indicator.fraction = 0.9
                    updateStatus("Opening test file...")

                    // Close dialog and open file
                    SwingUtilities.invokeLater {
                        stopLoadingAnimation(loadingDialog)
                        loadingDialog.dispose()
                        statusLabel = null // Clear reference

                        indicator.fraction = 1.0

                        // Open the file with proper write action
                        openFileInIDEWithWriteAction(project, testFilePath)
                    }
                } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                    // User cancelled the operation - cleanup and rethrow
                    SwingUtilities.invokeLater {
                        stopLoadingAnimation(loadingDialog)
                        loadingDialog.dispose()
                        statusLabel = null
                    }
                    throw e // MUST rethrow ProcessCanceledException
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        stopLoadingAnimation(loadingDialog)
                        loadingDialog.dispose()
                        statusLabel = null
                        Messages.showErrorDialog(project, "Error generating test: ${e.message}", "Error")
                    }
                }
            }
        }

        // Run the task with progress indicator
        ProgressManager.getInstance().run(task)
    }

    // Enhanced language detection
    private fun detectSourceLanguage(stackFrame: XStackFrame): SourceLanguage? {
        val fileName = stackFrame.sourcePosition?.file?.name ?: return null
        return when {
            fileName.endsWith(".kt") -> SourceLanguage.KOTLIN
            fileName.endsWith(".java") -> SourceLanguage.JAVA
            fileName.endsWith(".scala") -> SourceLanguage.SCALA
            else -> null
        }
    }

    // Build system detection
    private fun detectBuildSystem(project: com.intellij.openapi.project.Project): BuildSystem {
        val basePath = project.basePath ?: return BuildSystem.UNKNOWN

        return when {
            // Check for Gradle files first (more specific)
            File(basePath, "build.gradle.kts").exists() ||
                    File(basePath, "build.gradle").exists() ||
                    File(basePath, "settings.gradle.kts").exists() ||
                    File(basePath, "settings.gradle").exists() -> {
                println("DEBUG: Detected Gradle build system")
                BuildSystem.GRADLE
            }
            // Check for Maven files
            File(basePath, "pom.xml").exists() -> {
                println("DEBUG: Detected Maven build system")
                BuildSystem.MAVEN
            }
            // Check for SBT files
            File(basePath, "build.sbt").exists() -> {
                println("DEBUG: Detected SBT build system")
                BuildSystem.SBT
            }
            // Check for Bazel files last (most specific check)
            (File(basePath, "WORKSPACE").exists() || File(basePath, "WORKSPACE.bazel").exists()) &&
                    (File(basePath, "BUILD").exists() || File(basePath, "BUILD.bazel").exists()) -> {
                println("DEBUG: Detected Bazel build system")
                BuildSystem.BAZEL
            }
            else -> {
                println("DEBUG: Could not detect build system")
                BuildSystem.UNKNOWN
            }
        }
    }

    // Helper method to update status on EDT
    private fun updateStatus(status: String) {
        SwingUtilities.invokeLater {
            statusLabel?.text = status
            statusLabel?.repaint()
            println("DEBUG: Updated status to: $status")
        }
    }

    // Fixed file opening with proper threading
    private fun openFileInIDEWithWriteAction(project: com.intellij.openapi.project.Project, filePath: String) {
        // Use invokeLaterOnWriteThread as shown in search result [2]
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLaterOnWriteThread {
            try {
                println("DEBUG: Attempting to open file with write thread: $filePath")

                // Refresh the virtual file system - now in proper write-safe context
                com.intellij.openapi.vfs.VirtualFileManager.getInstance().syncRefresh()

                // Find the virtual file using refreshAndFindFileByPath
                val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)

                if (virtualFile != null) {
                    println("DEBUG: Virtual file found, opening in editor")
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    println("DEBUG: Successfully opened file in IDE: $filePath")
                } else {
                    println("DEBUG: Could not find virtual file for: $filePath")
                    Messages.showInfoMessage(project, "Test file was created at:\n$filePath\n\nPlease open it manually.", "File Created")
                }
            } catch (e: Exception) {
                println("DEBUG: Error opening file in IDE: ${e.message}")
                e.printStackTrace()
                Messages.showErrorDialog(project, "Test file created but could not be opened automatically:\n$filePath", "File Created")
            }
        }
    }

    // Simplified debugger capture to avoid hanging
    private fun captureDebuggerWithCallTreeSimplified(currentFrame: XStackFrame, session: com.intellij.xdebugger.XDebugSession, fileData: FileData): String {
        return buildString {
            appendLine("=== DEBUGGER CAPTURE ===")
            appendLine("Current Location: ${currentFrame.sourcePosition?.file?.name}:${currentFrame.sourcePosition?.line}")
            appendLine("Source Language: ${fileData.sourceLanguage}")
            appendLine("Build System: ${fileData.buildSystem.displayName}")
            appendLine()

            // Add build content section with preserved indentation
            if (fileData.buildContent.isNotEmpty()) {
                val buildFileType = when (fileData.buildSystem) {
                    BuildSystem.GRADLE -> "BUILD GRADLE CONTENT"
                    BuildSystem.MAVEN -> "POM.XML CONTENT"
                    BuildSystem.SBT -> "BUILD.SBT CONTENT"
                    BuildSystem.BAZEL -> "BAZEL BUILD CONTENT"
                    BuildSystem.UNKNOWN -> "BUILD CONTENT"
                }
                appendLine("$buildFileType:")
                appendLine("====================")
                val lines = fileData.buildContent.lines()
                lines.subList(0, minOf(50, lines.size)).forEach { line ->
                    appendLine(line)
                }
                appendLine()
            }

            appendLine("EXECUTION INFO:")
            appendLine("===============")
            appendLine("Frame 0:")
            appendLine("  File: ${currentFrame.sourcePosition?.file?.name ?: "unknown"}:${currentFrame.sourcePosition?.line ?: 0}")
            appendLine("  Method: ${fileData.methodName}")

            // Simplified variable extraction with timeout
            try {
                val variables = extractFrameVariablesWithTimeout(currentFrame, 3) // 3 second timeout
                if (variables.isNotEmpty()) {
                    appendLine("  Variables:")
                    val variableList = variables.toList()
                    variableList.subList(0, minOf(100, variableList.size)).forEach { (name, value) ->
                        if (name != "this") {
                            appendLine("    $name = $value")
                        }
                    }
                }
            } catch (e: Exception) {
                appendLine("  Variables: <could not extract - ${e.message}>")
            }

            // Add limited file content
            if (fileData.fileContent.isNotEmpty()) {
                appendLine("  File Content:")
                appendLine("  " + "=".repeat(50))
                val contentLines = fileData.fileContent.lines()
                contentLines.subList(0, minOf(100, contentLines.size)).forEach { line ->
                    appendLine(line)
                }
                appendLine("  " + "=".repeat(50))
            }
        }
    }

    private fun extractFrameVariablesWithTimeout(stackFrame: XStackFrame, timeoutSeconds: Long): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        val latch = CountDownLatch(1)

        stackFrame.computeChildren(object : XCompositeNode {
            override fun addChildren(children: XValueChildrenList, last: Boolean) {
                for (i in 0 until minOf(children.size(), 20)) { // Limit to 20 children
                    val name = children.getName(i)
                    val value = children.getValue(i)
                    variables[name] = extractValueSafely(value, name)
                }
                if (last) latch.countDown()
            }

            override fun tooManyChildren(remaining: Int) { latch.countDown() }
            override fun setAlreadySorted(alreadySorted: Boolean) {}
            override fun setErrorMessage(errorMessage: String) { latch.countDown() }
            override fun setErrorMessage(errorMessage: String, link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) { latch.countDown() }
            override fun setMessage(message: String, icon: javax.swing.Icon?, attributes: com.intellij.ui.SimpleTextAttributes, hyperlink: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) {}
        })

        val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            println("DEBUG: Variable extraction timed out after $timeoutSeconds seconds")
        }
        return variables
    }

    private fun extractValueSafely(xValue: XValue, name: String): String {
        return try {
            if (xValue is JavaValue) {
                val descriptor = xValue.getDescriptor()
                val value = descriptor?.calcValue(null)
                if (value != null) {
                    val actualValue = value.toString()
                    val limitedValue = if (actualValue.length > 200) {
                        actualValue.substring(0, 200) + "..."
                    } else {
                        actualValue
                    }
                    return if (limitedValue.startsWith("\"") && limitedValue.endsWith("\"")) {
                        limitedValue
                    } else {
                        "\"$limitedValue\""
                    }
                }
            }
            "<could not extract>"
        } catch (e: Exception) {
            println("DEBUG: Failed to extract value for '$name': ${e.message}")
            "<extraction error>"
        }
    }

    private fun stopLoadingAnimation(dialog: JDialog) {
        val panel = dialog.contentPane as? JPanel
        val centerComponent = panel?.getComponent(0) as? JLabel
        val timer = centerComponent?.getClientProperty("timer") as? Timer
        timer?.stop()
    }

    data class FileData(
        val packageName: String,
        val fileContent: String,
        val methodName: String,
        val buildContent: String,
        val sourceLanguage: SourceLanguage,
        val buildSystem: BuildSystem
    )

    // Enhanced package extraction for multiple languages
    private fun extractPackageFromCurrentFileInReadAction(currentFrame: XStackFrame, sourceLanguage: SourceLanguage): String {
        return try {
            val sourcePosition = currentFrame.sourcePosition
            if (sourcePosition != null) {
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(sourcePosition.file)
                if (document != null) {
                    val text = document.text
                    val packageRegex = when (sourceLanguage) {
                        SourceLanguage.KOTLIN, SourceLanguage.JAVA, SourceLanguage.SCALA -> Regex("""package\s+([\w.]+)""")
                    }
                    val match = packageRegex.find(text)
                    val packageName = match?.groupValues?.get(1) ?: ""

                    println("DEBUG: Extracted package name for $sourceLanguage: '$packageName'")
                    return packageName
                }
            }
            ""
        } catch (e: Exception) {
            println("DEBUG: Error extracting package name: ${e.message}")
            ""
        }
    }

    private fun extractFileContentInReadAction(frame: XStackFrame): String {
        return try {
            val sourcePosition = frame.sourcePosition
            if (sourcePosition != null) {
                val file = sourcePosition.file
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)
                if (document != null) {
                    return document.text
                }
            }
            ""
        } catch (e: Exception) {
            println("DEBUG: Error extracting file content: ${e.message}")
            ""
        }
    }

    // Enhanced method name extraction for multiple languages
    private fun extractMethodNameInReadAction(frame: XStackFrame, sourceLanguage: SourceLanguage): String {
        return try {
            val sourcePosition = frame.sourcePosition
            if (sourcePosition != null) {
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(sourcePosition.file)
                if (document != null) {
                    val line = sourcePosition.line

                    for (i in line downTo maxOf(0, line - 30)) {
                        val lineText = document.getText(
                            com.intellij.openapi.util.TextRange(
                                document.getLineStartOffset(i),
                                document.getLineEndOffset(i)
                            )
                        ).trim()

                        val methodPattern = when (sourceLanguage) {
                            SourceLanguage.KOTLIN -> Regex("""(private\s+|public\s+|internal\s+)?fun\s+(\w+)\s*\([^)]*\)(\s*:\s*\w+)?""")
                            SourceLanguage.JAVA -> Regex("""(private\s+|public\s+|protected\s+|static\s+)*\s*\w+\s+(\w+)\s*\([^)]*\)""")
                            SourceLanguage.SCALA -> Regex("""(private\s+|protected\s+)?def\s+(\w+)\s*\([^)]*\)(\s*:\s*\w+)?""")
                        }

                        val match = methodPattern.find(lineText)
                        if (match != null) {
                            val fullSignature = match.value
                            println("DEBUG: Found method signature for $sourceLanguage: $fullSignature")
                            return fullSignature
                        }
                    }
                }
            }
            "unknown"
        } catch (e: Exception) {
            println("DEBUG: Error extracting method name: ${e.message}")
            "unknown"
        }
    }

    // Enhanced build content extraction supporting multiple build systems
    private fun extractBuildContentInReadAction(project: com.intellij.openapi.project.Project, buildSystem: BuildSystem): String {
        return try {
            val basePath = project.basePath ?: return ""

            when (buildSystem) {
                BuildSystem.GRADLE -> {
                    val buildGradleKts = File(basePath, "build.gradle.kts")
                    val buildGradle = File(basePath, "build.gradle")
                    when {
                        buildGradleKts.exists() -> buildGradleKts.readText()
                        buildGradle.exists() -> buildGradle.readText()
                        else -> ""
                    }
                }
                BuildSystem.MAVEN -> {
                    val pomXml = File(basePath, "pom.xml")
                    if (pomXml.exists()) pomXml.readText() else ""
                }
                BuildSystem.SBT -> {
                    val buildSbt = File(basePath, "build.sbt")
                    if (buildSbt.exists()) buildSbt.readText() else ""
                }
                BuildSystem.BAZEL -> {
                    // For Bazel, collect multiple relevant files as per search results
                    val bazelFiles = mutableListOf<String>()

                    // WORKSPACE file
                    val workspace = File(basePath, "WORKSPACE")
                    val workspaceBazel = File(basePath, "WORKSPACE.bazel")
                    when {
                        workspaceBazel.exists() -> {
                            bazelFiles.add("=== WORKSPACE.bazel ===")
                            bazelFiles.add(workspaceBazel.readText())
                        }
                        workspace.exists() -> {
                            bazelFiles.add("=== WORKSPACE ===")
                            bazelFiles.add(workspace.readText())
                        }
                    }

                    // BUILD file in root
                    val build = File(basePath, "BUILD")
                    val buildBazel = File(basePath, "BUILD.bazel")
                    when {
                        buildBazel.exists() -> {
                            bazelFiles.add("=== BUILD.bazel ===")
                            bazelFiles.add(buildBazel.readText())
                        }
                        build.exists() -> {
                            bazelFiles.add("=== BUILD ===")
                            bazelFiles.add(build.readText())
                        }
                    }

                    // .bazelproject file (project view file as mentioned in search results)
                    val bazelProject = File(basePath, ".bazelproject")
                    if (bazelProject.exists()) {
                        bazelFiles.add("=== .bazelproject ===")
                        bazelFiles.add(bazelProject.readText())
                    }

                    bazelFiles.joinToString("\n\n")
                }
                BuildSystem.UNKNOWN -> ""
            }
        } catch (e: Exception) {
            println("DEBUG: Error reading build file for $buildSystem: ${e.message}")
            ""
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val debuggerManager = project?.let { XDebuggerManager.getInstance(it) }
        val currentSession = debuggerManager?.currentSession
        e.presentation.isEnabledAndVisible = currentSession?.isPaused == true
    }

    // Enhanced AI prompt for multiple languages and build systems
    private fun sendToAI(debuggerOutput: String, currentFrame: XStackFrame, project: com.intellij.openapi.project.Project, packageName: String, indicator: ProgressIndicator, sourceLanguage: SourceLanguage, buildSystem: BuildSystem): String {
        val apiKey = System.getenv("DESSERT_API_KEY")
            ?: throw Exception("DESSERT_API_KEY environment variable not set")

        indicator.checkCanceled()

        val existingTestContent = getExistingTestContent(project, currentFrame, sourceLanguage, buildSystem)

        val buildSystemInstructions = when (buildSystem) {
            BuildSystem.GRADLE -> "This is a Gradle project. Use appropriate Gradle test configurations."
            BuildSystem.MAVEN -> "This is a Maven project. Use appropriate Maven test configurations."
            BuildSystem.SBT -> "This is an SBT project. Use appropriate SBT test configurations."
            BuildSystem.BAZEL -> "This is a Bazel project. Use appropriate Bazel test targets and BUILD file configurations."
            BuildSystem.UNKNOWN -> "Build system not detected. Use standard testing practices."
        }

        val prompt = if (existingTestContent.isNotEmpty()) {
            """
        |Please extend the existing test class with new unit test(s) in ${sourceLanguage.name} for the method shown in Frame 0
        |
        |EXISTING TEST FILE CONTENT:
        |$existingTestContent
        |
        |NEW DEBUGGER OUTPUT TO ADD:
        |$debuggerOutput
        |
        |BUILD SYSTEM CONTEXT:
        |$buildSystemInstructions
        |
        |NOTICE:
        |====================
        |Please keep test(s) as simple, short, and clear as possible - use Arrange-Act-Assert template.
        |Do not rely on IDE auto-imports - import all required dependencies explicitly.
        |Tests should succeed to compile.
        |====================
        |IMPORTANT: Output only plain text. Do not use markdown formatting. Do not use code blocks with triple backticks (```
        |ALWAYS start the file with the exact package declaration: package $packageName
        """.trimMargin()
        } else {
            """
        |Please create unit test(s) in ${sourceLanguage.name} for the method shown in Frame 0
        |
        |Debugger Output:
        |$debuggerOutput
        |
        |BUILD SYSTEM CONTEXT:
        |$buildSystemInstructions
        |
        |NOTICE:
        |====================
        |Please keep test(s) as simple, short, and clear as possible - use Arrange-Act-Assert template.
        |Do not rely on IDE auto-imports - import all required dependencies explicitly.
        |Tests should succeed to compile.
        |====================
        |IMPORTANT: Output only plain text. Do not use markdown formatting. Do not use code blocks with triple backticks (```). Do not use any markdown syntax. Return only the raw code without any formatting or explanation.
        |ALWAYS start the file with the exact package declaration: package $packageName
        """.trimMargin()
        }

        return when (aiProvider.lowercase()) {
            "openai" -> sendToOpenAI(prompt, apiKey, indicator)
            "claude" -> sendToClaude(prompt, apiKey, indicator)
            "gemini" -> sendToGemini(prompt, apiKey, indicator)
            else -> throw Exception("Unsupported AI provider: $aiProvider")
        }
    }

    private fun sendToOpenAI(prompt: String, apiKey: String, indicator: ProgressIndicator): String {
        updateStatus("Connecting to OpenAI...")
        indicator.checkCanceled()

        val requestBody = OpenAIRequest(
            model = modelName,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            max_tokens = 8192,
            temperature = 0.0
        )

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()

        updateStatus("Waiting for OpenAI response...")
        indicator.checkCanceled()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw Exception("OpenAI API error: ${response.statusCode()} - ${response.body()}")
        }

        val chatResponse = json.decodeFromString<OpenAIResponse>(response.body())
        return chatResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("No response from OpenAI")
    }

    private fun sendToClaude(prompt: String, apiKey: String, indicator: ProgressIndicator): String {
        updateStatus("Connecting to Claude...")
        indicator.checkCanceled()

        val requestBody = ClaudeRequest(
            model = modelName,
            max_tokens = 8192,
            messages = listOf(ChatMessage(role = "user", content = prompt))
        )

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()

        updateStatus("Waiting for Claude response...")
        indicator.checkCanceled()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw Exception("Claude API error: ${response.statusCode()} - ${response.body()}")
        }

        val claudeResponse = json.decodeFromString<ClaudeResponse>(response.body())
        return claudeResponse.content.firstOrNull()?.text
            ?: throw Exception("No response from Claude")
    }

    private fun sendToGemini(prompt: String, apiKey: String, indicator: ProgressIndicator): String {
        updateStatus("Connecting to Gemini...")
        indicator.checkCanceled()

        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                maxOutputTokens = 8192,
                temperature = 0.0
            )
        )

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()

        updateStatus("Waiting for Gemini response...")
        indicator.checkCanceled()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw Exception("Gemini API error: ${response.statusCode()} - ${response.body()}")
        }

        val geminiResponse = json.decodeFromString<GeminiResponse>(response.body())

        val text = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text

        if (text.isNullOrEmpty()) {
            throw Exception("No response from Gemini or empty response")
        }

        return text
    }

    // Enhanced test content reading for multiple languages and build systems
    private fun getExistingTestContent(project: com.intellij.openapi.project.Project, currentFrame: XStackFrame, sourceLanguage: SourceLanguage, buildSystem: BuildSystem): String {
        return try {
            val testFile = getTestFile(project, currentFrame, sourceLanguage, buildSystem)
            if (testFile.exists()) {
                testFile.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            println("DEBUG: Error reading existing test file: ${e.message}")
            ""
        }
    }

    // Enhanced getTestFile method that handles multiple languages and build systems
    private fun getTestFile(project: com.intellij.openapi.project.Project, currentFrame: XStackFrame, sourceLanguage: SourceLanguage, buildSystem: BuildSystem): File {
        val basePath = project.basePath ?: throw Exception("Project base path not found")
        val sourceFileName = currentFrame.sourcePosition?.file?.name ?: "Unknown"
        val testClassName = sourceFileName.removeSuffix(".${sourceLanguage.extension}") + sourceLanguage.testSuffix
        val currentFilePath = currentFrame.sourcePosition?.file?.path ?: ""

        println("DEBUG: Current file path: $currentFilePath")
        println("DEBUG: Project base path: $basePath")
        println("DEBUG: Source language: $sourceLanguage")
        println("DEBUG: Build system: $buildSystem")

        // Check if it's a Kotlin Multiplatform project (only for Kotlin)
        val isKmpProject = sourceLanguage == SourceLanguage.KOTLIN && isKotlinMultiplatformProject(basePath)

        val testDir = if (isKmpProject) {
            // KMP project: use shared/src/[sourceSet]Test/kotlin/generated
            val testSourceSet = determineTestSourceSet(currentFilePath, basePath)
            File(basePath, "shared/src/$testSourceSet/kotlin/generated")
        } else {
            // Regular project: mirror the source structure based on build system
            determineTestDirectoryForRegularProject(currentFilePath, basePath, sourceLanguage, buildSystem)
        }

        testDir.mkdirs()
        println("DEBUG: Using test directory: ${testDir.absolutePath}")

        return File(testDir, "$testClassName.${sourceLanguage.extension}")
    }

    // FIXED: Enhanced test directory determination for multiple languages and build systems
    private fun determineTestDirectoryForRegularProject(currentFilePath: String, basePath: String, sourceLanguage: SourceLanguage, buildSystem: BuildSystem): File {
        val normalizedCurrentPath = currentFilePath.replace("\\", "/")
        val normalizedBasePath = basePath.replace("\\", "/")

        // Remove the base path to get the relative path
        val relativePath = if (normalizedCurrentPath.startsWith(normalizedBasePath)) {
            normalizedCurrentPath.substring(normalizedBasePath.length).removePrefix("/")
        } else {
            normalizedCurrentPath
        }

        println("DEBUG: Relative path: $relativePath")

        // For Bazel projects, use different logic as per search results
        if (buildSystem == BuildSystem.BAZEL) {
            return determineBazelTestDirectory(currentFilePath, basePath, sourceLanguage)
        }

        // Look for different source patterns based on language
        val sourcePatterns = when (sourceLanguage) {
            SourceLanguage.KOTLIN -> listOf("/src/main/kotlin/")
            SourceLanguage.JAVA -> listOf("/src/main/java/")
            SourceLanguage.SCALA -> listOf("/src/main/scala/")
        }

        for (pattern in sourcePatterns) {
            val srcMainIndex = relativePath.indexOf(pattern)
            if (srcMainIndex >= 0) {
                // Extract module path (everything before /src/main/)
                val modulePath = relativePath.substring(0, srcMainIndex)
                val moduleDir = if (modulePath.isNotEmpty()) {
                    File(basePath, modulePath)
                } else {
                    File(basePath)
                }

                println("DEBUG: Module path: $modulePath")
                println("DEBUG: Module dir: ${moduleDir.absolutePath}")

                // FIXED: Create test directory based on language - use src/test/ not src/main/
                val testSubPath = when (sourceLanguage) {
                    SourceLanguage.KOTLIN -> "src/test/kotlin/generated"
                    SourceLanguage.JAVA -> "src/test/java/generated"
                    SourceLanguage.SCALA -> "src/test/scala/generated"
                }

                return File(moduleDir, testSubPath)
            }
        }

        // Fallback: if we can't determine the structure, use project root
        println("DEBUG: Could not determine module structure, using project root")
        val fallbackSubPath = when (sourceLanguage) {
            SourceLanguage.KOTLIN -> "src/test/kotlin/generated"
            SourceLanguage.JAVA -> "src/test/java/generated"
            SourceLanguage.SCALA -> "src/test/scala/generated"
        }
        return File(basePath, fallbackSubPath)
    }

    // Bazel-specific test directory determination
    private fun determineBazelTestDirectory(currentFilePath: String, basePath: String, sourceLanguage: SourceLanguage): File {
        // For Bazel projects, tests are typically in the same package as the source
        // but with different BUILD targets as per search results
        val normalizedCurrentPath = currentFilePath.replace("\\", "/")
        val normalizedBasePath = basePath.replace("\\", "/")

        val relativePath = if (normalizedCurrentPath.startsWith(normalizedBasePath)) {
            normalizedCurrentPath.substring(normalizedBasePath.length).removePrefix("/")
        } else {
            normalizedCurrentPath
        }

        // Extract the directory containing the source file
        val sourceDir = File(relativePath).parent ?: ""
        val testDir = File(basePath, "$sourceDir/generated")

        println("DEBUG: Bazel test directory: ${testDir.absolutePath}")
        return testDir
    }

    private fun isKotlinMultiplatformProject(basePath: String): Boolean {
        return try {
            val sharedDir = File(basePath, "shared")
            if (!sharedDir.exists()) {
                return false
            }

            val sharedBuildFile = File(basePath, "shared/build.gradle.kts")
            val rootBuildFile = File(basePath, "build.gradle.kts")

            val buildFileContent = when {
                sharedBuildFile.exists() -> sharedBuildFile.readText()
                rootBuildFile.exists() -> rootBuildFile.readText()
                else -> ""
            }

            val isMultiplatform = buildFileContent.contains("kotlin(\"multiplatform\")") ||
                    buildFileContent.contains("kotlin('multiplatform')") ||
                    buildFileContent.contains("multiplatform")

            val commonTestDir = File(basePath, "shared/src/commonTest")
            val hasCommonTest = commonTestDir.exists()

            println("DEBUG: KMP detection - multiplatform in build: $isMultiplatform, commonTest exists: $hasCommonTest")

            return isMultiplatform || hasCommonTest
        } catch (e: Exception) {
            println("DEBUG: Error detecting KMP project: ${e.message}")
            false
        }
    }

    private fun determineTestSourceSet(currentFilePath: String, basePath: String): String {
        return when {
            currentFilePath.contains("/desktopMain/") -> "desktopTest"
            currentFilePath.contains("/androidMain/") -> "androidTest"
            currentFilePath.contains("/iosMain/") -> "iosTest"
            currentFilePath.contains("/jvmMain/") -> "jvmTest"
            currentFilePath.contains("/jsMain/") -> "jsTest"
            currentFilePath.contains("/commonMain/") -> "commonTest"
            else -> {
                println("DEBUG: Could not determine source set from path: $currentFilePath, using commonTest")
                "commonTest"
            }
        }
    }

    private fun createOrExtendTestFile(project: com.intellij.openapi.project.Project, currentFrame: XStackFrame, testContent: String, sourceLanguage: SourceLanguage, buildSystem: BuildSystem): String {
        val testFile = getTestFile(project, currentFrame, sourceLanguage, buildSystem)

        testFile.writeText(testContent)

        val absolutePath = testFile.absolutePath
        println("Test file created/extended: $absolutePath")

        return absolutePath
    }

    // Data classes for different AI providers
    @Serializable
    data class OpenAIRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val max_tokens: Int,
        val temperature: Double
    )

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String
    )

    @Serializable
    data class OpenAIResponse(
        val choices: List<Choice>
    )

    @Serializable
    data class Choice(
        val message: ChatMessage
    )

    @Serializable
    data class ClaudeRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<ChatMessage>
    )

    @Serializable
    data class ClaudeResponse(
        val content: List<ClaudeContent>
    )

    @Serializable
    data class ClaudeContent(
        val text: String
    )

    @Serializable
    data class GeminiRequest(
        val contents: List<GeminiContent>,
        val generationConfig: GeminiGenerationConfig
    )

    @Serializable
    data class GeminiContent(
        val parts: List<GeminiPart>? = null,
        val role: String? = null
    )

    @Serializable
    data class GeminiPart(
        val text: String? = null
    )

    @Serializable
    data class GeminiGenerationConfig(
        val maxOutputTokens: Int,
        val temperature: Double
    )

    @Serializable
    data class GeminiResponse(
        val candidates: List<GeminiCandidate>
    )

    @Serializable
    data class GeminiCandidate(
        val content: GeminiContent
    )
}
