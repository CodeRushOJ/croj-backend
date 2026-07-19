package com.zephyr.croj.problem.importer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class FpsProblemPackageParser implements ProblemPackageParser {
    private static final String EXTERNAL_ENTITIES_PROPERTY = "javax.xml.stream.isSupportingExternalEntities";
    private static final List<String> SUPPORTED_VERSIONS = List.of("1.1", "1.2");

    private final ProblemImportLimits limits;

    public FpsProblemPackageParser(ProblemImportLimits limits) {
        this.limits = limits;
    }

    @Override
    public ProblemPackageFormat format() {
        return ProblemPackageFormat.FPS_XML;
    }

    @Override
    public ProblemImportBatch parse(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("FPS input is required");
        }

        XMLStreamReader reader = null;
        try {
            XMLInputFactory factory = secureFactory();
            reader = factory.createXMLStreamReader(input);
            moveToStart(reader);
            requireElement(reader, "fps");

            String version = attribute(reader, "version");
            if (!SUPPORTED_VERSIONS.contains(version)) {
                throw new ProblemPackageParseException("Unsupported FPS version: " + version);
            }
            String sourceUrl = attribute(reader, "url");

            List<ProblemImportDraft> problems = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            while (reader.hasNext()) {
                int event = reader.next();
                rejectDtd(event);
                if (event == XMLStreamConstants.START_ELEMENT && "item".equals(reader.getLocalName())) {
                    if (problems.size() >= limits.maxProblems()) {
                        throw new ProblemPackageParseException("FPS package exceeds the problem count limit");
                    }
                    problems.add(parseProblem(reader, version, warnings));
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("generator".equals(reader.getLocalName())) {
                        skipElement(reader);
                    } else {
                        warnings.add("Unsupported FPS root element: " + reader.getLocalName());
                        skipElement(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "fps".equals(reader.getLocalName())) {
                    break;
                }
            }
            return new ProblemImportBatch(format(), version, sourceUrl, problems, warnings);
        } catch (ProblemPackageParseException exception) {
            throw exception;
        } catch (XMLStreamException exception) {
            throw new ProblemPackageParseException(
                    "FPS XML parsing failed; DTD and external entities are prohibited: " + exception.getMessage(),
                    exception);
        } finally {
            close(reader);
        }
    }

    private ProblemImportDraft parseProblem(
            XMLStreamReader reader,
            String version,
            List<String> warnings
    ) throws XMLStreamException {
        DraftBuilder draft = new DraftBuilder();
        PendingCase sample = null;
        PendingCase test = null;

        while (reader.hasNext()) {
            int event = reader.next();
            rejectDtd(event);
            if (event == XMLStreamConstants.END_ELEMENT && "item".equals(reader.getLocalName())) {
                if (sample != null) {
                    throw new ProblemPackageParseException("sample_input is missing a matching sample_output");
                }
                if (test != null) {
                    throw new ProblemPackageParseException("test_input is missing a matching test_output");
                }
                return draft.build();
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            String element = reader.getLocalName();
            switch (element) {
                case "title" -> draft.title = readText(reader, element);
                case "description" -> draft.description = readText(reader, element);
                case "input" -> draft.inputDescription = readText(reader, element);
                case "output" -> draft.outputDescription = readText(reader, element);
                case "hint" -> draft.hint = readText(reader, element);
                case "source" -> draft.source = readText(reader, element);
                case "url" -> draft.upstreamUrl = readText(reader, element);
                case "remote_oj" -> draft.remoteJudge = readText(reader, element);
                case "remote_id" -> draft.remoteId = readText(reader, element);
                case "time_limit" -> {
                    String unit = attribute(reader, "unit");
                    draft.timeLimitMillis = parseTimeLimit(readText(reader, element), unit, version);
                }
                case "memory_limit" -> {
                    String unit = attribute(reader, "unit");
                    draft.memoryLimitKilobytes = parseMemoryLimit(readText(reader, element), unit);
                }
                case "sample_input" -> {
                    if (sample != null) {
                        throw new ProblemPackageParseException("sample_input is missing a matching sample_output");
                    }
                    sample = new PendingCase(null, readText(reader, element));
                }
                case "sample_output" -> {
                    if (sample == null) {
                        throw new ProblemPackageParseException("sample_output has no matching sample_input");
                    }
                    draft.samples.add(new ProblemImportCase(null, sample.input(), readText(reader, element)));
                    sample = null;
                }
                case "test_input" -> {
                    if (test != null) {
                        throw new ProblemPackageParseException("test_input is missing a matching test_output");
                    }
                    enforceTestLimit(draft.tests.size());
                    test = new PendingCase(attribute(reader, "name"), readText(reader, element));
                }
                case "test_output" -> {
                    if (test == null) {
                        throw new ProblemPackageParseException("test_output has no matching test_input");
                    }
                    String outputName = attribute(reader, "name");
                    if (test.name() != null && outputName != null && !test.name().equals(outputName)) {
                        throw new ProblemPackageParseException("test_input and test_output names do not match");
                    }
                    draft.tests.add(new ProblemImportCase(
                            test.name() != null ? test.name() : outputName,
                            test.input(),
                            readText(reader, element)));
                    test = null;
                }
                case "solution" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.SOLUTION, element, true));
                case "prepend" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.PREPEND, element, true));
                case "template" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.TEMPLATE, element, true));
                case "append" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.APPEND, element, true));
                case "spj" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.SPECIAL_JUDGE, element, false));
                case "tpj" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.TESTLIB_JUDGE, element, false));
                case "interactor" -> draft.codeResources.add(codeResource(
                        reader, ProblemImportCodeKind.INTERACTOR, element, false));
                case "img" -> draft.images.add(parseImage(reader));
                default -> {
                    warnings.add("Unsupported FPS item element: " + element);
                    skipElement(reader);
                }
            }
        }
        throw new ProblemPackageParseException("FPS item is not closed");
    }

    private ProblemImportCodeResource codeResource(
            XMLStreamReader reader,
            ProblemImportCodeKind kind,
            String element,
            boolean languageRequired
    ) throws XMLStreamException {
        String language = attribute(reader, "language");
        if (languageRequired && isBlank(language)) {
            throw new ProblemPackageParseException(element + " requires a language attribute");
        }
        return new ProblemImportCodeResource(kind, language, readText(reader, element));
    }

    private ProblemImportImage parseImage(XMLStreamReader reader) throws XMLStreamException {
        String source = null;
        String encoded = null;
        while (reader.hasNext()) {
            int event = reader.next();
            rejectDtd(event);
            if (event == XMLStreamConstants.END_ELEMENT && "img".equals(reader.getLocalName())) {
                if (isBlank(source) || isBlank(encoded)) {
                    throw new ProblemPackageParseException("img requires src and base64 elements");
                }
                int maximumEncodedLength = ((limits.maxEmbeddedImageBytes() + 2) / 3) * 4 + 16;
                if (encoded.length() > maximumEncodedLength) {
                    throw new ProblemPackageParseException("Embedded image exceeds the configured size limit");
                }
                try {
                    byte[] content = Base64.getMimeDecoder().decode(encoded);
                    if (content.length > limits.maxEmbeddedImageBytes()) {
                        throw new ProblemPackageParseException("Embedded image exceeds the configured size limit");
                    }
                    return new ProblemImportImage(source, content);
                } catch (IllegalArgumentException exception) {
                    throw new ProblemPackageParseException("Embedded image is not valid base64", exception);
                }
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("src".equals(reader.getLocalName())) {
                    source = readText(reader, "src");
                } else if ("base64".equals(reader.getLocalName())) {
                    encoded = readText(reader, "base64");
                } else {
                    skipElement(reader);
                }
            }
        }
        throw new ProblemPackageParseException("img is not closed");
    }

    private int parseTimeLimit(String raw, String rawUnit, String version) {
        String unit = isBlank(rawUnit) ? "s" : rawUnit.toLowerCase(Locale.ROOT);
        if (!"s".equals(unit) && !"ms".equals(unit)) {
            throw new ProblemPackageParseException("Unsupported FPS time limit unit: " + rawUnit);
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0) {
                throw new ProblemPackageParseException("FPS time limit must be positive");
            }
            if ("1.1".equals(version) && value.stripTrailingZeros().scale() > 0) {
                throw new ProblemPackageParseException("FPS 1.1 time limits must be integers");
            }
            BigDecimal milliseconds = "s".equals(unit) ? value.movePointRight(3) : value;
            return milliseconds.setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new ProblemPackageParseException("Invalid FPS time limit: " + raw, exception);
        }
    }

    private int parseMemoryLimit(String raw, String rawUnit) {
        String unit = isBlank(rawUnit) ? "MB" : rawUnit.toUpperCase(Locale.ROOT);
        if (!"MB".equals(unit) && !"KB".equals(unit)) {
            throw new ProblemPackageParseException("Unsupported FPS memory limit unit: " + rawUnit);
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new ProblemPackageParseException("FPS memory limit must be positive");
            }
            return Math.toIntExact("MB".equals(unit) ? Math.multiplyExact(value, 1024L) : value);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new ProblemPackageParseException("Invalid FPS memory limit: " + raw, exception);
        }
    }

    private String readText(XMLStreamReader reader, String element) throws XMLStreamException {
        StringBuilder value = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            rejectDtd(event);
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA
                    || event == XMLStreamConstants.SPACE || event == XMLStreamConstants.ENTITY_REFERENCE) {
                value.append(reader.getText());
                if (value.length() > limits.maxTextCharacters()) {
                    throw new ProblemPackageParseException(element + " exceeds the configured text limit");
                }
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                throw new ProblemPackageParseException(element + " contains an unsupported nested element");
            } else if (event == XMLStreamConstants.END_ELEMENT && element.equals(reader.getLocalName())) {
                return value.toString();
            }
        }
        throw new ProblemPackageParseException(element + " is not closed");
    }

    private void enforceTestLimit(int existingTests) {
        if (existingTests >= limits.maxTestCasesPerProblem()) {
            throw new ProblemPackageParseException("Problem exceeds the hidden test count limit");
        }
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        setProperty(factory, EXTERNAL_ENTITIES_PROPERTY, false);
        setProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("DTD and external entity resolution is prohibited");
        });
        return factory;
    }

    private static void setProperty(XMLInputFactory factory, String property, Object value) {
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException exception) {
            throw new ProblemPackageParseException("XML parser does not support required security property: " + property, exception);
        }
    }

    private static void moveToStart(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext() && reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            int event = reader.next();
            rejectDtd(event);
        }
    }

    private static void requireElement(XMLStreamReader reader, String expected) {
        if (reader.getEventType() != XMLStreamConstants.START_ELEMENT
                || !expected.equals(reader.getLocalName())) {
            throw new ProblemPackageParseException("Expected FPS root element: " + expected);
        }
    }

    private static String attribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return isBlank(value) ? null : value.trim();
    }

    private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            rejectDtd(event);
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static void rejectDtd(int event) {
        if (event == XMLStreamConstants.DTD) {
            throw new ProblemPackageParseException("DTD declarations are prohibited in FPS imports");
        }
    }

    private static void close(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // The input stream lifecycle belongs to the caller.
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PendingCase(String name, String input) {
    }

    private static final class DraftBuilder {
        private String title;
        private String description;
        private String inputDescription;
        private String outputDescription;
        private String hint;
        private String source;
        private int timeLimitMillis;
        private int memoryLimitKilobytes;
        private String remoteJudge;
        private String remoteId;
        private String upstreamUrl;
        private final List<ProblemImportCase> samples = new ArrayList<>();
        private final List<ProblemImportCase> tests = new ArrayList<>();
        private final List<ProblemImportCodeResource> codeResources = new ArrayList<>();
        private final List<ProblemImportImage> images = new ArrayList<>();

        private ProblemImportDraft build() {
            if (isBlank(title) || isBlank(description)) {
                throw new ProblemPackageParseException("FPS item requires title and description");
            }
            if (timeLimitMillis <= 0 || memoryLimitKilobytes <= 0) {
                throw new ProblemPackageParseException("FPS item requires positive time and memory limits");
            }
            return new ProblemImportDraft(
                    title,
                    description,
                    inputDescription,
                    outputDescription,
                    hint,
                    source,
                    timeLimitMillis,
                    memoryLimitKilobytes,
                    samples,
                    tests,
                    codeResources,
                    images,
                    upstreamUrl,
                    remoteJudge,
                    remoteId);
        }
    }
}
