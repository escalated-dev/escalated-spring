package dev.escalated;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every page name this package renders resolves to a component in
 * {@code @escalated-dev/escalated}.
 *
 * <p>Inertia resolving a name to nothing is not an error. The response is a 200, the resolver
 * returns undefined, Vue renders nothing, and the panel comes up blank -- which reads as a
 * permissions problem or an empty dataset. Screens shipped that way across six of the backends in
 * this portfolio before anyone noticed, and the controller tests asserting a 200 said they were
 * fine throughout.
 *
 * <p>Neither repo's tests can see the failure alone: a controller test asserts a status, and the
 * frontend never hears the name. This is the comparison, against the manifest the frontend package
 * publishes and this repo vendors at src/test/resources/escalated-pages.json.
 *
 * <p>Adding a screen goes: component into the frontend, frontend release, refresh the fixture, then
 * render the name here. In that order, or it ships blank.
 */
class PageNameParityTest {

  private static final Path MANIFEST = Path.of("src/test/resources/escalated-pages.json");
  private static final Path SOURCE = Path.of("src/main/java");
  private static final Pattern PAGE_NAME = Pattern.compile("\"(Escalated/[A-Za-z0-9/_]+)\"");

  private static List<String> shippedPages() throws IOException {
    JsonNode manifest = new ObjectMapper().readTree(Files.readString(MANIFEST, StandardCharsets.UTF_8));

    List<String> pages = new ArrayList<>();
    manifest.get("pages").forEach(page -> pages.add(page.asText()));

    return pages;
  }

  /**
   * Page names rendered anywhere under src/main/java, mapped to the files that render them, so a
   * failure can name the file and not only the string.
   */
  private static Map<String, Set<String>> renderedPages() throws IOException {
    Map<String, Set<String>> found = new TreeMap<>();

    try (Stream<Path> files = Files.walk(SOURCE)) {
      for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        Matcher matcher = PAGE_NAME.matcher(Files.readString(path, StandardCharsets.UTF_8));

        while (matcher.find()) {
          found
              .computeIfAbsent(matcher.group(1), name -> new TreeSet<>())
              .add(SOURCE.relativize(path).toString().replace('\\', '/'));
        }
      }
    }

    return found;
  }

  private static String explain(List<String> missing, Map<String, Set<String>> rendered) {
    StringBuilder message =
        new StringBuilder(
            "these page names have no component in @escalated-dev/escalated, so they render a blank panel:");

    for (String name : missing) {
      message.append("\n  ").append(name).append("  (").append(String.join(", ", rendered.get(name))).append(")");
    }

    message.append("\n\nEither the name is wrong, or the component has not been released yet.");
    message.append("\nIf it has been: refresh src/test/resources/escalated-pages.json from the package.");

    return message.toString();
  }

  @Test
  void rendersOnlyPageNamesTheFrontendShips() throws IOException {
    Map<String, Set<String>> rendered = renderedPages();
    Set<String> shipped = Set.copyOf(shippedPages());

    assertFalse(
        rendered.isEmpty(), "found no page names at all, which means this test is not looking where it should");

    List<String> missing = rendered.keySet().stream().filter(name -> !shipped.contains(name)).sorted().toList();

    assertTrue(missing.isEmpty(), () -> explain(missing, rendered));
  }

  @Test
  void thePageManifestIsPresentAndLooksLikeOne() throws IOException {
    // A fixture gone missing or empty would make the test above pass by comparing
    // against nothing.
    List<String> shipped = shippedPages();

    assertAll(
        () -> assertTrue(Files.exists(MANIFEST)),
        () ->
            assertTrue(
                shipped.size() > 50,
                () -> "the manifest has only " + shipped.size() + " pages, which does not look like the real one"),
        () -> assertTrue(shipped.stream().allMatch(name -> name.startsWith("Escalated/"))));
  }
}
