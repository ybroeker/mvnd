/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.junit.ClientFactory;
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.TestLayout;
import org.jboss.fuse.mvnd.junit.TestRegistry;
import org.jboss.fuse.mvnd.junit.TestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@MvndNativeTest(projectDir = "src/test/projects/upgrades-in-bom")
public class UpgradesInBomNativeIT {

    @Inject
    TestLayout layout;

    @Inject
    TestRegistry registry;

    @Inject
    ClientFactory clientFactory;

    @Test
    void upgrade() throws IOException, InterruptedException {
        /* Install the dependencies */
        for (String artifactDir : Arrays.asList("project/hello-0.0.1", "project/hello-0.0.2-SNAPSHOT")) {
            final Client cl = clientFactory.newClient(layout.cd(layout.getTestDir().resolve(artifactDir)));
            final ClientOutput output = Mockito.mock(ClientOutput.class);
            cl.execute(output, "clean", "install", "-e").assertSuccess();
            registry.killAll();
        }
        Assertions.assertThat(registry.getAll().size()).isEqualTo(0);

        /* Build the initial state of the test project */
        final Path parentDir = layout.getTestDir().resolve("project/parent");
        final Client cl = clientFactory.newClient(layout.cd(parentDir));
        {
            final ClientOutput output = Mockito.mock(ClientOutput.class);
            cl.execute(output, "clean", "install", "-e").assertSuccess();
        }
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);

        final DaemonInfo d = registry.getAll().get(0);
        /* Wait, till the instance becomes idle */
        registry.awaitIdle(d.getUid());

        /* Upgrade the dependency  */
        final Path parentPomPath = parentDir.resolve("pom.xml");
        TestUtils.replace(parentPomPath, "<hello.version>0.0.1</hello.version>",
                "<hello.version>0.0.2-SNAPSHOT</hello.version>");
        /* Adapt the caller  */
        final Path useHelloPath = parentDir
                .resolve("module/src/main/java/org/jboss/fuse/mvnd/test/upgrades/bom/module/UseHello.java");
        TestUtils.replace(useHelloPath, "new Hello().sayHello()", "new Hello().sayWisdom()");
        {
            final ClientOutput output = Mockito.mock(ClientOutput.class);
            cl.execute(output, "clean", "install", "-e").assertSuccess();
        }
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);

    }
}
