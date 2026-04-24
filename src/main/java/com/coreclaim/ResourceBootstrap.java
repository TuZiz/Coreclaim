package com.coreclaim;

import com.coreclaim.config.ResourceConfig;

public final class ResourceBootstrap {

    public PreparedResources prepare(CoreClaimPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.ensureConfigDefaults();
        plugin.ensureRulesDefaults();
        plugin.ensureMessagesDefaults();
        plugin.ensureHealthyGuiResources();
        ResourceConfig messageResource = new ResourceConfig(plugin, "messages.yml");
        ResourceConfig groupsResource = new ResourceConfig(plugin, "groups.yml");
        ResourceConfig rulesResource = new ResourceConfig(plugin, "rules.yml");
        plugin.loadMenuResources();
        return new PreparedResources(messageResource, groupsResource, rulesResource);
    }

    public record PreparedResources(
        ResourceConfig messageResource,
        ResourceConfig groupsResource,
        ResourceConfig rulesResource
    ) {
    }
}
