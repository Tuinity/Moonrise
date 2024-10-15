import dev.architectury.at.AccessChange;
import dev.architectury.at.AccessTransform;
import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.ModifierChange;
import dev.architectury.at.io.AccessTransformFormats;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

@CacheableTask
public abstract class Aw2AtTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    public abstract ProjectLayout getLayout();

    public static TaskProvider<Aw2AtTask> configureDefault(
        final Project project,
        final File awFile,
        final SourceSet sourceSet
    ) {
        final TaskProvider<Aw2AtTask> aw2at = project.getTasks().register("aw2at", Aw2AtTask.class, task -> {
            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("aw2at/files/accesstransformer.cfg"));
            task.getInputFile().set(awFile);
        });

        final TaskProvider<CopyTask> copyTask = project.getTasks().register("copyAt", CopyTask.class, copy -> {
            copy.getInputFile().set(aw2at.flatMap(Aw2AtTask::getOutputFile));
            copy.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("aw2at/dir"));
            copy.getDestination().set("META-INF/accesstransformer.cfg");
        });

        sourceSet.resources(resources -> {
            resources.srcDir(copyTask.flatMap(CopyTask::getOutputDirectory));
        });

        return aw2at;
    }

    @TaskAction
    public void run() {
        try (final BufferedReader reader = Files.newBufferedReader(this.getInputFile().get().getAsFile().toPath())) {
            final AccessTransformSet accessTransformSet = toAccessTransformSet(reader);
            Files.deleteIfExists(this.getOutputFile().get().getAsFile().toPath());
            Files.createDirectories(this.getOutputFile().get().getAsFile().toPath().getParent());
            AccessTransformFormats.FML.write(this.getOutputFile().get().getAsFile().toPath(), accessTransformSet);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Below methods are heavily based on architectury-loom Aw2At class (MIT licensed)
    /*
    MIT License

    Copyright (c) 2016 FabricMC

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
     */

    public static AccessTransformSet toAccessTransformSet(final BufferedReader reader) throws IOException {
        AccessTransformSet atSet = AccessTransformSet.create();

        new AccessWidenerReader(new AccessWidenerVisitor() {
            @Override
            public void visitClass(final String name, final AccessWidenerReader.AccessType access, final boolean transitive) {
                atSet.getOrCreateClass(name).merge(toAt(access));
            }

            @Override
            public void visitMethod(final String owner, final String name, final String descriptor, final AccessWidenerReader.AccessType access, final boolean transitive) {
                atSet.getOrCreateClass(owner).mergeMethod(MethodSignature.of(name, descriptor), toAt(access));
            }

            @Override
            public void visitField(final String owner, final String name, final String descriptor, final AccessWidenerReader.AccessType access, final boolean transitive) {
                atSet.getOrCreateClass(owner).mergeField(name, toAt(access));
            }
        }).read(reader);

        return atSet;
    }

    public static AccessTransform toAt(final AccessWidenerReader.AccessType access) {
        return switch (access) {
            case ACCESSIBLE -> AccessTransform.of(AccessChange.PUBLIC);
            case EXTENDABLE, MUTABLE -> AccessTransform.of(AccessChange.PUBLIC, ModifierChange.REMOVE);
        };
    }
}
