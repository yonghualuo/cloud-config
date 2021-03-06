package org.squirrelframework.cloud.resource;

import com.google.common.collect.Maps;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.utils.ZKPaths;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by kailianghe on 9/10/15.
 */
public class RoutingAwareResourceConfigFactoryBean extends AbstractResourceConfigFactoryBean<RoutingAwareResourceConfig> {

    private ConcurrentMap<String, Object> configHolder = Maps.newConcurrentMap();

    @Override
    protected RoutingAwareResourceConfig createInstance() throws Exception {
        List<String> children = client.getChildren().forPath(path);
        for(String child : children) {
            buildResourceConfig(child);
        }
        return new RoutingAwareResourceConfig(configHolder);
    }

    private void buildResourceConfig(String tenantId) throws Exception {
        String cnfPath = path+"/"+ tenantId;
        Object configObject = createConfig( client.getData().forPath(cnfPath) );

        List<String> profileNodes = client.getChildren().forPath(path);
        for(String configProfile : configProfiles) {
            if(profileNodes.contains(configProfile)) {
                byte[] data = client.getData().forPath(path+"/"+configProfile);
                configObject = createConfig(data, resourceType.cast(configObject));
            }
        }
        configHolder.putIfAbsent(tenantId, configObject);
    }

    @Override
    protected void handleChildAdded(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
        String nodeName = event.getData()!=null ?
                ZKPaths.getNodeFromPath(event.getData().getPath()) : "";
        buildResourceConfig(nodeName);
    }

    @Override
    protected void handleChildUpdated(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
        String nodeName = safeGetNodeNameFromEvent(event);
        Object configObj = configHolder.get(nodeName);
        if( configObj instanceof CloudResourceConfig) {
            CloudResourceConfig resourceConfig = (CloudResourceConfig)configObj;
            ChildData childData = event.getData();
            Object newConfig = createConfig(childData.getData(), getObject());
            BeanUtils.copyProperties(newConfig, resourceConfig);
            resourceConfig.reload();
        }
    }

    @Override
    public Class<?> getObjectType() {
        return RoutingAwareResourceConfig.class;
    }
}
