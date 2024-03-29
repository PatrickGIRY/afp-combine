package afp.file.combine;

import afp.file.AfpFiles;
import org.afplib.ResourceKey;
import org.afplib.afplib.*;
import org.afplib.base.SF;
import org.afplib.base.Triplet;
import org.afplib.io.AfpFilter;
import org.afplib.io.AfpInputStream;
import org.afplib.io.AfpOutputStream;
import org.afplib.io.Filter;
import org.afplib.io.Filter.STATE;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

public class AfpCombine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AfpCombine.class);

    static class Resource {
        long start, end, ersPos;
        byte[] content;
        String hash;
    }

    static class MediumMap {
        long start, end;
        byte[] content;
        LinkedList<SF> sfs = new LinkedList<>();
        String hash;
    }

    static class InputFile {
        private final Path path;
        List<ResourceKey> resources = new LinkedList<>();
        Map<ResourceKey, Resource> filePos = new HashMap<>();
        Map<ResourceKey, String> renamings = new HashMap<>();
        Map<String, String> renameIMM = new HashMap<>();
        long documentStart;
        LinkedList<SF> formdef = new LinkedList<>();
        LinkedList<String> mmNames = new LinkedList<>();
        Map<String, MediumMap> mediumMaps = new HashMap<>();

        public InputFile(Path path) {
            this.path = path;
        }

        String getName() {
            return path.getFileName().toString();
        }
    }

    private final Path outFile;
    private final InputFile[] inputFiles;
    private final MessageDigest algorithm;
    private final LinkedList<String> resourceNames = new LinkedList<>();
    private final LinkedList<String> mmNames = new LinkedList<>();
    private SF[] formdef;
    private final boolean checkResourceEquality = true;

    public AfpCombine(Path outFile, Path[] inFiles) {
        this.outFile = outFile;
        inputFiles = Stream.of(inFiles) //
                .map(InputFile::new) //
                .toArray(InputFile[]::new);
        try {
            algorithm = MessageDigest.getInstance(System.getProperty("security.digest", "MD5"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() throws IOException {

        scanResources();

        buildRenamingTable();

        buildFormdef();

        writeResourceGroup();

        writeDocuments();
    }

    private void scanResources() throws IOException {
        for (final InputFile inputFile : inputFiles) {
            try (final AfpInputStream ain = AfpFiles.newAfpBufferedInputStream(inputFile.path)) {
                SF sf;
                long filepos, prevFilePos = 0;
                ByteArrayOutputStream buffer = null;
                ResourceKey key = null;
                Resource resource = null;
                String mmName = null;
                MediumMap mediumMap = null;
                boolean processingFormdef = false, isFirstFormdef = true;

                while ((sf = ain.readStructuredField()) != null) {
                    filepos = ain.getCurrentOffset();
                    if (sf instanceof ERG) {
                        inputFile.documentStart = filepos;
                        break;
                    }
                    if (sf instanceof BRS) {
                        key = ResourceKey.toResourceKey((BRS) sf);
                        if (key.getType() == ResourceObjectTypeObjType.CONST_FORM_MAP_VALUE) {
                            key = null; // do not save formdef resources
                        } else {
                            buffer = new ByteArrayOutputStream();
                            inputFile.resources.add(key);
                            inputFile.filePos.put(key, resource = new Resource());
                            resource.start = prevFilePos;
                        }
                    }

                    if (sf instanceof BFM && isFirstFormdef) {
                        LOGGER.debug("processing formdef");
                        processingFormdef = true;
                    }

                    if (processingFormdef)
                        inputFile.formdef.add(sf);

                    if (sf instanceof BMM && isFirstFormdef) {
                        BMM bmm = (BMM) sf;
                        mmName = bmm.getMMName();
                        LOGGER.debug("{}: found medium map {}", inputFile.getName(), mmName);
                        inputFile.mmNames.add(mmName);
                        inputFile.mediumMaps.put(mmName, mediumMap = new MediumMap());
                        mediumMap.start = prevFilePos;
                        buffer = new ByteArrayOutputStream();
                    }

                    if (processingFormdef && mediumMap != null) {
                        mediumMap.sfs.add(sf);
                    }

                    if (buffer != null)
                        buffer.write(ain.getLastReadBuffer());

                    if (sf instanceof EMM && isFirstFormdef) {
                        if (mediumMap != null) {
                            mediumMap.end = filepos;
                            if (buffer != null) {
                                byte[] byteArray = buffer.toByteArray();
                                mediumMap.hash = getHash(byteArray);
                                if (checkResourceEquality) mediumMap.content = byteArray;
                                if (!mmNames.contains(mmName))
                                    mmNames.add(mmName);

                                LOGGER.debug("{}@{}-{}: found {}, hash {}", inputFile.getName(), mediumMap.start, mediumMap.end, mmName, mediumMap.hash);
                            }
                        }

                        mmName = null;
                        mediumMap = null;
                        buffer = null;
                    }

                    if (sf instanceof EFM) {
                        processingFormdef = false;
                    }

                    if (sf instanceof ERS) {
                        if (buffer == null) {
                            // this is the end of a formdef, which we don't save
                            isFirstFormdef = false;
                        } else {
                            if (resource != null) {
                                resource.ersPos = prevFilePos;
                                resource.end = filepos;
                                byte[] byteArray = buffer.toByteArray();
                                resource.hash = getHash(byteArray);
                                if (checkResourceEquality) resource.content = byteArray;
                                if (!resourceNames.contains(key.getName()))
                                    resourceNames.add(key.getName());
                                LOGGER.debug("{}@{}-{}: found {}, hash {}", inputFile.getName(), resource.start, resource.end, key, resource.hash);
                            }

                            buffer = null;
                            key = null;
                            resource = null;
                        }
                    }

                    prevFilePos = filepos;
                }
            }

        }
    }

    private void buildRenamingTable() {

        for (int i = 0; i < inputFiles.length; i++) {
            for (int j = i + 1; j < inputFiles.length; j++) {
                InputFile f1 = inputFiles[i];
                InputFile f2 = inputFiles[j];

                LOGGER.debug("comparing resources in {} and {}", f1.getName(), f2.getName());

                for (ResourceKey k1 : f1.resources) {
                    if (f2.resources.contains(k1)
                            && !f2.renamings.containsKey(k1)) { // can this ever happen????
                        String h1 = f1.filePos.get(k1).hash;
                        String h2 = f2.filePos.get(k1).hash;

                        if (h1.equals(h2) && equals(f1.filePos.get(k1).content, f2.filePos.get(k1).content)) {
                            if (f1.renamings.containsKey(k1)) {
                                String newName = f1.renamings.get(k1);
                                LOGGER.debug("resource {} is same in {} and {}, but being renamed to {}", k1.getName(), f1.getName(), f2.getName(), newName);
                                f2.renamings.put(k1, newName);
                            } else {
                                LOGGER.debug("resource {} is same in {} and {}", k1.getName(), f1.getName(), f2.getName());
                            }
                        } else {
                            String newName = getNewResourceName(k1.getName(), h2);
                            f2.renamings.put(k1, newName);
                            resourceNames.add(newName);
                            LOGGER.debug("{}: renaming resource {} to {}", f2.getName(), k1.getName(), newName);
                        }
                    }
                }

                for (String mmName : f1.mmNames) {
                    if (f2.mmNames.contains(mmName)
                            && !f2.renameIMM.containsKey(mmName)) {

                        String h1 = f1.mediumMaps.get(mmName).hash;
                        String h2 = f2.mediumMaps.get(mmName).hash;

                        if (h1.equals(h2)
                                && equals(f1.mediumMaps.get(mmName).content, f2.mediumMaps.get(mmName).content)) {
                            if (f1.renameIMM.containsKey(mmName)) {
                                String newName = f1.renameIMM.get(mmName);
                                LOGGER.debug("medium map {} is same in {} and {}, but being renamed to {}", mmName, f1.getName(), f2.getName(), newName);
                                f2.renameIMM.put(mmName, newName);
                            } else {
                                LOGGER.debug("medium map {} is same in {} and {}", mmName, f1.getName(), f2.getName());
                            }
                        } else {
                            String newName = getNewFormdefName(mmName, h2);
                            f2.renameIMM.put(mmName, newName);
                            mmNames.add(newName);
                            LOGGER.debug("{}: renaming medium map {} to {}", f2.getName(), mmName, newName);
                        }

                    }
                }
            }
        }
    }

    private boolean equals(byte[] b1, byte[] b2) {
        if (!checkResourceEquality) return true;

        return Arrays.equals(b1, b2);
    }

    private void buildFormdef() {
        LinkedList<SF> formdef = new LinkedList<>();
        LinkedList<String> mmsWritten = new LinkedList<>();


        formdef.add(AfplibFactory.eINSTANCE.createBFM());

        for (InputFile inputFile : inputFiles) {

            // build environment group
            LinkedList<SF> bdg = new LinkedList<>();
            boolean isbdg = false;
            for (SF sf : inputFile.formdef) {
                if (sf instanceof BDG) {
                    isbdg = true;
                    continue;
                }
                if (sf instanceof EDG) isbdg = false;
                if (isbdg) bdg.add(sf);
            }

            for (String mmName : inputFile.mmNames) {
                MediumMap map = inputFile.mediumMaps.get(mmName);
                Iterator<SF> it = map.sfs.iterator();
                BMM bmm = (BMM) it.next();
                if (inputFile.renameIMM.containsKey(mmName)) {
                    String newName = inputFile.renameIMM.get(mmName);

                    if (mmsWritten.contains(newName)) {
                        LOGGER.debug("not writing resource {} as {} again", mmName, newName);
                        continue;
                    }

                    bmm.setMMName(newName);
                    LOGGER.debug("writing medium map {} as {} from {}", mmName, bmm.getMMName(), inputFile.getName());
                } else if (mmsWritten.contains(mmName)) {
                    LOGGER.debug("not writing medium map {} again", mmName);
                    continue;
                } else {
                    LOGGER.debug("writing medium map {} from {}", mmName, inputFile.getName());
                }

                formdef.add(bmm);

                // add allowed sfs from the map inherited
                // by the environment group if needed
                add(formdef, bdg, map.sfs, FGD.class);
                add(formdef, bdg, map.sfs, MMO.class);
                add(formdef, bdg, map.sfs, MPO.class);
                add(formdef, bdg, map.sfs, MMT.class);
                add(formdef, bdg, map.sfs, MMD.class);
                add(formdef, bdg, map.sfs, MDR.class);
                add(formdef, bdg, map.sfs, PGP.class);
                add(formdef, bdg, map.sfs, MDD.class);
                add(formdef, bdg, map.sfs, MCC.class);
                add(formdef, bdg, map.sfs, MMC.class);
                add(formdef, bdg, map.sfs, PMC.class);
                add(formdef, bdg, map.sfs, MFC.class);
                add(formdef, bdg, map.sfs, PEC.class);

                formdef.add(AfplibFactory.eINSTANCE.createEMM());

                mmsWritten.add(bmm.getMMName());
            }
        }

        formdef.add(AfplibFactory.eINSTANCE.createEFM());

        this.formdef = formdef.toArray(new SF[0]);
    }

    @SuppressWarnings("unchecked")
    private <T extends SF> void add(LinkedList<SF> dest, LinkedList<SF> bdg,
                                    LinkedList<SF> map, Class<T> clazz) {

        LinkedList<T> result = new LinkedList<>();

        for (SF sf : map) {
            if (clazz.isInstance(sf) || (clazz.equals(PGP.class) && sf instanceof PGP1)) {
                result.add((T) sf);
            }
        }
        if (result.isEmpty()) {
            for (SF sf : bdg) {
                if (clazz.isInstance(sf) || (clazz.equals(PGP.class) && sf instanceof PGP1)) {
                    result.add((T) sf);
                }
            }
        }

        dest.addAll(result);
    }

    private void writeResourceGroup() throws IOException {
        LinkedList<ResourceKey> resourcesWritten = new LinkedList<>();

        try (AfpOutputStream aout = AfpFiles.newAfpBufferedOutputStream(outFile, StandardOpenOption.CREATE)) {
            BRG brg = AfplibFactory.eINSTANCE.createBRG();
            aout.writeStructuredField(brg);

            {
                BRS brs = AfplibFactory.eINSTANCE.createBRS();
                brs.setRSName("F1INLINE");
                ResourceObjectType type = AfplibFactory.eINSTANCE.createResourceObjectType();
                type.setConData(new byte[]{0, 0, 0, 0});
                type.setObjType(ResourceObjectTypeObjType.CONST_FORM_MAP_VALUE);
                brs.getTriplets().add(type);
                aout.writeStructuredField(brs);

                for (SF sf : formdef)
                    aout.writeStructuredField(sf);

                ERS ers = AfplibFactory.eINSTANCE.createERS();
                aout.writeStructuredField(ers);
            }

            LOGGER.info("writing resource group");

            for (final InputFile inputFile : inputFiles) {
                try (final AfpInputStream ain = AfpFiles.newAfpInputStream(inputFile.path)) {
                    for (ResourceKey key : inputFile.resources) {

                        if (key.getType() == ResourceObjectTypeObjType.CONST_FORM_MAP_VALUE) {
                            LOGGER.debug("not writing formdef {}", key.getName());
                            continue;
                        }

                        ain.position(inputFile.filePos.get(key).start);
                        BRS brs = (BRS) ain.readStructuredField();

                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            ResourceKey newkey = new ResourceKey(key.getType(), newName, key.getObjId());
                            if (resourcesWritten.contains(newkey)) {
                                LOGGER.debug("not writing resource {} as {} again", key.getName(), newName);
                                continue;
                            }
                            renameBRSERS(brs, newName);
                            resourcesWritten.add(newkey);
                            LOGGER.debug("writing resource {} as {} from {}", key.getName(), newName, inputFile.getName());
                        } else if (resourcesWritten.contains(key)) {
                            LOGGER.debug("not writing resource {} again", key.getName());
                            continue;
                        } else {
                            resourcesWritten.add(key);
                            LOGGER.debug("writing resource {} from {}", key.getName(), inputFile.getName());
                        }

                        aout.writeStructuredField(brs);
                        byte[] buffer = new byte[8 * 1024];
                        int l;
                        long left = inputFile.filePos.get(key).ersPos - ain.getCurrentOffset();
                        while ((l = ain.read(buffer, 0, left > buffer.length ? buffer.length : (int) left)) > 0) {
                            aout.write(buffer, 0, l);
                            left -= l;
                        }
                        if (left > 0) throw new IOException("couldn't copy resource from " + inputFile.getName());

                        ERS ers = (ERS) ain.readStructuredField();
                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            renameBRSERS(ers, newName);
                        }
                        aout.writeStructuredField(ers);
                    }
                }
            }

            ERG erg = AfplibFactory.eINSTANCE.createERG();
            aout.writeStructuredField(erg);
        }

    }

    private void renameBRSERS(SF sf, String newName) {
        if (sf instanceof BRS) {
            ((BRS) sf).setRSName(newName);
            EList<Triplet> triplets = ((BRS) sf).getTriplets();
            for (Object t : triplets) {
                if (t instanceof FullyQualifiedName) {
                    FullyQualifiedName fqn = (FullyQualifiedName) t;
                    if (fqn.getFQNType() == null) continue;
                    if (FullyQualifiedNameFQNType.CONST_REPLACE_FIRST_GID_NAME_VALUE != fqn.getFQNType())
                        continue;
                    fqn.setFQName(newName);
                    break;
                }
            }
        } else {
            ((ERS) sf).setRSName(newName);
        }
    }

    private void writeDocuments() throws IOException {
        for (final InputFile inputFile : inputFiles) {
            LOGGER.info("writing documents from {}", inputFile.getName());
            try (final AfpInputStream ain = AfpFiles.newAfpInputStream(inputFile.path);
                 final AfpOutputStream aout = AfpFiles.newAfpBufferedOutputStream(outFile, StandardOpenOption.APPEND)) {

                ain.position(inputFile.documentStart);

                AfpFilter.filter(ain, aout, sf -> {
                    LOGGER.trace("{}", sf);
                    switch (sf.getId()) {
                        case SFName.IMM_VALUE:
                            return rename(inputFile, (IMM) sf);
                        case SFName.IOB_VALUE:
                            return rename(inputFile, (IOB) sf);
                        case SFName.IPG_VALUE:
                            return rename();
                        case SFName.IPO_VALUE:
                            return rename(inputFile, (IPO) sf);
                        case SFName.IPS_VALUE:
                            return rename(inputFile, (IPS) sf);
                        case SFName.MCF_VALUE:
                            return rename(inputFile, (MCF) sf);
                        case SFName.MCF1_VALUE:
                            return rename(inputFile, (MCF1) sf);
                        case SFName.MDR_VALUE:
                            return rename(inputFile, (MDR) sf);
                        case SFName.MMO_VALUE:
                            return rename(inputFile, (MMO) sf);
                        case SFName.MPG_VALUE:
                            return rename((MPG) sf);
                        case SFName.MPO_VALUE:
                            return rename(inputFile, (MPO) sf);
                        case SFName.MPS_VALUE:
                            return rename(inputFile, (MPS) sf);
                    }
                    return STATE.UNTOUCHED;
                });

            }
        }
    }

    private void overrideGid(EList<Triplet> triplets, String newName) {
        for (Object t : triplets) {
            if (t instanceof FullyQualifiedName) {
                FullyQualifiedName fqn = (FullyQualifiedName) t;
                if (fqn.getFQNType() == null) continue;
                if (FullyQualifiedNameFQNType.CONST_REPLACE_FIRST_GID_NAME_VALUE != fqn.getFQNType())
                    continue;
                fqn.setFQName(newName);
                break;
            }
        }
    }

    private Filter.STATE rename(InputFile inputFile, IMM imm) {
        if (inputFile.renameIMM.containsKey(imm.getMMPName())) {
            String newName = inputFile.renameIMM.get(imm.getMMPName());
            imm.setMMPName(newName);
            overrideGid(imm.getTriplets(), newName);
            LOGGER.trace("rename {}", newName);
            return Filter.STATE.MODIFIED;
        }
        return Filter.STATE.UNTOUCHED;
    }

    private Filter.STATE rename(InputFile inputFile, IOB sf) {
        ResourceKey key = ResourceKey.toResourceKey(sf);
        if (inputFile.renamings.containsKey(key)) {
            String newName = inputFile.renamings.get(key);
            sf.setObjName(newName);
            overrideGid(sf.getTriplets(), newName);
            LOGGER.trace("rename {}", newName);
            return Filter.STATE.MODIFIED;
        }
        return Filter.STATE.UNTOUCHED;
    }

    private Filter.STATE rename() {
        return Filter.STATE.UNTOUCHED;
    }

    private Filter.STATE rename(InputFile inputFile, IPO sf) {
        ResourceKey key = ResourceKey.toResourceKey(sf);
        if (inputFile.renamings.containsKey(key)) {
            String newName = inputFile.renamings.get(key);
            sf.setOvlyName(newName);
            overrideGid(sf.getTriplets(), newName);
            LOGGER.trace("rename {}", newName);
            return Filter.STATE.MODIFIED;
        }
        return Filter.STATE.UNTOUCHED;
    }

    private Filter.STATE rename(InputFile inputFile, IPS sf) {
        ResourceKey key = ResourceKey.toResourceKey(sf);
        if (inputFile.renamings.containsKey(key)) {
            String newName = inputFile.renamings.get(key);
            sf.setPsegName(newName);
            overrideGid(sf.getTriplets(), newName);
            LOGGER.trace("rename {}", newName);
            return Filter.STATE.MODIFIED;
        }
        return Filter.STATE.UNTOUCHED;
    }

    private Filter.STATE rename(InputFile inputFile, MCF sf) {
        Filter.STATE result = Filter.STATE.UNTOUCHED;
        for (MCFRG rg : sf.getRG()) {
            for (Triplet t : rg.getTriplets()) {
                if (t instanceof FullyQualifiedName) {
                    FullyQualifiedName fqn = (FullyQualifiedName) t;
                    LOGGER.trace("{}", fqn);
                    if (fqn.getFQNType() == FullyQualifiedNameFQNType.CONST_FONT_CHARACTER_SET_NAME_REFERENCE_VALUE) {
                        ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_FONT_CHARACTER_SET, fqn.getFQName());
                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            fqn.setFQName(newName);
                            LOGGER.trace("rename {}", newName);
                            result = Filter.STATE.MODIFIED;
                        }
                    }
                    if (fqn.getFQNType() == FullyQualifiedNameFQNType.CONST_CODE_PAGE_NAME_REFERENCE_VALUE) {
                        ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_CODE_PAGE, fqn.getFQName());
                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            fqn.setFQName(newName);
                            LOGGER.trace("rename {}", newName);
                            result = Filter.STATE.MODIFIED;
                        }
                    }
                    if (fqn.getFQNType() == FullyQualifiedNameFQNType.CONST_CODED_FONT_NAME_REFERENCE_VALUE) {
                        ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_CODED_FONT, fqn.getFQName());
                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            fqn.setFQName(newName);
                            LOGGER.trace("rename {}", newName);
                            result = Filter.STATE.MODIFIED;
                        }
                    }
                }
            }
        }
        return result;
    }

    private Filter.STATE rename(InputFile inputFile, MCF1 sf) {
        STATE result = Filter.STATE.UNTOUCHED;
        for (MCF1RG rg : sf.getRG()) {
            LOGGER.trace("{}", rg);

            byte[] fcsname = rg.getFCSName().getBytes(Charset.forName("IBM500"));
            if (fcsname[0] != (byte) 0xff && fcsname[1] != (byte) 0xff) {
                ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_FONT_CHARACTER_SET, rg.getFCSName());
                if (inputFile.renamings.containsKey(key)) {
                    String newName = inputFile.renamings.get(key);
                    rg.setFCSName(newName);
                    LOGGER.trace("rename {}", newName);
                    result = Filter.STATE.MODIFIED;
                }
            }

            byte[] cfname = rg.getCFName().getBytes(Charset.forName("IBM500"));
            if (cfname[0] != (byte) 0xff && cfname[1] != (byte) 0xff) {
                ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_CODED_FONT, rg.getCFName());
                if (inputFile.renamings.containsKey(key)) {
                    String newName = inputFile.renamings.get(key);
                    rg.setCFName(newName);
                    LOGGER.trace("rename {}", newName);
                    result = Filter.STATE.MODIFIED;
                }
            }

            byte[] cpname = rg.getCPName().getBytes(Charset.forName("IBM500"));
            if (cpname[0] != (byte) 0xff && cpname[1] != (byte) 0xff) {
                ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_CODE_PAGE, rg.getCPName());
                if (inputFile.renamings.containsKey(key)) {
                    String newName = inputFile.renamings.get(key);
                    rg.setCPName(newName);
                    LOGGER.trace("rename {}", newName);
                    result = Filter.STATE.MODIFIED;
                }
            }

        }
        return result;
    }

    private Filter.STATE rename(InputFile inputFile, MDR sf) {
        STATE result = Filter.STATE.UNTOUCHED;

        for (MDRRG rg : sf.getRG()) {
            EList<Triplet> mcfGroup = rg.getTriplets();
            for (EObject triplet : mcfGroup) {
                if (triplet instanceof FullyQualifiedName) {
                    LOGGER.trace("{}", triplet);
                    int fqnType = ((FullyQualifiedName) triplet).getFQNType();
                    String name = ((FullyQualifiedName) triplet).getFQName();
                    ResourceKey key = null;

                    if (fqnType == FullyQualifiedNameFQNType.CONST_RESOURCE_OBJECT_REFERENCE_VALUE) {
                        key = new ResourceKey(ResourceObjectTypeObjType.CONST_IOCA, name);
                    } else if (fqnType == FullyQualifiedNameFQNType.CONST_OTHER_OBJECT_DATA_REFERENCE_VALUE) {
                        for (Triplet t : rg.getTriplets()) {
                            if (t instanceof ObjectClassification) {
                                ObjectClassification clazz = (ObjectClassification) t;
                                key = new ResourceKey(ResourceObjectTypeObjType.CONST_OBJECT_CONTAINER, name, clazz.getRegObjId());
                            }
                        }
                    } else if (fqnType == FullyQualifiedNameFQNType.CONST_DATA_OBJECT_EXTERNAL_RESOURCE_REFERENCE_VALUE) {
                        for (Triplet t : rg.getTriplets()) {
                            if (t instanceof ObjectClassification) {
                                ObjectClassification clazz = (ObjectClassification) t;
                                key = new ResourceKey(ResourceObjectTypeObjType.CONST_OBJECT_CONTAINER, name, clazz.getRegObjId());
                            }
                        }
                    }
                    if (key != null) {
                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            ((FullyQualifiedName) triplet).setFQName(newName);
                            LOGGER.trace("rename {}", newName);
                            result = Filter.STATE.MODIFIED;
                        }
                    }
                }
            }
        }

        return result;
    }

    private Filter.STATE rename(InputFile inputFile, MMO sf) {
        STATE result = Filter.STATE.UNTOUCHED;
        for (MMORG rg : sf.getRg()) {
            LOGGER.trace("{}", rg);
            ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_OVERLAY, rg.getOVLname());
            if (inputFile.renamings.containsKey(key)) {
                String newName = inputFile.renamings.get(key);
                rg.setOVLname(newName);
                LOGGER.trace("rename {}", newName);
                result = Filter.STATE.MODIFIED;
            }
        }
        return result;
    }

    private Filter.STATE rename(MPG sf) {
        LOGGER.warn("MPG is not supported: {}", sf);
        return Filter.STATE.UNTOUCHED;
    }

    private Filter.STATE rename(InputFile inputFile, MPO sf) {
        STATE result = Filter.STATE.UNTOUCHED;
        for (MPORG rg : sf.getRG()) {
            LOGGER.trace("{}", rg);
            for (Triplet t : rg.getTriplets()) {
                if (t instanceof FullyQualifiedName)
                    if (((FullyQualifiedName) t).getFQNType() == FullyQualifiedNameFQNType.CONST_RESOURCE_OBJECT_REFERENCE_VALUE) {
                        ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_OVERLAY, ((FullyQualifiedName) t).getFQName());
                        if (inputFile.renamings.containsKey(key)) {
                            String newName = inputFile.renamings.get(key);
                            ((FullyQualifiedName) t).setFQName(newName);
                            LOGGER.trace("rename {}", newName);
                            result = Filter.STATE.MODIFIED;
                        }
                    }
            }
        }
        return result;
    }

    private Filter.STATE rename(InputFile inputFile, MPS sf) {
        STATE result = Filter.STATE.UNTOUCHED;

        for (MPSRG rg : sf.getFixedLengthRG()) {
            ResourceKey key = new ResourceKey(ResourceObjectTypeObjType.CONST_PAGE_SEGMENT, rg.getPsegName());
            if (inputFile.renamings.containsKey(key)) {
                String newName = inputFile.renamings.get(key);
                rg.setPsegName(newName);
                LOGGER.trace("rename {}", newName);
                result = Filter.STATE.MODIFIED;
            }
        }

        return result;
    }


    private String getNewResourceName(String oldName, String hash) {
        for (int i = 0; i < hash.length() - 5; i++) {
            String res = oldName.substring(0, 2) + hash.substring(i, i + 6);
            if (!resourceNames.contains(res))
                return res.toUpperCase();
        }
        for (int i = 0; i < 999999; i++) {
            String res = oldName.substring(0, 2) + String.format("%06d", i);
            if (!resourceNames.contains(res))
                return res.toUpperCase();
        }
        LOGGER.error("unable to find a resource name for hash " + hash);
        throw new IllegalStateException(
                "unable to find a resource name for hash " + hash);
    }

    private String getNewFormdefName(String oldName, String hash) {
        for (int i = 0; i < hash.length() - 5; i++) {
            String res = oldName.substring(0, 2) + hash.substring(i, i + 6);
            if (!mmNames.contains(res))
                return res.toUpperCase();
        }
        for (int i = 0; i < 999999; i++) {
            String res = oldName.substring(0, 2) + String.format("%06d", i);
            if (!mmNames.contains(res))
                return res.toUpperCase();
        }
        LOGGER.error("unable to find a resource name for hash " + hash);
        throw new IllegalStateException(
                "unable to find a resource name for hash " + hash);
    }

    private String getHash(byte[] bytes) {
        algorithm.reset();
        algorithm.update(bytes);
        byte[] messageDigest = algorithm.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte b : messageDigest) {
            hexString.append(Integer.toHexString(0xFF & b));
        }
        return hexString.toString();
    }

}

