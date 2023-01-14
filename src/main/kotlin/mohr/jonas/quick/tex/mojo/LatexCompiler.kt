package mohr.jonas.quick.tex.mojo

import com.eclipsesource.json.Json
import mohr.jonas.quick.tex.Document
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

@Suppress("unused")
@Mojo(name = "compile-latex")
class LatexCompiler : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    private lateinit var project: MavenProject

    @Parameter(defaultValue = "pdflatex", required = false)
    private lateinit var pdflatex: String

    private lateinit var javaClassFolder: Path
    private lateinit var texFolder: Path
    private lateinit var pdfFolder: Path

    private val processes = mutableListOf<Process>()

    override fun execute() {
        checkCompiler()
        javaClassFolder = Path.of(project.build.outputDirectory)
        texFolder = Path.of(project.build.outputDirectory).parent.resolve("tex")
        pdfFolder = Path.of(project.build.outputDirectory).parent.resolve("pdf")
        Files.createDirectories(texFolder)
        Files.createDirectories(pdfFolder)
        val loader = URLClassLoader(arrayOf(javaClassFolder.toUri().toURL()), this.javaClass.classLoader)
        val documents = Json.parse(Files.readString(findFileIndex())).asArray().map { loadDocument(it.asString(), loader) }
        documents.map {
            val texFilePath = texFolder.resolve("${it.javaClass.canonicalName}.tex")
            Files.writeString(texFilePath, it.get().toString())
            texFilePath
        }.map { compileTex(it, pdfFolder) }
        while (processes.any { it.isAlive }) Thread.sleep(10)
    }

    private fun loadDocument(name: String, loader: ClassLoader): Document {
        try {
            return loader.loadClass(name).newInstance() as Document
        } catch (e: ClassNotFoundException) {
            throw MojoFailureException("Unable to find class $name")
        }
    }

    private fun checkCompiler() {
        try {
            if (ProcessBuilder(pdflatex, "--version").start().waitFor() != 0) throw MojoExecutionException("Unable to run pdflatex")
        } catch (e: IOException) {
            throw MojoExecutionException("Unable to find pdflatex")
        }
    }

    private fun compileTex(texFile: Path, outPath: Path) {
        val compileRootPath = Path.of(project.build.directory)
        val relativeTexFile = texFile.relativeTo(compileRootPath)
        val relativeOutPath = outPath.relativeTo(compileRootPath)
        log.info(compileRootPath.pathString)
        log.info(relativeTexFile.pathString.replace("\\", "/"))
        log.info(relativeOutPath.pathString)
        processes.add(
            ProcessBuilder(pdflatex, "-interaction", "nonstopmode", "-output-directory", relativeOutPath.pathString, relativeTexFile.pathString.replace("\\", "/")).directory(
                compileRootPath.toFile()
            ).inheritIO().start()
        )
    }

    private fun findFileIndex(): Path {
        val indexes = project.compileSourceRoots.filter { Files.exists(Path.of(it as String).resolve("FileIndex.json")) }.toList()
        if (indexes.isEmpty()) throw MojoFailureException("No FileIndex.json in ${project.compileSourceRoots} found")
        if (indexes.size > 1) throw MojoFailureException("More than one FileIndex.json found: $indexes")
        return Path.of(indexes[0] as String).resolve("FileIndex.json")
    }
}