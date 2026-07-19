package com.zephyr.croj.problem.importer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ProblemPackageReader {
    private static final int MAX_TRANSPORT_ENTRIES = 10_000;
    private static final int MAX_COMPRESSION_RATIO = 100;

    private final ProblemPackageParserRegistry parsers;
    private final ProblemImportLimits limits;

    public ProblemPackageReader(ProblemPackageParserRegistry parsers, ProblemImportLimits limits) {
        this.parsers = parsers;
        this.limits = limits;
    }

    public ProblemImportBatch read(String filename, byte[] packageBytes) {
        if (packageBytes == null || packageBytes.length == 0
                || packageBytes.length > limits.maxPackageBytes()) {
            throw new ProblemPackageParseException("Problem package size is invalid");
        }
        byte[] xml = isZip(packageBytes) ? extractSingleXml(packageBytes) : requireXml(packageBytes);
        return parsers.parse(ProblemPackageFormat.FPS_XML, new ByteArrayInputStream(xml));
    }

    private byte[] extractSingleXml(byte[] archive) {
        Set<String> names = new HashSet<>();
        byte[] xml = null;
        long totalBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                String name = entry.getName();
                if (entries > MAX_TRANSPORT_ENTRIES || entry.isDirectory()
                        || !safePath(name) || !names.add(name)) {
                    throw new ProblemPackageParseException("Problem package ZIP entry is unsafe");
                }
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml") || xml != null) {
                    throw new ProblemPackageParseException(
                            "Problem package ZIP must contain exactly one XML file");
                }
                ByteArrayOutputStream extracted = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalBytes = Math.addExact(totalBytes, read);
                    if (totalBytes > limits.maxPackageBytes()
                            || totalBytes > (long) archive.length * MAX_COMPRESSION_RATIO) {
                        throw new ProblemPackageParseException("Problem package ZIP expands beyond limits");
                    }
                    extracted.write(buffer, 0, read);
                }
                xml = extracted.toByteArray();
                zip.closeEntry();
            }
        } catch (IOException | ArithmeticException exception) {
            throw new ProblemPackageParseException("Problem package ZIP is invalid", exception);
        }
        if (entries != 1 || xml == null) {
            throw new ProblemPackageParseException("Problem package ZIP must contain exactly one XML file");
        }
        return requireXml(xml);
    }

    private byte[] requireXml(byte[] bytes) {
        int index = 0;
        while (index < bytes.length && Character.isWhitespace(bytes[index])) {
            index++;
        }
        if (index >= bytes.length || bytes[index] != '<') {
            throw new ProblemPackageParseException("Unsupported problem package content");
        }
        return bytes;
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }

    private boolean safePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.contains("\\") || path.indexOf('\0') >= 0 || path.contains("//")) {
            return false;
        }
        return java.util.Arrays.stream(path.split("/"))
                .noneMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."));
    }
}
