package com.idea.ohmydata.persisitence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikoyo.otest.ODataServlet;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.core.uri.queryoption.ExpandItemImpl;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;


@Service
public class Storage {


    @Autowired
    private ODataServlet servlet;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ThreadLocal<OData> oDataThreadLocal = new ThreadLocal<OData>();
    private ThreadLocal<ServiceMetadata> serviceMetadataThreadLocal = new ThreadLocal<ServiceMetadata>();

    public void setOData(OData oData) {
        oDataThreadLocal.set(oData);
    }

    private OData getOData() {
        return oDataThreadLocal.get();
    }

    public void setServiceMetadata(ServiceMetadata serviceMetadata) {
        serviceMetadataThreadLocal.set(serviceMetadata);
    }

    private ServiceMetadata getServiceMetadata() {
        return serviceMetadataThreadLocal.get();
    }


    public int countEntitySet(EdmEntitySet edmEntitySet, String sql) {
        StringBuffer sqlBuilder = new StringBuffer();
        sqlBuilder.append("select count(1) from entity where \"repositoryId\" = ").append("'").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        if (StringUtils.isNotBlank(sql)) {
            sqlBuilder.append(" and ( ").append(sql).append(" )");
        }
        return jdbcTemplate.queryForObject(sqlBuilder.toString(), Integer.class);
    }

    public EntityCollection readEntitySetData(EdmEntitySet edmEntitySet, int top, int skip, String sql, String orderBy, ExpandOption responseExpandOption) throws ODataApplicationException {
        EdmEntityType entityType = edmEntitySet.getEntityType();
        StringBuffer sqlBuilder = new StringBuffer();
        sqlBuilder.append("select * from entity where \"repositoryId\" = ").append("'").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        if (StringUtils.isNotBlank(sql)) {
            sqlBuilder.append(" and ( ").append(sql).append(" )");
        }
        sqlBuilder.append(orderBy);
        if (top > 0) sqlBuilder.append(" limit ").append(top);
        if (skip > 0) sqlBuilder.append(" offset ").append(skip);
        //responseExpandOption.getExpandItems().get(0).getResourcePath().getUriResourceParts().get(0);

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        EntityCollection entityCollection = new EntityCollection();
        for (Map<String, Object> row : result) {
            Entity resEntity = getEntity(edmEntitySet, responseExpandOption, row);
            entityCollection.getEntities().add(resEntity);
        }

        return entityCollection;

    }

