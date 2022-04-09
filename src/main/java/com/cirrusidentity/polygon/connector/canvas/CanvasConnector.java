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

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.rest.AbstractRestConnector;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;

@ConnectorClass(displayNameKey = "canvas.connector.display", configurationClass = CanvasConfiguration.class)
public class CanvasConnector extends AbstractRestConnector<CanvasConfiguration> implements TestOp, SchemaOp, CreateOp, DeleteOp {

    private static final Log LOG = Log.getLog(CanvasConnector.class);

    private static final String ATTR_NAME = "name";
    private static final String ATTR_UID = "id";
    private static final String ATTR_UNIQUE_ID = "unique_id";
    private static final String ATTR_MAIL = "address";
    private static final String ATTR_AUTHN_PROVIDER_ID = "authentication_provider_id";
    private static final String ATTR_TIMEZONE = "time_zone";
    private static final String ATTR_LOCALE = "locale";
    private static final String ATTR_SEND_CONFIRMATION = "send_confirmation";
    private static final String ATTR_SKIP_REGISTRATION = "skip_registration";
    private static final String ATTR_TERMS_OF_USE = "terms_of_use";
    private static final String ATTR_COMM_CHANNEL_TYPE = "type";

    private static final String PING = "/login/canvas";
    private static final String USER = "/api/v1/accounts/self/users";

    // json strings
    private static final String CONTENT_TYPE = "application/json";

    private CanvasConfiguration configuration;
    private CanvasConnection connection;

    @Override
    public void init(Configuration configuration) {
        super.init(configuration);
        this.configuration = (CanvasConfiguration)configuration;
        this.connection = new CanvasConnection(this.configuration);
    }

    @Override
    public void test() {
        if (getConfiguration().getSkipTestConnection()){
            LOG.ok("test is skipped");
        } else {
            // get login page as "ping" (per https://community.canvaslms.com/t5/Canvas-Question-Forum/Lightweight-Canvas-API-call-to-detect-if-the-service-itself-is/m-p/109758#M34662)
            LOG.ok("test - getting Canvas login page");
            HttpGet request = new HttpGet(getConfiguration().getServiceAddress() + PING);
            HttpResponse response = execute(request);
            processResponseErrors((CloseableHttpResponse) response);
        }
    }

    @Override
    public void dispose() {
        configuration = null;
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
    }

