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
 * A model returned from the Java backend to the renderer.
 *
 * @param message A greeting.
 * @param timestamp The response time.
 * @param thread The virtual thread which handled the request.
 */
public record Greeting(String message, String timestamp, String thread) {
}
