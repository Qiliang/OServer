package com.idea.ohmydata.persistence;


import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntityType;

public class DbEntityCollection {
    EntityCollection entityCollection;
    EdmEntityType entityType;

    public EntityCollection getEntityCollection() {
        return entityCollection;
    }

    public void setEntityCollection(EntityCollection entityCollection) {
        this.entityCollection = entityCollection;
    }

    public EdmEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EdmEntityType entityType) {
        this.entityType = entityType;
    }
}
