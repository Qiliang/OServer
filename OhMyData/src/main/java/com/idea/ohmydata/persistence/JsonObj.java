package com.idea.ohmydata.persistence;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.uri.UriParameter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class JsonObj extends HashMap<String, Object> {

    final static ObjectMapper mapper = new ObjectMapper();

    public static JsonObj parse(byte[] bytes) throws ODataApplicationException {
        try {
            return mapper.readValue(bytes, JsonObj.class);
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
        }
    }

    public static JsonObj parse(String jsongString) throws ODataApplicationException {
        try {
            return mapper.readValue(jsongString, JsonObj.class);
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
        }
    }

    public static JsonObj parse(List<UriParameter> parameters) {
        JsonObj jsonObj = new JsonObj();
        for (UriParameter parameter : parameters) {
            jsonObj.put(parameter.getName(), parameter.getText().replaceAll("'", ""));
        }

        return jsonObj;
    }

    private EdmEntityType edmEntityType;

    public JsonObj() {
    }

    public JsonObj(Map<? extends String, ?> m) {
        super(m);
    }

    public EdmEntityType getEdmEntityType() {
        return edmEntityType;
    }

    public JsonObj setEdmEntityType(EdmEntityType edmEntityType) {
        this.edmEntityType = edmEntityType;
        return this;
    }

    public JsonObj getKeyObj() {
        JsonObj keys = new JsonObj();
        for (String s : edmEntityType.getKeyPredicateNames()) {
            keys.put(s, this.get(s));
        }

        return keys;
    }

    public String toSQL() {
        List<String> list = new ArrayList<>();
        for (String key : this.keySet()) {
            list.add(String.format("\"data\"->>'%s'='%s'", key, this.get(key)));
        }
        if (list.size() == 0) return " 1=1 ";
        return String.join(" and ", list);
    }

    public String toJson() throws ODataApplicationException {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }
    }

    public Entity toEntity(OData odata) throws ODataApplicationException {
        try {
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            return deserializer.entity(toInputStream(), edmEntityType).getEntity();
        } catch (Exception e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }


    }

    private InputStream toInputStream() throws JsonProcessingException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(mapper.writeValueAsBytes(this));
        return new AutoCloseInputStream(inputStream);
    }
}
