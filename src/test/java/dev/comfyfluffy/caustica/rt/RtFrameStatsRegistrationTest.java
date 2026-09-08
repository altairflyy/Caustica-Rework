package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtFrameStatsRegistrationTest {
    private static final Pattern CALL = Pattern.compile(
            "RtFrameStats\\.FRAME\\.(stage|endStage|count)\\(\"([^\"]+)\"");

    @Test
    void everyLiteralProductionMetricIsRegistered() throws Exception {
        Set<String> stages = registered("stageNames");
        Set<String> counters = registered("counterNames");
        Path root = Path.of("src/main/java/dev/comfyfluffy/caustica/rt");
        Set<String> missing = new HashSet<>();

        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = CALL.matcher(Files.readString(file));
                while (matcher.find()) {
                    boolean counter = matcher.group(1).equals("count");
                    String name = matcher.group(2);
                    if (!(counter ? counters : stages).contains(name)) {
                        missing.add(file.getFileName() + ":" + name);
                    }
                }
            }
        }

        assertTrue(missing.isEmpty(), "unregistered RtFrameStats metrics: " + missing);
    }

    private static Set<String> registered(String fieldName) throws Exception {
        Field field = RtFrameStats.Profile.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return Set.of((String[]) field.get(RtFrameStats.FRAME));
    }
}
