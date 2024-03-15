package afp.file;

import org.afplib.io.AfpInputStream;
import org.afplib.io.AfpOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.util.Objects.requireNonNull;

public final class AfpFiles {

    public static AfpInputStream newAfpBufferedInputStream(Path path) throws FileNotFoundException {
        return neeAfpInputStreamFrom(new BufferedInputStream(newFileInputStream(path)));
    }

    private static AfpInputStream neeAfpInputStreamFrom(InputStream inputStream) {
        return new AfpInputStream(inputStream);
    }

    public static AfpInputStream newAfpInputStream(Path path) throws FileNotFoundException {
        return neeAfpInputStreamFrom(newFileInputStream(path));
    }

    private static FileInputStream newFileInputStream(Path path) throws FileNotFoundException {
        requireNonNull(path, "Path is required");
        return new FileInputStream(path.toFile());
    }

    public static AfpOutputStream newAfpBufferedOutputStream(Path path) throws IOException {
        return new AfpOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.APPEND)));
    }

    private AfpFiles() {
    }
}
