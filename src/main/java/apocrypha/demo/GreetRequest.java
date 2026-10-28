/*
 * Copyright (C) 2026 The APOCRYPHA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package apocrypha.demo;

/**
 * A model sent from the renderer to the Java backend.
 *
 * @param name A name to greet.
 */
public record GreetRequest(String name) {
}
