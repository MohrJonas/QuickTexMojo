package mohr.jonas.quick.tex.mojo

import io.methvin.watcher.DirectoryChangeEvent.EventType.*
import io.methvin.watcher.DirectoryWatcher
import org.apache.maven.Maven
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.nio.file.Path
import kotlin.io.path.exists

@Suppress("unused")
@Mojo(name = "watch-latex")
class LatexCompilerWatcher : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    private lateinit var project: MavenProject

    @Parameter(defaultValue = "\${session}", required = true, readonly = true)
    private lateinit var session: MavenSession

    @Component
    private lateinit var maven: Maven

    override fun execute() {
        val srcDirectories = project.compileSourceRoots
            .map { Path.of(it as String) }
            .filter { it.exists() }
        val watchers = srcDirectories.map {
            DirectoryWatcher.builder()
                .path(it)
                .listener {
                    when (it.eventType()) {
                        CREATE -> maven.execute(
                            DefaultMavenExecutionRequest.copy(session.request).setGoals(listOf("kotlin:compile", "resources:copy-resources", "QuickTexPlugin:compile-latex"))
                        )

                        MODIFY -> maven.execute(
                            DefaultMavenExecutionRequest.copy(session.request).setGoals(listOf("kotlin:compile", "resources:copy-resources", "QuickTexPlugin:compile-latex"))
                        )

                        else -> Unit
                    }
                }
                .build()
        }
        watchers.forEach { it.watchAsync() }
        while (true) {
            Thread.sleep(2000L)
        }
    }
}