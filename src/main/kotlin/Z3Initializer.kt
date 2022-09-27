import java.io.File
import java.nio.file.Files.createTempDirectory
import java.util.Locale

object Z3Initializer {
    private val libraries = listOf("libz3", "libz3java")
    private val vcWinLibrariesToLoadBefore = listOf("vcruntime140", "vcruntime140_1")
    private val supportedArchs = setOf("amd64", "x86_64")
    private val initializeCallback by lazy {
        System.setProperty("z3.skipLibraryLoad", "true")
        val arch = System.getProperty("os.arch")
        require(arch in supportedArchs) { "Not supported arch: $arch" }

        val osProperty = System.getProperty("os.name").lowercase(Locale.getDefault())
        val (ext, allLibraries) = when {
            osProperty.startsWith("windows") -> ".dll" to vcWinLibrariesToLoadBefore + libraries
            osProperty.startsWith("linux") -> ".so" to libraries
            osProperty.startsWith("mac") -> ".dylib" to libraries
            else -> error("Unknown OS: $osProperty")
        }
        val libZ3DllUrl = Z3Initializer::class.java
            .classLoader
            .getResource("lib/x64/libz3.dll") ?: error("Can't find native library folder")
        // can't take resource of parent folder right here because in obfuscated jar parent folder
        // can be missed (e.g., in case if obfuscation was applied)

        val libFolder: String?
        if (libZ3DllUrl.toURI().scheme == "jar") {
            val tempDir = createTempDirectory("_z3_libs").toFile()

            allLibraries.forEach { name ->
                Z3Initializer::class.java
                    .classLoader
                    .getResourceAsStream("lib/x64/$name$ext")
                    ?.use { input ->
                        File(tempDir, "$name$ext")
                            .outputStream()
                            .use { output -> input.copyTo(output) }
                    } ?: error("Can't find file: $name$ext")
            }

            libFolder = "$tempDir"
        } else {
            libFolder = File(libZ3DllUrl.file).parent
        }

        allLibraries.forEach { System.load("$libFolder/$it$ext") }
    }

    fun init() {
        initializeCallback
    }
}
