/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package apocrypha;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kiss.I;
import kiss.JSON;

class IpcCodecTest {

    @Test
    void modelRoundTrip() {
        Person person = new Person("Alice", 20);
        String json = IpcCodec.write(person);

        Person decoded = IpcCodec.decode(json, Person.class);
        assertEquals("Alice", decoded.name());
        assertEquals(20, decoded.age());
    }

    @Test
    void containers() {
        String json = IpcCodec.write(Map.of("name", "Alice", "tags", List.of("a", "b")));

        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"tags\":[\"a\",\"b\"]"));
    }

    @Test
    void arraysAreRejectedClearly() {
        assertThrows(IpcException.class, () -> IpcCodec.decode("[1,2,3]", int[].class));
    }

    @Test
    void jsonValue() {
        JSON json = IpcCodec.decode("{\"name\":\"Alice\"}", JSON.class);
        assertEquals("Alice", json.text("name"));
    }

    @Test
    void primitiveAndString() {
        assertEquals(42, IpcCodec.decode("42", int.class));
        assertEquals("hello", IpcCodec.decode("\"hello\"", String.class));
    }

    public record Person(String name, int age) {
    }
}
