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

/**
 * A response served through the application scheme.
 *
 * @param status An HTTP status code.
 * @param mimeType A content type.
 * @param data The response body.
 */
record Resource(int status, String mimeType, byte[] data) {
}
