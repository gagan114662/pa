package com.blurr.voice.v2.fs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Manages a sandboxed file system for the agent to store and retrieve information.
 *
 * This class creates a dedicated, private directory for an agent session, preventing
 * interference with other app data or previous agent runs. It provides safe, asynchronous
 * methods for file manipulation.
 *
 * @param context The Android application context, used to access the app's private storage.
 * @param workspaceName The unique name for the agent's sandboxed directory. Defaults to "agent_workspace".
 */
class FileSystem(context: Context, workspaceName: String = "agent_workspace") {

    val workspaceDir: File

    // Pre-defined file handles for convenience
    private val todoFile: File
    private val resultsFile: File

    companion object {
        private const val TAG = "FileSystem"
    }

    init {
        val baseDir = context.filesDir
        workspaceDir = File(baseDir, workspaceName)

        // Safety Check: For a new session, the workspace should not already exist.
        if (workspaceDir.exists()) {
            // Instead of throwing an error, a more robust approach for Android might be to clear it.
            // For now, we will log a warning. For a strict safety check, you could throw an exception.
            Log.w(TAG, "Workspace directory '$workspaceName' already exists. Reusing it.")
            // throw IllegalStateException("Workspace directory '$workspaceName' already exists. Please clear it or use a new name.")
        }

        // Ensure the directory exists
        workspaceDir.mkdirs()

        // Initialize default files
        todoFile = File(workspaceDir, "todo.md")
        resultsFile = File(workspaceDir, "results.md")

        try {
            if (!todoFile.exists()) todoFile.createNewFile()
            if (!resultsFile.exists()) resultsFile.createNewFile()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create initial files in workspace.", e)
            throw e // Re-throw as this is a critical failure
        }
    }

    /**
     * A private helper to validate that filenames are safe and have an allowed extension.
     * Allowed pattern: alphanumeric characters, underscores, and hyphens, ending in .md or .txt.
     */
    private fun isValidFilename(fileName: String): Boolean {
        // Regex to match safe filenames
        val pattern = Regex("^[a-zA-Z0-9_-]+\\.(md|txt)$")
        return fileName.matches(pattern)
    }

    /**
     * Asynchronously reads the content of a file from the agent's workspace.
     * This operation is performed on a background thread.
     *
     * @param fileName The name of the file to read (e.g., "results.md").
     * @return The content of the file as a String, or an error message if it fails.
     */
    suspend fun readFile(fileName: String): String = withContext(Dispatchers.IO) {
        if (!isValidFilename(fileName)) {
            return@withContext "Error: Invalid filename. Only alphanumeric .md or .txt files are allowed."
        }

        val file = File(workspaceDir, fileName)
        if (!file.exists()) {
            return@withContext "Error: File '$fileName' not found."
        }

        return@withContext try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading file: $fileName", e)
            "Error: Could not read file '$fileName'."
        }
    }

    /**
     * Asynchronously writes (or overwrites) content to a file in the agent's workspace.
     * This operation is performed on a background thread.
     *
     * @param fileName The name of the file to write to (e.g., "todo.md").
     * @param content The new content for the file.
     * @return True if the write was successful, false otherwise.
     */
    suspend fun writeFile(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFilename(fileName)) {
            Log.e(TAG, "Invalid filename for write: $fileName")
            return@withContext false
        }

        return@withContext try {
            val file = File(workspaceDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to file: $fileName", e)
            false
        }
    }

    /**
     * Asynchronously appends content to an existing file in the agent's workspace.
     * This operation is performed on a background thread.
     *
     * @param fileName The name of the file to append to.
     * @param content The content to append.
     * @return True if the append was successful, false otherwise.
     */
    suspend fun appendFile(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFilename(fileName)) {
            Log.e(TAG, "Invalid filename for append: $fileName")
            return@withContext false
        }

        val file = File(workspaceDir, fileName)
        if (!file.exists()) {
            Log.w(TAG, "File '$fileName' not found for append. A new file will be created.")
        }

        return@withContext try {
            file.appendText(content, Charsets.UTF_8)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error appending to file: $fileName", e)
            false
        }
    }

    /**
     * Provides a string summary of the files in the workspace, including their line counts.
     * This is intended for use in the agent's prompt. This is a blocking I/O call
     * but is expected to be fast as the number of files is small.
     *
     * @return A formatted string describing the file system state.
     */
    fun describe(): String {
        return try {
            val files = workspaceDir.listFiles { file -> file.isFile }
            if (files.isNullOrEmpty()) {
                return "The file system is empty."
            }
            files.joinToString("\n") { file ->
                try {
                    val lineCount = file.readLines().size
                    "- ${file.name} — $lineCount lines"
                } catch (e: IOException) {
                    "- ${file.name} — [error reading file]"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error describing file system", e)
            "Error: Could not describe file system."
        }
    }

    /**
     * A synchronous helper to quickly get the contents of the 'todo.md' file.
     *
     * @return The content of todo.md, or an empty string if it cannot be read.
     */
    fun getTodoContents(): String {
        return try {
            if (todoFile.exists()) {
                todoFile.readText(Charsets.UTF_8)
            } else {
                ""
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read todo.md", e)
            ""
        }
    }
}