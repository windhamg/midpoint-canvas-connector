/*
 * Copyright (c) 2010-2014 Evolveum
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

package com.cirrusidentity.polygon.connector.canvas;

import com.evolveum.polygon.rest.AbstractRestConfiguration;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.ConfigurationProperty;

import java.util.Arrays;

public class CanvasConfiguration extends AbstractRestConfiguration {

    private static final Log LOG = Log.getLog(CanvasConfiguration.class);

    /**
     * if true, "ping" (retrieving Canvas login page) is skipped
     */
    private Boolean skipTestConnection = false;

    private String authenticationProviderId = "saml";

    private Boolean sendConfirmation = true;

    private Boolean skipRegistration = true;

    private Boolean termsOfUse = true;

    @Override
    public String toString() {
        return "CanvasConfiguration{" +
                "serviceAddress=" + getServiceAddress() +
                ", authMethod=" + getAuthMethod() +
                '}';
    }

    @Override
    public void validate() {
        //todo implement
    }

    @ConfigurationProperty(displayMessageKey = "canvas.config.skipTestConnection",
            helpMessageKey = "canvas.config.skipTestConnection.help")
    public Boolean getSkipTestConnection() {
        return skipTestConnection;
    }

    public void setSkipTestConnection(Boolean skipTestConnection) {
        this.skipTestConnection = skipTestConnection;
    }

    @ConfigurationProperty(displayMessageKey = "canvas.config.authenticationProviderId",
            helpMessageKey = "canvas.config.authenticationProviderId.help")
    public String getAuthenticationProviderId() {
        return authenticationProviderId;
    }

    public void setAuthenticationProviderId(String authenticationProviderId) {
        this.authenticationProviderId = authenticationProviderId;
    }

    @ConfigurationProperty(displayMessageKey = "canvas.config.sendConfirmation",
            helpMessageKey = "canvas.config.sendConfirmation.help")
    public Boolean getSendConfirmation() {
        return sendConfirmation;
    }

    public void setSendConfirmation(Boolean sendConfirmation) {
        this.sendConfirmation = sendConfirmation;
    }

    @ConfigurationProperty(displayMessageKey = "canvas.config.skipRegistration",
            helpMessageKey = "canvas.config.skipRegistration.help")
    public Boolean getSkipRegistration() {
        return skipRegistration;
    }

    public void setSkipRegistration(Boolean skipRegistration) {
        this.skipRegistration = skipRegistration;
    }

    @ConfigurationProperty(displayMessageKey = "canvas.config.termsOfUse",
            helpMessageKey = "canvas.config.termsOfUse.help")
    public Boolean getTermsOfUse() {
        return termsOfUse;
    }

    public void setTermsOfUse(Boolean termsOfUse) {
        this.termsOfUse = termsOfUse;
    }

}