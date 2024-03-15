package afp.file;

import org.afplib.io.AfpInputStream;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;

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

    private AfpFiles() {
    }
}
