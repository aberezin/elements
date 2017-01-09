/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.sample.web.cxf;

import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.sample.BaseCase;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;

/**
 * Created by futeh.
 */
public class HellowWorldTest extends BaseCase {
    HelloWorld helloWorld;
    RestfulProxy proxy;

    @Before
    public void setup() {
        proxy = new RestfulProxy("http://localhost:9001/restful");
        proxy.setSkipCertCheck(true);
        proxy.setPrinter(new PrintWriter(System.out, true));
        helloWorld = proxy.newProxy(HelloWorld.class);
    }

    @Test
    public void sayHello() {

        helloWorld.ping();

        String response = helloWorld.sayHello("hello");
        System.out.println(response);
    }
}
