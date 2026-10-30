/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package apocrypha.demo;

import java.beans.Introspector;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

import kiss.I;
import kiss.Ⅱ;
import reincarnation.Reincarnation;
import reincarnation.coder.js.JavascriptCodingOption;

public interface Action extends Runnable, Serializable {

    /**
     * Decompile code.
     * 
     * @return
     */
    default String decompile() {
        Ⅱ<Method, SerializedLambda> method = method();
        if (method.ⅰ.isSynthetic()) {
            // lambda expression
            String code = Reincarnation.rebirth(method.ⅰ.getDeclaringClass(), new JavascriptCodingOption());
            return code;
        } else {
            // method reference
            return inspectPropertyName(method.ⅰ);
        }
    }

    /**
     * Get the implementation of this lambda.
     * 
     * @return The implementation method of this lambda.
     */
    private Ⅱ<Method, SerializedLambda> method() {
        try {
            Method m = getClass().getDeclaredMethod("writeReplace");
            m.setAccessible(true);
            SerializedLambda s = (SerializedLambda) m.invoke(this);

            return I.pair(I.signal(I.type(s.getImplClass().replaceAll("/", ".")).getDeclaredMethods())
                    .take(x -> x.getName().equals(s.getImplMethodName()))
                    .first()
                    .to()
                    .exact(), s);
        } catch (Exception e) {
            throw I.quiet(e);
        }
    }

    /**
     * Utility method to inspect property name from {@link Method}.
     * 
     * @param method
     * @return
     */
    static String inspectPropertyName(Method method) {
        String name = method.getName();
        if (method.getReturnType() == boolean.class) {
            if (name.startsWith("is")) {
                name = name.substring(2);
            }
        } else {
            if (name.startsWith("get")) {
                name = name.substring(3);
            }
        }
        return Introspector.decapitalize(name);
    }
}
