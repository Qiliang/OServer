package com.idea.ohmydata.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idea.ohmydata.ODataServlet;
import com.idea.ohmydata.UriInfoUtils;
import com.idea.ohmydata.persistence.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

@Service
class PostgresDataService implements PersistenceDataService {


    @Autowired
    private ODataServlet servlet;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    PersistenceJsonSerializer serializer = new PersistenceJsonSerializer();

    @Override
    @Transactional
    public void createReference(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
//        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
//        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
//        try {
//            UriInfoContext uriInfoContext = getUriInfoContext(odata, serviceMetadata, uriResources);
//            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
//            DeserializerResult result = deserializer.entityReferences(request.getBody());
//
//
//            List<Map> propertyJsonList = new ArrayList<>();
//            int rawBaseUriIndex = request.getRawBaseUri().length();
//            List<List<UriParameter>> uriParameterList = new ArrayList<>();
//            for (URI uri : result.getEntityReferences()) {
//                UriInfo refUriInfo = new Parser().parseUri(uri.toString().substring(rawBaseUriIndex), null, null, serviceMetadata.getEdm());
//                UriInfoContext refContext = getUriInfoContext(odata, serviceMetadata, refUriInfo.getUriResourceParts());
//                Map propertyMap = new HashMap<>();
//                for (UriParameter uriParameter : refContext.getKeyParams()) {
//                    propertyMap.put(uriParameter.getName(), uriParameter.getText().replace("'", StringUtils.EMPTY));
//                }
//                propertyJsonList.add(propertyMap);
//
//            }
//
//            Map propertyJsonMap = new HashMap<>();
//            propertyJsonMap.put(uriInfoContext.getEdmNavigationProperty().getName(), propertyJsonList);
//            updateNavigationProperty(uriInfoContext.getPreEntitySet(), uriInfoContext.getKeyParams(), propertyJsonMap);
//
//        } catch (SerializerException e) {
//            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
//        } catch (SQLException e) {
//            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
//        } catch (UriParserException e) {
//            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
//        } catch (DeserializerException e) {
//            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
//        } catch (IOException e) {
//            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
//        } finally {
//
//        }
    }


    @Override
    public DbEntityCollection readEntityCollection(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        DbEntityCollection dbEntityCollection = new DbEntityCollection();
        UriInfoContext ctx = getUriInfoContext(odata, serviceMetadata, uriInfo);

        if (ctx.edmNavigationProperty != null) {
            //"select t.data from (select data from entity where type='@ref') ref inner join (select data from entity where type<>'@ref') t on (ref.data->'Friends'->'source'->'UserName' = t.data->'UserName')"
            JsonCollection jsonObjs = retrieveEntityCollection(ctx.getEntityType(), uriInfo.asUriInfoResource(), new JsonObj());
            StringBuffer from = new StringBuffer(255);
            from.append("(select data from entity where type='@ref' and ").append("\"repositoryId\" ='").append(servlet.getRepositoryId()).append("') ref");
            from.append(" inner join ");
            from.append("(select data from entity where type<>'@ref' and ").append("\"repositoryId\" ='").append(servlet.getRepositoryId()).append("') t");
            from.append(" on ");
            from.append("ref.data->'").append(ctx.edmNavigationProperty.getName()).append("'->");

            List<String> joinCondition = new ArrayList<>();
            for (String key : ctx.entity.getKeyObj().keySet()) {
                joinCondition.add(String.format(key, ctx));
            }
           //String.join(" and ",);

            dbEntityCollection.setEntityType(ctx.getEntityType());
            dbEntityCollection.setEntityCollection(jsonObjs.toEntityCollection(odata));
        } else {
            JsonCollection jsonObjs = retrieveEntityCollection(ctx.getEntityType(), uriInfo.asUriInfoResource(), new JsonObj());
            dbEntityCollection.setEntityType(ctx.getEntityType());
            dbEntityCollection.setEntityCollection(jsonObjs.toEntityCollection(odata));
        }
        return dbEntityCollection;
    }

