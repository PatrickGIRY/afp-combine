package afp.file;

import org.afplib.afplib.BRG;
import org.afplib.base.SF;
import org.afplib.io.AfpInputStream;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class AFPFileReader {
    private final Path path;

    public AFPFileReader(Path path) {

        this.path = path;
    }

    public void read(Consumer<Context> consumer) {
        try (FileInputStream fin = new FileInputStream(path.toFile());
             AfpInputStream ain = new AfpInputStream(new BufferedInputStream(fin))) {
            SF sf;
            long index = 0;
            while ((sf = ain.readStructuredField()) != null) {
               final Context context = new Context(path.getFileName().toString(), index, ain.getCurrentOffset(), sf);
               consumer.accept(context);
               index++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class Context {
        private final String filename;
        private final long index;
        private final long offset;
        private final SF sf;

        public Context(String filename, long index, long offset, SF sf) {
            this.filename = filename;
            this.index = index;
            this.offset = offset;
            this.sf = sf;
        }

        public String filename() {
            return filename;
        }

        public long index() {
            return index;
        }

        public long offset() {
            return offset;
        }

        public <T extends SF> T structuredField(Class<T> sfType) {
            return sfType.cast(sf);
        }
    }
}
