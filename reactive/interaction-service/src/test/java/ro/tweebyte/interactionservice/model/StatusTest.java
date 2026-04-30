package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusTest {

    @Test
    void valueOfPending() {
        assertEquals(Status.PENDING, Status.valueOf("PENDING"));
    }

    @Test
    void valueOfAccepted() {
        assertEquals(Status.ACCEPTED, Status.valueOf("ACCEPTED"));
    }

    @Test
    void valueOfRejected() {
        assertEquals(Status.REJECTED, Status.valueOf("REJECTED"));
    }

    @Test
    void valuesContainsAllThree() {
        Status[] values = Status.values();
        assertEquals(3, values.length);
    }

    @Test
    void fromStringResolvesAccepted() {
        Status s = Status.PENDING.fromString("ACCEPTED");
        assertEquals(Status.ACCEPTED, s);
    }

    @Test
    void fromStringResolvesPending() {
        Status s = Status.PENDING.fromString("PENDING");
        assertEquals(Status.PENDING, s);
    }

    @Test
    void fromStringThrowsForUnknown() {
        assertThrows(RuntimeException.class, () -> Status.PENDING.fromString("UNKNOWN"));
    }

    @Test
    void valueOfThrowsForInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Status.valueOf("BOGUS"));
    }

    @Test
    void nameMatchesEnumConstant() {
        assertEquals("PENDING", Status.PENDING.name());
        assertEquals("ACCEPTED", Status.ACCEPTED.name());
        assertEquals("REJECTED", Status.REJECTED.name());
    }

    @Test
    void valuesArrayNotNull() {
        assertNotNull(Status.values());
    }
}
