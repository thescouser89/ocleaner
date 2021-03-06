package org.jboss.pnc.openshiftcleaner.client;

import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.model.IResource;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.openshiftcleaner.configuration.Configuration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

@ApplicationScoped
@Slf4j
public class OpenshiftClient {

    @Inject
    Configuration config;

    /**
     * Clean items of a particular kind which matches the label query. The intervalHours is the number of hours elapsed
     * before it is ok to remove that resource
     *
     * @param kind Kind of resource
     * @param namespace Namespace of the resources
     * @param intervalDays number of hours elapsed before removing resource
     * @param query query to run to find resources
     * @return list of resources removed
     */
    public List<String> cleanResources(String kind, String namespace, long intervalDays, String query) {

        List<String> deletedResources = new LinkedList<>();

        IClient client = getClient();

        List<IResource> resources = client.list(kind, namespace)
                .stream()
                .filter(a -> a.getName().contains(query))
                .collect(Collectors.toList());
        for (IResource resource : resources) {
            Map<String, String> metadata = resource.getMetadata();

            if (metadata.containsKey("creationTimestamp")) {

                LocalDate dateCreated = parseTimestamp(metadata.get("creationTimestamp"));
                long days = dayDuration(dateCreated);

                if (days > intervalDays) {
                    deleteResource(client, resource);
                    deletedResources.add(namespace + ":" + kind + ":" + resource.getName());
                }
            }
        }
        return deletedResources;
    }

    private IClient getClient() {

        return new ClientBuilder(config.getOpenshiftServer()).usingToken(config.getToken()).build();
    }

    /**
     * Given a timestamp, return a LocaleDate object
     * <p>
     * timestamp in the format: 2016-08-16T15:23:01Z
     *
     * @param timestamp: timestamp
     * @return LocaleDate object
     */
    private LocalDate parseTimestamp(String timestamp) {
        Instant instant = Instant.parse(timestamp);
        LocalDateTime result = LocalDateTime.ofInstant(instant, ZoneId.of(ZoneOffset.UTC.getId()));
        return result.toLocalDate();
    }

    private void deleteResource(IClient client, IResource resource) {
        log.info("Removing resource: " + resource.getName());
        client.delete(resource);
    }

    private long dayDuration(LocalDate start) {
        LocalDate today = LocalDate.now();
        return DAYS.between(start, today);
    }

}
