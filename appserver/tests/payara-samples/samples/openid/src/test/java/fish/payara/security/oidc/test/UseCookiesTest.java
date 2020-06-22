/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) [2018-2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */
package fish.payara.security.oidc.test;

import com.gargoylesoftware.htmlunit.WebClient;

import fish.payara.samples.NotMicroCompatible;
import fish.payara.samples.PayaraArquillianTestRunner;
import fish.payara.samples.ServerOperations;

import java.io.IOException;
import java.net.URL;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static fish.payara.security.annotations.OpenIdAuthenticationDefinition.OPENID_MP_USE_SESSION;

/**
 *
 * @author Gaurav Gupta
 */
@NotMicroCompatible
@RunWith(PayaraArquillianTestRunner.class)
public class UseCookiesTest {

    @OperateOnDeployment("openid-client")
    @ArquillianResource
    private URL base;

    private static String clientKeyStorePath;

    @Deployment(name = "openid-server")
    public static WebArchive createServerDeployment() {
        return OpenIdTestUtil.createServerDeployment();
    }

    @Deployment(name = "openid-client")
    public static WebArchive createClientDeployment() {
        clientKeyStorePath = ServerOperations.createClientKeyStore();
        StringAsset mpConfig = new StringAsset(OPENID_MP_USE_SESSION + "=" + Boolean.FALSE.toString());
        return OpenIdTestUtil
                .createClientDeployment()
                .addAsWebInfResource(mpConfig, "classes/META-INF/microprofile-config.properties");
    }

    @Test
    @RunAsClient
    public void testOpenIdConnect() throws IOException {
        WebClient webClient = new WebClient();
        URL baseHttps = ServerOperations.createClientTrustStore(webClient, base, clientKeyStorePath);
        OpenIdTestUtil.testOpenIdConnect(webClient, baseHttps);
    }

}
