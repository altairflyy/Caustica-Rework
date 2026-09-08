package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Mode.*;
import static dev.comfyfluffy.caustica.rt.graph.GraphValidation.Kind.*;

class GraphValidationTest {
    private final GraphResource image = new GraphResource("image");

    private GraphResourceUse use(GraphResourceUse.Mode mode) {
        return new GraphResourceUse(image, mode, GraphResourceUse.Queue.GRAPHICS,
                Set.of(GraphResourceUse.Stage.COMPUTE), GraphResourceUse.Layout.GENERAL);
    }

    private GraphPass pass(String name, GraphResourceUse.Mode mode) {
        return new GraphPass(name, List.of(use(mode)));
    }

    private GraphAccess edge(GraphPass from, GraphPass to) {
        return new GraphAccess(from, to, image);
    }

    @Test void futureWriteDoesNotSatisfyRead() {
        GraphPass read = pass("read", READ), write = pass("write", WRITE);
        var result = GraphValidation.diagnose(List.of(read, write), List.of(edge(read, write)),
                Set.of(image), Set.of());
        assertEquals(List.of(new GraphValidation.Diagnostic(READ_BEFORE_WRITE, "read", "image")), result);
    }

    @Test void unorderedWriterCannotSatisfyReadEvenIfListedFirst() {
        GraphPass write = pass("write", WRITE), read = pass("read", READ);
        assertEquals(READ_BEFORE_WRITE, GraphValidation.diagnose(List.of(write, read), List.of(),
                Set.of(image), Set.of()).getFirst().kind());
    }

    @Test void unorderedWritersAreAmbiguous() {
        assertEquals(MULTIPLE_WRITER_AMBIGUITY, GraphValidation.diagnose(
                List.of(pass("a", WRITE), pass("b", WRITE)), List.of(),
                Set.of(image), Set.of()).getFirst().kind());
    }

    @Test void transitiveOrderingAllowsMultipleWritersAndReadWrite() {
        GraphPass a = pass("a", WRITE), b = new GraphPass("b"), c = pass("c", READ_WRITE);
        assertTrue(GraphValidation.diagnose(List.of(c, b, a), List.of(edge(a, b), edge(b, c)),
                Set.of(image), Set.of()).isEmpty());
    }

    @Test void readWriteNeedsExistingContents() {
        GraphPass update = pass("update", READ_WRITE);
        assertEquals(READ_BEFORE_WRITE, GraphValidation.diagnose(List.of(update), List.of(),
                Set.of(image), Set.of()).getFirst().kind());
        assertTrue(GraphValidation.diagnose(List.of(update), List.of(),
                Set.of(image), Set.of(image)).isEmpty());
    }

    @Test void undeclaredReadAndWriteAreDiagnosed() {
        for (var mode : GraphResourceUse.Mode.values()) {
            assertEquals(UNDECLARED_RESOURCE, GraphValidation.diagnose(List.of(pass("p", mode)),
                    List.of(), Set.of(), Set.of()).getFirst().kind());
        }
    }

    @Test void intraPassWriteThenReadIsValidButReverseIsNot() {
        assertTrue(GraphValidation.diagnose(List.of(new GraphPass("p", List.of(use(WRITE), use(READ)))),
                List.of(), Set.of(image), Set.of()).isEmpty());
        assertEquals(READ_BEFORE_WRITE, GraphValidation.diagnose(
                List.of(new GraphPass("p", List.of(use(READ), use(WRITE)))), List.of(),
                Set.of(image), Set.of()).getFirst().kind());
    }

    @Test void metadataAndDeclarationsAreImmutable() {
        var uses = new java.util.ArrayList<>(List.of(use(READ)));
        GraphPass pass = new GraphPass("p", uses);
        uses.clear();
        assertEquals(1, pass.resources().size());
        assertThrows(UnsupportedOperationException.class, () -> pass.resources().clear());
        assertThrows(UnsupportedOperationException.class, () -> pass.resources().getFirst().stages().clear());
        assertThrows(IllegalArgumentException.class, () -> new GraphResourceUse(image, READ,
                GraphResourceUse.Queue.GRAPHICS, Set.of(), GraphResourceUse.Layout.GENERAL));
    }
}
