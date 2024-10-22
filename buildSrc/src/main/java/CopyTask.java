import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class CopyTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @Input
    public abstract Property<String> getDestination();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void run() {
        final Path outputDirPath = this.getOutputDirectory().get().getAsFile().toPath();
        try {
            try (final Stream<Path> walk = Files.walk(outputDirPath)) {
                for (final Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }

            final Path destFile = outputDirPath.resolve(this.getDestination().get());
            Files.createDirectories(destFile.getParent());

            Files.copy(this.getInputFile().get().getAsFile().toPath(), destFile);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