    private Entity getEntity(EdmEntitySet edmEntitySet, ExpandOption responseExpandOption, Map<String, Object> row) throws ODataApplicationException {

        Entity resEntity = getEntity(edmEntitySet.getEntityType(), row);
        for (ExpandItem expandItem : responseExpandOption.getExpandItems()) {
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) expandItem.getResourcePath().getUriResourceParts().get(0);
            String refName = uriResourceNavigation.getProperty().getName();
            PGobject $ref = (PGobject) row.get("$ref");
            if ($ref == null) continue;
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map refMap = mapper.readValue($ref.getValue(), Map.class);
                if (refMap.containsKey(refName)) {
                    List valueList = (List) refMap.get(refName);
                    EdmEntitySet refEdmEntitySet = getNavigationPropertyBindingSet(refName, edmEntitySet);
                    EntityCollection refEntityCollection = readEntitySetData(refEdmEntitySet, -1, -1, whereIds(refEdmEntitySet, valueList), "", new ExpandOptionImpl());

                    Link link = new Link();
                    link.setRel(refName);
                    link.setTitle(refName);
                    link.setInlineEntitySet(refEntityCollection);
                    resEntity.getNavigationLinks().add(link);
                }
            } catch (IOException e) {
                throw new ODataApplicationException("IOException", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.CHINESE);
            }
        }
        return resEntity;
    }

    private EdmEntitySet getNavigationPropertyBindingSet(String refName, EdmEntitySet edmEntitySet) throws ODataApplicationException {
        for (EdmNavigationPropertyBinding edmNavigationPropertyBinding : edmEntitySet.getNavigationPropertyBindings()) {
            if (edmNavigationPropertyBinding.getPath().equals(refName)) {
                return getServiceMetadata().getEdm().getEntityContainer().getEntitySet(edmNavigationPropertyBinding.getTarget());
            }
        }
        throw new ODataApplicationException("EntitySet not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.CHINESE);
    }


    private void where(StringBuffer sqlBuilder, EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        sqlBuilder.append(" where \"repositoryId\" = '").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        for (UriParameter keyParam : keyParams) {
            sqlBuilder.append(" and data->>'").append(keyParam.getName()).append("' =  ").append(keyParam.getText());
        }
    }

    private String whereIds(EdmEntitySet edmEntitySet, List<Map> keyMapList) {
        List<String> entityConditions = new ArrayList<String>();
        for (Map keyMap : keyMapList) {
            List<String> keyConditions = new ArrayList<String>();
            for (Object key : keyMap.keySet()) {
                keyConditions.add("data->>'" + key + "' =  " + keyMap.get(key));
            }
            entityConditions.add(" ( " + String.join(" and ", keyConditions) + " )");

        }

        return String.join(" or ", entityConditions);

    }

    public Entity readEntityData(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, ExpandOption responseExpandOption) throws ODataApplicationException {

        EdmEntityType entityType = edmEntitySet.getEntityType();
        StringBuffer sqlBuilder = new StringBuffer("select * from entity ");
        where(sqlBuilder, edmEntitySet, keyParams);


        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        if (result.size() == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);

        Map row = result.get(0);


        return getEntity(edmEntitySet, responseExpandOption, row);
    }

    private Entity getEntity(EdmEntityType entityType, Map row) throws ODataApplicationException {
        DeserializerResult deserializerResult;
        try {
            ODataDeserializer deserializer = getOData().createDeserializer(ODataFormat.JSON);
            PGobject jsonObject = (PGobject) row.get("data");
            deserializerResult = deserializer.entity(IOUtils.toInputStream(jsonObject.getValue()), entityType);
        } catch (DeserializerException e) {
            throw new ODataApplicationException("DeserializerException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }

        return deserializerResult.getEntity();
    }


    private Map parseJson(EdmStructuredType edmStructuredType, Object value) throws ODataApplicationException {
        Map objMap = new HashMap();
        List<Property> properties = null;
        if (value instanceof ComplexValue) properties = ((ComplexValue) value).getValue();
        else properties = ((Entity) value).getProperties();
        for (Property property : properties) {
            EdmProperty edmProperty = (EdmProperty) edmStructuredType.getProperty(property.getName());

            if (edmProperty == null)
                throw new ODataApplicationException(property.getName() + " is not a valid property", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            if (property.isEnum()) {
                objMap.put(property.getName(), property.getValue());
            } else if (property.isCollection()) {
                List valueList = new ArrayList();
                if (property.isComplex()) {
                    for (Object o : ((List) property.getValue())) {
                        valueList.add(parseJson((EdmComplexType) edmProperty.getType(), o));
                    }
                } else {
                    valueList.addAll(((List) property.getValue()));
                }
                objMap.put(property.getName(), valueList);

            } else if (property.isComplex()) {
                objMap.put(property.getName(), parseJson((EdmComplexType) edmProperty.getType(), (ComplexValue) property.getValue()));
            } else if (property.isNull()) {

            } else if (property.isGeospatial()) {
                objMap.put(property.getName(), property.getValue());
            } else if (property.isPrimitive()) {
                objMap.put(property.getName(), property.getValue());
            }

        }
        return objMap;
    }


    public void createEntityData(EdmEntitySet edmEntitySet, Entity createdEntity) throws ODataApplicationException {

        try {

            String id = getKey(edmEntitySet.getEntityType(), createdEntity);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(toJson(edmEntitySet, createdEntity));
            jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"name\", \"data\" ,\"id\") VALUES (?, ?, ?, ?)", servlet.getRepositoryId(), edmEntitySet.getName(), jsonObject, id);
        } catch (SQLException e) {
            throw new ODataApplicationException("SQLException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }


    }

    private String toJson(EdmEntitySet edmEntitySet, Entity entity) throws ODataApplicationException {
        try {
            ODataSerializer serializer = getOData().createSerializer(ODataFormat.JSON);
            ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();

            ExpandOptionImpl expandOption = new ExpandOptionImpl();
            expandOption.addExpandItem(new ExpandItemImpl() {{
                setIsStar(true);
            }});
            EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).expand(expandOption).build();
            SerializerResult serializedResponse = serializer.entity(getServiceMetadata(), edmEntitySet.getEntityType(), entity, options);


            return IOUtils.toString(serializedResponse.getContent());

        } catch (SerializerException e) {
            throw new ODataApplicationException("SerializerException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        } catch (IOException e) {
            throw new ODataApplicationException("IOException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }
    }

    /**
     * This method is invoked for PATCH or PUT requests
     */
    public void updateEntity(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Entity updateEntity, HttpMethod httpMethod) throws ODataApplicationException {

        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        Entity entity = readEntityData(edmEntitySet, keyParams, new ExpandOptionImpl());


        for (Property existingProp : entity.getProperties()) {
            String propName = existingProp.getName();

            if (isKey(edmEntityType, propName)) {
                continue;
            }

            Property updateProperty = updateEntity.getProperty(propName);

            if (updateProperty == null) {
                if (httpMethod.equals(HttpMethod.PATCH)) {
                    continue;
                } else if (httpMethod.equals(HttpMethod.PUT)) {
                    existingProp.setValue(existingProp.getValueType(), null);
                    continue;
                }
            }

            existingProp.setValue(existingProp.getValueType(), updateProperty.getValue());
        }

        updateDataToDb(edmEntitySet, keyParams, entity);

    }

    private void updateDataToDb(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Entity entity) throws ODataApplicationException {
        StringBuffer sqlBuilder = new StringBuffer("update  entity set data = ? ");
        where(sqlBuilder, edmEntitySet, keyParams);

        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(toJson(edmEntitySet, entity));
        } catch (SQLException e) {
            throw new ODataApplicationException("SQLException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }

        int result = jdbcTemplate.update(sqlBuilder.toString(), jsonObject);
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
    }


    public Map readEntityNavigationLinks(EdmEntitySet edmEntitySet, List<UriParameter> keyParams) throws ODataApplicationException {
        StringBuffer sqlBuilder = new StringBuffer("select * from  entity ");
        where(sqlBuilder, edmEntitySet, keyParams);
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        if (result.size() == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.CHINESE);
        PGobject jsonObject = (PGobject) result.get(0).get("$ref");

        ObjectMapper mapper = new ObjectMapper();
        try {
            if (jsonObject == null) return new HashMap();
            return mapper.readValue(jsonObject.getValue(), Map.class);
        } catch (IOException e) {
            throw new ODataApplicationException("IOException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }
    }


    public void updateEntityNavigationLinks(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, String refName, List<UriInfo> link, HttpMethod httpMethod) throws ODataApplicationException {
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        Map linkMap = readEntityNavigationLinks(edmEntitySet, keyParams);

        List<Map> mapLinks = new ArrayList<Map>();
        if (httpMethod == HttpMethod.PATCH && linkMap.containsKey(refName)) {
            mapLinks.addAll((List<Map>) linkMap.get(refName));
        } else {
            linkMap.remove(refName);
        }
        linkMap.put(refName, mapLinks);
        for (UriInfo uriInfo : link) {
            UriResourceEntitySet refUriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
            mapLinks.add(getKey(refUriResourceEntitySet.getKeyPredicates()));
        }
        ObjectMapper mapper = new ObjectMapper();


        StringBuffer sqlBuilder = new StringBuffer("update  entity set \"$ref\" = ? ");
        where(sqlBuilder, edmEntitySet, keyParams);

        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(mapper.writeValueAsString(linkMap));
        } catch (SQLException e) {
            throw new ODataApplicationException("SQLException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        } catch (JsonProcessingException e) {
            throw new ODataApplicationException("JsonProcessingException", HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }

        int result = jdbcTemplate.update(sqlBuilder.toString(), jsonObject);
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);


    }


    public void deleteEntityData(EdmEntitySet edmEntitySet, List<UriParameter> keyParams) throws ODataApplicationException {

        EdmEntityType entityType = edmEntitySet.getEntityType();
        StringBuffer sqlBuilder = new StringBuffer("delete  from entity ");
        where(sqlBuilder, edmEntitySet, keyParams);
        int result = jdbcTemplate.update(sqlBuilder.toString());

        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
    }

	/* HELPER */


    private boolean isKey(EdmEntityType edmEntityType, String propertyName) {
        List<EdmKeyPropertyRef> keyPropertyRefs = edmEntityType.getKeyPropertyRefs();
        for (EdmKeyPropertyRef propRef : keyPropertyRefs) {
            String keyPropertyName = propRef.getName();
            if (keyPropertyName.equals(propertyName)) {
                return true;
            }
        }
        return false;
    }


    private Map getKey(List<UriParameter> uriParameters) {
        Map<String, String> map = new HashMap<String, String>();
        for (UriParameter uriParameter : uriParameters) {
            map.put(uriParameter.getName(), uriParameter.getText());
        }
        return map;
    }

    private String getKey(EdmEntityType edmEntityType, Entity entity) {
        StringBuilder keyBuilder = new StringBuilder();
        List<EdmKeyPropertyRef> keyPropertyRefs = edmEntityType.getKeyPropertyRefs();
        for (EdmKeyPropertyRef propRef : keyPropertyRefs) {
            String keyPropertyName = propRef.getName();
            keyBuilder.append(entity.getProperty(keyPropertyName).getValue());
        }
        return keyBuilder.toString();
    }

}
