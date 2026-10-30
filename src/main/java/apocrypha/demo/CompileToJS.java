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

public class CompileToJS {

    /**
     * 
     */
    public CompileToJS() {
        DOM dom = view();
        dom.declare();

        System.out.println(dom.root);
    }

    public DOM view() {
        return new DOM() {
            @Override
            public void declare() {
                $("div", click(() -> {
                    System.out.println("test");
                }));
            }
        };
    }

    public static void main(String[] args) {
        new CompileToJS();
    }
}
