package afp.file.combine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AFPCombineTest {

    private static final String START_AFP = "/testdata/start.afp";
    private static final String ENDE_AFP = "/testdata/ende.afp";
    private static final String EXPECTED_OUTPUT = "/testdata/expected_output.afp";

    @Test
    void generate_output() throws IOException {
        final Path output = Files.createTempFile("out", "afp");
        getResourcePath(START_AFP).ifPresent(start ->
                getResourcePath(ENDE_AFP).ifPresent(ende -> {
                    AFPCombine combine = new AFPCombine(output.toAbsolutePath().toString(),
                            Stream.of(start, start, ende, ende)
                                    .map(path -> path.toAbsolutePath().toString())
                                    .toArray(String[]::new));
                    try {
                        combine.run();
                        getResourcePath(EXPECTED_OUTPUT).ifPresent(expectedOutput ->
                                assertThat(expectedOutput).hasSameBinaryContentAs(output));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));


    }

    private Optional<Path> getResourcePath(String name) {
        return Optional.ofNullable(getClass().getResource(name)).map(URL::getPath).map(Paths::get);
    }
}
