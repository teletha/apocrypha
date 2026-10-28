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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an IPC channel.
 * <p>
 * Register an instance or a class with {@link Ipc#register(Object)} or
 * {@link Ipc#register(Class)}. The method may be static or an instance method. A method parameter
 * of type {@link IpcRequest} receives the request itself, and a parameter of type {@link Window}
 * receives the source window. All other parameters consume positional arguments from the renderer
 * in declaration order.
 * <p>
 * Arguments and return values which are model classes are serialized with Sinobu
 * ({@code I.write} / {@code I.json}).
 *
 * <pre>{@code
 * public class Notes {
 *     @IpcChannel("notes:save")
 *     public Note save(Note note) {
 *         // note was decoded from JSON by I.json
 *         return note;
 *     }
 * }
 *
 * ipc().register(new Notes());
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IpcChannel {

    /**
     * The channel name. An empty value uses the method name.
     * 
     * @return A channel name.
     */
    String value() default "";
}
