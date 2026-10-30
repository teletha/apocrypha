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

import java.util.function.Consumer;

import kiss.XML;

public abstract class DOM extends lycoris.HTML {

    protected Consumer<XML> click(Action action) {
        return xml -> {
            String code = action.decompile();
            System.out.println(code);
            xml.attr("onclick", "console.log(111)");
        };
    }
}
