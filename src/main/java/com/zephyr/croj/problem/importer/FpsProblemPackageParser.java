package com.zephyr.croj.problem.importer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class FpsProblemPackageParser implements ProblemPackageParser {
    private static final String EXTERNAL_ENTITIES_PROPERTY = "javax.xml.stream.isSupportingExternalEntities";
    private static final List<String> SUPPORTED_VERSIONS = List.of("1.1", "1.2", "1.4");
    private static final Pattern CANONICAL_FPS_DOCTYPE = Pattern.compile(
            "\\s*<!DOCTYPE\\s+fps\\s+PUBLIC\\s+"
                    + "\"-//freeproblemset//An opensource XML standard for AlgorithmContest Problem Set//EN\"\\s+"
                    + "\"https?://hustoj\\.com/fps\\.current\\.dtd\"\\s*>\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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
            ParseBudget budget = new ParseBudget(limits);
            XMLInputFactory factory = secureFactory();
            reader = factory.createXMLStreamReader(new SizeLimitedInputStream(input, limits.maxPackageBytes()));
            moveToStart(reader, budget);
            requireElement(reader, "fps");

            String version = attribute(reader, "version");
            if (!SUPPORTED_VERSIONS.contains(version)) {
                throw new ProblemPackageParseException("Unsupported FPS version: " + version);
            }
            String sourceUrl = attribute(reader, "url");

            List<ProblemImportDraft> problems = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            boolean rootClosed = false;
            while (reader.hasNext()) {
                int event = nextEvent(reader, budget);
                rejectDtd(event);
                if (event == XMLStreamConstants.START_ELEMENT && "item".equals(reader.getLocalName())) {
                    if (problems.size() >= limits.maxProblems()) {
                        throw new ProblemPackageParseException("FPS package exceeds the problem count limit");
                    }
                    problems.add(parseProblem(reader, version, warnings, budget));
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("generator".equals(reader.getLocalName())) {
                        skipElement(reader, budget);
                    } else {
                        addWarning(warnings, "Unsupported FPS root element: " + reader.getLocalName());
                        skipElement(reader, budget);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "fps".equals(reader.getLocalName())) {
                    rootClosed = true;
                    break;
                }
            }
            if (!rootClosed) {
                throw new ProblemPackageParseException("FPS root element is not closed");
            }
            consumeDocumentEnd(reader, budget);
            return new ProblemImportBatch(format(), version, sourceUrl, problems, warnings);
        } catch (ProblemPackageParseException exception) {
            throw exception;
        } catch (XMLStreamException exception) {
            SizeLimitExceededException sizeLimit = findCause(exception, SizeLimitExceededException.class);
            if (sizeLimit != null) {
                throw new ProblemPackageParseException(sizeLimit.getMessage(), exception);
            }
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
            List<String> warnings,
            ParseBudget budget
    ) throws XMLStreamException {
        DraftBuilder draft = new DraftBuilder();
        List<PendingCase> sampleInputs = new ArrayList<>();
        List<PendingCase> sampleOutputs = new ArrayList<>();
        List<PendingCase> testInputs = new ArrayList<>();
        List<PendingCase> testOutputs = new ArrayList<>();

        while (reader.hasNext()) {
            int event = nextEvent(reader, budget);
            rejectDtd(event);
            if (event == XMLStreamConstants.END_ELEMENT && "item".equals(reader.getLocalName())) {
                pairCases(sampleInputs, sampleOutputs, draft.samples, "sample");
                pairCases(testInputs, testOutputs, draft.tests, "test");
                return draft.build();
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            String element = reader.getLocalName();
            switch (element) {
                case "title" -> draft.title = readText(reader, element, limits.maxTextCharacters(), budget);
                case "description" -> draft.description = readText(reader, element, limits.maxTextCharacters(), budget);
                case "input" -> draft.inputDescription = readText(reader, element, limits.maxTextCharacters(), budget);
                case "output" -> draft.outputDescription = readText(reader, element, limits.maxTextCharacters(), budget);
                case "hint" -> draft.hint = readText(reader, element, limits.maxTextCharacters(), budget);
                case "source" -> draft.source = readText(reader, element, limits.maxTextCharacters(), budget);
                case "url" -> draft.upstreamUrl = readText(reader, element, limits.maxTextCharacters(), budget);
                case "remote_oj" -> draft.remoteJudge = readText(reader, element, limits.maxTextCharacters(), budget);
                case "remote_id" -> draft.remoteId = readText(reader, element, limits.maxTextCharacters(), budget);
                case "time_limit" -> {
                    String unit = attribute(reader, "unit");
                    draft.timeLimitMillis = parseTimeLimit(
                            readText(reader, element, limits.maxTextCharacters(), budget), unit, version);
                }
                case "memory_limit" -> {
                    String unit = attribute(reader, "unit");
                    draft.memoryLimitKilobytes = parseMemoryLimit(
                            readText(reader, element, limits.maxTextCharacters(), budget), unit);
                }
                case "sample_input" -> {
                    enforceCaseLimit(sampleInputs.size(), limits.maxSampleCasesPerProblem(), "sample_input");
                    sampleInputs.add(new PendingCase(
                            null, readText(reader, element, limits.maxTextCharacters(), budget)));
                }
                case "sample_output" -> {
                    enforceCaseLimit(sampleOutputs.size(), limits.maxSampleCasesPerProblem(), "sample_output");
                    sampleOutputs.add(new PendingCase(
                            null, readText(reader, element, limits.maxTextCharacters(), budget)));
                }
                case "test_input" -> {
                    enforceTestLimit(testInputs.size());
                    testInputs.add(new PendingCase(
                            attribute(reader, "name"),
                            readText(reader, element, limits.maxTextCharacters(), budget)));
                }
                case "test_output" -> {
                    enforceTestLimit(testOutputs.size());
                    testOutputs.add(new PendingCase(
                            attribute(reader, "name"),
                            readText(reader, element, limits.maxTextCharacters(), budget)));
                }
                case "solution" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.SOLUTION, element, true, budget));
                case "prepend" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.PREPEND, element, true, budget));
                case "template" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.TEMPLATE, element, true, budget));
                case "append" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.APPEND, element, true, budget));
                case "spj" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.SPECIAL_JUDGE, element, false, budget));
                case "tpj" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.TESTLIB_JUDGE, element, false, budget));
                case "interactor" -> addCodeResource(draft, codeResource(
                        reader, ProblemImportCodeKind.INTERACTOR, element, false, budget));
                case "img" -> {
                    if (draft.images.size() >= limits.maxImagesPerProblem()) {
                        throw new ProblemPackageParseException("Problem exceeds the embedded image count limit");
                    }
                    draft.images.add(parseImage(reader, budget));
                }
                default -> {
                    addWarning(warnings, "Unsupported FPS item element: " + element);
                    skipElement(reader, budget);
                }
            }
        }
        throw new ProblemPackageParseException("FPS item is not closed");
    }

    private ProblemImportCodeResource codeResource(
            XMLStreamReader reader,
            ProblemImportCodeKind kind,
            String element,
            boolean languageRequired,
            ParseBudget budget
    ) throws XMLStreamException {
        String language = attribute(reader, "language");
        if (languageRequired && isBlank(language)) {
            throw new ProblemPackageParseException(element + " requires a language attribute");
        }
        return new ProblemImportCodeResource(
                kind, language, readText(reader, element, limits.maxTextCharacters(), budget));
    }

    private ProblemImportImage parseImage(XMLStreamReader reader, ParseBudget budget) throws XMLStreamException {
        String source = null;
        String encoded = null;
        while (reader.hasNext()) {
            int event = nextEvent(reader, budget);
            rejectDtd(event);
            if (event == XMLStreamConstants.END_ELEMENT && "img".equals(reader.getLocalName())) {
                if (isBlank(source) || isBlank(encoded)) {
                    throw new ProblemPackageParseException("img requires src and base64 elements");
                }
                long maximumEncodedLength = Math.addExact(
                        Math.multiplyExact((long) limits.maxEmbeddedImageBytes(), 2L),
                        1_024L);
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
                    source = readText(reader, "src", limits.maxTextCharacters(), budget);
                } else if ("base64".equals(reader.getLocalName())) {
                    long maximumEncodedLength = Math.addExact(
                            Math.multiplyExact((long) limits.maxEmbeddedImageBytes(), 2L),
                            1_024L);
                    encoded = readText(reader, "base64", maximumEncodedLength, budget);
                } else {
                    skipElement(reader, budget);
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

    private String readText(
            XMLStreamReader reader,
            String element,
            long maximumCharacters,
            ParseBudget budget
    ) throws XMLStreamException {
        StringBuilder value = new StringBuilder();
        while (reader.hasNext()) {
            int event = nextEvent(reader, budget);
            rejectDtd(event);
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new ProblemPackageParseException(
                        "Unresolved XML entity reference is prohibited in FPS imports");
            }
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA
                    || event == XMLStreamConstants.SPACE) {
                value.append(reader.getText());
                if (value.length() > maximumCharacters) {
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

    private static void enforceCaseLimit(int existingCases, int maximumCases, String element) {
        if (existingCases >= maximumCases) {
            throw new ProblemPackageParseException(element + " exceeds the configured case count limit");
        }
    }

    private void addCodeResource(DraftBuilder draft, ProblemImportCodeResource resource) {
        if (draft.codeResources.size() >= limits.maxCodeResourcesPerProblem()) {
            throw new ProblemPackageParseException("Problem exceeds the code resource count limit");
        }
        draft.codeResources.add(resource);
    }

    private void addWarning(List<String> warnings, String warning) {
        if (warnings.size() >= limits.maxWarnings()) {
            throw new ProblemPackageParseException("FPS package exceeds the warning limit");
        }
        warnings.add(warning);
    }

    private static void pairCases(
            List<PendingCase> inputs,
            List<PendingCase> outputs,
            List<ProblemImportCase> target,
            String kind
    ) {
        Set<String> inputNames = new HashSet<>();
        for (PendingCase input : inputs) {
            if (input.name() != null && !inputNames.add(input.name())) {
                throw new ProblemPackageParseException(kind + "_input has a duplicate name: " + input.name());
            }
        }

        Map<String, Integer> namedOutputs = new LinkedHashMap<>();
        ArrayDeque<Integer> unnamedOutputs = new ArrayDeque<>();
        ArrayDeque<Integer> unmatchedNamedOutputs = new ArrayDeque<>();
        for (int index = 0; index < outputs.size(); index++) {
            PendingCase output = outputs.get(index);
            if (output.name() == null) {
                unnamedOutputs.addLast(index);
            } else if (namedOutputs.putIfAbsent(output.name(), index) != null) {
                throw new ProblemPackageParseException(kind + "_output has a duplicate name: " + output.name());
            } else if (!inputNames.contains(output.name())) {
                unmatchedNamedOutputs.addLast(index);
            }
        }

        for (PendingCase input : inputs) {
            Integer outputIndex;
            if (input.name() != null && namedOutputs.containsKey(input.name())) {
                outputIndex = namedOutputs.remove(input.name());
            } else if (input.name() != null) {
                outputIndex = unnamedOutputs.pollFirst();
            } else {
                outputIndex = pollFirstByDocumentOrder(unnamedOutputs, unmatchedNamedOutputs);
            }
            if (outputIndex == null) {
                throw new ProblemPackageParseException(kind + "_input is missing a matching " + kind + "_output");
            }
            PendingCase output = outputs.get(outputIndex);
            if (output.name() != null) {
                namedOutputs.remove(output.name());
            }
            String name = input.name() != null ? input.name() : output.name();
            target.add(new ProblemImportCase(name, input.content(), output.content()));
        }

        if (!namedOutputs.isEmpty() || !unnamedOutputs.isEmpty() || !unmatchedNamedOutputs.isEmpty()) {
            throw new ProblemPackageParseException(kind + "_output has no matching " + kind + "_input");
        }
    }

    private static Integer pollFirstByDocumentOrder(
            ArrayDeque<Integer> unnamed,
            ArrayDeque<Integer> named
    ) {
        if (unnamed.isEmpty()) {
            return named.pollFirst();
        }
        if (named.isEmpty()) {
            return unnamed.pollFirst();
        }
        return unnamed.peekFirst() < named.peekFirst() ? unnamed.pollFirst() : named.pollFirst();
    }

    static XMLInputFactory secureFactory() {
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

    private static void moveToStart(XMLStreamReader reader, ParseBudget budget) throws XMLStreamException {
        while (reader.hasNext() && reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            int event = nextEvent(reader, budget);
            if (event == XMLStreamConstants.DTD) {
                requireCanonicalDoctype(reader.getText());
            }
        }
    }

    private static void requireCanonicalDoctype(String declaration) {
        if (declaration == null || !CANONICAL_FPS_DOCTYPE.matcher(declaration).matches()) {
            throw new ProblemPackageParseException(
                    "Only the canonical FreeProblemSet public DTD declaration is accepted; DTD resolution remains disabled");
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

    private static void skipElement(XMLStreamReader reader, ParseBudget budget) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = nextEvent(reader, budget);
            rejectDtd(event);
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static void consumeDocumentEnd(XMLStreamReader reader, ParseBudget budget) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = nextEvent(reader, budget);
            rejectDtd(event);
            if (event == XMLStreamConstants.START_ELEMENT) {
                throw new ProblemPackageParseException("FPS package contains content after the root element");
            }
        }
    }

    private static int nextEvent(XMLStreamReader reader, ParseBudget budget) throws XMLStreamException {
        int event = reader.next();
        budget.recordEvent();
        return event;
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

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return causeType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class ParseBudget {
        private final long maximumEvents;
        private long events;

        private ParseBudget(ProblemImportLimits limits) {
            this.maximumEvents = limits.maxXmlEvents();
        }

        private void recordEvent() {
            events++;
            if (events > maximumEvents) {
                throw new ProblemPackageParseException("FPS package exceeds the XML event limit");
            }
        }
    }

    private static final class SizeLimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytesRead;

        private SizeLimitedInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                recordBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                recordBytes(count);
            }
            return count;
        }

        private void recordBytes(int count) throws SizeLimitExceededException {
            bytesRead += count;
            if (bytesRead > maximumBytes) {
                throw new SizeLimitExceededException("FPS package exceeds the configured byte limit");
            }
        }
    }

    private static final class SizeLimitExceededException extends IOException {
        private SizeLimitExceededException(String message) {
            super(message);
        }
    }

    private record PendingCase(String name, String content) {
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
