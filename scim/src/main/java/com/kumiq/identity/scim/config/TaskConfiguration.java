package com.kumiq.identity.scim.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * @author Weinan Qiu
 * @since 1.0.0
 */
@Configuration
@ImportResource({
        "config/UserGetTaskConfig.xml",
        "config/GroupGetTaskConfig.xml",
        "config/UserDeleteTaskConfig.xml",
        "config/GroupDeleteTaskConfig.xml",
        "config/UserQueryTaskConfig.xml"
})
public class TaskConfiguration {
}
