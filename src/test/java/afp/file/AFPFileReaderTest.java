package afp.file;

import org.afplib.AfpBuilder;
import org.afplib.afplib.*;
import org.afplib.base.BasePackage;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AFPFileReaderTest {
    private static final String FILENAME = "start.afp";
    private static final String START_AFP = "/testdata/" + FILENAME;
    @Test
    void read_afp_file() {
        getResourcePath().ifPresent(resourcePath -> {
            List<AFPFileReader.Context> allContexts = new ArrayList<>();
            final AFPFileReader afpFileReader = new AFPFileReader(resourcePath);

            afpFileReader.read(allContexts::add);
            assertThat(allContexts).hasSize(64);

          /*  assertThat(allContexts)
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(0L);
                        assertThat(context.offset()).isEqualTo(0L);
                        final BRG beginResourceGroup = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13871302)
                                .with(BasePackage.SF__LENGTH, 17)
                                .with(AfplibPackage.BRG__RGRP_NAME, "<U+009F><U+009F><U+009F><U+009F><U+009F><U+009F><U+009F><U+009F>")
                                .with(BasePackage.SF__RAW_DATA, null)
                                .create(BRG.class);
                        assertThat(context.structuredField(BRG.class)).usingRecursiveComparison().isEqualTo(beginResourceGroup);
                    }, Index.atIndex(0))
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(1);
                        assertThat(context.offset()).isEqualTo(1);
                        final BRS beginResource = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13871310)
                                .with(BasePackage.SF__LENGTH, 29)
                                .with(AfplibPackage.BRS__RS_NAME, "F1VORNAL")
                                .with(BasePackage.SF__NUMBER, 1L)
                                .with(BasePackage.SF__OFFSET, 17L)
                                .withMember(new AfpBuilder()
                                        .with(BasePackage.TRIPLET__TRIPLET_LENGTH, 10)
                                        .with(BasePackage.TRIPLET__FILE_OFFSET, 19L)
                                        .with(AfplibPackage.RESOURCE_OBJECT_TYPE__OBJ_TYPE, 254)
                                        .with(AfplibPackage.RESOURCE_OBJECT_TYPE__CON_DATA, new byte[]{0, 0, 0, 0, 0, 0, 0})
                                        .with(BasePackage.TRIPLET__TRIPLET_ID, 33)
                                        .create(ResourceObjectType.class))
                                .create(BRS.class);
                        assertThat(context.structuredField(BRS.class)).usingRecursiveComparison().isEqualTo(beginResource);
                    }, Index.atIndex(1))
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(1);
                        assertThat(context.offset()).isEqualTo(1);
                        final BFM beginFormMap = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13871309)
                                .with(BasePackage.SF__LENGTH, 9)
                                .with(BasePackage.SF__NUMBER, 2L)
                                .with(BasePackage.SF__OFFSET, 46L)
                                .create(BFM.class);
                        assertThat(context.structuredField(BFM.class)).usingRecursiveComparison().isEqualTo(beginFormMap);
                    }, Index.atIndex(2))
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(1);
                        assertThat(context.offset()).isEqualTo(1);
                        final BDG beginDocumentEnvironmentGroup = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13871300)
                                .with(BasePackage.SF__LENGTH, 9)
                                .with(BasePackage.SF__NUMBER, 3L)
                                .with(BasePackage.SF__OFFSET, 55L)
                                .create(BDG.class);
                        assertThat(context.structuredField(BDG.class)).usingRecursiveComparison().isEqualTo(beginDocumentEnvironmentGroup);
                    }, Index.atIndex(3))
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(1);
                        assertThat(context.offset()).isEqualTo(1);
                        final PGP pagePosition = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13873583)
                                .with(BasePackage.SF__LENGTH, 20)
                                .with(BasePackage.SF__NUMBER, 4L)
                                .with(BasePackage.SF__OFFSET, 64L)
                                .with(AfplibPackage.PGP__CONSTANT, 1)
                                .withMember(new AfpBuilder()
                                        .with(AfplibPackage.PGP__CONSTANT, 1)
                                        .with(AfplibPackage.PGPRG__PGORIENT, 0)
                                        .with(AfplibPackage.PGPRG__RG_LENGTH, 10)
                                        .with(AfplibPackage.PGPRG__SHSIDE, 0)
                                        .with(AfplibPackage.PGPRG__XM_OSET, 0)
                                        .with(AfplibPackage.PGPRG__YM_OSET, 0)
                                        .create(PGPRG.class))
                                .create(PGP.class);
                        assertThat(context.structuredField(PGP.class)).usingRecursiveComparison().isEqualTo(pagePosition);
                    }, Index.atIndex(4))
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(1);
                        assertThat(context.offset()).isEqualTo(1);
                        final MDD mediumDescription = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13870728)
                                .with(BasePackage.SF__LENGTH, 25)
                                .with(BasePackage.SF__NUMBER, 5L)
                                .with(BasePackage.SF__OFFSET, 84L)
                                .with(AfplibPackage.MDD__MDD_FLGS, 0)
                                .with(AfplibPackage.MDD__XM_BASE, 0)
                                .with(AfplibPackage.MDD__YM_BASE, 0)
                                .with(AfplibPackage.MDD__XM_UNITS, 2400)
                                .with(AfplibPackage.MDD__YM_UNITS, 2400)
                                .with(AfplibPackage.MDD__XM_SIZE, 0)
                                .with(AfplibPackage.MDD__YM_SIZE, 0)
                                .with(AfplibPackage.MDD__MDD_FLGS, 0)
                                .withMember(new AfpBuilder()
                                        .with(BasePackage.TRIPLET__TRIPLET_LENGTH, 3)
                                        .with(BasePackage.TRIPLET__FILE_OFFSET, 22L)
                                        .with(BasePackage.TRIPLET__TRIPLET_ID, 104)
                                        .with(BasePackage.TRIPLET__TRIPLET_NUMBER, 0)
                                        .with(AfplibPackage.MEDIUM_ORIENTATION__MED_ORIENT, 0)
                                        .create(MediumOrientation.class))
                                .create(MDD.class);
                        assertThat(context.structuredField(MDD.class)).usingRecursiveComparison().isEqualTo(mediumDescription);
                    }, Index.atIndex(5))
                    .satisfies(context -> {
                        assertThat(context.filename()).isEqualTo(FILENAME);
                        assertThat(context.index()).isEqualTo(1);
                        assertThat(context.offset()).isEqualTo(1);
                        final EDG endDocumentEnvironmentGroup = new AfpBuilder()
                                .with(BasePackage.SF__ID, 13871556)
                                .with(BasePackage.SF__LENGTH, 9)
                                .with(BasePackage.SF__NUMBER, 6L)
                                .with(BasePackage.SF__OFFSET, 109L)
                                .create(EDG.class);
                        assertThat(context.structuredField(EDG.class)).usingRecursiveComparison().isEqualTo(endDocumentEnvironmentGroup);
                    }, Index.atIndex(6));
*/
        });
    }

    private Optional<Path> getResourcePath() {
        return Optional.ofNullable(getClass().getResource(START_AFP)) //
                .map(URL::getPath) //
                .map(Paths::get);
    }

}