    @Override
    public Schema schema() {
        SchemaBuilder schemaBuilder = new SchemaBuilder(CanvasConnector.class);
        buildAccountObjectClass(schemaBuilder);
        return schemaBuilder.build();
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions operationOptions) {
        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {    // __ACCOUNT__
            return createUser(attributes);
        } else {
            // not found
            throw new UnsupportedOperationException("Unsupported object class " + objectClass);
        }
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions operationOptions) {
        try {
            if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
                LOG.ok("delete user, Uid: {0}", uid);
                HttpDelete request = new HttpDelete(getConfiguration().getServiceAddress() + USER + "/" + uid.getUidValue());
                callRequest(request, false);
            } else {
                // not found
                throw new UnsupportedOperationException("Unsupported object class " + objectClass);
            }
        } catch (IOException e) {
            throw new ConnectorIOException(e.getMessage(), e);
        }
    }

    protected JSONObject callRequest(HttpEntityEnclosingRequestBase request, JSONObject jo) throws IOException {
        // don't log request here - password field !!!
        LOG.ok("request URI: {0}", request.getURI());
//        LOG.ok("gdw auth method: {0}", getConfiguration().getAuthMethod());
//        LOG.ok("gdw token name: {0}", getConfiguration().getTokenName());
//        GuardedString tokenVal = getConfiguration().getTokenValue();
//        GuardedStringAccessor guardedStringAccessor = new GuardedStringAccessor();
//        tokenVal.access(guardedStringAccessor);
//        LOG.ok("gdw token val: {0}", guardedStringAccessor.getClearString());

        request.setHeader("Content-Type", CONTENT_TYPE);

        HttpEntity entity = new ByteArrayEntity(jo.toString().getBytes("UTF-8"));
        request.setEntity(entity);
//        LOG.ok("*** Canvas JSON request = {0}", entity.toString());
        CloseableHttpResponse response = execute(request);
        LOG.ok("response: {0}", response);
        processCanvasResponseErrors(response);

        String result = EntityUtils.toString(response.getEntity());
        LOG.ok("response body: {0}", result);
        closeResponse(response);
        return new JSONObject(result);
    }

    protected JSONObject callRequest(HttpRequestBase request, boolean parseResult) throws IOException {
        LOG.ok("request URI: {0}", request.getURI());
        request.setHeader("Content-Type", CONTENT_TYPE);

        CloseableHttpResponse response = null;
        response = execute(request);
        LOG.ok("response: {0}", response);
        processCanvasResponseErrors(response);

        if (!parseResult) {
            closeResponse(response);
            return null;
        }
        String result = EntityUtils.toString(response.getEntity());
        LOG.ok("response body: {0}", result);
        closeResponse(response);
        return new JSONObject(result);
    }

    private void processCanvasResponseErrors(CloseableHttpResponse response){
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 400) {
            String result = null;
            try {
                result = EntityUtils.toString(response.getEntity());
            } catch (IOException e) {
                throw new ConnectorIOException("Error when trying to get response entity: "+response, e);
            }
            JSONObject err;
            try {
                LOG.ok("Result body: {0}", result);
                JSONObject jo = new JSONObject(result);
                err = jo.getJSONObject("errors").getJSONObject("pseudonym").getJSONArray("unique_id").getJSONObject(0);
            } catch (JSONException e) {
                closeResponse(response);
                throw new ConnectorIOException(e.getMessage() + " when parsing result: " + result, e);
            }
            if (err.has("message")) {
                closeResponse(response);
                throw new AlreadyExistsException(err.getString("message")); // The unique_id is already taken.
            } else {
                closeResponse(response);
                throw new ConnectorIOException("Error when process response: " + result);
            }
        }
        super.processResponseErrors(response);
    }

    private Uid createUser(Set<Attribute> attributes) {
        LOG.ok("createUser, attributes: {1}", attributes);
        if (attributes == null || attributes.isEmpty()) {
            LOG.ok("request ignored, empty attributes");
            return null;
        }
        // parent JSON object
        JSONObject jo = new JSONObject();

        // create user (child) object
        JSONObject userJo = new JSONObject();

        // create pseudonym (child) object
        JSONObject pseudonymJo = new JSONObject();

        // create communication_channel (child) object
        JSONObject commChannelJo = new JSONObject();

        // __NAME__
        String name = getStringAttr(attributes, Name.NAME);
        if (StringUtil.isBlank(name)) {
            throw new InvalidAttributeValueException("Missing mandatory attribute " + ATTR_NAME);
        }
        userJo.put(ATTR_NAME, name);

        // unique_id
        String uniqId = getStringAttr(attributes, ATTR_UNIQUE_ID);
        if (StringUtil.isBlank(uniqId)) {
            throw new InvalidAttributeValueException("Missing mandatory attribute " + ATTR_UNIQUE_ID);
        }
        pseudonymJo.put(ATTR_UNIQUE_ID, uniqId);

        // email
        String mail = getStringAttr(attributes, ATTR_MAIL);
        if (StringUtil.isBlank(mail)) {
            throw new InvalidAttributeValueException("Missing mandatory attribute " + ATTR_MAIL);
        }
        commChannelJo.put(ATTR_MAIL, mail);

        // static attribute values in user object
        userJo.put(ATTR_SKIP_REGISTRATION, getConfiguration().getSkipRegistration());
        userJo.put(ATTR_TERMS_OF_USE, getConfiguration().getTermsOfUse());

        // static attribute values in communication_channel object
        commChannelJo.put(ATTR_COMM_CHANNEL_TYPE, "email");

        // optional user object attributes
        putFieldIfExists(attributes, ATTR_LOCALE, userJo);
        putFieldIfExists(attributes, ATTR_TIMEZONE, userJo);

        // optional pseudonym object attributes
        pseudonymJo.put(ATTR_SEND_CONFIRMATION, getConfiguration().getSendConfirmation());
        pseudonymJo.put(ATTR_AUTHN_PROVIDER_ID, getConfiguration().getAuthenticationProviderId());

        // add child objects to parent
        jo.put("user", userJo);
        jo.put("pseudonym", pseudonymJo);
        jo.put("communication_channel", commChannelJo);

        try {
            HttpEntityEnclosingRequestBase request;
            request = new HttpPost(getConfiguration().getServiceAddress() + USER);
            JSONObject jores = callRequest(request, jo);

            String newUid = Integer.toString(jores.getInt(ATTR_UID));
            LOG.info("response UID: {0}", newUid);
            return new Uid(newUid);
        } catch (IOException e) {
            throw new ConnectorIOException(e.getMessage(), e);
        }
    }

    private void putFieldIfExists(Set<Attribute> attributes, String fieldName, JSONObject jo) {
        String fieldValue = getStringAttr(attributes, fieldName);
        if (fieldValue != null) {
            jo.put(fieldName, fieldValue);
        }
    }

    private void buildAccountObjectClass(SchemaBuilder schemaBuilder) {
        ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();

        // NAME is set to user[name]
        AttributeInfoBuilder attrNameBuilder = new AttributeInfoBuilder(Name.NAME);
        attrNameBuilder.setNativeName(ATTR_NAME);
        attrNameBuilder.setRequired(true);
        objClassBuilder.addAttributeInfo(attrNameBuilder.build());

        // UID is set to id returned from Canvas upon user creation
        AttributeInfoBuilder attrUidBuilder = new AttributeInfoBuilder(Uid.NAME);
        attrUidBuilder.setNativeName(ATTR_UID);
        attrUidBuilder.setRequired(false);
        objClassBuilder.addAttributeInfo(attrUidBuilder.build());

        // communication_channel[address]
        AttributeInfoBuilder attrMailBuilder = new AttributeInfoBuilder(ATTR_MAIL);
        attrMailBuilder.setRequired(true);
        objClassBuilder.addAttributeInfo(attrMailBuilder.build());

        // pseudonym[unique_id]
        AttributeInfoBuilder attrUniqueIdBuilder = new AttributeInfoBuilder(ATTR_UNIQUE_ID);
        attrUniqueIdBuilder.setRequired(true);
        objClassBuilder.addAttributeInfo(attrUniqueIdBuilder.build());

//        // pseudonym[authentication_provider_id]
//        AttributeInfoBuilder attrAuthnProviderIdBuilder = new AttributeInfoBuilder(ATTR_AUTHN_PROVIDER_ID);
//        attrAuthnProviderIdBuilder.setRequired(false);
//        objClassBuilder.addAttributeInfo(attrAuthnProviderIdBuilder.build());

        // user[time_zone]
        AttributeInfoBuilder attrTimeZoneBuilder = new AttributeInfoBuilder(ATTR_TIMEZONE);
        attrTimeZoneBuilder.setRequired(false);
        objClassBuilder.addAttributeInfo(attrTimeZoneBuilder.build());
        
        // user[locale]
        AttributeInfoBuilder attrLocaleBuilder = new AttributeInfoBuilder(ATTR_LOCALE);
        attrLocaleBuilder.setRequired(false);
        objClassBuilder.addAttributeInfo(attrLocaleBuilder.build());

//        // pseudonym[send_confirmation]
//        AttributeInfoBuilder attrSendConfirmationBuilder = new AttributeInfoBuilder(ATTR_SEND_CONFIRMATION);
//        attrSendConfirmationBuilder.setRequired(false);
//        objClassBuilder.addAttributeInfo(attrSendConfirmationBuilder.build());

        objClassBuilder.addAttributeInfo(OperationalAttributeInfos.ENABLE);     // status

        schemaBuilder.defineObjectClass(objClassBuilder.build());
    }
}
