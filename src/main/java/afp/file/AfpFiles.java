package afp.file;

import org.afplib.io.AfpInputStream;
import org.afplib.io.AfpOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class AfpFiles {

    public static AfpInputStream newAfpBufferedInputStream(Path path) throws FileNotFoundException {
        return newAfpInputStreamFrom(new BufferedInputStream(newFileInputStream(path)));
    }

    private static AfpInputStream newAfpInputStreamFrom(InputStream inputStream) {
        return new AfpInputStream(inputStream);
    }

    public static AfpInputStream newAfpInputStream(Path path) throws FileNotFoundException {
        return newAfpInputStreamFrom(newFileInputStream(path));
    }

    private static FileInputStream newFileInputStream(Path path) throws FileNotFoundException {
        requireNonNull(path, "Path is required");
        return new FileInputStream(path.toFile());
    }

    public static AfpOutputStream newAfpBufferedOutputStream(Path path, OpenOption... options) throws IOException {
        return new AfpOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path, options)));
    }

    private AfpFiles() {
    }
}
