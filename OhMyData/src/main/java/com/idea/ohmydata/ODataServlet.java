package com.idea.ohmydata;

import com.nikoyo.otest.persisitence.Storage;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.edmx.EdmxReference;
import org.apache.olingo.server.api.processor.ReferenceProcessor;
import org.postgresql.jdbc4.Jdbc4SQLXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class ODataServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ODataServlet.class);

    @Autowired
    Storage storage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DefaultEntityProcessor entityProcessor;

    @Autowired
    private ReferenceProcessor referenceProcessor;

    @Autowired
    private DefaultProcessor defaultProcessor;

    private ThreadLocal<String> repositoryId = new ThreadLocal<String>();

    public ODataServlet() {
        super();


    }

    public String getRepositoryId() {
        return repositoryId.get();
    }

    @Override
    public void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        try {
            OData odata = OData.newInstance();
            String url = req.getRequestURI();
            repositoryId.set(getRepositoryId(url));

            List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT  * FROM  \"repository\" WHERE \"id\" = ?", getRepositoryId());
            if (result.size() == 0) {
                resp.sendError(404);
                return;
            }


            Jdbc4SQLXML metadata = (Jdbc4SQLXML) result.get(0).get("metadata");
            DefaultEdmProvider provider = new DefaultEdmProvider(metadata.getString());
            List<EdmxReference> references = new ArrayList<EdmxReference>();
            ServiceMetadata serviceMetadata = odata.createServiceMetadata(provider, references);
            storage.setOData(odata);
            storage.setServiceMetadata(serviceMetadata);

            ODataHttpHandler handler = odata.createHandler(serviceMetadata);
            handler.setSplit(1);
            handler.register(defaultProcessor);
            handler.register(new DefaultEntityCollectionProcessor(storage));
            handler.register(entityProcessor);
            handler.register(referenceProcessor);
            handler.register(new DefaultPrimitiveProcessor(storage));
            handler.process(req, resp);

        } catch (RuntimeException e) {
            LOG.error("Server Error occurred in ExampleServlet", e);
            throw new ServletException(e);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private String getRepositoryId(String url) {
        return url.split("/")[1];
    }
}