    private JsonCollection retrieveEntityCollection(EdmEntityType edmEntityType, UriInfoResource uriInfoResource, JsonObj condition) throws ODataApplicationException {
        int top = uriInfoResource.getTopOption() == null ? 0 : uriInfoResource.getTopOption().getValue();
        int skip = uriInfoResource.getSkipOption() == null ? 0 : uriInfoResource.getSkipOption().getValue();
        String orderBy = UriInfoUtils.getOrderBy(uriInfoResource.getOrderByOption());
        ExpandOption expandOption = uriInfoResource.getExpandOption() == null ? new ExpandOptionImpl() : uriInfoResource.getExpandOption();
        StringBuffer sql = new StringBuffer();
        sql.append("select * from entity where \"repositoryId\" = ").append("'").append(servlet.getRepositoryId()).append("' and type='")
                .append(edmEntityType.getFullQualifiedName().getFullQualifiedNameAsString()).append("'");
        sql.append(" and ( ").append(condition.toSQL()).append(" )");
        sql.append(orderBy);
        if (top > 0) sql.append(" limit ").append(top);
        if (skip > 0) sql.append(" offset ").append(skip);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql.toString());
        JsonCollection entityCollection = new JsonCollection();
        for (Map<String, Object> row : result) {
            entityCollection.add(retrieveEntity(edmEntityType, expandOption, (PGobject) row.get("data")));
        }
        return entityCollection;

    }

    private EntityCollection readEntitySetData(EdmEntitySet edmEntitySet, UriInfoResource uriInfoResource, String sql, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        int top = uriInfoResource.getTopOption() == null ? 0 : uriInfoResource.getTopOption().getValue();
        int skip = uriInfoResource.getSkipOption() == null ? 0 : uriInfoResource.getSkipOption().getValue();
        String orderBy = UriInfoUtils.getOrderBy(uriInfoResource.getOrderByOption());
        ExpandOption expandOption = uriInfoResource.getExpandOption() == null ? new ExpandOptionImpl() : uriInfoResource.getExpandOption();
        return readEntitySetData(edmEntitySet, top, skip, sql, orderBy, expandOption, odata, serviceMetadata);

    }

    private EntityCollection readEntitySetData(EdmEntitySet edmEntitySet, int top, int skip, String sql, String orderBy, ExpandOption expandOption, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        StringBuffer sqlBuilder = new StringBuffer();
        sqlBuilder.append("select * from entity where \"repositoryId\" = ").append("'").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        if (StringUtils.isNotBlank(sql)) {
            sqlBuilder.append(" and ( ").append(sql).append(" )");
        }
        sqlBuilder.append(orderBy);
        if (top > 0) sqlBuilder.append(" limit ").append(top);
        if (skip > 0) sqlBuilder.append(" offset ").append(skip);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        EntityCollection entityCollection = new EntityCollection();
        for (Map<String, Object> row : result) {
            Entity resEntity = getEntity(edmEntitySet, row, expandOption, odata, serviceMetadata);
            entityCollection.getEntities().add(resEntity);
        }

        return entityCollection;
    }

    @Override
    public int countEntityCollection(UriInfo uriInfo) throws ODataApplicationException {
        return 0;
    }

    @Override
    public Entity readEntity(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        UriInfoContext ctx = getUriInfoContext(odata, serviceMetadata, uriInfo);
        return ctx.entity.toEntity(odata);
    }

    private Entity getEntity(EdmEntitySet edmEntitySet, List<UriParameter> keyParameters, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        Map row = getEntityRow(edmEntitySet, where(edmEntitySet, keyParameters));
        return getEntity(edmEntitySet, row, new ExpandOptionImpl(), odata, serviceMetadata);
    }

    private Entity getEntity(EdmEntitySet edmEntitySet, Map entityRow, ExpandOption expandOption, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {

        DeserializerResult deserializerResult;
        try {
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            PGobject jsonObject = (PGobject) entityRow.get("data");
            deserializerResult = deserializer.entity(IOUtils.toInputStream(jsonObject.getValue()), edmEntitySet.getEntityType());
        } catch (DeserializerException e) {
            throw new ODataApplicationException("DeserializerException:" + e.getMessage(), HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.getDefault());
        }
        Map dataRow = getData(entityRow);
        Entity entity = deserializerResult.getEntity();

        for (ExpandItem expandItem : expandOption.getExpandItems()) {
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) expandItem.getResourcePath().getUriResourceParts().get(0);
            EdmNavigationProperty navigationProperty = uriResourceNavigation.getProperty();
            String refName = navigationProperty.getName();
            if (!dataRow.containsKey(refName)) continue;

            EdmEntitySet refEdmEntitySet = getNavigationPropertyBindingSet(refName, edmEntitySet, serviceMetadata);
            Link link = entity.getNavigationLink(refName);
            link.setRel(refName);
            link.setTitle(refName);
            if (navigationProperty.isCollection()) {
                EntityCollection refEntityCollection = readEntitySetData(refEdmEntitySet, -1, -1, whereIds((List<Map>) dataRow.get(refName)), "", new ExpandOptionImpl(), odata, serviceMetadata);
                link.setInlineEntitySet(refEntityCollection);
            } else {
                Map row = getEntityRow(refEdmEntitySet, where(refEdmEntitySet, (Map) dataRow.get(refName)));
                link.setInlineEntity(getEntity(refEdmEntitySet, row, new ExpandOptionImpl(), odata, serviceMetadata));
            }

        }
        return entity;
    }

    private Map getEntityRow(EdmEntitySet edmEntitySet, String where) throws ODataApplicationException {
        StringBuffer sqlBuilder = new StringBuffer("select * from entity ");
        sqlBuilder.append(where);
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        if (result.size() == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.getDefault());
        return result.get(0);
    }

    private Map getData(Map entityRow) throws ODataApplicationException {
        ObjectMapper mapper = new ObjectMapper();
        PGobject jsonObject = (PGobject) entityRow.get("data");
        try {
            return mapper.readValue(jsonObject.getValue(), Map.class);
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.getDefault());
        }
    }

    @Override
    @Transactional(rollbackFor = ODataApplicationException.class)
    public Entity createEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {

        try {
            UriInfoContext ctx = getUriInfoContext(odata, serviceMetadata, uriInfo);
            EdmEntitySet edmEntitySet = ctx.entitySet;
            EdmNavigationProperty edmNavigationProperty = ctx.edmNavigationProperty;
            byte[] content = IOUtils.toByteArray(new AutoCloseInputStream(request.getBody()));
            EdmEntityType edmEntityType = edmNavigationProperty != null ? edmNavigationProperty.getType() : edmEntitySet.getEntityType();
            JsonObj entity = JsonObj.parse(content);
            entity.setEdmEntityType(edmEntityType);
            createEntity(edmEntityType, content);
            if (ctx.entity != null) {
                createRef(edmNavigationProperty, ctx.entity.getKeyObj(), entity.getKeyObj());
            }
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            return deserializer.entity(toInputStream(content), edmEntityType).getEntity();

        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (Exception e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }
    }

    private InputStream toInputStream(byte[] content) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
        return new AutoCloseInputStream(inputStream);
    }

    private void createEntity(EdmEntityType edmEntityType, byte[] content) throws ODataApplicationException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(new String(content));
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
        }
        jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"type\", \"data\" ) VALUES (?, ?, ?)", servlet.getRepositoryId(), edmEntityType.getFullQualifiedName().getFullQualifiedNameAsString(), jsonObject);
    }

    private void createRef(EdmNavigationProperty edmNavigationProperty, JsonObj source, JsonObj target) throws ODataApplicationException {

        JsonObj data = new JsonObj();
        data.put("source", source);
        data.put("target", source);
        JsonObj ref = new JsonObj();
        ref.put(edmNavigationProperty.getName(), data);
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(ref.toJson());
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
        }
        jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"type\", \"data\" ) VALUES (?, ?, ?)", servlet.getRepositoryId(), "@ref", jsonObject);
    }

    private JsonObj retrieveEntity(EdmEntityType edmEntityType, JsonObj condition) throws ODataApplicationException {
        return retrieveEntity(edmEntityType, condition, new ExpandOptionImpl());
    }

    private JsonObj retrieveEntity(EdmEntityType edmEntityType, JsonObj condition, ExpandOption expandOption) throws ODataApplicationException {

        StringBuffer sql = new StringBuffer("select * from entity ");
        sql.append("where \"repositoryId\"='").append(servlet.getRepositoryId()).append("' ");
        sql.append(" and (").append(condition.getKeyObj().toSQL()).append(")");
        List<Map<String, Object>> resultSet = jdbcTemplate.queryForList(sql.toString());
        if (resultSet.size() == 0)
            throw new ODataApplicationException("Entity Not Found", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());

        return retrieveEntity(edmEntityType, expandOption, (PGobject) resultSet.get(0).get("data"));
    }

    private JsonObj retrieveEntity(EdmEntityType edmEntityType, ExpandOption expandOption, PGobject pGobject) throws ODataApplicationException {
        JsonObj entityObj = JsonObj.parse(pGobject.getValue());
        entityObj.setEdmEntityType(edmEntityType);
        for (ExpandItem expandItem : expandOption.getExpandItems()) {
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) expandItem.getResourcePath().getUriResourceParts().get(0);
            EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
            JsonObj expandCondition = new JsonObj();
            expandCondition.setEdmEntityType(edmNavigationProperty.getType());
            expandCondition.put("@from", entityObj.getKeyObj());
            List<JsonObj> jsonObjList = retrieveEntityCollection(edmNavigationProperty.getType(), expandItem.getResourcePath(), expandCondition);
            if (edmNavigationProperty.isCollection()) {
                entityObj.put(edmNavigationProperty.getName(), jsonObjList);
            } else {
                entityObj.put(edmNavigationProperty.getName(), jsonObjList.get(0));
            }
        }

        return entityObj;
    }


    private UriInfoContext getUriInfoContext(OData odata, ServiceMetadata serviceMetadata, UriInfo uriInfo) throws ODataApplicationException {
        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
        UriInfoContext context = new UriInfoContext();
        for (int i = 0; i < uriResources.size(); i++) {
            UriResource uriResource = uriResources.get(i);
            if (uriResource.getKind() == UriResourceKind.entitySet) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
                context.entitySet = uriResourceEntitySet.getEntitySet();
                if (uriResourceEntitySet.getKeyPredicates() == null || uriResourceEntitySet.getKeyPredicates().size() == 0) {
                    break;
                }
                context.keyParams = uriResourceEntitySet.getKeyPredicates();
                context.entity = retrieveEntity(context.entitySet.getEntityType(), JsonObj.parse(context.keyParams).setEdmEntityType(context.entitySet.getEntityType()));
            } else if (uriResource.getKind() == UriResourceKind.navigationProperty) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResource;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                context.edmNavigationProperty = edmNavigationProperty;
                if (uriResourceNavigation.getKeyPredicates() == null || uriResourceNavigation.getKeyPredicates().size() == 0)
                    break;
                context.keyParams = uriResourceNavigation.getKeyPredicates();
                context.entity = retrieveEntity(context.entitySet.getEntityType(), JsonObj.parse(context.keyParams));
            }

        }
        return context;
    }

    private boolean match(Entity entity, List<UriParameter> parameters) {
        for (UriParameter parameter : parameters) {
            Property property = entity.getProperty(parameter.getName());
            if (property == null) return false;
            if (!property.getValue().toString().equals(parameter.getText().replace("'", ""))) return false;
        }
        return true;
    }


    @Override
    @Transactional
    public void updateEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        try {
            UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
            List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
            UriInfoContext uriInfoContext = null;// getUriInfoContext(odata, serviceMetadata, uriResources);

            Entity entity = null;// uriInfoContext.getEntity();
            EdmEntitySet edmEntitySet = null;// uriInfoContext.getEntitySet();

            InputStream requestInputStream = request.getBody();
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            DeserializerResult result = deserializer.entity(requestInputStream, edmEntitySet.getEntityType());
            Entity updateEntity = result.getEntity();


            for (Property existingProp : entity.getProperties()) {
                String propName = existingProp.getName();

                if (isKey(edmEntitySet.getEntityType(), propName)) {
                    continue;
                }

                Property updateProperty = updateEntity.getProperty(propName);

                if (updateProperty == null) {
                    if (request.getMethod().equals(HttpMethod.PATCH)) {
                        continue;
                    } else if (request.getMethod().equals(HttpMethod.PUT)) {
                        existingProp.setValue(existingProp.getValueType(), null);
                        continue;
                    }
                }

                existingProp.setValue(existingProp.getValueType(), updateProperty.getValue());
            }
            // updateEntity(edmEntitySet, uriInfoContext.getKeyParams(), entity, serviceMetadata);
            updateEntity(edmEntitySet, null, entity, serviceMetadata);
        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (SerializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } finally {
            // IOUtils.closeQuietly(jsonStream);
        }
    }

    private void updateEntity(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Entity entity, ServiceMetadata serviceMetadata) throws ODataApplicationException, SerializerException, IOException, SQLException {
        StringBuffer sqlBuilder = new StringBuffer("update entity set data = ? ");
        where(sqlBuilder, edmEntitySet, keyParams);
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        InputStream jsonStream = null;
        try {
            jsonStream = serializer.entity(serviceMetadata, edmEntitySet.getEntityType(), entity, EntitySerializerOptions.with().build());
            jsonObject.setValue(IOUtils.toString(jsonStream));
        } finally {
            IOUtils.closeQuietly(jsonStream);
        }

        int result = jdbcTemplate.update(sqlBuilder.toString(), jsonObject);
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.getDefault());
    }

    private void updateEntityRow(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Map entityRow) throws ODataApplicationException {
        ObjectMapper mapper = new ObjectMapper();

        StringBuffer sqlBuilder = new StringBuffer("update entity set data = ? ");
        where(sqlBuilder, edmEntitySet, keyParams);
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(mapper.writeValueAsString(entityRow));
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (JsonProcessingException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }

        int result = jdbcTemplate.update(sqlBuilder.toString(), jsonObject);
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.getDefault());
    }


    private void updateNavigationProperty(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Map propertyJsonMap) throws ODataApplicationException, SerializerException, IOException, SQLException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map row = getEntityRow(edmEntitySet, where(edmEntitySet, keyParams));
        PGobject jsonObject = (PGobject) row.get("data");
        Map entityMap = objectMapper.readValue(jsonObject.getValue(), Map.class);
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        for (Object proeprtyKey : propertyJsonMap.keySet()) {
            EdmNavigationProperty edmNavigationProperty = edmEntityType.getNavigationProperty(proeprtyKey.toString());
            if (edmNavigationProperty == null || !propertyJsonMap.containsKey(proeprtyKey)) continue;
            if (edmNavigationProperty.isCollection()) {
                List valueList = entityMap.containsKey(proeprtyKey) ? (List) entityMap.get(proeprtyKey) : new ArrayList<>();
                valueList.addAll((Collection) propertyJsonMap.get(proeprtyKey));
                entityMap.put(proeprtyKey, valueList);
            } else {
                List list = (List) propertyJsonMap.get(proeprtyKey);
                entityMap.put(proeprtyKey, list.get(0));
            }
        }
        updateEntityRow(edmEntitySet, keyParams, entityMap);
    }

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

    @Override
    @Transactional
    public void deleteEntity(UriInfo uriInfo) throws ODataApplicationException {

    }


    private EdmEntitySet getNavigationPropertyBindingSet(String refName, EdmEntitySet edmEntitySet, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        for (EdmNavigationPropertyBinding edmNavigationPropertyBinding : edmEntitySet.getNavigationPropertyBindings()) {
            if (edmNavigationPropertyBinding.getPath().equals(refName)) {
                return serviceMetadata.getEdm().getEntityContainer().getEntitySet(edmNavigationPropertyBinding.getTarget());
            }
        }
        throw new ODataApplicationException(String.format("EntitySet %s not found", refName), HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.CHINESE);
    }

    private String where(EdmEntitySet edmEntitySet, Map keyParams) {
        StringBuffer sqlBuilder = new StringBuffer();
        sqlBuilder.append(" where \"repositoryId\" = '").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        for (Object key : keyParams.keySet()) {
            String value = keyParams.get(key).toString().replaceAll("'", "");
            sqlBuilder.append(" and data->>'").append(key).append("' =  '").append(value + "'");
        }
        return sqlBuilder.toString();
    }

    private String where(EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        StringBuffer sqlBuilder = new StringBuffer();
        where(sqlBuilder, edmEntitySet, keyParams);
        return sqlBuilder.toString();
    }

    private void where(StringBuffer sqlBuilder, EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        sqlBuilder.append(" where \"repositoryId\" = '").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        for (UriParameter keyParam : keyParams) {
            String value = keyParam.getText().replaceAll("'", "");
            sqlBuilder.append(" and data->>'").append(keyParam.getName()).append("' =  '").append(value).append("'");
        }
    }

    private String whereIds(EntityCollection entityCollection) {
        List<String> entityConditions = new ArrayList<String>();
        for (Entity entity : entityCollection.getEntities()) {
            List<String> keyConditions = new ArrayList<String>();
            for (Property property : entity.getProperties()) {
                keyConditions.add("data->>'" + property.getName() + "' =  '" + property.getValue() + "'");
            }
            entityConditions.add(" ( " + String.join(" and ", keyConditions) + " )");

        }

        return String.join(" or ", entityConditions);

    }

    private String whereIds(List<Map> keyMapList) {
        List<String> entityConditions = new ArrayList<String>();
        for (Map keyMap : keyMapList) {
            List<String> keyConditions = new ArrayList<String>();
            for (Object key : keyMap.keySet()) {
                keyConditions.add("data->>'" + key + "' =  '" + keyMap.get(key) + "'");
            }
            entityConditions.add(" ( " + String.join(" and ", keyConditions) + " )");

        }

        return String.join(" or ", entityConditions);

    }

    class UriInfoContext {
        JsonObj entity = null;
        EdmEntitySet entitySet = null;
        List<UriParameter> keyParams;
        EdmNavigationProperty edmNavigationProperty;

        public EdmEntityType getEntityType() {
            return edmNavigationProperty == null ? entitySet.getEntityType() : edmNavigationProperty.getType();
        }
    }

}
