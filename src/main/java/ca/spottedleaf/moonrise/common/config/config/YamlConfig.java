package ca.spottedleaf.moonrise.common.config.config;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class YamlConfig<T> {

    public final TypeAdapterRegistry typeAdapters;

    private final Class<? extends T> clazz;

    public volatile T config;

    private final Yaml yaml;

    public YamlConfig(final Class<? extends T> clazz, final T dfl) throws Exception {
        this(clazz, dfl, new TypeAdapterRegistry());
    }

    public YamlConfig(final Class<? extends T> clazz, final T dfl, final TypeAdapterRegistry registry) throws Exception {
        this.clazz = clazz;
        this.config = dfl;
        this.typeAdapters = registry;
        this.typeAdapters.makeAdapter(clazz);

        final LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setProcessComments(true);

        final DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setProcessComments(true);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        this.yaml = new Yaml(new YamlConstructor(loaderOptions), new YamlRepresenter(dumperOptions), dumperOptions, loaderOptions);
    }

    public void load(final File file) throws IOException {
        try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            this.load(is);
        }
    }

    public void load(final InputStream is) throws IOException {
        final Object serialized = this.yaml.load(new InputStreamReader(is, StandardCharsets.UTF_8));

        this.config = (T)this.typeAdapters.deserialize(serialized, this.clazz);
    }

    public void save(final File file) throws IOException {
        this.save(file, "");
    }

    public void save(final File file, final String header) throws IOException {
        if (file.isDirectory()) {
            throw new IOException("File is a directory");
        }

        final File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        tmp.delete();
        tmp.createNewFile();
        try {
            try (final OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {
                this.save(os, header);
            }

            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            tmp.delete();
        }
    }

    public void save(final OutputStream os) throws IOException {
        os.write(this.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    public void save(final OutputStream os, final String header) throws IOException {
        os.write(this.saveToString(header).getBytes(StandardCharsets.UTF_8));
    }

    public String saveToString() {
        return this.yaml.dump(this.typeAdapters.serialize(this.config, this.clazz));
    }

    public String saveToString(final String header) {
        if (header.isBlank()) {
            return this.saveToString();
        }

        final StringBuilder ret = new StringBuilder();

        for (final String line : header.split("\n")) {
            ret.append("# ").append(line).append('\n');
        }

        ret.append('\n');

        return ret.append(this.saveToString()).toString();
    }

    private static final class YamlConstructor extends Constructor {

        public YamlConstructor(final LoaderOptions loadingConfig) {
            super(loadingConfig);
        }
    }

    private static final class YamlRepresenter extends Representer {

        public YamlRepresenter(final DumperOptions options) {
            super(options);

            this.representers.put(TypeAdapterRegistry.CommentedData.class, new CommentedDataRepresenter());

        }

        private final class CommentedDataRepresenter implements Represent {

            @Override
            public Node representData(final Object data0) {
                final TypeAdapterRegistry.CommentedData commentedData = (TypeAdapterRegistry.CommentedData)data0;

                final Node node = YamlRepresenter.this.representData(commentedData.data);

                final List<CommentLine> comments = new ArrayList<>();

                for (final String line : commentedData.comment.split("\n")) {
                    comments.add(new CommentLine(null, null, " ".concat(line.trim()), CommentType.BLOCK));
                }

                node.setBlockComments(comments);

                return node;
            }
        }
    }
}
