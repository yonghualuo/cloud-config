package org.squirrelframework.cloud.resource.database;

import org.squirrelframework.cloud.resource.AbstractRoutingResourceFactoryBean;
import org.squirrelframework.cloud.resource.TenantSupport;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by kailianghe on 9/7/15.
 */
public class RoutingDataSourceFactoryBean extends AbstractRoutingResourceFactoryBean<DataSource> {

    private static final Logger myLogger = LoggerFactory.getLogger(RoutingDataSourceFactoryBean.class);

    private Class<?> dataSourceFactoryBeanClass;

    @Override
    public Class<?> getObjectType() {
        return DataSource.class;
    }

    @Override
    protected DataSource createInstance() throws Exception {
        List<String> children = client.getChildren().forPath(path);
        for(String child : children) {
            String resPath = path + "/" + child;
            buildResourceBeanDefinition(resPath, getResourceBeanIdFromPath(resPath));
        }
        return new RoutingDataSource();
    }

    @Override
    protected String getResourceBeanIdFromPath(String resPath) {
        return "_"+resPath.replace('/','_')+"DS";
    }

    @Override
    protected void buildResourceBeanDefinition(String dsPath, String dsBeanId) throws Exception {
        DefaultListableBeanFactory beanFactory =
                (DefaultListableBeanFactory)((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        if(beanFactory.containsBeanDefinition(dsBeanId)) {
            return;
        }
        // build datasource bean based on config bean
        BeanDefinitionBuilder dsBuilder = BeanDefinitionBuilder.rootBeanDefinition(dataSourceFactoryBeanClass);
        dsBuilder.addPropertyValue("configPath", dsPath);
        dsBuilder.addPropertyValue("validator", validator);
        dsBuilder.setLazyInit(true);
        beanFactory.registerBeanDefinition(dsBeanId, dsBuilder.getBeanDefinition());
        myLogger.info("Bean definition of resource '{}' is created as '{}'.", dsPath, dsBeanId);
    }

    @Override
    protected void removeResourceBeanDefinition(String resPath, String beanId) throws Exception {
        DefaultListableBeanFactory beanFactory =
                (DefaultListableBeanFactory)((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        if(beanFactory.containsBeanDefinition(beanId)) {
            beanFactory.removeBeanDefinition(beanId);
        }
        String confBeanId = AbstractDataSourceFactoryBean.getResourceConfigBeanIdFromPath(resPath);
        if(beanFactory.containsBeanDefinition(confBeanId)) {
            beanFactory.removeBeanDefinition(confBeanId);
        }

        // TODO-hhe: clean local cached data sources as we cannot get tenant id here
        if( getObject()!=null ) {
            ((RoutingDataSource)getObject()).localDataSources.clear();
        }
        myLogger.info("Bean definition of resource '{}' is removed as '{}'.", resPath, beanId);
    }

    @Required
    public void setDataSourceFactoryBeanClass(Class<?> dataSourceFactoryBeanClass) {
        this.dataSourceFactoryBeanClass = dataSourceFactoryBeanClass;
    }

    public void setFallbackDataSourcePath(String fallbackDataSourcePath) {
        this.fallbackResourcePath = fallbackDataSourcePath;
    }

    public void setFallbackDataSource(DataSource fallbackDataSource) {
        this.fallbackResource = fallbackDataSource;
    }

    public class RoutingDataSource extends AbstractRoutingDataSource implements TenantSupport<DataSource> {

        private ConcurrentMap<String, DataSource> localDataSources = Maps.newConcurrentMap();

        public RoutingDataSource() {
            setTargetDataSources(Collections.emptyMap());
        }

        @Override
        protected Object determineCurrentLookupKey() {
            Object lookupKey = resolver.get().orNull();
            if( lookupKey!=null ) {
                myLogger.debug("Routing data source lookup key is '{}'", lookupKey.toString());
            } else {
                myLogger.warn("Routing data source lookup key cannot be found in current context!");
                lookupKey = "__absent_tenant__";
            }
            return lookupKey;
        }

        @Override
        protected DataSource determineTargetDataSource() {
            String lookupKey = determineCurrentLookupKey().toString();
            return get(lookupKey);
        }

        @Override
        public DataSource get(String tenantId) {
            DataSource dataSource = localDataSources.get(tenantId);
            if(dataSource==null) {
                try {
                    String expectedBeanId = getResourceBeanIdFromPath(path+"/"+ tenantId);
                    dataSource = applicationContext.getBean(expectedBeanId, DataSource.class);
                    localDataSources.put(tenantId, dataSource);
                    myLogger.info("DataSource for tenant '{}' is resolved as '{}'.", tenantId, dataSource.toString());
                } catch (NoSuchBeanDefinitionException e) {
                    // find fallback datasource - "unknown"
                    if(fallbackResource!=null) {
                        dataSource = fallbackResource;
                        myLogger.warn("Cannot find proper data source for tenant '{}'. Use fallback data source instead.", tenantId);
                    } else {
                        throw new IllegalStateException("Cannot determine target DataSource for lookup key [" + tenantId + "]", e);
                    }
                }
            }
            return dataSource;
        }
    }
}